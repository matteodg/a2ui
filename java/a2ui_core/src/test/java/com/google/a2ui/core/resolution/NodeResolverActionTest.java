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
import static com.google.a2ui.core.resolution.NodeFixture.countProps;
import static com.google.a2ui.core.resolution.NodeFixture.object;
import static com.google.a2ui.core.resolution.NodeFixture.path;
import static com.google.a2ui.core.resolution.NodeFixture.template;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.state.RendererAction;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The behavior of the actions that the node resolver makes. */
class NodeResolverActionTest {
  private final NodeFixture fixture = new NodeFixture();

  private static Map<String, Object> open(String url) {
    return Map.of("functionCall", call("openUrl", "url", url));
  }

  private ComponentNode<ComponentApi> button(Map<String, Object> data, Map<String, Object> action) {
    return fixture.root(
        data,
        component("root", "Button", "child", "label", "action", action),
        component("label", "Text", "text", "Go"));
  }

  private static NodeAction action(ComponentNode<ComponentApi> node) {
    return node.props().get().action("action").orElseThrow();
  }

  @Test
  void anEventActionReportsTheContextAndTheUserMessageOfTheTimeOfTheCall() {
    Map<String, Object> event =
        object(
            "name", "save",
            "context", object("who", path("/name"), "fixed", 1L),
            "userMessage", call("formatString", "value", "Saved ${/name}"));
    ComponentNode<ComponentApi> root = button(Map.of("name", "Ada"), Map.of("event", event));
    NodeAction action = action(root);
    int[] emissions = countProps(root);

    fixture.setData("/name", "Grace");

    assertEquals(0, emissions[0]);
    assertSame(action, action(root));
    assertEquals(List.of(), fixture.actions);

    action.invoke();

    assertEquals(1, fixture.actions.size());
    RendererAction sent = fixture.actions.getFirst();
    assertEquals("save", sent.name());
    assertEquals(object("who", "Grace", "fixed", 1L), sent.context());
    assertEquals(Optional.of("Saved Grace"), sent.userMessage());
    assertEquals("root", sent.sourceComponentId());
    assertEquals(Optional.empty(), sent.catalogId());
    assertEquals(Optional.empty(), sent.metadata());
  }

  @Test
  void anActionSendsTheMetadataOfItsOptions() {
    ComponentNode<ComponentApi> root = button(Map.of(), Map.of("event", Map.of("name", "save")));
    A2uiMetadata metadata = new A2uiMetadata(Optional.of(Map.of("com_example_signed", true)));
    A2uiMetadata bad = new A2uiMetadata(Optional.of(Map.of("not an identifier", 1L)));

    action(root).invoke(ActionDispatchOptions.withMetadata(metadata));
    action(root).invoke(ActionDispatchOptions.withMetadata(bad));

    assertEquals(1, fixture.actions.size());
    assertEquals(Optional.of(metadata), fixture.actions.getFirst().metadata());
    assertEquals(Map.of(), fixture.actions.getFirst().context());
    assertEquals(1, fixture.errors.size());
    assertInstanceOf(A2uiValidationError.class, fixture.errors.getFirst());
  }

  @Test
  void anActionOfATemplateItemResolvesItsContextAtTheScopeOfTheItem() {
    Map<String, Object> event =
        object("name", "pick", "context", object("who", path("name"), "row", call("@index")));
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of("people", List.of(Map.of("name", "Ada"), Map.of("name", "Lin"))),
            component("root", "Column", "children", template("pick", "/people")),
            component("pick", "Button", "child", "label", "action", Map.of("event", event)),
            component("label", "Text", "text", path("name")));
    List<ComponentNode<ComponentApi>> buttons = root.props().get().children("children");

    action(buttons.get(1)).invoke();

    assertEquals(object("who", "Lin", "row", 1L), fixture.actions.getFirst().context());
    assertEquals("pick", fixture.actions.getFirst().sourceComponentId());
    assertNotEquals(action(buttons.get(0)), action(buttons.get(1)));
  }

  @Test
  void aFunctionActionRunsWithAUserGestureAndReportsNoAction() {
    ComponentNode<ComponentApi> root = button(Map.of(), open("https://example.com/a"));

    assertEquals(List.of(), fixture.opened);

    action(root).invoke();

    assertEquals(List.of(URI.create("https://example.com/a")), fixture.opened);
    assertEquals(List.of(), fixture.actions);
    assertEquals(List.of(), fixture.errors);
  }

  @Test
  void aFunctionActionThatFailsReportsItsFailure() {
    ComponentNode<ComponentApi> root = button(Map.of(), open("https://example.com/a"));
    int[] emissions = countProps(root);

    fixture.update(
        component("root", "Button", "child", "label", "action", open("javascript:alert(1)")));
    action(root).invoke();

    assertEquals(1, emissions[0]);
    assertEquals(List.of(), fixture.opened);
    assertEquals(1, fixture.errors.size());
    assertEquals(Optional.of("s"), fixture.errors.getFirst().surfaceId());
  }

  @Test
  void anActionOfADisposedNodeDoesNothing() {
    NodeResolver<ComponentApi> resolver =
        fixture.resolve(
            Map.of(),
            component("root", "Button", "child", "label", "action", open("https://example.com/a")),
            component("label", "Text", "text", "Go"));
    NodeAction action = action(resolver.rootNode().get().orElseThrow());

    resolver.dispose();
    action.invoke();

    assertEquals(List.of(), fixture.opened);
  }
}
