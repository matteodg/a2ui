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

import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeState;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.swing.catalog.ChildViews;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import com.google.a2ui.swing.surface.A2uiErrorInfo;
import com.google.a2ui.swing.surface.A2uiFallbackError;
import com.google.a2ui.swing.surface.A2uiPendingInfo;
import com.google.a2ui.swing.surface.A2uiServices;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.JComponent;

/**
 * Shows the node at one position of the tree of a surface. This is the node dispatcher of the
 * adapter.
 *
 * <p>A host follows one position, which the instance ID of the node names. The node object at the
 * position can change, for example when a pending component arrives. Then the owner of the host
 * gives the new node to {@link #bind(ComponentNode)}.
 *
 * <ul>
 *   <li>For a resolved node, the host makes the view of the component implementation, subscribes to
 *       the properties of the node and updates the view after each change. The host keeps the view
 *       when a new node has the same implementation.
 *   <li>For a node in a different state, the host shows the fallback of the surface, or nothing.
 *   <li>For a resolved node whose implementation has no view, the host shows the {@code error}
 *       fallback of the surface with the error type {@code no-view}, or nothing.
 * </ul>
 *
 * <p>The Swing component of a host can thus change, and it can be absent. The host tells its
 * listener about each such change. The listener puts the component in a container.
 */
public final class NodeHost {
  /** Receives the changes of a host. */
  public interface Listener {
    /**
     * Tells that {@link NodeHost#component()} has a different result.
     *
     * @param host the host
     */
    void componentChanged(NodeHost host);

    /**
     * Tells that the host updated its view with new properties.
     *
     * @param host the host
     */
    default void updated(NodeHost host) {}
  }

  private final SurfaceScope scope;

  /** The host of the parent position, or null for the root. */
  private final NodeHost parent;

  private final Optional<String> parentComponentType;
  private final Listener listener;

  /** The node at the position, or null before the first call of {@link #bind(ComponentNode)}. */
  private ComponentNode<?> node;

  /** The implementation that made the view, or null if there is no view. */
  private SwingComponentImplementation implementation;

  private SwingComponentView view;

  /** The accessibility state of the view, or null if there is no view. */
  private Accessibility accessibility;

  /** The context of the view, or null if there is no view. */
  private HostViewContext context;

  private Subscription propsSubscription;

  /** The description that made the fallback, or null if the host shows no fallback state. */
  private Object fallbackInfo;

  private JComponent fallback;
  private JComponent component;
  private boolean disposed;

  /**
   * Makes the host of the root. It shows nothing until the first call of {@link
   * #bind(ComponentNode)}.
   *
   * @param scope the objects of the mounted surface
   * @param listener receives the changes of this host
   */
  public NodeHost(SurfaceScope scope, Listener listener) {
    this(scope, null, Optional.empty(), listener);
  }

  /**
   * Makes a host that shows nothing until the first call of {@link #bind(ComponentNode)}.
   *
   * @param scope the objects of the mounted surface
   * @param parent the host of the parent position, or null for the root
   * @param parentComponentType the type of the component whose child this position is, or an empty
   *     value for the root
   * @param listener receives the changes of this host
   */
  NodeHost(
      SurfaceScope scope,
      NodeHost parent,
      Optional<String> parentComponentType,
      Listener listener) {
    this.scope = Objects.requireNonNull(scope, "scope");
    this.parent = parent;
    this.parentComponentType = Objects.requireNonNull(parentComponentType, "parentComponentType");
    this.listener = Objects.requireNonNull(listener, "listener");
  }

  /**
   * Gets the node that this host shows.
   *
   * @return the node, or null before the first call of {@link #bind(ComponentNode)}
   */
  public ComponentNode<?> node() {
    return node;
  }

  /**
   * Gets the Swing component at this position: the component of the view, or of the fallback.
   *
   * @return the component, or an empty result if the position shows nothing
   */
  public Optional<JComponent> component() {
    return Optional.ofNullable(component);
  }

