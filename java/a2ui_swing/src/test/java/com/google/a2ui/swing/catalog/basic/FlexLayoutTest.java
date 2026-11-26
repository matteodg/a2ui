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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.catalog.basic.FlexLayout.Align;
import com.google.a2ui.swing.catalog.basic.FlexLayout.Axis;
import com.google.a2ui.swing.catalog.basic.FlexLayout.Justify;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/**
 * The tests use three components. Their preferred sizes are 50 x 20, 70 x 30 and 30 x 10. Thus
 * their sum on the main axis is 150 for a row, and 60 for a column.
 */
class FlexLayoutTest {
  private final JPanel a = box(50, 20);
  private final JPanel b = box(70, 30);
  private final JPanel c = box(30, 10);

  /** Makes a component with a preferred size and with a minimum size of 10 x 5. */
  private static JPanel box(int width, int height) {
    JPanel box = new JPanel();
    box.setPreferredSize(new Dimension(width, height));
    box.setMinimumSize(new Dimension(10, 5));
    return box;
  }

  /** Makes a container of the three components and does its layout at a size. */
  private JPanel layout(FlexLayout layout, int width, int height) {
    JPanel panel = new JPanel(layout);
    panel.add(a);
    panel.add(b);
    panel.add(c);
    panel.setSize(width, height);
    panel.doLayout();
    return panel;
  }

  private JPanel row(Justify justify, Align align, int gap) {
    return layout(new FlexLayout(Axis.HORIZONTAL, justify, align, gap), 300, 100);
  }

  private void assertBounds(Rectangle forA, Rectangle forB, Rectangle forC) {
    assertEquals(forA, a.getBounds(), "a");
    assertEquals(forB, b.getBounds(), "b");
    assertEquals(forC, c.getBounds(), "c");
  }

  /** Makes sure of the position and the size of each component on the horizontal axis. */
  private void assertColumns(
      int xOfA, int widthOfA, int xOfB, int widthOfB, int xOfC, int widthOfC) {
    assertEquals(xOfA, a.getX(), "x of a");
    assertEquals(widthOfA, a.getWidth(), "width of a");
    assertEquals(xOfB, b.getX(), "x of b");
    assertEquals(widthOfB, b.getWidth(), "width of b");
    assertEquals(xOfC, c.getX(), "x of c");
    assertEquals(widthOfC, c.getWidth(), "width of c");
  }

  @Test
  void theDefaultRowPutsTheComponentsAtTheStartAndFillsTheCrossAxis() {
    layout(new FlexLayout(Axis.HORIZONTAL), 300, 100);

    assertBounds(
        new Rectangle(0, 0, 50, 100),
        new Rectangle(50, 0, 70, 100),
        new Rectangle(120, 0, 30, 100));
  }

  @Test
  void theDefaultColumnPutsTheComponentsAtTheStartAndFillsTheCrossAxis() {
    layout(new FlexLayout(Axis.VERTICAL), 100, 300);

    assertBounds(
        new Rectangle(0, 0, 100, 20), new Rectangle(0, 20, 100, 30), new Rectangle(0, 50, 100, 10));
  }

  @Test
  void theGapIsBetweenTwoComponentsOnly() {
    row(Justify.START, Align.STRETCH, 10);

    assertColumns(0, 50, 60, 70, 140, 30);
  }

  @Test
  void justifyCenterPutsHalfOfTheFreeSpaceBeforeTheComponents() {
    row(Justify.CENTER, Align.STRETCH, 0);

    assertColumns(75, 50, 125, 70, 195, 30);
  }

  @Test
  void justifyEndPutsTheFreeSpaceBeforeTheComponents() {
    row(Justify.END, Align.STRETCH, 0);

    assertColumns(150, 50, 200, 70, 270, 30);
  }

  @Test
  void justifySpaceBetweenPutsTheFreeSpaceBetweenTheComponents() {
    row(Justify.SPACE_BETWEEN, Align.STRETCH, 0);

    assertColumns(0, 50, 125, 70, 270, 30);
  }

  @Test
  void justifySpaceBetweenAddsTheFreeSpaceToTheGap() {
    row(Justify.SPACE_BETWEEN, Align.STRETCH, 10);

    // The free space is 300 - 150 - 20 = 130. Each of the two spaces is 10 + 65.
    assertColumns(0, 50, 125, 70, 270, 30);
  }

