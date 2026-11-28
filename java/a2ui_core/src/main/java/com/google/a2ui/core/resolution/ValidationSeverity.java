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

import java.util.Optional;

/** The importance of a check that failed. Only {@link #ERROR} makes a component not valid. */
public enum ValidationSeverity {
  /** The value is not correct. The component is not valid. */
  ERROR("error"),

  /** The value is permitted, but the user must know about a risk. */
  WARNING("warning"),

  /** The value is permitted. The result gives a hint to the user. */
  INFO("info");

  private final String wireValue;

  ValidationSeverity(String wireValue) {
    this.wireValue = wireValue;
  }

  /**
   * Gets the text that the protocol uses for this value.
   *
   * @return the text of the value
   */
  public String wireValue() {
    return wireValue;
  }

  /**
   * Reads a value from the text of a validation result object.
   *
   * @param wireValue the value to read, or null
   * @return the severity, or an empty result if the value is not the text of a severity
   */
  public static Optional<ValidationSeverity> fromWire(Object wireValue) {
    for (ValidationSeverity severity : values()) {
      if (severity.wireValue.equals(wireValue)) {
        return Optional.of(severity);
      }
    }
    return Optional.empty();
  }
}
