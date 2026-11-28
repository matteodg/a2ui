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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.a2ui.core.common.Subscription;
import java.awt.Image;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CountingImageLoaderTest {
  private static final URI URL = URI.create("https://example.com/a.png");

  private final List<Consumer<Optional<Image>>> callbacks = new ArrayList<>();
  private final List<URI> cancelled = new ArrayList<>();
  private final CountingImageLoader loader =
      new CountingImageLoader((uri, onResult) -> {
        callbacks.add(onResult);
        return () -> cancelled.add(uri);
      });
  private final List<Optional<Image>> results = new ArrayList<>();

  @Test
  void itCountsTheRequestsThatWaitAndTheirResults() {
    loader.load(URL, results::add);
    loader.load(URL, results::add);
    assertEquals(2, loader.pending());

    callbacks.get(0).accept(Optional.of(Headless.generatedImage("a")));
    assertEquals(1, loader.pending());
    assertEquals(1, loader.loaded());

    callbacks.get(1).accept(Optional.empty());
    assertEquals(0, loader.pending());
    assertEquals(1, loader.failed());
    assertEquals(2, results.size());
  }

  @Test
  void aCancelledRequestDoesNotWait() {
    Subscription request = loader.load(URL, results::add);

    request.cancel();
    request.cancel();

    assertEquals(0, loader.pending());
    assertEquals(List.of(URL, URL), cancelled);

    // A late result does not change the counters.
    callbacks.get(0).accept(Optional.empty());
    assertEquals(0, loader.pending());
    assertEquals(0, loader.failed());
  }
}
