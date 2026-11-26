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

import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.processing.OutboundMessageListener;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.core.validation.ValidationConfig;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.catalog.basic.SwingBasicCatalog;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.A2uiSwing;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One run of an example: a message processor with the basic catalog, and the messages of the
 * example that the processor got.
 *
 * <p>The processor accepts a reference to a component that arrives in a later message, because an
 * example can send its components in steps. A message that the processor rejects does not stop the
 * run. The session keeps the text of the failure.
 *
 * <p>Use a session only on the event dispatch thread.
 */
final class GallerySession {
  private final Example example;
  private final MessageProcessor<SwingComponentImplementation> processor;
  private final Consumer<Optional<SurfaceModel<SwingComponentImplementation>>> surfaceListener;
  private final List<String> failures = new ArrayList<>();
  private SurfaceModel<SwingComponentImplementation> surface;
  private int processed;
  private boolean disposed;

  /**
   * Makes a session that processed no message.
   *
   * @param example the example to run
   * @param services the services of the views. The function {@code openUrl} uses their URL opener.
   * @param outbound receives each message for the agent: the actions, the errors and the messages
   *     of the function calls
   * @param surfaceListener receives the surface to show when the processor makes it, and an empty
   *     result when the processor deletes it
   */
  GallerySession(
      Example example,
      A2uiServices services,
      OutboundMessageListener outbound,
      Consumer<Optional<SurfaceModel<SwingComponentImplementation>>> surfaceListener) {
    A2uiSwing.requireEventDispatchThread();
    this.example = example;
    this.surfaceListener = surfaceListener;
    this.processor =
        new MessageProcessor<>(
            List.of(SwingBasicCatalog.create(Locale.getDefault(), services)),
            ValidationConfig.RELAXED,
            outbound,
            true);
    A2uiSwing.install(processor);
    processor.model().onSurfaceCreated().subscribe(this::surfaceCreated);
    processor.model().onSurfaceDeleted().subscribe(this::surfaceDeleted);
  }

  /** The gallery shows the first surface of an example. */
  private void surfaceCreated(SurfaceModel<SwingComponentImplementation> created) {
    if (surface == null && !disposed) {
      surface = created;
      surfaceListener.accept(Optional.of(created));
    }
  }

  private void surfaceDeleted(String surfaceId) {
    if (surface != null && surface.id().equals(surfaceId) && !disposed) {
      surface = null;
      surfaceListener.accept(Optional.empty());
    }
  }

  /**
   * Gives the subsequent message of the example to the processor.
   *
   * @return false if the processor got all the messages before this call
   */
  boolean step() {
    A2uiSwing.requireEventDispatchThread();
    if (disposed || processed >= example.messages().size()) {
      return false;
    }
    Object message = example.messages().get(processed);
    processed++;
    try {
      processor.processMessages(message);
    } catch (RuntimeException e) {
      String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      failures.add("Message " + processed + ": " + reason);
    }
    return true;
  }

  /** Gives each message that the processor did not get to the processor. */
  void runAll() {
    while (step()) {
      // Each step processes one message.
    }
  }

  /**
   * Gets the example of this session.
   *
   * @return the example
   */
  Example example() {
    return example;
  }

  /**
   * Gets the processor of this session.
   *
   * @return the processor
   */
  MessageProcessor<SwingComponentImplementation> processor() {
    return processor;
  }

  /**
   * Gets the surface that the gallery shows.
   *
   * @return the first surface that the processor made, or an empty result
   */
  Optional<SurfaceModel<SwingComponentImplementation>> surface() {
    return Optional.ofNullable(surface);
  }

  /**
   * Gets the number of the messages that the processor got.
   *
   * @return the number
   */
  int processed() {
    return processed;
  }

  /**
   * Gets the failures of the messages that the processor rejected.
   *
   * @return one text for each rejected message
   */
  List<String> failures() {
    return Collections.unmodifiableList(failures);
  }

  /** Disposes the processor and its surfaces. The session tells its listener nothing more. */
  void dispose() {
    if (!disposed) {
      disposed = true;
      surface = null;
      processor.dispose();
    }
  }
}
