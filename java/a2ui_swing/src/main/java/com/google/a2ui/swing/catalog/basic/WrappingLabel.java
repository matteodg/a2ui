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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;

/**
 * A label that shows plain text and breaks it into lines at its width. The views use it for short
 * messages, for example the message of a check that failed.
 *
 * <p>A {@code JLabel} shows its text in one line and cuts the text that does not fit. This label
 * implements {@link HeightForWidth}. Thus a layout gives it the height for its lines. The label
 * shows the text as it is: it has no markup.
 *
 * <p>The label is not opaque. Its font and its color are those of the component, which come from
 * the component above it if code sets none.
 */
final class WrappingLabel extends JComponent implements HeightForWidth, Accessible {
  private static final long serialVersionUID = 1L;

  private String text = "";

  /** The font and the width of the lines in {@link #lines}, or null if there are none. */
  private transient Font linesFont;

  private transient int linesWidth = -1;
  private transient List<String> lines = List.of();

  /** Makes a label with no text. */
  WrappingLabel() {
    setOpaque(false);
  }

  /**
   * Gets the text of the label.
   *
   * @return the text
   */
  String getText() {
    return text;
  }

  /**
   * Sets the text of the label. A line feed starts a new line.
   *
   * @param text the text
   */
  void setText(String text) {
    Objects.requireNonNull(text, "text");
    if (!text.equals(this.text)) {
      this.text = text;
      linesFont = null;
      revalidate();
      repaint();
    }
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize();
    }
    FontMetrics metrics = metrics();
    Insets insets = getInsets();
    int width = 0;
    int count = 0;
    if (!text.isEmpty()) {
      for (String line : text.split("\n", -1)) {
        width = Math.max(width, metrics.stringWidth(line));
        count++;
      }
    }
    return new Dimension(
        width + insets.left + insets.right,
        count * metrics.getHeight() + insets.top + insets.bottom);
  }

  @Override
  public Dimension getMinimumSize() {
    if (isMinimumSizeSet()) {
      return super.getMinimumSize();
    }
    FontMetrics metrics = metrics();
    Insets insets = getInsets();
    int width = 0;
    for (String word : text.split("\\s+")) {
      width = Math.max(width, metrics.stringWidth(word));
    }
    Dimension preferred = getPreferredSize();
    return new Dimension(
        Math.min(preferred.width, width + insets.left + insets.right), preferred.height);
  }

  @Override
  public int preferredHeightFor(int width) {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize().height;
    }
    Insets insets = getInsets();
    int count = linesFor(width - insets.left - insets.right).size();
    return count * metrics().getHeight() + insets.top + insets.bottom;
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      BasicStyle.textHints(g);
      Font font = BasicStyle.fontOf(this);
      FontMetrics metrics = getFontMetrics(font);
      Insets insets = getInsets();
      int available = getWidth() - insets.left - insets.right;
      g.setFont(font);
      g.setColor(BasicStyle.foregroundOf(this));
      int baseline = insets.top + metrics.getAscent();
      for (String line : linesFor(available)) {
        int x =
            getComponentOrientation().isLeftToRight()
                ? insets.left
                : getWidth() - insets.right - metrics.stringWidth(line);
        g.drawString(line, x, baseline);
        baseline += metrics.getHeight();
      }
    } finally {
      g.dispose();
    }
  }

  private FontMetrics metrics() {
    return getFontMetrics(BasicStyle.fontOf(this));
  }

  /** Breaks the text into lines that are not wider than a width. */
  private List<String> linesFor(int width) {
    Font font = BasicStyle.fontOf(this);
    if (font.equals(linesFont) && width == linesWidth) {
      return lines;
    }
    FontMetrics metrics = getFontMetrics(font);
    List<String> result = new ArrayList<>();
    if (!text.isEmpty()) {
      for (String paragraph : text.split("\n", -1)) {
        wrap(paragraph, Math.max(1, width), metrics, result);
      }
    }
    linesFont = font;
    linesWidth = width;
    lines = result;
    return result;
  }

  /** Adds the lines of one paragraph. A word that is wider than the width is a line of its own. */
  private static void wrap(String paragraph, int width, FontMetrics metrics, List<String> out) {
    StringBuilder line = new StringBuilder();
    for (String word : paragraph.strip().split("\\s+")) {
      String longer = line.isEmpty() ? word : line + " " + word;
      if (!line.isEmpty() && metrics.stringWidth(longer) > width) {
        out.add(line.toString());
        line.setLength(0);
        line.append(word);
      } else {
        line.setLength(0);
        line.append(longer);
      }
    }
    out.add(line.toString());
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleWrappingLabel();
    }
    return accessibleContext;
  }

  /** The accessible object of the label: a label whose name is its text. */
  private final class AccessibleWrappingLabel extends AccessibleJComponent {
    private static final long serialVersionUID = 1L;

    @Override
    public String getAccessibleName() {
      String name = super.getAccessibleName();
      return name != null ? name : text;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.LABEL;
    }
  }
}
