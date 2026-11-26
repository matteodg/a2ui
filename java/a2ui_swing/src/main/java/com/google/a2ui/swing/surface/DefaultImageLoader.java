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
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * The default {@link ImageLoader}. It reads {@code http}, {@code https} and {@code data} URLs with
 * {@code ImageIO}, on a thread that is not the event dispatch thread. It has no cache.
 */
final class DefaultImageLoader implements ImageLoader {
  private static final int TIMEOUT_MILLIS = 30_000;
  private static final String BASE64_MARK = ";base64";

  @Override
  public Subscription load(URI uri, Consumer<Optional<Image>> onResult) {
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(onResult, "onResult");
    AtomicBoolean cancelled = new AtomicBoolean();
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
    return () -> cancelled.set(true);
  }

  /** Reads an image. Each failure gives an empty result, because a surface shows no image then. */
  static Optional<Image> read(URI uri) {
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    try {
      return switch (scheme) {
        case "http", "https" -> readRemote(uri);
        case "data" -> decode(new ByteArrayInputStream(dataOf(uri.getRawSchemeSpecificPart())));
        default -> Optional.empty();
      };
    } catch (IOException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static Optional<Image> readRemote(URI uri) throws IOException {
    URLConnection connection = uri.toURL().openConnection();
    connection.setConnectTimeout(TIMEOUT_MILLIS);
    connection.setReadTimeout(TIMEOUT_MILLIS);
    try (InputStream in = connection.getInputStream()) {
      return decode(in);
    }
  }

  private static Optional<Image> decode(InputStream in) throws IOException {
    return Optional.ofNullable(ImageIO.read(in));
  }

  /** Gets the bytes of a {@code data} URL from the part after the scheme. */
  private static byte[] dataOf(String content) {
    int comma = content.indexOf(',');
    if (comma < 0) {
      throw new IllegalArgumentException("A data URL must contain a comma.");
    }
    String header = content.substring(0, comma).toLowerCase(Locale.ROOT);
    String data = content.substring(comma + 1);
    if (header.endsWith(BASE64_MARK)) {
      return Base64.getMimeDecoder().decode(data);
    }
    // ISO 8859-1 gives each percent-encoded byte back as the same byte.
    return URLDecoder.decode(data.replace("+", "%2B"), StandardCharsets.ISO_8859_1)
        .getBytes(StandardCharsets.ISO_8859_1);
  }
}
