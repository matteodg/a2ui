/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.a2ui.core.resolution;

import static com.google.a2ui.core.resolution.NodeFixture.call;
import static com.google.a2ui.core.resolution.NodeFixture.component;
import static com.google.a2ui.core.resolution.NodeFixture.object;
import static com.google.a2ui.core.resolution.NodeFixture.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.catalog.ComponentApi;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The behavior of the node resolver for the check rules of a component. */
class NodeResolverChecksTest {
  private final NodeFixture fixture = new NodeFixture();

  private ComponentNode<ComponentApi> field(Map<String, Object> data, Object... checks) {
    return fixture.root(
        data,
        component(
            "root",
            "TextField",
            "label",
            "Name",
            "value",
            path("/name"),
            "@checks",
            List.of(checks)));
  }

  @Test
  void aComponentWithNoChecksHasNoValidityProperties() {
    ComponentNode<ComponentApi> root =
        fixture.root(Map.of(), component("root", "Text", "text", "Hi"));

    assertEquals(List.of("text"), List.copyOf(root.props().get().asMap().keySet()));
    assertTrue(root.props().get().isValid());
    assertEquals(List.of(), root.props().get().validationErrors());
    assertEquals(List.of(), root.props().get().validationResults());
  }

  @Test
  void aFailedCheckMakesTheComponentNotValidUntilTheDataChanges() {
    ComponentNode<ComponentApi> root =
        field(
            Map.of("name", ""),
            object(
                "condition", call("required", "value", path("/name")), "message", "Give a name."));
    int[] emissions = NodeFixture.countProps(root);

    NodeProps<ComponentApi> props = root.props().get();
    assertFalse(props.isValid());
    assertEquals(false, props.get(NodeProps.IS_VALID));
    assertEquals(List.of("Give a name."), props.validationErrors());
    assertEquals(1, props.validationResults().size());
    assertEquals(ValidationSeverity.ERROR, props.validationResults().getFirst().severity());

    props.writableBinding("value").orElseThrow().set("Ada");

    assertEquals(1, emissions[0]);
    assertTrue(root.props().get().isValid());
    assertEquals(List.of(), root.props().get().validationErrors());
    assertEquals(List.of(), root.props().get().get(NodeProps.VALIDATION_RESULTS));
  }

  @Test
  void theChecksKeepTheirSequenceAndOnlyTheSeverityErrorMakesAComponentNotValid() {
    Map<String, Object> hint =
        object("valid", false, "code", "HINT", "message", "Try a longer name.", "severity", "info");
    ComponentNode<ComponentApi> root =
        field(
            object("name", "", "hint", hint, "agreed", true),
            object("condition", call("required", "value", path("/name"))),
            object("condition", path("/hint"), "message", "Not used."),
            object("condition", path("/agreed"), "message", "Please agree."));

    List<ValidationResult> results = root.props().get().validationResults();
    assertEquals(2, results.size());
    assertEquals(
        new ValidationResult(
            false, Optional.of("HINT"), Optional.of("Try a longer name."), ValidationSeverity.INFO),
        results.get(1));
    assertEquals(1, root.props().get().validationErrors().size());
    assertEquals(
        results.get(0).message().orElseThrow(), root.props().get().validationErrors().get(0));

    fixture.setData("/name", "Ada");

    assertTrue(root.props().get().isValid());
    assertEquals(List.of(), root.props().get().validationErrors());
    assertEquals(1, root.props().get().validationResults().size());

    fixture.setData("/agreed", "no");

    assertFalse(root.props().get().isValid());
    assertEquals(List.of("Please agree."), root.props().get().validationErrors());
  }

  @Test
  void theMessageOfTheRuleReplacesTheMessageOfTheFunction() {
    ComponentNode<ComponentApi> root =
        field(
            Map.of("name", "not an address"),
            object("condition", call("email", "value", path("/name")), "message", "Bad address."),
            object("condition", call("email", "value", path("/name"))));

    List<ValidationResult> results = root.props().get().validationResults();

    assertEquals(Optional.of("Bad address."), results.get(0).message());
    assertEquals(Optional.of("INVALID_EMAIL"), results.get(0).code());
    assertTrue(results.get(1).message().isPresent());
    assertFalse(results.get(1).message().equals(results.get(0).message()));
  }

  @Test
  void aCheckThatCannotBeEvaluatedFailsAndTheResolverReportsItOneTime() {
    ComponentNode<ComponentApi> root =
        field(
            Map.of("name", "Ada"),
            object(
                "condition",
                call("regex", "value", path("/name"), "pattern", "(a+)+"),
                "message",
                "Bad pattern."));

    assertFalse(root.props().get().isValid());
    assertEquals(List.of("Bad pattern."), root.props().get().validationErrors());
    assertEquals(1, fixture.errors.size());

    fixture.setData("/name", "Grace");

    assertEquals(1, fixture.errors.size());
    assertFalse(root.props().get().isValid());
  }

  @Test
  void theAccessibilityAttributesAreResolvedInTheirOwnProperty() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of("name", "Ada", "quiet", "true"),
            component(
                "root",
                "Text",
                "text",
                "Hi",
                "@accessibility",
                object("label", path("/name"), "hidden", path("/quiet"), "live", "polite")));

    NodeProps<ComponentApi> attributes = root.props().get().accessibility();

    assertTrue(root.props().get().has(NodeProps.ACCESSIBILITY));
    assertEquals("Ada", attributes.value("label"));
    assertEquals(true, attributes.value("hidden"));
    assertEquals("polite", attributes.get("live"));

    fixture.setData("/name", "Grace");
    assertEquals("Grace", root.props().get().accessibility().value("label"));
  }

  @Test
  void aComponentWithNoAccessibilityAttributesHasAnEmptyResult() {
    ComponentNode<ComponentApi> root =
        fixture.root(Map.of(), component("root", "Text", "text", "Hi"));

    assertEquals(Map.of(), root.props().get().accessibility().asMap());
  }
}
