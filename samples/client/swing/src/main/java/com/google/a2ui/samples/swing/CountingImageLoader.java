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

import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.swing.surface.ImageLoader;
import java.awt.Image;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * An image loader that counts the requests of a different loader. The screenshot mode uses it to
 * wait for the images of an example. The counters are safe to read from each thread.
 */
final class CountingImageLoader implements ImageLoader {
  private final ImageLoader loader;
  private final AtomicInteger pending = new AtomicInteger();
  private final AtomicInteger loaded = new AtomicInteger();
  private final AtomicInteger failed = new AtomicInteger();

  /**
   * Makes the loader.
   *
   * @param loader the loader that loads the images
   */
  CountingImageLoader(ImageLoader loader) {
    this.loader = Objects.requireNonNull(loader, "loader");
  }

  @Override
  public Subscription load(URI uri, Consumer<Optional<Image>> onResult) {
    AtomicBoolean open = new AtomicBoolean(true);
    pending.incrementAndGet();
    Subscription request =
        loader.load(
            uri,
            result -> {
              if (open.getAndSet(false)) {
                pending.decrementAndGet();
                (result.isPresent() ? loaded : failed).incrementAndGet();
              }
              onResult.accept(result);
            });
    return () -> {
      if (open.getAndSet(false)) {
        pending.decrementAndGet();
      }
      request.cancel();
    };
  }

  /**
   * Gets the number of the requests that have no result and that the caller did not cancel.
   *
   * @return the number
   */
  int pending() {
    return pending.get();
  }

  /**
   * Gets the number of the requests that gave an image.
   *
   * @return the number
   */
  int loaded() {
    return loaded.get();
  }

  /**
   * Gets the number of the requests that gave no image.
   *
   * @return the number
   */
  int failed() {
    return failed.get();
  }
}
