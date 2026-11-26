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

import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.surface.A2uiAccessibility;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.accessibility.AccessibleContext;
import javax.swing.JComponent;

/**
 * Gives the {@code @accessibility} attributes of one node to the Swing component of its view. Each
 * node host has one instance. The dispatcher does this work in one place. Thus each component gets
 * it, also a custom component.
 */
final class Accessibility {
  private static final String OFF = "off";

  /** The name that this object gave to the component, or null if it gave none. */
  private String givenName;

  /** The description that this object gave to the component, or null if it gave none. */
  private String givenDescription;

  /** True after the first call. A component is not live for its first content. */
  private boolean applied;

  /**
   * Applies the attributes after an update of a view.
   *
   * @param view the view of the node
   * @param props the current properties of the node
   * @param updated true if the view got new properties, false if only a view below it changed
   */
  void apply(SwingComponentView view, NodeProps<?> props, boolean updated) {
    JComponent component = view.accessibleComponent();
    NodeProps<?> written = props.accessibility();
    A2uiAccessibility attributes =
        new A2uiAccessibility(
            text(written.value("label")),
            text(written.value("description")),
            Boolean.TRUE.equals(written.value("hidden")),
            text(written.value("live")));
    component.putClientProperty(
        A2uiAccessibility.CLIENT_PROPERTY,
        attributes.equals(A2uiAccessibility.NONE) ? null : attributes);

    // An explicit label is before the name that the view infers from its visible text. A hidden
    // component has an empty name, because Swing cannot remove it from the accessible tree.
    String name =
        attributes.hidden() ? "" : attributes.label().or(view::inferredAccessibleName).orElse(null);
    String description = attributes.hidden() ? "" : attributes.description().orElse(null);
    boolean live = attributes.live().filter(value -> !value.equals(OFF)).isPresent();
    if (name == null
        && description == null
        && givenName == null
        && givenDescription == null
        && !live) {
      // There is nothing to give or to take back. Thus Swing does not make the accessible context
      // of the component before an assistive tool asks for it.
      return;
    }
    AccessibleContext context = component.getAccessibleContext();
    if (context == null) {
      return;
    }
    givenName = give(name, givenName, context.getAccessibleName(), context::setAccessibleName);
    givenDescription =
        give(
            description,
            givenDescription,
            context.getAccessibleDescription(),
            context::setAccessibleDescription);

    if (live && applied && updated && !attributes.hidden()) {
      // Swing has no live regions. This event tells a listener that the content changed.
      context.firePropertyChange(
          AccessibleContext.ACCESSIBLE_VISIBLE_DATA_PROPERTY, Boolean.FALSE, Boolean.TRUE);
    }
    applied = true;
  }

  /**
   * Sets a text of the accessible context, or removes the text that this object set before.
   *
   * @param wanted the text to set, or null if the attributes and the view give none
   * @param given the text that this object set before, or null
   * @param current the text that the context has at this time
   * @param setter sets the text of the context
   * @return the text that this object set, or null
   */
  private static String give(String wanted, String given, String current, Consumer<String> setter) {
    if (wanted != null) {
      if (given == null || !wanted.equals(current)) {
        setter.accept(wanted);
      }
      return wanted;
    }
    // The context gets its default text back, unless the view set a text of its own after this
    // object set one.
    if (given != null && Objects.equals(given, current)) {
      setter.accept(null);
    }
    return null;
  }

  private static Optional<String> text(Object value) {
    return value instanceof String text && !text.isEmpty() ? Optional.of(text) : Optional.empty();
  }
}
