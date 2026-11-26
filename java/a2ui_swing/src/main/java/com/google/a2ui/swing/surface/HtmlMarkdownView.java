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

import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.surface.MarkdownSubset.Block;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.UIManager;
import javax.swing.text.DefaultCaret;
import javax.swing.text.View;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

/**
 * The view of the default {@link MarkdownRenderer}. It shows the Markdown subset of the protocol in
 * a {@code JEditorPane} that the user cannot edit.
 *
 * <p>The view converts the Markdown text to HTML that has only the tags of the subset. See {@link
 * MarkdownSubset} for the subset and {@link MarkdownHtml} for the HTML.
 *
 * <p>The component is a {@code JEditorPane} and not a {@code JLabel} with HTML for these reasons:
 *
 * <ul>
 *   <li>A text component breaks its lines at its width, and its root view gives the height for a
 *       width. A label gives only the size of the text with no line breaks, and it puts wrapped
 *       text at an incorrect vertical position.
 *   <li>The document of a text component has the visible text and its structure. Assistive tools
 *       read them through the accessible text of the component. The accessible name of a label with
 *       HTML is the HTML source.
 * </ul>
 *
 * <p>The component is not opaque, has no border, and does not get the focus. Its base text style is
 * its font and its foreground color. The view shows the text again when code sets one of the two.
 * The component implements {@link HeightForWidth}.
 */
final class HtmlMarkdownView implements MarkdownView {
  private final Pane pane = new Pane();
  private final PropertyChangeListener styleListener = event -> render();
  private String markdown = "";
  private List<Block> blocks = List.of();

  HtmlMarkdownView() {
    pane.addPropertyChangeListener("font", styleListener);
    pane.addPropertyChangeListener("foreground", styleListener);
  }

  @Override
  public JComponent component() {
    return pane;
  }

  @Override
  public void setMarkdown(String markdown) {
    Objects.requireNonNull(markdown, "markdown");
    if (markdown.equals(this.markdown)) {
      return;
    }
    this.markdown = markdown;
    this.blocks = MarkdownSubset.parse(markdown);
    render();
  }

  @Override
  public void dispose() {
    pane.removePropertyChangeListener("font", styleListener);
    pane.removePropertyChangeListener("foreground", styleListener);
  }

  /** Writes the document again, with the font and the color that the component has at this time. */
  private void render() {
    Font font = pane.getFont();
    if (font == null) {
      font = UIManager.getFont("Label.font");
    }
    if (font == null) {
      font = new Font(Font.DIALOG, Font.PLAIN, 12);
    }
    Color color = pane.getForeground();
    if (color == null) {
      color = Color.BLACK;
    }
    StyleSheet sheet = ((HTMLDocument) pane.getDocument()).getStyleSheet();
    // A rule for a selector replaces the declarations of the rule before it.
    sheet.addRule(
        "body { margin: 0; padding: 0; font-family: "
            + font.getFamily()
            + "; font-size: "
            + font.getSize()
            + "pt; font-weight: "
            + (font.isBold() ? "bold" : "normal")
            + "; font-style: "
            + (font.isItalic() ? "italic" : "normal")
            + "; color: "
            + String.format("#%06x", color.getRGB() & 0xffffff)
            + "; }");
    int indent = MarkdownHtml.listIndent(font.getSize2D());
    for (String list : List.of("ul", "ol")) {
      sheet.addRule(
          list + " { margin-left-ltr: " + indent + "pt; margin-right-rtl: " + indent + "pt; }");
    }
    // The default style sheet gives code a fixed small size.
    for (String code : List.of("code", "pre")) {
      sheet.addRule(code + " { font-family: Monospaced; font-size: " + font.getSize() + "pt; }");
    }
    pane.empty = blocks.isEmpty();
    pane.contentChanged();
    pane.setText(blocks.isEmpty() ? "" : MarkdownHtml.toHtml(blocks, font.getSize2D()));
    pane.revalidate();
    pane.repaint();
  }

  /**
   * The text component of the view.
   *
   * <p>The component gets the size of its text from the root view of its UI. The root view breaks
   * the lines for a width, and it tells the component to do its layout again each time the height
   * of the text changes. A question about a size must not start a new layout, because the answer to
   * that question is a part of a layout. If it did, a window would do its layout again and again.
   * Thus the component ignores the request of the root view during a question, gives the root view
   * its own size back after the question, and keeps the answers.
   */
  private static final class Pane extends JEditorPane implements HeightForWidth {
    private static final long serialVersionUID = 1L;

    /** True if the document has no blocks. Then the component has no height. */
    private boolean empty = true;

    /** True while the component asks the root view for a size. */
    private boolean measuring;

