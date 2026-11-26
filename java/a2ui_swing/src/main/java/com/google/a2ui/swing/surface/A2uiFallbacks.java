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

import java.util.Objects;
import java.util.function.Function;
import javax.swing.JComponent;

/**
 * The Swing components that a surface shows in the position of a component that it cannot show.
 *
 * <p>Without fallbacks, such a component shows nothing and uses no space. A fallback changes only
 * what the user sees. The surface reports each error to the agent with or without a fallback. Thus
 * do not use a fallback to report an error.
 *
 * <p>A fallback returns a new component for each call, or null to show nothing. If a fallback
 * throws an exception, the surface writes it to the log and shows nothing. The surface does not
 * delay a fallback. A fallback that must appear only after a time does its own timing.
 *
 * <pre>{@code
 * A2uiFallbacks fallbacks =
 *     A2uiFallbacks.NONE
 *         .withPending(info -> new JLabel("..."))
 *         .withError(info -> new JLabel("Cannot show " + info.componentId()));
 * }</pre>
 *
 * @param pending makes the component for a component that did not arrive at this time, and for the
 *     root before it arrives. The surface replaces it in the same position when the component
 *     arrives.
 * @param error makes the component for a component that cannot show unless the agent sends a
 *     change: a component of an unknown type, or a reference that makes a cycle
 */
public record A2uiFallbacks(
    Function<? super A2uiPendingInfo, ? extends JComponent> pending,
    Function<? super A2uiErrorInfo, ? extends JComponent> error) {
  /** The fallbacks that show nothing. */
  public static final A2uiFallbacks NONE = new A2uiFallbacks(info -> null, info -> null);

  /** Makes the fallbacks. */
  public A2uiFallbacks {
    Objects.requireNonNull(pending, "pending");
    Objects.requireNonNull(error, "error");
  }

  /**
   * Makes a copy of these fallbacks with a different {@code pending} fallback.
   *
   * @param pending the new fallback
   * @return the new fallbacks
   */
  public A2uiFallbacks withPending(
      Function<? super A2uiPendingInfo, ? extends JComponent> pending) {
    return new A2uiFallbacks(pending, error);
  }

  /**
   * Makes a copy of these fallbacks with a different {@code error} fallback.
   *
   * @param error the new fallback
   * @return the new fallbacks
   */
  public A2uiFallbacks withError(Function<? super A2uiErrorInfo, ? extends JComponent> error) {
    return new A2uiFallbacks(pending, error);
  }
}
