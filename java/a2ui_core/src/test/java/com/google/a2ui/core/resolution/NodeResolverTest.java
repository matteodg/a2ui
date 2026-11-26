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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.catalog.ComponentApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * The behavior of the node resolver for the root, the bindings and the explicit children. The
 * functions are the functions of the basic catalog with their code.
 */
class NodeResolverTest {
  private final NodeFixture fixture = new NodeFixture();

  @Test
  void theRootNodeFollowsTheRootComponent() {
    NodeResolver<ComponentApi> resolver =
        fixture.resolve(Map.of(), component("other", "Text", "text", "Not the root"));
    List<Optional<ComponentNode<ComponentApi>>> roots = new ArrayList<>();
    resolver.rootNode().subscribe(roots::add);

    assertSame(fixture.surface(), resolver.surface());
    assertEquals(Optional.empty(), resolver.rootNode().get());

    fixture.update(component("root", "Text", "text", "Hi"));
    ComponentNode<ComponentApi> root = resolver.rootNode().get().orElseThrow();
    fixture.surface().componentsModel().removeComponent("root");

    assertEquals(List.of(Optional.of(root), Optional.empty()), roots);
    assertTrue(root.isDisposed());
  }

  @Test
  void aNodeDescribesItsComponent() {
    ComponentNode<ComponentApi> root =
        fixture.root(Map.of(), component("root", "Text", "text", "Hi"));

    assertEquals("root", root.componentId());
    assertEquals("root", root.instanceId());
    assertEquals("Text", root.type());
    assertEquals("/", root.dataPath());
    assertEquals(OptionalInt.empty(), root.index());
    assertEquals(NodeState.RESOLVED, root.state());
    assertFalse(root.isPlaceholder());
    assertFalse(root.isDisposed());
    assertEquals(Optional.of(BasicCatalog.api().components().get("Text")), root.impl());
    assertEquals(Optional.empty(), root.metadata().get());
    assertTrue(root.id().startsWith("a2ui-node-"));
    assertEquals("ComponentNode(root: Text, resolved)", root.toString());
    assertEquals(List.of("text"), List.copyOf(root.props().get().asMap().keySet()));
  }

