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

package com.google.a2ui.swing.javafx;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** The helpers of the tests that use Swing components with no display. */
final class Swing {
  /** The directory of the images, relative to the directory of the module. */
  static final Path SNAPSHOTS = Path.of("target", "snapshots");

  /** A block of test code that can throw each exception. */
  @FunctionalInterface
  interface Block {
    /** Runs the block. */
    void run() throws Exception;
  }

  private Swing() {}

  /**
   * Runs a block on the event dispatch thread and waits for its end. A failure of the block is the
   * failure of the caller.
   */
  static void onEdt(Block block) {
    Throwable[] failure = {null};
    try {
      EventQueue.invokeAndWait(() -> {
        try {
          block.run();
        } catch (Throwable e) {
          failure[0] = e;
        }
      });
    } catch (InterruptedException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
    switch (failure[0]) {
      case null -> {}
      case RuntimeException e -> throw e;
      case Error e -> throw e;
      default -> throw new IllegalStateException(failure[0]);
    }
  }

  /**
   * Connects a component as a window does, with no window, and does its layout at a size. The
   * component gets a parent that is not in a window.
   *
   * @param component the component
   * @param width the width in pixels
   * @param height the height in pixels
   * @return the parent of the component
   */
  static JPanel mount(JComponent component, int width, int height) {
    JPanel holder = new JPanel(null);
    holder.add(component);
    component.addNotify();
    component.setSize(width, height);
    component.validate();
    return holder;
  }

  /**
   * Finds a component by its name below a container.
   *
   * @param root the container
   * @param name the name of the component
   * @return the component
   * @throws IllegalArgumentException if no component has the name
   */
  static <T extends Component> T named(Container root, String name, Class<T> type) {
    Component found = find(root, name);
    if (found == null) {
      throw new IllegalArgumentException("No component has the name " + name + ".");
    }
    return type.cast(found);
  }

  private static Component find(Container root, String name) {
    for (Component child : root.getComponents()) {
      if (name.equals(child.getName())) {
        return child;
      }
      if (child instanceof Container container) {
        Component found = find(container, name);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  /**
   * Paints a component at its current size and writes the image as a PNG file in {@link
   * #SNAPSHOTS}. A person looks at the images. The build does not compare them.
   *
   * @param component the component to paint
   * @param name the name of the file, without the extension
   * @return the image
   */
  static BufferedImage snapshot(JComponent component, String name) {
    BufferedImage image =
        new BufferedImage(
            Math.max(1, component.getWidth()),
            Math.max(1, component.getHeight()),
            BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      graphics.setColor(new Color(238, 238, 238));
      graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
      // The print methods paint with no double buffer. Thus they work with no window.
      component.print(graphics);
    } finally {
      graphics.dispose();
    }
    try {
      Files.createDirectories(SNAPSHOTS);
      ImageIO.write(image, "png", SNAPSHOTS.resolve(name + ".png").toFile());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return image;
  }
}
