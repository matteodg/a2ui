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

package com.google.a2ui.swing.surface;

import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeResolver;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.nodes.NodeHost;
import com.google.a2ui.swing.nodes.SurfaceScope;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The Swing component that shows one surface.
 *
 * <p>The surface component only shows the surface. It does not process messages. The application
 * owns the {@code MessageProcessor}, gets the {@link SurfaceModel} from it and puts an {@code
 * A2uiSurface} in its window:
 *
 * <pre>{@code
 * processor.model().onSurfaceCreated().subscribe(model -> frame.add(new A2uiSurface(model)));
 * }</pre>
 *
 * <p>The component follows the life of a Swing component. It makes its node resolver and its views
 * when Swing connects it to a window ({@code addNotify}), and it disposes them when Swing removes
 * it ({@code removeNotify}). Thus the application disposes nothing, and a component that the
 * application did not show uses no resources. A component that the application adds again makes new
 * views from the model. Then the state that only a view has is lost, for example a scroll position.
 *
 * <p>When the processor deletes the surface, the component becomes empty.
 *
 * <p>The root view fills the component. While the surface has no root component, the component
 * shows the {@code pending} fallback, or nothing.
 *
 * <p>Use the component only on the event dispatch thread. The constructors and the life cycle
 * methods throw an {@code IllegalStateException} on each other thread.
 */
public final class A2uiSurface extends JPanel {
  private static final long serialVersionUID = 1L;

  private final transient SurfaceModel<? extends SwingComponentImplementation> surface;
  private final transient A2uiFallbacks fallbacks;
  private final transient A2uiServices services;

  /** The objects of the last mount. They stay after the unmount, for the subscription count. */
  private transient SurfaceScope scope;

  /** The parts of the mount, or null while the component is not mounted. */
  private transient Mount<?> mount;

  /** The component that fills this component at this time, or null. */
  private transient JComponent content;

  /**
   * Makes a component with no fallbacks and with the default services.
   *
   * @param surface the surface to show
   */
  public A2uiSurface(SurfaceModel<? extends SwingComponentImplementation> surface) {
    this(surface, A2uiFallbacks.NONE, A2uiServices.DEFAULT);
  }

  /**
   * Makes a component with the default services.
   *
   * @param surface the surface to show
   * @param fallbacks the components to show for a component that the surface cannot show
   */
  public A2uiSurface(
      SurfaceModel<? extends SwingComponentImplementation> surface, A2uiFallbacks fallbacks) {
    this(surface, fallbacks, A2uiServices.DEFAULT);
  }

  /**
   * Makes a component.
   *
   * @param surface the surface to show
   * @param fallbacks the components to show for a component that the surface cannot show
   * @param services the services that the views of the surface use
   * @throws IllegalStateException if the current thread is not the event dispatch thread
   */
  public A2uiSurface(
      SurfaceModel<? extends SwingComponentImplementation> surface,
      A2uiFallbacks fallbacks,
      A2uiServices services) {
    super(new BorderLayout());
    A2uiSwing.requireEventDispatchThread();
    this.surface = Objects.requireNonNull(surface, "surface");
    this.fallbacks = Objects.requireNonNull(fallbacks, "fallbacks");
    this.services = Objects.requireNonNull(services, "services");
  }

  /**
   * Gets the surface that this component shows.
   *
   * @return the surface
   */
  public SurfaceModel<? extends SwingComponentImplementation> surface() {
    return surface;
  }

  /**
   * Gets the fallbacks of this component.
   *
   * @return the fallbacks
   */
  public A2uiFallbacks fallbacks() {
    return fallbacks;
  }

  /**
   * Gets the services of this component.
   *
   * @return the services
   */
  public A2uiServices services() {
    return services;
  }

  /**
   * Tells if this component follows the surface at this time.
   *
   * @return true between {@code addNotify} and {@code removeNotify}, while the surface is not
   *     disposed
   */
  public boolean isMounted() {
    return mount != null;
  }

