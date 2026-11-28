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

package com.google.a2ui.core.resolution;

import com.google.a2ui.core.exceptions.A2uiError;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A value that the core does not know at this time, because it waits for the agent. An evaluation
 * returns this object in place of the value. Code outside this package does not get it.
 *
 * <p>An instance is not thread-safe. Use it on the thread that owns its surface.
 */
final class Later {
  /** The condition of a value that waits. */
  enum State {
    /** The agent did not answer at this time. */
    WAITING,
    /** The value is known. */
    VALUE,
    /** The evaluation failed. */
    FAILED,
    /** The call was withdrawn. There is no value and no error to report. */
    CANCELLED,
  }

  /**
   * The value of a binding that waits. This object does not settle: the resolver resolves the
   * binding again from the start when the agent answers.
   */
  static final Later PENDING = new Later();

  private State state = State.WAITING;
  private Object value;
  private A2uiError error;
  private List<Consumer<Later>> callbacks = new ArrayList<>();

  State state() {
    return state;
  }

  /** Gets the value. It is null unless the state is {@link State#VALUE}. */
  Object value() {
    return value;
  }

  /** Gets the cause of the failure. It is null unless the state is {@link State#FAILED}. */
  A2uiError error() {
    return error;
  }

  boolean isSettled() {
    return state != State.WAITING;
  }

  /**
   * Calls a function with this object when this object settles. If it is settled, the function runs
   * immediately.
   */
  void then(Consumer<Later> callback) {
    if (callbacks == null) {
      callback.accept(this);
    } else {
      callbacks.add(callback);
    }
  }

  void succeed(Object result) {
    if (!isSettled()) {
      value = result;
      settle(State.VALUE);
    }
  }

  void fail(A2uiError cause) {
    if (!isSettled()) {
      error = cause;
      settle(State.FAILED);
    }
  }

  void cancel() {
    if (!isSettled()) {
      settle(State.CANCELLED);
    }
  }

  /**
   * Settles this object as a result settles. If the result is not a {@code Later}, it is the value.
   */
  void follow(Object result) {
    if (!(result instanceof Later other)) {
      succeed(result);
      return;
    }
    other.then(end -> {
      switch (end.state) {
        case FAILED -> fail(end.error);
        case CANCELLED -> cancel();
        default -> succeed(end.value);
      }
    });
  }

  private void settle(State end) {
    state = end;
    List<Consumer<Later>> waiting = callbacks;
    callbacks = null;
    waiting.forEach(callback -> callback.accept(this));
  }
}
