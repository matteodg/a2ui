/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.surface.MountedNode;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.Snapshots;
import java.awt.Color;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

/** The surface of the tests is 400 pixels wide and 300 pixels high. */
class CardViewTest {
  private static final String LONG_TEXT =
      "A card has a text of many words. The text is too long for one line of the card, and thus"
          + " it needs more lines when the card is narrow.";

  private final BasicFixture fixture = new BasicFixture();

  /** Makes a surface whose root is a card with one text. */
  private void show(String text) {
    fixture.create(
        Map.of(),
        component("root", "Card", "child", "inside"),
        component("inside", "Text", "text", text));
  }

  /** Gets the panel of the card that paints the outline. */
  private JComponent body() {
    return (JComponent) fixture.componentOf("root").getComponent(0);
  }

  @Test
  void theCardHasTheMarginOfAControlAndThePaddingAroundItsChild() {
    Edt.run(() -> {
      show("Inside");
      int margin = BasicStyle.MARGIN;
      int inset = CardView.PADDING + 1;

      assertEquals(new Rectangle(0, 0, 400, 300), fixture.bounds("root"));
      assertEquals(
          new Insets(margin, margin, margin, margin), fixture.componentOf("root").getInsets());
      assertEquals(
          new Rectangle(margin, margin, 400 - 2 * margin, 300 - 2 * margin), body().getBounds());
      assertEquals(new Insets(inset, inset, inset, inset), body().getInsets());
      // The child fills the area in the outline.
      assertEquals(
          new Rectangle(
              margin + inset,
              margin + inset,
              400 - 2 * (margin + inset),
              300 - 2 * (margin + inset)),
          fixture.bounds("inside"));
      assertSame(body(), fixture.componentOf("inside").getParent());
    });
  }

  @Test
  void theCardShowsOnlyTheViewOfItsChild() {
    Edt.run(() -> {
      show("Inside");
      MountedNode card = fixture.node("root");

      assertEquals(1, card.children().size());
      assertEquals("inside", card.children().get(0).node().componentId());
      assertEquals("Inside", VisibleText.of(fixture.componentOf("root")));
      assertEquals(1, body().getComponentCount());
    });
  }

  @Test
  void theCardHasNoBackgroundAndPaintsAnOutlineWithTheBorderColorOfTheLookAndFeel() {
    Edt.run(() -> {
      show("Inside");
      Color outline = BasicStyle.outline(body());
      Color background = fixture.view.getBackground();
      int margin = BasicStyle.MARGIN;

      assertFalse(fixture.componentOf("root").isOpaque());
      assertFalse(body().isOpaque());
      assertNotEquals(background, outline);
      BufferedImage image = Snapshots.render(fixture.view);
      // The line is on the first pixel in the margin, in the center of each side.
      assertEquals(outline.getRGB(), image.getRGB(200, margin));
      assertEquals(outline.getRGB(), image.getRGB(margin, 150));
      assertEquals(outline.getRGB(), image.getRGB(400 - margin - 1, 150));
      assertEquals(outline.getRGB(), image.getRGB(200, 300 - margin - 1));
      // The margin and the area in the outline have the background of the surface.
      assertEquals(background.getRGB(), image.getRGB(200, margin - 2));
      assertEquals(background.getRGB(), image.getRGB(200, margin + 3));
      // The corner is round. Thus the corner pixel has no line.
      assertEquals(background.getRGB(), image.getRGB(margin, margin));
    });
  }

  @Test
  void theOutlineUsesTheBorderColorThatTheLookAndFeelHas() {
    Edt.run(() -> {
      show("Inside");
      Object before = UIManager.get("Component.borderColor");
      try {
        UIManager.put("Component.borderColor", new Color(0x12, 0x34, 0x56));

        assertEquals(new Color(0x12, 0x34, 0x56), BasicStyle.outline(body()));
      } finally {
        UIManager.put("Component.borderColor", before);
      }
    });
  }

  @Test
  void theOutlineDoesNotUseAColorThatTheUserCannotSeeOnTheBackground() {
    Edt.run(() -> {
      show("Inside");
      Color background = BasicStyle.backgroundOf(body());
      Object before = UIManager.get("Component.borderColor");
      try {
        UIManager.put("Component.borderColor", background);

        Color outline = BasicStyle.outline(body());

        assertNotEquals(background, outline);
        assertTrue(BasicStyle.contrast(outline, background) >= 1.3, outline.toString());
      } finally {
        UIManager.put("Component.borderColor", before);
      }
    });
  }

