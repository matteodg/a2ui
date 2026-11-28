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
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.surface.A2uiErrorInfo;
import com.google.a2ui.swing.surface.A2uiFallbacks;
import com.google.a2ui.swing.surface.A2uiPendingInfo;
import com.google.a2ui.swing.surface.A2uiServices;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.JComponent;

/**
 * The objects that all the node hosts of one mounted surface share: the surface, its fallbacks and
 * its services. The scope also counts the subscriptions to the core that the hosts hold.
 */
public final class SurfaceScope {
  private static final System.Logger LOG = System.getLogger("com.google.a2ui.swing");

  private final SurfaceModel<? extends SwingComponentImplementation> surface;
  private final A2uiFallbacks fallbacks;
  private final A2uiServices services;
  private int activeSubscriptions;

  /**
   * Makes a scope.
   *
   * @param surface the surface that the hosts show
   * @param fallbacks the fallbacks of the surface
   * @param services the services of the surface
   */
  public SurfaceScope(
      SurfaceModel<? extends SwingComponentImplementation> surface,
      A2uiFallbacks fallbacks,
      A2uiServices services) {
    this.surface = Objects.requireNonNull(surface, "surface");
    this.fallbacks = Objects.requireNonNull(fallbacks, "fallbacks");
    this.services = Objects.requireNonNull(services, "services");
  }

  /**
   * Gets the surface that the hosts show.
   *
   * @return the surface
   */
  public SurfaceModel<? extends SwingComponentImplementation> surface() {
    return surface;
  }

  /**
   * Gets the services of the surface.
   *
   * @return the services
   */
  public A2uiServices services() {
    return services;
  }

  /**
   * Gets the number of the subscriptions to the properties of nodes that the hosts hold at this
   * time.
   *
   * @return the number. It is 0 after the disposal of each host.
   */
  public int activeSubscriptions() {
    return activeSubscriptions;
  }

  /**
   * Subscribes to the properties of a node and counts the subscription.
   *
   * @param node the node
   * @param onChange the work to do after each change of the properties
   * @return the subscription. The scope stops to count it when the caller cancels it.
   */
  Subscription subscribe(ComponentNode<?> node, Runnable onChange) {
    Subscription subscription = node.props().subscribe(props -> onChange.run());
    activeSubscriptions++;
    boolean[] cancelled = {false};
    return () -> {
      if (!cancelled[0]) {
        cancelled[0] = true;
        activeSubscriptions--;
        subscription.cancel();
      }
    };
  }

  /**
   * Makes the component of the {@code pending} fallback.
   *
   * @param info the description of the component that did not arrive
   * @return the component, or null to show nothing. The result is also null if the fallback throws
   *     an exception. Then the scope writes the exception to the log.
   */
  public JComponent pendingFallback(A2uiPendingInfo info) {
    return fallback("pending", info.componentId(), () -> fallbacks.pending().apply(info));
  }

  /**
   * Makes the component of the {@code error} fallback.
   *
   * @param info the description of the component that cannot show
   * @return the component, or null to show nothing. The result is also null if the fallback throws
   *     an exception. Then the scope writes the exception to the log.
   */
  public JComponent errorFallback(A2uiErrorInfo info) {
    return fallback("error", info.componentId(), () -> fallbacks.error().apply(info));
  }

  private static JComponent fallback(
      String category, String componentId, Supplier<? extends JComponent> builder) {
    try {
      return builder.get();
    } catch (RuntimeException e) {
      log("The " + category + " fallback failed for the component '" + componentId + "'.", e);
      return null;
    }
  }

  /** Writes a failure of application code to the log. The surface continues to operate. */
  static void log(String message, Throwable cause) {
    LOG.log(System.Logger.Level.ERROR, message, cause);
  }
}
