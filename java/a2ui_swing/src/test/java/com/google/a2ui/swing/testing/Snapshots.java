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

package com.google.a2ui.swing.testing;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.JComponent;

/**
 * Makes an image of a Swing component with no window, for the visual check of a test. A person
 * looks at the images. The build does not compare them. Use this class on the event dispatch
 * thread.
 */
public final class Snapshots {
  /** The directory of the images, relative to the directory of the module. */
  public static final Path DIRECTORY = Path.of("target", "snapshots");

  private Snapshots() {}

  /**
   * Paints a component at its current size. Mount the component and do its layout before this call,
   * for example with {@link Headless#mount(com.google.a2ui.swing.surface.A2uiSurface, int, int)}.
   *
   * @param component the component to paint
   * @return the image
   */
  public static BufferedImage render(JComponent component) {
    BufferedImage image =
        new BufferedImage(
            Math.max(1, component.getWidth()),
            Math.max(1, component.getHeight()),
            BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      graphics.setColor(component.getBackground());
      graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
      // The print methods paint with no double buffer. Thus they work with no window.
      component.print(graphics);
    } finally {
      graphics.dispose();
    }
    return image;
  }

  /**
   * Paints a component and writes the image as a PNG file in {@link #DIRECTORY}.
   *
   * @param component the component to paint
   * @param name the name of the file, without the extension
   * @return the path of the file
   */
  public static Path write(JComponent component, String name) {
    Path file = DIRECTORY.resolve(name + ".png");
    try {
      Files.createDirectories(DIRECTORY);
      ImageIO.write(render(component), "png", file.toFile());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return file;
  }
}
