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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SvgPathTest {
  private static final double DELTA = 1e-6;

  private static Path2D path(String data) {
    return SvgPath.parse(data).orElseThrow(() -> new AssertionError("No path for: " + data));
  }

  /** Writes the segments of a path as text, with each coordinate rounded to 3 decimal places. */
  private static List<String> segments(String data) {
    List<String> segments = new ArrayList<>();
    double[] c = new double[6];
    for (PathIterator i = path(data).getPathIterator(null); !i.isDone(); i.next()) {
      switch (i.currentSegment(c)) {
        case PathIterator.SEG_MOVETO -> segments.add("M " + numbers(c, 2));
        case PathIterator.SEG_LINETO -> segments.add("L " + numbers(c, 2));
        case PathIterator.SEG_QUADTO -> segments.add("Q " + numbers(c, 4));
        case PathIterator.SEG_CUBICTO -> segments.add("C " + numbers(c, 6));
        default -> segments.add("Z");
      }
    }
    return segments;
  }

  private static String numbers(double[] values, int count) {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < count; i++) {
      double rounded = Math.round(values[i] * 1000) / 1000.0;
      text.append(i == 0 ? "" : " ").append(rounded == 0 ? 0.0 : rounded);
    }
    return text.toString();
  }

  private static void assertBounds(double x, double y, double width, double height, String data) {
    Rectangle2D bounds = path(data).getBounds2D();
    assertEquals(x, bounds.getX(), 1e-3, data);
    assertEquals(y, bounds.getY(), 1e-3, data);
    assertEquals(width, bounds.getWidth(), 1e-3, data);
    assertEquals(height, bounds.getHeight(), 1e-3, data);
  }

  /** Makes sure that each point of the flat form of a path is on a circle. */
  private static void assertOnCircle(double centerX, double centerY, double radius, String data) {
    double[] c = new double[6];
    int points = 0;
    for (PathIterator i = path(data).getPathIterator(null, 0.01); !i.isDone(); i.next()) {
      if (i.currentSegment(c) != PathIterator.SEG_CLOSE) {
        assertEquals(radius, Math.hypot(c[0] - centerX, c[1] - centerY), 0.02, data);
        points++;
      }
    }
    assertTrue(points > 4, data);
  }

  @Test
  void theLineCommandsHaveAbsoluteAndRelativeForms() {
    assertEquals(
        List.of("M 1.0 2.0", "L 3.0 4.0", "L 5.0 4.0", "L 5.0 6.0", "Z", "M 1.0 2.0"),
        segments("M1 2 L3 4 H5 V6 Z"));
    assertEquals(
        List.of("M 1.0 2.0", "L 3.0 5.0", "L 7.0 5.0", "L 7.0 0.0", "Z", "M 1.0 2.0"),
        segments("m1 2 l2 3 h4 v-5 z"));
  }

  @Test
  void aCommandLetterAppliesToEachSubsequentGroupOfNumbers() {
    assertEquals(List.of("M 0.0 0.0", "L 1.0 1.0", "L 2.0 0.0"), segments("M0 0 1 1 2 0"));
    assertEquals(List.of("M 1.0 1.0", "L 2.0 2.0", "L 4.0 5.0"), segments("m1 1 1 1 2 3"));
    assertEquals(List.of("M 0.0 0.0", "L 1.0 0.0", "L 3.0 0.0"), segments("M0 0h1 2"));
    assertEquals(
        List.of("M 0.0 0.0", "Q 1.0 1.0 2.0 0.0", "Q 3.0 1.0 4.0 0.0"),
        segments("M0 0Q1 1 2 0 3 1 4 0"));
  }

  @Test
  void theNumbersCanHaveNoSeparator() {
    assertEquals(List.of("M 1.5 0.5", "L -1.0 -2.0"), segments("M1.5.5-1-2"));
    assertEquals(List.of("M 0.5 0.25", "L 10.0 -0.5"), segments("M.5.25L1e1-.5"));
    assertEquals(List.of("M 1.0 2.0", "L 3.0 4.0"), segments(" M 1,2\n\tL+3 , 4 "));
    assertEquals(List.of("M 100.0 0.02", "L 5.0 0.0"), segments("M1E2 2e-2L5.,0"));
  }

  @Test
  void aSubpathAfterACloseStartsAtTheFirstPointOfTheClosedSubpath() {
    assertEquals(
        List.of("M 2.0 2.0", "L 4.0 2.0", "Z", "M 2.0 2.0", "L 3.0 3.0"), segments("M2 2h2zl1 1"));
  }

  @Test
  void theCurveCommandsHaveAbsoluteAndRelativeForms() {
    assertEquals(List.of("M 0.0 0.0", "C 1.0 2.0 3.0 4.0 5.0 6.0"), segments("M0 0C1 2 3 4 5 6"));
    assertEquals(
        List.of("M 10.0 10.0", "C 11.0 12.0 13.0 14.0 15.0 16.0"), segments("M10 10c1 2 3 4 5 6"));
    assertEquals(List.of("M 0.0 0.0", "Q 1.0 2.0 3.0 4.0"), segments("M0 0Q1 2 3 4"));
    assertEquals(List.of("M 10.0 10.0", "Q 11.0 12.0 13.0 14.0"), segments("M10 10q1 2 3 4"));
  }

  @Test
  void aSmoothCurveReflectsTheControlPointOfTheCurveBeforeIt() {
    assertEquals(
        List.of("M 0.0 0.0", "C 0.0 2.0 2.0 4.0 4.0 4.0", "C 6.0 4.0 8.0 2.0 8.0 0.0"),
        segments("M0 0C0 2 2 4 4 4S8 2 8 0"));
    assertEquals(
        List.of("M 0.0 0.0", "C 0.0 2.0 2.0 4.0 4.0 4.0", "C 6.0 4.0 8.0 2.0 8.0 0.0"),
        segments("M0 0c0 2 2 4 4 4s4-2 4-4"));
    assertEquals(
        List.of("M 0.0 0.0", "Q 2.0 4.0 4.0 0.0", "Q 6.0 -4.0 8.0 0.0", "Q 10.0 4.0 12.0 0.0"),
        segments("M0 0Q2 4 4 0T8 0t4 0"));
    // With no curve before it, the control point is the current point.
    assertEquals(List.of("M 1.0 1.0", "C 1.0 1.0 2.0 3.0 4.0 4.0"), segments("M1 1S2 3 4 4"));
    assertEquals(List.of("M 1.0 1.0", "Q 1.0 1.0 4.0 4.0"), segments("M1 1T4 4"));
    assertEquals(
        List.of("M 0.0 0.0", "Q 2.0 4.0 4.0 0.0", "C 4.0 0.0 6.0 2.0 8.0 0.0"),
        segments("M0 0Q2 4 4 0S6 2 8 0"));
  }

  @Test
  void anArcIsAPartOfACircle() {
    // The sweep flag selects the direction of increasing angles: through the top of the box.
    assertBounds(0, -5, 10, 5, "M0 0A5 5 0 0 1 10 0");
    assertBounds(0, 0, 10, 5, "M0 0A5 5 0 0 0 10 0");
    assertOnCircle(5, 0, 5, "M0 0A5 5 0 0 1 10 0");
    assertOnCircle(5, 0, 5, "M0 0a5 5 0 0 0 10 0");
    // Two arcs make a full circle.
    assertBounds(2, 2, 20, 20, "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z");
    assertOnCircle(12, 12, 10, "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20");
    assertTrue(path("M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z").contains(12, 12));
  }

  @Test
  void theLargeArcFlagSelectsTheLongPartOfTheCircle() {
    // The two points are a quarter of a circle of radius 5 apart.
    assertBounds(0, 0, 5, 5, "M5 0A5 5 0 0 1 0 5");
    assertOnCircle(0, 0, 5, "M5 0A5 5 0 0 1 0 5");
    assertOnCircle(5, 5, 5, "M5 0A5 5 0 0 0 0 5");
    assertBounds(0, 0, 10, 10, "M5 0A5 5 0 1 1 0 5");
    assertBounds(-5, -5, 10, 10, "M5 0A5 5 0 1 0 0 5");
    assertOnCircle(0, 0, 5, "M5 0A5 5 0 1 0 0 5");
  }

  @Test
  void theFlagsOfAnArcCanHaveNoSeparator() {
    assertEquals(segments("M0 0a5 5 0 0 1 10 0"), segments("M0 0a5 5 0 0110 0"));
    assertEquals(segments("M0 0a5 5 0 1 0 10 0"), segments("M0 0a5,5,0,10,10,0"));
    assertEquals(segments("M0 0a5 5 0 1 1 -3 4"), segments("M0 0a5 5 0 11-3 4"));
  }

  @Test
  void anArcOfAnEllipseHasARotation() {
    // The long axis of the ellipse is vertical after a rotation of 90 degrees.
    assertBounds(0, 0, 5, 20, "M0 0A10 5 90 0 1 0 20");
    assertBounds(-5, 0, 5, 20, "M0 0A10 5 90 0 0 0 20");
    assertBounds(0, -5, 20, 5, "M0 0A10 5 0 0 1 20 0");
  }

  @Test
  void anArcWithRadiiThatAreTooSmallGetsLargerRadii() {
    assertBounds(0, -5, 10, 5, "M0 0A1 1 0 0 1 10 0");
    assertOnCircle(5, 0, 5, "M0 0A1 1 0 0 1 10 0");
  }

  @Test
  void anArcWithNoRadiusIsALineAndAnArcToTheSamePointIsNothing() {
    assertEquals(List.of("M 0.0 0.0", "L 10.0 0.0"), segments("M0 0A0 5 0 0 1 10 0"));
    assertEquals(List.of("M 3.0 3.0", "L 4.0 4.0"), segments("M3 3A5 5 0 1 1 3 3L4 4"));
  }

  @Test
  void anArcEndsExactlyOnItsEndPoint() {
    Path2D arc = path("M0 0A5 5 0 0 1 10 0");

    assertEquals(10, arc.getCurrentPoint().getX(), DELTA);
    assertEquals(0, arc.getCurrentPoint().getY(), DELTA);
  }

  @Test
  void thePathHasTheNonZeroWindingRule() {
    Path2D ring = path("M0 0h10v10h-10zM3 3v4h4v-4z");

    assertEquals(Path2D.WIND_NON_ZERO, ring.getWindingRule());
    assertTrue(ring.contains(1, 1));
    // The second square has the opposite direction. Thus it is a hole.
    assertFalse(ring.contains(5, 5));
  }

  @Test
  void dataThatIsNotCorrectGivesNoPath() {
    for (String data :
        new String[] {
          "",
          "   ",
          "L1 1",
          "1 2",
          "M",
          "M1",
          "M1 2 L3",
          "M1 2 X3 4",
          "M1 2 L3 4 5",
          "M1 2 L a b",
          "M0 0A5 5 0 2 1 10 0",
          "M0 0A5 5 0 0 10",
          "M0 0C1 2 3 4 5",
          "M1 2 L-",
          "M1 2 L.",
          "M1 2 L1e 3",
          "M1 2 L1e999 0",
          "M1 2 L99999999 0",
          "M1 2 LNaN 0",
          "M1 2 LInfinity 0",
          "<path d=\"M0 0\"/>",
          "M0 0 L1 1 javascript:alert(1)"
        }) {
      assertEquals(Optional.empty(), SvgPath.parse(data), data);
    }
    assertEquals(Optional.empty(), SvgPath.parse(null));
  }

  @Test
  void dataThatIsTooLongGivesNoPath() {
    String segment = "l1 0";
    String fits = "M0 0" + segment.repeat((SvgPath.MAX_LENGTH - 4) / segment.length());

    assertTrue(SvgPath.parse(fits).isPresent());
    assertEquals(Optional.empty(), SvgPath.parse(fits + segment));
  }
}
