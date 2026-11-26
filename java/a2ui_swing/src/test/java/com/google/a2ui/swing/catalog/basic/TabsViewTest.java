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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.surface.MountedNode;
import com.google.a2ui.swing.testing.Edt;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.Test;

/** The surface of the tests is 400 pixels wide and 300 pixels high. */
class TabsViewTest {
  private static final String LONG_TEXT =
      "A tab has a text of many words. The text is too long for one line of the tab, and thus it"
          + " needs more lines when the tab is narrow.";

  private final BasicFixture fixture = new BasicFixture();

  private static Map<String, Object> tab(Object title, String child) {
    return object("title", title, "child", child);
  }

  private static Object tabs(Object... tabs) {
    return component("root", "Tabs", "tabs", List.of(tabs));
  }

  private static Object text(String id, String text) {
    return component(id, "Text", "text", text);
  }

  /** Makes a surface whose root has the three tabs One, Two and Three. */
  private void show() {
    fixture.create(
        Map.of("title", "Two"),
        tabs(tab("One", "one"), tab(path("/title"), "two"), tab("Three", "three")),
        text("one", "First content"),
        text("two", "Second content"),
        text("three", "Third content"));
  }

  private JTabbedPane pane() {
    return (JTabbedPane) fixture.viewOf("root").accessibleComponent();
  }

  /** Gets the IDs of the children that the view of the tabs mounts. */
  private List<String> mounted() {
    List<String> ids = new ArrayList<>();
    for (MountedNode child : fixture.node("root").children()) {
      ids.add(child.node().componentId());
    }
    return ids;
  }

  private List<String> titles() {
    List<String> titles = new ArrayList<>();
    for (int i = 0; i < pane().getTabCount(); i++) {
      titles.add(pane().getTitleAt(i));
    }
    return titles;
  }

  @Test
  void theFirstTabIsSelectedAndOnlyItsChildIsMounted() {
    Edt.run(() -> {
      show();

      assertEquals(List.of("One", "Two", "Three"), titles());
      assertEquals(0, pane().getSelectedIndex());
      assertEquals(List.of("one"), mounted());
      assertEquals("First content", VisibleText.of(pane()));
      // The page of the selected tab has the child. The other tabs have no component.
      assertSame(pane().getComponentAt(0), fixture.componentOf("one").getParent());
      assertNull(pane().getComponentAt(1));
      assertNull(pane().getComponentAt(2));
    });
  }

  @Test
  void theSelectionOfATabMountsItsChildAndDisposesTheChildBefore() {
    Edt.run(() -> {
      show();
      JComponent page = (JComponent) pane().getComponentAt(0);
      JComponent first = fixture.componentOf("one");
      int subscriptions = fixture.view.activeSubscriptionCount();

      pane().setSelectedIndex(2);
      fixture.view.validate();

      assertEquals(List.of("three"), mounted());
      assertEquals("Third content", VisibleText.of(pane()));
      assertNull(first.getParent());
      assertNull(pane().getComponentAt(0));
      assertSame(page, pane().getComponentAt(2));
      assertSame(page, fixture.componentOf("three").getParent());
      assertTrue(page.isVisible());
      assertEquals(subscriptions, fixture.view.activeSubscriptionCount());
      // The page has a size, and the child fills it.
      assertTrue(page.getWidth() > 300 && page.getHeight() > 100, page.getBounds().toString());
      assertEquals(new Rectangle(page.getSize()), fixture.componentOf("three").getBounds());
    });
  }

  @Test
  void theTitleOfATabFollowsTheData() {
    Edt.run(() -> {
      show();
      JTabbedPane pane = pane();

      fixture.setData("/title", "Second");

      assertSame(pane, pane());
      assertEquals(List.of("One", "Second", "Three"), titles());
    });
  }

