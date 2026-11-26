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

package com.google.a2ui.swing.catalog.basic;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * The spacing and the colors that the views of the basic catalog share.
 *
 * <p>Protocol v1.0 has no theme. Thus each color comes from the look and feel, or from the
 * component above a view. The views never give a color to the views of their children. A container
 * sets the foreground color of its Swing container, and AWT gives that color to each component
 * below it that has no color of its own.
 *
 * <p>The spacing is the leaf margin strategy of the basic catalog implementation guide. A container
 * that the user cannot see has no padding and no gap. Each leaf and each control with an outline
 * has the same margin on its four sides. Thus a row or a column around a component does not change
 * the space around the component.
 */
final class BasicStyle {
  /** The margin of a leaf and of a control with an outline, in pixels. */
  static final int MARGIN = 8;

  /**
   * The key of the client property that gives a different margin to the leaves below a Swing
   * component. The value is an {@code Integer}. A button uses it: the text in a button has no
   * margin, because the button has its own padding.
   */
  static final String LEAF_MARGIN_PROPERTY = "a2ui.leafMargin";

  /** The least contrast between a text and its background that the views accept. */
  private static final double TEXT_CONTRAST = 4.5;

  private BasicStyle() {}

  /**
   * Gets the margin of a leaf.
   *
   * @param component the Swing component of the leaf
   * @return the value of {@link #LEAF_MARGIN_PROPERTY} of the nearest component above the leaf that
   *     has one, or {@link #MARGIN}
   */
  static int leafMargin(Component component) {
    for (Component above = component.getParent(); above != null; above = above.getParent()) {
      if (above instanceof JComponent swing
          && swing.getClientProperty(LEAF_MARGIN_PROPERTY) instanceof Integer margin) {
        return Math.max(0, margin);
      }
    }
    return MARGIN;
  }

  /**
   * Gets the font of the text of a component: the font that the component has or gets from the
   * component above it, or the label font of the look and feel.
   *
   * @param component the component
   * @return the font
   */
  static Font fontOf(Component component) {
    Font font = component.getFont();
    if (font == null) {
      font = UIManager.getFont("Label.font");
    }
    return font != null ? font : new Font(Font.DIALOG, Font.PLAIN, 12);
  }

  /**
   * Gets the color of the text of a component: the color that the component has or gets from the
   * component above it, or the label color of the look and feel.
   *
   * @param component the component
   * @return the color
   */
  static Color foregroundOf(Component component) {
    Color color = component.getForeground();
    return color != null ? color : lookAndFeelColor(Color.BLACK, "Label.foreground", "textText");
  }

  /**
   * Gets the color of the area behind a component.
   *
   * @param component the component
   * @return the color
   */
  static Color backgroundOf(Component component) {
    Color color = component.getBackground();
    return color != null ? color : lookAndFeelColor(Color.WHITE, "Panel.background", "control");
  }

  /**
   * Gets a color for text that is less important than the usual text, for example a caption or a
   * hint. The color is between the text color and the background color of the component.
   *
   * @param component the component that shows the text
   * @return the color
   */
  static Color muted(Component component) {
    return mix(foregroundOf(component), backgroundOf(component), 0.35);
  }

  /**
   * Gets the accent color of the look and feel. A primary button has this background.
   *
   * @return the color
   */
  static Color accent() {
    return lookAndFeelColor(
        new Color(0x1a, 0x73, 0xe8),
        "Component.accentColor",
        "textHighlight",
        "List.selectionBackground",
        "TextField.selectionBackground");
  }

  /**
   * Gets the color of text on the accent color.
   *
   * @return the color
   */
  static Color onAccent() {
    Color accent = accent();
    Color text = lookAndFeelColor(Color.WHITE, "textHighlightText", "List.selectionForeground");
    return contrast(text, accent) >= TEXT_CONTRAST ? text : readableOn(accent);
  }

