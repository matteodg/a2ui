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

import com.google.a2ui.swing.catalog.HeightForWidth;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * A check box or a radio button with a text that breaks into lines at the width of the row.
 *
 * <p>The text of a {@code JCheckBox} has one line, and the button cuts the text that does not fit.
 * Thus the row shows the button with no text, and a {@link WrappingLabel} next to it. The center of
 * the button is at the center of the first line of the text.
 *
 * <ul>
 *   <li>A click on the text operates the button, as a click on the text of a {@code JCheckBox}
 *       does. It also gives the focus to the button.
 *   <li>While the button has the focus, the row paints a dotted line around the text, because a
 *       button with no text does not paint the focus in each look and feel.
 *   <li>The text has the color of the text above the row. It has the disabled color while the user
 *       cannot operate the button.
 *   <li>The row does not set the accessible name of the button. The view gives it.
 * </ul>
 */
final class ToggleRow extends JComponent implements HeightForWidth {
  private static final long serialVersionUID = 1L;

  private final AbstractButton button;
  private final WrappingLabel text = new WrappingLabel();

  /**
   * Makes a row.
   *
   * @param button the button. The row removes its text and makes it not opaque.
   */
  ToggleRow(AbstractButton button) {
    this.button = Objects.requireNonNull(button, "button");
    button.setText("");
    button.setOpaque(false);
    add(button);
    add(text);
    text.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            if (SwingUtilities.isLeftMouseButton(event) && button.isEnabled()) {
              button.requestFocusInWindow();
              button.doClick(0);
            }
          }
        });
    button.addFocusListener(
        new FocusListener() {
          @Override
          public void focusGained(FocusEvent event) {
            repaint();
          }

          @Override
          public void focusLost(FocusEvent event) {
            repaint();
          }
        });
    button.addPropertyChangeListener(
        "enabled",
        event -> text.setForeground(button.isEnabled() ? null : BasicStyle.disabled(button)));
  }

  /**
   * Gets the button of the row.
   *
   * @return the button
   */
  AbstractButton button() {
    return button;
  }

  /**
   * Gets the label of the text.
   *
   * @return the label
   */
  WrappingLabel textLabel() {
    return text;
  }

  /**
   * Sets the text next to the button.
   *
   * @param value the text. An empty text uses no space.
   */
  void setText(String value) {
    text.setText(value);
    text.setVisible(!value.isEmpty());
  }

  /**
   * Gets the text next to the button.
   *
   * @return the text
   */
  String getText() {
    return text.getText();
  }

  private int gap() {
    return text.isVisible() ? Math.max(0, button.getIconTextGap()) : 0;
  }

  /** Gets the height of one line of the text. */
  private int lineHeight() {
    return getFontMetrics(BasicStyle.fontOf(text)).getHeight();
  }

  /**
   * Calculates the vertical positions of the button and of the text.
   *
   * @param textHeight the height of the text at its width
   * @return the y of the button, the y of the text, and the height of the content
   */
  private int[] rows(int textHeight) {
    int buttonHeight = button.getPreferredSize().height;
    if (!text.isVisible()) {
      return new int[] {0, 0, buttonHeight};
    }
    int line = lineHeight();
    int center = Math.max(buttonHeight, line) / 2;
    int buttonY = center - buttonHeight / 2;
    int textY = center - line / 2;
    return new int[] {buttonY, textY, Math.max(buttonY + buttonHeight, textY + textHeight)};
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize();
    }
    Insets insets = getInsets();
    Dimension box = button.getPreferredSize();
    Dimension label = text.isVisible() ? text.getPreferredSize() : new Dimension();
    return new Dimension(
        insets.left + box.width + gap() + label.width + insets.right,
        insets.top + rows(label.height)[2] + insets.bottom);
  }

  @Override
  public Dimension getMinimumSize() {
    if (isMinimumSizeSet()) {
      return super.getMinimumSize();
    }
    Insets insets = getInsets();
    int label = text.isVisible() ? text.getMinimumSize().width : 0;
    return new Dimension(
        insets.left + button.getPreferredSize().width + gap() + label + insets.right,
        getPreferredSize().height);
  }

  @Override
  public int preferredHeightFor(int width) {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize().height;
    }
    Insets insets = getInsets();
    int textHeight = text.isVisible() ? text.preferredHeightFor(textWidth(width)) : 0;
    return insets.top + rows(textHeight)[2] + insets.bottom;
  }

  /** Gets the width of the text in a row of a width. */
  private int textWidth(int width) {
    Insets insets = getInsets();
    return Math.max(
        1, width - insets.left - insets.right - button.getPreferredSize().width - gap());
  }

  @Override
  public void doLayout() {
    Insets insets = getInsets();
    Dimension box = button.getPreferredSize();
    int width = textWidth(getWidth());
    int[] rows = rows(text.isVisible() ? text.preferredHeightFor(width) : 0);
    boolean leftToRight = getComponentOrientation().isLeftToRight();
    int buttonX = leftToRight ? insets.left : getWidth() - insets.right - box.width;
    button.setBounds(buttonX, insets.top + rows[0], box.width, box.height);
    // The text is not wider than its lines. Thus a click next to a short text does nothing.
    int shown = text.isVisible() ? Math.min(width, text.getPreferredSize().width) : 0;
    int textX =
        leftToRight
            ? insets.left + box.width + gap()
            : getWidth() - insets.right - box.width - gap() - shown;
    text.setBounds(textX, insets.top + rows[1], shown, text.preferredHeightFor(width));
  }

  /** Paints the content, and a dotted line around the text while the button has the focus. */
  @Override
  protected void paintChildren(Graphics graphics) {
    super.paintChildren(graphics);
    if (!button.isFocusOwner() || !button.isFocusPainted() || !text.isVisible()) {
      return;
    }
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      Rectangle bounds = text.getBounds();
      g.setColor(BasicStyle.foregroundOf(text));
      g.setStroke(
          new BasicStroke(
              1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {1f, 1f}, 0f));
      g.drawRect(bounds.x - 1, bounds.y, bounds.width + 1, bounds.height - 1);
    } finally {
      g.dispose();
    }
  }
}
