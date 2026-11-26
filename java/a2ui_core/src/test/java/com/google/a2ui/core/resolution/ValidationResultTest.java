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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.exceptions.A2uiExpressionError;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ValidationResultTest {
  private static Map<String, Object> object(Object... keysAndValues) {
    Map<String, Object> object = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      object.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return object;
  }

  @Test
  void aBooleanBecomesAResultWithTheMessageOfTheRuleAndTheSeverityError() {
    ValidationResult failed = ValidationResult.of(false, "Must be 18 or older.", false);
    ValidationResult passed = ValidationResult.of(true, "Must be 18 or older.", false);

    assertEquals(
        new ValidationResult(
            false, Optional.empty(), Optional.of("Must be 18 or older."), ValidationSeverity.ERROR),
        failed);
    assertTrue(passed.valid());
  }

  @Test
  void aValueThatIsNoResultObjectIsReadAsABoolean() {
    assertTrue(ValidationResult.of("TRUE", null, true).valid());
    assertTrue(ValidationResult.of(2L, null, true).valid());
    assertFalse(ValidationResult.of("yes", null, true).valid());
    assertFalse(ValidationResult.of(null, null, true).valid());
    assertFalse(ValidationResult.of(Map.of("valid", "true"), null, true).valid());
    assertFalse(ValidationResult.of(List.of(true), null, false).valid());
  }

  @Test
  void aResultObjectGivesItsCodeItsMessageAndItsSeverity() {
    Map<String, Object> outcome =
        object("valid", false, "code", "WEAK", "message", "Too weak.", "severity", "warning");

    ValidationResult result = ValidationResult.of(outcome, null, false);

    assertEquals(
        new ValidationResult(
            false, Optional.of("WEAK"), Optional.of("Too weak."), ValidationSeverity.WARNING),
        result);
  }

  @Test
  void theSeverityIsErrorIfTheObjectGivesNoneOrAValueThatIsNotCorrect() {
    assertEquals(
        ValidationSeverity.ERROR,
        ValidationResult.of(object("valid", false), null, false).severity());
    assertEquals(
        ValidationSeverity.ERROR,
        ValidationResult.of(object("valid", false, "severity", "fatal"), null, false).severity());
    assertEquals(
        ValidationSeverity.INFO,
        ValidationResult.of(object("valid", false, "severity", "info"), null, false).severity());
  }

  @Test
  void theMessageOfTheRuleReplacesTheMessageOfAFunctionResult() {
    Map<String, Object> outcome = object("valid", false, "code", "E", "message", "From function.");

    assertEquals(
        Optional.of("From rule."), ValidationResult.of(outcome, "From rule.", false).message());
    assertEquals(
        Optional.of("From function."), ValidationResult.of(outcome, null, false).message());
  }

  @Test
  void theMessageOfAnObjectInTheDataReplacesTheMessageOfTheRule() {
    Map<String, Object> withMessage = object("valid", false, "message", "From data.");
    Map<String, Object> withNone = object("valid", false, "message", "");

    assertEquals(
        Optional.of("From data."), ValidationResult.of(withMessage, "From rule.", true).message());
    assertEquals(
        Optional.of("From rule."), ValidationResult.of(withNone, "From rule.", true).message());
  }

  @Test
  void aResultWithNoOtherMessageHasTheDefaultMessage() {
    assertEquals(
        Optional.of("Validation failed"), ValidationResult.of(false, null, false).message());
    assertEquals(
        Optional.of("Validation failed"),
        ValidationResult.of(object("valid", false), null, true).message());
  }

  @Test
  void aConditionThatCannotBeEvaluatedFails() {
    A2uiExpressionError error = new A2uiExpressionError("Unknown function 'x'.");

    ValidationResult withRule = ValidationResult.failed("Fix it.", error);
    ValidationResult withNone = ValidationResult.failed(null, error);

    assertFalse(withRule.valid());
    assertEquals(Optional.of("Fix it."), withRule.message());
    assertEquals(Optional.of("Unknown function 'x'."), withNone.message());
    assertEquals(ValidationSeverity.ERROR, withNone.severity());
  }

  @Test
  void toJsonWritesOnlyTheMembersThatTheResultHas() {
    ValidationResult full =
        new ValidationResult(
            false, Optional.of("HINT"), Optional.of("Try again."), ValidationSeverity.INFO);
    ValidationResult small =
        new ValidationResult(true, Optional.empty(), Optional.empty(), ValidationSeverity.ERROR);

    assertEquals(
        object("valid", false, "code", "HINT", "message", "Try again.", "severity", "info"),
        full.toJson());
    assertEquals(
        List.of("valid", "code", "message", "severity"), List.copyOf(full.toJson().keySet()));
    assertEquals(object("valid", true, "severity", "error"), small.toJson());
    assertThrows(UnsupportedOperationException.class, () -> small.toJson().put("x", 1));
  }

  @Test
  void aSeverityHasTheTextOfTheProtocol() {
    assertEquals("warning", ValidationSeverity.WARNING.wireValue());
    assertEquals(Optional.of(ValidationSeverity.INFO), ValidationSeverity.fromWire("info"));
    assertEquals(Optional.empty(), ValidationSeverity.fromWire("INFO"));
    assertEquals(Optional.empty(), ValidationSeverity.fromWire(null));
    assertEquals(Optional.empty(), ValidationSeverity.fromWire(3L));
  }
}
