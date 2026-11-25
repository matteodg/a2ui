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

package com.google.a2ui.core.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Controls the notifications of a group of {@link Signal} objects. */
public final class Reactive {
  private static final ThreadLocal<BatchState> STATE = ThreadLocal.withInitial(BatchState::new);

  private Reactive() {}

  /**
   * Runs a block and holds each signal notification until the block ends.
   *
   * <p>Thus a listener sees an update of many signals one time, when the update is complete. A
   * batch can contain a different batch. The notifications go out when the outermost batch ends.
   * This is also true when the block throws an exception.
   *
   * @param block the code to run
   */
  public static void batch(Runnable block) {
    Objects.requireNonNull(block, "block");
    BatchState state = STATE.get();
    state.depth++;
    try {
      block.run();
    } finally {
      state.depth--;
      if (state.depth == 0) {
        state.flush();
      }
    }
  }

  /**
   * Records that a signal changed during a batch.
   *
   * @param signal the signal that changed
   * @param previousValue the value before the change
   * @return false if no batch is open. In that condition, the signal must notify immediately.
   */
  static boolean defer(Signal<?> signal, Object previousValue) {
    BatchState state = STATE.get();
    if (state.depth == 0) {
      return false;
    }
    state.pending.putIfAbsent(signal, new Previous(previousValue));
    return true;
  }

  /**
   * The value of a signal before the batch. The record lets the map keep a null value.
   *
   * @param value the value before the first write of the batch
   */
  private record Previous(Object value) {}

  private static final class BatchState {
    private int depth;
    private final Map<Signal<?>, Previous> pending = new LinkedHashMap<>();

    private void flush() {
      RuntimeException failure = null;
      // A listener can write a signal during the flush. The batch is closed at that time, thus
      // that signal notifies immediately. The loop is a precaution for a batch that opens again.
      while (!pending.isEmpty()) {
        List<Map.Entry<Signal<?>, Previous>> entries = new ArrayList<>(pending.entrySet());
        pending.clear();
        for (Map.Entry<Signal<?>, Previous> entry : entries) {
          try {
            entry.getKey().flush(entry.getValue().value());
          } catch (RuntimeException e) {
            if (failure == null) {
              failure = e;
            }
          }
        }
      }
      if (failure != null) {
        throw failure;
      }
    }
  }
}
