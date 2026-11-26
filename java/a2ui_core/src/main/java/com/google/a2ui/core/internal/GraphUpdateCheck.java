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

import com.google.a2ui.core.exceptions.A2uiIntegrityError;
import com.google.a2ui.core.exceptions.A2uiRecursionError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.CatalogSchemaIndex.ChildReference;
import com.google.a2ui.core.internal.ComponentGraph.Node;
import com.google.a2ui.core.state.ComponentModel;
import com.google.a2ui.core.state.SurfaceComponentsModel;
import com.google.a2ui.core.validation.ValidationConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Examines the component graph of one surface for each update, and does not read the full graph
 * when it is not necessary.
 *
 * <p>{@link ComponentGraph#validate(Map, String, ValidationConfig)} reads each component of the
 * surface. An agent that streams sends a surface in many small updates. If each update reads the
 * full surface, the time for the surface increases with the square of its size.
 *
 * <p>This class keeps the facts that the last examination found: the references between the
 * components, and for each component a limit for the length of the longest chain of references
 * below it. A new cycle goes through a component of the update, and only the chains through the
 * components of the update become longer. The composition constraints of a component depend on its
 * type, on its children and on its parents. Thus the class examines only the components of the
 * update and the components that refer to them.
 *
 * <p>The result is the result of the full examination. The class uses the kept facts only to show
 * that the graph is correct. If they cannot show that, the class does the full examination, which
 * throws the error. The class also does the full examination in these conditions:
 *
 * <ul>
 *   <li>The configuration does not accept dangling references or does not accept orphans. Then the
 *       examination needs the full graph.
 *   <li>The components of the surface changed in a different way than through {@link
 *       #apply(Runnable)}, for example an update with no validation configuration or a change that
 *       the application made.
 * </ul>
 *
 * <p>An instance is not thread-safe. Use it on the thread that owns its surface.
 */
public final class GraphUpdateCheck {
  private final SurfaceComponentsModel components;
  private final Function<ComponentModel, Node> nodeOf;

  /** The components of the graph that the kept facts describe, by ID. */
  private final Map<String, Node> nodes = new HashMap<>();

  /** For each ID, the components that refer to it. The ID can name no component. */
  private final Map<String, Set<String>> parents = new HashMap<>();

  /**
   * For each component, a number that is not less than the number of references in the longest
   * chain below it.
   */
  private final Map<String, Integer> heights = new HashMap<>();

  /** True if the kept facts describe the components of the surface as they are. */
  private boolean trusted;

  /** True if the kept facts describe the surface after the write of the examined update. */
  private boolean pending;

  /** True while {@link #apply(Runnable)} writes an examined update. */
  private boolean applying;

  /** True if the components changed in a different way than through {@link #apply(Runnable)}. */
  private boolean changedOutside;

  /**
   * Makes the check for the components of one surface, and starts to follow their changes.
   *
   * @param components the components of the surface
   * @param nodeOf makes the graph data of a component of the surface
   */
  public GraphUpdateCheck(
      SurfaceComponentsModel components, Function<ComponentModel, Node> nodeOf) {
    this.components = Objects.requireNonNull(components, "components");
    this.nodeOf = Objects.requireNonNull(nodeOf, "nodeOf");
    components.all().values().forEach(this::follow);
    components
        .onCreated()
        .subscribe(component -> {
          changed();
          follow(component);
        });
    components.onDeleted().subscribe(id -> changed());
  }

  private void follow(ComponentModel component) {
    component.onUpdated().subscribe(updated -> changed());
  }

  private void changed() {
    if (!applying) {
      changedOutside = true;
    }
  }

  /**
   * Examines the graph that the surface has after an update. The result is the result of {@link
   * ComponentGraph#validate(Map, String, ValidationConfig)} for the components of the surface with
   * the components of the update.
   *
   * @param update the components of the update
   * @param rootId the ID of the root component
   * @param config the problems to accept
   * @throws A2uiValidationError if the catalog of a component does not permit its parent or one of
   *     its children
   * @throws A2uiIntegrityError for a missing root, a dangling reference, an orphan or a cycle
   * @throws A2uiRecursionError if a component is deeper than {@link ComponentGraph#MAX_DEPTH}
   */
  public void validate(List<Node> update, String rootId, ValidationConfig config) {
    boolean incremental =
        trusted
            && !changedOutside
            && config.allowDanglingReferences()
            && config.allowOrphanComponents();
    // The facts change with the update. They describe the surface again after the write.
    trusted = false;
    pending = false;
    if (incremental && admits(update, rootId, config)) {
      pending = true;
      return;
    }
    changedOutside = false;
    Map<String, Node> graph = new LinkedHashMap<>();
    for (ComponentModel existing : components.all().values()) {
      graph.put(existing.id(), nodeOf.apply(existing));
    }
    update.forEach(node -> graph.put(node.id(), node));
    ComponentGraph.validate(graph, rootId, config);
    pending = describe(graph);
  }

  /**
   * Writes the update that {@link #validate(List, String, ValidationConfig)} examined last.
   *
   * @param write changes the components of the surface to the components of that update
   */
  public void apply(Runnable write) {
    applying = true;
    try {
      write.run();
    } finally {
      applying = false;
    }
    trusted = pending && !changedOutside;
    pending = false;
  }

  /**
   * Tells if the kept facts show that the graph with an update is correct. If the result is true,
   * the facts describe the graph with the update. If the result is false, the facts are not correct
   * and the graph is possibly correct.
   */
  private boolean admits(List<Node> update, String rootId, ValidationConfig config) {
    for (Node node : update) {
      for (ChildReference child : node.children()) {
        if (child.id().equals(node.id())) {
          return false;
        }
      }
    }
    update.forEach(this::put);
    if (!config.allowMissingRoot() && !nodes.containsKey(rootId)) {
      return false;
    }
    // The composition constraints. Only a pair with a component of the update can be new.
    for (Node node : update) {
      Node current = nodes.get(node.id());
      if (current.id().equals(rootId) && !ComponentGraph.permitsSurface(current)) {
        return false;
      }
      for (ChildReference reference : current.children()) {
        Node child = nodes.get(reference.id());
        if (child != null && !ComponentGraph.permits(current, child)) {
          return false;
        }
      }
      for (String parentId : parents.getOrDefault(current.id(), Set.of())) {
        if (!ComponentGraph.permits(nodes.get(parentId), current)) {
          return false;
        }
      }
    }
    // The chains. The limit of each component above a component whose limit increased also
    // increases. A cycle increases its limits to more than the permitted depth.
    Deque<String> rising = new ArrayDeque<>();
    for (Node node : update) {
      heights.put(node.id(), heightFromChildren(node));
      rising.add(node.id());
    }
    while (!rising.isEmpty()) {
      String id = rising.poll();
      int height = heights.get(id);
      if (height > ComponentGraph.MAX_DEPTH) {
        return false;
      }
      for (String parentId : parents.getOrDefault(id, Set.of())) {
        if (heights.get(parentId) <= height) {
          heights.put(parentId, height + 1);
          rising.add(parentId);
        }
      }
    }
    return true;
  }

  private int heightFromChildren(Node node) {
    int height = 0;
    for (ChildReference child : node.children()) {
      Integer below = heights.get(child.id());
      if (below != null && nodes.containsKey(child.id())) {
        height = Math.max(height, below + 1);
      }
    }
    return height;
  }

  /** Writes a component in the kept facts, in place of a component with the same ID. */
  private void put(Node node) {
    Node previous =
        nodes.put(node.id(), new Node(node.id(), node.type(), node.api(), node.children()));
    if (previous != null) {
      for (ChildReference child : previous.children()) {
        Set<String> referrers = parents.get(child.id());
        if (referrers != null) {
          referrers.remove(node.id());
        }
      }
    }
    for (ChildReference child : node.children()) {
      parents.computeIfAbsent(child.id(), key -> new HashSet<>()).add(node.id());
    }
    heights.putIfAbsent(node.id(), 0);
  }

  /**
   * Makes the kept facts from a graph that the full examination accepted.
   *
   * @return true if the facts can show that a subsequent graph is correct. The result is false if
   *     the graph has a chain that is longer than the permitted depth. The full examination can
   *     accept such a graph, because it does not follow each chain.
   */
  private boolean describe(Map<String, Node> graph) {
    nodes.clear();
    parents.clear();
    heights.clear();
    graph.values().forEach(this::put);
    heights.clear();
    for (String id : graph.keySet()) {
      if (height(id, 0) < 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Calculates the number of references in the longest chain below a component.
   *
   * @param above the number of components above this component in the chain that the method follows
   * @return the number, or -1 if a chain is longer than the permitted depth
   */
  private int height(String id, int above) {
    Integer known = heights.get(id);
    if (known != null) {
      return known;
    }
    if (above > ComponentGraph.MAX_DEPTH) {
      // The chain is too long, or it is a cycle.
      return -1;
    }
    int height = 0;
    for (ChildReference child : nodes.get(id).children()) {
      if (!nodes.containsKey(child.id())) {
        continue;
      }
      int below = height(child.id(), above + 1);
      if (below < 0) {
        return -1;
      }
      height = Math.max(height, below + 1);
    }
    if (height > ComponentGraph.MAX_DEPTH) {
      return -1;
    }
    heights.put(id, height);
    return height;
  }
}
