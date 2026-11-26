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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.surface.MarkdownSubset.Block;
import com.google.a2ui.swing.surface.MarkdownSubset.Code;
import com.google.a2ui.swing.surface.MarkdownSubset.CodeBlock;
import com.google.a2ui.swing.surface.MarkdownSubset.Emphasis;
import com.google.a2ui.swing.surface.MarkdownSubset.Heading;
import com.google.a2ui.swing.surface.MarkdownSubset.Inline;
import com.google.a2ui.swing.surface.MarkdownSubset.Item;
import com.google.a2ui.swing.surface.MarkdownSubset.LineBreak;
import com.google.a2ui.swing.surface.MarkdownSubset.ListBlock;
import com.google.a2ui.swing.surface.MarkdownSubset.Paragraph;
import com.google.a2ui.swing.surface.MarkdownSubset.Quote;
import com.google.a2ui.swing.surface.MarkdownSubset.Rule;
import com.google.a2ui.swing.surface.MarkdownSubset.Strike;
import com.google.a2ui.swing.surface.MarkdownSubset.Strong;
import com.google.a2ui.swing.surface.MarkdownSubset.Text;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownSubsetTest {
  private static Text text(String text) {
    return new Text(text);
  }

  private static Paragraph paragraph(Inline... content) {
    return new Paragraph(List.of(content));
  }

  private static Emphasis emphasis(Inline... content) {
    return new Emphasis(List.of(content));
  }

  private static Strong strong(Inline... content) {
    return new Strong(List.of(content));
  }

  private static Item item(String text, ListBlock... lists) {
    return new Item(List.of(text(text)), List.of(lists));
  }

  private static List<Inline> inlines(String markdown) {
    return MarkdownSubset.inlines(markdown);
  }

  private static void assertPlain(String markdown) {
    assertEquals(List.of(text(markdown)), inlines(markdown), markdown);
  }

  // Blocks.

  @Test
  void textWithNoMarksIsOneParagraph() {
    assertEquals(List.of(paragraph(text("Hello world"))), MarkdownSubset.parse("Hello world"));
    assertEquals(List.of(), MarkdownSubset.parse(""));
    assertEquals(List.of(), MarkdownSubset.parse(" \n\n  "));
  }

  @Test
  void anEmptyLineSeparatesTwoParagraphsAndALineBreakOfTheSourceIsASpace() {
    assertEquals(
        List.of(paragraph(text("one two")), paragraph(text("three"))),
        MarkdownSubset.parse("one\r\ntwo\n\n\nthree\n"));
  }

  @Test
  void twoSpacesOrABackslashAtTheEndOfALineMakeALineBreak() {
    assertEquals(
        List.of(paragraph(text("one"), new LineBreak(), text("two"), new LineBreak(), text("3"))),
        MarkdownSubset.parse("one  \ntwo\\\n  3"));
  }

  @Test
  void aLineOfOneToSixHashMarksAndASpaceIsAHeading() {
    assertEquals(
        List.of(
            new Heading(1, List.of(text("Header 1"))),
            new Heading(2, List.of(text("Header 2"))),
            new Heading(6, List.of(text("Six"))),
            paragraph(text("####### seven #hashtag"))),
        MarkdownSubset.parse("# Header 1\n## Header 2\n ###### Six ##\n####### seven\n#hashtag"));
    assertEquals(List.of(new Heading(3, List.of())), MarkdownSubset.parse("###"));
  }

  @Test
  void aHeadingHasInlineElementsAndEndsAParagraph() {
    assertEquals(
        List.of(
            paragraph(text("intro")),
            new Heading(2, List.of(text("A "), strong(text("bold")), text(" title"))),
            paragraph(text("body"))),
        MarkdownSubset.parse("intro\n## A **bold** title\nbody"));
  }

  @Test
  void aLineOfEqualSignsOrOfHyphensBelowATextMakesAHeading() {
    assertEquals(
        List.of(
            new Heading(1, List.of(text("Title"))),
            new Heading(2, List.of(text("Two lines"))),
            new Rule()),
        MarkdownSubset.parse("Title\n===\nTwo\nlines\n---\n\n---"));
  }

  @Test
  void aListHasOneItemForEachMarkedLine() {
    ListBlock expected =
        new ListBlock(false, 1, List.of(item("Item 1"), item("Item 2"), item("Item 3")));

    assertEquals(List.of(expected), MarkdownSubset.parse("- Item 1\n- Item 2\n- Item 3"));
    assertEquals(List.of(expected), MarkdownSubset.parse("* Item 1\n+ Item 2\n\n- Item 3"));
  }

  @Test
  void anOrderedListKeepsItsFirstNumber() {
    assertEquals(
        List.of(new ListBlock(true, 3, List.of(item("three"), item("four")))),
        MarkdownSubset.parse("3. three\n4) four"));
  }

  @Test
  void aListEndsAParagraphAndALineWithNoMarkContinuesAnItem() {
    assertEquals(
        List.of(
            paragraph(text("Items:")),
            new ListBlock(false, 1, List.of(item("first line"), item("second"))),
            paragraph(text("after"))),
        MarkdownSubset.parse("Items:\n- first\n  line\n- second\n\nafter"));
  }

  @Test
  void anItemWithMoreIndentIsAListInTheItemBeforeIt() {
    ListBlock inner = new ListBlock(true, 1, List.of(item("one"), item("two")));
    ListBlock deep = new ListBlock(false, 1, List.of(item("deep")));

    assertEquals(
        List.of(
            new ListBlock(
                false,
                1,
                List.of(
                    item("a", inner),
                    item("b", new ListBlock(false, 1, List.of(item("c", deep)))),
                    item("d")))),
        MarkdownSubset.parse("- a\n  1. one\n  2. two\n- b\n    - c\n        - deep\n- d"));
  }

  @Test
  void aListOfADifferentKindAtTheSameLevelIsANewList() {
    assertEquals(
        List.of(
            new ListBlock(false, 1, List.of(item("a"))),
            new ListBlock(true, 1, List.of(item("b")))),
        MarkdownSubset.parse("- a\n1. b"));
  }

  @Test
  void textThatOnlyLooksLikeAListIsAParagraph() {
    assertEquals(List.of(paragraph(text("-5 degrees"))), MarkdownSubset.parse("-5 degrees"));
    assertEquals(List.of(paragraph(text("-"))), MarkdownSubset.parse("-"));
    assertEquals(List.of(paragraph(text("3.14 is pi"))), MarkdownSubset.parse("3.14 is pi"));
    assertEquals(
        List.of(paragraph(text("It was in 1986. A good year."))),
        MarkdownSubset.parse("It was in\n1986. A good year."));
    assertEquals(
        List.of(paragraph(strong(text("bold")), text(" start"))),
        MarkdownSubset.parse("**bold** start"));
  }

  @Test
  void aCodeBlockKeepsItsTextAsItIs() {
    assertEquals(
        List.of(
            paragraph(text("Run:")),
            new CodeBlock("a2ui **start**\n  <b>--now</b>"),
            paragraph(text("done"))),
        MarkdownSubset.parse("Run:\n```sh\na2ui **start**\n  <b>--now</b>\n```\ndone"));
    assertEquals(List.of(new CodeBlock("no end")), MarkdownSubset.parse("~~~\nno end"));
  }

  @Test
  void aQuoteHasBlocks() {
    assertEquals(
        List.of(
            new Quote(
                List.of(
                    paragraph(text("first second")),
                    new ListBlock(false, 1, List.of(item("item"))))),
            paragraph(text("after"))),
        MarkdownSubset.parse("> first\n> second\n>\n> - item\nafter"));
  }

  @Test
  void aLineOfThreeOrMoreMarksIsARule() {
    assertEquals(
        List.of(paragraph(text("a")), new Rule(), new Rule(), new Rule()),
        MarkdownSubset.parse("a\n\n---\n* * *\n_____"));
  }

  // Inline elements.

  @Test
  void oneMarkIsEmphasisAndTwoMarksAreStrongText() {
    assertEquals(
        List.of(
            text("This is "),
            emphasis(text("italic")),
            text(" and "),
            strong(text("bold")),
            text(" text.")),
        inlines("This is *italic* and **bold** text."));
    assertEquals(
        List.of(emphasis(text("italic")), text(" "), strong(text("bold"))),
        inlines("_italic_ __bold__"));
  }

  @Test
  void elementsCanBeInElements() {
    assertEquals(
        List.of(strong(text("bold "), emphasis(text("and italic")), text(" text"))),
        inlines("**bold *and italic* text**"));
    assertEquals(List.of(emphasis(strong(text("both")))), inlines("***both***"));
    assertEquals(
        List.of(emphasis(text("a "), new Code("b*c"), text(" d"))), inlines("*a `b*c` d*"));
  }

  @Test
  void aMarkThatHasNoPartnerOrThatIsNotAtAWordIsText() {
    assertPlain("2 * 3 * 4 = 24");
    assertPlain("snake_case_name and file_name.txt");
    assertPlain("a * b");
    assertPlain("**not closed");
    assertPlain("agreed=false choice=[\"opt_a\"] level=10");
    assertPlain("price: $5 ~ $10, ~approx~");
    assertEquals(List.of(text("a"), emphasis(text("b")), text("c")), inlines("a*b*c"));
    assertEquals(List.of(text("*"), emphasis(text("a"))), inlines("**a*"));
  }

  @Test
  void twoTildesMakeTextWithALineThroughIt() {
    assertEquals(
        List.of(text("was "), new Strike(List.of(text("10"))), text(" now 8")),
        inlines("was ~~10~~ now 8"));
  }

  @Test
  void aCodeSpanKeepsItsTextAsItIs() {
    assertEquals(
        List.of(text("Use the "), new Code("a2ui"), text(" command to start.")),
        inlines("Use the `a2ui` command to start."));
    assertEquals(List.of(new Code("a ` b")), inlines("`` a ` b ``"));
    assertEquals(List.of(new Code("**x** <i>")), inlines("`**x** <i>`"));
    assertPlain("a ` b");
  }

  @Test
  void aBackslashBeforeAMarkMakesTheMarkText() {
    assertEquals(
        List.of(text("*not italic* and # and \\d")), inlines("\\*not italic\\* and \\# and \\d"));
  }

  @Test
  void htmlIsText() {
    assertPlain("Hello <span>world</span>");
    assertPlain("<script>alert(1)</script> <img src=x onerror=y> <!-- c -->");
    assertEquals(List.of(text("<b>"), emphasis(text("x")), text("</b>")), inlines("<b>*x*</b>"));
  }

  @Test
  void aLinkAndAnImageAreTheirTextOnly() {
    assertEquals(
        List.of(text("Visit Documentation for details.")),
        inlines("Visit [Documentation](https://example.com) for details."));
    assertEquals(
        List.of(text("Look at photo")), inlines("Look at ![photo](https://example.com/pic.png)"));
    assertEquals(
        List.of(text("a "), strong(text("bold")), text(" link (x) b")),
        inlines("a [**bold** link \\(x\\)](https://example.com/a_(b) \"title )\") b"));
    assertEquals(List.of(text("badge")), inlines("[![badge](i.png)](https://example.com)"));
    assertEquals(
        List.of(text("see https://example.com/a_b now")),
        inlines("see <https://example.com/a_b> now"));
  }

  @Test
  void bracketsThatAreNotALinkAreText() {
    assertPlain("[not a link] (text)");
    assertPlain("array[0](x");
    assertPlain("wow! [x]");
    assertPlain("[ref][1]");
  }

  @Test
  void aCharacterReferenceIsItsCharacter() {
    assertEquals(
        List.of(text("a & b < c > d \" ' é € &unknown; &")),
        inlines("a &amp; b &lt; c &gt; d &quot; &apos; &#233; &#x20AC; &unknown; &"));
  }

  @Test
  void theParserAcceptsEachText() {
    String nested = "[".repeat(2000) + "x" + "](y)".repeat(2000);
    String quotes = ">".repeat(3000) + " deep";
    String marks = "*_".repeat(3000) + "x" + "_*".repeat(3000);
    String items = "- a\n".repeat(2000);

    for (String text : List.of(nested, quotes, marks, "```", "> ", "- ", "#", "[](", "![", "\\")) {
      List<Block> blocks = MarkdownSubset.parse(text);
      assertTrue(blocks.size() <= 1, text);
    }
    assertEquals(2000, ((ListBlock) MarkdownSubset.parse(items).getFirst()).items().size());
  }
}