  /**
   * Gets the view of the node.
   *
   * @return the view, or an empty result if the node is not resolved
   */
  public Optional<SwingComponentView> view() {
    return Optional.ofNullable(view);
  }

  /**
   * Gets the hosts of the children that the view of this host shows, through the {@code children}
   * helpers of its context.
   *
   * @return the hosts, in the sequence of the helpers and then of the children. The list is a
   *     read-only copy.
   */
  public List<NodeHost> childHosts() {
    if (context == null) {
      return List.of();
    }
    List<NodeHost> hosts = new ArrayList<>();
    context.reconcilers.forEach(reconciler -> hosts.addAll(reconciler.hosts()));
    return Collections.unmodifiableList(hosts);
  }

  /**
   * Shows a node at this position. The node replaces the node that the host showed before.
   *
   * @param next the node to show
   * @throws IllegalStateException if the host is disposed
   */
  public void bind(ComponentNode<?> next) {
    Objects.requireNonNull(next, "next");
    if (disposed) {
      throw new IllegalStateException("The node host is disposed.");
    }
    if (next == node) {
      return;
    }
    unsubscribe();
    node = next;
    JComponent before = component;
    SwingComponentImplementation resolved = implementationOf(next);
    if (resolved == null) {
      disposeView();
      showFallback(next);
    } else {
      fallbackInfo = null;
      fallback = null;
      if (view == null || resolved != implementation) {
        disposeView();
        createView(resolved);
      }
    }
    component = view != null ? view.component() : fallback;
    if (view != null) {
      propsSubscription = scope.subscribe(next, this::update);
      update();
    }
    if (component != before) {
      listener.componentChanged(this);
    }
  }

  /**
   * Disposes the view and stops to follow the node. The host does not tell its listener. A second
   * call has no effect.
   */
  public void dispose() {
    if (disposed) {
      return;
    }
    disposed = true;
    unsubscribe();
    disposeView();
    fallbackInfo = null;
    fallback = null;
    component = null;
  }

  /** Gets the implementation of a resolved node, or null for a node that has no view. */
  private static SwingComponentImplementation implementationOf(ComponentNode<?> node) {
    if (node.state() != NodeState.RESOLVED) {
      return null;
    }
    if (node.impl().orElse(null) instanceof SwingComponentImplementation implementation) {
      // An implementation that only declares its component is handled as an error state.
      return implementation.hasView() ? implementation : null;
    }
    SurfaceScope.log(
        "The component '"
            + node.componentId()
            + "' of the type '"
            + node.type()
            + "' has no SwingComponentImplementation. The surface shows nothing for it.",
        null);
    return null;
  }

  private void createView(SwingComponentImplementation resolved) {
    HostViewContext created = new HostViewContext(resolved.name());
    try {
      SwingComponentView made = resolved.createView(created);
      Objects.requireNonNull(made, "createView returned null");
      Objects.requireNonNull(made.component(), "the view has no component");
      view = made;
      accessibility = new Accessibility();
      context = created;
      implementation = resolved;
    } catch (RuntimeException e) {
      created.dispose();
      SurfaceScope.log("The implementation of '" + resolved.name() + "' did not make a view.", e);
    }
  }

  /** Gives the current properties of the node to the view. */
  private void update() {
    try {
      view.update(node, node.props().get());
    } catch (RuntimeException e) {
      SurfaceScope.log(
          "The view of the component '" + node.componentId() + "' failed in an update.", e);
    }
    applyAccessibility(true);
    // The name that a parent infers can come from the text of this view, as for a button.
    for (NodeHost above = parent; above != null; above = above.parent) {
      above.applyAccessibility(false);
    }
    listener.updated(this);
  }

  private void applyAccessibility(boolean updated) {
    if (view == null) {
      return;
    }
    boolean hiddenBefore = accessibility.hidden();
    try {
      accessibility.apply(
          view, node.props().get(), updated, parent != null && parent.hidesContent());
    } catch (RuntimeException e) {
      SurfaceScope.log(
          "The accessibility attributes failed for the component '" + node.componentId() + "'.", e);
    }
    if (accessibility.hidden() != hiddenBefore) {
      // The attribute hidden applies to all the content below the node.
      for (NodeHost child : childHosts()) {
        child.applyAccessibility(false);
      }
    }
  }

