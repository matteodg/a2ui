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

import com.google.a2ui.swing.surface.MarkdownSubset.Block;
import com.google.a2ui.swing.surface.MarkdownSubset.Inline;
import com.google.a2ui.swing.surface.MarkdownSubset.ListBlock;
import java.util.List;

/**
 * Writes the blocks of a Markdown document as the HTML that the Swing HTML view shows.
 *
 * <p>The writer makes each tag, and it escapes each text of the document. Thus HTML that the agent
 * wrote in the Markdown text is never a tag of the output. The output has no link, no image, no
 * script and no attribute that has a value from the document.
 *
 * <p>Each size in the output is in points. In the Swing HTML view, one point is one unit of the
 * font size of a Swing component. The first block has no space above it and the last block has no
 * space below it. Thus the view has no margin of its own.
 */
final class MarkdownHtml {
  /** The font size of each heading level, as a multiple of the base font size. */
  private static final float[] HEADING_SCALE = {2.0f, 1.6f, 1.35f, 1.2f, 1.1f, 1.0f};

  private MarkdownHtml() {}

  /**
   * Writes a document.
   *
   * @param blocks the blocks of the document
   * @param fontSize the base font size of the text
   * @return the HTML document
   */
  static String toHtml(List<Block> blocks, float fontSize) {
    StringBuilder out = new StringBuilder("<html><body>");
    blocks(blocks, fontSize, out);
    return out.append("</body></html>").toString();
  }

  /**
   * Gets the indent of the items of a list.
   *
   * @param fontSize the base font size of the text
   * @return the indent in points
   */
  static int listIndent(float fontSize) {
    return Math.round(fontSize * 1.75f);
  }

  private static void blocks(List<Block> blocks, float fontSize, StringBuilder out) {
    int gap = Math.round(fontSize * 0.6f);
    for (int i = 0; i < blocks.size(); i++) {
      block(blocks.get(i), i == 0 ? 0 : gap, fontSize, out);
    }
  }

  private static void block(Block block, int top, float fontSize, StringBuilder out) {
    String margin = "margin-top:" + top + "pt;margin-bottom:0pt";
    switch (block) {
      case MarkdownSubset.Heading heading -> {
        float scale = HEADING_SCALE[heading.level() - 1];
        // A heading that is not the first block has more space above it than a paragraph.
        int above = top == 0 ? 0 : Math.round(top * 1.5f);
        out.append("<h").append(heading.level());
        out.append(" style=\"font-size:").append(Math.round(fontSize * scale));
        out.append("pt;font-weight:bold;margin-top:").append(above).append("pt;margin-bottom:0pt");
        out.append("\">");
        inlines(heading.content(), out);
        out.append("</h").append(heading.level()).append('>');
      }
      case MarkdownSubset.Paragraph paragraph -> {
        out.append("<p style=\"").append(margin).append("\">");
        inlines(paragraph.content(), out);
        out.append("</p>");
      }
      case ListBlock list -> list(list, top, out);
      case MarkdownSubset.CodeBlock code -> {
        out.append("<pre style=\"").append(margin).append("\">");
        escape(code.code(), out);
        out.append("</pre>");
      }
      case MarkdownSubset.Quote quote -> {
        out.append("<blockquote style=\"").append(margin);
        out.append(";margin-left:").append(listIndent(fontSize)).append("pt;margin-right:0pt\">");
        blocks(quote.blocks(), fontSize, out);
        out.append("</blockquote>");
      }
      case MarkdownSubset.Rule rule -> out.append("<hr>");
    }
  }

  private static void list(ListBlock list, int top, StringBuilder out) {
    String tag = list.ordered() ? "ol" : "ul";
    out.append('<').append(tag);
    if (list.ordered() && list.start() != 1) {
      out.append(" start=\"").append(list.start()).append('"');
    }
    out.append(" style=\"margin-top:").append(top).append("pt;margin-bottom:0pt\">");
    for (MarkdownSubset.Item item : list.items()) {
      out.append("<li>");
      inlines(item.content(), out);
      item.lists().forEach(nested -> list(nested, 0, out));
      out.append("</li>");
    }
    out.append("</").append(tag).append('>');
  }

  private static void inlines(List<Inline> content, StringBuilder out) {
    for (Inline inline : content) {
      switch (inline) {
        case MarkdownSubset.Text text -> escape(text.text(), out);
        case MarkdownSubset.Emphasis emphasis -> wrap("em", emphasis.content(), out);
        case MarkdownSubset.Strong strong -> wrap("strong", strong.content(), out);
        case MarkdownSubset.Strike strike -> wrap("strike", strike.content(), out);
        case MarkdownSubset.Code code -> {
          out.append("<code>");
          escape(code.code(), out);
          out.append("</code>");
        }
        case MarkdownSubset.LineBreak lineBreak -> out.append("<br>");
      }
    }
  }

  private static void wrap(String tag, List<Inline> content, StringBuilder out) {
    out.append('<').append(tag).append('>');
    inlines(content, out);
    out.append("</").append(tag).append('>');
  }

  /**
   * Writes a text of the document. The HTML parser reads no character of the result as markup.
   *
   * @param text the text
   * @param out receives the escaped text
   */
  static void escape(String text, StringBuilder out) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\n', '\t' -> out.append(c);
        default -> {
          // A control character has no glyph, and the parser can stop at one.
          if (c >= ' ' && c != 0x7f) {
            out.append(c);
          }
        }
      }
    }
  }
}
