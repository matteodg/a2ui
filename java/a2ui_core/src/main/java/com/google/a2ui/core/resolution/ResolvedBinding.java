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

import java.util.Objects;
import java.util.Optional;

/**
 * The current value of one dynamic property of a node: a literal, the data at a path, or the result
 * of a function.
 *
 * <p>A binding is a snapshot. When the value changes, the node publishes new properties that
 * contain a new binding. This binding keeps the value that it had at its creation.
 *
 * <p>Two bindings are equal if they have the same class and equal values.
 */
public sealed class ResolvedBinding permits WritableBinding {
  private final Object value;

  /**
   * Makes a binding.
   *
   * @param value the resolved value, or null
   */
  ResolvedBinding(Object value) {
    this.value = value;
  }

  /**
   * Gets the resolved value.
   *
   * @return the value as a plain JSON value, or null if the property has no value. Maps and lists
   *     are read-only.
   */
  public final Object value() {
    return value;
  }

  /**
   * Tells if code can write the value. This is true only if the message bound the property to a
   * data path.
   *
   * @return true if this binding is a {@link WritableBinding}
   */
  public final boolean isWritable() {
    return this instanceof WritableBinding;
  }

  /**
   * Gets this binding as a binding that code can write.
   *
   * @return this binding, or an empty result if the message did not bind the property to a data
   *     path
   */
  public final Optional<WritableBinding> asWritable() {
    return this instanceof WritableBinding writable ? Optional.of(writable) : Optional.empty();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ResolvedBinding binding
        && binding.getClass() == getClass()
        && Objects.equals(binding.value, value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  @Override
  public String toString() {
    return "ResolvedBinding(" + value + ")";
  }
}
