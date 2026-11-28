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
import static com.google.a2ui.swing.testing.TestSurface.path;
import static com.google.a2ui.swing.testing.TestSurface.template;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.catalog.basic.FlexLayout.Align;
import com.google.a2ui.swing.catalog.basic.FlexLayout.Axis;
import com.google.a2ui.swing.catalog.basic.FlexLayout.Justify;
import com.google.a2ui.swing.surface.MountedNode;
import com.google.a2ui.swing.testing.Edt;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;

/**
 * The tests use short texts as children. Each text is one line, and each has the margin of a leaf.
 * The surface is 400 pixels wide and 300 pixels high.
 */
class FlexContainerViewTest {
  private final BasicFixture fixture = new BasicFixture();

  private static Object text(String id, String text, Object... properties) {
    List<Object> all = new ArrayList<>(List.of("text", text));
    all.addAll(List.of(properties));
    return component(id, "Text", all.toArray());
  }

  /** Makes a surface whose root has the three children a, b and c. */
  private void show(String type, Object... properties) {
    List<Object> all = new ArrayList<>(List.of("children", List.of("a", "b", "c")));
    all.addAll(List.of(properties));
    fixture.create(
        Map.of(),
        component("root", type, all.toArray()),
        text("a", "one"),
        text("b", "second"),
        text("c", "3"));
  }

  private FlexPanel panel() {
    return (FlexPanel) fixture.componentOf("root");
  }

  private Rectangle a() {
    return fixture.bounds("a");
  }

  private Rectangle b() {
    return fixture.bounds("b");
  }

  private Rectangle c() {
    return fixture.bounds("c");
  }

  private int width(String id) {
    return fixture.componentOf(id).getPreferredSize().width;
  }

  private int height(String id) {
    return fixture.componentOf(id).getPreferredSize().height;
  }

  private List<String> shownChildren() {
    List<String> ids = new ArrayList<>();
    for (MountedNode child : fixture.node("root").children()) {
      if (child.occupiesLayout()) {
        ids.add(child.node().componentId());
      }
    }
    return ids;
  }

  @Test
  void aColumnPutsEachChildBelowTheChildBeforeItAndFillsTheWidth() {
    Edt.run(() -> {
      show("Column");

      assertEquals(Axis.VERTICAL, panel().flexLayout().axis());
      assertEquals(Justify.START, panel().flexLayout().justify());
      assertEquals(Align.STRETCH, panel().flexLayout().align());
      assertEquals(new Rectangle(0, 0, 400, 300), panel().getBounds());
      assertEquals(new Rectangle(0, 0, 400, height("a")), a());
      assertEquals(new Rectangle(0, height("a"), 400, height("b")), b());
      assertEquals(new Rectangle(0, height("a") + height("b"), 400, height("c")), c());
    });
  }

  @Test
  void aRowPutsTheChildrenSideBySideAndFillsTheHeight() {
    Edt.run(() -> {
      show("Row");

      assertEquals(Axis.HORIZONTAL, panel().flexLayout().axis());
      assertEquals(new Rectangle(0, 0, width("a"), 300), a());
      assertEquals(new Rectangle(width("a"), 0, width("b"), 300), b());
      assertEquals(new Rectangle(width("a") + width("b"), 0, width("c"), 300), c());
    });
  }

  @Test
  void aContainerHasNoPaddingNoGapAndNoBackground() {
    Edt.run(() -> {
      show("Row");

      assertEquals(new Insets(0, 0, 0, 0), panel().getInsets());
      assertEquals(0, panel().flexLayout().gap());
      assertFalse(panel().isOpaque());
      // The space between two texts is the sum of their margins.
      assertEquals(a().x + a().width, b().x);
    });
  }

