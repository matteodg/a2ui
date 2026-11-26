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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.state.ComponentModel;
import com.google.a2ui.core.state.DataModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ComponentNodeTest {
  private static final ComponentApi TEXT = BasicCatalog.api().components().get("Text");
  private static final A2uiMetadata METADATA =
      new A2uiMetadata(Optional.of(Map.of("com_example_trace", "t1")));

  private static ComponentNode<ComponentApi> node(NodeState state, int index) {
    ComponentModel model =
        new ComponentModel("label", "Text", BasicCatalog.api(), Map.of(), METADATA, null, null);
    return new ComponentNode<>(
        "label-[/items/2]", "label", "Text", "/items/2", state, TEXT, null, model, index);
  }

  private static NodeProps<ComponentApi> props(Object... namesAndValues) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      values.put((String) namesAndValues[i], namesAndValues[i + 1]);
    }
    return new NodeProps<>(values);
  }

  @Test
  void aNodeKeepsItsValues() {
    ComponentNode<ComponentApi> node = node(NodeState.RESOLVED, 2);

    assertEquals("label-[/items/2]", node.instanceId());
    assertEquals("label", node.componentId());
    assertEquals("Text", node.type());
    assertEquals("/items/2", node.dataPath());
    assertEquals(OptionalInt.of(2), node.index());
    assertEquals(NodeState.RESOLVED, node.state());
    assertFalse(node.isPlaceholder());
    assertEquals(Optional.of(TEXT), node.impl());
    assertEquals(Optional.of(METADATA), node.metadata().get());
    assertEquals(Map.of(), node.props().get().asMap());
    assertNotEquals(node.id(), node(NodeState.RESOLVED, 2).id());
    assertEquals("ComponentNode(label-[/items/2]: Text, resolved)", node.toString());
  }

  @Test
  void eachStateThatIsNotResolvedIsAPlaceholder() {
    assertTrue(node(NodeState.PENDING, ComponentNode.NO_INDEX).isPlaceholder());
    assertTrue(node(NodeState.UNKNOWN_TYPE, ComponentNode.NO_INDEX).isPlaceholder());
    assertTrue(node(NodeState.CYCLIC, ComponentNode.NO_INDEX).isPlaceholder());
    assertEquals(OptionalInt.empty(), node(NodeState.PENDING, ComponentNode.NO_INDEX).index());
    assertEquals("unknown-type", NodeState.UNKNOWN_TYPE.wireValue());
  }

  @Test
  void theFirstPropertiesDoNotNotifyAndEachChangeNotifiesOneTime() {
    ComponentNode<ComponentApi> node = node(NodeState.RESOLVED, 0);
    node.publish(props("text", new ResolvedBinding("a")));
    List<NodeProps<ComponentApi>> published = new ArrayList<>();
    node.props().subscribe(published::add);

    node.publish(props("text", new ResolvedBinding("a")));
    node.publish(props("text", new ResolvedBinding("b")));

    assertEquals(List.of(props("text", new ResolvedBinding("b"))), published);
  }

  @Test
  void destroyNotifiesOneTimeAndRunsTheCleanups() {
    ComponentNode<ComponentApi> node = node(NodeState.RESOLVED, 0);
    List<String> events = new ArrayList<>();
    node.onDestroyed().subscribe(destroyed -> events.add("destroyed " + destroyed.componentId()));
    node.addCleanup(() -> events.add("cleanup"));

    node.destroy();
    node.destroy();
    node.addCleanup(() -> events.add("late cleanup"));

    assertTrue(node.isDisposed());
    assertEquals(List.of("destroyed label", "cleanup", "late cleanup"), events);
  }

  @Test
  void thePropertiesGiveTypedValues() {
    ComponentNode<ComponentApi> child = node(NodeState.RESOLVED, 0);
    NodeAction action = options -> {};
    WritableBinding writable = new WritableBinding("Ada", "/name", new DataModel(), false);
    NodeProps<ComponentApi> props =
        props(
            "value",
            writable,
            "label",
            new ResolvedBinding("Name"),
            "variant",
            "h1",
            "action",
            action,
            "child",
            child,
            "children",
            List.of(child, "not a node"),
            "tabs",
            List.of(Map.of("child", child), "not an object"),
            "style",
            Map.of("bold", true));

    assertTrue(props.has("value"));
    assertFalse(props.has("missing"));
    assertEquals(Optional.of(writable), props.binding("value"));
    assertEquals(Optional.of(writable), props.writableBinding("value"));
    assertEquals(Optional.empty(), props.writableBinding("label"));
    assertEquals(Optional.empty(), props.binding("variant"));
    assertEquals("Name", props.value("label"));
    assertEquals("h1", props.value("variant"));
    assertEquals(Optional.of(action), props.action("action"));
    assertEquals(Optional.empty(), props.action("child"));
    assertEquals(Optional.of(child), props.child("child"));
    assertEquals(Optional.empty(), props.child("children"));
    assertEquals(List.of(child), props.children("children"));
    assertEquals(List.of(), props.children("child"));
    assertEquals(1, props.objects("tabs").size());
    assertEquals(Optional.of(child), props.objects("tabs").getFirst().child("child"));
    assertEquals(true, props.object("style").orElseThrow().get("bold"));
    assertEquals(Optional.empty(), props.object("variant"));
    assertThrows(UnsupportedOperationException.class, () -> props.asMap().put("x", 1));
    assertEquals(props("a", 1L), props("a", 1L));
    assertEquals(props("a", 1L).hashCode(), props("a", 1L).hashCode());
    assertNotEquals(props("a", 1L), props("a", 2L));
  }
}
