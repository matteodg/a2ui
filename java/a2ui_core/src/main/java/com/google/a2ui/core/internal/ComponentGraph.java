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

import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.exceptions.A2uiErrorDetail;
import com.google.a2ui.core.exceptions.A2uiIntegrityError;
import com.google.a2ui.core.exceptions.A2uiRecursionError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.CatalogSchemaIndex.ChildReference;
import com.google.a2ui.core.validation.ValidationConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/** Examines the component graph of a surface before the message processor changes the surface. */
public final class ComponentGraph {
  /** The largest depth of a component below the root. The root has the depth 0. */
  public static final int MAX_DEPTH = 50;

  /** The parent type of the root component, for {@code allowedParents}. */
  private static final String SURFACE = "Surface";

  /** The protocol error code of a cycle. The protocol reports a cycle as a recursion problem. */
  private static final String RECURSION_CODE = "RECURSION_LIMIT_EXCEEDED";

  private static final String UNALLOWED_PARENT = "UNALLOWED_PARENT";
  private static final String UNALLOWED_CHILD = "UNALLOWED_CHILD";

  /**
   * One component of a graph: the data that the checks need.
   *
   * @param id the ID of the component
   * @param type the name of the component type
   * @param api the definition of the type in its catalog, or null if the catalog does not define
   *     the type
   * @param children the components that the component refers to
   * @param position the index of the component in the update that the processor examines. The
   *     result is empty if the surface had the component before the update.
   */
  public record Node(
      String id,
      String type,
      ComponentApi api,
      List<ChildReference> children,
      OptionalInt position) {
    /**
     * Makes a component that the surface had before the update.
     *
     * @param id the ID of the component
     * @param type the name of the component type
     * @param api the definition of the type in its catalog, or null
     * @param children the components that the component refers to
     */
    public Node(String id, String type, ComponentApi api, List<ChildReference> children) {
      this(id, type, api, children, OptionalInt.empty());
    }
  }

  private ComponentGraph() {}

  /**
   * Examines the graph that a surface has after an update. The checks run in this sequence:
   * composition constraints, the root, dangling references, cycles and depth, and then orphans.
   *
   * @param nodes the components of the graph by ID
   * @param rootId the ID of the root component
   * @param config the problems to accept
   * @throws A2uiValidationError if the catalog of a component does not permit its parent or one of
   *     its children. The error has the code {@code UNALLOWED_PARENT} or {@code UNALLOWED_CHILD},
   *     and the path of the component or of the reference in the update.
   * @throws A2uiIntegrityError for a missing root, a dangling reference or an orphan, unless the
   *     configuration accepts the problem. A cycle always causes this error, with the code {@code
   *     RECURSION_LIMIT_EXCEEDED}.
   * @throws A2uiRecursionError if a component is deeper than {@link #MAX_DEPTH}
   */
  public static void validate(Map<String, Node> nodes, String rootId, ValidationConfig config) {
    checkComposition(nodes, rootId);
    if (!config.allowMissingRoot() && !nodes.containsKey(rootId)) {
      throw new A2uiIntegrityError(
          "Missing root component: no component has the id '" + rootId + "'.");
    }
    if (!config.allowDanglingReferences()) {
      for (Node node : nodes.values()) {
        for (ChildReference child : node.children()) {
          if (!nodes.containsKey(child.id())) {
            throw new A2uiIntegrityError(
                "Dangling reference: component '"
                    + node.id()
                    + "' references non-existent component '"
                    + child.id()
                    + "' in field '"
                    + child.field()
                    + "'.");
          }
        }
      }
    }

    Set<String> reachable = new HashSet<>();
    walk(rootId, nodes, reachable, new ArrayList<>());
    // A cycle that the root does not reach is also a cycle.
    Set<String> seen = new HashSet<>(reachable);
    for (String id : nodes.keySet()) {
      if (!seen.contains(id)) {
        walk(id, nodes, seen, new ArrayList<>());
      }
    }
    if (!config.allowOrphanComponents() && nodes.containsKey(rootId)) {
      for (String id : nodes.keySet()) {
        if (!reachable.contains(id)) {
          throw new A2uiIntegrityError(
              "Component '" + id + "' is not reachable from '" + rootId + "', the root component.");
        }
      }
    }
  }

