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
    pane.setText(blocks.isEmpty() ? "" : MarkdownHtml.toHtml(blocks, font.getSize2D()));
    pane.revalidate();
    pane.repaint();
  }

  /** The text component of the view. */
  private static final class Pane extends JEditorPane implements HeightForWidth {
    private static final long serialVersionUID = 1L;

    /** True if the document has no blocks. Then the component has no height. */
    private boolean empty = true;

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

    @Override
    public Dimension getPreferredSize() {
      return isPreferredSizeSet() ? super.getPreferredSize() : measure(Integer.MAX_VALUE);
    }

    @Override
    public Dimension getMinimumSize() {
      if (isMinimumSizeSet()) {
        return super.getMinimumSize();
      }
      // The text can become as narrow as its longest word. Then it has more lines.
      Dimension preferred = getPreferredSize();
      Insets insets = getInsets();
      int width =
          empty ? 0 : (int) Math.ceil(getUI().getRootView(this).getMinimumSpan(View.X_AXIS));
      return new Dimension(
          Math.min(preferred.width, width + insets.left + insets.right), preferred.height);
    }

    @Override
    public int preferredHeightFor(int width) {
      if (isPreferredSizeSet()) {
        return super.getPreferredSize().height;
      }
      Insets insets = getInsets();
      return measure(Math.max(1, width - insets.left - insets.right)).height;
    }

    /**
     * Gets the size of the text when the lines have a maximum width.
     *
     * @param contentWidth the maximum width of a line, without the insets
     * @return the size of the component, with the insets
     */
    private Dimension measure(int contentWidth) {
      Insets insets = getInsets();
      int width = 0;
      int height = 0;
      if (!empty) {
        // The paint of the component sets the size of the root view again.
        View root = getUI().getRootView(this);
        root.setSize(contentWidth, Integer.MAX_VALUE);
        width = (int) Math.min(contentWidth, Math.ceil(root.getPreferredSpan(View.X_AXIS)));
        height = (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS));
      }
      return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
    }
  }
}
