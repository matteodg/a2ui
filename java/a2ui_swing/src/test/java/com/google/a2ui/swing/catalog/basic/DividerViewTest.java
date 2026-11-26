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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.testing.Edt;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import javax.accessibility.AccessibleRole;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** The surface of the tests is 400 pixels wide and 300 pixels high. */
class DividerViewTest {
  private static final int MARGIN = BasicStyle.MARGIN;

  private final BasicFixture fixture = new BasicFixture();

  /** Makes a surface whose root has a text, a divider and a text. */
  private void show(String container, Object... dividerProperties) {
    fixture.create(
        Map.of(),
        component("root", "Column", "children", List.of("box")),
        component("box", container, "children", List.of("before", "line", "after")),
        component("before", "Text", "text", "Before"),
        component("line", "Divider", dividerProperties),
        component("after", "Text", "text", "After"));
  }

  private JSeparator line() {
    return (JSeparator) fixture.viewOf("line").accessibleComponent();
  }

  /** Gets the bounds of the line in the coordinates of the surface component. */
  private Rectangle lineBounds() {
    return SwingUtilities.convertRectangle(line().getParent(), line().getBounds(), fixture.view);
  }

  @Test
  void theDefaultIsAHorizontalLineWithTheWidthOfItsColumn() {
    Edt.run(() -> {
      show("Column");
      int thickness = line().getPreferredSize().height;

      assertEquals(SwingConstants.HORIZONTAL, line().getOrientation());
      assertTrue(thickness >= 1, "the line has a thickness");
      assertEquals(0, fixture.bounds("line").x);
      assertEquals(400, fixture.bounds("line").width);
      assertEquals(thickness + 2 * MARGIN, fixture.bounds("line").height);
      // The line has the margin of a leaf on each side.
      assertEquals(
          new Rectangle(MARGIN, fixture.bounds("line").y + MARGIN, 400 - 2 * MARGIN, thickness),
          lineBounds());
      // The divider is between the two texts.
      assertEquals(fixture.bounds("before").height, fixture.bounds("line").y);
      assertEquals(
          fixture.bounds("line").y + fixture.bounds("line").height, fixture.bounds("after").y);
    });
  }

  @Test
  void aVerticalLineHasTheHeightOfItsRow() {
    Edt.run(() -> {
      show("Row", "axis", "vertical");
      int thickness = line().getPreferredSize().width;
      Rectangle row = fixture.bounds("box");

      assertEquals(SwingConstants.VERTICAL, line().getOrientation());
      assertTrue(thickness >= 1, "the line has a thickness");
      assertTrue(row.height > 2 * MARGIN, "the row has a height");
      assertEquals(row.height, fixture.bounds("line").height);
      assertEquals(thickness + 2 * MARGIN, fixture.bounds("line").width);
      assertEquals(
          new Rectangle(
              fixture.bounds("line").x + MARGIN,
              row.y + MARGIN,
              thickness,
              row.height - 2 * MARGIN),
          lineBounds());
      // The divider is between the two texts.
      assertEquals(fixture.bounds("before").width, fixture.bounds("line").x);
      assertEquals(
          fixture.bounds("line").x + fixture.bounds("line").width, fixture.bounds("after").x);
    });
  }

  @Test
  void aLineFillsItsContainerWithEachAlignOfTheContainer() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Column", "children", List.of("row", "column")),
          component("row", "Row", "children", List.of("tall", "upright"), "align", "center"),
          component("tall", "Text", "text", "# A heading\n\nA paragraph"),
          component("upright", "Divider", "axis", "vertical"),
          component("column", "Column", "children", List.of("flat", "short"), "align", "center"),
          component("flat", "Divider"),
          component("short", "Text", "text", "Short"));

      assertTrue(fixture.bounds("row").height > DividerView.LENGTH + 2 * MARGIN);
      assertEquals(fixture.bounds("row").height, fixture.bounds("upright").height);
      assertEquals(fixture.bounds("row").y, fixture.bounds("upright").y);
      assertEquals(400, fixture.bounds("flat").width);
      assertEquals(0, fixture.bounds("flat").x);
      // The text of the same column has its preferred width, in the center.
      assertTrue(fixture.bounds("short").width < 400);
      assertTrue(fixture.bounds("short").x > 0);
    });
  }

  @Test
  void aLineHasItsPreferredLengthIfTheContainerGivesNoMoreSpace() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Column", "children", List.of("row")),
          component("row", "Row", "children", List.of("upright", "flat")),
          component("upright", "Divider", "axis", "vertical"),
          component("flat", "Divider"));

      // No other component makes the row higher, and a row gives each child its preferred width.
      assertEquals(DividerView.LENGTH + 2 * MARGIN, fixture.bounds("upright").height);
      assertEquals(DividerView.LENGTH + 2 * MARGIN, fixture.bounds("flat").width);
      assertTrue(fixture.bounds("upright").width > 2 * MARGIN);
    });
  }

  @Test
  void aHorizontalLineInAHighSpaceIsInTheCenter() {
    Edt.run(() -> {
      show("Row");
      int thickness = line().getPreferredSize().height;
      Rectangle leaf = fixture.bounds("line");

      assertEquals(thickness, lineBounds().height);
      assertEquals(leaf.y + (leaf.height - thickness) / 2, lineBounds().y);
      assertEquals(DividerView.LENGTH, lineBounds().width);
    });
  }

  @Test
  void anUpdateOfTheAxisKeepsTheComponentAndTurnsTheLine() {
    Edt.run(() -> {
      show("Row", "axis", "vertical");
      JSeparator line = line();
      Object panel = fixture.componentOf("line");

      assertEquals(SwingConstants.VERTICAL, line.getOrientation());

      fixture.update(component("line", "Divider", "axis", "horizontal"));

      assertSame(line, line());
      assertSame(panel, fixture.componentOf("line"));
      assertEquals(SwingConstants.HORIZONTAL, line.getOrientation());
      assertEquals(line.getPreferredSize().height, lineBounds().height);

      fixture.update(component("line", "Divider"));

      assertSame(line, line());
      assertEquals(SwingConstants.HORIZONTAL, line.getOrientation());
    });
  }

  @Test
  void theLineIsASeparatorForAssistiveTools() {
    Edt.run(() -> {
      show("Column", "@accessibility", object("label", "End of the summary"));

      assertEquals(AccessibleRole.SEPARATOR, line().getAccessibleContext().getAccessibleRole());
      assertEquals("End of the summary", line().getAccessibleContext().getAccessibleName());
      assertFalse(line().isFocusable());
    });
  }

  @Test
  void aLineWithNoLabelHasNoAccessibleName() {
    Edt.run(() -> {
      show("Column");

      assertNull(line().getAccessibleContext().getAccessibleName());
    });
  }

  @Test
  void theDisposalLeavesNoSubscription() {
    Edt.run(() -> {
      show("Column");

      assertTrue(fixture.view.activeSubscriptionCount() > 0);

      fixture.view.removeNotify();

      assertEquals(0, fixture.view.activeSubscriptionCount());
    });
  }
}
