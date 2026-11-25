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

package com.google.a2ui.core.catalog;

import java.util.Optional;

/** The parties that can call a catalog function. */
public enum AllowedCallers {
  /** Only the renderer: a property binding, a check or an action. This is the default. */
  RENDERER_ONLY("rendererOnly"),

  /** Only the agent, through {@code callRendererFunction}. */
  AGENT_ONLY("agentOnly"),

  /** The renderer and the agent. */
  RENDERER_OR_AGENT("rendererOrAgent");

  private final String wireValue;

  AllowedCallers(String wireValue) {
    this.wireValue = wireValue;
  }

  /**
   * Gets the text that catalog documents use for this value.
   *
   * @return the text of the value
   */
  public String wireValue() {
    return wireValue;
  }

  /**
   * Reads a value from the text of a catalog document.
   *
   * @param wireValue the text to read, or null
   * @return the value, or an empty result if the text is not one of the values
   */
  public static Optional<AllowedCallers> fromWire(String wireValue) {
    for (AllowedCallers callers : values()) {
      if (callers.wireValue.equals(wireValue)) {
        return Optional.of(callers);
      }
    }
    return Optional.empty();
  }
}
