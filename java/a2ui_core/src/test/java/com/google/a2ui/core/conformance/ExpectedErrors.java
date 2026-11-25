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

package com.google.a2ui.core.conformance;

import static com.google.a2ui.core.conformance.JsonValues.asObject;
import static com.google.a2ui.core.conformance.JsonValues.asString;

import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiDataError;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.exceptions.A2uiIntegrityError;
import com.google.a2ui.core.exceptions.A2uiParseError;
import com.google.a2ui.core.exceptions.A2uiRecursionError;
import com.google.a2ui.core.exceptions.A2uiStateError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** The error expectations of the conformance cases. */
final class ExpectedErrors {
  /** The most specific SDK error class for each {@code expectError.category}. */
  static final Map<String, Class<? extends A2uiError>> ERROR_CATEGORIES =
      Map.of(
          "ParseError", A2uiParseError.class,
          "ValidationError", A2uiValidationError.class,
          "IntegrityError", A2uiIntegrityError.class,
          "RecursionError", A2uiRecursionError.class,
          "CatalogError", A2uiCatalogError.class,
          "DataError", A2uiDataError.class,
          "ExpressionError", A2uiExpressionError.class,
          "StateError", A2uiStateError.class);

  private static final Set<String> EXPECT_ERROR_KEYS = Set.of("category", "message");

  private ExpectedErrors() {}

  /** A block of code that can throw an exception. */
  @FunctionalInterface
  interface Block {
    void run() throws Exception;
  }

  /** Runs a block and fails if it does not throw the expected error. See the other overload. */
  static void assertRaises(Object expectError, String where, Block block) throws Exception {
    assertRaises(expectError, where, Map.of(), block);
  }

  /**
   * Runs a block and fails if it does not throw the expected error.
   *
   * <p>The error must be an instance of the class for the {@code category}. Its message must
   * contain the text {@code message}, or a match for {@code message} as a regular expression.
   *
   * @param expectError the expectation of the case
   * @param where the location for failure messages
   * @param categories the classes that replace the default class for some categories
   * @param block the code to run
   */
  static void assertRaises(
      Object expectError,
      String where,
      Map<String, Class<? extends A2uiError>> categories,
      Block block)
      throws Exception {
    Map<String, Object> expected = asObject(expectError, "expectError in " + where);
    KnownKeys.assertKnownKeys(expected, EXPECT_ERROR_KEYS, "expectError in " + where);
    String category = asString(expected.get("category"), "expectError.category in " + where);
    Class<? extends A2uiError> errorClass =
        categories.getOrDefault(category, ERROR_CATEGORIES.get(category));
    if (errorClass == null) {
      throw new AssertionError("Unknown error category '" + category + "' in " + where);
    }

    A2uiError thrown = null;
    try {
      block.run();
    } catch (A2uiError e) {
      thrown = e;
    }
    if (thrown == null) {
      throw new AssertionError(where + ": expected " + category + " but nothing was thrown");
    }
    if (!errorClass.isInstance(thrown)) {
      throw new AssertionError(
          where
              + ": expected "
              + category
              + " ("
              + errorClass.getSimpleName()
              + ") but got "
              + thrown,
          thrown);
    }
    Object pattern = expected.get("message");
    if (pattern != null) {
      String regex = asString(pattern, "expectError.message in " + where);
      String message = thrown.getMessage() == null ? "" : thrown.getMessage();
      // Some suites write a message as plain text that is not a correct pattern, for example
      // "external $ref". Thus a message that contains the text as the suite writes it is also a
      // match.
      if (!message.contains(regex) && !Pattern.compile(regex).matcher(message).find()) {
        throw new AssertionError(
            where + ": expected a message matching /" + regex + "/ but got '" + message + "'",
            thrown);
      }
    }
  }

  /**
   * Gets the error expectation of a case or of a step. The core suites use two names for this key.
   *
   * @return the expectation, or null if there is none
   */
  static Object expectedError(Map<String, Object> fields) {
    Object snakeCase = fields.get("expect_error");
    return snakeCase != null ? snakeCase : fields.get("expectError");
  }
}
