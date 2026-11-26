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

import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.swing.surface.ImageLoader;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.imageio.ImageIO;

/**
 * An {@link ImageLoader} for tests. It uses no network and no thread.
 *
 * <p>A loader from the constructor keeps each request until the test answers it. Thus a test
 * controls the sequence of the results. A loader from {@link #answering(Function)} answers each
 * request during the call of {@code load}. Use a loader on the event dispatch thread.
 */
public final class TestImageLoader implements ImageLoader {
  /** One request of a view. */
  private static final class Request {
    final URI uri;
    final Consumer<Optional<Image>> onResult;
    boolean cancelled;
    boolean answered;

    Request(URI uri, Consumer<Optional<Image>> onResult) {
      this.uri = uri;
      this.onResult = onResult;
    }
  }

  /** The URL of each request, in the sequence of the requests. */
  public final List<URI> requests = new ArrayList<>();

  /** The URL of each request that the view cancelled, in the sequence of the cancellations. */
  public final List<URI> cancelled = new ArrayList<>();

  private final List<Request> all = new ArrayList<>();
  private final Function<URI, Optional<Image>> answers;

  /** Makes a loader that keeps each request until the test answers it. */
  public TestImageLoader() {
    this.answers = null;
  }

  private TestImageLoader(Function<URI, Optional<Image>> answers) {
    this.answers = answers;
  }

  /**
   * Makes a loader that answers each request during the call of {@code load}.
   *
   * @param answers gives the image for a URL, or an empty result for a failure
   * @return the loader
   */
  public static TestImageLoader answering(Function<URI, Optional<Image>> answers) {
    return new TestImageLoader(answers);
  }

  /**
   * Makes a loader that answers each request with a pattern image. The URL selects the size: a URL
   * that contains {@code tall} gives 200 x 400 pixels, a URL that contains {@code square} gives 300
   * x 300 pixels, a URL that contains {@code fail} gives no image, and each other URL gives 400 x
   * 200 pixels.
   *
   * @return the loader
   */
  public static TestImageLoader patterns() {
    return answering(uri -> {
      String text = uri.toString();
      if (text.contains("fail")) {
        return Optional.empty();
      }
      if (text.contains("tall")) {
        return Optional.of(pattern(200, 400));
      }
      return Optional.of(text.contains("square") ? pattern(300, 300) : pattern(400, 200));
    });
  }

  @Override
  public Subscription load(URI uri, Consumer<Optional<Image>> onResult) {
    requests.add(uri);
    Request request = new Request(uri, onResult);
    all.add(request);
    if (answers != null) {
      request.answered = true;
      onResult.accept(answers.apply(uri));
    }
    return () -> {
      if (!request.cancelled) {
        request.cancelled = true;
        cancelled.add(uri);
      }
    };
  }

  /**
   * Gets the number of the requests that have no answer and that the view did not cancel.
   *
   * @return the number
   */
  public int pending() {
    return (int) all.stream().filter(r -> !r.answered && !r.cancelled).count();
  }

  /**
   * Answers each waiting request for a URL with an image.
   *
   * @param uri the URL
   * @param image the image
   */
  public void complete(String uri, Image image) {
    answer(uri, Optional.of(image), false);
  }

  /**
   * Answers each waiting request for a URL with no image.
   *
   * @param uri the URL
   */
  public void fail(String uri) {
    answer(uri, Optional.empty(), false);
  }

  /**
   * Answers each request for a URL that the view cancelled, as a loader that does not obey a
   * cancellation does.
   *
   * @param uri the URL
   * @param image the image
   */
  public void completeCancelled(String uri, Image image) {
    answer(uri, Optional.of(image), true);
  }

  private void answer(String uri, Optional<Image> result, boolean cancelledOnes) {
    int count = 0;
    for (Request request : List.copyOf(all)) {
      if (!request.answered
          && request.cancelled == cancelledOnes
          && request.uri.toString().equals(uri)) {
        request.answered = true;
        request.onResult.accept(result);
        count++;
      }
    }
    if (count == 0) {
      throw new AssertionError("No request waits for " + uri + ". The requests are " + requests);
    }
  }

  /**
   * Makes an image with a pattern that shows a distortion and a cut: a color gradient, a border, a
   * grid of squares, the two diagonals and a circle in the center.
   *
   * @param width the width in pixels
   * @param height the height in pixels
   * @return the image
   */
  public static BufferedImage pattern(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setPaint(
          new GradientPaint(
              0, 0, new Color(0x2a, 0x6f, 0xdb), width, height, new Color(0xf2, 0xa9, 0x3b)));
      g.fillRect(0, 0, width, height);
      g.setColor(new Color(255, 255, 255, 90));
      for (int x = 0; x < width; x += 50) {
        g.drawLine(x, 0, x, height);
      }
      for (int y = 0; y < height; y += 50) {
        g.drawLine(0, y, width, y);
      }
      g.setColor(Color.WHITE);
      g.setStroke(new BasicStroke(3));
      g.drawLine(0, 0, width, height);
      g.drawLine(0, height, width, 0);
      int radius = Math.min(width, height) / 3;
      g.drawOval(width / 2 - radius, height / 2 - radius, 2 * radius, 2 * radius);
      g.setColor(new Color(0x7a, 0x1f, 0x3d));
      g.setStroke(new BasicStroke(8));
      g.drawRect(4, 4, width - 8, height - 8);
    } finally {
      g.dispose();
    }
    return image;
  }

  /**
   * Makes an image of one color.
   *
   * @param width the width in pixels
   * @param height the height in pixels
   * @param color the color
   * @return the image
   */
  public static BufferedImage solid(int width, int height, Color color) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(color);
      g.fillRect(0, 0, width, height);
    } finally {
      g.dispose();
    }
    return image;
  }

  /**
   * Writes an image as a {@code data} URL with PNG data.
   *
   * @param image the image
   * @return the URL
   */
  public static String dataUrl(BufferedImage image) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, "png", bytes);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
  }
}
