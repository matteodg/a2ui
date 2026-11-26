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

import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.util.Optional;

/**
 * Reads SVG path data, the content of the attribute {@code d} of an SVG {@code path} element, and
 * makes a {@code Path2D} from it.
 *
 * <p>The reader knows each command of SVG 1.1: {@code M}, {@code L}, {@code H}, {@code V}, {@code
 * C}, {@code S}, {@code Q}, {@code T}, {@code A} and {@code Z}, in upper case for absolute
 * coordinates and in lower case for relative coordinates. It reads the compact forms: a command
 * letter that applies to more than one group of numbers, numbers with no separator such as {@code
 * 1.5.5} and {@code -1-2}, and the flags of an arc with no separator.
 *
 * <p>Path data can come from the agent. Thus the reader does not throw for data that is not
 * correct. It gives an empty result. It also refuses data that is longer than {@value #MAX_LENGTH}
 * characters and numbers that are not finite.
 */
final class SvgPath {
  /** The largest number of characters of path data that the reader accepts. */
  static final int MAX_LENGTH = 16_384;

  /** The largest absolute value of a coordinate that the reader accepts. */
  private static final double MAX_COORDINATE = 1e6;

  private final String data;
  private int position;

  private final Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO);

  /** The current point. */
  private double x;

  private double y;

  /** The first point of the current subpath. */
  private double startX;

  private double startY;

  /** The last control point, for the smooth curve commands. */
  private double controlX;

  private double controlY;

  /** The command before the current command, in upper case, or 0 at the start. */
  private char previous;

  private SvgPath(String data) {
    this.data = data;
  }

  /**
   * Reads path data.
   *
   * @param data the path data
   * @return the path, with the non-zero winding rule, or an empty result if the data is null, has
   *     no command, is too long or is not correct
   */
  static Optional<Path2D> parse(String data) {
    if (data == null || data.length() > MAX_LENGTH) {
      return Optional.empty();
    }
    SvgPath reader = new SvgPath(data);
    try {
      return reader.read() ? Optional.of(reader.path) : Optional.empty();
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** Reads all the data. Returns false for data with no command. Throws for data with an error. */
  private boolean read() {
    skipSeparators(false);
    if (position == data.length()) {
      return false;
    }
    char first = data.charAt(position);
    if (first != 'M' && first != 'm') {
      throw new IllegalArgumentException("Path data must start with a move command.");
    }
    while (position < data.length()) {
      char command = data.charAt(position++);
      if (command == 'Z' || command == 'z') {
        close();
        skipSeparators(false);
        continue;
      }
      if ("MmLlHhVvCcSsQqTtAa".indexOf(command) < 0) {
        throw new IllegalArgumentException("Unknown command: " + command);
      }
      // A command letter applies to each subsequent group of numbers.
      boolean firstGroup = true;
      do {
        group(command, firstGroup);
        firstGroup = false;
        skipSeparators(true);
      } while (position < data.length() && startsNumber(data.charAt(position)));
    }
    return true;
  }

  /** Reads the numbers of one group of a command and adds the segment. */
  private void group(char command, boolean firstGroup) {
    boolean relative = Character.isLowerCase(command);
    char type = Character.toUpperCase(command);
    double baseX = relative ? x : 0;
    double baseY = relative ? y : 0;
    switch (type) {
      case 'M' -> {
        double nextX = baseX + number();
        double nextY = baseY + number();
        if (firstGroup) {
          path.moveTo(nextX, nextY);
          startX = nextX;
          startY = nextY;
        } else {
          // The subsequent groups of a move command are lines.
          path.lineTo(nextX, nextY);
        }
        moved(nextX, nextY);
      }
      case 'L' -> {
        double nextX = baseX + number();
        double nextY = baseY + number();
        path.lineTo(nextX, nextY);
        moved(nextX, nextY);
      }
      case 'H' -> {
        double nextX = baseX + number();
        path.lineTo(nextX, y);
        moved(nextX, y);
      }
      case 'V' -> {
        double nextY = baseY + number();
        path.lineTo(x, nextY);
        moved(x, nextY);
      }
      case 'C', 'S' -> {
        double x1;
        double y1;
        if (type == 'C') {
          x1 = baseX + number();
          y1 = baseY + number();
        } else {
          // The first control point is the reflection of the last control point of a curve.
          boolean afterCurve = previous == 'C' || previous == 'S';
          x1 = afterCurve ? 2 * x - controlX : x;
          y1 = afterCurve ? 2 * y - controlY : y;
        }
        double x2 = baseX + number();
        double y2 = baseY + number();
        double nextX = baseX + number();
        double nextY = baseY + number();
        path.curveTo(x1, y1, x2, y2, nextX, nextY);
        x = nextX;
        y = nextY;
        controlX = x2;
        controlY = y2;
      }
      case 'Q', 'T' -> {
        double x1;
        double y1;
        if (type == 'Q') {
          x1 = baseX + number();
          y1 = baseY + number();
        } else {
          boolean afterCurve = previous == 'Q' || previous == 'T';
          x1 = afterCurve ? 2 * x - controlX : x;
          y1 = afterCurve ? 2 * y - controlY : y;
        }
        double nextX = baseX + number();
        double nextY = baseY + number();
        path.quadTo(x1, y1, nextX, nextY);
        x = nextX;
        y = nextY;
        controlX = x1;
        controlY = y1;
      }
      case 'A' -> {
        double radiusX = Math.abs(number());
        double radiusY = Math.abs(number());
        double rotation = number();
        boolean largeArc = flag();
        boolean sweep = flag();
        double nextX = baseX + number();
        double nextY = baseY + number();
        arc(radiusX, radiusY, rotation, largeArc, sweep, nextX, nextY);
        moved(nextX, nextY);
      }
      default -> throw new IllegalArgumentException("Unknown command: " + command);
    }
    previous = type;
  }

  /** Sets the current point after a segment that has no control point. */
  private void moved(double nextX, double nextY) {
    x = nextX;
    y = nextY;
    controlX = nextX;
    controlY = nextY;
  }

  private void close() {
    path.closePath();
    moved(startX, startY);
    // A segment after a close starts a new subpath at the same point.
    path.moveTo(startX, startY);
    previous = 'Z';
  }

  /**
   * Adds an elliptical arc from the current point. The method converts the end point form of SVG to
   * the center form, as the implementation notes of the SVG specification describe.
   */
  private void arc(
      double radiusX,
      double radiusY,
      double rotation,
      boolean largeArc,
      boolean sweep,
      double endX,
      double endY) {
    if (x == endX && y == endY) {
      return;
    }
    if (radiusX == 0 || radiusY == 0) {
      path.lineTo(endX, endY);
      return;
    }
    double angle = Math.toRadians(rotation % 360.0);
    double cos = Math.cos(angle);
    double sin = Math.sin(angle);
    double halfX = (x - endX) / 2;
    double halfY = (y - endY) / 2;
    double x1 = cos * halfX + sin * halfY;
    double y1 = -sin * halfX + cos * halfY;
    // Radii that are too small for the two points become larger.
    double scale = (x1 * x1) / (radiusX * radiusX) + (y1 * y1) / (radiusY * radiusY);
    if (scale > 1) {
      radiusX *= Math.sqrt(scale);
      radiusY *= Math.sqrt(scale);
    }
    double rx2 = radiusX * radiusX;
    double ry2 = radiusY * radiusY;
    double denominator = rx2 * y1 * y1 + ry2 * x1 * x1;
    double factor = Math.sqrt(Math.max(0, (rx2 * ry2 - denominator) / denominator));
    if (largeArc == sweep) {
      factor = -factor;
    }
    double cx1 = factor * radiusX * y1 / radiusY;
    double cy1 = -factor * radiusY * x1 / radiusX;
    double centerX = cos * cx1 - sin * cy1 + (x + endX) / 2;
    double centerY = sin * cx1 + cos * cy1 + (y + endY) / 2;
    double startAngle = Math.atan2((y1 - cy1) / radiusY, (x1 - cx1) / radiusX);
    double endAngle = Math.atan2((-y1 - cy1) / radiusY, (-x1 - cx1) / radiusX);
    double extent = endAngle - startAngle;
    if (sweep && extent < 0) {
      extent += 2 * Math.PI;
    } else if (!sweep && extent > 0) {
      extent -= 2 * Math.PI;
    }
    // The angles of Arc2D go in the opposite direction, because its y axis goes up.
    Arc2D.Double arc =
        new Arc2D.Double(
            centerX - radiusX,
            centerY - radiusY,
            2 * radiusX,
            2 * radiusY,
            -Math.toDegrees(startAngle),
            -Math.toDegrees(extent),
            Arc2D.OPEN);
    path.append(
        AffineTransform.getRotateInstance(angle, centerX, centerY).createTransformedShape(arc),
        true);
    // The curves of the arc are approximate. This line puts the path exactly on the end point.
    path.lineTo(endX, endY);
  }

  private static boolean startsNumber(char c) {
    return (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.';
  }

  /** Skips white space, and one comma if the caller permits it. */
  private void skipSeparators(boolean comma) {
    boolean seen = !comma;
    while (position < data.length()) {
      char c = data.charAt(position);
      if (c == ',' && !seen) {
        seen = true;
      } else if (c != ' ' && c != '\t' && c != '\n' && c != '\r' && c != '\f') {
        return;
      }
      position++;
    }
  }

  /** Reads one flag of an arc: the character 0 or 1. */
  private boolean flag() {
    skipSeparators(true);
    if (position < data.length()) {
      char c = data.charAt(position);
      if (c == '0' || c == '1') {
        position++;
        return c == '1';
      }
    }
    throw new IllegalArgumentException("A flag must be 0 or 1.");
  }

  /** Reads one number. */
  private double number() {
    skipSeparators(true);
    int start = position;
    if (position < data.length()
        && (data.charAt(position) == '-' || data.charAt(position) == '+')) {
      position++;
    }
    int digits = digits();
    if (position < data.length() && data.charAt(position) == '.') {
      position++;
      digits += digits();
    }
    if (digits == 0) {
      throw new IllegalArgumentException("A number is necessary at " + start + ".");
    }
    if (position < data.length()
        && (data.charAt(position) == 'e' || data.charAt(position) == 'E')) {
      int mark = position++;
      if (position < data.length()
          && (data.charAt(position) == '-' || data.charAt(position) == '+')) {
        position++;
      }
      if (digits() == 0) {
        // The letter is not an exponent. It is not a command also, thus the data is not correct.
        position = mark;
      }
    }
    double value = Double.parseDouble(data.substring(start, position));
    if (!(Math.abs(value) <= MAX_COORDINATE)) {
      throw new IllegalArgumentException("A number is too large.");
    }
    return value;
  }

  private int digits() {
    int start = position;
    while (position < data.length()
        && data.charAt(position) >= '0'
        && data.charAt(position) <= '9') {
      position++;
    }
    return position - start;
  }
}
