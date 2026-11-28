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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;

/**
 * Shows examples in the gallery with no display and writes images of the surface column and of the
 * three columns. The images are in {@code target/snapshots/}. A person looks at them after a change
 * of the gallery or of a view. The build does not compare them.
 */
class GallerySnapshotTest {
  /**
   * Examples of each type of component: layouts, inputs, a modal, icons, images, and the
   * substitutes of the video and of the audio player. The image loader of the tests gives a
   * generated image for each URL.
   */
  private static final List<String> EXAMPLES =
      List.of(
          "scaffold-simple-login-form",
          "scaffold-complex-layout",
          "scaffold-incremental",
          "login-form",
          "markdown-text",
          "modal",
          "calendar-day",
          "contact-card",
          "flight-status",
          "recipe-card",
          "financial-data-grid",
          "live-invitation-builder",
          "movie-card",
          "music-player",
          "podcast-episode",
          "product-card",
          "user-profile",
          "stats-card");

  private GalleryPanel gallery;

  private Path snapshot(String example, JComponent component, String name) {
    Headless.onEdt(() -> assertTrue(gallery.select(example), example));
    Headless.settle(gallery.component());
    Path[] file = {null};
    Headless.onEdt(() -> file[0] = Headless.snapshot(component, name));
    assertTrue(Files.exists(file[0]), file[0].toString());
    return file[0];
  }

  @Test
  void theSurfaceColumnOfEachSelectedExamplePaints() {
    Headless.onEdt(() -> {
      gallery = new GalleryPanel(Headless.examples(), Headless.services());
      Headless.mount(gallery.component(), 1280, 800);
    });

    for (String example : EXAMPLES) {
      snapshot(example, gallery.surfaceColumn().component(), "surface-" + example);
    }
  }

  @Test
  void theThreeColumnsPaint() {
    Headless.onEdt(() -> {
      gallery = new GalleryPanel(Headless.examples(), Headless.services());
      Headless.mount(gallery.component(), 1280, 800);
    });

    snapshot("scaffold-simple-login-form", gallery.component(), "gallery-simple-login-form");
    Headless.onEdt(() -> gallery.inspector().selectTab("Data model"));
    snapshot("contact-card", gallery.component(), "gallery-contact-card");
  }
}
