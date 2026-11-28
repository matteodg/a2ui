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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.schema.v1_0.SchemaDocuments;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The behavior that the shared suite {@code core/common_types.yaml} does not examine. */
class CommonTypeValidatorTest {
  @Test
  void aValueOfTheTypeIsAccepted() {
    CommonTypeValidator.validate("DynamicString", "plain");
    CommonTypeValidator.validate("DynamicString", Map.of("@path", "/name"));
    CommonTypeValidator.validate("DynamicBoolean", true);
    CommonTypeValidator.validate("ChildList", List.of("a", "b"));
    CommonTypeValidator.validate("DynamicValue", Map.of("label", "x"));
  }

  @Test
  void aValueThatIsNotOfTheTypeIsRejected() {
    assertThrows(A2uiValidationError.class, () -> CommonTypeValidator.validate("DynamicString", 5));
    assertThrows(A2uiValidationError.class, () -> CommonTypeValidator.validate("ChildList", "a"));
    assertThrows(
        A2uiValidationError.class, () -> CommonTypeValidator.validate("DynamicString", null));
  }

  @Test
  void theMessageNamesTheTypeAndEachProblem() {
    A2uiValidationError error =
        assertThrows(
            A2uiValidationError.class,
            () ->
                CommonTypeValidator.validate(
                    "Surface", Map.of("component", "Text", "child", "main")));

    String message = error.getMessage();
    assertTrue(message.startsWith("Not a valid Surface: "), message);
    assertTrue(message.contains("/component: "), message);
    assertTrue(message.contains("; "), message);
    assertTrue(message.contains("/child: "), message);
  }

  @Test
  void aFunctionCallIsExaminedAsAnEnvelopeOnly() {
    CommonTypeValidator.validate("FunctionCall", Map.of("@call", "anyName"));
    CommonTypeValidator.validate(
        "DynamicString", Map.of("@call", "anyName", "catalogId", "urn:x", "args", Map.of()));
    assertThrows(
        A2uiValidationError.class,
        () -> CommonTypeValidator.validate("FunctionCall", Map.of("@call", "f", "args", 1)));
    assertThrows(
        A2uiValidationError.class,
        () -> CommonTypeValidator.validate("DynamicString", Map.of("@call", 1)));
  }

  @Test
  void aNameThatIsNotACommonTypeIsRejected() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class, () -> CommonTypeValidator.validate("NoSuchType", 1));

    assertEquals("There is no v1.0 common type named 'NoSuchType'.", error.getMessage());
  }

  @Test
  void aValueThatIsNotJsonIsRejected() {
    A2uiValidationError error =
        assertThrows(
            A2uiValidationError.class,
            () -> CommonTypeValidator.validate("DynamicValue", new Object()));

    assertTrue(error.getMessage().startsWith("The value to validate is not JSON: "));
  }

  @Test
  void theNamesAreTheDefinitionsOfTheSpecification() {
    Object published = SchemaDocuments.commonTypes().get("$defs");

    assertEquals(((Map<?, ?>) published).keySet(), CommonTypeValidator.definitionNames());
    assertTrue(CommonTypeValidator.definitionNames().contains("Action"));
    assertThrows(
        UnsupportedOperationException.class,
        () -> CommonTypeValidator.definitionNames().remove("Action"));
  }
}