    /** The size of the text with no line breaks, or null if the component must get it again. */
    private Dimension natural;

    /** The width of the longest word, or -1 if the component must get it again. */
    private int narrowest = -1;

    /**
     * The width that the UI of a text component keeps free for the caret, or -1 if the component
     * must get it again. The UI gives the text that much less than the width of the component.
     */
    private int caretSpace = -1;

    /** The last width that the component got a height for, or -1. */
    private int wrappedWidth = -1;

    private int wrappedHeight;

    Pane() {
      setEditorKit(new HTMLEditorKit());
      setEditable(false);
      setFocusable(false);
      setOpaque(false);
      setBorder(null);
      // The caret of a text component makes a scroll pane show the position of the last change.
      if (getCaret() instanceof DefaultCaret caret) {
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
      }
    }

    /** Forgets the sizes of the text, after a change of the document or of the UI. */
    void contentChanged() {
      natural = null;
      caretSpace = -1;
      narrowest = -1;
      wrappedWidth = -1;
    }

    @Override
    public void updateUI() {
      super.updateUI();
      // Some look and feels paint the background of a text component that is not opaque. A
      // background color with no opacity makes that paint show nothing.
      setBackground(new Color(0, 0, 0, 0));
      contentChanged();
    }

    @Override
    public void revalidate() {
      if (!measuring) {
        super.revalidate();
      }
    }

    @Override
    public void repaint(long time, int x, int y, int width, int height) {
      if (!measuring) {
        super.repaint(time, x, y, width, height);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      if (isPreferredSizeSet()) {
        return super.getPreferredSize();
      }
      Insets insets = getInsets();
      Dimension text = naturalSize();
      return new Dimension(
          text.width + caretSpace + insets.left + insets.right,
          text.height + insets.top + insets.bottom);
    }

    @Override
    public Dimension getMinimumSize() {
      if (isMinimumSizeSet()) {
        return super.getMinimumSize();
      }
      // The text can become as narrow as its longest word. Then it has more lines.
      Insets insets = getInsets();
      Dimension text = naturalSize();
      if (narrowest < 0) {
        narrowest =
            empty ? 0 : (int) Math.ceil(getUI().getRootView(this).getMinimumSpan(View.X_AXIS));
      }
      return new Dimension(
          Math.min(text.width, narrowest) + caretSpace + insets.left + insets.right,
          text.height + insets.top + insets.bottom);
    }

    @Override
    public int preferredHeightFor(int width) {
      if (isPreferredSizeSet()) {
        return super.getPreferredSize().height;
      }
      Insets insets = getInsets();
      Dimension text = naturalSize();
      int contentWidth = Math.max(1, width - insets.left - insets.right - caretSpace);
      int height = text.height;
      if (contentWidth < text.width) {
        if (wrappedWidth != contentWidth) {
          wrappedHeight = measure(contentWidth).height;
          wrappedWidth = contentWidth;
        }
        height = wrappedHeight;
      }
      return height + insets.top + insets.bottom;
    }

    /** Gets the size of the text with no line breaks, without the insets. */
    private Dimension naturalSize() {
      if (natural == null) {
        natural = measure(Integer.MAX_VALUE);
        caretSpace = empty ? 0 : caretSpace();
      }
      return natural;
    }

    /** Gets the width that the UI keeps free for the caret, from the preferred size of the UI. */
    private int caretSpace() {
      measuring = true;
      try {
        Insets insets = getInsets();
        // The UI adds the space of the caret to the width of the text in the root view.
        int withCaret = getUI().getPreferredSize(this).width - insets.left - insets.right;
        int text = (int) getUI().getRootView(this).getPreferredSpan(View.X_AXIS);
        return Math.max(0, Math.min(withCaret - text, 16));
      } finally {
        measuring = false;
      }
    }

    /**
     * Asks the root view for the size of the text when the lines have a maximum width.
     *
     * @param contentWidth the maximum width of a line
     * @return the size of the text, without the insets
     */
    private Dimension measure(int contentWidth) {
      if (empty) {
        return new Dimension(0, 0);
      }
      View root = getUI().getRootView(this);
      measuring = true;
      try {
        root.setSize(contentWidth, Integer.MAX_VALUE);
        Dimension size =
            new Dimension(
                (int) Math.min(contentWidth, Math.ceil(root.getPreferredSpan(View.X_AXIS))),
                (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)));
        Insets insets = getInsets();
        int width = getWidth() - insets.left - insets.right;
        int height = getHeight() - insets.top - insets.bottom;
        if (width > 0 && height > 0) {
          root.setSize(width, height);
        }
        return size;
      } finally {
        measuring = false;
      }
    }
  }
}
