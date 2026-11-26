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
}
