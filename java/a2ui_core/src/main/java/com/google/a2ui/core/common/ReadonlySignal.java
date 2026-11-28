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

/**
 * A value that code can read at all times and observe for changes.
 *
 * @param <T> the type of the value
 */
public interface ReadonlySignal<T> extends EventSource<T> {
  /**
   * Gets the current value.
   *
   * @return the current value
   */
  T get();

  /**
   * Registers a listener for each subsequent change.
   *
   * <p>The signal does not call the listener with the current value. Call {@link #get()} to read
   * the current value.
   *
   * @param listener the listener to register
   * @return the subscription that removes the listener
   */
  @Override
  Subscription subscribe(EventListener<? super T> listener);
}