  /**
   * Tells if assistive tools must ignore the content below this host: the node of this host, or a
   * node above it, has the attribute {@code hidden}.
   *
   * <p>The method reads the attribute from the properties of the nodes, and not from the state of
   * the hosts. A host shows its children during its first update, before it gets its own
   * attributes. Thus the state of a host above can be old at that time.
   */
  private boolean hidesContent() {
    for (NodeHost host = this; host != null; host = host.parent) {
      if (host.view != null
          && Boolean.TRUE.equals(host.node.props().get().accessibility().value("hidden"))) {
        return true;
      }
    }
    return false;
  }

  private void showFallback(ComponentNode<?> placeholder) {
    String id = placeholder.componentId();
    Object info =
        switch (placeholder.state()) {
          case PENDING -> new A2uiPendingInfo(id, parentComponentType);
          case UNKNOWN_TYPE ->
              new A2uiErrorInfo(
                  id,
                  parentComponentType,
                  new A2uiFallbackError(
                      A2uiFallbackError.UNKNOWN_TYPE, Optional.of(placeholder.type())));
          case CYCLIC ->
              new A2uiErrorInfo(
                  id,
                  parentComponentType,
                  new A2uiFallbackError(A2uiFallbackError.CYCLIC, Optional.empty()));
          // The catalog declares the component, but its implementation has no view.
          case RESOLVED ->
              placeholder.impl().orElse(null) instanceof SwingComponentImplementation
                  ? new A2uiErrorInfo(
                      id,
                      parentComponentType,
                      new A2uiFallbackError(
                          A2uiFallbackError.NO_VIEW, Optional.of(placeholder.type())))
                  : null;
        };
    if (Objects.equals(info, fallbackInfo)) {
      // The same description gives the same fallback. Thus the host keeps its component.
      return;
    }
    fallbackInfo = info;
    fallback =
        switch (info) {
          case A2uiPendingInfo pending -> scope.pendingFallback(pending);
          case A2uiErrorInfo error -> scope.errorFallback(error);
          case null, default -> null;
        };
  }

  private void unsubscribe() {
    if (propsSubscription != null) {
      propsSubscription.cancel();
      propsSubscription = null;
    }
  }

  private void disposeView() {
    if (view == null) {
      return;
    }
    SwingComponentView disposedView = view;
    HostViewContext disposedContext = context;
    // A hidden view gets its focus values back, and the shield over it goes away.
    accessibility.dispose();
    view = null;
    accessibility = null;
    context = null;
    implementation = null;
    // The views of the children go first, as the children of a Swing component do.
    disposedContext.dispose();
    try {
      disposedView.dispose();
    } catch (RuntimeException e) {
      SurfaceScope.log("The view of a component failed in its disposal.", e);
    }
  }

  /** The context of the view of this host. */
  private final class HostViewContext implements ViewContext {
    private final String componentType;
    private final List<ChildReconciler> reconcilers = new ArrayList<>();
    private boolean contextDisposed;

    HostViewContext(String componentType) {
      this.componentType = componentType;
    }

    @Override
    public SurfaceModel<? extends SwingComponentImplementation> surface() {
      return scope.surface();
    }

    @Override
    public A2uiServices services() {
      return scope.services();
    }

    @Override
    public ChildViews children(Container container) {
      return children(container, null);
    }

    @Override
    public ChildViews children(
        Container container, Function<? super ComponentNode<?>, ?> constraints) {
      if (contextDisposed) {
        throw new IllegalStateException("The view of this context is disposed.");
      }
      ChildReconciler reconciler =
          new ChildReconciler(scope, NodeHost.this, componentType, container, constraints);
      reconcilers.add(reconciler);
      return reconciler;
    }

    void dispose() {
      contextDisposed = true;
      reconcilers.forEach(ChildReconciler::dispose);
    }
  }

  @Override
  public String toString() {
    return "NodeHost(" + node + ")";
  }
}