  @Test
  void aContainerAroundAComponentDoesNotChangeTheSpaceAroundTheComponent() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Column", "children", List.of("plain", "row")),
          text("plain", "same"),
          component("row", "Row", "children", List.of("column")),
          component("column", "Column", "children", List.of("nested")),
          text("nested", "same"));

      Rectangle plain = fixture.bounds("plain");
      Rectangle nested = fixture.bounds("nested");

      assertEquals(plain.x, nested.x);
      assertEquals(plain.height, nested.height);
      assertEquals(plain.y + plain.height, nested.y);
      assertEquals(fixture.bounds("row").y, nested.y);
      assertEquals(fixture.bounds("row").height, nested.height);
      assertEquals(fixture.bounds("column").getLocation(), nested.getLocation());
    });
  }

  @Test
  void justifyOfARowMovesTheFreeSpaceOfTheWidth() {
    Edt.run(() -> {
      show("Row", "justify", "end");
      int used = width("a") + width("b") + width("c");
      int free = 400 - used;

      assertEquals(free, a().x);
      assertEquals(400, c().x + c().width);

      fixture.update(
          component("root", "Row", "children", List.of("a", "b", "c"), "justify", "center"));
      assertEquals(free / 2, a().x, 1);
      assertEquals(a().x + used, c().x + c().width);

      fixture.update(
          component("root", "Row", "children", List.of("a", "b", "c"), "justify", "spaceBetween"));
      assertEquals(0, a().x);
      assertEquals(400, c().x + c().width);
      assertEquals(b().x - (a().x + a().width), c().x - (b().x + b().width), 1);

      fixture.update(
          component("root", "Row", "children", List.of("a", "b", "c"), "justify", "spaceAround"));
      assertEquals(free / 6, a().x, 1);
      assertEquals(free / 3, b().x - (a().x + a().width), 1);

      fixture.update(
          component("root", "Row", "children", List.of("a", "b", "c"), "justify", "spaceEvenly"));
      assertEquals(free / 4, a().x, 1);
      assertEquals(free / 4, b().x - (a().x + a().width), 1);
      assertEquals(free / 4, 400 - (c().x + c().width), 1);

      fixture.update(
          component("root", "Row", "children", List.of("a", "b", "c"), "justify", "stretch"));
      assertEquals(0, a().x);
      assertEquals(width("a") + free / 3, a().width, 1);
      assertEquals(400, c().x + c().width);

      fixture.update(component("root", "Row", "children", List.of("a", "b", "c")));
      assertEquals(0, a().x);
      assertEquals(used, c().x + c().width);
    });
  }

  @Test
  void justifyOfAColumnMovesTheFreeSpaceOfTheHeight() {
    Edt.run(() -> {
      show("Column", "justify", "end");

      assertEquals(300, c().y + c().height);
      assertEquals(300 - height("a") - height("b") - height("c"), a().y);

      fixture.update(
          component(
              "root", "Column", "children", List.of("a", "b", "c"), "justify", "spaceBetween"));

      assertEquals(0, a().y);
      assertEquals(300, c().y + c().height);
    });
  }

  @Test
  void alignOfARowGivesThePositionOnTheVerticalAxis() {
    Edt.run(() -> {
      show("Row", "align", "start");

      assertEquals(new Rectangle(0, 0, width("a"), height("a")), a());

      fixture.update(
          component("root", "Row", "children", List.of("a", "b", "c"), "align", "center"));
      assertEquals((300 - height("a")) / 2, a().y);
      assertEquals(height("a"), a().height);

      fixture.update(component("root", "Row", "children", List.of("a", "b", "c"), "align", "end"));
      assertEquals(300, a().y + a().height);

      fixture.update(
          component("root", "Row", "children", List.of("a", "b", "c"), "align", "stretch"));
      assertEquals(300, a().height);
    });
  }

  @Test
  void alignOfAColumnGivesThePositionOnTheHorizontalAxis() {
    Edt.run(() -> {
      show("Column", "align", "end");

      assertEquals(400 - width("a"), a().x);
      assertEquals(width("a"), a().width);
      assertEquals(400 - width("b"), b().x);

      fixture.update(
          component("root", "Column", "children", List.of("a", "b", "c"), "align", "center"));

      assertEquals((400 - width("a")) / 2, a().x);
    });
  }

  @Test
  void theChildrenWithAWeightShareTheFreeSpaceInProportion() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Row", "children", List.of("a", "b", "c"), "justify", "end"),
          text("a", "one", "weight", 1),
          text("b", "second", "weight", 3),
          text("c", "3"));
      int free = 400 - width("c");

      // The layout can round the share of a child up or down by one pixel.
      assertEquals(new Point(0, 0), a().getLocation());
      assertEquals(300, a().height);
      assertEquals(free / 4.0, a().width, 1);
      assertEquals(free - a().width, b().width);
      assertEquals(400, c().x + c().width);

      // A new weight of a child changes the layout of the container.
      fixture.update(text("a", "one", "weight", 3));

      assertEquals(free / 2, a().width, 1);
      assertEquals(free / 2, b().width, 1);

      fixture.update(text("a", "one"), text("b", "second", "weight", 0));

      assertEquals(width("a"), a().width);
      assertEquals(width("b"), b().width);
      assertEquals(400, c().x + c().width);
    });
  }

  @Test
  void aChildWithAWeightIsNotNarrowerThanItsLongestWord() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Row", "children", List.of("a", "b", "c")),
          text("a", "one", "weight", 2),
          text("b", "two", "weight", 1),
          text("c", "$850,000,000,000.00 total", "weight", 1));
      // One quarter of this width is less than the width of the first word of the last child.
      int word = fixture.componentOf("c").getMinimumSize().width;
      fixture.resize(2 * word, 300);

      assertEquals(word, c().width);
      assertEquals(2 * word, c().x + c().width);
      // The two other children share the space that stays as 2 to 1.
      assertEquals(2.0 * b().width, a().width, 2);
      assertEquals(c().x, b().x + b().width);
    });
  }

  @Test
  void aWeightInAColumnGivesTheFreeHeight() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Column", "children", List.of("a", "b")),
          text("a", "one", "weight", 1),
          text("b", "second"));

      assertEquals(new Rectangle(0, 0, 400, 300 - height("b")), a());
      assertEquals(300, b().y + b().height);
    });
  }

  @Test
  void anUpdateKeepsThePanelAndTheComponentsOfTheChildrenThatStay() {
    Edt.run(() -> {
      show("Column");
      JComponent root = panel();
      JComponent first = fixture.componentOf("a");
      JComponent third = fixture.componentOf("c");

      fixture.update(
          component("root", "Column", "children", List.of("c", "a"), "justify", "center"));

      assertSame(root, panel());
      assertSame(third, fixture.componentOf("c"));
      assertSame(first, fixture.componentOf("a"));
      assertEquals(List.of(third, first), List.of(root.getComponents()));
      assertEquals(List.of("c", "a"), shownChildren());
      assertTrue(c().y < a().y);
      assertEquals(Justify.CENTER, panel().flexLayout().justify());
    });
  }

  @Test
  void aTemplateGivesOneChildForEachItemOfTheData() {
    Edt.run(() -> {
      fixture.create(
          Map.of("items", List.of(Map.of("name", "Alpha"), Map.of("name", "Beta"))),
          component("root", "Row", "children", template("item", "/items")),
          text("item", "x", "text", path("name")));
      JComponent alpha = (JComponent) panel().getComponent(0);

      assertEquals(2, panel().getComponentCount());
      assertEquals("Alpha", VisibleText.of(panel().getComponent(0)));
      assertEquals("Beta", VisibleText.of(panel().getComponent(1)));

      fixture.setData(
          "/items",
          List.of(Map.of("name", "Alpha"), Map.of("name", "Gamma"), Map.of("name", "Beta")));

      assertEquals(3, panel().getComponentCount());
      assertSame(alpha, panel().getComponent(0));
      assertEquals("Alpha Gamma Beta", VisibleText.of(panel()));
      assertEquals(panel().getComponent(0).getWidth(), panel().getComponent(1).getX());

      fixture.setData("/items", List.of(Map.of("name", "Beta")));

      assertEquals(1, panel().getComponentCount());
      assertSame(alpha, panel().getComponent(0));
      assertEquals("Beta", VisibleText.of(panel()));
    });
  }

  @Test
  void aChildThatDidNotArriveHasNoPositionInTheLine() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component(
              "root", "Row", "children", List.of("a", "late", "c"), "justify", "spaceBetween"),
          text("a", "one"),
          text("c", "3"));

      assertEquals(List.of("a", "c"), shownChildren());
      assertEquals(3, fixture.node("root").children().size());
      assertEquals(0, a().x);
      assertEquals(400, c().x + c().width);

      JComponent root = panel();
      fixture.update(text("late", "second"));

      assertSame(root, panel());
      assertEquals(List.of("a", "late", "c"), shownChildren());
      assertTrue(a().x + a().width < fixture.bounds("late").x);
    });
  }

  @Test
  void aChildOfADifferentTypeGetsANewComponentAtTheSamePosition() {
    Edt.run(() -> {
      show("Column");
      JComponent before = fixture.componentOf("b");

      fixture.update(component("b", "Row", "children", List.of()));

      assertNotSame(before, fixture.componentOf("b"));
      assertSame(fixture.componentOf("b"), panel().getComponent(1));
      assertTrue(fixture.componentOf("b") instanceof FlexPanel);
    });
  }

  @Test
  void aLongTextInARowThatIsTooSmallGetsMoreLinesAndTheColumnGivesTheHeight() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Column", "children", List.of("row", "below")),
          component("row", "Row", "children", List.of("label", "long")),
          text("label", "Note"),
          text(
              "long",
              "This sentence has many words. It is much longer than the width of the surface, and"
                  + " thus it cannot be in one line."),
          text("below", "below"));
      int oneLine = height("label");

      Rectangle row = fixture.bounds("row");
      Rectangle longText = fixture.bounds("long");

      assertTrue(width("long") > 400, "the text must not fit in one line");
      assertEquals(400, longText.x + longText.width);
      assertTrue(longText.height >= 2 * oneLine - 16, "the height is " + longText.height);
      assertEquals(longText.height, row.height);
      assertEquals(row.height, fixture.bounds("below").y);
      assertEquals(
          longText.height,
          ((FlexPanel) fixture.componentOf("long")).preferredHeightFor(longText.width));
    });
  }

  @Test
  void theUnmountOfTheSurfaceDisposesTheViewsOfTheChildren() {
    Edt.run(() -> {
      show("Row");
      JComponent root = panel();

      assertEquals(4, fixture.view.activeSubscriptionCount());

      fixture.view.removeNotify();

      assertEquals(0, fixture.view.activeSubscriptionCount());
      assertEquals(0, root.getComponentCount());
    });
  }

  @Test
  void aWeightThatALayoutCannotUseIsNoWeight() {
    Edt.run(() -> {
      fixture.create(
          Map.of("weight", -2),
          component("root", "Row", "children", List.of("a")),
          text("a", "one", "weight", 0));

      assertEquals(null, FlexContainerView.weightOf(fixture.node("a").node()));
      assertEquals(0.0, panel().flexLayout().weightOf(fixture.componentOf("a")));
      assertEquals(width("a"), a().width);
    });
  }
}
