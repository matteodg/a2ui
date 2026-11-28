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
import static com.google.a2ui.swing.testing.TestSurface.event;
import static com.google.a2ui.swing.testing.TestSurface.object;
import static com.google.a2ui.swing.testing.TestSurface.path;
import static com.google.a2ui.swing.testing.TestSurface.template;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.state.RendererAction;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.Pointer;
import com.google.a2ui.swing.testing.TestModalPresenter;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The user cannot operate a part of a surface that the attribute {@code hidden} hides.
 *
 * <p>The surface of each test is the content of a root pane, as in a window, and it is 400 pixels
 * wide and 300 pixels high. {@link Pointer} sends the events of the user. The data value {@code
 * /secret} hides the component {@code part} and all the components below it.
 *
 * <p>The adapter starts to keep the mouse out of a hidden part in the event after the event that
 * hides the part. Thus each test is a sequence of events of the event dispatch thread.
 */
class HiddenInputTest {
  private final TestModalPresenter presenter = new TestModalPresenter();
  private final BasicFixture fixture =
      new BasicFixture(A2uiServices.DEFAULT.withModalPresenter(presenter)).inRootPane();

  /** Runs each block as one event of the event dispatch thread. */
  private static void events(Edt.Block... blocks) {
    for (Edt.Block block : blocks) {
      Edt.run(block);
    }
  }

  private static Map<String, Object> hidden() {
    return Map.of("hidden", path("/secret"));
  }

  private static Object text(String id, String text) {
    return component(id, "Text", "text", text);
  }

  /**
   * Makes a surface with a part that the data can hide, and a button after the part.
   *
   * @param secret true if the part is hidden at the start
   * @param children the IDs of the children of the part
   * @param components the components of the part
   */
  private void show(boolean secret, List<String> children, Object... components) {
    List<Object> all = new ArrayList<>();
    all.add(component("root", "Column", "children", List.of("part", "after")));
    all.add(component("part", "Column", "children", children, "@accessibility", hidden()));
    all.add(component("after", "Button", "child", "after_text", "action", event("after")));
    all.add(text("after_text", "After"));
    all.addAll(List.of(components));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("secret", secret);
    data.put("checked", false);
    data.put("choice", List.of("apple"));
    data.put("name", "");
    fixture.create(data, all.toArray());
  }

  private static Object[] controls() {
    return new Object[] {
      component("go", "Button", "child", "go_text", "action", event("go")),
      text("go_text", "Go"),
      component("check", "CheckBox", "label", "Agree", "value", path("/checked")),
      component(
          "picker",
          "ChoicePicker",
          "label",
          "Fruit",
          "variant",
          "mutuallyExclusive",
          "options",
          List.of(
              object("label", "Apple", "value", "apple"),
              object("label", "Banana", "value", "banana")),
          "value",
          path("/choice")),
      component("field", "TextField", "label", "Name", "value", path("/name"))
    };
  }

  private void showControls(boolean secret) {
    show(secret, List.of("go", "check", "picker", "field"), controls());
  }

  private JComponent control(String id) {
    return fixture.viewOf(id).accessibleComponent();
  }

  private List<String> actionNames() {
    return fixture.actions.stream().map(RendererAction::name).toList();
  }

