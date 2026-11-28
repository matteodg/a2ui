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

package com.google.a2ui.swing.nodes;

import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.swing.catalog.ChildViews;
import java.awt.Component;
import java.awt.Container;
import java.awt.LayoutManager2;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.swing.JComponent;

/**
 * The child reconciliation of the adapter: keeps one {@link NodeHost} for each child position of
 * one component, and keeps the Swing container equal to the list of the children.
 */
final class ChildReconciler implements ChildViews, NodeHost.Listener {
  private final SurfaceScope scope;

  /** The host of the component that has the children. */
  private final NodeHost owner;

  private final String parentComponentType;
  private final Container container;

  /** Gives the layout constraint of a child, or null if the children have no constraints. */
  private final Function<? super ComponentNode<?>, ?> constraints;

  /** The host of each child by the instance ID of its node, in the sequence of the list. */
  private Map<String, NodeHost> hosts = new LinkedHashMap<>();

  /** The constraint that the layout has for the component of each host. */
  private final Map<NodeHost, Object> applied = new IdentityHashMap<>();

  /** True during {@link #sync(List)}. Then one arrangement at the end handles all the changes. */
  private boolean syncing;

  private boolean disposed;

  ChildReconciler(
      SurfaceScope scope,
      NodeHost owner,
      String parentComponentType,
      Container container,
      Function<? super ComponentNode<?>, ?> constraints) {
    this.scope = scope;
    this.owner = owner;
    this.parentComponentType = parentComponentType;
    this.container = Objects.requireNonNull(container, "container");
    this.constraints = constraints;
  }

  @Override
  public void sync(List<? extends ComponentNode<?>> children) {
    Objects.requireNonNull(children, "children");
    if (disposed) {
      throw new IllegalStateException("The child views are disposed.");
    }
    Map<String, NodeHost> before = hosts;
    Map<String, NodeHost> next = new LinkedHashMap<>();
    syncing = true;
    try {
      for (ComponentNode<?> child : children) {
        if (next.containsKey(child.instanceId())) {
          // The resolver gives a different instance ID to each child of one parent.
          continue;
        }
        NodeHost host = before.remove(child.instanceId());
        if (host == null) {
          host = new NodeHost(scope, owner, Optional.of(parentComponentType), this);
        }
        next.put(child.instanceId(), host);
        host.bind(child);
      }
      hosts = next;
      for (NodeHost removed : before.values()) {
        applied.remove(removed);
        removed.dispose();
      }
    } finally {
      syncing = false;
    }
    arrange();
  }

  @Override
  public void sync(Optional<? extends ComponentNode<?>> child) {
    Objects.requireNonNull(child, "child");
    sync(child.isPresent() ? List.of(child.get()) : List.of());
  }

  @Override
  public Container container() {
    return container;
  }

  @Override
  public List<JComponent> components() {
    List<JComponent> components = new ArrayList<>(hosts.size());
    for (NodeHost host : hosts.values()) {
      host.component().ifPresent(components::add);
    }
    return Collections.unmodifiableList(components);
  }

  @Override
  public Optional<JComponent> componentOf(ComponentNode<?> child) {
    Objects.requireNonNull(child, "child");
    NodeHost host = hosts.get(child.instanceId());
    return host == null ? Optional.empty() : host.component();
  }

  @Override
  public void dispose() {
    if (disposed) {
      return;
    }
    disposed = true;
    Map<String, NodeHost> before = hosts;
    hosts = new LinkedHashMap<>();
    applied.clear();
    before.values().forEach(NodeHost::dispose);
    arrange();
  }

  /** Gets the hosts of the children, in the sequence of the list. */
  List<NodeHost> hosts() {
    return List.copyOf(hosts.values());
  }

  @Override
  public void componentChanged(NodeHost host) {
    if (!syncing && !disposed) {
      arrange();
    }
  }

  @Override
  public void updated(NodeHost host) {
    if (syncing || disposed || constraints == null) {
      return;
    }
    JComponent component = host.component().orElse(null);
    if (component != null && component.getParent() == container && applyConstraint(host)) {
      layoutChanged();
    }
  }

  /**
   * Makes the content of the container equal to the components of the hosts. The method changes the
   * container only at the positions that are different.
   */
  private void arrange() {
    Set<Component> wanted = Collections.newSetFromMap(new IdentityHashMap<>());
    wanted.addAll(components());
    boolean changed = false;
    for (int i = container.getComponentCount() - 1; i >= 0; i--) {
      if (!wanted.contains(container.getComponent(i))) {
        container.remove(i);
        changed = true;
      }
    }
    int index = 0;
    for (NodeHost host : hosts.values()) {
      JComponent component = host.component().orElse(null);
      if (component == null) {
        // A child that shows nothing has no slot.
        applied.remove(host);
        continue;
      }
      if (component.getParent() != container) {
        Object constraint = constraintOf(host);
        container.add(component, constraint, index);
        applied.put(host, constraint);
        changed = true;
      } else {
        if (container.getComponent(index) != component) {
          // This moves the component and does not remove it. Thus it keeps its focus.
          container.setComponentZOrder(component, index);
          changed = true;
        }
        changed |= applyConstraint(host);
      }
      index++;
    }
    if (changed) {
      layoutChanged();
    }
  }

  /** Makes the container invalid, also if it is not in a window at this time. */
  private void layoutChanged() {
    container.invalidate();
    if (container instanceof JComponent component) {
      component.revalidate();
    }
    container.repaint();
  }

  /**
   * Gives the constraint of a child to the layout, if the constraint changed.
   *
   * @return true if the layout got a new constraint
   */
  private boolean applyConstraint(NodeHost host) {
    if (constraints == null) {
      return false;
    }
    Object constraint = constraintOf(host);
    if (applied.containsKey(host) && Objects.equals(applied.get(host), constraint)) {
      return false;
    }
    applied.put(host, constraint);
    if (container.getLayout() instanceof LayoutManager2 layout) {
      layout.addLayoutComponent(host.component().orElseThrow(), constraint);
      return true;
    }
    return false;
  }

  private Object constraintOf(NodeHost host) {
    if (constraints == null) {
      return null;
    }
    try {
      return constraints.apply(host.node());
    } catch (RuntimeException e) {
      SurfaceScope.log(
          "The layout constraint failed for the component '" + host.node().componentId() + "'.", e);
      return null;
    }
  }
}
