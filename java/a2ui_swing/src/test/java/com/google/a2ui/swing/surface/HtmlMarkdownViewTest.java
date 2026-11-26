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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.Snapshots;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import org.junit.jupiter.api.Test;

class HtmlMarkdownViewTest {
  private static final String LONG_TEXT =
      "A text with many words that does not fit in one line of a small component, and thus the"
          + " component must break it into lines.";

  private final HtmlMarkdownView view = new HtmlMarkdownView();
  private final JEditorPane pane = (JEditorPane) view.component();

  /** Gets the text that the component shows, with one space for each run of white space. */
  private String shownText() throws BadLocationException {
    Document document = pane.getDocument();
    return document.getText(0, document.getLength()).replaceAll("\\s+", " ").strip();
  }

  /** Gets the name of each HTML tag of the document: the block tags and the inline tags. */
  private Set<String> tags() {
    Set<String> tags = new TreeSet<>();
    collect(pane.getDocument().getDefaultRootElement(), tags);
    tags.removeAll(List.of("html", "head", "body", "content", "p-implied"));
    return tags;
  }

  private static void collect(Element element, Set<String> tags) {
    AttributeSet attributes = element.getAttributes();
    tags.add(String.valueOf(attributes.getAttribute(StyleConstants.NameAttribute)));
    attributes
        .getAttributeNames()
        .asIterator()
        .forEachRemaining(name -> {
          if (name instanceof HTML.Tag tag) {
            tags.add(tag.toString());
          }
        });
    for (int i = 0; i < element.getElementCount(); i++) {
      collect(element.getElement(i), tags);
    }
  }

  @Test
  void theComponentIsATextComponentThatTheUserCannotEdit() {
    Edt.run(() -> {
      assertFalse(pane.isEditable());
      assertFalse(pane.isFocusable());
      assertFalse(pane.isOpaque());
      assertNull(pane.getBorder());
      assertEquals("text/html", pane.getContentType());
      assertSame(pane, view.component());
      assertTrue(pane instanceof HeightForWidth);
    });
  }

  @Test
  void theViewShowsTheTextWithoutTheMarksAndWithTheTagsOfTheSubset() {
    Edt.run(() -> {
      view.setMarkdown(
          "# Title\n\nThis is *italic* and **bold** with `code`.\n\n- Item 1\n- Item 2\n\n1. one");

      assertEquals("Title This is italic and bold with code. Item 1 Item 2 one", shownText());
      assertEquals(Set.of("code", "em", "h1", "li", "ol", "p", "strong", "ul"), tags());
    });
  }

  @Test
  void htmlInTheTextIsVisibleTextAndNotATag() {
    Edt.run(() -> {
      view.setMarkdown("Hello <span>world</span> <script>x</script> <b>bold</b> &lt;i&gt;");

      assertEquals("Hello <span>world</span> <script>x</script> <b>bold</b> <i>", shownText());
      assertEquals(Set.of("p"), tags());
    });
  }

  @Test
  void aLinkAndAnImageAreTextOnly() {
    Edt.run(() -> {
      view.setMarkdown("Visit [Documentation](https://example.com) ![photo](https://x.test/p.png)");

      assertEquals("Visit Documentation photo", shownText());
      assertEquals(Set.of("p"), tags());
    });
  }

  @Test
  void aNewTextReplacesTheTextBefore() {
    Edt.run(() -> {
      view.setMarkdown("## One");
      view.setMarkdown("two");

      assertEquals("two", shownText());
      assertEquals(Set.of("p"), tags());

      view.setMarkdown("");

      assertEquals("", shownText());
      assertEquals(new Dimension(0, 0), pane.getPreferredSize());
    });
  }