  /** Gets the button of the option of the picker at an index. */
  private AbstractButton option(int index) {
    List<AbstractButton> buttons = new ArrayList<>();
    collect(control("picker"), buttons);
    return buttons.get(index);
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

  /** Tells if a component is in the content of the root pane, where the views are. */
  private boolean inContent(Component component) {
    return component != null
        && SwingUtilities.isDescendingFrom(component, fixture.rootPane.getContentPane());
  }

  /** Counts the components that the adapter put in the layered pane of the root pane. */
  private int shields() {
    return fixture.rootPane.getLayeredPane().getComponentCount() - 1;
  }

  /** Makes sure that the user operates each control of the part. */
  private void assertTheControlsWork() {
    int actions = fixture.actions.size();
    assertSame(control("go"), Pointer.click(control("go")));
    assertEquals(actions + 1, fixture.actions.size());
    assertEquals("go", fixture.actions.getLast().name());

    fixture.setData("/checked", false);
    assertSame(control("check"), Pointer.click(control("check")));
    assertEquals(true, fixture.data("/checked"));
    // The click clears the check box, and the Space key sets it again.
    assertTrue(Pointer.clickAndType(control("check"), KeyEvent.VK_SPACE, ' '));
    assertEquals(true, fixture.data("/checked"));

    fixture.setData("/choice", List.of("apple"));
    assertSame(option(1), Pointer.click(option(1)));
    assertEquals(List.of("banana"), fixture.data("/choice"));

    fixture.setData("/name", "");
    assertTrue(Pointer.clickAndType(control("field"), KeyEvent.VK_A, 'a'));
    assertEquals("a", fixture.data("/name"));
  }

  @Test
  void theControlsOfAPartThatIsNotHiddenGetTheEventsOfTheUser() {
    events(
        () -> showControls(false),
        () -> {
          assertEquals(0, shields());
          assertTheControlsWork();
        });
  }

  @Test
  void aClickAndAKeyOnAHiddenButtonRunNoAction() {
    events(
        () -> showControls(true),
        () -> {
          Component target = Pointer.click(control("go"));

          assertNotNull(target);
          assertFalse(inContent(target), "the click goes to " + target);
          assertEquals(List.of(), actionNames());
          assertFalse(((JButton) control("go")).getModel().isArmed());
          assertFalse(Pointer.clickAndType(control("go"), KeyEvent.VK_SPACE, ' '));
          assertEquals(List.of(), actionNames());

          // The button after the hidden part gets its events.
          assertSame(control("after"), Pointer.click(control("after")));
          assertEquals(List.of("after"), actionNames());
        });
  }

  @Test
  void aClickAndAKeyOnAHiddenCheckBoxWriteNothing() {
    events(
        () -> showControls(true),
        () -> {
          assertFalse(inContent(Pointer.click(control("check"))));
          assertFalse(Pointer.clickAndType(control("check"), KeyEvent.VK_SPACE, ' '));

          assertEquals(false, fixture.data("/checked"));
          assertFalse(((JCheckBox) control("check")).isSelected());
        });
  }

  @Test
  void aClickAndAKeyOnAnOptionOfAHiddenChoicePickerWriteNothing() {
    events(
        () -> showControls(true),
        () -> {
          assertFalse(inContent(Pointer.click(option(1))));
          assertFalse(Pointer.clickAndType(option(1), KeyEvent.VK_SPACE, ' '));

          assertEquals(List.of("apple"), fixture.data("/choice"));
          assertTrue(option(0).isSelected());
          assertFalse(option(1).isSelected());
        });
  }

  @Test
  void aClickAndAKeyOnAHiddenTextFieldWriteNothing() {
    events(
        () -> showControls(true),
        () -> {
          JTextField field = (JTextField) control("field");

          assertFalse(inContent(Pointer.click(field)));
          assertFalse(Pointer.clickAndType(field, KeyEvent.VK_A, 'a'));

          assertEquals("", fixture.data("/name"));
          assertEquals("", field.getText());
          assertFalse(field.isFocusable());
        });
  }

  @Test
  void theControlsWorkAgainWhenThePartIsNotHiddenAndTheLayoutIsTheSame() {
    Map<String, JComponent> components = new LinkedHashMap<>();
    Map<String, Rectangle> bounds = new LinkedHashMap<>();
    events(
        () -> showControls(true),
        () -> {
          for (String id : List.of("part", "go", "check", "picker", "field", "after")) {
            components.put(id, fixture.componentOf(id));
            bounds.put(id, fixture.bounds(id));
          }
          assertTrue(shields() > 0);

          fixture.setData("/secret", false);

          // The user operates the controls at once, in the event that shows the part again.
          assertTheControlsWork();
          for (String id : components.keySet()) {
            assertSame(components.get(id), fixture.componentOf(id), id);
            assertEquals(bounds.get(id), fixture.bounds(id), id);
          }
        },
        () -> {
          assertEquals(0, shields());
          assertTheControlsWork();
          fixture.setData("/name", "kept");
          fixture.setData("/secret", true);
        },
        () -> {
          int actions = fixture.actions.size();
          assertFalse(inContent(Pointer.click(control("go"))));
          assertFalse(Pointer.clickAndType(control("field"), KeyEvent.VK_B, 'b'));
          assertEquals(actions, fixture.actions.size());
          assertEquals("kept", fixture.data("/name"));
        });
  }

  @Test
  void aPartThatIsNotHiddenHasTheLayoutOfAPartThatIsHidden() {
    Map<String, Rectangle> hiddenBounds = new LinkedHashMap<>();
    List<String> ids = List.of("part", "go", "check", "picker", "field", "after");
    events(
        () -> showControls(true),
        () -> ids.forEach(id -> hiddenBounds.put(id, fixture.bounds(id))));
    BasicFixture plain = new BasicFixture();
    Edt.run(() -> {
      List<Object> all = new ArrayList<>();
      all.add(component("root", "Column", "children", List.of("part", "after")));
      all.add(component("part", "Column", "children", List.of("go", "check", "picker", "field")));
      all.add(component("after", "Button", "child", "after_text", "action", event("after")));
      all.add(text("after_text", "After"));
      all.addAll(List.of(controls()));
      plain.create(Map.of("checked", false, "choice", List.of("apple"), "name", ""), all.toArray());

      for (String id : ids) {
        assertEquals(plain.bounds(id), hiddenBounds.get(id), id);
      }
    });
  }

  @Test
  void aHiddenPartThatTheSurfaceRemovesLeavesNoShield() {
    events(
        () -> showControls(true),
        () -> {
          assertTrue(shields() > 0);
          fixture.update(component("root", "Column", "children", List.of("after")));
        },
        () -> {
          assertEquals(0, shields());
          assertSame(control("after"), Pointer.click(control("after")));
          assertEquals(List.of("after"), actionNames());
        });
  }

  @Test
  void aSurfaceThatLeavesItsWindowLeavesNoShieldAndGivesTheFocusValuesBack() {
    JComponent[] go = {null};
    events(
        () -> showControls(true),
        () -> {
          go[0] = control("go");
          assertTrue(shields() > 0);
          assertFalse(go[0].isFocusable());

          fixture.rootPane.getContentPane().remove(fixture.view);

          assertTrue(go[0].isFocusable(), "the button has its focus value again");
        },
        () -> assertEquals(0, shields()));
  }

  @Test
  void aRootPaneThatClosesWithAHiddenPartHasNoError() {
    events(
        () -> showControls(true),
        () -> {
          assertTrue(shields() > 0);
          // A window does this when it closes: each component of the layered pane, from the last
          // to the first, stops to be displayable. The surface disposes its views at that time.
          fixture.rootPane.removeNotify();
        },
        () -> assertEquals(0, shields()));
  }

  @Test
  void aClickOnATabOfHiddenTabsDoesNotSelectTheTab() {
    int[] point = new int[2];
    events(
        () ->
            show(
                true,
                List.of("tabs"),
                component(
                    "tabs",
                    "Tabs",
                    "tabs",
                    List.of(
                        object("title", "One", "child", "one"),
                        object("title", "Two", "child", "two"))),
                text("one", "First content"),
                text("two", "Second content")),
        () -> {
          JTabbedPane pane = (JTabbedPane) control("tabs");
          Rectangle second = pane.getBoundsAt(1);
          point[0] = second.x + second.width / 2;
          point[1] = second.y + second.height / 2;

          assertFalse(inContent(Pointer.click(pane, point[0], point[1])));
          assertEquals(0, pane.getSelectedIndex());

          fixture.setData("/secret", false);

          assertSame(pane, Pointer.click(pane, point[0], point[1]));
          assertEquals(1, pane.getSelectedIndex());
        });
  }

  @Test
  void aClickOnTheTriggerOfAHiddenModalDoesNotOpenTheModal() {
    events(
        () ->
            show(
                true,
                List.of("modal", "plain"),
                component("modal", "Modal", "trigger", "open", "content", "body"),
                component("open", "Button", "child", "open_text", "action", event("opened")),
                text("open_text", "Open"),
                text("body", "The details"),
                component("plain", "Modal", "trigger", "more", "content", "plain_body"),
                text("more", "More information"),
                text("plain_body", "More details")),
        () -> {
          assertFalse(inContent(Pointer.click(control("open"))));
          assertFalse(inContent(Pointer.click(fixture.componentOf("more"))));

          assertEquals(List.of(), presenter.open());
          assertEquals(List.of(), actionNames());

          fixture.setData("/secret", false);
          Pointer.click(fixture.componentOf("more"));

          assertEquals(1, presenter.open().size());
          presenter.open().get(0).userCloses();

          Pointer.click(control("open"));

          assertEquals(List.of("opened"), actionNames());
          assertEquals(1, presenter.open().size());
        });
  }

  @Test
  void aTurnOfTheWheelOnAHiddenItemScrollsTheListAndAHiddenListDoesNotScroll() {
    int[] scrolled = {0};
    events(
        () -> {
          List<Map<String, Object>> items = new ArrayList<>();
          for (int i = 0; i < 40; i++) {
            items.add(Map.of("name", "Item " + i, "off", i < 20));
          }
          fixture.create(
              Map.of("items", items, "secret", false),
              component(
                  "root",
                  "List",
                  "children",
                  template("item", "/items"),
                  "@accessibility",
                  hidden()),
              component(
                  "item",
                  "Text",
                  "text",
                  path("name"),
                  "@accessibility",
                  Map.of("hidden", path("off"))));
        },
        () -> {
          JScrollPane pane = (JScrollPane) fixture.componentOf("root");
          Component firstItem = ((Container) pane.getViewport().getView()).getComponent(0);

          // The first item is hidden and the list is not. The turn goes to the shield of the
          // item, and the shield gives it to the list.
          Component target = Pointer.wheel(firstItem, 1);

          assertFalse(inContent(target), "the turn goes to " + target);
          scrolled[0] = pane.getVerticalScrollBar().getValue();
          assertTrue(scrolled[0] > 0, "the list scrolls");

          fixture.setData("/secret", true);
        },
        () -> {
          JScrollPane pane = (JScrollPane) fixture.componentOf("root");

          assertFalse(inContent(Pointer.wheel(pane, 1)));
          assertEquals(scrolled[0], pane.getVerticalScrollBar().getValue());

          fixture.setData("/secret", false);
          Pointer.wheel(pane, 1);

          assertTrue(
              pane.getVerticalScrollBar().getValue() > scrolled[0], "the list scrolls again");
        });
  }
}
