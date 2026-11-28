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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.testing.Edt;
import java.awt.Component;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.accessibility.AccessibleState;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class CheckBoxViewTest {
  private final BasicFixture fixture = new BasicFixture();

  private static Map<String, Object> check(Object condition, String message) {
    return Map.of("condition", condition, "message", message);
  }

  private static Object checkBox(Object... properties) {
    List<Object> all = new ArrayList<>();
    if (!List.of(properties).contains("label")) {
      all.addAll(List.of("label", "I agree"));
    }
    if (!List.of(properties).contains("value")) {
      all.addAll(List.of("value", path("/agreed")));
    }
    all.addAll(List.of(properties));
    return component("root", "CheckBox", all.toArray());
  }

  /** Makes a surface whose root is a check box with the label "I agree" and the value /agreed. */
  private void show(Map<String, Object> data, Object... properties) {
    fixture.create(data, checkBox(properties));
  }

  private JCheckBox box() {
    return (JCheckBox) fixture.viewOf("root").accessibleComponent();
  }

  private JComponent panel() {
    return fixture.componentOf("root");
  }

  private ToggleRow row() {
    return (ToggleRow) panel().getComponent(1);
  }

  private List<String> messages() {
    JComponent holder = (JComponent) panel().getComponent(2);
    List<String> texts = new ArrayList<>();
    if (holder.isVisible()) {
      for (Component line : holder.getComponents()) {
        texts.add(((WrappingLabel) line).getText());
      }
    }
    return texts;
  }

  private Rectangle boundsOf(Component component) {
    return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), panel());
  }

  /** Counts the changes of the state of the check box. */
  private static List<Boolean> changesOf(JCheckBox box) {
    List<Boolean> changes = new ArrayList<>();
    box.addItemListener(event -> changes.add(box.isSelected()));
    return changes;
  }

  @Test
  void theViewShowsACheckBoxWithItsLabelNextToIt() {
    Edt.run(() -> {
      show(Map.of("agreed", true));

      JCheckBox box = box();
      assertTrue(box.isSelected());
      assertTrue(box.isEnabled());
      assertEquals("", box.getText());
      assertFalse(box.isOpaque());
      assertEquals("I agree", row().getText());
      assertSame(box, row().button());
      // The label of the panel is for a label above the control. A check box has none.
      assertFalse(panel().getComponent(0).isVisible());
      assertEquals(new Insets(8, 8, 8, 8), panel().getInsets());
      Rectangle boxBounds = boundsOf(box);
      Rectangle textBounds = boundsOf(row().textLabel());
      assertEquals(8, boxBounds.x);
      assertEquals(boxBounds.x + boxBounds.width + box.getIconTextGap(), textBounds.x);
      // The text is in the vertical center of the check box.
      int line = textBounds.height;
      assertTrue(
          Math.abs(boxBounds.y + boxBounds.height / 2 - (textBounds.y + line / 2)) <= 1,
          boxBounds + " " + textBounds);
      assertEquals(List.of(), messages());
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void aClickOfTheUserWritesTheStateAndDoesNotTouchTheCheckBoxAgain() {
    Edt.run(() -> {
      fixture.create(
          Map.of("agreed", false),
          component("root", "Column", "children", List.of("box", "preview")),
          component("box", "CheckBox", "label", "I agree", "value", path("/agreed")),
          component("preview", "Text", "text", path("/agreed")));
      JCheckBox box = (JCheckBox) fixture.viewOf("box").accessibleComponent();
      List<Boolean> changes = changesOf(box);

      box.doClick(0);

      assertEquals(true, fixture.data("/agreed"));
      assertEquals("true", VisibleText.of(fixture.componentOf("preview")));
      assertEquals(List.of(true), changes);

      box.doClick(0);

      assertEquals(false, fixture.data("/agreed"));
      assertEquals(List.of(true, false), changes);
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void aClickOnTheTextOperatesTheCheckBox() {
    Edt.run(() -> {
      show(Map.of("agreed", false));
      WrappingLabel text = row().textLabel();

      text.dispatchEvent(
          new MouseEvent(text, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 1, false, MouseEvent.BUTTON1));

      assertTrue(box().isSelected());
      assertEquals(true, fixture.data("/agreed"));
    });
  }

  @Test
  void aNewValueOfTheDataModelShowsAndWritesNothing() {
    Edt.run(() -> {
      show(Map.of("agreed", false, "other", 1L));
      JCheckBox box = box();
      List<Boolean> changes = changesOf(box);
      List<Object> writes = new ArrayList<>();
      fixture.model().dataModel().subscribe("/agreed", writes::add);

      fixture.setData("/agreed", true);

      assertTrue(box.isSelected());
      assertEquals(List.of(true), changes);
      assertEquals(List.of(true), writes);

      // A value that the check box shows does not touch the check box.
      fixture.setData("/other", 2L);
      fixture.update(checkBox("label", "I agree to the terms"));

      assertSame(box, box());
      assertEquals(List.of(true), changes);
      assertEquals(List.of(true), writes);
      assertEquals("I agree to the terms", row().getText());
    });
  }

  @Test
  void aValueThatIsNotABooleanIsReadAsABoolean() {
    Edt.run(() -> {
      show(Map.of("agreed", "true"));
      assertTrue(box().isSelected());

      fixture.setData("/agreed", 0L);
      assertFalse(box().isSelected());

      fixture.setData("/agreed", null);
      assertFalse(box().isSelected());
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void aValueWithNoBindingChangesOnlyTheCheckBox() {
    Edt.run(() -> {
      show(Map.of("agreed", false), "value", true);

      box().doClick(0);

      assertFalse(box().isSelected());
      assertEquals(false, fixture.data("/agreed"));
      assertEquals(List.of(), fixture.errors);

      // The next update shows the value of the property again.
      fixture.update(checkBox("value", true, "label", "Other"));
      assertTrue(box().isSelected());
    });
  }

  @Test
  void anEmptyLabelShowsOnlyTheCheckBox() {
    Edt.run(() -> {
      show(Map.of("agreed", false), "label", "");

      assertFalse(row().textLabel().isVisible());
      assertEquals(box().getPreferredSize().width, row().getPreferredSize().width);
      assertEquals(box().getPreferredSize().height, row().getPreferredSize().height);
    });
  }

  @Test
  void aLongLabelBreaksAtTheWidthOfTheViewAndTheHeightFollows() {
    Edt.run(() -> {
      show(
          Map.of("agreed", false),
          "label",
          "I agree to the terms of the service, to the policy of the data, and to the other"
              + " conditions of the contract.");
      WrappingLabel text = row().textLabel();
      int oneLine = text.getPreferredSize().height;

      fixture.resize(200, 300);

      assertTrue(text.getHeight() >= 3 * oneLine, "the height is " + text.getHeight());
      Rectangle bounds = boundsOf(text);
      assertTrue(bounds.x + bounds.width <= 200 - 8, bounds.toString());
      FlexPanel panel = (FlexPanel) panel();
      assertEquals(panel.preferredHeightFor(200), bounds.y + bounds.height + 8);
      assertTrue(panel.preferredHeightFor(200) > panel.preferredHeightFor(2000));
    });
  }

  @Test
  void theMessagesOfTheChecksShowBelowAndTheUserCanStillOperateTheCheckBox() {
    Edt.run(() -> {
      Map<String, Object> warning =
          Map.of("valid", false, "message", "Read the terms first.", "severity", "warning");
      show(
          Map.of("agreed", false, "warning", warning),
          "checks",
          List.of(check(path("/warning"), "unused"), check(path("/agreed"), "You must agree.")));

      assertEquals(List.of("Read the terms first.", "You must agree."), messages());
      assertEquals("You must agree.", box().getToolTipText());
      assertEquals("error", box().getClientProperty("JComponent.outline"));
      assertTrue(box().isEnabled());
      JComponent lines = (JComponent) panel().getComponent(2);
      assertEquals(BasicStyle.warning(panel()), lines.getComponent(0).getForeground());
      assertEquals(BasicStyle.error(panel()), lines.getComponent(1).getForeground());
      assertEquals(
          boundsOf(row()).y + boundsOf(row()).height + LabeledControl.GAP,
          boundsOf(lines.getComponent(0)).y);

      box().doClick(0);

      assertEquals(List.of("Read the terms first."), messages());
      assertNull(box().getToolTipText());
      assertNull(box().getClientProperty("JComponent.outline"));
    });
  }

  @Test
  void theAccessibleNameIsTheLabelUnlessTheAgentGaveOne() {
    Edt.run(() -> {
      show(Map.of("agreed", true));

      assertEquals("I agree", box().getAccessibleContext().getAccessibleName());
      assertTrue(
          box().getAccessibleContext().getAccessibleStateSet().contains(AccessibleState.CHECKED));

      fixture.update(
          checkBox(
              "@accessibility",
              object("label", "Accept the terms", "description", "Needed to continue")));

      assertEquals("Accept the terms", box().getAccessibleContext().getAccessibleName());
      assertEquals("Needed to continue", box().getAccessibleContext().getAccessibleDescription());
    });
  }

  @Test
  void theDisposalRemovesTheListenerOfTheCheckBox() {
    Edt.run(() -> {
      show(Map.of("agreed", false), "checks", List.of(check(path("/agreed"), "You must agree.")));
      JCheckBox box = box();
      int listeners = box.getItemListeners().length;

      fixture.view.removeNotify();

      assertEquals(listeners - 1, box.getItemListeners().length);
      assertNull(box.getToolTipText());
      box.doClick(0);
      assertEquals(false, fixture.data("/agreed"));
      assertEquals(0, fixture.view.activeSubscriptionCount());
    });
  }
}