  @Test
  void theSameTextDoesNotChangeTheDocument() {
    Edt.run(() -> {
      view.setMarkdown("same **text**");
      List<String> events = new ArrayList<>();
      pane.getDocument()
          .addDocumentListener(
              new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent event) {
                  events.add("insert");
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent event) {
                  events.add("remove");
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent event) {
                  events.add("change");
                }
              });

      view.setMarkdown("same **text**");

      assertEquals(List.of(), events);
    });
  }

  @Test
  void thePreferredSizeIsTheSizeOfTheTextWithNoLineBreaks() {
    Edt.run(() -> {
      view.setMarkdown(LONG_TEXT);
      int lineHeight = pane.getFontMetrics(pane.getFont()).getHeight();
      int lineWidth = pane.getFontMetrics(pane.getFont()).stringWidth(LONG_TEXT);

      Dimension preferred = pane.getPreferredSize();

      assertEquals(lineHeight, preferred.height, 2);
      assertEquals(lineWidth, preferred.width, 4);
      assertEquals(preferred.height, ((HeightForWidth) pane).preferredHeightFor(preferred.width));
      assertEquals(preferred.height, ((HeightForWidth) pane).preferredHeightFor(5000));
    });
  }

  @Test
  void theTextBreaksAtTheWidthOfTheComponentAndTheHeightFollows() {
    Edt.run(() -> {
      view.setMarkdown(LONG_TEXT);
      HeightForWidth wrapping = (HeightForWidth) pane;
      int oneLine = pane.getPreferredSize().height;
      int lineWidth = pane.getPreferredSize().width;

      int half = wrapping.preferredHeightFor(lineWidth / 2 + 20);
      int quarter = wrapping.preferredHeightFor(lineWidth / 4 + 20);

      assertEquals(2 * oneLine, half, 2);
      // Words do not break. Thus the text can need one line more than its length gives.
      assertTrue(quarter >= 4 * oneLine - 4 && quarter <= 5 * oneLine + 4, "height " + quarter);
      // The answer does not depend on the size that the component has.
      pane.setSize(50, 50);
      assertEquals(half, wrapping.preferredHeightFor(lineWidth / 2 + 20));
      assertEquals(new Dimension(lineWidth, oneLine), pane.getPreferredSize());
    });
  }

  @Test
  void aQuestionAboutASizeDoesNotStartANewLayout() {
    Edt.run(() -> {
      view.setMarkdown(LONG_TEXT);
      JPanel parent = new JPanel(new BorderLayout());
      JPanel holder = new JPanel(null);
      holder.add(parent);
      parent.add(pane);
      parent.addNotify();
      parent.setSize(200, 200);
      parent.validate();
      HeightForWidth wrapping = (HeightForWidth) pane;
      Dimension natural = pane.getPreferredSize();

      assertTrue(parent.isValid());
      assertTrue(pane.isValid());

      int narrow = wrapping.preferredHeightFor(90);
      int wide = wrapping.preferredHeightFor(150);

      // The root view of a text component asks for a new layout when the height of its text
      // changes. A window would then do its layout again after each layout.
      assertTrue(narrow > wide && wide > natural.height);
      assertEquals(natural, pane.getPreferredSize());
      assertTrue(pane.getMinimumSize().width < 90);
      assertTrue(pane.isValid(), "the component");
      assertTrue(parent.isValid(), "the parent");

      // The root view has the size of the component again. Thus a paint changes nothing.
      Snapshots.render(pane);

      assertTrue(pane.isValid(), "the component after the paint");
      assertTrue(parent.isValid(), "the parent after the paint");
    });
  }

  @Test
  void theMinimumWidthIsTheWidthOfTheLongestWord() {
    Edt.run(() -> {
      view.setMarkdown("a b incomprehensibilities c d");
      int wordWidth = pane.getFontMetrics(pane.getFont()).stringWidth("incomprehensibilities");

      assertEquals(wordWidth, pane.getMinimumSize().width, 4);
      assertTrue(pane.getMinimumSize().width < pane.getPreferredSize().width);
    });
  }

  @Test
  void theSizesHaveTheInsetsOfABorder() {
    Edt.run(() -> {
      view.setMarkdown(LONG_TEXT);
      Dimension plain = pane.getPreferredSize();
      int wrapped = ((HeightForWidth) pane).preferredHeightFor(200);

      pane.setBorder(BorderFactory.createEmptyBorder(3, 5, 7, 11));

      assertEquals(new Dimension(plain.width + 16, plain.height + 10), pane.getPreferredSize());
      assertEquals(wrapped + 10, ((HeightForWidth) pane).preferredHeightFor(216));
    });
  }

  @Test
  void theFontOfTheComponentIsTheBaseFontOfTheText() {
    Edt.run(() -> {
      Font base = new Font(Font.SERIF, Font.PLAIN, 30);
      Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 30);
      Font heading = new Font(Font.SERIF, Font.BOLD, 60);
      view.setMarkdown("plain");

      pane.setFont(base);

      assertEquals(pane.getFontMetrics(base).getHeight(), pane.getPreferredSize().height, 2);
      assertEquals(
          pane.getFontMetrics(base).stringWidth("plain"), pane.getPreferredSize().width, 2);

      view.setMarkdown("`iiii`");

      assertEquals(pane.getFontMetrics(mono).stringWidth("iiii"), pane.getPreferredSize().width, 2);

      view.setMarkdown("# Big");

      assertEquals(pane.getFontMetrics(heading).getHeight(), pane.getPreferredSize().height, 3);
      assertEquals(
          pane.getFontMetrics(heading).stringWidth("Big"), pane.getPreferredSize().width, 3);
    });
  }

  @Test
  void theForegroundColorOfTheComponentIsTheColorOfTheText() {
    Edt.run(() -> {
      Color color = new Color(0x12, 0xc4, 0x56);
      view.setMarkdown("# IIII **IIII** `IIII`");
      pane.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
      pane.setBackground(Color.WHITE);

      pane.setForeground(color);

      pane.setSize(pane.getPreferredSize());
      BufferedImage image = Snapshots.render(pane);
      Set<Integer> colors = new TreeSet<>();
      for (int x = 0; x < image.getWidth(); x++) {
        colors.add(image.getRGB(x, image.getHeight() / 2) & 0xffffff);
      }
      assertTrue(colors.contains(0x12c456), colors.toString());
      assertFalse(colors.contains(0x000000), colors.toString());
      assertEquals("IIII IIII IIII", shownText());
    });
  }

  @Test
  void aDisposedViewDoesNotFollowTheStyleOfTheComponent() {
    Edt.run(() -> {
      view.setMarkdown("text");
      int listeners = pane.getPropertyChangeListeners("font").length;

      view.dispose();

      assertEquals(listeners - 1, pane.getPropertyChangeListeners("font").length);
      assertEquals(0, pane.getPropertyChangeListeners("foreground").length);
    });
  }
}
