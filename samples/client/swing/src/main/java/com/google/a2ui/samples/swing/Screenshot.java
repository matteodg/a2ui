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

import com.google.a2ui.swing.surface.A2uiServices;
import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import javax.swing.JRootPane;
import javax.swing.ToolTipManager;

/**
 * The screenshot mode of the gallery. It opens the window, shows one example, waits until the event
 * dispatch thread has no more work and the image loader has no more requests, writes an image of
 * the window and closes the window.
 *
 * <p>The mode is a test of the adapter in a window. A layout that requests a new layout without end
 * keeps the event dispatch thread busy. Then the mode fails with a message.
 */
final class Screenshot {
  /** The time with no event after which the event dispatch thread is idle. */
  private static final long QUIET_NANOS = 300_000_000L;

  /** The time in which the event dispatch thread must become idle. */
  private static final long TIMEOUT_NANOS = 10_000_000_000L;

  /** The longest time that the mode waits for the images of the example. */
  private static final long IMAGES_NANOS = 40_000_000_000L;

  private Screenshot() {}

  /**
   * The options of the screenshot mode.
   *
   * @param file the PNG file to write
   * @param example the ID of the example to show, or an empty result for the first example
   * @param lookAndFeel the look and feel to change to after the window opens, or an empty result
   * @param tab the tab of the inspector to show, or an empty result
   * @param size the size to give to the window after the example shows, or an empty result
   */
  record Options(
      Path file,
      Optional<String> example,
      Optional<String> lookAndFeel,
      Optional<String> tab,
      Optional<Dimension> size) {}

