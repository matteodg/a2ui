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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Reads JSON text as plain Java values, and writes plain Java values as JSON text. */
final class Json {
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private Json() {}

  /**
   * Reads one JSON document.
   *
   * @param text the JSON text
   * @return a {@code Map}, a {@code List}, a {@code String}, a {@code Number}, a {@code Boolean} or
   *     null
   * @throws IllegalArgumentException if the text is not JSON
   */
  static Object parse(String text) {
    try {
      return MAPPER.readValue(text, Object.class);
    } catch (JacksonException e) {
      throw new IllegalArgumentException(e.getOriginalMessage(), e);
    }
  }

  /**
   * Writes a value as JSON text with line breaks and indentation.
   *
   * @param value a plain JSON value
   * @return the text. A value that is not JSON gives the result of its {@code toString}.
   */
  static String pretty(Object value) {
    try {
      return MAPPER
          .writerWithDefaultPrettyPrinter()
          .writeValueAsString(value)
          .replace("\r\n", "\n");
    } catch (JacksonException e) {
      return String.valueOf(value);
    }
  }
}