  @Test
  void justifySpaceBetweenWithOneComponentPutsItAtTheStart() {
    JPanel panel =
        new JPanel(new FlexLayout(Axis.HORIZONTAL, Justify.SPACE_BETWEEN, Align.START, 0));
    panel.add(a);
    panel.setSize(300, 100);
    panel.doLayout();

    assertEquals(new Rectangle(0, 0, 50, 20), a.getBounds());
  }

  @Test
  void justifySpaceAroundPutsEqualSpaceOnTheTwoSidesOfEachComponent() {
    row(Justify.SPACE_AROUND, Align.STRETCH, 0);

    // The free space is 150. Each component has 25 on each side.
    assertColumns(25, 50, 125, 70, 245, 30);
  }

  @Test
  void justifySpaceEvenlyMakesAllSpacesEqual() {
    row(Justify.SPACE_EVENLY, Align.STRETCH, 0);

    // The free space is 150. Each of the four spaces is 37.5, and the layout rounds each position.
    assertColumns(38, 50, 125, 70, 233, 30);
  }

  @Test
  void justifyStretchGivesTheFreeSpaceToTheComponentsInEqualParts() {
    row(Justify.STRETCH, Align.STRETCH, 0);

    assertColumns(0, 100, 100, 120, 220, 80);
  }

  @Test
  void justifyStretchGivesEachPixelOfASpaceThatIsNotAMultiple() {
    layout(new FlexLayout(Axis.HORIZONTAL, Justify.STRETCH, Align.STRETCH, 0), 301, 100);

    // The free space is 151. The parts are 50, 51 and 50.
    assertColumns(0, 100, 100, 121, 221, 80);
    assertEquals(301, c.getX() + c.getWidth());
  }

  @Test
  void alignStartKeepsThePreferredSizeAtTheStartOfTheCrossAxis() {
    row(Justify.START, Align.START, 0);

    assertBounds(
        new Rectangle(0, 0, 50, 20), new Rectangle(50, 0, 70, 30), new Rectangle(120, 0, 30, 10));
  }

  @Test
  void alignCenterKeepsThePreferredSizeInTheCenterOfTheCrossAxis() {
    row(Justify.START, Align.CENTER, 0);

    assertBounds(
        new Rectangle(0, 40, 50, 20),
        new Rectangle(50, 35, 70, 30),
        new Rectangle(120, 45, 30, 10));
  }

  @Test
  void alignEndKeepsThePreferredSizeAtTheEndOfTheCrossAxis() {
    row(Justify.START, Align.END, 0);

    assertBounds(
        new Rectangle(0, 80, 50, 20),
        new Rectangle(50, 70, 70, 30),
        new Rectangle(120, 90, 30, 10));
  }

  @Test
  void alignOfAColumnIsOnTheHorizontalAxis() {
    layout(new FlexLayout(Axis.VERTICAL, Justify.END, Align.END, 5), 100, 300);

    // The free space is 300 - 60 - 10 = 230.
    assertBounds(
        new Rectangle(50, 230, 50, 20),
        new Rectangle(30, 255, 70, 30),
        new Rectangle(70, 290, 30, 10));
  }

  @Test
  void aComponentWithTheStretchPropertyFillsTheCrossAxisWithEachAlign() {
    b.putClientProperty(FlexLayout.STRETCH_PROPERTY, Boolean.TRUE);

    row(Justify.START, Align.CENTER, 0);

    assertBounds(
        new Rectangle(0, 40, 50, 20),
        new Rectangle(50, 0, 70, 100),
        new Rectangle(120, 45, 30, 10));

    FlexLayout column = new FlexLayout(Axis.VERTICAL, Justify.START, Align.END, 0);
    JPanel panel = layout(column, 100, 300);

    assertBounds(
        new Rectangle(50, 0, 50, 20), new Rectangle(0, 20, 100, 30), new Rectangle(70, 50, 30, 10));
    // The property does not change the preferred size of the container.
    assertEquals(new Dimension(70, 60), column.preferredLayoutSize(panel));
    assertEquals(60, column.preferredHeight(panel, 100));
  }