  /**
   * Visits a component and the components below it.
   *
   * @param done the components that a walk visited fully
   * @param path the components from the start of the walk to the parent of this component
   */
  private static void walk(
      String id, Map<String, Node> nodes, Set<String> done, List<String> path) {
    Node node = nodes.get(id);
    if (node == null) {
      return;
    }
    if (!path.isEmpty() && path.getLast().equals(id)) {
      throw ErrorFields.errorCode(
          new A2uiIntegrityError(
              "Self-reference detected: component '" + id + "' references itself."),
          RECURSION_CODE);
    }
    int start = path.indexOf(id);
    if (start >= 0) {
      List<String> cycle = new ArrayList<>(path.subList(start, path.size()));
      cycle.add(id);
      throw ErrorFields.errorCode(
          new A2uiIntegrityError(
              "Circular component reference. Circular reference detected: "
                  + String.join(" -> ", cycle)),
          RECURSION_CODE);
    }
    if (path.size() > MAX_DEPTH) {
      throw new A2uiRecursionError(
          "Global recursion limit exceeded: component '"
              + id
              + "' is deeper than "
              + MAX_DEPTH
              + " levels.");
    }
    // A different path reached this component before, and that walk visited it fully.
    if (!done.add(id)) {
      return;
    }
    path.add(id);
    for (ChildReference child : node.children()) {
      walk(child.id(), nodes, done, path);
    }
    path.removeLast();
  }

  private static void checkComposition(Map<String, Node> nodes, String rootId) {
    Node root = nodes.get(rootId);
    if (root != null) {
      checkParent(root, SURFACE, SURFACE);
    }
    for (Node parent : nodes.values()) {
      for (ChildReference reference : parent.children()) {
        Node child = nodes.get(reference.id());
        if (child == null) {
          continue;
        }
        checkParent(child, parent.id(), parent.type());
        List<String> allowedChildren =
            parent.api() == null ? null : parent.api().allowedChildren().orElse(null);
        if (allowedChildren != null && !allowedChildren.contains(child.type())) {
          throw compositionError(
              "Container '"
                  + parent.id()
                  + "' ("
                  + parent.type()
                  + ") cannot contain child '"
                  + child.id()
                  + "' ("
                  + child.type()
                  + "). Allowed children: "
                  + quoted(allowedChildren)
                  + ".",
              UNALLOWED_CHILD,
              parent.position().isPresent()
                  ? "/components/" + parent.position().getAsInt() + reference.pointer()
                  : null);
        }
      }
    }
  }

  /**
   * Tells if the composition constraints permit a component below a parent. The method examines the
   * {@code allowedParents} of the child and the {@code allowedChildren} of the parent.
   *
   * @param parent the component that refers to the child
   * @param child the component that the parent refers to
   * @return true if the two catalogs permit the pair
   */
  static boolean permits(Node parent, Node child) {
    List<String> allowedChildren =
        parent.api() == null ? null : parent.api().allowedChildren().orElse(null);
    return permitsParent(child, parent.type())
        && (allowedChildren == null || allowedChildren.contains(child.type()));
  }

  /**
   * Tells if the composition constraints permit a component as the root of a surface.
   *
   * @param root the root component
   * @return true if the catalog of the component permits the surface as its parent
   */
  static boolean permitsSurface(Node root) {
    return permitsParent(root, SURFACE);
  }

  private static boolean permitsParent(Node child, String parentType) {
    List<String> allowedParents =
        child.api() == null ? null : child.api().allowedParents().orElse(null);
    return allowedParents == null || allowedParents.contains(parentType);
  }

  private static void checkParent(Node child, String parentId, String parentType) {
    List<String> allowedParents =
        child.api() == null ? null : child.api().allowedParents().orElse(null);
    if (allowedParents != null && !allowedParents.contains(parentType)) {
      throw compositionError(
          "Component '"
              + child.id()
              + "' ("
              + child.type()
              + ") cannot be placed under parent '"
              + parentId
              + "' ("
              + parentType
              + "). Allowed parents: "
              + quoted(allowedParents)
              + ".",
          UNALLOWED_PARENT,
          child.position().isPresent() ? "/components/" + child.position().getAsInt() : null);
    }
  }

  /**
   * Makes the error of a composition constraint.
   *
   * @param code the protocol error code
   * @param at the JSON Pointer of the problem in the update, or null if the component is not in the
   *     update. Such a problem has the path of the full message.
   */
  private static A2uiValidationError compositionError(String message, String code, String at) {
    String path = at == null ? "/" : at;
    A2uiValidationError error = new A2uiValidationError(message);
    ErrorFields.errorCode(error, code);
    ErrorFields.path(error, path);
    return ErrorFields.details(error, List.of(new A2uiErrorDetail(path, code, message)));
  }

  private static String quoted(List<String> names) {
    return names.stream().map(name -> "'" + name + "'").collect(Collectors.joining(", ", "[", "]"));
  }
}
