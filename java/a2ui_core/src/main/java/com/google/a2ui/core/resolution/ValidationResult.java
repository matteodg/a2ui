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

import com.google.a2ui.core.common.Coercion;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of one check rule of a component.
 *
 * @param valid true if the check passed
 * @param code the reason in a form for programs. The result is empty if the condition gave none.
 * @param message the text for the user. The result is empty if the result has no text. A failed
 *     result that the resolver makes always has a text.
 * @param severity the importance of a failure
 */
public record ValidationResult(
    boolean valid, Optional<String> code, Optional<String> message, ValidationSeverity severity) {
  /** The message of a failed check that has no other message. */
  private static final String DEFAULT_MESSAGE = "Validation failed";

  /** Makes a result. */
  public ValidationResult {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(severity, "severity");
  }

  /**
   * Writes the result as the validation result object of the protocol.
   *
   * @return the JSON object. The map is read-only.
   */
  public Map<String, Object> toJson() {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("valid", valid);
    code.ifPresent(text -> json.put("code", text));
    message.ifPresent(text -> json.put("message", text));
    json.put("severity", severity.wireValue());
    return Collections.unmodifiableMap(json);
  }

  /**
   * Reads the value of the condition of a check rule.
   *
   * <p>A validation result object is an object that has a boolean {@code valid}. The method reads
   * each other value as a boolean. See {@link Coercion#toBoolean(Object)}.
   *
   * <p>The message of the result is the first of these that is available:
   *
   * <ul>
   *   <li>For a condition that is a data path: the message of the object at the path, then the
   *       message of the rule. The agent or the application wrote the object, and its message
   *       describes the problem.
   *   <li>For each other condition: the message of the rule, then the message of the object. Thus
   *       the message of the rule replaces the default message of a function.
   *   <li>A default message.
   * </ul>
   *
   * @param outcome the resolved value of the condition, or null
   * @param ruleMessage the {@code message} of the rule, or null if the rule has none
   * @param pathBound true if the condition is a data path
   */
  static ValidationResult of(Object outcome, String ruleMessage, boolean pathBound) {
    if (!(outcome instanceof Map<?, ?> object && object.get("valid") instanceof Boolean valid)) {
      return new ValidationResult(
          Coercion.toBoolean(outcome),
          Optional.empty(),
          Optional.of(ruleMessage != null ? ruleMessage : DEFAULT_MESSAGE),
          ValidationSeverity.ERROR);
    }
    String own = object.get("message") instanceof String text && !text.isEmpty() ? text : null;
    String first = pathBound ? own : ruleMessage;
    String second = pathBound ? ruleMessage : own;
    String message = first != null ? first : second != null ? second : DEFAULT_MESSAGE;
    return new ValidationResult(
        valid,
        object.get("code") instanceof String code ? Optional.of(code) : Optional.empty(),
        Optional.of(message),
        ValidationSeverity.fromWire(object.get("severity")).orElse(ValidationSeverity.ERROR));
  }

  /**
   * Makes the result of a check whose condition the resolver cannot evaluate. Such a check fails.
   *
   * @param ruleMessage the {@code message} of the rule, or null if the rule has none
   * @param error the failure of the evaluation
   */
  static ValidationResult failed(String ruleMessage, RuntimeException error) {
    String message = ruleMessage != null ? ruleMessage : error.getMessage();
    return new ValidationResult(
        false,
        Optional.empty(),
        Optional.of(message != null ? message : DEFAULT_MESSAGE),
        ValidationSeverity.ERROR);
  }
}
