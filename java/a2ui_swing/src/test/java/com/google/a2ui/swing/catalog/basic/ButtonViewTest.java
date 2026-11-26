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

import static com.google.a2ui.swing.testing.TestSurface.call;
import static com.google.a2ui.swing.testing.TestSurface.component;
import static com.google.a2ui.swing.testing.TestSurface.event;
import static com.google.a2ui.swing.testing.TestSurface.object;
import static com.google.a2ui.swing.testing.TestSurface.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.catalog.basic.ButtonView.Variant;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.Snapshots;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ButtonViewTest {
  private final BasicFixture fixture = new BasicFixture();

  private static Map<String, Object> check(Object condition, String message) {
    return Map.of("condition", condition, "message", message);
  }

  /** Makes a surface whose root is a button with the text child "label". */
  private void show(Map<String, Object> data, String labelText, Object... properties) {
    fixture.create(data, definition(properties), component("label", "Text", "text", labelText));
  }

  private static Object definition(Object... properties) {
    List<Object> all = new ArrayList<>(List.of("child", "label"));
    if (!List.of(properties).contains("action")) {
      all.addAll(List.of("action", event("tap")));
    }
    all.addAll(List.of(properties));
    return component("root", "Button", all.toArray());
  }

  private JButton button() {
    return (JButton) fixture.viewOf("root").accessibleComponent();
  }

  /** Gets the text component of the child. */
  private JComponent labelText() {
    return fixture.viewOf("label").accessibleComponent();
  }

  private Set<Integer> colorsOf(JComponent component) {
    BufferedImage image = Snapshots.render(component);
    Set<Integer> colors = new HashSet<>();
    for (int x = 0; x < image.getWidth(); x++) {
      for (int y = 0; y < image.getHeight(); y++) {
        colors.add(image.getRGB(x, y) & 0xffffff);
      }
    }
    return colors;
  }

  @Test
  void theButtonShowsTheViewOfItsChild() {
    Edt.run(() -> {
      show(Map.of(), "Get Started");

      JButton button = button();
      JComponent child = fixture.componentOf("label");
      assertTrue(SwingUtilities.isDescendingFrom(child, button));
      assertEquals("", button.getText());
      assertEquals("Get Started", VisibleText.of(button));
      // The text in a button has no margin, because the button has its own padding.
      assertEquals(new Insets(0, 0, 0, 0), child.getInsets());
      Insets padding = button.getInsets();
      Dimension text = child.getPreferredSize();
      assertEquals(
          new Dimension(
              text.width + padding.left + padding.right,
              text.height + padding.top + padding.bottom),
          button.getPreferredSize());
      assertTrue(padding.left > 0 && padding.top > 0, padding.toString());
    });
  }

  @Test
  void theViewHasTheMarginOfAControlAndTheButtonFillsItsWidth() {
    Edt.run(() -> {
      show(Map.of(), "Go");
      JComponent panel = fixture.componentOf("root");

      assertEquals(new Insets(8, 8, 8, 8), panel.getInsets());
      assertEquals(8, button().getX());
      assertEquals(8, button().getY());
      assertEquals(BasicFixture.WIDTH - 16, button().getWidth());
      assertEquals(button().getPreferredSize().height, button().getHeight());
      assertEquals(button().getPreferredSize().height + 16, panel.getPreferredSize().height);
      // The child is in the center of the button.
      JComponent child = fixture.componentOf("label");
      int left = SwingUtilities.convertPoint(child, 0, 0, button()).x;
      assertEquals((button().getWidth() - child.getWidth()) / 2, left, 1);
    });
  }

  @Test
  void aClickDoesAnEventActionInTheListenerWithTheDataOfThatTime() {
    Edt.run(() -> {
      show(Map.of("itemId", "sku-1"), "Buy", "action", event("buy_item", "sku", path("/itemId")));
      fixture.setData("/itemId", "sku-42");

      button().doClick(0);

      assertEquals(1, fixture.actions.size());
      assertEquals("buy_item", fixture.actions.getFirst().name());
      assertEquals("root", fixture.actions.getFirst().sourceComponentId());
      assertEquals(Map.of("sku", "sku-42"), fixture.actions.getFirst().context());
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void aClickRunsAFunctionActionThatNeedsAUserGesture() {
    Edt.run(() -> {
      show(
          Map.of(),
          "Open",
          "action",
          Map.of("functionCall", call("openUrl", "url", "https://example.com/a")));

      button().doClick(0);

      assertEquals(List.of(URI.create("https://example.com/a")), fixture.opened);
      assertEquals(List.of(), fixture.actions);
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void theDefaultVariantIsTheButtonOfTheLookAndFeel() {
    Edt.run(() -> {
      show(Map.of(), "Default");
      JButton reference = new JButton("x");

      assertTrue(button().isContentAreaFilled());
      assertTrue(button().isBorderPainted());
      assertEquals(reference.getInsets(), button().getInsets());
      assertEquals(reference.getBorder().getClass(), button().getBorder().getClass());
      assertEquals(Cursor.getDefaultCursor(), button().getCursor());
      // The text has the color and the font of a button of the look and feel.
      assertEquals(reference.getForeground(), labelText().getForeground());
      assertEquals(reference.getFont(), labelText().getFont());
    });
  }

  @Test
  void thePrimaryVariantHasTheAccentColorAndATextColorWithContrast() {
    Edt.run(() -> {
      show(Map.of(), "IIIIIIII", "variant", "primary");
      JButton reference = new JButton("x");
      Color accent = BasicStyle.accent();
      Color onAccent = BasicStyle.onAccent();

      assertFalse(button().isContentAreaFilled());
      assertFalse(button().isBorderPainted());
      assertEquals(reference.getInsets(), button().getInsets());
      assertEquals(onAccent, labelText().getForeground());
      assertTrue(BasicStyle.contrast(accent, onAccent) >= 4.5);
      Set<Integer> colors = colorsOf(button());
      assertTrue(colors.contains(accent.getRGB() & 0xffffff), "the background");
      assertTrue(colors.contains(onAccent.getRGB() & 0xffffff), "the text");
    });
  }

  @Test
  void aVariantKeepsItsTextColorWhenTheWindowAppliesALookAndFeelAgain() {
    Edt.run(() -> {
      show(Map.of(), "Primary", "variant", "primary");
      assertEquals(BasicStyle.onAccent(), labelText().getForeground());

      // A change of the look and feel at run time does this for each component of a window.
      SwingUtilities.updateComponentTreeUI(fixture.view);

      assertEquals(BasicStyle.onAccent(), labelText().getForeground());

      fixture.update(definition("variant", "borderless"));
      SwingUtilities.updateComponentTreeUI(fixture.view);

      assertEquals(BasicStyle.link(button()), labelText().getForeground());
    });
  }

  @Test
  void theBorderlessVariantHasNoBackgroundAndTheColorOfALink() {
    Edt.run(() -> {
      show(Map.of(), "IIIIIIII", "variant", "borderless");
      JButton reference = new JButton("x");
      Color link = BasicStyle.link(button());
      Color background = fixture.view.getBackground();

      assertFalse(button().isContentAreaFilled());
      assertFalse(button().isBorderPainted());
      assertEquals(reference.getInsets().top, button().getInsets().top);
      assertTrue(button().getInsets().left <= 6);
      assertEquals(link, labelText().getForeground());
      assertTrue(BasicStyle.contrast(link, background) >= 4.5, link.toString());
      assertEquals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), button().getCursor());
      button().getParent().setBackground(background);
      Set<Integer> colors = colorsOf((JComponent) button().getParent());
      assertTrue(colors.contains(link.getRGB() & 0xffffff), "the text");
      assertFalse(
          colors.contains(BasicStyle.accent().getRGB() & 0xffffff)
              && !link.equals(BasicStyle.accent()));
    });
  }

  @Test
  void theThreeVariantsHaveTheSameHeight() {
    Edt.run(() -> {
      show(Map.of(), "Same");
      int height = button().getPreferredSize().height;

      fixture.update(definition("variant", "primary"));
      assertEquals(height, button().getPreferredSize().height);

      fixture.update(definition("variant", "borderless"));
      assertEquals(height, button().getPreferredSize().height);
    });
  }

  @Test
  void anUpdateOfTheVariantKeepsTheButtonAndGivesItTheNewLook() {
    Edt.run(() -> {
      show(Map.of(), "Next", "variant", "primary");
      JButton button = button();
      JComponent panel = fixture.componentOf("root");
      JComponent child = fixture.componentOf("label");
      Color buttonText = new JButton("x").getForeground();

      fixture.update(definition("variant", "default"));

      assertSame(button, button());
      assertSame(panel, fixture.componentOf("root"));
      assertSame(child, fixture.componentOf("label"));
      assertTrue(button.isContentAreaFilled());
      assertTrue(button.isBorderPainted());
      assertEquals(new JButton("x").getBorder().getClass(), button.getBorder().getClass());
      assertEquals(buttonText, labelText().getForeground());

      fixture.update(definition("variant", "primary"));

      assertFalse(button.isContentAreaFilled());
      assertEquals(BasicStyle.onAccent(), labelText().getForeground());
      assertEquals(Variant.DEFAULT, Variant.fromWire(null));
      assertEquals(Variant.DEFAULT, Variant.fromWire("other"));
    });
  }

  @Test
  void theUserCannotOperateTheButtonWhileACheckFails() {
    Edt.run(() -> {
      show(
          Map.of("name", ""),
          "Send",
          "variant",
          "primary",
          "checks",
          List.of(check(call("required", "value", path("/name")), "Enter a name first.")));
      Color enabledText = BasicStyle.onAccent();

      assertFalse(button().isEnabled());
      assertEquals("Enter a name first.", button().getToolTipText());
      assertEquals(
          "Enter a name first.", button().getAccessibleContext().getAccessibleDescription());
      assertNotEquals(enabledText, labelText().getForeground());
      assertFalse(colorsOf(button()).contains(BasicStyle.accent().getRGB() & 0xffffff));

      button().doClick(0);

      assertEquals(List.of(), fixture.actions);

      fixture.setData("/name", "Ada");

      assertTrue(button().isEnabled());
      assertNull(button().getToolTipText());
      assertEquals(enabledText, labelText().getForeground());

      button().doClick(0);

      assertEquals(1, fixture.actions.size());
    });
  }

  @Test
  void aDisabledButtonOfTheDefaultVariantHasTheDisabledTextColor() {
    Edt.run(() -> {
      show(Map.of("ok", false), "Send", "checks", List.of(check(path("/ok"), "Not now.")));

      assertFalse(button().isEnabled());
      assertEquals(BasicStyle.disabled(button()), labelText().getForeground());

      fixture.setData("/ok", true);

      assertEquals(new JButton("x").getForeground(), labelText().getForeground());
    });
  }

  @Test
  void aWarningDoesNotStopTheButtonAndTheToolTipHasEachMessage() {
    Edt.run(() -> {
      Map<String, Object> warning =
          Map.of("valid", false, "message", "The <list> is long.", "severity", "warning");
      show(
          Map.of("warning", warning, "ok", true),
          "Send",
          "checks",
          List.of(check(path("/warning"), "unused"), check(path("/ok"), "Not ready & waiting.")));

      assertTrue(button().isEnabled());
      assertEquals("The <list> is long.", button().getToolTipText());

      fixture.setData("/ok", false);

      assertFalse(button().isEnabled());
      assertEquals(
          "<html>The &lt;list&gt; is long.<br>Not ready &amp; waiting.</html>",
          button().getToolTipText());
    });
  }

  @Test
  void theAccessibleNameIsTheTextOfTheChildUnlessTheAgentGaveALabel() {
    Edt.run(() -> {
      show(Map.of("label", "Save **Changes**"), "x");
      fixture.update(component("label", "Text", "text", path("/label")));

      assertEquals("Save Changes", button().getAccessibleContext().getAccessibleName());

      fixture.setData("/label", "Saved");

      assertEquals("Saved", button().getAccessibleContext().getAccessibleName());

      fixture.update(
          definition("@accessibility", object("label", "Close dialog", "description", "Discards")));

      assertEquals("Close dialog", button().getAccessibleContext().getAccessibleName());
      assertEquals("Discards", button().getAccessibleContext().getAccessibleDescription());
    });
  }

  @Test
  void theMouseEventsOnTheContentGoToTheButton() {
    Edt.run(() -> {
      show(Map.of(), "Click me");
      JComponent text = labelText();
      java.awt.Point center =
          SwingUtilities.convertPoint(text, text.getWidth() / 2, text.getHeight() / 2, button());

      assertTrue(text.getMouseListeners().length > 0, "the text component has mouse listeners");
      Component target = button().findComponentAt(center);

      assertSame(button(), target);
    });
  }

  @Test
  void theTextOfANarrowButtonGetsMoreLines() {
    Edt.run(() -> {
      show(Map.of(), "A button with a text of many words that is too long for one line");
      HeightForWidth wrapping = (HeightForWidth) button();
      Dimension preferred = button().getPreferredSize();

      assertEquals(preferred.height, wrapping.preferredHeightFor(preferred.width));
      assertTrue(wrapping.preferredHeightFor(150) > preferred.height);
      assertTrue(button().getMinimumSize().width < preferred.width);

      fixture.resize(150, 300);

      assertEquals(wrapping.preferredHeightFor(134), button().getHeight());
      assertEquals(134, button().getWidth());
      assertTrue(labelText().getHeight() > labelText().getPreferredSize().height);
    });
  }

  @Test
  void aButtonWhoseChildDidNotArriveIsEmptyAndGetsTheChildLater() {
    Edt.run(() -> {
      fixture.create(Map.of(), definition());
      JButton button = button();

      assertEquals("", VisibleText.of(button));
      assertEquals("", button.getAccessibleContext().getAccessibleName());

      fixture.update(component("label", "Text", "text", "Late"));

      assertSame(button, button());
      assertEquals("Late", VisibleText.of(button));
      assertEquals("Late", button.getAccessibleContext().getAccessibleName());
    });
  }

  @Test
  void theDisposalRemovesTheListenerOfTheButton() {
    Edt.run(() -> {
      show(Map.of(), "Bye");
      JButton button = button();
      int listeners = button.getActionListeners().length;

      fixture.view.removeNotify();

      assertEquals(listeners - 1, button.getActionListeners().length);
      assertNull(button.getToolTipText());
      button.doClick(0);
      assertEquals(List.of(), fixture.actions);
      assertEquals(0, fixture.view.activeSubscriptionCount());
    });
  }
}
