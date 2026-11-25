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

/** The type of the value that a catalog function returns. */
public enum FunctionReturnType {
  /** A string. */
  STRING("string"),

  /** A number. */
  NUMBER("number"),

  /** A boolean. */
  BOOLEAN("boolean"),

  /** A list. */
  ARRAY("array"),

  /** An object. */
  OBJECT("object"),

  /**
   * A validation result: {@code {valid, message?, code?, severity?}}. Protocol v1.0 adds this type.
   */
  VALIDATION_RESULT("validationResult"),

  /** A value of each type. */
  ANY("any"),

  /** No value. */
  VOID("void");

  private final String wireValue;

  FunctionReturnType(String wireValue) {
    this.wireValue = wireValue;
  }

  /**
   * Gets the text that catalog documents use for this type.
   *
   * @return the text of the type
   */
  public String wireValue() {
    return wireValue;
  }

  /**
   * Reads a type from the text of a catalog document.
   *
   * <p>The result is {@link #ANY} for a text that this SDK does not know. A later protocol version
   * can add such a text.
   *
   * @param wireValue the text to read, or null
   * @return the type
   */
  public static FunctionReturnType fromWire(String wireValue) {
    for (FunctionReturnType type : values()) {
      if (type.wireValue.equals(wireValue)) {
        return type;
      }
    }
    return ANY;
  }
}
