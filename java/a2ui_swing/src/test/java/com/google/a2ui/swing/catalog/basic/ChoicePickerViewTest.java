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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.testing.Edt;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.accessibility.AccessibleRole;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ChoicePickerViewTest {
  private final BasicFixture fixture = new BasicFixture();

  /** The writes to the path /choice. */
  private final List<Object> writes = new ArrayList<>();

  private static List<Object> options(String... labels) {
    List<Object> options = new ArrayList<>();
    for (String label : labels) {
      options.add(object("label", label, "value", label.toLowerCase()));
    }
    return options;
  }

  private static Object picker(Object... properties) {
    List<Object> all = new ArrayList<>();
    if (!List.of(properties).contains("label")) {
      all.addAll(List.of("label", "Fruit"));
    }
    if (!List.of(properties).contains("options")) {
      all.addAll(List.of("options", options("Apple", "Banana", "Cherry")));
    }
    if (!List.of(properties).contains("value")) {
      all.addAll(List.of("value", path("/choice")));
    }
    all.addAll(List.of(properties));
    return component("root", "ChoicePicker", all.toArray());
  }

  /** Makes a surface whose root is a picker of three fruits with the value of /choice. */
  private void show(Map<String, Object> data, Object... properties) {
    fixture.create(data, picker(properties));
    fixture.model().dataModel().subscribe("/choice", writes::add);
  }

  private JComponent group() {
    return fixture.viewOf("root").accessibleComponent();
  }

  private JComponent panel() {
    return fixture.componentOf("root");
  }

  private JTextField filter() {
    return (JTextField) group().getParent().getComponent(0);
  }

  private List<AbstractButton> buttons() {
    List<AbstractButton> buttons = new ArrayList<>();
    collect(group(), buttons);
    return buttons;
  }

  private static void collect(Component component, List<AbstractButton> buttons) {
    if (component instanceof AbstractButton button) {
      buttons.add(button);
    } else if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        collect(child, buttons);
      }
    }
  }

  private List<Boolean> selected() {
    return buttons().stream().map(AbstractButton::isSelected).toList();
  }

  /** Gets the item of an option in the group: a row with a button, or a chip. */
  private Component item(int index) {
    return group().getComponent(index);
  }

  private Rectangle boundsOf(Component component) {
    return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), panel());
  }

  @Test
  void theDefaultIsARadioButtonForEachOptionBelowTheLabel() {
    Edt.run(() -> {
      show(Map.of("choice", List.of("banana")));

      List<AbstractButton> buttons = buttons();
      assertEquals(3, buttons.size());
      for (AbstractButton button : buttons) {
        assertInstanceOf(JRadioButton.class, button);
      }
      assertEquals(List.of(false, true, false), selected());
      assertEquals(
          List.of("apple", "banana", "cherry"),
          buttons.stream().map(AbstractButton::getActionCommand).toList());
      assertEquals("Apple", ((ToggleRow) item(0)).getText());
      assertEquals("Apple", buttons.get(0).getAccessibleContext().getAccessibleName());
      JLabel label = (JLabel) panel().getComponent(0);
      assertEquals("Fruit", label.getText());
      assertSame(group(), label.getLabelFor());
      assertEquals(AccessibleRole.GROUP_BOX, group().getAccessibleContext().getAccessibleRole());
      assertEquals("Fruit", group().getAccessibleContext().getAccessibleName());
      assertFalse(filter().isVisible());
      // The options are in one column below the label.
      assertTrue(boundsOf(label).y + boundsOf(label).height <= boundsOf(item(0)).y);
      assertEquals(boundsOf(item(0)).x, boundsOf(item(2)).x);
      assertEquals(boundsOf(item(0)).y + boundsOf(item(0)).height, boundsOf(item(1)).y);
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void aSelectionOfTheUserWritesAListWithOneValue() {
    Edt.run(() -> {
      show(Map.of("choice", List.of("banana")));
      List<AbstractButton> buttons = buttons();

      buttons.get(2).doClick(0);

      assertEquals(List.of("cherry"), fixture.data("/choice"));
      assertEquals(List.of(false, false, true), selected());
      assertEquals(List.of(List.of("cherry")), writes);

      // A click on the selected option changes nothing, and writes nothing.
      buttons.get(2).doClick(0);
      assertEquals(1, writes.size());

      // A click on the text of an option selects it.
      ((ToggleRow) item(0)).textLabel().dispatchEvent(click(((ToggleRow) item(0)).textLabel()));
      assertEquals(List.of("apple"), fixture.data("/choice"));
      assertEquals(List.of(), fixture.errors);
    });
  }

  private static MouseEvent click(Component target) {
    return new MouseEvent(
        target, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 1, false, MouseEvent.BUTTON1);
  }

  @Test
  void theMultipleSelectionVariantHasCheckBoxesAndWritesTheValuesInTheSequenceOfTheOptions() {
    Edt.run(() -> {
      show(Map.of("choice", List.of()), "variant", "multipleSelection");
      List<AbstractButton> buttons = buttons();
      for (AbstractButton button : buttons) {
        assertInstanceOf(JCheckBox.class, button);
      }

      buttons.get(2).doClick(0);
      buttons.get(0).doClick(0);

      assertEquals(List.of("apple", "cherry"), fixture.data("/choice"));

      buttons.get(2).doClick(0);

      assertEquals(List.of("apple"), fixture.data("/choice"));
      assertEquals(List.of(true, false, false), selected());

      buttons.get(0).doClick(0);
      assertEquals(List.of(), fixture.data("/choice"));
      assertEquals(4, writes.size());
    });
  }

  @Test
  void aNewValueOfTheDataModelShowsAndWritesNothing() {
    Edt.run(() -> {
      show(Map.of("choice", List.of("apple"), "other", 1L), "variant", "multipleSelection");
      List<AbstractButton> buttons = buttons();
      List<String> changes = new ArrayList<>();
      for (AbstractButton button : buttons) {
        button.addItemListener(event -> changes.add(button.getActionCommand()));
      }

      fixture.setData("/choice", List.of("banana", "cherry"));

      assertEquals(List.of(false, true, true), selected());
      assertEquals(List.of(List.of("banana", "cherry")), writes);
      assertEquals(3, changes.size());

      // The same selection does not touch the options.
      fixture.setData("/choice", List.of("cherry", "banana"));
      fixture.setData("/other", 2L);
      fixture.update(picker("variant", "multipleSelection", "label", "Fruits"));

      assertEquals(3, changes.size());
      assertEquals(2, writes.size());
      assertEquals(buttons, buttons());
    });
  }

  @Test
  void aValueThatIsNotTheValueOfAnOptionDoesNotShowAndIsNotWritten() {
    Edt.run(() -> {
      show(Map.of("choice", List.of("grape", "cherry")));

      assertEquals(List.of(false, false, true), selected());

      fixture.setData("/choice", "banana");
      assertEquals(List.of(false, true, false), selected());

      fixture.setData("/choice", null);
      assertEquals(List.of(false, false, false), selected());

      fixture.setData("/choice", 42L);
      assertEquals(List.of(false, false, false), selected());
      assertEquals(3, writes.size());
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void aMutuallyExclusivePickerShowsOnlyTheFirstValueOfTheList() {
    Edt.run(() -> {
      show(Map.of("choice", List.of("cherry", "apple")));

      assertEquals(List.of(false, false, true), selected());
      assertEquals(List.of(), writes);
    });
  }

  @Test
  void newOptionsKeepTheSelectionWhereTheValuesStillExist() {
    Edt.run(() -> {
      show(Map.of("choice", List.of("banana", "cherry")), "variant", "multipleSelection");
      List<AbstractButton> before = buttons();

      fixture.update(
          picker(
              "variant",
              "multipleSelection",
              "options",
              options("Cherry", "Date", "Banana", "Elderberry")));

      List<AbstractButton> after = buttons();
      assertEquals(4, after.size());
      // The controls of the first three options stay. Only their text changes.
      assertEquals(before, after.subList(0, 3));
      assertEquals(List.of(true, false, true, false), selected());
      assertEquals("Date", ((ToggleRow) item(1)).getText());
      assertEquals(List.of(), writes);

      fixture.update(picker("variant", "multipleSelection", "options", options("Date")));

      assertEquals(1, buttons().size());
      assertEquals(List.of(false), selected());
      assertEquals(1, group().getComponentCount());
      assertEquals(List.of(), writes);
      assertEquals(List.of("banana", "cherry"), fixture.data("/choice"));

      // The next change of the user writes the selection that the view shows.
      buttons().getFirst().doClick(0);
      assertEquals(List.of("date"), fixture.data("/choice"));
    });
  }

  @Test
  void aChangeOfTheVariantMakesNewControls() {
    Edt.run(() -> {
      show(Map.of("choice", List.of("banana")));
      List<AbstractButton> before = buttons();

      fixture.update(picker("variant", "multipleSelection"));

      List<AbstractButton> after = buttons();
      assertNotSame(before.get(1), after.get(1));
      assertInstanceOf(JCheckBox.class, after.get(1));
      assertEquals(List.of(false, true, false), selected());
      for (AbstractButton button : before) {
        assertEquals(0, button.getActionListeners().length);
      }
      assertEquals(List.of(), writes);
    });
  }

  @Test
  void theChipsStyleHasAToggleButtonForEachOptionThatWrapsToMoreLines() {
    Edt.run(() -> {
      List<Object> many =
          options("Red", "Orange", "Yellow", "Green", "Blue", "Indigo", "Violet", "Black");
      show(
          Map.of("choice", List.of("green")),
          "displayStyle",
          "chips",
          "variant",
          "multipleSelection",
          "options",
          many);

      List<AbstractButton> chips = buttons();
      assertEquals(8, chips.size());
      for (AbstractButton chip : chips) {
        assertInstanceOf(JToggleButton.class, chip);
        assertFalse(chip instanceof JCheckBox || chip instanceof JRadioButton);
        assertSame(group(), chip.getParent());
      }
      assertEquals("Green", chips.get(3).getText());
      assertTrue(chips.get(3).isSelected());
      // The chips are side by side.
      assertEquals(boundsOf(chips.get(0)).y, boundsOf(chips.get(1)).y);
      assertTrue(boundsOf(chips.get(0)).x < boundsOf(chips.get(1)).x);

      chips.get(0).doClick(0);
      assertEquals(List.of("red", "green"), fixture.data("/choice"));

      fixture.resize(200, 300);

      // In a narrow view, the chips are in more lines, and each chip is in the view.
      Set<Integer> lines = new HashSet<>();
      for (AbstractButton chip : chips) {
        Rectangle bounds = boundsOf(chip);
        lines.add(bounds.y);
        assertTrue(bounds.x >= 8 && bounds.x + bounds.width <= 200 - 8, bounds.toString());
      }
      assertTrue(lines.size() >= 3, "lines " + lines);
      FlexPanel panel = (FlexPanel) panel();
      assertTrue(panel.preferredHeightFor(200) > panel.preferredHeightFor(2000));
      Rectangle last = boundsOf(chips.getLast());
      assertTrue(last.y + last.height <= panel.getHeight() - 8);
    });
  }

  @Test
  void mutuallyExclusiveChipsSelectOneOption() {
    Edt.run(() -> {
      show(Map.of("choice", List.of("apple")), "displayStyle", "chips");

      buttons().get(1).doClick(0);

      assertEquals(List.of("banana"), fixture.data("/choice"));
      assertEquals(List.of(false, true, false), selected());
    });
  }

  @Test
  void theFilterShowsOnlyTheOptionsThatContainItsTextAndKeepsTheSelection() {
    Edt.run(() -> {
      show(
          Map.of("choice", List.of("apple")),
          "filterable",
          true,
          "options",
          options("Apple", "Banana", "Cherry", "Pineapple"));
      JTextField filter = filter();

      assertTrue(filter.isVisible());
      assertTrue(boundsOf(filter).y + boundsOf(filter).height <= boundsOf(group()).y);
      assertEquals("Fruit", filter.getAccessibleContext().getAccessibleName());

      filter.setText("AN");

      assertEquals(List.of(false, true, false, false), visibleItems());
      // The selected option that the filter hides stays selected.
      assertEquals(List.of(true, false, false, false), selected());
      assertEquals(List.of(), writes);

      filter.setText("apple");
      fixture.view.validate();
      assertEquals(List.of(true, false, false, true), visibleItems());
      // The visible options are together at the top.
      assertEquals(boundsOf(item(0)).y + boundsOf(item(0)).height, boundsOf(item(3)).y);

      buttons().get(3).doClick(0);
      assertEquals(List.of("pineapple"), fixture.data("/choice"));

      filter.setText("");
      assertEquals(List.of(true, true, true, true), visibleItems());

      filter.setText("zzz");
      assertEquals(List.of(false, false, false, false), visibleItems());

      // A picker that stops to be filterable shows all the options again.
      fixture.update(picker("options", options("Apple", "Banana", "Cherry", "Pineapple")));
      assertFalse(filter.isVisible());
      assertEquals("", filter.getText());
      assertEquals(List.of(true, true, true, true), visibleItems());
    });
  }

  private List<Boolean> visibleItems() {
    List<Boolean> visible = new ArrayList<>();
    for (Component item : group().getComponents()) {
      visible.add(item.isVisible());
    }
    return visible;
  }

  @Test
  void manyOptionsGoInColumnsWhenTheWidthIsSufficient() {
    Edt.run(() -> {
      List<String> labels = new ArrayList<>();
      for (int i = 1; i <= 30; i++) {
        labels.add("Item " + i);
      }
      show(Map.of("choice", List.of()), "options", options(labels.toArray(String[]::new)));

      // The first column has the first options, and the second column the next ones.
      Rectangle first = boundsOf(item(0));
      Rectangle second = boundsOf(item(1));
      assertEquals(first.x, second.x);
      int rows = 0;
      while (rows < 30 && boundsOf(item(rows)).x == first.x) {
        rows++;
      }
      assertTrue(rows >= ChoicePickerView.ROWS_IN_ONE_COLUMN && rows < 30, "rows " + rows);
      Rectangle next = boundsOf(item(rows));
      assertEquals(first.y, next.y);
      assertTrue(next.x > first.x + first.width);
      for (int i = 0; i < 30; i++) {
        Rectangle bounds = boundsOf(item(i));
        assertTrue(bounds.x + bounds.width <= BasicFixture.WIDTH - 8, i + " " + bounds);
      }

      // A view that is too narrow for two columns has one column.
      fixture.resize(item(29).getPreferredSize().width + 16 + 10, 300);
      assertEquals(boundsOf(item(0)).x, boundsOf(item(29)).x);
    });
  }

  @Test
  void aValueWithNoBindingChangesOnlyTheOptions() {
    Edt.run(() -> {
      show(Map.of("choice", List.of()), "value", List.of("apple"));

      buttons().get(1).doClick(0);

      assertEquals(List.of(false, true, false), selected());
      assertEquals(List.of(), fixture.data("/choice"));
      assertEquals(List.of(), fixture.errors);

      fixture.update(picker("value", List.of("apple"), "label", "Other"));
      assertEquals(List.of(true, false, false), selected());
    });
  }

  @Test
  void theMessagesOfTheChecksShowBelowTheOptions() {
    Edt.run(() -> {
      show(
          Map.of("choice", List.of(), "ok", false),
          "checks",
          List.of(Map.of("condition", path("/ok"), "message", "Select a fruit.")));

      JComponent lines = (JComponent) panel().getComponent(2);
      assertTrue(lines.isVisible());
      assertEquals("Select a fruit.", ((WrappingLabel) lines.getComponent(0)).getText());
      assertEquals("Select a fruit.", group().getToolTipText());
      assertTrue(boundsOf(group()).y + boundsOf(group()).height <= boundsOf(lines).y);

      fixture.setData("/ok", true);
      assertFalse(lines.isVisible());
      assertNull(group().getToolTipText());
    });
  }

  @Test
  void theAccessibleNameOfTheAgentWins() {
    Edt.run(() -> {
      show(Map.of("choice", List.of()), "@accessibility", object("label", "Select a fruit"));

      assertEquals("Select a fruit", group().getAccessibleContext().getAccessibleName());
    });
  }

  @Test
  void theDisposalRemovesTheListenersOfTheOptions() {
    Edt.run(() -> {
      show(Map.of("choice", List.of()));
      List<AbstractButton> buttons = buttons();
      int listeners = buttons.getFirst().getActionListeners().length;

      fixture.view.removeNotify();

      assertEquals(listeners - 1, buttons.getFirst().getActionListeners().length);
      buttons.getFirst().doClick(0);
      assertEquals(List.of(), fixture.data("/choice"));
      assertEquals(0, fixture.view.activeSubscriptionCount());
    });
  }
}
