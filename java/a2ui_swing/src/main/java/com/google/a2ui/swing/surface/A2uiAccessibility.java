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

import com.google.a2ui.swing.internal.ViewMarks;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JComponent;

/**
 * The resolved {@code @accessibility} attributes of one component of a surface.
 *
 * <p>The adapter gives the label and the description to the {@code AccessibleContext} of the Swing
 * component. The Java Accessibility API has no equivalent of {@code hidden} and of {@code live}.
 * Thus the adapter also keeps all the attributes as a client property of the Swing component, with
 * the key {@link #CLIENT_PROPERTY}. An assistive tool or a test reads them with {@link
 * #of(JComponent)}.
 *
 * <p>The attribute {@code hidden} applies to a component and to all the components below it in the
 * surface. Swing cannot remove a component from the accessible tree. Thus the adapter gives an
 * empty accessible name and an empty accessible description to the component of each view in the
 * hidden part, and it sends no change event for a live component there. It also marks each of these
 * components. Read the mark with {@link #isHidden(JComponent)}. The user cannot operate a hidden
 * part. The keyboard focus does not go into it: the Tab key goes past it, and the component of each
 * view there is not focusable. In a window, the mouse events at a point of a hidden view go to a
 * component of the adapter that does nothing, and not to the view. The record of a component below
 * a hidden component has only the attributes of that component itself.
 *
 * @param label the name of the component for assistive tools, if the agent gave one
 * @param description the description of the component for assistive tools, if the agent gave one
 * @param hidden true if assistive tools must ignore the component
 * @param live how assistive tools announce a change of the component: {@code off}, {@code polite}
 *     or {@code assertive}, if the agent gave a value
 */
public record A2uiAccessibility(
    Optional<String> label, Optional<String> description, boolean hidden, Optional<String> live) {
  /** The key of the client property of a Swing component that has the attributes. */
  public static final String CLIENT_PROPERTY = "a2ui.accessibility";

  /** The attributes of a component that has none. */
  public static final A2uiAccessibility NONE =
      new A2uiAccessibility(Optional.empty(), Optional.empty(), false, Optional.empty());

  /** Makes the attributes. */
  public A2uiAccessibility {
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(live, "live");
  }

  /**
   * Gets the attributes that the adapter gave to a Swing component.
   *
   * @param component the Swing component of a view. See {@code
   *     SwingComponentView.accessibleComponent()}.
   * @return the attributes, or {@link #NONE} if the component has none
   */
  public static A2uiAccessibility of(JComponent component) {
    return component.getClientProperty(CLIENT_PROPERTY) instanceof A2uiAccessibility attributes
        ? attributes
        : NONE;
  }

  /**
   * Tells if assistive tools must ignore a Swing component: the component has the attribute {@code
   * hidden}, or a component above it in the surface has it.
   *
   * @param component the Swing component of a view. See {@code
   *     SwingComponentView.accessibleComponent()}.
   * @return true if the component is in a hidden part of a surface
   */
  public static boolean isHidden(JComponent component) {
    return ViewMarks.isHidden(component);
  }
}
