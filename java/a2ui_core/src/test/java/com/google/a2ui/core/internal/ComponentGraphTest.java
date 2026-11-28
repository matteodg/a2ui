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

package com.google.a2ui.core.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.exceptions.A2uiErrorDetail;
import com.google.a2ui.core.exceptions.A2uiIntegrityError;
import com.google.a2ui.core.exceptions.A2uiRecursionError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.CatalogSchemaIndex.ChildReference;
import com.google.a2ui.core.internal.ComponentGraph.Node;
import com.google.a2ui.core.validation.ValidationConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ComponentGraphTest {
  private static final ValidationConfig STRICT = ValidationConfig.STRICT;

  @Test
  void aTreeBelowTheRootIsAccepted() {
    Map<String, Node> nodes =
        graph(node("root", "a", "b"), node("a", "c"), node("b", "c"), node("c"));

    assertDoesNotThrow(() -> ComponentGraph.validate(nodes, "root", STRICT));
  }

  @Test
  void aMissingRootIsRejectedUnlessTheConfigurationAcceptsIt() {
    Map<String, Node> nodes = graph(node("a"));

    A2uiIntegrityError error =
        assertThrows(
            A2uiIntegrityError.class, () -> ComponentGraph.validate(nodes, "root", STRICT));

    assertEquals("Missing root component: no component has the id 'root'.", error.getMessage());
    assertDoesNotThrow(
        () -> ComponentGraph.validate(nodes, "root", STRICT.withAllowMissingRoot(true)));
  }

  @Test
  void aDanglingReferenceIsRejectedUnlessTheConfigurationAcceptsIt() {
    Map<String, Node> nodes = graph(node("root", "gone"));

    A2uiIntegrityError error =
        assertThrows(
            A2uiIntegrityError.class, () -> ComponentGraph.validate(nodes, "root", STRICT));

    assertEquals(
        "Dangling reference: component 'root' references non-existent component 'gone' in field"
            + " 'children[0]'.",
        error.getMessage());
    assertDoesNotThrow(
        () -> ComponentGraph.validate(nodes, "root", STRICT.withAllowDanglingReferences(true)));
  }

  @Test
  void anOrphanIsRejectedUnlessTheConfigurationAcceptsIt() {
    Map<String, Node> nodes = graph(node("root"), node("lonely"));

    A2uiIntegrityError error =
        assertThrows(
            A2uiIntegrityError.class, () -> ComponentGraph.validate(nodes, "root", STRICT));

    assertEquals(
        "Component 'lonely' is not reachable from 'root', the root component.", error.getMessage());
    assertDoesNotThrow(
        () -> ComponentGraph.validate(nodes, "root", STRICT.withAllowOrphanComponents(true)));
    // Without a root, no component is an orphan.
    assertDoesNotThrow(() ->
        ComponentGraph.validate(graph(node("lonely")), "root", STRICT.withAllowMissingRoot(true)));
  }

  @Test
  void aCycleIsAlwaysRejected() {
    Map<String, Node> selfReference = graph(node("root", "root"));
    Map<String, Node> cycle = graph(node("root", "a"), node("a", "b"), node("b", "a"));
    Map<String, Node> apartFromTheRoot = graph(node("root"), node("x", "y"), node("y", "x"));

    A2uiIntegrityError self =
        assertThrows(
            A2uiIntegrityError.class,
            () -> ComponentGraph.validate(selfReference, "root", ValidationConfig.RELAXED));
    A2uiIntegrityError circular =
        assertThrows(
            A2uiIntegrityError.class,
            () -> ComponentGraph.validate(cycle, "root", ValidationConfig.RELAXED));

    assertEquals("Self-reference detected: component 'root' references itself.", self.getMessage());
    assertEquals(Optional.of("RECURSION_LIMIT_EXCEEDED"), self.errorCode());
    assertEquals(Optional.of("RECURSION_LIMIT_EXCEEDED"), circular.errorCode());
    assertEquals(
        "Circular component reference. Circular reference detected: a -> b -> a",
        circular.getMessage());
    assertThrows(
        A2uiIntegrityError.class,
        () -> ComponentGraph.validate(apartFromTheRoot, "root", ValidationConfig.RELAXED));
  }

  @Test
  void aComponentCanBeAtTheLargestDepthAndNotBelowIt() {
    assertDoesNotThrow(
        () -> ComponentGraph.validate(chain(ComponentGraph.MAX_DEPTH), "root", STRICT));

    A2uiRecursionError error =
        assertThrows(
            A2uiRecursionError.class,
            () -> ComponentGraph.validate(chain(ComponentGraph.MAX_DEPTH + 1), "root", STRICT));

    assertEquals(
        "Global recursion limit exceeded: component 'c51' is deeper than 50 levels.",
        error.getMessage());
    assertEquals(Optional.empty(), error.errorCode());
  }

  @Test
  void theAllowedParentsOfAChildAreExamined() {
    ComponentApi item = new ComponentApi("Item", Map.of("allowedParents", List.of("Menu")));
    Map<String, Node> inAMenu = graph(typed("root", "Menu", null, "i"), typed("i", "Item", item));
    Map<String, Node> inABox = graph(typed("root", "Box", null, "i"), typed("i", "Item", item));
    Map<String, Node> atTheRoot = graph(typed("root", "Item", item));

    assertDoesNotThrow(() -> ComponentGraph.validate(inAMenu, "root", STRICT));
    A2uiValidationError box =
        assertThrows(
            A2uiValidationError.class, () -> ComponentGraph.validate(inABox, "root", STRICT));
    A2uiValidationError root =
        assertThrows(
            A2uiValidationError.class, () -> ComponentGraph.validate(atTheRoot, "root", STRICT));

    assertEquals(
        "Component 'i' (Item) cannot be placed under parent 'root' (Box). Allowed parents:"
            + " ['Menu'].",
        box.getMessage());
    assertEquals(
        "Component 'root' (Item) cannot be placed under parent 'Surface' (Surface). Allowed"
            + " parents: ['Menu'].",
        root.getMessage());
    assertEquals(Optional.of("UNALLOWED_PARENT"), box.errorCode());
  }

  @Test
  void theAllowedChildrenOfAParentAreExamined() {
    ComponentApi menu = new ComponentApi("Menu", Map.of("allowedChildren", List.of("Item")));
    Map<String, Node> nodes = graph(typed("root", "Menu", menu, "t"), typed("t", "Text", null));

    A2uiValidationError error =
        assertThrows(
            A2uiValidationError.class,
            () -> ComponentGraph.validate(nodes, "root", ValidationConfig.RELAXED));

    assertEquals(
        "Container 'root' (Menu) cannot contain child 't' (Text). Allowed children: ['Item'].",
        error.getMessage());
    assertEquals(Optional.of("UNALLOWED_CHILD"), error.errorCode());
  }

  @Test
  void aCompositionErrorHasThePathOfTheComponentInTheUpdate() {
    ComponentApi item = new ComponentApi("Item", Map.of("allowedParents", List.of("Menu")));
    ComponentApi menu = new ComponentApi("Menu", Map.of("allowedChildren", List.of("Item")));
    List<ChildReference> tab = List.of(new ChildReference("i", "tabs[0].child"));
    Map<String, Node> parent =
        graph(
            new Node("root", "Box", null, tab, OptionalInt.of(0)),
            new Node("i", "Item", item, List.of(), OptionalInt.of(1)));
    Map<String, Node> child =
        graph(
            new Node("root", "Menu", menu, tab, OptionalInt.of(0)),
            new Node("i", "Text", null, List.of(), OptionalInt.of(1)));

    A2uiValidationError unallowedParent =
        assertThrows(
            A2uiValidationError.class, () -> ComponentGraph.validate(parent, "root", STRICT));
    A2uiValidationError unallowedChild =
        assertThrows(
            A2uiValidationError.class, () -> ComponentGraph.validate(child, "root", STRICT));

    assertEquals(Optional.of("/components/1"), unallowedParent.path());
    assertEquals(
        List.of(
            new A2uiErrorDetail("/components/1", "UNALLOWED_PARENT", unallowedParent.getMessage())),
        unallowedParent.details());
    assertEquals(Optional.of("/components/0/tabs/0/child"), unallowedChild.path());
    assertEquals(
        List.of(
            new A2uiErrorDetail(
                "/components/0/tabs/0/child", "UNALLOWED_CHILD", unallowedChild.getMessage())),
        unallowedChild.details());
  }

  @Test
  void aCompositionErrorOfAComponentThatIsNotInTheUpdateHasThePathOfTheMessage() {
    ComponentApi item = new ComponentApi("Item", Map.of("allowedParents", List.of("Menu")));
    Map<String, Node> nodes = graph(typed("root", "Box", null, "i"), typed("i", "Item", item));

    A2uiValidationError error =
        assertThrows(
            A2uiValidationError.class, () -> ComponentGraph.validate(nodes, "root", STRICT));

    assertEquals(Optional.of("/"), error.path());
  }

  /** Makes a chain of components. The last component is at the given depth below the root. */
  private static Map<String, Node> chain(int depth) {
    List<Node> nodes = new ArrayList<>();
    nodes.add(node("root", "c1"));
    for (int level = 1; level < depth; level++) {
      nodes.add(node("c" + level, "c" + (level + 1)));
    }
    nodes.add(node("c" + depth));
    return graph(nodes.toArray(Node[]::new));
  }

  private static Node node(String id, String... children) {
    return typed(id, "Box", null, children);
  }

  private static Node typed(String id, String type, ComponentApi api, String... children) {
    List<ChildReference> references = new ArrayList<>();
    for (int index = 0; index < children.length; index++) {
      references.add(new ChildReference(children[index], "children[" + index + "]"));
    }
    return new Node(id, type, api, references);
  }

  private static Map<String, Node> graph(Node... nodes) {
    Map<String, Node> byId = new LinkedHashMap<>();
    for (Node node : nodes) {
      byId.put(node.id(), node);
    }
    return byId;
  }
}