  /**
   * Gets the color of text that operates as a link, for a button with no border. It is the accent
   * color if the user can read it on the background. If not, it is a color between the accent color
   * and the text color.
   *
   * @param component the component that shows the text
   * @return the color
   */
  static Color link(Component component) {
    return legible(accent(), component);
  }

  /**
   * Gets the color of the message of a check that failed with the severity {@code error}.
   *
   * @param component the component that shows the message
   * @return the color
   */
  static Color error(Component component) {
    return legible(
        lookAndFeelColor(new Color(0xd3, 0x2f, 0x2f), "Component.error.focusedBorderColor"),
        component);
  }

  /**
   * Gets the color of the message of a check that failed with the severity {@code warning}.
   *
   * @param component the component that shows the message
   * @return the color
   */
  static Color warning(Component component) {
    return legible(
        lookAndFeelColor(new Color(0xb2, 0x6a, 0x00), "Component.warning.focusedBorderColor"),
        component);
  }

  /**
   * Gets the color of the text of a control that the user cannot operate.
   *
   * @param component the control
   * @return the color
   */
  static Color disabled(Component component) {
    return lookAndFeelColor(
        mix(foregroundOf(component), backgroundOf(component), 0.5),
        "Button.disabledText",
        "Label.disabledForeground",
        "textInactiveText");
  }

  /**
   * Prepares a graphics object to draw text as the desktop of the user draws it.
   *
   * @param graphics the graphics object of a paint
   */
  static void textHints(Graphics2D graphics) {
    Object hints = null;
    try {
      hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
    } catch (RuntimeException e) {
      // A computer with no display has no desktop properties.
    }
    if (hints instanceof Map<?, ?> map) {
      graphics.addRenderingHints(map);
    } else {
      graphics.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
  }

  /**
   * Mixes two colors.
   *
   * @param first the first color
   * @param second the second color
   * @param share the part of the second color in the result, from 0 to 1
   * @return the color between the two
   */
  static Color mix(Color first, Color second, double share) {
    double keep = 1 - share;
    return new Color(
        (int) Math.round(first.getRed() * keep + second.getRed() * share),
        (int) Math.round(first.getGreen() * keep + second.getGreen() * share),
        (int) Math.round(first.getBlue() * keep + second.getBlue() * share));
  }

  /**
   * Gets the contrast of two colors, as WCAG 2 specifies it.
   *
   * @param first the first color
   * @param second the second color
   * @return the contrast, from 1 for equal colors to 21 for black and white
   */
  static double contrast(Color first, Color second) {
    double a = luminance(first);
    double b = luminance(second);
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
  }

  /** Gets black or white, the one that has more contrast with a background. */
  private static Color readableOn(Color background) {
    return contrast(Color.BLACK, background) >= contrast(Color.WHITE, background)
        ? Color.BLACK
        : Color.WHITE;
  }

  /**
   * Moves a color in the direction of the text color of a component until the user can read it on
   * the background of the component.
   */
  private static Color legible(Color color, Component component) {
    Color background = backgroundOf(component);
    Color text = readableOn(background);
    Color result = color;
    for (int step = 1; step <= 10 && contrast(result, background) < TEXT_CONTRAST; step++) {
      result = mix(color, text, step / 10.0);
    }
    return result;
  }

  private static double luminance(Color color) {
    return 0.2126 * channel(color.getRed())
        + 0.7152 * channel(color.getGreen())
        + 0.0722 * channel(color.getBlue());
  }

  private static double channel(int value) {
    double part = value / 255.0;
    return part <= 0.03928 ? part / 12.92 : Math.pow((part + 0.055) / 1.055, 2.4);
  }

  /** Gets the first color that the look and feel has for a list of keys. */
  private static Color lookAndFeelColor(Color fallback, String... keys) {
    for (String key : List.of(keys)) {
      Color color = UIManager.getColor(key);
      if (color != null) {
        // A color of the look and feel can be an object that changes with the look and feel.
        return new Color(color.getRGB(), false);
      }
    }
    return fallback;
  }
}