  @Test
  void aLiteralIsABindingThatIsNotWritableAndAPathIsAWritableBinding() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of("user", Map.of("name", "Ada")),
            component("root", "TextField", "label", "Name", "value", path("/user/name")));
    NodeProps<ComponentApi> props = root.props().get();

    assertEquals(new ResolvedBinding("Name"), props.get("label"));
    assertEquals(Optional.empty(), props.writableBinding("label"));
    WritableBinding value = props.writableBinding("value").orElseThrow();
    assertEquals("Ada", value.value());
    assertEquals("/user/name", value.path());

    value.set("Grace");

    assertEquals("Grace", fixture.surface().dataModel().get("/user/name"));
    assertEquals("Grace", root.props().get().value("value"));
    assertEquals("Ada", value.value());
  }

  @Test
  void aStaticPropertyStaysAsTheAgentWroteIt() {
    ComponentNode<ComponentApi> root =
        fixture.root(Map.of(), component("root", "Text", "text", "Hi", "variant", "caption"));

    assertEquals("caption", root.props().get().get("variant"));
    assertEquals("caption", root.props().get().value("variant"));
    assertEquals(Optional.empty(), root.props().get().binding("variant"));
  }

  @Test
  void aBooleanPropertyIsReadAsABoolean() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of("flag", "TRUE"),
            component("root", "CheckBox", "label", "Accept", "value", path("/flag")));

    assertEquals(true, root.props().get().value("value"));

    fixture.setData("/flag", Map.of("valid", false));
    assertEquals(false, root.props().get().value("value"));

    root.props().get().writableBinding("value").orElseThrow().set("true");
    assertEquals(true, fixture.surface().dataModel().get("/flag"));
  }

  @Test
  void aFunctionResultFollowsTheDataThatTheFunctionRead() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of("user", Map.of("name", "Ada")),
            component(
                "root", "Text", "text", call("formatString", "value", "Hello, ${/user/name}!")));
    int[] emissions = countProps(root);

    assertEquals("Hello, Ada!", root.props().get().value("text"));
    assertFalse(root.props().get().binding("text").orElseThrow().isWritable());

    fixture.setData("/user/name", "Grace");
    assertEquals("Hello, Grace!", root.props().get().value("text"));

    fixture.setData("/unrelated", 1L);
    fixture.setData("/user/name", "Grace");
    assertEquals(1, emissions[0]);
  }

  @Test
  void anUpdateOfManyPropertiesNotifiesOneTimeWithTheCompleteProperties() {
    ComponentNode<ComponentApi> root =
        fixture.root(Map.of(), component("root", "TextField", "label", "Name", "value", "Ada"));
    List<NodeProps<ComponentApi>> published = new ArrayList<>();
    root.props().subscribe(published::add);

    fixture.update(component("root", "TextField", "label", "Updated", "value", "Fixed"));

    assertEquals(1, published.size());
    assertEquals("Updated", published.getFirst().value("label"));
    assertEquals("Fixed", published.getFirst().value("value"));
  }

  @Test
  void aSingleReferenceAndAnExplicitListMountTheirChildren() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of(),
            component("root", "Column", "children", List.of("card", "line")),
            component("card", "Card", "child", "label"),
            component("label", "Text", "text", "Hi"),
            component("line", "Divider"));

    List<ComponentNode<ComponentApi>> children = root.props().get().children("children");
    ComponentNode<ComponentApi> label = children.get(0).props().get().child("child").orElseThrow();

    assertEquals(
        List.of("card", "line"), children.stream().map(ComponentNode::componentId).toList());
    assertEquals("Hi", label.props().get().value("text"));
    assertEquals("/", label.dataPath());
    assertEquals(Optional.empty(), root.props().get().child("children"));
    assertEquals(List.of(), children.get(1).props().get().children("children"));
  }

  @Test
  void aReferenceInAnObjectOfAListMountsItsChild() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of("first", "One"),
            component(
                "root",
                "Tabs",
                "tabs",
                List.of(
                    object("title", path("/first"), "child", "pane1"),
                    object("title", "Two", "child", "pane2"))),
            component("pane1", "Text", "text", "First pane"),
            component("pane2", "Text", "text", "Second pane"));

    List<NodeProps<ComponentApi>> tabs = root.props().get().objects("tabs");

    assertEquals(2, tabs.size());
    assertEquals("One", tabs.get(0).value("title"));
    assertTrue(tabs.get(0).binding("title").orElseThrow().isWritable());
    assertEquals("pane2", tabs.get(1).child("child").orElseThrow().componentId());
    assertEquals(
        "Second pane", tabs.get(1).child("child").orElseThrow().props().get().value("text"));
  }

  @Test
  void aComponentThatTwoParentsReferToHasTwoNodes() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of("greeting", "Hello"),
            component("root", "Row", "children", List.of("left", "right")),
            component("left", "Card", "child", "shared"),
            component("right", "Card", "child", "shared"),
            component("shared", "Text", "text", path("/greeting")));
    List<ComponentNode<ComponentApi>> cards = root.props().get().children("children");
    ComponentNode<ComponentApi> first = cards.get(0).props().get().child("child").orElseThrow();
    ComponentNode<ComponentApi> second = cards.get(1).props().get().child("child").orElseThrow();

    assertNotSame(first, second);
    assertNotEquals(first.id(), second.id());
    assertEquals("Hello", second.props().get().value("text"));

    fixture.surface().componentsModel().removeComponent("left");

    assertTrue(first.isDisposed());
    assertTrue(cards.get(0).isDisposed());
    assertFalse(second.isDisposed());
    fixture.setData("/greeting", "Still here");
    assertEquals("Still here", second.props().get().value("text"));
  }

  @Test
  void aComponentThatAListNamesTwoTimesHasTwoNodesWithDifferentInstanceIds() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of(),
            component("root", "Column", "children", List.of("line", "line")),
            component("line", "Divider"));
    List<ComponentNode<ComponentApi>> lines = root.props().get().children("children");

    assertNotSame(lines.get(0), lines.get(1));
    assertEquals("line", lines.get(0).instanceId());
    assertEquals("line#2", lines.get(1).instanceId());
  }

  @Test
  void aChangeOfTheSequenceOfAListKeepsTheNodes() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of(),
            component("root", "Column", "children", List.of("a", "b")),
            component("a", "Text", "text", "Alpha"),
            component("b", "Text", "text", "Beta"));
    List<ComponentNode<ComponentApi>> before = root.props().get().children("children");
    int[] emissions = countProps(root);

    fixture.update(component("root", "Column", "children", List.of("b", "a")));

    List<ComponentNode<ComponentApi>> after = root.props().get().children("children");
    assertEquals(1, emissions[0]);
    assertSame(before.get(0), after.get(1));
    assertSame(before.get(1), after.get(0));
    assertFalse(before.get(0).isDisposed());
  }

  @Test
  void aChangeOfAChildDoesNotNotifyOnTheParent() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of("name", "Ada"),
            component("root", "Card", "child", "label"),
            component("label", "Text", "text", path("/name")));
    ComponentNode<ComponentApi> label = root.props().get().child("child").orElseThrow();
    int[] onParent = countProps(root);
    int[] onChild = countProps(label);

    fixture.setData("/name", "Grace");
    fixture.update(component("label", "Text", "text", "Fixed"));

    assertEquals(0, onParent[0]);
    assertEquals(2, onChild[0]);
    assertSame(label, root.props().get().child("child").orElseThrow());
  }

  @Test
  void aChildThatAnUpdateRemovesIsDisposed() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of(),
            component("root", "Column", "children", List.of("a", "b")),
            component("a", "Text", "text", "Alpha"),
            component("b", "Text", "text", "Beta"));
    List<ComponentNode<ComponentApi>> before = root.props().get().children("children");

    fixture.update(component("root", "Column", "children", List.of("b")));

    assertTrue(before.get(0).isDisposed());
    assertFalse(before.get(1).isDisposed());
    assertEquals(List.of(before.get(1)), root.props().get().children("children"));
  }

  @Test
  void aChangeOfTheTypeOfAComponentReplacesItsNode() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of(),
            component("root", "Card", "child", "item"),
            component("item", "Text", "text", "Hello"));
    ComponentNode<ComponentApi> before = root.props().get().child("child").orElseThrow();
    int[] emissions = countProps(root);

    fixture.update(component("item", "Icon", "name", "check"));

    ComponentNode<ComponentApi> after = root.props().get().child("child").orElseThrow();
    assertNotSame(before, after);
    assertTrue(before.isDisposed());
    assertEquals("Icon", after.type());
    assertEquals(before.instanceId(), after.instanceId());
    assertEquals(1, emissions[0]);
  }

  @Test
  void theMetadataOfAComponentIsNotInTheProperties() {
    Map<String, Object> first = Map.of("extensions", Map.of("com_example_trace", "t1"));
    Map<String, Object> second = Map.of("extensions", Map.of("com_example_trace", "t2"));
    ComponentNode<ComponentApi> root =
        fixture.root(Map.of(), component("root", "Text", "text", "Hi", "@metadata", first));
    int[] onProps = countProps(root);
    List<Optional<A2uiMetadata>> published = new ArrayList<>();
    root.metadata().subscribe(published::add);

    assertEquals(
        Optional.of(new A2uiMetadata(Optional.of(Map.of("com_example_trace", "t1")))),
        root.metadata().get());
    assertFalse(root.props().get().has("@metadata"));

    fixture.update(component("root", "Text", "text", "Hi", "@metadata", second));
    assertEquals(0, onProps[0]);
    assertEquals(1, published.size());

    fixture.update(component("root", "Text", "text", "Changed"));
    assertEquals(1, onProps[0]);
    assertEquals(List.of(published.getFirst(), Optional.empty()), published);
  }

  @Test
  void disposeStopsTheResolverAndRunsTheCleanupsOneTime() {
    NodeResolver<ComponentApi> resolver =
        fixture.resolve(
            Map.of("name", "Ada"),
            component("root", "Column", "children", List.of("name")),
            component("name", "Text", "text", path("/name")));
    ComponentNode<ComponentApi> root = resolver.rootNode().get().orElseThrow();
    ComponentNode<ComponentApi> child = root.props().get().children("children").getFirst();
    int[] cleanups = {0};
    child.addCleanup(() -> cleanups[0]++);
    List<ComponentNode<ComponentApi>> destroyed = new ArrayList<>();
    child.onDestroyed().subscribe(destroyed::add);
    int[] emissions = countProps(child);

    resolver.dispose();
    resolver.dispose();

    assertTrue(resolver.isDisposed());
    assertTrue(root.isDisposed());
    assertTrue(child.isDisposed());
    assertEquals(Optional.empty(), resolver.rootNode().get());
    assertEquals(List.of(child), destroyed);
    assertEquals(1, cleanups[0]);

    child.addCleanup(() -> cleanups[0]++);
    assertEquals(2, cleanups[0]);

    fixture.setData("/name", "Grace");
    fixture.update(component("extra", "Text", "text", "Late"));
    assertEquals(0, emissions[0]);
    assertEquals("Ada", child.props().get().value("text"));
  }

  @Test
  void theDeletionOfTheSurfaceDisposesTheResolver() {
    NodeResolver<ComponentApi> resolver =
        fixture.resolve(Map.of(), component("root", "Text", "text", "Hi"));
    ComponentNode<ComponentApi> root = resolver.rootNode().get().orElseThrow();

    fixture.deleteSurface();

    assertTrue(resolver.isDisposed());
    assertTrue(root.isDisposed());
    assertNull(root.props().get().get("missing"));
  }

  @Test
  void aBindingThatCannotBeEvaluatedIsNullAndTheResolverReportsItOneTime() {
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of("name", "Ada"),
            component("root", "Column", "children", List.of("broken", "fine")),
            component(
                "broken",
                "Text",
                "text",
                call("regex", "value", path("/name"), "pattern", "(a+)+")),
            component("fine", "Text", "text", path("/name")));
    List<ComponentNode<ComponentApi>> children = root.props().get().children("children");

    assertNull(children.get(0).props().get().value("text"));
    assertTrue(children.get(0).props().get().binding("text").isPresent());
    assertEquals("Ada", children.get(1).props().get().value("text"));
    assertEquals(1, fixture.errors.size());
    assertEquals(Optional.of("s"), fixture.errors.getFirst().surfaceId());
    assertTrue(fixture.errors.getFirst().getMessage().contains("NESTED_QUANTIFIER"));

    fixture.setData("/name", "Grace");

    assertEquals(1, fixture.errors.size());
    assertEquals("Grace", children.get(1).props().get().value("text"));
  }
}
