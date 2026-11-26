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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class MarkdownHtmlTest {
  private static final String FIRST = " style=\"margin-top:0pt;margin-bottom:0pt\"";
  private static final String NEXT = " style=\"margin-top:6pt;margin-bottom:0pt\"";

  /** Converts Markdown to HTML with a base font size of 10, and removes the document tags. */
  private static String html(String markdown) {
    String html = MarkdownHtml.toHtml(MarkdownSubset.parse(markdown), 10f);
    assertEquals("<html><body>", html.substring(0, 12));
    assertEquals("</body></html>", html.substring(html.length() - 14));
    return html.substring(12, html.length() - 14);
  }

  @Test
  void aParagraphHasTheInlineTags() {
    assertEquals(
        "<p" + FIRST + ">This is <em>italic</em> and <strong>bold</strong> text.</p>",
        html("This is *italic* and **bold** text."));
    assertEquals(
        "<p" + FIRST + ">Use <code>a2ui</code><br>was <strike>10</strike></p>",
        html("Use `a2ui`  \nwas ~~10~~"));
  }

  @Test
  void theFirstBlockHasNoSpaceAboveItAndEachOtherBlockHasTheGap() {
    assertEquals("<p" + FIRST + ">one</p><p" + NEXT + ">two</p>", html("one\n\ntwo"));
  }

  @Test
  void aHeadingHasAFontSizeInProportionToTheBaseSize() {
    assertEquals(
        "<h1 style=\"font-size:20pt;font-weight:bold;margin-top:0pt;margin-bottom:0pt\">"
            + "Header 1</h1>"
            + "<h2 style=\"font-size:16pt;font-weight:bold;margin-top:9pt;margin-bottom:0pt\">"
            + "Header 2</h2>"
            + "<h6 style=\"font-size:10pt;font-weight:bold;margin-top:9pt;margin-bottom:0pt\">"
            + "Six</h6>",
        html("# Header 1\n## Header 2\n###### Six"));
  }

  @Test
  void aListHasOneTagForEachItemAndAListInAnItem() {
    assertEquals(
        "<ul"
            + FIRST
            + "><li>Item 1<ol start=\"3\""
            + FIRST
            + "><li>three</li></ol></li>"
            + "<li>Item 2</li></ul>"
            + "<ol"
            + NEXT
            + "><li>one</li></ol>",
        html("- Item 1\n  3. three\n- Item 2\n\n1. one"));
  }

  @Test
  void aCodeBlockAQuoteAndARuleHaveTheirTags() {
    assertEquals(
        "<pre"
            + FIRST
            + ">a &lt;b&gt;\n  c</pre>"
            + "<blockquote style=\"margin-top:6pt;margin-bottom:0pt;margin-left:18pt;"
            + "margin-right:0pt\"><p"
            + FIRST
            + ">quote</p></blockquote>"
            + "<hr>",
        html("```\na <b>\n  c\n```\n> quote\n\n---"));
  }

  @Test
  void theWriterEscapesEachTextOfTheDocument() {
    assertEquals(
        "<p" + FIRST + ">Hello &lt;span&gt;world&lt;/span&gt; &amp; &quot;you&quot;</p>",
        html("Hello <span>world</span> & \"you\""));
    assertEquals(
        "<p" + FIRST + ">&lt;script&gt;alert(1)&lt;/script&gt;<code>&lt;img src=x&gt;</code></p>",
        html("<script>alert(1)</script>`<img src=x>`"));
    assertEquals("<p" + FIRST + ">&lt;b&gt; &amp;amp;</p>", html("&lt;b&gt; &amp;amp;"));
    assertEquals("<p" + FIRST + ">ab</p>", html("a\u0000\u0007\u007fb"));
  }

  @Test
  void theOutputHasNoLinkAndNoImage() {
    String html =
        html(
            "Visit [Documentation](https://example.com) and ![photo](https://example.com/pic.png)"
                + " and <https://example.com/auto> and <a href=\"x\">raw</a>");

    assertEquals(
        "<p"
            + FIRST
            + ">Visit Documentation and photo and https://example.com/auto and"
            + " &lt;a href=&quot;x&quot;&gt;raw&lt;/a&gt;</p>",
        html);
    assertFalse(html.contains("<a"), html);
    assertFalse(html.contains("<img"), html);
  }

  @Test
  void anEmptyDocumentHasNoBlocks() {
    assertEquals("", html(""));
  }
}