  @Test
  void aComponentThatIsLargerThanTheCrossAxisGetsTheCrossAxis() {
    layout(new FlexLayout(Axis.HORIZONTAL, Justify.START, Align.CENTER, 0), 300, 24);

    assertEquals(new Rectangle(50, 0, 70, 24), b.getBounds());
    assertEquals(new Rectangle(0, 2, 50, 20), a.getBounds());
  }

  @Test
  void aComponentWithAWeightGetsTheSpaceThatTheOthersDoNotUse() {
    JPanel panel = new JPanel(new FlexLayout(Axis.HORIZONTAL, Justify.CENTER, Align.STRETCH, 0));
    panel.add(a);
    panel.add(b, 1.0);
    panel.add(c);
    panel.setSize(300, 100);
    panel.doLayout();

    assertColumns(0, 50, 50, 220, 270, 30);
  }

  @Test
  void twoComponentsWithWeightsShareTheSpaceInProportion() {
    JPanel panel = new JPanel(new FlexLayout(Axis.HORIZONTAL, Justify.START, Align.STRETCH, 10));
    panel.add(a, 1.0);
    panel.add(b);
    panel.add(c, 3.0);
    panel.setSize(300, 100);
    panel.doLayout();

    // The space for the weights is 300 - 70 - 20 = 210. The parts are 52.5 and 157.5. The layout
    // rounds the first part up and gives the other pixels to the second part.
    assertColumns(0, 53, 63, 70, 143, 157);
  }

  @Test
  void theWeightsOfAColumnShareTheHeight() {
    JPanel panel = new JPanel(new FlexLayout(Axis.VERTICAL));
    panel.add(a, 1.0);
    panel.add(b, 1.0);
    panel.add(c);
    panel.setSize(100, 210);
    panel.doLayout();

    assertBounds(
        new Rectangle(0, 0, 100, 100),
        new Rectangle(0, 100, 100, 100),
        new Rectangle(0, 200, 100, 10));
  }

  @Test
  void aChangedWeightAppliesAtTheSubsequentLayout() {
    FlexLayout layout = new FlexLayout(Axis.HORIZONTAL);
    JPanel panel = layout(layout, 300, 100);

    layout.addLayoutComponent(a, 2.0);
    panel.doLayout();

    assertEquals(2.0, layout.weightOf(a));
    assertColumns(0, 200, 200, 70, 270, 30);

    layout.addLayoutComponent(a, null);
    panel.doLayout();

    assertEquals(0.0, layout.weightOf(a));
    assertColumns(0, 50, 50, 70, 120, 30);
  }

  @Test
  void aRemovedComponentLosesItsWeight() {
    FlexLayout layout = new FlexLayout(Axis.HORIZONTAL);
    JPanel panel = new JPanel(layout);
    panel.add(a, 2.0);

    panel.remove(a);

    assertEquals(0.0, layout.weightOf(a));
  }

  @Test
  void theConstraintMustBeAWeight() {
    FlexLayout layout = new FlexLayout(Axis.HORIZONTAL);
    JPanel panel = new JPanel(layout);

    assertThrows(IllegalArgumentException.class, () -> panel.add(a, "North"));
    assertThrows(IllegalArgumentException.class, () -> panel.add(a, -1.0));
    assertThrows(IllegalArgumentException.class, () -> panel.add(a, Double.NaN));
    panel.add(b, 0.0);
    assertEquals(0.0, layout.weightOf(b));
  }