  /**
   * Gets the number of the subscriptions to the properties of nodes that the views of this
   * component hold. A test uses it to make sure that an unmounted component follows nothing.
   *
   * @return the number. It is 0 while the component is not mounted.
   */
  public int activeSubscriptionCount() {
    return scope == null ? 0 : scope.activeSubscriptions();
  }

  /**
   * Gets a snapshot of the tree that this component shows: each node with its view and its Swing
   * component. Tests and debug tools use it.
   *
   * @return the root of the tree, or an empty result if the component is not mounted or the surface
   *     has no root component
   * @throws IllegalStateException if the current thread is not the event dispatch thread
   */
  public Optional<MountedNode> viewTree() {
    A2uiSwing.requireEventDispatchThread();
    return mount == null || mount.rootHost == null
        ? Optional.empty()
        : Optional.of(snapshot(mount.rootHost));
  }

  /**
   * Connects this component to a window, and starts to show the surface.
   *
   * @throws IllegalStateException if the current thread is not the event dispatch thread
   */
  @Override
  public void addNotify() {
    A2uiSwing.requireEventDispatchThread();
    super.addNotify();
    if (mount == null && !surface.isDisposed()) {
      scope = new SurfaceScope(surface, fallbacks, services);
      mount = new Mount<>(surface);
    }
  }

  /**
   * Disposes the views and the node resolver, and removes this component from its window.
   *
   * @throws IllegalStateException if the current thread is not the event dispatch thread
   */
  @Override
  public void removeNotify() {
    A2uiSwing.requireEventDispatchThread();
    unmount();
    super.removeNotify();
  }

  private void unmount() {
    if (mount != null) {
      Mount<?> ended = mount;
      mount = null;
      ended.dispose();
      setContent(null);
    }
  }

  /** Puts a component in the center of this component, in the position of the content before. */
  private void setContent(JComponent next) {
    if (next == content) {
      return;
    }
    if (content != null) {
      remove(content);
    }
    content = next;
    if (next != null) {
      add(next, BorderLayout.CENTER);
    }
    invalidate();
    revalidate();
    repaint();
  }

  private static MountedNode snapshot(NodeHost host) {
    List<MountedNode> children = new ArrayList<>();
    host.childHosts().forEach(child -> children.add(snapshot(child)));
    return new MountedNode(
        host.node(), host.view(), host.component(), Collections.unmodifiableList(children));
  }

  /**
   * The parts of one mount: the node resolver, the host of the root and the subscriptions.
   *
   * @param <C> the type of the components of the catalogs of the surface
   */
  private final class Mount<C extends SwingComponentImplementation> {
    private final Subscription disposing;
    private final NodeResolver<C> resolver;
    private final Subscription rootSubscription;
    private NodeHost rootHost;

    Mount(SurfaceModel<C> model) {
      // This listener is before the listener of the resolver. Thus the views go away before the
      // nodes when the processor deletes the surface.
      disposing = model.onDisposing().subscribe(disposed -> unmount());
      resolver = new NodeResolver<>(model);
      rootSubscription = resolver.rootNode().subscribe(root -> showRoot(root.orElse(null)));
      showRoot(resolver.rootNode().get().orElse(null));
    }

    private void showRoot(ComponentNode<C> root) {
      if (root == null) {
        if (rootHost != null) {
          rootHost.dispose();
          rootHost = null;
        }
        setContent(scope.pendingFallback(new A2uiPendingInfo(surface.rootId(), Optional.empty())));
        return;
      }
      if (rootHost == null) {
        rootHost =
            new NodeHost(
                scope, Optional.empty(), host -> setContent(host.component().orElse(null)));
      }
      rootHost.bind(root);
      setContent(rootHost.component().orElse(null));
    }

    void dispose() {
      disposing.cancel();
      rootSubscription.cancel();
      if (rootHost != null) {
        rootHost.dispose();
        rootHost = null;
      }
      resolver.dispose();
    }
  }
}
