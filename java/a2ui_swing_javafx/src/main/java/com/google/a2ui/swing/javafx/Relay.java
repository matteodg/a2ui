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

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Gives the events of a player from the thread of the player to a different thread. The JavaFX
 * engine uses it to give the events of the JavaFX application thread to the event dispatch thread.
 *
 * <p>Each event is one task of the executor. The relay never waits for the task. After {@link
 * #close()}, the target gets no event, also not an event that was already a task of the executor.
 * Each method is safe on each thread.
 */
final class Relay implements MediaEngine.Listener {
  private final Executor executor;
  private final MediaEngine.Listener target;
  private volatile boolean closed;

  /**
   * Makes a relay.
   *
   * @param executor runs a task on the thread of the target, in a later event
   * @param target gets the events
   */
  Relay(Executor executor, MediaEngine.Listener target) {
    this.executor = Objects.requireNonNull(executor, "executor");
    this.target = Objects.requireNonNull(target, "target");
  }

  /** Stops the events. Call it on the thread of the target. */
  void close() {
    closed = true;
  }

  private void post(Runnable event) {
    if (closed) {
      return;
    }
    executor.execute(() -> {
      if (!closed) {
        event.run();
      }
    });
  }

  @Override
  public void ready(double durationSeconds) {
    post(() -> target.ready(durationSeconds));
  }

  @Override
  public void playing() {
    post(target::playing);
  }

  @Override
  public void paused() {
    post(target::paused);
  }

  @Override
  public void time(double seconds) {
    post(() -> target.time(seconds));
  }

  @Override
  public void ended() {
    post(target::ended);
  }

  @Override
  public void failed(String message) {
    post(() -> target.failed(message));
  }
}
