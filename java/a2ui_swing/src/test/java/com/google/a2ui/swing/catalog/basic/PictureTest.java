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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.catalog.basic.Picture.Fit;
import com.google.a2ui.swing.testing.TestImageLoader;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.MemoryImageSource;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PictureTest {
  private static final Color BACK = Color.MAGENTA;
  private static final Color RED = new Color(200, 0, 0);
  private static final Color BLUE = new Color(0, 0, 200);

  private static Rectangle2D box(double x, double y, double width, double height) {
    return new Rectangle2D.Double(x, y, width, height);
  }

  /** Makes an image whose left half is red and whose right half is blue. */
  private static BufferedImage halves(int width, int height) {
    BufferedImage image = TestImageLoader.solid(width, height, RED);
    Graphics2D g = image.createGraphics();
    g.setColor(BLUE);
    g.fillRect(width / 2, 0, width - width / 2, height);
    g.dispose();
    return image;
  }

  /** Paints a picture on an image of 100 x 100 pixels with a background color. */
  private static BufferedImage paint(
      BufferedImage source, Rectangle2D box, Fit fit, boolean circle, double scale) {
    BufferedImage target = TestImageLoader.solid(100, 100, BACK);
    Graphics2D g = target.createGraphics();
    g.scale(scale, scale);
    Picture.of(source)
        .orElseThrow()
        .paint(
            g,
            box,
            fit,
            circle
                ? new Ellipse2D.Double(box.getX(), box.getY(), box.getWidth(), box.getHeight())
                : box);
    g.dispose();
    return target;
  }

  private static Color at(BufferedImage image, int x, int y) {
    return new Color(image.getRGB(x, y));
  }

  @Test
  void eachTextOfThePropertyHasAValue() {
    assertEquals(Optional.of(Fit.CONTAIN), Fit.fromWire("contain"));
    assertEquals(Optional.of(Fit.COVER), Fit.fromWire("cover"));
    assertEquals(Optional.of(Fit.FILL), Fit.fromWire("fill"));
    assertEquals(Optional.of(Fit.NONE), Fit.fromWire("none"));
    assertEquals(Optional.of(Fit.SCALE_DOWN), Fit.fromWire("scaleDown"));
    assertEquals(Optional.empty(), Fit.fromWire("scale-down"));
    assertEquals(Optional.empty(), Fit.fromWire(null));
    assertEquals(Optional.empty(), Fit.fromWire(3L));
  }

  @Test
  void theDestinationOfAWideImageInASquareBox() {
    Rectangle2D square = box(10, 20, 100, 100);

    assertEquals(box(10, 20, 100, 100), Picture.destination(Fit.FILL, 400, 200, square));
    assertEquals(box(10, 45, 100, 50), Picture.destination(Fit.CONTAIN, 400, 200, square));
    assertEquals(box(-40, 20, 200, 100), Picture.destination(Fit.COVER, 400, 200, square));
    assertEquals(box(-140, -30, 400, 200), Picture.destination(Fit.NONE, 400, 200, square));
    assertEquals(box(10, 45, 100, 50), Picture.destination(Fit.SCALE_DOWN, 400, 200, square));
  }

  @Test
  void theDestinationOfATallImageInAWideBox() {
    Rectangle2D wide = box(0, 0, 300, 100);

    assertEquals(box(0, 0, 300, 100), Picture.destination(Fit.FILL, 50, 200, wide));
    assertEquals(box(137.5, 0, 25, 100), Picture.destination(Fit.CONTAIN, 50, 200, wide));
    assertEquals(box(0, -550, 300, 1200), Picture.destination(Fit.COVER, 50, 200, wide));
    assertEquals(box(125, -50, 50, 200), Picture.destination(Fit.NONE, 50, 200, wide));
    assertEquals(box(137.5, 0, 25, 100), Picture.destination(Fit.SCALE_DOWN, 50, 200, wide));
  }

  @Test
  void aSmallImageBecomesLargerOnlyWithContainCoverAndFill() {
    Rectangle2D square = box(0, 0, 100, 100);

    assertEquals(box(0, 0, 100, 100), Picture.destination(Fit.FILL, 20, 10, square));
    assertEquals(box(0, 25, 100, 50), Picture.destination(Fit.CONTAIN, 20, 10, square));
    assertEquals(box(-50, 0, 200, 100), Picture.destination(Fit.COVER, 20, 10, square));
    assertEquals(box(40, 45, 20, 10), Picture.destination(Fit.NONE, 20, 10, square));
    assertEquals(box(40, 45, 20, 10), Picture.destination(Fit.SCALE_DOWN, 20, 10, square));
  }

  @Test
  void fillPaintsAllTheImageInAllTheBox() {
    BufferedImage painted = paint(halves(40, 20), box(10, 10, 60, 60), Fit.FILL, false, 1);

    assertEquals(RED, at(painted, 12, 12));
    assertEquals(RED, at(painted, 36, 68));
    assertEquals(BLUE, at(painted, 44, 12));
    assertEquals(BLUE, at(painted, 68, 68));
    assertEquals(BACK, at(painted, 8, 40));
    assertEquals(BACK, at(painted, 71, 40));
    assertEquals(BACK, at(painted, 40, 71));
  }

  @Test
  void containPaintsAllTheImageAndLeavesTheOtherAreaOfTheBox() {
    BufferedImage painted = paint(halves(40, 20), box(10, 10, 60, 60), Fit.CONTAIN, false, 1);

    // The image is 60 x 30 pixels, from y = 25 to y = 55.
    assertEquals(BACK, at(painted, 40, 20));
    assertEquals(RED, at(painted, 12, 28));
    assertEquals(BLUE, at(painted, 68, 52));
    assertEquals(BACK, at(painted, 40, 58));
  }

  @Test
  void coverFillsTheBoxAndCutsTheSidesOfTheImage() {
    BufferedImage painted = paint(halves(40, 20), box(10, 10, 60, 60), Fit.COVER, false, 1);

    // The image is 120 x 60 pixels. The box shows its center, and nothing is out of the box.
    assertEquals(RED, at(painted, 12, 12));
    assertEquals(RED, at(painted, 36, 68));
    assertEquals(BLUE, at(painted, 44, 68));
    assertEquals(BLUE, at(painted, 68, 12));
    assertEquals(BACK, at(painted, 8, 40));
    assertEquals(BACK, at(painted, 71, 40));
  }

  @Test
  void nonePaintsTheImageAtItsOwnSizeInTheCenterOfTheBox() {
    BufferedImage small = paint(halves(20, 10), box(10, 10, 60, 60), Fit.NONE, false, 1);

    // The image is from x = 30 to x = 50 and from y = 35 to y = 45.
    assertEquals(BACK, at(small, 28, 40));
    assertEquals(RED, at(small, 32, 40));
    assertEquals(BLUE, at(small, 48, 40));
    assertEquals(BACK, at(small, 52, 40));
    assertEquals(BACK, at(small, 40, 33));
    assertEquals(BACK, at(small, 40, 47));

    BufferedImage large = paint(halves(200, 200), box(10, 10, 60, 60), Fit.NONE, false, 1);
    assertEquals(RED, at(large, 12, 12));
    assertEquals(BLUE, at(large, 68, 68));
    assertEquals(BACK, at(large, 8, 40));
    assertEquals(BACK, at(large, 71, 40));
  }

  @Test
  void aCircleCutsTheCornersOfTheImage() {
    BufferedImage painted = paint(halves(40, 40), box(10, 10, 60, 60), Fit.COVER, true, 1);

    assertEquals(RED, at(painted, 30, 40));
    assertEquals(BLUE, at(painted, 50, 40));
    assertEquals(BACK, at(painted, 12, 12));
    assertEquals(BACK, at(painted, 68, 68));
  }

  @Test
  void aLargeImageBecomesSmallInStepsAndKeepsTheMeanOfItsPixels() {
    // Each line of pixels is black and white in turn. A reduction in one step can keep only the
    // black pixels or only the white pixels.
    BufferedImage stripes = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < 1024; y++) {
      for (int x = 0; x < 1024; x++) {
        stripes.setRGB(x, y, (x + y) % 2 == 0 ? 0xffffff : 0x000000);
      }
    }

    BufferedImage painted = paint(stripes, box(10, 10, 60, 60), Fit.FILL, false, 1);

    for (int[] point : new int[][] {{12, 12}, {40, 40}, {68, 68}, {25, 55}}) {
      Color color = at(painted, point[0], point[1]);
      assertEquals(127, color.getRed(), 12, "red at " + point[0] + ", " + point[1]);
      assertEquals(127, color.getGreen(), 12);
      assertEquals(127, color.getBlue(), 12);
    }
  }

  @Test
  void thePaintUsesTheScaleOfTheScreen() {
    BufferedImage painted = paint(halves(400, 400), box(5, 5, 30, 30), Fit.FILL, false, 2);

    // With a scale of 2, the box is from 10 to 70 on the device.
    assertEquals(RED, at(painted, 12, 12));
    assertEquals(BLUE, at(painted, 68, 68));
    assertEquals(BACK, at(painted, 8, 40));
    assertEquals(BACK, at(painted, 72, 40));
  }

  @Test
  void anImageOfTheToolkitBecomesAPicture() {
    int[] pixels = new int[6 * 4];
    java.util.Arrays.fill(pixels, 0xff00c800);
    java.awt.Image image =
        Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(6, 4, pixels, 0, 6));

    Picture picture = Picture.of(image).orElseThrow();

    assertEquals(6, picture.width());
    assertEquals(4, picture.height());
  }

  @Test
  void anAbsentImageGivesNoPicture() {
    assertEquals(Optional.empty(), Picture.of(null));
    assertTrue(Picture.of(TestImageLoader.pattern(3, 2)).isPresent());
  }
}
