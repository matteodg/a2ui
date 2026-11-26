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

package com.google.a2ui.samples.swing;

import com.google.a2ui.swing.catalog.basic.SwingBasicCatalog;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** The helpers of the tests that use Swing components with no display. */
final class Headless {
  /** The directory of the images, relative to the directory of the module. */
  static final Path SNAPSHOTS = Path.of("target", "snapshots");

  /** A block of test code that can throw each exception. */
  @FunctionalInterface
  interface Block {
    /** Runs the block. */
    void run() throws Exception;
  }

  private Headless() {}

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

  /** Reads the examples of the basic catalog from the repository. */
  static List<Example> examples() {
    Path directory =
        ExampleLoader.resolveDirectory(
            Optional.ofNullable(System.getProperty(ExampleLoader.DIRECTORY_PROPERTY)), Path.of(""));
    return ExampleLoader.load(directory, SwingBasicCatalog.viewNames());
  }

  /**
   * Connects a component as a window does, in a parent that is not in a window, and gives it a
   * size. Call this method on the event dispatch thread.
   */
  static void mount(JComponent component, int width, int height) {
    JPanel holder = new JPanel(null);
    holder.add(component);
    component.addNotify();
    component.setSize(width, height);
    component.validate();
  }

  /**
   * Does the layouts that the components requested, as the repaint manager of a window does. Call
   * this method from the test thread.
   */
  static void settle(JComponent component) {
    for (int pass = 0; pass < 4; pass++) {
      onEdt(component::validate);
    }
  }

  /** Finds each component of a tree that a test accepts. */
  static List<Component> find(Component root, Predicate<Component> test) {
    List<Component> found = new ArrayList<>();
    collect(root, test, found);
    return found;
  }

  private static void collect(
      Component component, Predicate<Component> test, List<Component> found) {
    if (test.test(component)) {
      found.add(component);
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        collect(child, test, found);
      }
    }
  }

  /**
   * Paints a component at its size and writes the image as a PNG file in {@link #SNAPSHOTS}. Call
   * this method on the event dispatch thread.
   */
  static Path snapshot(JComponent component, String name) {
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
    Path file = SNAPSHOTS.resolve(name + ".png");
    try {
      Files.createDirectories(SNAPSHOTS);
      ImageIO.write(image, "png", file.toFile());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return file;
  }
}