  /**
   * Reads the size of a window.
   *
   * @param text the width and the height in pixels, for example {@code 900x600}
   * @return the size
   * @throws IllegalArgumentException if the text is not a size
   */
  static Dimension parseSize(String text) {
    String[] parts = text.toLowerCase(Locale.ROOT).split("x");
    try {
      if (parts.length == 2) {
        Dimension size =
            new Dimension(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        if (size.width > 0 && size.height > 0) {
          return size;
        }
      }
    } catch (NumberFormatException e) {
      // The message below tells the problem.
    }
    throw new IllegalArgumentException(
        "The window size '" + text + "' is not correct. Use for example 900x600.");
  }

  /**
   * Runs the screenshot mode. Call this method on the main thread.
   *
   * @param examples the examples of the gallery
   * @param options the options of the mode
   * @throws IllegalStateException if the event dispatch thread does not become idle, or an option
   *     is not correct
   */
  static void run(List<Example> examples, Options options) {
    if (examples.isEmpty()) {
      throw new IllegalStateException("The gallery has no examples.");
    }
    options.lookAndFeel().ifPresent(LookAndFeels::className);
    CountingImageLoader images = new CountingImageLoader(A2uiServices.DEFAULT.imageLoader());
    CountingQueue queue = new CountingQueue();
    Toolkit.getDefaultToolkit().getSystemEventQueue().push(queue);
    long start = System.nanoTime();
    GalleryFrame[] window = {null};
    onEdt(() -> {
      // The mouse pointer of the user can be on the window. A tool tip must not be in the image.
      ToolTipManager.sharedInstance().setEnabled(false);
      LookAndFeels.apply(LookAndFeels.SYSTEM, null);
      window[0] =
          new GalleryFrame(
              examples, A2uiServices.DEFAULT.withImageLoader(images), LookAndFeels.SYSTEM);
      window[0].frame().setVisible(true);
    });
    GalleryFrame frame = window[0];
    try {
      awaitIdle(queue, "after the window opened");
      String example = options.example().orElse(examples.get(0).id());
      onEdt(() -> {
        if (!frame.gallery().select(example)) {
          throw new IllegalStateException("The gallery has no example '" + example + "'.");
        }
        options
            .tab()
            .ifPresent(tab -> {
              if (!frame.gallery().inspector().selectTab(tab)) {
                throw new IllegalStateException(
                    "The inspector has no tab '"
                        + tab
                        + "'. Use one of "
                        + InspectorPanel.TABS
                        + ".");
              }
            });
      });
      awaitIdle(queue, "after the selection of the example '" + example + "'");
      awaitImages(images);
      awaitIdle(queue, "after the images of the example '" + example + "'");
      if (options.lookAndFeel().isPresent()) {
        onEdt(() -> frame.setLookAndFeel(options.lookAndFeel().get()));
        awaitIdle(queue, "after the change of the look and feel");
      }
      if (options.size().isPresent()) {
        onEdt(() -> frame.frame().setSize(options.size().get()));
        awaitIdle(queue, "after the change of the window size");
      }
      long events = queue.dispatched.get();
      long millis = (System.nanoTime() - start) / 1_000_000L;
      onEdt(() -> write(frame.frame().getRootPane(), options.file()));
      System.out.println(
          "Wrote "
              + options.file().toAbsolutePath()
              + ": example '"
              + example
              + "', "
              + events
              + " events, "
              + millis
              + " ms, "
              + images.loaded()
              + " images, "
              + (images.failed() + images.pending())
              + " images that did not load.");
    } finally {
      onEdt(() -> frame.frame().dispose());
    }
  }

  /**
   * Gets the size of the image of a window.
   *
   * @param window the size of the content of the window
   * @param scale the scale of the screen of the window
   * @return the size in the pixels of the screen. Each side is 1 or more.
   */
  static Dimension imageSize(Dimension window, AffineTransform scale) {
    return new Dimension(
        Math.max(1, (int) Math.ceil(window.width * scale.getScaleX())),
        Math.max(1, (int) Math.ceil(window.height * scale.getScaleY())));
  }

  /**
   * Paints the content of the window and its menu bar on an image, and writes the image. The image
   * has the scale of the screen of the window. A component calculates the width of its text for
   * that scale. With a different scale, a text can be wider than its component.
   */
  private static void write(JRootPane root, Path file) {
    GraphicsConfiguration screen = root.getGraphicsConfiguration();
    AffineTransform scale = screen == null ? new AffineTransform() : screen.getDefaultTransform();
    Dimension size = imageSize(root.getSize(), scale);
    BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      // With the text hints of the desktop, the text has the widths that it has on the screen.
      if (Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints")
          instanceof Map<?, ?> hints) {
        graphics.addRenderingHints(hints);
      }
      graphics.scale(scale.getScaleX(), scale.getScaleY());
      root.paint(graphics);
    } finally {
      graphics.dispose();
    }
    try {
      Path parent = file.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      ImageIO.write(image, "png", file.toFile());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Waits until the image loader has no more requests. An image that does not arrive in the time
   * limit does not stop the mode: the image of the window then shows the area of that image.
   */
  private static void awaitImages(CountingImageLoader images) {
    long start = System.nanoTime();
    while (images.pending() > 0 && System.nanoTime() - start < IMAGES_NANOS) {
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("The wait for the images stopped.", e);
      }
    }
  }

  /** Waits until the event dispatch thread ran no event for some time. */
  private static void awaitIdle(CountingQueue queue, String when) {
    long start = System.nanoTime();
    long before = queue.dispatched.get();
    while (true) {
      long now = System.nanoTime();
      if (!queue.busy && queue.peekEvent() == null && now - queue.lastEnd >= QUIET_NANOS) {
        return;
      }
      if (now - start > TIMEOUT_NANOS) {
        throw new IllegalStateException(
            "The event dispatch thread did not become idle in 10 seconds "
                + when
                + ". It ran "
                + (queue.dispatched.get() - before)
                + " events in this time. A layout possibly requests a new layout without end.");
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("The wait for the event dispatch thread stopped.", e);
      }
    }
  }

  private static void onEdt(Runnable task) {
    try {
      EventQueue.invokeAndWait(task);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("The wait for the event dispatch thread stopped.", e);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException failure) {
        throw failure;
      }
      throw new IllegalStateException(e.getCause());
    }
  }

  /** An event queue that counts its events and knows when the last event ended. */
  private static final class CountingQueue extends EventQueue {
    private final AtomicLong dispatched = new AtomicLong();
    private volatile long lastEnd = System.nanoTime();
    private volatile boolean busy;

    @Override
    protected void dispatchEvent(AWTEvent event) {
      busy = true;
      try {
        super.dispatchEvent(event);
      } finally {
        dispatched.incrementAndGet();
        lastEnd = System.nanoTime();
        busy = false;
      }
    }
  }
}
