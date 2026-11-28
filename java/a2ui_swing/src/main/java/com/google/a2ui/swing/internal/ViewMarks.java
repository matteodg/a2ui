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

package com.google.a2ui.swing.internal;

import java.net.URI;
import java.util.Optional;
import javax.swing.JComponent;

/**
 * The marks that the adapter puts on the Swing component of a view, as client properties.
 *
 * <p>The component is the {@code accessibleComponent()} of the view. The code of the module writes
 * the marks, and the tests of the module read them. The keys of the client properties are not a
 * part of the public API. Thus only this class knows them.
 */
public final class ViewMarks {
  private static final String ICON_NAME = "a2ui.basic.iconName";
  private static final String MEDIA_URL = "a2ui.basic.mediaUrl";
  private static final String HIDDEN = "a2ui.accessibility.hidden";

  private ViewMarks() {}

  /**
   * Sets the name of the icon that the view of an {@code Icon} shows.
   *
   * @param component the Swing component of the view
   * @param name the name that the agent gave, or null if the icon shows SVG path data or nothing
   */
  public static void setIconName(JComponent component, String name) {
    component.putClientProperty(ICON_NAME, name);
  }

  /**
   * Gets the name of the icon that the view of an {@code Icon} shows.
   *
   * @param component the Swing component of the view
   * @return the name that the agent gave, also if the catalog does not have the name. The result is
   *     empty if the icon shows SVG path data or nothing, and for a component of a different view.
   */
  public static Optional<String> iconName(JComponent component) {
    return component.getClientProperty(ICON_NAME) instanceof String name
        ? Optional.of(name)
        : Optional.empty();
  }

  /**
   * Sets the URL that the view of an {@code Image}, a {@code Video} or an {@code AudioPlayer}
   * shows.
   *
   * @param component the Swing component of the view
   * @param url the URL that the view accepted, or null if the component has no URL or if its URL is
   *     not safe
   */
  public static void setMediaUrl(JComponent component, URI url) {
    component.putClientProperty(MEDIA_URL, url);
  }

  /**
   * Gets the URL that the view of an {@code Image}, a {@code Video} or an {@code AudioPlayer}
   * shows.
   *
   * @param component the Swing component of the view
   * @return the URL that the view accepted. The result is empty if the component has no URL or if
   *     its URL is not safe, and for a component of a different view.
   */
  public static Optional<URI> mediaUrl(JComponent component) {
    return component.getClientProperty(MEDIA_URL) instanceof URI url
        ? Optional.of(url)
        : Optional.empty();
  }

  /**
   * Marks a Swing component that is in a part of a surface that the {@code @accessibility}
   * attribute {@code hidden} hides, or removes the mark.
   *
   * @param component the Swing component of a view
   * @param hidden true if assistive tools must ignore the component
   */
  public static void setHidden(JComponent component, boolean hidden) {
    component.putClientProperty(HIDDEN, hidden ? Boolean.TRUE : null);
  }

  /**
   * Tells if a Swing component has the mark of a hidden part of a surface.
   *
   * @param component the Swing component of a view
   * @return true if the component has the mark
   */
  public static boolean isHidden(JComponent component) {
    return Boolean.TRUE.equals(component.getClientProperty(HIDDEN));
  }
}
