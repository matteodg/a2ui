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

import com.google.a2ui.swing.surface.A2uiErrorInfo;
import com.google.a2ui.swing.surface.A2uiFallbackError;
import com.google.a2ui.swing.surface.A2uiFallbacks;
import com.google.a2ui.swing.surface.A2uiPendingInfo;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/**
 * The fallbacks of the gallery. A component that the surface cannot show is visible as a label with
 * a dashed border, and not as empty space.
 */
final class Placeholders {
  /** The name of the Swing component of each placeholder. A test finds the placeholders by it. */
  static final String COMPONENT_NAME = "gallery.placeholder";

  private static final float[] DASH = {4f, 3f};

  private Placeholders() {}

  /**
   * Makes the fallbacks.
   *
   * @return fallbacks that show a label for a component that did not arrive and for a component
   *     that cannot show
   */
  static A2uiFallbacks fallbacks() {
    return A2uiFallbacks.NONE.withPending(Placeholders::pending).withError(Placeholders::error);
  }

  private static JComponent pending(A2uiPendingInfo info) {
    return label("Waiting for the component '" + info.componentId() + "'", true);
  }

  private static JComponent error(A2uiErrorInfo info) {
    String type = info.error().componentType().orElse("?");
    String text =
        switch (info.error().type()) {
          case A2uiFallbackError.NO_VIEW ->
              "No Swing view: " + type + " '" + info.componentId() + "'";
          case A2uiFallbackError.UNKNOWN_TYPE ->
              "Unknown type: " + type + " '" + info.componentId() + "'";
          case A2uiFallbackError.CYCLIC -> "Cycle at the component '" + info.componentId() + "'";
          default -> "Cannot show '" + info.componentId() + "': " + info.error().type();
        };
    return label(text, false);
  }

  private static JLabel label(String text, boolean pending) {
    JLabel label = new JLabel(text);
    label.setName(COMPONENT_NAME);
    // A placeholder for the root fills the surface. Its text stays at the top.
    label.setVerticalAlignment(SwingConstants.TOP);
    label.setToolTipText(text);
    Color color = UIManager.getColor("Label.disabledForeground");
    if (color == null) {
      color = Color.GRAY;
    }
    label.setForeground(color);
    label.setFont(label.getFont().deriveFont(pending ? Font.ITALIC : Font.PLAIN));
    label.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(4, 4, 4, 4),
            BorderFactory.createCompoundBorder(
                BorderFactory.createStrokeBorder(
                    new BasicStroke(
                        1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, DASH, 0f),
                    color),
                BorderFactory.createEmptyBorder(4, 8, 4, 8))));
    return label;
  }
}