  @Test
  void theInsetsOfTheContainerAreOutsideTheLayout() {
    JPanel panel = new JPanel(new FlexLayout(Axis.HORIZONTAL, Justify.END, Align.STRETCH, 0));
    panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 15, 20));
    panel.add(a);
    panel.add(b);
    panel.setSize(300, 100);
    panel.doLayout();

    // The inner space is 270 x 80. The free space is 270 - 120 = 150.
    assertEquals(new Rectangle(160, 5, 50, 80), a.getBounds());
    assertEquals(new Rectangle(210, 5, 70, 80), b.getBounds());
  }

  @Test
  void aComponentThatIsNotVisibleGetsNoSpaceAndNoGap() {
    b.setVisible(false);

    row(Justify.SPACE_BETWEEN, Align.STRETCH, 10);

    assertEquals(new Rectangle(0, 0, 50, 100), a.getBounds());
    assertEquals(new Rectangle(270, 0, 30, 100), c.getBounds());
  }

  @Test
  void aContainerThatIsTooSmallMakesTheComponentsSmallerInProportion() {
    layout(new FlexLayout(Axis.HORIZONTAL, Justify.CENTER, Align.STRETCH, 0), 100, 100);

    // 50 pixels are missing. The components can give 40, 60 and 20. Thus they give 17, 25 and 8.
    assertColumns(0, 33, 33, 45, 78, 22);
  }

  @Test
  void aComponentIsNotSmallerThanItsMinimumSize() {
    layout(new FlexLayout(Axis.HORIZONTAL), 20, 100);

    assertColumns(0, 10, 10, 10, 20, 10);
  }

  @Test
  void aComponentWithAWeightKeepsItsMinimumSizeInAContainerThatIsTooSmall() {
    JPanel panel = new JPanel(new FlexLayout(Axis.HORIZONTAL));
    panel.add(a);
    panel.add(b, 1.0);
    panel.add(c);
    panel.setSize(60, 100);
    panel.doLayout();

    // The component with a weight has its minimum width of 10. Then 30 pixels are missing. The
    // two components without a weight can give 40 and 20.
    assertColumns(0, 30, 30, 10, 40, 20);
  }

  @Test
  void aComponentWithAWeightIsNotSmallerThanItsMinimumSize() {
    b.setMinimumSize(new Dimension(60, 5));
    JPanel panel = new JPanel(new FlexLayout(Axis.HORIZONTAL));
    panel.add(a, 1.0);
    panel.add(b, 1.0);
    panel.add(c);
    panel.setSize(120, 100);
    panel.doLayout();

    // The two components with a weight have 90 pixels. One half is less than the minimum width
    // of the second component. Thus the second gets 60 and the first gets the 30 that stay.
    assertColumns(0, 30, 30, 60, 90, 30);
  }

  @Test
  void theComponentsWithAWeightShareTheSpaceThatAMinimumSizeDoesNotUse() {
    a.setPreferredSize(new Dimension(120, 20));
    a.setMinimumSize(new Dimension(100, 5));
    JPanel panel = new JPanel(new FlexLayout(Axis.HORIZONTAL));
    panel.add(a, 1.0);
    panel.add(b, 1.0);
    panel.add(c, 3.0);
    panel.setSize(300, 100);
    panel.doLayout();

    // One fifth of 300 is less than 100. The two other components share 200 as 1 to 3.
    assertColumns(0, 100, 100, 50, 150, 150);
  }

  @Test
  void aMinimumSizeHasNoEffectOnAWeightThatGivesMoreSpace() {
    a.setPreferredSize(new Dimension(120, 20));
    a.setMinimumSize(new Dimension(100, 5));
    JPanel panel = new JPanel(new FlexLayout(Axis.HORIZONTAL));
    panel.add(a, 2.0);
    panel.add(b, 1.0);
    panel.add(c, 1.0);
    panel.setSize(300, 100);
    panel.doLayout();

    assertColumns(0, 150, 150, 75, 225, 75);
  }

  @Test
  void aComponentWithAWeightInAColumnIsNotSmallerThanItsMinimumSize() {
    a.setPreferredSize(new Dimension(50, 50));
    a.setMinimumSize(new Dimension(10, 40));
    JPanel panel = new JPanel(new FlexLayout(Axis.VERTICAL));
    panel.add(a, 1.0);
    panel.add(b, 1.0);
    panel.setSize(100, 60);
    panel.doLayout();

    assertEquals(new Rectangle(0, 0, 100, 40), a.getBounds());
    assertEquals(new Rectangle(0, 40, 100, 20), b.getBounds());
  }

  @Test
  void theComponentsWithAWeightShareAContainerThatIsTooSmallForTheirMinimumSizes() {
    a.setMinimumSize(new Dimension(40, 5));
    b.setMinimumSize(new Dimension(20, 5));
    JPanel panel = new JPanel(new FlexLayout(Axis.HORIZONTAL));
    panel.add(a, 1.0);
    panel.add(b, 3.0);
    panel.add(c);
    panel.setSize(40, 100);
    panel.doLayout();

    // The component without a weight has its minimum width of 10. The 30 pixels that stay are
    // less than the minimum widths of 40 and 20. The two components share them as 1 to 3.
    assertColumns(0, 8, 8, 22, 30, 10);
  }

  @Test
  void thePreferredHeightOfARowUsesTheWidthsThatTheLayoutGives() {
    b.setMinimumSize(new Dimension(60, 5));
    FlexLayout layout = new FlexLayout(Axis.HORIZONTAL);
    JPanel panel = new JPanel(layout);
    panel.add(a, 1.0);
    panel.add(b, 1.0);
    HeightAsWidth tall = new HeightAsWidth();
    panel.add(tall, 1.0);

    // The three components share 120 pixels. The second component keeps 60, and the two other
    // components get 30 each.
    assertEquals(30, layout.preferredHeight(panel, 120));
  }

  /** A component whose preferred height at a width is that width. */
  private static final class HeightAsWidth extends JPanel implements HeightForWidth {
    private static final long serialVersionUID = 1L;

    HeightAsWidth() {
      setMinimumSize(new Dimension(0, 0));
      setPreferredSize(new Dimension(10, 10));
    }

    @Override
    public int preferredHeightFor(int width) {
      return width;
    }
  }

  @Test
  void aRowStartsAtTheRightSideInARightToLeftContainer() {
    JPanel panel = new JPanel(new FlexLayout(Axis.HORIZONTAL, Justify.START, Align.STRETCH, 10));
    panel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
    panel.add(a);
    panel.add(b);
    panel.add(c);
    panel.setSize(300, 100);
    panel.doLayout();

    assertColumns(250, 50, 170, 70, 130, 30);
  }

  @Test
  void thePreferredSizeIsTheSumOnTheMainAxisAndTheLargestOnTheCrossAxis() {
    JPanel row = layout(new FlexLayout(Axis.HORIZONTAL, Justify.START, Align.START, 10), 0, 0);
    row.setBorder(BorderFactory.createEmptyBorder(1, 2, 3, 4));

    assertEquals(new Dimension(150 + 20 + 6, 30 + 4), row.getPreferredSize());
    assertEquals(new Dimension(30 + 20 + 6, 5 + 4), row.getMinimumSize());
    assertEquals(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE), row.getMaximumSize());
  }

  @Test
  void thePreferredSizeOfAColumnHasTheMainAxisOnTheHeight() {
    JPanel column = layout(new FlexLayout(Axis.VERTICAL, Justify.START, Align.START, 4), 0, 0);

    assertEquals(new Dimension(70, 60 + 8), column.getPreferredSize());
  }

  @Test
  void anEmptyContainerHasTheSizeOfItsInsets() {
    JPanel panel = new JPanel(new FlexLayout(Axis.HORIZONTAL, Justify.CENTER, Align.CENTER, 10));
    panel.setBorder(BorderFactory.createEmptyBorder(1, 2, 3, 4));
    panel.setSize(100, 100);
    panel.doLayout();

    assertEquals(new Dimension(6, 4), panel.getPreferredSize());
  }

  @Test
  void theEnumsReadTheTextOfTheBasicCatalog() {
    assertEquals(Optional.of(Justify.SPACE_BETWEEN), Justify.fromWire("spaceBetween"));
    assertEquals(Optional.of(Justify.SPACE_AROUND), Justify.fromWire("spaceAround"));
    assertEquals(Optional.of(Justify.SPACE_EVENLY), Justify.fromWire("spaceEvenly"));
    assertEquals(Optional.of(Justify.STRETCH), Justify.fromWire("stretch"));
    assertEquals(Optional.of(Align.CENTER), Align.fromWire("center"));
    assertEquals(Optional.of(Axis.HORIZONTAL), Axis.fromWire("horizontal"));
    assertEquals(Optional.empty(), Justify.fromWire("baseline"));
    assertEquals(Optional.empty(), Align.fromWire(null));
    assertEquals(Optional.empty(), Axis.fromWire(3));
    assertEquals("spaceBetween", Justify.SPACE_BETWEEN.wireValue());
  }

  @Test
  void theGapCannotBeLessThanZero() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new FlexLayout(Axis.HORIZONTAL, Justify.START, Align.START, -1));
  }
}