  @Test
  void theSelectionStaysWhenATitleChangesAndWhenOtherTabsChange() {
    Edt.run(() -> {
      show();
      JTabbedPane pane = pane();
      pane.setSelectedIndex(1);
      JComponent second = fixture.componentOf("two");

      fixture.setData("/title", "Second");

      assertEquals(1, pane.getSelectedIndex());
      assertEquals(List.of("two"), mounted());
      assertSame(second, fixture.componentOf("two"));

      fixture.update(
          tabs(tab("First", "one"), tab("Two", "two"), tab("Three", "three"), tab("Four", "one")));

      assertSame(pane, pane());
      assertEquals(List.of("First", "Two", "Three", "Four"), titles());
      assertEquals(1, pane.getSelectedIndex());
      assertEquals(List.of("two"), mounted());
      assertSame(second, fixture.componentOf("two"));

      fixture.update(tabs(tab("One", "one"), tab("Two", "two")));

      assertEquals(List.of("One", "Two"), titles());
      assertEquals(1, pane.getSelectedIndex());
      assertSame(second, fixture.componentOf("two"));
    });
  }

  @Test
  void theLastTabBecomesSelectedIfTheSelectedTabGoesAway() {
    Edt.run(() -> {
      show();
      pane().setSelectedIndex(2);

      fixture.update(tabs(tab("One", "one"), tab("Two", "two")));

      assertEquals(1, pane().getSelectedIndex());
      assertEquals(List.of("two"), mounted());
      assertEquals("Second content", VisibleText.of(pane()));
      assertSame(pane().getComponentAt(1), fixture.componentOf("two").getParent());
    });
  }

  @Test
  void anUpdateOfTheChildOfTheSelectedTabShowsTheNewChild() {
    Edt.run(() -> {
      show();
      JComponent page = (JComponent) pane().getComponentAt(0);

      fixture.update(tabs(tab("One", "three"), tab("Two", "two")));

      assertEquals(List.of("three"), mounted());
      assertSame(page, pane().getComponentAt(0));
      assertEquals(1, page.getComponentCount());
      assertEquals("Third content", VisibleText.of(pane()));
    });
  }

  @Test
  void aTitleThatStartsWithAnHtmlTagShowsAsPlainText() {
    Edt.run(() -> {
      fixture.create(Map.of(), tabs(tab("<html><b>Bold</b>", "one")), text("one", "First content"));

      assertEquals("<html><b>Bold</b>", pane().getTitleAt(0).strip());
      assertFalse(BasicHTML.isHTMLString(pane().getTitleAt(0)));
      assertEquals("plain", TabsView.plainTitle("plain"));
      assertEquals(" <HTML>x", TabsView.plainTitle("<HTML>x"));
    });
  }

  @Test
  void theViewHasTheMarginOfAControlAndThePaneFillsIt() {
    Edt.run(() -> {
      show();
      int margin = BasicStyle.MARGIN;

      assertEquals(new Rectangle(0, 0, 400, 300), fixture.bounds("root"));
      assertEquals(
          new Insets(margin, margin, margin, margin), fixture.componentOf("root").getInsets());
      assertEquals(
          new Rectangle(margin, margin, 400 - 2 * margin, 300 - 2 * margin), pane().getBounds());
      // The child is below the headers, in the pane.
      Rectangle child =
          SwingUtilities.convertRectangle(
              fixture.componentOf("one").getParent(),
              fixture.componentOf("one").getBounds(),
              pane());
      assertTrue(child.y > 10, child.toString());
      assertTrue(new Rectangle(pane().getSize()).contains(child), child.toString());
    });
  }

