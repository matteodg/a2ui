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
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import com.google.a2ui.swing.surface.A2uiErrorInfo;
import com.google.a2ui.swing.surface.A2uiFallbackError;
import com.google.a2ui.swing.surface.A2uiPendingInfo;
import com.google.a2ui.swing.surface.A2uiServices;
import java.util.Objects;
import java.util.Optional;
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
  private final Optional<String> parentComponentType;
  private final Listener listener;

  /** The node at the position, or null before the first call of {@link #bind(ComponentNode)}. */
  private ComponentNode<?> node;

  /** The implementation that made the view, or null if there is no view. */
  private SwingComponentImplementation implementation;

  private SwingComponentView view;
  private Subscription propsSubscription;

  /** The description that made the fallback, or null if the host shows no fallback state. */
  private Object fallbackInfo;

  private JComponent fallback;
  private JComponent component;
  private boolean disposed;

  /**
   * Makes a host that shows nothing until the first call of {@link #bind(ComponentNode)}.
   *
   * @param scope the objects of the mounted surface
   * @param parentComponentType the type of the component whose child this position is, or an empty
   *     value for the root
   * @param listener receives the changes of this host
   */
  public NodeHost(SurfaceScope scope, Optional<String> parentComponentType, Listener listener) {
    this.scope = Objects.requireNonNull(scope, "scope");
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
      return implementation;
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
    try {
      SwingComponentView created = resolved.createView(new HostViewContext());
      Objects.requireNonNull(created, "createView returned null");
      Objects.requireNonNull(created.component(), "the view has no component");
      view = created;
      implementation = resolved;
    } catch (RuntimeException e) {
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
    listener.updated(this);
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
          case RESOLVED -> null;
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
    view = null;
    implementation = null;
    try {
      disposedView.dispose();
    } catch (RuntimeException e) {
      SurfaceScope.log("The view of a component failed in its disposal.", e);
    }
  }

  /** The context of the view of this host. */
  private final class HostViewContext implements ViewContext {
    @Override
    public SurfaceModel<? extends SwingComponentImplementation> surface() {
      return scope.surface();
    }

    @Override
    public A2uiServices services() {
      return scope.services();
    }
  }

  @Override
  public String toString() {
    return "NodeHost(" + node + ")";
  }
}
