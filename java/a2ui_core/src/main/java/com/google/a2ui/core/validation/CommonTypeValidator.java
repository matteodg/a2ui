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

package com.google.a2ui.core.validation;

import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.Json;
import com.google.a2ui.core.internal.JsonSchemaEngine;
import com.google.a2ui.core.internal.SchemaReferences;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Examines a value against one of the common types of protocol v1.0, for example {@code
 * DynamicString}, {@code Action} or {@code ChildList}. The validator uses no catalog.
 *
 * <p>Thus it examines only the envelope of a function call: {@code @call}, and optionally {@code
 * catalogId} and {@code args}. {@link PayloadValidator} examines a call against the function of a
 * catalog.
 *
 * <p>The methods of this class are safe to call from more than one thread.
 */
public final class CommonTypeValidator {
  private CommonTypeValidator() {}

  /**
   * Gets the names of the common types.
   *
   * @return the names, in the sequence of the specification. The set is read-only.
   */
  public static Set<String> definitionNames() {
    return Schemas.DEFINITIONS.keySet();
  }

  /**
   * Examines a value against a common type.
   *
   * @param definition the name of the common type, for example {@code DynamicString}
   * @param value the value to examine, as plain JSON values. It can be null.
   * @throws IllegalArgumentException if no common type has the given name
   * @throws A2uiValidationError if the value is not a correct value of the type. The message lists
   *     each problem.
   */
  public static void validate(String definition, Object value) {
    if (!Schemas.DEFINITIONS.containsKey(definition)) {
      throw new IllegalArgumentException(
          "There is no v1.0 common type named '" + definition + "'.");
    }
    Object frozen;
    try {
      frozen = Json.freeze(value);
    } catch (IllegalArgumentException e) {
      throw new A2uiValidationError("The value to validate is not JSON: " + e.getMessage(), e);
    }
    List<String> errors = Schemas.ENGINE.errors("/$defs/" + Json.escapePointer(definition), frozen);
    if (!errors.isEmpty()) {
      throw new A2uiValidationError("Not a valid " + definition + ": " + String.join("; ", errors));
    }
  }

  /** Makes the schemas and the engine when the JVM initializes the class, at the first use. */
  private static final class Schemas {
    static final Map<String, Object> DEFINITIONS = SchemaReferences.validationTypes();
    static final JsonSchemaEngine ENGINE = new JsonSchemaEngine(Map.of("$defs", DEFINITIONS));
  }
}
