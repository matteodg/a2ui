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

package com.google.a2ui.swing.surface;

import com.google.a2ui.core.common.Subscription;
import java.awt.EventQueue;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * The default {@link ImageLoader}. It reads {@code http}, {@code https} and {@code data} URLs with
 * {@code ImageIO}, on a thread that is not the event dispatch thread. It has no cache, and it loads
 * nothing for a relative URL.
 *
 * <p>The URL comes from the agent, and the server of an image is not trusted. Thus the loader has
 * these limits:
 *
 * <ul>
 *   <li>The connection has a timeout of {@value #CONNECT_TIMEOUT_MILLIS} milliseconds, and each
 *       read has a timeout of {@value #READ_TIMEOUT_MILLIS} milliseconds.
 *   <li>The loader reads at most {@value #MAX_BYTES} bytes, and it decodes an image of at most
 *       {@value #MAX_PIXELS} pixels. The limit for the pixels applies before the loader makes the
 *       pixel data.
 *   <li>The loader follows at most {@value #MAX_REDIRECTS} redirections, and only to a URL that
 *       {@link MediaUrls} accepts and that has the scheme {@code http} or {@code https}. Thus a
 *       server cannot send the loader to a local file.
 *   <li>The loader accepts only the status 200, and it keeps no temporary file.
 * </ul>
 */
final class DefaultImageLoader implements ImageLoader {
  /** The timeout to connect, in milliseconds. */
  static final int CONNECT_TIMEOUT_MILLIS = 10_000;

  /** The timeout of one read, in milliseconds. */
  static final int READ_TIMEOUT_MILLIS = 20_000;

  /** The largest number of bytes of the data of an image. */
  static final int MAX_BYTES = 16 * 1024 * 1024;

  /** The largest number of pixels of an image. */
  static final long MAX_PIXELS = 40_000_000L;

  /** The largest number of redirections that the loader follows for one image. */
  static final int MAX_REDIRECTS = 5;

  private static final String BASE64_MARK = ";base64";

  private final int maxBytes;
  private final long maxPixels;

  /** Makes a loader with the default limits. */
  DefaultImageLoader() {
    this(MAX_BYTES, MAX_PIXELS);
  }

  /**
   * Makes a loader.
   *
   * @param maxBytes the largest number of bytes of the data of an image
   * @param maxPixels the largest number of pixels of an image
   */
  DefaultImageLoader(int maxBytes, long maxPixels) {
    this.maxBytes = maxBytes;
    this.maxPixels = maxPixels;
  }

  @Override
  public Subscription load(URI uri, Consumer<Optional<Image>> onResult) {
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(onResult, "onResult");
    AtomicBoolean cancelled = new AtomicBoolean();
    Thread thread =
        Thread.ofVirtual()
            .name("a2ui-image-loader")
            .start(() -> {
              Optional<Image> image = cancelled.get() ? Optional.empty() : read(uri);
              EventQueue.invokeLater(() -> {
                if (!cancelled.get()) {
                  onResult.accept(image);
                }
              });
            });
    return () -> {
      cancelled.set(true);
      // The interruption stops a read that waits for the server.
      thread.interrupt();
    };
  }

  /**
   * Reads an image. Each failure gives an empty result, because a surface shows no image then.
   *
   * @param uri the URL of the image
   * @return the image, or an empty result
   */
  Optional<Image> read(URI uri) {
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    try {
      return switch (scheme) {
        case "http", "https" -> decode(readRemote(uri));
        case "data" -> decode(dataOf(uri.getRawSchemeSpecificPart()));
        default -> Optional.empty();
      };
    } catch (IOException | RuntimeException e) {
      return Optional.empty();
    }
  }

  private byte[] readRemote(URI first) throws IOException {
    URI uri = first;
    for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
      if (!(uri.toURL().openConnection() instanceof HttpURLConnection connection)) {
        throw new IOException("Not an HTTP connection.");
      }
      connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
      connection.setReadTimeout(READ_TIMEOUT_MILLIS);
      // The loader examines each redirection itself.
      connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("Accept", "image/*");
      try {
        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_OK) {
          if (connection.getContentLengthLong() > maxBytes) {
            throw new IOException("The image is too large.");
          }
          try (InputStream in = connection.getInputStream()) {
            return readLimited(in);
          }
        }
        if (status < 300 || status > 399 || status == HttpURLConnection.HTTP_NOT_MODIFIED) {
          throw new IOException("The server answered with the status " + status + ".");
        }
        uri = redirection(uri, connection.getHeaderField("Location"));
      } finally {
        connection.disconnect();
      }
    }
    throw new IOException("Too many redirections.");
  }

  /** Gets the target of a redirection, if the loader can go there. */
  private static URI redirection(URI from, String location) throws IOException {
    URI target =
        MediaUrls.safe(location)
            .map(from::resolve)
            .orElseThrow(() -> new IOException("The redirection is not safe."));
    String scheme = target.getScheme() == null ? "" : target.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new IOException("The redirection is not to an HTTP URL.");
    }
    return target;
  }

  private byte[] readLimited(InputStream in) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] buffer = new byte[16 * 1024];
    for (int count = in.read(buffer); count >= 0; count = in.read(buffer)) {
      if (bytes.size() + count > maxBytes) {
        throw new IOException("The image is too large.");
      }
      bytes.write(buffer, 0, count);
    }
    return bytes.toByteArray();
  }

  /** Decodes the first image of the data, if it does not have too many pixels. */
  private Optional<Image> decode(byte[] data) throws IOException {
    // A stream in memory makes no temporary file.
    try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) {
        return Optional.empty();
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(in, true, true);
        long width = reader.getWidth(0);
        long height = reader.getHeight(0);
        if (width <= 0 || height <= 0 || width * height > maxPixels) {
          return Optional.empty();
        }
        return Optional.ofNullable(reader.read(0));
      } finally {
        reader.dispose();
      }
    }
  }

  /** Gets the bytes of a {@code data} URL from the part after the scheme. */
  private byte[] dataOf(String content) throws IOException {
    int comma = content.indexOf(',');
    if (comma < 0) {
      throw new IllegalArgumentException("A data URL must contain a comma.");
    }
    // The decoded data is not longer than the text.
    if (content.length() - comma > 3L * maxBytes) {
      throw new IOException("The image is too large.");
    }
    String header = content.substring(0, comma).toLowerCase(Locale.ROOT);
    String data = content.substring(comma + 1);
    byte[] bytes;
    if (header.endsWith(BASE64_MARK)) {
      bytes = Base64.getMimeDecoder().decode(data);
    } else {
      // ISO 8859-1 gives each percent-encoded byte back as the same byte.
      bytes =
          URLDecoder.decode(data.replace("+", "%2B"), StandardCharsets.ISO_8859_1)
              .getBytes(StandardCharsets.ISO_8859_1);
    }
    if (bytes.length > maxBytes) {
      throw new IOException("The image is too large.");
    }
    return bytes;
  }
}
