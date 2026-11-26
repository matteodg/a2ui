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

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Optional;
import javax.swing.ImageIcon;

/**
 * A loaded image that paints itself in a box, with one of the values of the property {@code fit}.
 *
 * <p>Java2D makes a poor image when it makes an image much smaller in one step. Thus this class
 * first makes the image smaller in steps of one half, until the result is less than two times
 * larger than its size on the screen. It keeps that smaller image for the subsequent paints. A
 * paint at a very different size makes a new one. This class has no other cache.
 *
 * <p>Use an object only on the event dispatch thread.
 */
final class Picture {
  /** The values of the property {@code fit}. They are the values of CSS {@code object-fit}. */
  enum Fit {
    /** The image is as large as possible and all of it shows. The box can have empty areas. */
    CONTAIN,
    /** The image fills the box and keeps its proportions. The box cuts the parts outside it. */
    COVER,
    /** The image fills the box. It does not keep its proportions. */
    FILL,
    /** The image has its own size. The box cuts the parts outside it. */
    NONE,
    /** The smaller result of {@link #NONE} and {@link #CONTAIN}. */
    SCALE_DOWN;

    /**
     * Gets the value for the text of the property.
     *
     * @param value the value of the property
     * @return the value, or an empty result for an unknown or absent text
     */
    static Optional<Fit> fromWire(Object value) {
      if (!(value instanceof String text)) {
        return Optional.empty();
      }
      return switch (text) {
        case "contain" -> Optional.of(CONTAIN);
        case "cover" -> Optional.of(COVER);
        case "fill" -> Optional.of(FILL);
        case "none" -> Optional.of(NONE);
        case "scaleDown" -> Optional.of(SCALE_DOWN);
        default -> Optional.empty();
      };
    }
  }

  private final BufferedImage original;

  /** A smaller copy of the image for the size of the last paint, or null. */
  private BufferedImage reduced;

  private Picture(BufferedImage original) {
    this.original = original;
  }

  /**
   * Makes a picture from the image of an image loader.
   *
   * @param image the image
   * @return the picture, or an empty result if the image has no size
   */
  static Optional<Picture> of(Image image) {
    if (image == null) {
      return Optional.empty();
    }
    if (image instanceof BufferedImage buffered) {
      return buffered.getWidth() > 0 && buffered.getHeight() > 0
          ? Optional.of(new Picture(buffered))
          : Optional.empty();
    }
    // An image of the toolkit loads its pixels later. An icon waits for them.
    Image complete = new ImageIcon(image).getImage();
    int width = complete.getWidth(null);
    int height = complete.getHeight(null);
    if (width <= 0 || height <= 0) {
      return Optional.empty();
    }
    BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = copy.createGraphics();
    try {
      g.drawImage(complete, 0, 0, null);
    } finally {
      g.dispose();
    }
    return Optional.of(new Picture(copy));
  }

  /**
   * Gets the width of the image.
   *
   * @return the width in pixels
   */
  int width() {
    return original.getWidth();
  }

  /**
   * Gets the height of the image.
   *
   * @return the height in pixels
   */
  int height() {
    return original.getHeight();
  }

  /**
   * Calculates where an image shows for a value of {@code fit}. The result can be larger than the
   * box. The image is in the center of the box.
   *
   * @param fit the value of the property
   * @param width the width of the image
   * @param height the height of the image
   * @param box the box that shows the image
   * @return the rectangle that the full image fills
   */
  static Rectangle2D destination(Fit fit, double width, double height, Rectangle2D box) {
    double contain = Math.min(box.getWidth() / width, box.getHeight() / height);
    double scaleX;
    double scaleY;
    switch (fit) {
      case FILL -> {
        scaleX = box.getWidth() / width;
        scaleY = box.getHeight() / height;
      }
      case COVER -> {
        scaleX = Math.max(box.getWidth() / width, box.getHeight() / height);
        scaleY = scaleX;
      }
      case NONE -> {
        scaleX = 1;
        scaleY = 1;
      }
      case SCALE_DOWN -> {
        scaleX = Math.min(1, contain);
        scaleY = scaleX;
      }
      default -> {
        scaleX = contain;
        scaleY = contain;
      }
    }
    double shownWidth = width * scaleX;
    double shownHeight = height * scaleY;
    return new Rectangle2D.Double(
        box.getCenterX() - shownWidth / 2,
        box.getCenterY() - shownHeight / 2,
        shownWidth,
        shownHeight);
  }

  /**
   * Paints the image in a box.
   *
   * @param graphics the graphics object of a paint
   * @param box the box that shows the image
   * @param fit the value of the property {@code fit}
   * @param clip the shape that cuts the image, for example the box or a circle in it
   */
  void paint(Graphics2D graphics, Rectangle2D box, Fit fit, Shape clip) {
    if (box.getWidth() <= 0 || box.getHeight() <= 0) {
      return;
    }
    Rectangle2D shown = destination(fit, width(), height(), box);
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.clip(clip);
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      AffineTransform device = g.getTransform();
      BufferedImage source =
          sourceFor(
              shown.getWidth() * Math.abs(device.getScaleX()),
              shown.getHeight() * Math.abs(device.getScaleY()));
      AffineTransform place =
          new AffineTransform(
              shown.getWidth() / source.getWidth(),
              0,
              0,
              shown.getHeight() / source.getHeight(),
              shown.getX(),
              shown.getY());
      g.drawImage(source, place, null);
    } finally {
      g.dispose();
    }
  }

  /**
   * Gets the image to paint at a size on the device: the original, or a copy that is less than two
   * times larger than the size.
   */
  private BufferedImage sourceFor(double deviceWidth, double deviceHeight) {
    int wantedWidth = Math.max(1, (int) Math.ceil(deviceWidth));
    int wantedHeight = Math.max(1, (int) Math.ceil(deviceHeight));
    if (original.getWidth() < 2 * wantedWidth || original.getHeight() < 2 * wantedHeight) {
      reduced = null;
      return original;
    }
    if (reduced != null
        && reduced.getWidth() >= wantedWidth
        && reduced.getHeight() >= wantedHeight
        && (reduced.getWidth() < 2 * wantedWidth || reduced.getHeight() < 2 * wantedHeight)) {
      return reduced;
    }
    BufferedImage current = original;
    while (current.getWidth() >= 2 * wantedWidth && current.getHeight() >= 2 * wantedHeight) {
      current = half(current);
    }
    reduced = current;
    return current;
  }

  /** Makes a copy of an image with one half of its width and of its height. */
  private static BufferedImage half(BufferedImage image) {
    int width = Math.max(1, image.getWidth() / 2);
    int height = Math.max(1, image.getHeight() / 2);
    BufferedImage smaller =
        new BufferedImage(
            width,
            height,
            image.getTransparency() == Transparency.OPAQUE
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = smaller.createGraphics();
    try {
      // With this hint, each new pixel is the mean of the four pixels that it replaces.
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(image, 0, 0, width, height, null);
    } finally {
      g.dispose();
    }
    return smaller;
  }
}
