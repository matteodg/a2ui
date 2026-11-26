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

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;

/** Runs test code on the event dispatch thread, because the adapter works only on that thread. */
public final class Edt {
  /** A block of test code that can throw each exception. */
  @FunctionalInterface
  public interface Block {
    /** Runs the block. */
    void run() throws Exception;
  }

  private Edt() {}

  /**
   * Runs a block on the event dispatch thread and waits for its end. A failure of the block is the
   * failure of the caller.
   */
  public static void run(Block block) {
    Throwable[] failure = {null};
    try {
      EventQueue.invokeAndWait(() -> {
        try {
          block.run();
        } catch (Throwable e) {
          failure[0] = e;
        }
      });
    } catch (InterruptedException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
    switch (failure[0]) {
      case null -> {}
      case RuntimeException e -> throw e;
      case Error e -> throw e;
      default -> throw new IllegalStateException(failure[0]);
    }
  }

  /** Waits until the event dispatch thread ran each event that is in its queue at this time. */
  public static void flush() {
    run(() -> {});
  }
}
