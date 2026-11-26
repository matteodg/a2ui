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
import static com.google.a2ui.core.resolution.NodeFixture.path;
import static com.google.a2ui.core.resolution.NodeFixture.template;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.catalog.ComponentApi;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** The behavior of the node resolver for the list templates. */
class NodeResolverTemplateTest {
  private final NodeFixture fixture = new NodeFixture();

  private static List<ComponentNode<ComponentApi>> children(ComponentNode<ComponentApi> node) {
    return node.props().get().children("children");
  }

  private static Object text(ComponentNode<ComponentApi> node) {
    return node.props().get().value("text");
  }

  @Test
  void aTemplateMakesOneNodeForEachItemAtTheScopeOfTheItem() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of("people", List.of(Map.of("name", "Ada"), Map.of("name", "Lin"))),
            component("root", "Column", "children", template("nameField", "/people")),
            component("nameField", "TextField", "label", "Name", "value", path("name")));
    List<ComponentNode<ComponentApi>> fields = children(root);
    int[] onRoot = countProps(root);
    int[] onFirst = countProps(fields.get(0));
    int[] onSecond = countProps(fields.get(1));

    assertEquals(
        List.of("/people/0", "/people/1"), fields.stream().map(ComponentNode::dataPath).toList());
    assertEquals(OptionalInt.of(1), fields.get(1).index());
    assertEquals("Lin", fields.get(1).props().get().value("value"));
    assertEquals("nameField-[/people/0]", fields.get(0).instanceId());

    WritableBinding first = fields.get(0).props().get().writableBinding("value").orElseThrow();
    assertEquals("/people/0/name", first.path());
    first.set("Grace");

    assertEquals("Grace", fixture.surface().dataModel().get("/people/0/name"));
    assertEquals("Grace", fields.get(0).props().get().value("value"));
    assertEquals(List.of(0, 1, 0), List.of(onRoot[0], onFirst[0], onSecond[0]));
  }

  @Test
  void aTemplateFollowsTheLengthOfItsList() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of("people", List.of("Ada", "Lin")),
            component("root", "Column", "children", template("name", "/people")),
            component("name", "Text", "text", path("")));
    List<ComponentNode<ComponentApi>> before = children(root);
    int[] onRoot = countProps(root);

    fixture.setData("/people", List.of("Ada", "New", "Lin"));

    List<ComponentNode<ComponentApi>> grown = children(root);
    assertEquals(1, onRoot[0]);
    assertEquals(List.of("Ada", "New", "Lin"), grown.stream().map(node -> text(node)).toList());
    assertSame(before.get(0), grown.get(0));
    assertSame(before.get(1), grown.get(1));
    assertEquals("/people/2", grown.get(2).dataPath());

    fixture.setData("/people", List.of("Ada"));

    assertEquals(List.of(before.get(0)), children(root));
    assertTrue(grown.get(1).isDisposed());
    assertTrue(grown.get(2).isDisposed());
    assertFalse(before.get(0).isDisposed());

    fixture.setData("/people", "no list");
    assertEquals(List.of(), children(root));
  }

  @Test
  void aTemplateItemKnowsItsPositionAndItsChildrenInheritIt() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of("tasks", List.of(Map.of("title", "Buy milk"), Map.of("title", "Pay bills"))),
            component("root", "List", "children", template("row", "/tasks")),
            component("row", "Row", "children", List.of("number", "title")),
            component(
                "number", "Text", "text", call("formatString", "value", "#${@index(offset: 1)}")),
            component("title", "Text", "text", path("title")));
    List<ComponentNode<ComponentApi>> rows = children(root);

    assertEquals("#1", text(children(rows.get(0)).get(0)));
    assertEquals("#2", text(children(rows.get(1)).get(0)));
    assertEquals("Pay bills", text(children(rows.get(1)).get(1)));
    assertEquals("/tasks/1", children(rows.get(1)).get(1).dataPath());
    assertEquals(OptionalInt.of(1), children(rows.get(1)).get(1).index());
  }

  @Test
  void aTemplateInATemplateReadsItsListFromTheItemOfTheOuterTemplate() {
    List<Object> groups =
        List.of(
            Map.of("name", "A", "members", List.of("Ada", "Lin")),
            Map.of("name", "B", "members", List.of("Grace")));
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of("groups", groups),
            component("root", "Column", "children", template("group", "/groups")),
            component("group", "Column", "children", template("member", "members")),
            component("member", "Text", "text", path("")));
    List<ComponentNode<ComponentApi>> first = children(children(root).get(0));
    List<ComponentNode<ComponentApi>> second = children(children(root).get(1));

    assertEquals(List.of("Ada", "Lin"), first.stream().map(node -> text(node)).toList());
    assertEquals("/groups/1/members/0", second.getFirst().dataPath());
    List<String> ids =
        List.of(first.get(0).instanceId(), first.get(1).instanceId(), second.get(0).instanceId());
    assertEquals(3, new HashSet<>(ids).size());
  }

  @Test
  void anUpdateOfTheTemplateComponentNotifiesOnEachItemAndNotOnTheParent() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of("people", List.of(Map.of("name", "Ada"), Map.of("name", "Lin"))),
            component("root", "Column", "children", template("name", "/people")),
            component("name", "Text", "text", path("name")));
    List<ComponentNode<ComponentApi>> items = children(root);
    int[] onRoot = countProps(root);
    int[] onFirst = countProps(items.get(0));
    int[] onSecond = countProps(items.get(1));

    fixture.update(component("name", "Text", "text", "Fixed"));

    assertEquals(List.of(0, 1, 1), List.of(onRoot[0], onFirst[0], onSecond[0]));
    assertEquals(items, children(root));
  }
}
