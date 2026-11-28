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

import java.util.Objects;

/**
 * A {@link ReadonlySignal} that code can also write.
 *
 * <p>If the new value is equal to the current value, the signal does not notify the listeners.
 *
 * <p>During {@link Reactive#batch(Runnable)}, the signal holds its notifications until the
 * outermost batch ends. If the value at the end is equal to the value before the batch, the signal
 * does not notify the listeners.
 *
 * <p>This class is not thread-safe. Only the thread that owns the model can use a signal.
 *
 * @param <T> the type of the value
 */
public final class Signal<T> implements ReadonlySignal<T> {
  private final EventNotifier<T> notifier = new EventNotifier<>();
  private T current;

  /**
   * Makes a signal.
   *
   * @param initialValue the first value of the signal
   */
  public Signal(T initialValue) {
    this.current = initialValue;
  }

  @Override
  public T get() {
    return current;
  }

  /**
   * Sets the value and notifies the listeners if the value changed.
   *
   * @param newValue the new value
   */
  public void set(T newValue) {
    if (Objects.equals(current, newValue)) {
      return;
    }
    T previous = current;
    current = newValue;
    if (!Reactive.defer(this, previous)) {
      notifier.emit(newValue);
    }
  }

  @Override
  public Subscription subscribe(EventListener<? super T> listener) {
    return notifier.subscribe(listener);
  }

  /** Removes each listener. Code can continue to read and write the value. */
  public void dispose() {
    notifier.dispose();
  }

  /**
   * Notifies the listeners at the end of a batch, if the value changed during the batch.
   *
   * @param valueBeforeBatch the value before the first write of the batch
   */
  void flush(Object valueBeforeBatch) {
    if (!Objects.equals(current, valueBeforeBatch)) {
      notifier.emit(current);
    }
  }
}
