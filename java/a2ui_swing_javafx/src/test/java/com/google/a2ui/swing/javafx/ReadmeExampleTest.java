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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.catalog.basic.SwingBasicCatalog;
import com.google.a2ui.swing.surface.A2uiFallbacks;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.A2uiSurface;
import com.google.a2ui.swing.surface.A2uiSwing;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Makes sure that the example of the README of this module compiles and operates. */
class ReadmeExampleTest {
  private final List<A2uiSurface> shown = new ArrayList<>();

  private void show(A2uiSurface surface) {
    shown.add(surface);
  }

  @Test
  void theExampleOfTheSectionUseThePlayerMakesASurface() {
    Swing.onEdt(() -> {
      A2uiServices base = A2uiServices.DEFAULT;
      A2uiServices services =
          base.withMediaPlayer(
              JavaFxMediaPlayer.builder()
                  .imageLoader(base.imageLoader())
                  .urlOpener(base.urlOpener())
                  .build());

      MessageProcessor<SwingComponentImplementation> processor =
          new MessageProcessor<>(List.of(SwingBasicCatalog.create(Locale.getDefault(), services)));
      A2uiSwing.install(processor);
      processor
          .model()
          .onSurfaceCreated()
          .subscribe(surface -> show(new A2uiSurface(surface, A2uiFallbacks.NONE, services)));

      processor.processMessages(
          Map.of(
              "version",
              "1.0",
              "createSurface",
              Map.of(
                  "surfaceId",
                  "main",
                  "catalogId",
                  SwingBasicCatalog.create().id(),
                  "components",
                  List.of(
                      Map.of(
                          "id",
                          "root",
                          "component",
                          "Video",
                          "url",
                          "https://example.com/intro.mp4")))));

      assertEquals(1, shown.size());
      assertInstanceOf(JavaFxMediaPlayer.class, services.mediaPlayer());
    });
  }
}
