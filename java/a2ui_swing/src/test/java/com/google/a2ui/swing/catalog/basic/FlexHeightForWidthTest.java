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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.catalog.basic.FlexLayout.Align;
import com.google.a2ui.swing.catalog.basic.FlexLayout.Axis;
import com.google.a2ui.swing.catalog.basic.FlexLayout.Justify;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/**
 * The tests use a component that acts as text that wraps. Its area is always 6000 square pixels:
 * its preferred size is 300 x 20, and at a width of 100 its preferred height is 60.
 */
class FlexHeightForWidthTest {
  /** A component whose height depends on its width, as text that wraps. */
  private static final class Wrapping extends JPanel implements HeightForWidth {
    private static final long serialVersionUID = 1L;

    Wrapping() {
      setPreferredSize(new Dimension(300, 20));
      setMinimumSize(new Dimension(50, 20));
    }

    @Override
    public int preferredHeightFor(int width) {
      return width <= 0 ? 6000 : 6000 / width;
    }
  }

  private static JPanel box(int width, int height) {
    JPanel box = new JPanel();
    box.setPreferredSize(new Dimension(width, height));
    box.setMinimumSize(new Dimension(10, 5));
    return box;
  }

  private final Wrapping text = new Wrapping();
  private final JPanel box = box(40, 30);

  @Test
  void aColumnGivesAComponentTheHeightForTheWidthOfTheColumn() {
    FlexPanel column = new FlexPanel(Axis.VERTICAL);
    column.add(text);
    column.add(box);
    column.setSize(100, 400);
    column.doLayout();

    assertEquals(new Rectangle(0, 0, 100, 60), text.getBounds());
    assertEquals(new Rectangle(0, 60, 100, 30), box.getBounds());
    assertEquals(90, column.preferredHeightFor(100));
    assertEquals(50, column.preferredHeightFor(300));
    // With no width, the preferred size is the size of the content with no line breaks.
    assertEquals(new Dimension(300, 50), column.getPreferredSize());
  }

  @Test
  void aColumnThatDoesNotStretchGivesAComponentNoMoreThanItsPreferredWidth() {
    FlexPanel column = new FlexPanel(new FlexLayout(Axis.VERTICAL, Justify.START, Align.END, 0));
    column.add(text);
    column.setSize(600, 400);
    column.doLayout();

    assertEquals(new Rectangle(300, 0, 300, 20), text.getBounds());
    assertEquals(20, column.preferredHeightFor(600));
    assertEquals(40, column.preferredHeightFor(150));
  }

  @Test
  void aRowGivesAComponentTheHeightForTheWidthThatItGets() {
    FlexPanel row = new FlexPanel(new FlexLayout(Axis.HORIZONTAL, Justify.START, Align.START, 0));
    row.add(box);
    row.add(text, 1.0);
    row.setSize(240, 200);
    row.doLayout();

    // The component with the weight gets 200 pixels. Thus its height is 30.
    assertEquals(new Rectangle(0, 0, 40, 30), box.getBounds());
    assertEquals(new Rectangle(40, 0, 200, 30), text.getBounds());
    assertEquals(30, row.preferredHeightFor(240));
    assertEquals(100, row.preferredHeightFor(100));
  }

  @Test
  void aRowThatIsTooSmallMakesTheTextSmallerAndThenHigher() {
    FlexPanel row = new FlexPanel(new FlexLayout(Axis.HORIZONTAL, Justify.START, Align.CENTER, 0));
    row.add(text);
    row.add(box);
    row.setSize(160, 100);
    row.doLayout();

    // The two components need 340 pixels. They give the 180 pixels in proportion to the space
    // between their preferred size and their minimum size: 250 and 30.
    assertEquals(139, text.getWidth());
    assertEquals(21, box.getWidth());
    assertEquals(6000 / 139, text.getHeight());
    assertEquals((100 - 6000 / 139) / 2, text.getY());
  }

  @Test
  void theQuestionGoesThroughEachContainerBelow() {
    FlexPanel inner = new FlexPanel(Axis.VERTICAL);
    inner.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    inner.add(text);
    FlexPanel row = new FlexPanel(Axis.HORIZONTAL);
    row.add(box);
    row.add(inner, 1.0);
    FlexPanel outer = new FlexPanel(Axis.VERTICAL);
    outer.add(row);
    outer.setSize(180, 500);
    outer.validate();
    outer.doLayout();
    row.doLayout();
    inner.doLayout();

    // The inner panel gets 140 pixels, and the text 120 of them. Thus the text is 50 high.
    assertEquals(new Rectangle(0, 0, 180, 60), row.getBounds());
    assertEquals(new Rectangle(40, 0, 140, 60), inner.getBounds());
    assertEquals(new Rectangle(10, 5, 120, 50), text.getBounds());
    assertEquals(60, outer.preferredHeightFor(180));
  }

  @Test
  void aComponentWithAWeightInAColumnGetsTheFreeSpaceAndNotItsPreferredHeight() {
    FlexPanel column = new FlexPanel(Axis.VERTICAL);
    column.add(text, 1.0);
    column.add(box);
    column.setSize(100, 400);
    column.doLayout();

    assertEquals(new Rectangle(0, 0, 100, 370), text.getBounds());
    assertEquals(new Rectangle(0, 370, 100, 30), box.getBounds());
  }

  @Test
  void aPanelWithAPreferredSizeOfTheApplicationKeepsItsHeight() {
    FlexPanel column = new FlexPanel(Axis.VERTICAL);
    column.add(text);
    column.setPreferredSize(new Dimension(80, 44));

    assertEquals(44, column.preferredHeightFor(100));
  }

  @Test
  void aFlexPanelIsNotOpaqueAndTakesItsTextStyleFromTheComponentAboveIt() {
    JPanel parent = new JPanel(new BorderLayout());
    Font font = new Font(Font.SERIF, Font.ITALIC, 19);
    parent.setFont(font);
    parent.setForeground(Color.MAGENTA);
    FlexPanel panel = new FlexPanel(Axis.HORIZONTAL);

    assertFalse(panel.isOpaque());
    assertNull(panel.getFont());
    assertNull(panel.getForeground());

    parent.add(panel);
    panel.updateUI();

    assertSame(font, panel.getFont());
    assertEquals(Color.MAGENTA, panel.getForeground());
    assertFalse(panel.isFontSet());
    assertFalse(panel.isForegroundSet());
  }

  @Test
  void aFlexPanelAcceptsOnlyAFlexLayout() {
    FlexPanel panel = new FlexPanel(Axis.HORIZONTAL);
    FlexLayout layout = new FlexLayout(Axis.VERTICAL);

    panel.setLayout(layout);

    assertSame(layout, panel.flexLayout());
    assertThrows(IllegalArgumentException.class, () -> panel.setLayout(new BorderLayout()));
    assertThrows(IllegalArgumentException.class, () -> panel.setLayout(null));
  }
}