  @Test
  void theTextInANarrowCardGetsMoreLines() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Column", "children", List.of("card", "below")),
          component("card", "Card", "child", "inside"),
          component("inside", "Text", "text", LONG_TEXT),
          component("below", "Text", "text", "Below"));
      HeightForWidth card = (HeightForWidth) fixture.componentOf("card");
      int wide = fixture.bounds("card").height;

      assertEquals(card.preferredHeightFor(400), wide);
      assertTrue(card.preferredHeightFor(200) > wide, "a narrow card is higher");

      fixture.resize(200, 600);

      assertEquals(card.preferredHeightFor(200), fixture.bounds("card").height);
      assertTrue(fixture.bounds("card").height > wide);
      // The text is in the card, and the next component is below the card.
      Rectangle text = fixture.bounds("inside");
      assertTrue(fixture.bounds("card").contains(text), text.toString());
      assertEquals(200 - 2 * (BasicStyle.MARGIN + CardView.PADDING + 1), text.width);
      assertEquals(fixture.bounds("card").height, fixture.bounds("below").y);
    });
  }

  @Test
  void twoCardsInARowHaveOutlinesOfTheSameHeight() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Column", "children", List.of("row")),
          component("row", "Row", "children", List.of("small", "large")),
          component("small", "Card", "child", "one", "weight", 1),
          component("one", "Text", "text", "One"),
          component("large", "Card", "child", "two", "weight", 1),
          component("two", "Text", "text", LONG_TEXT));

      JComponent small = (JComponent) fixture.componentOf("small").getComponent(0);
      JComponent large = (JComponent) fixture.componentOf("large").getComponent(0);

      assertEquals(large.getHeight(), small.getHeight());
      assertTrue(small.getHeight() > small.getPreferredSize().height);
    });
  }

  @Test
  void anUpdateOfTheChildKeepsTheCardAndDisposesTheViewBefore() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Card", "child", "first"),
          component("first", "Text", "text", "First"),
          component("second", "Text", "text", "Second"));
      JComponent card = fixture.componentOf("root");
      JComponent body = body();
      JComponent first = fixture.componentOf("first");
      int subscriptions = fixture.view.activeSubscriptionCount();

      fixture.update(component("root", "Card", "child", "second"));

      assertSame(card, fixture.componentOf("root"));
      assertSame(body, body());
      assertNull(first.getParent());
      assertEquals("Second", VisibleText.of(card));
      assertEquals(1, body.getComponentCount());
      assertEquals(subscriptions, fixture.view.activeSubscriptionCount());
    });
  }

  @Test
  void aCardWhoseChildDidNotArriveShowsItsOutlineAndGetsTheChildLater() {
    Edt.run(() -> {
      fixture.create(Map.of(), component("root", "Card", "child", "late"));
      JComponent card = fixture.componentOf("root");

      assertEquals(0, body().getComponentCount());
      assertFalse(fixture.node("late").occupiesLayout());
      assertEquals(new Rectangle(8, 8, 384, 284), body().getBounds());

      fixture.update(component("late", "Text", "text", "Late"));

      assertSame(card, fixture.componentOf("root"));
      assertEquals("Late", VisibleText.of(card));
    });
  }

  @Test
  void theAccessibilityAttributesGoToThePanelOfTheCard() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component(
              "root",
              "Card",
              "child",
              "inside",
              "@accessibility",
              object("label", "Summary", "description", "The totals of the month")),
          component("inside", "Text", "text", "Inside"));
      JComponent card = fixture.viewOf("root").accessibleComponent();

      assertSame(fixture.componentOf("root"), card);
      assertEquals(AccessibleRole.PANEL, card.getAccessibleContext().getAccessibleRole());
      assertEquals("Summary", card.getAccessibleContext().getAccessibleName());
      assertEquals(
          "The totals of the month", card.getAccessibleContext().getAccessibleDescription());
    });
  }

  @Test
  void aCardWithNoLabelHasNoAccessibleNameOfItsOwn() {
    Edt.run(() -> {
      show("Inside");

      assertNull(fixture.componentOf("root").getAccessibleContext().getAccessibleName());
    });
  }

  @Test
  void theDisposalRemovesTheViewOfTheChild() {
    Edt.run(() -> {
      show("Inside");
      JComponent body = body();

      assertTrue(fixture.view.activeSubscriptionCount() > 0);

      fixture.view.removeNotify();

      assertEquals(0, body.getComponentCount());
      assertEquals(0, fixture.view.activeSubscriptionCount());
    });
  }
}
