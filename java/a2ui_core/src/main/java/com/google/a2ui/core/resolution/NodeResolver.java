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

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.common.EventListener;
import com.google.a2ui.core.common.Reactive;
import com.google.a2ui.core.common.ReadonlySignal;
import com.google.a2ui.core.common.Signal;
import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiRecursionError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.ErrorFields;
import com.google.a2ui.core.resolution.ComponentNode.ChildKey;
import com.google.a2ui.core.state.ComponentModel;
import com.google.a2ui.core.state.SurfaceComponentsModel;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Makes a live tree of {@link ComponentNode} objects from the flat components and the data of one
 * surface.
 *
 * <p>A renderer makes one resolver for each surface. It shows {@link #rootNode()} and subscribes
 * each view to the properties of its node. Then the resolver follows the surface. It makes, updates
 * and disposes nodes when the components and the data change. It evaluates a binding again when the
 * data that the binding read changes.
 *
 * <p>These references resolve to a placeholder node: a component that the surface does not have at
 * this time, a component of an unknown type, and a cycle of references. Thus a surface that is not
 * complete has a tree. The resolver reports an unknown type, a cycle and a binding that it cannot
 * evaluate to the {@code onError} of the surface. Such a binding resolves to null.
 *
 * <p>A resolver is not thread-safe. Use it on the thread that owns its surface.
 *
 * @param <C> the type of the components of the catalogs
 */
public final class NodeResolver<C extends ComponentApi> {
  private static final String ROOT_PATH = "/";
  private static final int MAX_DEPTH = 100;

  /** The characters of an ID or of a path that have a meaning in an instance ID. */
  private static final String ID_ESCAPES = "~#[]>@";

  private final SurfaceModel<C> surface;
  private final Signal<Optional<ComponentNode<C>>> root = new Signal<>(Optional.empty());
  private final Map<String, Set<ComponentNode<C>>> nodesByComponent = new HashMap<>();
  private final Map<Catalog<?, ?>, PropertyBehaviors> behaviors = new HashMap<>();
  private final Set<String> reportedStructure = new HashSet<>();
  private final List<Subscription> subscriptions = new ArrayList<>();
  private boolean disposed;

  /** Receives each node immediately after its creation, or null. Tests use it to count events. */
  private EventListener<ComponentNode<C>> nodeCreated;

  /**
   * Makes a resolver, resolves the tree of the surface and starts to follow the surface.
   *
   * @param surface the surface whose components and data give the tree
   */
  public NodeResolver(SurfaceModel<C> surface) {
    this.surface = Objects.requireNonNull(surface, "surface");
    SurfaceComponentsModel components = surface.componentsModel();
    subscriptions.add(components.onCreated().subscribe(model -> componentChanged(model.id())));
    subscriptions.add(components.onDeleted().subscribe(this::componentChanged));
    subscriptions.add(surface.onDisposing().subscribe(model -> dispose()));
    syncRoot();
  }

  /**
   * Gets the surface that this resolver follows.
   *
   * @return the surface
   */
  public SurfaceModel<C> surface() {
    return surface;
  }

  /**
   * Gets the node of the root component of the surface.
   *
   * @return the signal of the root node. The value is empty while the surface has no root
   *     component. The signal notifies when the root arrives, when it goes away and when a
   *     different node replaces it.
   */
  public ReadonlySignal<Optional<ComponentNode<C>>> rootNode() {
    return root;
  }

  /**
   * Tells if the resolver is disposed.
   *
   * @return true after {@link #dispose()}, and after the disposal of the surface
   */
  public boolean isDisposed() {
    return disposed;
  }

  /** Disposes each node and stops to follow the surface. A second call has no effect. */
  public void dispose() {
    if (disposed) {
      return;
    }
    disposed = true;
    subscriptions.forEach(Subscription::cancel);
    subscriptions.clear();
    Optional<ComponentNode<C>> current = root.get();
    root.set(Optional.empty());
    current.ifPresent(this::disposeNode);
    root.dispose();
  }

  /** Sets the listener that receives each node immediately after its creation. */
  void onNodeCreated(EventListener<ComponentNode<C>> listener) {
    this.nodeCreated = listener;
  }

  // The resolver follows the components.

  /** Handles a component that the surface added or removed. */
  private void componentChanged(String componentId) {
    if (disposed) {
      return;
    }
    if (surface.componentsModel().get(componentId).isEmpty()) {
      reportedStructure.removeIf(key -> key.startsWith(componentId + "\u0000"));
    }
    if (componentId.equals(surface.rootId())) {
      syncRoot();
    }
    // The parent of a node fills the position of the node again.
    Set<ComponentNode<C>> parents = new LinkedHashSet<>();
    for (ComponentNode<C> node : nodesByComponent.getOrDefault(componentId, Set.of())) {
      if (node.parent != null) {
        parents.add(node.parent);
      }
    }
    parents.forEach(this::recompute);
  }

  private void syncRoot() {
    Optional<ComponentNode<C>> current = root.get();
    if (surface.componentsModel().get(surface.rootId()).isEmpty()) {
      root.set(Optional.empty());
      current.ifPresent(this::disposeNode);
      return;
    }
    if (current.isPresent() && isCurrent(current.get())) {
      return;
    }
    ComponentNode<C> created =
        createNode(surface.rootId(), ROOT_PATH, null, 1, ComponentNode.NO_INDEX);
    root.set(Optional.of(created));
    current.ifPresent(this::disposeNode);
  }

  @SuppressWarnings("unchecked")
  private C apiOf(ComponentModel model) {
    // The catalog of a component is a catalog of the surface. Thus its entries have the type C.
    return ((Catalog<C, ?>) model.catalog()).components().get(model.type());
  }

  /** Gets the state of the node that a reference resolves to at this time. */
  private NodeState stateOf(String componentId, String dataPath, ComponentNode<C> parent) {
    int depth = 0;
    for (ComponentNode<C> ancestor = parent; ancestor != null; ancestor = ancestor.parent) {
      if (ancestor.componentId().equals(componentId) && ancestor.dataPath().equals(dataPath)) {
        return NodeState.CYCLIC;
      }
      if (++depth > MAX_DEPTH) {
        return NodeState.CYCLIC;
      }
    }
    Optional<ComponentModel> model = surface.componentsModel().get(componentId);
    if (model.isEmpty()) {
      return NodeState.PENDING;
    }
    return apiOf(model.get()) == null ? NodeState.UNKNOWN_TYPE : NodeState.RESOLVED;
  }

  /** Tells if a node is the node that its position resolves to at this time. */
  private boolean isCurrent(ComponentNode<C> node) {
    if (node.isDisposed()
        || node.state() != stateOf(node.componentId(), node.dataPath(), node.parent)) {
      return false;
    }
    // A cyclic node and a pending node follow no component.
    return node.model == null
        || node.model == surface.componentsModel().get(node.componentId()).orElse(null);
  }

  private ComponentNode<C> createNode(
      String componentId, String dataPath, ComponentNode<C> parent, int occurrence, int index) {
    NodeState state = stateOf(componentId, dataPath, parent);
    ComponentModel held = surface.componentsModel().get(componentId).orElse(null);
    // A cyclic node shows nothing of its component. Thus it does not follow the component.
    ComponentModel model = state == NodeState.CYCLIC ? null : held;
    ComponentNode<C> node =
        new ComponentNode<>(
            instanceId(componentId, dataPath, occurrence),
            componentId,
            held == null ? ComponentNode.PLACEHOLDER_TYPE : held.type(),
            dataPath,
            state,
            state == NodeState.RESOLVED ? apiOf(model) : null,
            parent,
            model,
            index);
    nodesByComponent.computeIfAbsent(componentId, key -> new LinkedHashSet<>()).add(node);
    reportStructure(node);
    if (model != null) {
      node.modelSubscription =
          model
              .onUpdated()
              .subscribe(updated ->
                  // One update of a component changes the two signals in one batch.
                  Reactive.batch(() -> {
                    node.publishMetadata(updated.metadata());
                    recompute(node);
                  }));
    }
    recompute(node);
    if (nodeCreated != null) {
      nodeCreated.onEvent(node);
    }
    return node;
  }

  /**
   * Reports an unknown type or a cycle to the surface. The resolver reports a problem one time for
   * each component at each data scope, until the problem goes away.
   */
  private void reportStructure(ComponentNode<C> node) {
    String key = node.componentId() + "\u0000" + node.dataPath();
    switch (node.state()) {
      case RESOLVED -> reportedStructure.remove(key);
      case PENDING -> {}
      case UNKNOWN_TYPE -> {
        if (reportedStructure.add(key)) {
          surface.dispatchError(
              ErrorFields.errorCode(
                  new A2uiValidationError(
                      "Component '"
                          + node.componentId()
                          + "' has unknown type '"
                          + node.type()
                          + "'."),
                  "UNKNOWN_COMPONENT_TYPE"));
        }
      }
      case CYCLIC -> {
        if (reportedStructure.add(key)) {
          surface.dispatchError(
              ErrorFields.errorCode(
                  new A2uiRecursionError(
                      "Component '"
                          + node.componentId()
                          + "' is referenced from inside itself at data path '"
                          + node.dataPath()
                          + "'."),
                  "CYCLIC_REFERENCE"));
        }
      }
    }
  }

  private void disposeNode(ComponentNode<C> node) {
    if (node.isDisposed()) {
      return;
    }
    List<ComponentNode<C>> children = List.copyOf(node.children.values());
    node.children = Map.of();
    children.forEach(this::disposeNode);
    Set<ComponentNode<C>> nodes = nodesByComponent.get(node.componentId());
    if (nodes != null) {
      nodes.remove(node);
      if (nodes.isEmpty()) {
        nodesByComponent.remove(node.componentId());
      }
    }
    node.destroy();
  }

  // The resolver resolves one node.

  /** The values that one resolution of a node collects. */
  private final class Pass {
    private final ComponentNode<C> node;
    private final Set<String> reads = new LinkedHashSet<>();
    private final DataContext context;
    private final Map<String, Integer> occurrences = new HashMap<>();
    private final Map<ChildKey, ComponentNode<C>> children = new LinkedHashMap<>();
    private final Set<String> errors = new LinkedHashSet<>();

    private Pass(ComponentNode<C> node) {
      this.node = node;
      this.context = DataContext.tracking(surface, node.dataPath(), node.index(), reads);
    }
  }

  /** Resolves the properties of a node again and publishes them if they changed. */
  private void recompute(ComponentNode<C> node) {
    if (disposed || node.isDisposed() || node.state() != NodeState.RESOLVED) {
      return;
    }
    if (node.computing) {
      // A listener of this node changed a value that the node reads. Thus resolve one more time.
      node.stale = true;
      return;
    }
    node.computing = true;
    try {
      do {
        node.stale = false;
        resolveOnce(node);
      } while (node.stale && !node.isDisposed());
    } finally {
      node.computing = false;
    }
  }

  private void resolveOnce(ComponentNode<C> node) {
    ComponentModel model = node.model;
    Pass pass = new Pass(node);
    PropertyBehavior.Structure structure =
        behaviors.computeIfAbsent(model.catalog(), PropertyBehaviors::new).component(model.type());

    Map<String, Object> props = new LinkedHashMap<>();
    model
        .properties()
        .forEach((name, raw) -> props.put(name, resolve(pass, raw, structure.member(name), name)));

    Map<ChildKey, ComponentNode<C>> previous = node.children;
    node.children = pass.children;
    previous.forEach((key, child) -> {
      if (pass.children.get(key) != child) {
        disposeNode(child);
      }
    });
    watch(node, pass.reads);
    node.reportedErrors = pass.errors;
    node.publish(new NodeProps<>(props));
  }

  /** Subscribes a node to the data paths that it read, and removes its other subscriptions. */
  private void watch(ComponentNode<C> node, Set<String> reads) {
    if (node.isDisposed()) {
      return;
    }
    node.dataSubscriptions
        .entrySet()
        .removeIf(entry -> {
          if (reads.contains(entry.getKey())) {
            return false;
          }
          entry.getValue().cancel();
          return true;
        });
    for (String path : reads) {
      node.dataSubscriptions.computeIfAbsent(
          path, key -> surface.dataModel().subscribe(key, value -> recompute(node)));
    }
  }

  /**
   * Resolves a value.
   *
   * @param where the location of the value in the component, for the key of a reported error
   */
  private Object resolve(Pass pass, Object raw, PropertyBehavior behavior, String where) {
    return switch (behavior) {
      case PropertyBehavior.Static _ -> raw;
      case PropertyBehavior.Dynamic dynamic -> binding(pass, raw, dynamic.bool(), where);
      case PropertyBehavior.Action _ -> raw;
      case PropertyBehavior.Child _ ->
          raw instanceof String id ? child(pass, id, pass.node.dataPath(), pass.node.index) : raw;
      case PropertyBehavior.ChildList _ -> childList(pass, raw, where);
      case PropertyBehavior.Structure structure -> {
        if (!(raw instanceof Map<?, ?> object)) {
          yield raw;
        }
        Map<String, Object> members = new LinkedHashMap<>();
        object.forEach((key, value) -> {
          String name = String.valueOf(key);
          members.put(name, resolve(pass, value, structure.member(name), where + "/" + name));
        });
        yield Collections.unmodifiableMap(members);
      }
      case PropertyBehavior.Sequence sequence -> {
        if (!(raw instanceof List<?> list)) {
          yield raw;
        }
        List<Object> items = new ArrayList<>(list.size());
        for (int position = 0; position < list.size(); position++) {
          items.add(resolve(pass, list.get(position), sequence.element(), where + "/" + position));
        }
        yield Collections.unmodifiableList(items);
      }
    };
  }

  private ResolvedBinding binding(Pass pass, Object raw, boolean bool, String where) {
    Object resolved = evaluate(pass, raw, where);
    Object value = bool ? Coercion.toBoolean(resolved) : resolved;
    if (raw instanceof Map<?, ?> object && object.get("@path") instanceof String path) {
      return new WritableBinding(value, pass.context.resolvePath(path), surface.dataModel(), bool);
    }
    return new ResolvedBinding(value);
  }

  /**
   * Evaluates a dynamic value. If the evaluation fails, the result is null and the resolver reports
   * the failure to the surface. It reports the same failure of the same location again only after
   * an evaluation of the node without that failure.
   */
  private Object evaluate(Pass pass, Object raw, String where) {
    try {
      return pass.context.resolveDynamicValue(raw);
    } catch (A2uiError error) {
      report(pass, where, error);
      return null;
    }
  }

  private void report(Pass pass, String where, A2uiError error) {
    String key = where + "\u0000" + error.getMessage();
    pass.errors.add(key);
    if (!pass.node.reportedErrors.contains(key)) {
      surface.dispatchError(error);
    }
  }

  private ComponentNode<C> child(Pass pass, String componentId, String dataPath, int index) {
    int occurrence = pass.occurrences.merge(componentId + "\u0000" + dataPath, 1, Integer::sum);
    ChildKey key = new ChildKey(componentId, dataPath, occurrence);
    ComponentNode<C> existing = pass.node.children.get(key);
    ComponentNode<C> node =
        existing != null && isCurrent(existing)
            ? existing
            : createNode(componentId, dataPath, pass.node, occurrence, index);
    pass.children.put(key, node);
    return node;
  }

  private Object childList(Pass pass, Object raw, String where) {
    ComponentNode<C> node = pass.node;
    if (raw instanceof List<?> ids) {
      List<Object> children = new ArrayList<>(ids.size());
      for (Object item : ids) {
        children.add(
            item instanceof String id ? child(pass, id, node.dataPath(), node.index) : item);
      }
      return Collections.unmodifiableList(children);
    }
    if (!(raw instanceof Map<?, ?> template
        && template.get("componentId") instanceof String componentId
        && template.get("path") instanceof String path)) {
      return raw;
    }
    // The template makes one node for each item of the list, at the data scope of the item.
    String listPath;
    Object items;
    try {
      listPath = pass.context.resolvePath(path);
      items = pass.context.get(listPath);
    } catch (A2uiError error) {
      report(pass, where, error);
      return List.of();
    }
    if (!(items instanceof List<?> list)) {
      return List.of();
    }
    String prefix = listPath.equals(ROOT_PATH) ? ROOT_PATH : listPath + "/";
    List<Object> children = new ArrayList<>(list.size());
    for (int position = 0; position < list.size(); position++) {
      children.add(child(pass, componentId, prefix + position, position));
    }
    return Collections.unmodifiableList(children);
  }

  // Instance IDs.

  private static String escape(String part) {
    StringBuilder escaped = new StringBuilder(part.length());
    for (int i = 0; i < part.length(); i++) {
      char character = part.charAt(i);
      int code = ID_ESCAPES.indexOf(character);
      if (code < 0) {
        escaped.append(character);
      } else {
        escaped.append('~').append(code);
      }
    }
    return escaped.toString();
  }

  /**
   * Makes the name of a component at a data scope. The name is different for each child of one
   * parent that has the same component and the same data scope.
   */
  private static String instanceId(String componentId, String dataPath, int occurrence) {
    String base =
        dataPath.equals(ROOT_PATH)
            ? escape(componentId)
            : escape(componentId) + "-[" + escape(dataPath) + "]";
    return occurrence > 1 ? base + "#" + occurrence : base;
  }
}
