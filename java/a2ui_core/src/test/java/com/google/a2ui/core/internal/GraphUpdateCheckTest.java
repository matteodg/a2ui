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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiIntegrityError;
import com.google.a2ui.core.exceptions.A2uiRecursionError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.CatalogSchemaIndex.ChildReference;
import com.google.a2ui.core.internal.ComponentGraph.Node;
import com.google.a2ui.core.state.ComponentModel;
import com.google.a2ui.core.state.SurfaceComponentsModel;
import com.google.a2ui.core.validation.ValidationConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GraphUpdateCheckTest {
  private static final ValidationConfig RELAXED = ValidationConfig.RELAXED;
  private static final String ROOT = "root";

  /** A catalog with composition constraints: a {@code Menu} contains only {@code Item}. */
  private static final Catalog<ComponentApi, FunctionApi> CATALOG =
      new Catalog<>(
          "urn:test:graph",
          List.of(
              new ComponentApi("Box", Map.of("type", "object")),
              new ComponentApi("Menu", Map.of("allowedChildren", List.of("Item"))),
              new ComponentApi("Item", Map.of("allowedParents", List.of("Menu")))),
          List.of(),
          null,
          ProtocolVersion.V1_0);

  private final SurfaceComponentsModel components = new SurfaceComponentsModel();

  /** The number of components of the surface that the check read. */
  private int reads;

  private final GraphUpdateCheck check =
      new GraphUpdateCheck(
          components,
          existing -> {
            reads++;
            return nodeOf(existing);
          });

  private static Node nodeOf(ComponentModel model) {
    List<?> children = (List<?>) model.properties().get("children");
    return typed(model.id(), model.type(), children.toArray(String[]::new));
  }

  private static Node node(String id, String... children) {
    return typed(id, "Box", children);
  }

  private static Node typed(String id, String type, String... children) {
    List<ChildReference> references = new ArrayList<>();
    for (int index = 0; index < children.length; index++) {
      references.add(new ChildReference(children[index], "children[" + index + "]"));
    }
    return new Node(id, type, CATALOG.components().get(type), references);
  }

  /** Writes components directly in the model of the surface, as an application can do it. */
  private void write(List<Node> nodes) {
    writeTo(components, nodes);
  }

  /** Examines an update and then writes it, as the message processor does it. */
  private void update(ValidationConfig config, Node... nodes) {
    List<Node> update = new ArrayList<>();
    for (int position = 0; position < nodes.length; position++) {
      Node node = nodes[position];
      update.add(
          new Node(node.id(), node.type(), node.api(), node.children(), OptionalInt.of(position)));
    }
    check.validate(update, ROOT, config);
    check.apply(() -> write(update));
  }

  @Test
  void aSmallUpdateDoesNotReadTheOtherComponents() {
    update(RELAXED, node(ROOT, "a", "b", "c"));
    reads = 0;

    update(RELAXED, node("a"));
    update(RELAXED, node("b", "d"));
    update(RELAXED, node("c"), node("d"));

    assertEquals(0, reads);
    assertEquals(5, components.all().size());
  }

  @Test
  void aCycleThatAnUpdateMakesIsRejectedWithTheErrorOfTheFullExamination() {
    update(RELAXED, node(ROOT, "a"));
    update(RELAXED, node("a", "b"));

    A2uiIntegrityError cycle =
        assertThrows(A2uiIntegrityError.class, () -> update(RELAXED, node("b", "a")));
    A2uiIntegrityError self =
        assertThrows(A2uiIntegrityError.class, () -> update(RELAXED, node("b", "b")));

    assertEquals(
        "Circular component reference. Circular reference detected: a -> b -> a",
        cycle.getMessage());
    assertEquals("Self-reference detected: component 'b' references itself.", self.getMessage());
    assertEquals(Optional.of("RECURSION_LIMIT_EXCEEDED"), cycle.errorCode());
    // The rejected updates changed nothing. A correct update continues.
    assertDoesNotThrow(() -> update(RELAXED, node("b")));
  }

  @Test
  void aCompositionConstraintIsExaminedWhenTheChildArrivesAfterItsParent() {
    update(RELAXED, typed(ROOT, "Menu", "first", "second"));
    update(RELAXED, typed("first", "Item"));

    A2uiValidationError child =
        assertThrows(A2uiValidationError.class, () -> update(RELAXED, typed("second", "Box")));

    assertEquals(
        "Container 'root' (Menu) cannot contain child 'second' (Box). Allowed children: ['Item'].",
        child.getMessage());
    assertEquals(Optional.of("UNALLOWED_CHILD"), child.errorCode());
  }

  @Test
  void aCompositionConstraintIsExaminedWhenTheParentChanges() {
    update(RELAXED, node(ROOT, "menu"));
    update(RELAXED, typed("menu", "Menu", "item"));
    update(RELAXED, typed("item", "Item"));

    A2uiValidationError parent =
        assertThrows(
            A2uiValidationError.class, () -> update(RELAXED, typed("menu", "Box", "item")));
    A2uiValidationError surface =
        assertThrows(A2uiValidationError.class, () -> update(RELAXED, typed(ROOT, "Item")));

    assertEquals(
        "Component 'item' (Item) cannot be placed under parent 'menu' (Box). Allowed parents:"
            + " ['Menu'].",
        parent.getMessage());
    assertEquals(Optional.of("UNALLOWED_PARENT"), surface.errorCode());
    assertEquals(Optional.of("/components/0"), surface.path());
  }

  @Test
  void aChainThatBecomesTooDeepIsRejectedAsTheFullExaminationRejectsIt() {
    update(RELAXED, node(ROOT, "c1"));
    for (int level = 1; level <= ComponentGraph.MAX_DEPTH; level++) {
      update(RELAXED, node("c" + level, "c" + (level + 1)));
    }
    assertThrows(
        A2uiRecursionError.class,
        () -> update(RELAXED, node("c" + (ComponentGraph.MAX_DEPTH + 1))));

    // The children arrive before their parents, apart from the root. The full examination does
    // not follow each chain of such components. Thus it accepts this chain, and the check also
    // accepts it.
    update(RELAXED, node("d" + ComponentGraph.MAX_DEPTH));
    for (int level = ComponentGraph.MAX_DEPTH - 1; level >= 0; level--) {
      update(RELAXED, node("d" + level, "d" + (level + 1)));
    }
    reads = 0;
    assertDoesNotThrow(() -> update(RELAXED, node("top", "d0")));
    assertTrue(reads > 0);
    // The kept facts cannot describe that graph. The check continues with full examinations.
    assertThrows(A2uiIntegrityError.class, () -> update(RELAXED, node("d" + 3, "top")));
  }

  @Test
  void aChangeOutsideTheCheckMakesTheSubsequentExaminationReadTheSurface() {
    update(RELAXED, node(ROOT, "a"));
    reads = 0;

    // The application makes a cycle apart from the root.
    write(List.of(node("x", "y"), node("y", "x")));

    A2uiIntegrityError error =
        assertThrows(A2uiIntegrityError.class, () -> update(RELAXED, node("a")));
    assertTrue(error.getMessage().startsWith("Circular component reference."));
    assertTrue(reads > 0);
  }

  @Test
  void anExaminedUpdateThatIsNotWrittenIsNotPartOfTheSurface() {
    update(RELAXED, node(ROOT, "a"));
    check.validate(List.of(node("a", "b")), ROOT, RELAXED);

    // The component `a` does not exist. Thus this is no cycle.
    assertDoesNotThrow(() -> update(RELAXED, node("b", "a")));
    assertThrows(A2uiIntegrityError.class, () -> update(RELAXED, node("a", "b")));
  }

  @Test
  void aConfigurationThatNeedsTheFullGraphGetsTheFullExamination() {
    ValidationConfig noOrphans = RELAXED.withAllowOrphanComponents(false);
    update(RELAXED, node(ROOT, "a"));
    update(RELAXED, node("a"));

    A2uiIntegrityError orphan =
        assertThrows(A2uiIntegrityError.class, () -> update(noOrphans, node("lonely")));
    A2uiIntegrityError dangling =
        assertThrows(
            A2uiIntegrityError.class,
            () -> update(RELAXED.withAllowDanglingReferences(false), node("a", "gone")));
    A2uiIntegrityError root =
        assertThrows(
            A2uiIntegrityError.class,
            () ->
                new GraphUpdateCheck(new SurfaceComponentsModel(), GraphUpdateCheckTest::nodeOf)
                    .validate(List.of(node("a")), ROOT, RELAXED.withAllowMissingRoot(false)));

    assertEquals(
        "Component 'lonely' is not reachable from 'root', the root component.",
        orphan.getMessage());
    assertTrue(dangling.getMessage().startsWith("Dangling reference:"));
    assertTrue(root.getMessage().startsWith("Missing root component:"));
  }

  /**
   * Compares the check with the full examination for sequences of random updates. Some updates are
   * not correct, some are not written, and some changes do not go through the check.
   */
  @Test
  void theResultIsTheResultOfTheFullExaminationForRandomUpdates() {
    List<String> ids = List.of(ROOT, "n0", "n1", "n2", "n3", "n4", "n5", "n6");
    List<String> types = List.of("Box", "Box", "Box", "Menu", "Item");
    List<ValidationConfig> configs =
        List.of(
            RELAXED,
            RELAXED,
            RELAXED,
            RELAXED.withAllowMissingRoot(false),
            ValidationConfig.STRICT);
    for (long seed = 1; seed <= 20; seed++) {
      Random random = new Random(seed);
      SurfaceComponentsModel model = new SurfaceComponentsModel();
      GraphUpdateCheck subject = new GraphUpdateCheck(model, GraphUpdateCheckTest::nodeOf);
      for (int step = 0; step < 200; step++) {
        List<Node> update = new ArrayList<>();
        int size = 1 + random.nextInt(2);
        for (int position = 0; position < size; position++) {
          String id = ids.get(random.nextInt(ids.size()));
          if (update.stream().anyMatch(node -> node.id().equals(id))) {
            continue;
          }
          List<String> children = new ArrayList<>();
          for (int child = random.nextInt(3); child > 0; child--) {
            children.add(ids.get(1 + random.nextInt(ids.size() - 1)));
          }
          Node made =
              typed(id, types.get(random.nextInt(types.size())), children.toArray(String[]::new));
          update.add(
              new Node(
                  made.id(),
                  made.type(),
                  made.api(),
                  made.children(),
                  OptionalInt.of(update.size())));
        }
        ValidationConfig config = configs.get(random.nextInt(configs.size()));
        String where = "seed " + seed + " step " + step;

        Map<String, Node> full = new LinkedHashMap<>();
        model.all().values().forEach(existing -> full.put(existing.id(), nodeOf(existing)));
        update.forEach(node -> full.put(node.id(), node));
        String expected = outcome(() -> ComponentGraph.validate(full, ROOT, config));
        String actual = outcome(() -> subject.validate(update, ROOT, config));
        assertEquals(expected, actual, where);

        int then = random.nextInt(10);
        if (expected == null && then < 8) {
          subject.apply(() -> writeTo(model, update));
        } else if (then == 9) {
          // A change that does not go through the check. It can make a graph that is not correct.
          writeTo(model, update);
        }
      }
    }
  }

  private static String outcome(Runnable examination) {
    try {
      examination.run();
      return null;
    } catch (A2uiError error) {
      return error.getClass().getSimpleName()
          + ": "
          + error.getMessage()
          + " "
          + error.errorCode()
          + " "
          + error.path();
    }
  }

  private static void writeTo(SurfaceComponentsModel model, List<Node> nodes) {
    for (Node node : nodes) {
      Map<String, Object> properties =
          Map.of("children", node.children().stream().map(ChildReference::id).toList());
      ComponentModel existing = model.get(node.id()).orElse(null);
      if (existing != null && existing.type().equals(node.type())) {
        existing.replaceDefinition(properties, null, null, null);
      } else {
        model.removeComponent(node.id());
        model.addComponent(
            new ComponentModel(node.id(), node.type(), CATALOG, properties, null, null, null));
      }
    }
  }
}
