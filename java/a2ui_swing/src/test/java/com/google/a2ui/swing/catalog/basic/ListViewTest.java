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
import static com.google.a2ui.swing.testing.TestSurface.path;
import static com.google.a2ui.swing.testing.TestSurface.template;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.catalog.basic.FlexLayout.Align;
import com.google.a2ui.swing.catalog.basic.FlexLayout.Axis;
import com.google.a2ui.swing.testing.Edt;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import org.junit.jupiter.api.Test;

/**
 * The tests use short texts as children. Each text is one line, and each has the margin of a leaf.
 * The surface is 400 pixels wide and 300 pixels high.
 */
class ListViewTest {
  private static final String LONG_TEXT =
      "An item of a list has a text of many words. The text is too long for one line, and thus"
          + " it needs more lines when the list is narrow.";

  private final BasicFixture fixture = new BasicFixture();

  private static Object text(String id, String text) {
    return component(id, "Text", "text", text);
  }

  private static List<Map<String, Object>> items(int count) {
    List<Map<String, Object>> items = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      items.add(Map.of("name", "Item " + i));
    }
    return items;
  }

  /** Makes a surface whose root is a list with the three children a, b and c. */
  private void show(Object... properties) {
    List<Object> all = new ArrayList<>(List.of("children", List.of("a", "b", "c")));
    all.addAll(List.of(properties));
    fixture.create(
        Map.of(),
        component("root", "List", all.toArray()),
        text("a", "one"),
        text("b", "second"),
        text("c", "3"));
  }

  /** Makes a surface whose root is a list with one child for each item of the data. */
  private void showItems(int count, Object... properties) {
    List<Object> all = new ArrayList<>(List.of("children", template("item", "/items")));
    all.addAll(List.of(properties));
    fixture.create(
        Map.of("items", items(count)),
        component("root", "List", all.toArray()),
        component("item", "Text", "text", path("name")));
  }

  private JScrollPane pane(String id) {
    return (JScrollPane) fixture.componentOf(id);
  }

  private FlexPanel content(String id) {
    return (FlexPanel) pane(id).getViewport().getView();
  }

  private int height(String id) {
    return fixture.componentOf(id).getPreferredSize().height;
  }

  private int width(String id) {
    return fixture.componentOf(id).getPreferredSize().width;
  }

  private static MouseWheelEvent wheel(JComponent target, int rotation) {
    return new MouseWheelEvent(
        target,
        MouseWheelEvent.MOUSE_WHEEL,
        0L,
        0,
        10,
        10,
        0,
        false,
        MouseWheelEvent.WHEEL_UNIT_SCROLL,
        3,
        rotation);
  }

  @Test
  void aListPutsEachChildBelowTheChildBeforeItAndFillsTheWidth() {
    Edt.run(() -> {
      show();

      assertEquals(Axis.VERTICAL, content("root").flexLayout().axis());
      assertEquals(Align.STRETCH, content("root").flexLayout().align());
      assertEquals(new Rectangle(0, 0, 400, 300), fixture.bounds("root"));
      assertEquals(new Rectangle(0, 0, 400, height("a")), fixture.bounds("a"));
      assertEquals(new Rectangle(0, height("a"), 400, height("b")), fixture.bounds("b"));
      assertEquals(
          new Rectangle(0, height("a") + height("b"), 400, height("c")), fixture.bounds("c"));
      assertEquals(3, fixture.node("root").children().size());
    });
  }

  @Test
  void aListHasNoBorderNoPaddingNoGapAndNoBackground() {
    Edt.run(() -> {
      show();

      assertEquals(new Insets(0, 0, 0, 0), pane("root").getInsets());
      assertEquals(new Insets(0, 0, 0, 0), content("root").getInsets());
      assertEquals(0, content("root").flexLayout().gap());
      assertFalse(pane("root").isOpaque());
      assertFalse(pane("root").getViewport().isOpaque());
      assertFalse(content("root").isOpaque());
    });
  }

  @Test
  void aListThatHasSufficientSpaceShowsAllItsChildrenWithNoScrollBar() {
    Edt.run(() -> {
      show();

      assertFalse(pane("root").getVerticalScrollBar().isVisible());
      assertFalse(pane("root").getHorizontalScrollBar().isVisible());
      assertEquals(
          ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
          pane("root").getVerticalScrollBarPolicy());
      assertEquals(
          ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
          pane("root").getHorizontalScrollBarPolicy());
      assertEquals(height("a") + height("b") + height("c"), pane("root").getPreferredSize().height);
    });
  }

  @Test
  void aListThatGetsLessThanItsContentScrolls() {
    Edt.run(() -> {
      showItems(40);
      JScrollPane pane = pane("root");
      int bar = pane.getVerticalScrollBar().getPreferredSize().width;

      assertEquals(new Rectangle(0, 0, 400, 300), pane.getBounds());
      assertTrue(pane.getVerticalScrollBar().isVisible(), "the scroll bar shows");
      assertEquals(400 - bar, pane.getViewport().getWidth());
      assertEquals(300, pane.getViewport().getHeight());
      // The content has its full height, and each child has the width of the visible area.
      assertEquals(40 * fixture.componentOf("item").getHeight(), content("root").getHeight());
      assertTrue(content("root").getHeight() > 300);
      assertEquals(400 - bar, content("root").getWidth());
      assertEquals(40, content("root").getComponentCount());

      pane.getVerticalScrollBar().setValue(120);

      assertEquals(120, pane.getViewport().getViewPosition().y);
    });
  }

  @Test
  void aListInAParentWithNoLimitHasTheHeightOfItsContent() {
    Edt.run(() -> {
      fixture.create(
          Map.of("items", items(40)),
          component("root", "Column", "children", List.of("title", "list", "end")),
          text("title", "Title"),
          component("list", "List", "children", template("item", "/items")),
          component("item", "Text", "text", path("name")),
          text("end", "End"));
      // A surface in a scroll pane has the height that its content needs.
      int needed = fixture.view.preferredHeightFor(400);
      fixture.resize(400, needed);
      int item = fixture.componentOf("item").getHeight();

      assertEquals(height("title") + 40 * item + height("end"), needed);
      assertEquals(40 * item, fixture.bounds("list").height);
      assertFalse(pane("list").getVerticalScrollBar().isVisible());
      assertEquals(400, content("list").getWidth());
      assertEquals(needed - height("end"), fixture.bounds("end").y);
    });
  }

  @Test
  void aListInAColumnThatIsTooHighBecomesSmallerAndScrolls() {
    Edt.run(() -> {
      fixture.create(
          Map.of("items", items(40)),
          component("root", "Column", "children", List.of("title", "list", "end")),
          text("title", "Title"),
          component("list", "List", "children", template("item", "/items")),
          component("item", "Text", "text", path("name")),
          text("end", "End"));

      // The surface is 300 pixels high. The texts keep their height, and the list gets the rest.
      assertEquals(height("title"), fixture.bounds("title").height);
      assertEquals(height("end"), fixture.bounds("end").height);
      assertEquals(300 - height("title") - height("end"), fixture.bounds("list").height);
      assertEquals(300 - height("end"), fixture.bounds("end").y);
      assertTrue(pane("list").getVerticalScrollBar().isVisible());
      assertTrue(content("list").getHeight() > fixture.bounds("list").height);
    });
  }

  @Test
  void aListCanBecomeAsSmallAsItsMinimumExtentAndNotSmaller() {
    Edt.run(() -> {
      showItems(40);

      assertEquals(ListView.MINIMUM_EXTENT, pane("root").getMinimumSize().height);

      fixture.update(component("root", "List", "children", List.of("item")));

      // A list whose content is smaller than the minimum extent has the size of its content.
      assertTrue(pane("root").getPreferredSize().height < ListView.MINIMUM_EXTENT);
      assertEquals(pane("root").getPreferredSize().height, pane("root").getMinimumSize().height);
    });
  }

  @Test
  void theTextOfANarrowListGetsMoreLines() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Column", "children", List.of("list", "below")),
          component("list", "List", "children", List.of("long")),
          text("long", LONG_TEXT),
          text("below", "Below"));
      HeightForWidth list = (HeightForWidth) pane("list");
      fixture.resize(400, 1000);
      int wide = fixture.bounds("list").height;

      assertEquals(list.preferredHeightFor(400), wide);
      assertTrue(list.preferredHeightFor(200) > wide, "a narrow list is higher");

      fixture.resize(200, 1000);

      assertEquals(list.preferredHeightFor(200), fixture.bounds("list").height);
      assertEquals(200, fixture.bounds("long").width);
      assertEquals(fixture.bounds("list").height, fixture.bounds("long").height);
      assertEquals(fixture.bounds("list").height, fixture.bounds("below").y);
      assertFalse(pane("list").getVerticalScrollBar().isVisible());
    });
  }

  @Test
  void aListThatGrowsMakesItsParentDoTheLayoutAgain() {
    Edt.run(() -> {
      fixture.create(
          Map.of("items", items(2)),
          component("root", "Column", "children", List.of("list", "end")),
          component("list", "List", "children", template("item", "/items")),
          component("item", "Text", "text", path("name")),
          text("end", "End"));
      int item = fixture.componentOf("item").getHeight();

      assertFalse(pane("list").isValidateRoot());
      assertEquals(2 * item, fixture.bounds("list").height);
      assertEquals(2 * item, fixture.bounds("end").y);

      fixture.setData("/items", items(5));

      assertEquals(5 * item, fixture.bounds("list").height);
      assertEquals(5 * item, fixture.bounds("end").y);
      assertFalse(pane("list").getVerticalScrollBar().isVisible());
    });
  }

  @Test
  void aHorizontalListPutsTheChildrenSideBySideAndScrollsOnTheHorizontalAxis() {
    Edt.run(() -> {
      show("direction", "horizontal");

      assertEquals(Axis.HORIZONTAL, content("root").flexLayout().axis());
      assertEquals(
          ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, pane("root").getVerticalScrollBarPolicy());
      assertEquals(
          ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED,
          pane("root").getHorizontalScrollBarPolicy());
      // Each child has its preferred width and the height of the list.
      assertEquals(new Rectangle(0, 0, width("a"), 300), fixture.bounds("a"));
      assertEquals(new Rectangle(width("a"), 0, width("b"), 300), fixture.bounds("b"));
      assertEquals(new Rectangle(width("a") + width("b"), 0, width("c"), 300), fixture.bounds("c"));
      assertFalse(pane("root").getHorizontalScrollBar().isVisible());
    });
  }

  @Test
  void aHorizontalListThatIsWiderThanItsSpaceShowsAScrollBarBelowTheChildren() {
    Edt.run(() -> {
      fixture.create(
          Map.of("items", items(40)),
          component("root", "Column", "children", List.of("list", "below")),
          component(
              "list", "List", "direction", "horizontal", "children", template("item", "/items")),
          component("item", "Text", "text", path("name")),
          text("below", "Below"));
      JScrollPane pane = pane("list");
      int bar = pane.getHorizontalScrollBar().getPreferredSize().height;
      int item = fixture.componentOf("item").getPreferredSize().height;

      assertTrue(pane.getHorizontalScrollBar().isVisible(), "the scroll bar shows");
      assertFalse(pane.getVerticalScrollBar().isVisible());
      assertEquals(item + bar, fixture.bounds("list").height);
      assertEquals(item, pane.getViewport().getHeight());
      assertEquals(400, fixture.bounds("list").width);
      assertTrue(content("list").getWidth() > 400);
      assertEquals(item + bar, fixture.bounds("below").y);
      assertEquals(ListView.MINIMUM_EXTENT, pane.getMinimumSize().width);
    });
  }

  @Test
  void alignGivesThePositionOfEachChildOnTheCrossAxis() {
    Edt.run(() -> {
      show("align", "center");

      assertEquals(Align.CENTER, content("root").flexLayout().align());
      assertEquals(width("a"), fixture.bounds("a").width);
      assertEquals((400 - width("a")) / 2, fixture.bounds("a").x);

      fixture.update(component("root", "List", "children", List.of("a", "b", "c"), "align", "end"));

      assertEquals(400 - width("b"), fixture.bounds("b").x);

      fixture.update(component("root", "List", "children", List.of("a", "b", "c")));

      assertEquals(new Rectangle(0, 0, 400, height("a")), fixture.bounds("a"));
    });
  }

  @Test
  void anUpdateOfTheDirectionKeepsTheComponentsOfTheListAndOfTheChildren() {
    Edt.run(() -> {
      show();
      JScrollPane pane = pane("root");
      FlexPanel content = content("root");
      JComponent a = fixture.componentOf("a");

      fixture.update(
          component("root", "List", "children", List.of("a", "b", "c"), "direction", "horizontal"));

      assertSame(pane, pane("root"));
      assertSame(content, content("root"));
      assertSame(a, fixture.componentOf("a"));
      assertEquals(Axis.HORIZONTAL, content.flexLayout().axis());
      assertEquals(width("a"), fixture.bounds("b").x);

      fixture.update(component("root", "List", "children", List.of("c", "a")));

      assertSame(a, fixture.componentOf("a"));
      assertEquals(Axis.VERTICAL, content.flexLayout().axis());
      assertEquals(2, content.getComponentCount());
      assertEquals("3 one", VisibleText.of(content));
      assertEquals(height("c"), fixture.bounds("a").y);
    });
  }

  @Test
  void aTemplateGivesOneChildForEachItemAndAChildThatStaysKeepsItsComponent() {
    Edt.run(() -> {
      fixture.create(
          Map.of("items", List.of(Map.of("name", "Alpha"), Map.of("name", "Beta"))),
          component("root", "List", "children", template("item", "/items")),
          component("item", "Text", "text", path("name")));
      FlexPanel content = content("root");
      JComponent alpha = (JComponent) content.getComponent(0);
      int subscriptions = fixture.view.activeSubscriptionCount();

      assertEquals("Alpha Beta", VisibleText.of(content));

      fixture.setData(
          "/items",
          List.of(Map.of("name", "Alpha"), Map.of("name", "Gamma"), Map.of("name", "Beta")));

      assertEquals(3, content.getComponentCount());
      assertSame(alpha, content.getComponent(0));
      assertEquals("Alpha Gamma Beta", VisibleText.of(content));
      assertEquals(content.getComponent(0).getHeight(), content.getComponent(1).getY());

      fixture.setData("/items", List.of(Map.of("name", "Beta")));

      assertEquals(1, content.getComponentCount());
      assertSame(alpha, content.getComponent(0));
      assertEquals("Beta", VisibleText.of(content));
      assertTrue(fixture.view.activeSubscriptionCount() < subscriptions);
    });
  }

  @Test
  void aTurnOfTheMouseWheelScrollsTheListThatCanScroll() {
    Edt.run(() -> {
      showItems(40);
      JScrollPane pane = pane("root");
      List<MouseWheelEvent> outer = new ArrayList<>();
      pane.getParent().addMouseWheelListener(outer::add);

      pane.dispatchEvent(wheel(pane, 1));

      assertTrue(pane.getVerticalScrollBar().getValue() > 0, "the list scrolls");
      assertEquals(0, outer.size());
    });
  }

  @Test
  void aTurnOfTheMouseWheelGoesToTheParentIfTheListCannotScroll() {
    Edt.run(() -> {
      showItems(40);
      JScrollPane pane = pane("root");
      List<MouseWheelEvent> outer = new ArrayList<>();
      pane.getParent().addMouseWheelListener(outer::add);

      // The list is at its start. Thus it cannot scroll up.
      pane.dispatchEvent(wheel(pane, -1));

      assertEquals(0, pane.getVerticalScrollBar().getValue());
      assertEquals(1, outer.size());
      assertSame(pane.getParent(), outer.get(0).getSource());

      // A list that shows all its children has no scroll bar.
      fixture.setData("/items", items(2));
      pane.dispatchEvent(wheel(pane, 1));

      assertFalse(pane.getVerticalScrollBar().isVisible());
      assertEquals(2, outer.size());
    });
  }

  @Test
  void thePanelOfTheChildrenIsAListForAssistiveTools() {
    Edt.run(() -> {
      show("@accessibility", object("label", "Results", "description", "Three results"));
      JComponent list = fixture.viewOf("root").accessibleComponent();

      assertSame(content("root"), list);
      assertEquals(AccessibleRole.LIST, list.getAccessibleContext().getAccessibleRole());
      assertEquals("Results", list.getAccessibleContext().getAccessibleName());
      assertEquals("Three results", list.getAccessibleContext().getAccessibleDescription());
      assertEquals(3, list.getAccessibleContext().getAccessibleChildrenCount());
    });
  }

  @Test
  void aListWithNoLabelHasNoAccessibleNameOfItsOwn() {
    Edt.run(() -> {
      show();

      assertNull(content("root").getAccessibleContext().getAccessibleName());
    });
  }

  @Test
  void aChildThatDidNotArriveHasNoPositionInTheList() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "List", "children", List.of("a", "late", "c")),
          text("a", "one"),
          text("c", "3"));

      assertEquals(2, content("root").getComponentCount());
      assertFalse(fixture.node("late").occupiesLayout());
      assertEquals(height("a"), fixture.bounds("c").y);

      fixture.update(text("late", "late"));

      assertEquals(3, content("root").getComponentCount());
      assertEquals("one late 3", VisibleText.of(content("root")));
    });
  }

  @Test
  void theDisposalRemovesTheViewsOfTheChildren() {
    Edt.run(() -> {
      showItems(5);
      FlexPanel content = content("root");

      assertEquals(5, content.getComponentCount());

      fixture.view.removeNotify();

      assertEquals(0, content.getComponentCount());
      assertEquals(0, fixture.view.activeSubscriptionCount());
    });
  }
}
