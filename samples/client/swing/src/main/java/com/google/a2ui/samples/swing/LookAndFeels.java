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

package com.google.a2ui.samples.swing;

import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Locale;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/** The look and feels that the user can select in the gallery. */
final class LookAndFeels {
  /** The look and feel of the operating system. */
  static final String SYSTEM = "System";

  /** The names of the look and feels, in the sequence of the menu. */
  static final List<String> NAMES = List.of(SYSTEM, "Metal", "Nimbus");

  private LookAndFeels() {}

  /**
   * Gets the class of a look and feel.
   *
   * @param name one of {@link #NAMES}. The case of the letters is not important.
   * @return the name of the class
   * @throws IllegalArgumentException if the name is not known
   */
  static String className(String name) {
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "system" -> UIManager.getSystemLookAndFeelClassName();
      case "metal" -> "javax.swing.plaf.metal.MetalLookAndFeel";
      case "nimbus" -> "javax.swing.plaf.nimbus.NimbusLookAndFeel";
      default ->
          throw new IllegalArgumentException(
              "Unknown look and feel '" + name + "'. Use one of " + NAMES + ".");
    };
  }

  /**
   * Sets the look and feel, and applies it to the components of a window that is open.
   *
   * @param name one of {@link #NAMES}
   * @param root the top component of the window, or null when no window is open
   * @throws IllegalArgumentException if the name is not known or the computer does not have the
   *     look and feel
   */
  static void apply(String name, Component root) {
    try {
      UIManager.setLookAndFeel(className(name));
    } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
      throw new IllegalArgumentException("Cannot use the look and feel '" + name + "'.", e);
    }
    if (root != null) {
      SwingUtilities.updateComponentTreeUI(root);
      // A layout can keep the sizes of the look and feel before. Thus each container forgets them.
      invalidateTree(root);
      root.validate();
      root.repaint();
    }
  }

  private static void invalidateTree(Component component) {
    component.invalidate();
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        invalidateTree(child);
      }
    }
  }
}
