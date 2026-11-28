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

import static com.google.a2ui.core.resolution.NodeFixture.component;
import static com.google.a2ui.core.resolution.NodeFixture.countProps;
import static com.google.a2ui.core.resolution.NodeFixture.template;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.exceptions.A2uiRecursionError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.state.ComponentModel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The behavior of the node resolver for the references that it cannot resolve. */
class NodeResolverPlaceholderTest {
  private final NodeFixture fixture = new NodeFixture();

  private static ComponentNode<ComponentApi> child(ComponentNode<ComponentApi> node) {
    return node.props().get().child("child").orElseThrow();
  }

  @Test
  void aComponentThatTheSurfaceDoesNotHaveIsAPendingNodeUntilItArrives() {
    ComponentNode<ComponentApi> root =
        fixture.root(Map.of(), component("root", "Card", "child", "greeting"));
    ComponentNode<ComponentApi> pending = child(root);
    int[] emissions = countProps(root);

    assertEquals(NodeState.PENDING, pending.state());
    assertTrue(pending.isPlaceholder());
    assertEquals(ComponentNode.PLACEHOLDER_TYPE, pending.type());
    assertEquals("greeting", pending.componentId());
    assertEquals(Optional.empty(), pending.impl());
    assertEquals(Optional.empty(), pending.metadata().get());
    assertEquals(Map.of(), pending.props().get().asMap());
    assertEquals(List.of(), fixture.errors);

    fixture.update(component("greeting", "Text", "text", "Hello"));

    ComponentNode<ComponentApi> resolved = child(root);
    assertNotSame(pending, resolved);
    assertTrue(pending.isDisposed());
    assertEquals(NodeState.RESOLVED, resolved.state());
    assertEquals(pending.instanceId(), resolved.instanceId());
    assertEquals(1, emissions[0]);
  }

  @Test
  void aComponentThatGoesAwayBecomesAPendingNode() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of(),
            component("root", "Card", "child", "greeting"),
            component("greeting", "Text", "text", "Hello"));
    ComponentNode<ComponentApi> resolved = child(root);

    fixture.surface().componentsModel().removeComponent("greeting");

    assertTrue(resolved.isDisposed());
    assertEquals(NodeState.PENDING, child(root).state());
  }

  @Test
  void aComponentOfAnUnknownTypeIsAPlaceholderThatTheResolverReportsOneTime() {
    ComponentNode<ComponentApi> root =
        fixture.root(Map.of(), component("root", "Column", "children", List.of("odd", "odd")));
    fixture
        .surface()
        .componentsModel()
        .addComponent(
            new ComponentModel(
                "odd",
                "CustomUnknownWidget",
                fixture.surface().defaultCatalog().orElseThrow(),
                Map.of("label", "Hi"),
                new A2uiMetadata(Optional.of(Map.of("com_example_trace", "t1"))),
                null,
                null));
    ComponentNode<ComponentApi> unknown = root.props().get().children("children").getFirst();

    assertEquals(NodeState.UNKNOWN_TYPE, unknown.state());
    assertEquals("CustomUnknownWidget", unknown.type());
    assertEquals(Optional.empty(), unknown.impl());
    assertEquals(Map.of(), unknown.props().get().asMap());
    assertTrue(unknown.metadata().get().isPresent());
    assertEquals(1, fixture.errors.size());
    A2uiValidationError error =
        assertInstanceOf(A2uiValidationError.class, fixture.errors.getFirst());
    assertEquals("Component 'odd' has unknown type 'CustomUnknownWidget'.", error.getMessage());
    assertEquals(Optional.of("UNKNOWN_COMPONENT_TYPE"), error.errorCode());
    assertEquals(Optional.of("s"), error.surfaceId());
  }

  @Test
  void aReferenceToAParentAtTheSameScopeIsACyclicNode() {
    ComponentNode<ComponentApi> root =
        fixture.root(Map.of(), component("root", "Card", "child", "inner"));
    fixture.put("inner", "Card", "child", "root");
    ComponentNode<ComponentApi> cyclic = child(child(root));

    assertEquals(NodeState.CYCLIC, cyclic.state());
    assertEquals("root", cyclic.componentId());
    assertEquals("Card", cyclic.type());
    assertEquals("/", cyclic.dataPath());
    assertEquals(Optional.empty(), cyclic.metadata().get());
    assertEquals(Map.of(), cyclic.props().get().asMap());
    assertEquals(1, fixture.errors.size());
    A2uiRecursionError error =
        assertInstanceOf(A2uiRecursionError.class, fixture.errors.getFirst());
    assertEquals(
        "Component 'root' is referenced from inside itself at data path '/'.", error.getMessage());
    assertEquals(Optional.of("CYCLIC_REFERENCE"), error.errorCode());

    fixture.put("inner", "Card", "child", "leaf");
    fixture.put("leaf", "Text", "text", "Hi");

    assertTrue(cyclic.isDisposed());
    assertEquals(NodeState.RESOLVED, child(child(root)).state());
    assertEquals(1, fixture.errors.size());
  }

  @Test
  void aComponentThatATemplateRepeatsInItselfIsNotACycle() {
    NodeResolver<ComponentApi> resolver =
        fixture.resolve(
            Map.of("items", List.of(Map.of("items", List.of(Map.of())))),
            component("other", "Text", "text", "Hi"));
    // The message processor does not accept a component that refers to itself.
    fixture.put("root", "Column", "children", template("root", "items"));
    ComponentNode<ComponentApi> root = resolver.rootNode().get().orElseThrow();
    ComponentNode<ComponentApi> first = root.props().get().children("children").getFirst();
    ComponentNode<ComponentApi> second = first.props().get().children("children").getFirst();

    assertEquals(NodeState.RESOLVED, first.state());
    assertEquals("/items/0", first.dataPath());
    assertEquals(NodeState.RESOLVED, second.state());
    assertEquals("/items/0/items/0", second.dataPath());
    assertEquals(List.of(), second.props().get().children("children"));
    assertEquals(List.of(), fixture.errors);
  }
}