  @Test
  void theTextOfANarrowTabGetsMoreLines() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Column", "children", List.of("tabs", "below")),
          component("tabs", "Tabs", "tabs", List.of(tab("One", "one"), tab("Two", "two"))),
          text("one", LONG_TEXT),
          text("two", "Short"),
          text("below", "Below"));
      fixture.resize(400, 1000);
      HeightForWidth tabs = (HeightForWidth) fixture.componentOf("tabs");
      int wide = fixture.bounds("tabs").height;

      assertEquals(tabs.preferredHeightFor(400), wide);
      assertTrue(tabs.preferredHeightFor(200) > wide, "a narrow tab is higher");

      fixture.resize(200, 1000);

      assertEquals(tabs.preferredHeightFor(200), fixture.bounds("tabs").height);
      assertEquals(fixture.bounds("tabs").height, fixture.bounds("below").y);
      // The text has the height that it needs at its width.
      JComponent text = fixture.componentOf("one");
      assertEquals(((HeightForWidth) text).preferredHeightFor(text.getWidth()), text.getHeight());
      assertTrue(text.getHeight() > text.getPreferredSize().height);
    });
  }

  @Test
  void theHeightOfTheViewFollowsTheChildOfTheSelectedTab() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Column", "children", List.of("tabs", "below")),
          component("tabs", "Tabs", "tabs", List.of(tab("One", "one"), tab("Two", "two"))),
          text("one", "Short"),
          text("two", "# A heading\n\nA paragraph\n\nOne more paragraph"),
          text("below", "Below"));
      fixture.resize(400, 1000);
      int first = fixture.bounds("tabs").height;
      JTabbedPane pane = (JTabbedPane) fixture.viewOf("tabs").accessibleComponent();

      pane.setSelectedIndex(1);
      fixture.view.validate();

      assertTrue(fixture.bounds("tabs").height > first, "the second tab is higher");
      assertEquals(fixture.bounds("tabs").height, fixture.bounds("below").y);
    });
  }

  @Test
  void aTabWhoseChildDidNotArriveIsEmptyAndGetsTheChildLater() {
    Edt.run(() -> {
      fixture.create(Map.of(), tabs(tab("One", "late"), tab("Two", "two")), text("two", "Second"));
      JTabbedPane pane = pane();

      assertEquals(List.of("late"), mounted());
      assertFalse(fixture.node("late").occupiesLayout());
      assertEquals("", VisibleText.of(pane));

      fixture.update(text("late", "Late"));

      assertSame(pane, pane());
      assertEquals("Late", VisibleText.of(pane));
    });
  }

  @Test
  void thePaneIsAPageTabListForAssistiveTools() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component(
              "root",
              "Tabs",
              "tabs",
              List.of(tab("One", "one"), tab("Two", "two")),
              "@accessibility",
              object("label", "Sections", "description", "The sections of the report")),
          text("one", "First content"),
          text("two", "Second content"));
      AccessibleContext context = pane().getAccessibleContext();

      assertEquals(AccessibleRole.PAGE_TAB_LIST, context.getAccessibleRole());
      assertEquals("Sections", context.getAccessibleName());
      assertEquals("The sections of the report", context.getAccessibleDescription());
      assertEquals(2, context.getAccessibleChildrenCount());
      assertEquals("Two", context.getAccessibleChild(1).getAccessibleContext().getAccessibleName());
      assertEquals(
          AccessibleRole.PAGE_TAB,
          context.getAccessibleChild(1).getAccessibleContext().getAccessibleRole());
      assertTrue(pane().isFocusable());
    });
  }

  @Test
  void theDisposalRemovesTheListenerOfThePaneAndTheViewOfTheChild() {
    Edt.run(() -> {
      show();
      JTabbedPane pane = pane();
      JComponent page = (JComponent) pane.getComponentAt(0);
      int listeners = pane.getChangeListeners().length;

      fixture.view.removeNotify();

      assertEquals(listeners - 1, pane.getChangeListeners().length);
      assertEquals(0, page.getComponentCount());
      assertEquals(0, fixture.view.activeSubscriptionCount());
      // A selection after the disposal mounts no view.
      pane.setSelectedIndex(1);
      assertEquals(0, page.getComponentCount());
      assertEquals(0, fixture.view.activeSubscriptionCount());
    });
  }
}
