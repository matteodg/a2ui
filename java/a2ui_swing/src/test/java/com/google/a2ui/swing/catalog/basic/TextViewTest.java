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

import static com.google.a2ui.swing.testing.TestSurface.component;
import static com.google.a2ui.swing.testing.TestSurface.object;
import static com.google.a2ui.swing.testing.TestSurface.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.MarkdownView;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.LogCapture;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class TextViewTest {
  private static final String LONG_TEXT =
      "A text with many words that does not fit in one line of the surface, and thus the view"
          + " must break it into lines.";

  private BasicFixture fixture = new BasicFixture();

  /** Makes a surface whose root is a text. */
  private void show(Map<String, Object> data, Object... properties) {
    fixture.create(data, component("root", "Text", properties));
  }

  /** Gets the text component of the Markdown view of the root. */
  private JComponent content() {
    return fixture.viewOf("root").accessibleComponent();
  }

  private String shownText() {
    return VisibleText.of(fixture.componentOf("root"));
  }

  /** A Markdown view of an application: a label that shows the text in upper case. */
  private static final class UpperCaseView implements MarkdownView {
    final JLabel label = new JLabel();
    boolean disposed;
    boolean fails;

    @Override
    public JComponent component() {
      return label;
    }

    @Override
    public void setMarkdown(String markdown) {
      if (fails) {
        throw new IllegalStateException("The view cannot show this text.");
      }
      label.setText(markdown.toUpperCase(java.util.Locale.ROOT));
    }

    @Override
    public void dispose() {
      disposed = true;
    }
  }

  @Test
  void theViewShowsTheTextAsMarkdown() {
    Edt.run(() -> {
      show(Map.of(), "text", "## Title\n\nThis is *italic* and **bold**.");

      JEditorPane pane = (JEditorPane) content();
      assertEquals("Title This is italic and bold.", shownText());
      assertEquals("text/html", pane.getContentType());
      assertFalse(pane.isEditable());
      assertSame(fixture.componentOf("root"), pane.getParent());
    });
  }

  @Test
  void htmlInTheTextIsVisibleTextAndALinkIsItsTextOnly() {
    Edt.run(() -> {
      show(Map.of(), "text", "<b>Hi</b> <script>x()</script> [docs](https://example.com)");

      assertEquals("<b>Hi</b> <script>x()</script> docs", shownText());
    });
  }

  @Test
  void aValueThatIsNotAStringShowsAsText() {
    Edt.run(() -> {
      show(Map.of("count", 42L), "text", path("/count"));

      assertEquals("42", shownText());

      fixture.setData("/count", null);

      assertEquals("", shownText());
      assertEquals(0, content().getPreferredSize().height);
    });
  }

  @Test
  void anUpdateOfTheTextKeepsTheSwingComponents() {
    Edt.run(() -> {
      show(Map.of("status", "Mounted"), "text", path("/status"));
      JComponent panel = fixture.componentOf("root");
      JComponent pane = content();

      fixture.setData("/status", "**Changed**");
      fixture.update(component("root", "Text", "text", path("/status"), "variant", "caption"));

      assertEquals("Changed", shownText());
      assertSame(panel, fixture.componentOf("root"));
      assertSame(pane, content());
    });
  }

  @Test
  void theBodyVariantHasTheFontAndTheColorOfTheSurfaceComponent() {
    Edt.run(() -> {
      show(Map.of(), "text", "body");

      assertEquals(fixture.view.getFont(), content().getFont());
      assertEquals(fixture.view.getForeground(), content().getForeground());

      Font serif = new Font(Font.SERIF, Font.ITALIC, 21);
      fixture.view.setFont(serif);
      fixture.view.setForeground(Color.MAGENTA);
      fixture.view.revalidate();
      fixture.view.validate();

      assertEquals(serif, content().getFont());
      assertEquals(Color.MAGENTA, content().getForeground());
    });
  }

  @Test
  void theCaptionVariantIsSmallerAndHasAColorBetweenTheTextAndTheBackground() {
    Edt.run(() -> {
      show(Map.of(), "text", "caption", "variant", "caption");
      Font base = fixture.view.getFont();
      Color text = fixture.view.getForeground();
      Color background = fixture.view.getBackground();

      Font font = content().getFont();
      Color color = content().getForeground();

      assertEquals(base.getSize2D() * 0.8f, font.getSize2D(), 0.01f);
      assertEquals(base.getFamily(), font.getFamily());
      assertEquals(BasicStyle.muted(fixture.view), color);
      assertTrue(BasicStyle.contrast(color, background) < BasicStyle.contrast(text, background));
      assertTrue(BasicStyle.contrast(color, background) > 3, color.toString());

      fixture.update(component("root", "Text", "text", "caption", "variant", "body"));

      assertEquals(base, content().getFont());
      assertEquals(text, content().getForeground());
    });
  }

  @Test
  void theViewHasTheMarginOfALeaf() {
    Edt.run(() -> {
      show(Map.of(), "text", "margin");
      JComponent panel = fixture.componentOf("root");

      assertEquals(new Insets(8, 8, 8, 8), panel.getInsets());
      assertEquals(8, content().getX());
      assertEquals(8, content().getY());
      assertEquals(BasicFixture.WIDTH - 16, content().getWidth());
      assertEquals(content().getPreferredSize().height + 16, panel.getPreferredSize().height);
      assertFalse(panel.isOpaque());
    });
  }

  @Test
  void theTextBreaksAtTheWidthOfTheViewAndTheHeightFollows() {
    Edt.run(() -> {
      show(Map.of(), "text", LONG_TEXT);
      FlexPanel panel = (FlexPanel) fixture.componentOf("root");
      int oneLine = content().getPreferredSize().height;
      int lineWidth = content().getPreferredSize().width;

      assertEquals(oneLine + 16, panel.getPreferredSize().height);
      assertEquals(lineWidth + 16, panel.getPreferredSize().width);
      assertEquals(oneLine + 16, panel.preferredHeightFor(lineWidth + 16));
      assertEquals(2 * oneLine + 16, panel.preferredHeightFor(lineWidth / 2 + 40), 2);
      assertTrue(panel.preferredHeightFor(120) >= 5 * oneLine);
    });
  }

  @Test
  void theAccessibilityAttributesGoToTheTextComponent() {
    Edt.run(() -> {
      show(Map.of(), "text", "Connection lost", "@accessibility", object("label", "Alert"));

      assertEquals("Alert", content().getAccessibleContext().getAccessibleName());
    });
  }

  @Test
  void theViewUsesTheMarkdownServiceOfTheSurface() {
    Edt.run(() -> {
      List<UpperCaseView> made = new ArrayList<>();
      fixture =
          new BasicFixture(
              A2uiServices.DEFAULT.withMarkdown(() -> {
                made.add(new UpperCaseView());
                return made.getLast();
              }));

      show(Map.of(), "text", "shout", "variant", "caption");

      assertEquals(1, made.size());
      assertEquals("SHOUT", made.getFirst().label.getText());
      assertSame(made.getFirst().label, content());
      // The component of the application gets the base style of the text.
      assertEquals(
          fixture.view.getFont().getSize2D() * 0.8f, content().getFont().getSize2D(), 0.01f);
      assertEquals(BasicStyle.muted(fixture.view), content().getForeground());
    });
  }

  @Test
  void theDisposalOfTheViewDisposesTheMarkdownView() {
    Edt.run(() -> {
      UpperCaseView custom = new UpperCaseView();
      fixture = new BasicFixture(A2uiServices.DEFAULT.withMarkdown(() -> custom));
      show(Map.of(), "text", "bye");

      fixture.view.removeNotify();

      assertTrue(custom.disposed);
      assertEquals(0, fixture.view.activeSubscriptionCount());
    });
  }

  @Test
  void theDisposalOfTheDefaultViewRemovesItsListeners() {
    Edt.run(() -> {
      show(Map.of(), "text", "bye");
      JComponent pane = content();
      int fontListeners = pane.getPropertyChangeListeners("font").length;
      int colorListeners = pane.getPropertyChangeListeners("foreground").length;

      fixture.view.removeNotify();

      assertEquals(fontListeners - 1, pane.getPropertyChangeListeners("font").length);
      assertEquals(colorListeners - 1, pane.getPropertyChangeListeners("foreground").length);
    });
  }

  @Test
  void aMarkdownServiceThatMakesNoViewIsReplacedByTheDefaultService() {
    Edt.run(() -> {
      try (LogCapture log = new LogCapture()) {
        fixture =
            new BasicFixture(
                A2uiServices.DEFAULT.withMarkdown(() -> {
                  throw new IllegalStateException("no view");
                }));

        show(Map.of(), "text", "still **here**");

        assertEquals("still here", shownText());
        assertEquals(List.of("The Markdown service did not make a view."), log.messages);
      }
    });
  }

  @Test
  void aMarkdownViewThatFailsIsReplacedByTheDefaultView() {
    Edt.run(() -> {
      try (LogCapture log = new LogCapture()) {
        UpperCaseView custom = new UpperCaseView();
        fixture = new BasicFixture(A2uiServices.DEFAULT.withMarkdown(() -> custom));
        show(Map.of("text", "first"), "text", path("/text"));
        JComponent panel = fixture.componentOf("root");

        custom.fails = true;
        fixture.setData("/text", "second *text*");

        assertEquals("second text", shownText());
        assertNotSame(custom.label, content());
        assertSame(panel, fixture.componentOf("root"));
        assertTrue(custom.disposed);
        assertEquals(List.of("The Markdown view of the application failed."), log.messages);

        fixture.setData("/text", "third");

        assertEquals("third", shownText());
        assertEquals(1, log.messages.size());
      }
    });
  }
}
