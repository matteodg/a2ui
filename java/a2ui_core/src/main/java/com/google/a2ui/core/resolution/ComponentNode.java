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

import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.common.EventNotifier;
import com.google.a2ui.core.common.EventSource;
import com.google.a2ui.core.common.ReadonlySignal;
import com.google.a2ui.core.common.Signal;
import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.core.state.ComponentModel;
import com.google.a2ui.core.state.DataSubscription;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One component at one position of the tree of a surface, with resolved properties.
 *
 * <p>A {@code NodeResolver} makes the nodes and keeps them up to date. A view reads {@link
 * #props()} and subscribes to it. A view never resolves a binding.
 *
 * <p>There is one node for each position. Thus a component that two parents refer to has two nodes,
 * and a component that a template repeats has one node for each item.
 *
 * <p>A node is not thread-safe. Only the thread that owns the surface can use it.
 *
 * @param <C> the type of the components of the catalogs
 */
public final class ComponentNode<C extends ComponentApi> {
  /** The {@link #type()} of a node whose component the surface does not have. */
  public static final String PLACEHOLDER_TYPE = "Placeholder";

  /** The value of {@link #index} outside a list template. */
  static final int NO_INDEX = -1;

  private static final AtomicLong COUNTER = new AtomicLong();

  /**
   * Identifies a child in the children of one node: a component at a data scope.
   *
   * @param componentId the ID of the component
   * @param dataPath the data scope of the child
   * @param occurrence the position of the child in the children that have the same component and
   *     the same data scope, where the first is 1
   */
  record ChildKey(String componentId, String dataPath, int occurrence) {}

  private final String id = "a2ui-node-" + COUNTER.incrementAndGet();
  private final String instanceId;
  private final String componentId;
  private final String type;
  private final String dataPath;
  private final NodeState state;
  private final C impl;
  private final Signal<Optional<A2uiMetadata>> metadata;
  private final EventNotifier<ComponentNode<C>> destroyed = new EventNotifier<>();
  private final List<Runnable> cleanups = new ArrayList<>();
  private boolean disposed;

  /** The properties, or null before the resolver publishes the first properties. */
  private Signal<NodeProps<C>> props;

  // The values that the resolver keeps for this node.

  /** The parent of this node, or null for the root. */
  final ComponentNode<C> parent;

  /** The component that this node follows, or null if the node follows none. */
  final ComponentModel model;

  /** The position in the list of the nearest template, or {@link #NO_INDEX}. */
  final int index;

  Map<ChildKey, ComponentNode<C>> children = Map.of();
  final Map<String, DataSubscription> dataSubscriptions = new HashMap<>();
  Subscription modelSubscription;
  Set<String> reportedErrors = Set.of();
  boolean computing;
  boolean stale;

  ComponentNode(
      String instanceId,
      String componentId,
      String type,
      String dataPath,
      NodeState state,
      C impl,
      ComponentNode<C> parent,
      ComponentModel model,
      int index) {
    this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
    this.componentId = Objects.requireNonNull(componentId, "componentId");
    this.type = Objects.requireNonNull(type, "type");
    this.dataPath = Objects.requireNonNull(dataPath, "dataPath");
    this.state = Objects.requireNonNull(state, "state");
    this.impl = impl;
    this.parent = parent;
    this.model = model;
    this.index = index;
    this.metadata = new Signal<>(model == null ? Optional.empty() : model.metadata());
  }

  /**
   * Gets the name of the component at the data scope of this node.
   *
   * <p>The name is different for each child of one parent. It stays the same when a new node object
   * replaces the node at a position. Thus it is the key for the views of the children of one
   * parent, through updates and changes of the sequence.
   *
   * @return the instance ID
   */
  public String instanceId() {
    return instanceId;
  }

  /**
   * Gets an ID that is unique in the process during the life of this node object. An example of its
   * use is the name of a native component or the target of an accessibility relation.
   *
   * <p>A node that replaces this node has a different ID. Thus do not keep the ID in storage.
   *
   * @return the ID
   */
  public String id() {
    return id;
  }

  /**
   * Gets the ID of the component in the messages of the agent.
   *
   * @return the component ID
   */
  public String componentId() {
    return componentId;
  }

  /**
   * Gets the name of the component type.
   *
   * @return the name. It is {@link #PLACEHOLDER_TYPE} if the surface does not have the component.
   */
  public String type() {
    return type;
  }

  /**
   * Gets the absolute data path from which the component reads its relative paths.
   *
   * @return the path. It is {@code /}, or the path of a list item for a node that a template made.
   */
  public String dataPath() {
    return dataPath;
  }

  /**
   * Gets the position of the list item of this node.
   *
   * @return the position in the list of the nearest template, where the first item is 0. The result
   *     is empty for a node that is in no template.
   */
  public OptionalInt index() {
    return index == NO_INDEX ? OptionalInt.empty() : OptionalInt.of(index);
  }

  /**
   * Tells if the node is resolved.
   *
   * @return the state
   */
  public NodeState state() {
    return state;
  }

  /**
   * Tells if the node is a placeholder.
   *
   * @return true for each state that is not {@link NodeState#RESOLVED}
   */
  public boolean isPlaceholder() {
    return state != NodeState.RESOLVED;
  }

  /**
   * Gets the catalog entry of the type of the component.
   *
   * @return the entry, or an empty result if the node is a placeholder
   */
  public Optional<C> impl() {
    return Optional.ofNullable(impl);
  }

  /**
   * Gets the resolved properties.
   *
   * <p>The signal notifies when the properties of this node change. It notifies one time for each
   * update, with the complete new properties. A change in a child notifies only on the child.
   *
   * @return the signal of the properties. A placeholder has no properties.
   */
  public ReadonlySignal<NodeProps<C>> props() {
    if (props == null) {
      props = new Signal<>(new NodeProps<>(Map.of()));
    }
    return props;
  }

  /**
   * Gets the {@code @metadata} of the component.
   *
   * <p>The signal notifies when the metadata of the component changes. The metadata of the catalog
   * entry is not part of this value. Read it from {@link #impl()}.
   *
   * @return the signal of the metadata. The value is empty if the component has none, and for a
   *     pending or a cyclic node.
   */
  public ReadonlySignal<Optional<A2uiMetadata>> metadata() {
    return metadata;
  }

  /**
   * Tells if the resolver disposed this node.
   *
   * @return true after the disposal. The last properties stay available.
   */
  public boolean isDisposed() {
    return disposed;
  }

  /**
   * Gets the source of the event that occurs one time, when the resolver disposes this node.
   *
   * @return the event source. The event is this node.
   */
  public EventSource<ComponentNode<C>> onDestroyed() {
    return destroyed;
  }

  /**
   * Registers work to do when the resolver disposes this node. If the node is disposed, the work
   * runs immediately.
   *
   * @param cleanup the work to do
   */
  public void addCleanup(Runnable cleanup) {
    Objects.requireNonNull(cleanup, "cleanup");
    if (disposed) {
      cleanup.run();
    } else {
      cleanups.add(cleanup);
    }
  }

  /**
   * Sets the properties. The first properties are the initial state of the node. Thus the node does
   * not notify for them.
   */
  void publish(NodeProps<C> next) {
    if (props == null) {
      props = new Signal<>(next);
    } else {
      props.set(next);
    }
  }

  void publishMetadata(Optional<A2uiMetadata> next) {
    metadata.set(next);
  }

  /** Releases the node, notifies the listeners of the disposal and runs the registered work. */
  void destroy() {
    if (disposed) {
      return;
    }
    disposed = true;
    if (modelSubscription != null) {
      modelSubscription.cancel();
    }
    dataSubscriptions.values().forEach(DataSubscription::cancel);
    dataSubscriptions.clear();
    try {
      destroyed.emit(this);
      List.copyOf(cleanups).forEach(Runnable::run);
    } finally {
      cleanups.clear();
      destroyed.dispose();
      metadata.dispose();
      if (props != null) {
        props.dispose();
      }
    }
  }

  @Override
  public String toString() {
    return "ComponentNode(" + instanceId + ": " + type + ", " + state.wireValue() + ")";
  }
}
