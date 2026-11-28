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

import static com.google.a2ui.core.conformance.JsonValues.asList;
import static com.google.a2ui.core.conformance.JsonValues.asObject;
import static com.google.a2ui.core.conformance.JsonValues.asString;
import static com.google.a2ui.core.conformance.JsonValues.assertJsonEquals;

import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiDataError;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.exceptions.A2uiIntegrityError;
import com.google.a2ui.core.exceptions.A2uiParseError;
import com.google.a2ui.core.exceptions.A2uiRecursionError;
import com.google.a2ui.core.exceptions.A2uiStateError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
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

  private static final Set<String> EXPECT_ERROR_KEYS =
      Set.of(
          "category",
          "code",
          "details",
          "functionCallId",
          "message",
          "path",
          "rendererError",
          "surfaceId");

  private static final Set<String> DETAIL_KEYS = Set.of("code", "message", "path", "surfaceId");

  private static final Set<String> RENDERER_ERROR_KEYS = Set.of("error", "version");

  private static final Set<String> RENDERER_ERROR_BODY_KEYS =
      Set.of("code", "functionCallId", "message", "path", "surfaceId");

  /** The protocol error code of a cycle in a component graph. */
  private static final String RECURSION_CODE = "RECURSION_LIMIT_EXCEEDED";

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
   * Runs a block and fails if it does not throw the expected error. The expectation cannot examine
   * the message for the agent. See the other overload.
   */
  static void assertRaises(
      Object expectError,
      String where,
      Map<String, List<Class<? extends A2uiError>>> categories,
      Block block)
      throws Exception {
    assertRaises(expectError, where, categories, () -> null, block);
  }

  /**
   * Runs a block and fails if it does not throw the expected error.
   *
   * <p>The error must be an instance of a class for the {@code category}. An expectation with no
   * category accepts each SDK error. The message of the error must contain the text {@code
   * message}, or a match for {@code message} as a regular expression.
   *
   * <p>The expectation can also give the {@code surfaceId}, the {@code functionCallId}, the {@code
   * path}, the {@code code} and some {@code details} of the error, and the {@code rendererError}:
   * the {@code error} message that the SDK sends to the agent.
   *
   * @param expectError the expectation of the case: an object, or only the message as a string
   * @param where the location for failure messages
   * @param categories the classes that replace the default class for some categories
   * @param outbound gets the last message that the SDK sent to the agent while the block ran, or
   *     null if it sent none
   * @param block the code to run
   */
  static void assertRaises(
      Object expectError,
      String where,
      Map<String, List<Class<? extends A2uiError>>> categories,
      Supplier<Map<String, Object>> outbound,
      Block block)
      throws Exception {
    Map<String, Object> expected =
        expectError instanceof String message
            ? Map.of("message", message)
            : asObject(expectError, "expectError in " + where);
    KnownKeys.assertKnownKeys(expected, EXPECT_ERROR_KEYS, "expectError in " + where);
    String category =
        expected.get("category") == null
            ? null
            : asString(expected.get("category"), "expectError.category in " + where);
    List<Class<? extends A2uiError>> errorClasses;
    if (category == null) {
      errorClasses = List.of(A2uiError.class);
    } else if (categories.containsKey(category)) {
      errorClasses = categories.get(category);
    } else if (ERROR_CATEGORIES.containsKey(category)) {
      errorClasses = List.of(ERROR_CATEGORIES.get(category));
    } else {
      throw new AssertionError("Unknown error category '" + category + "' in " + where);
    }

    A2uiError caught = null;
    try {
      block.run();
    } catch (A2uiError e) {
      caught = e;
    }
    if (caught == null) {
      throw new AssertionError(where + ": expected " + category + " but nothing was thrown");
    }
    A2uiError thrown = caught;
    // A cycle is an integrity failure that the protocol reports with the recursion code. The suites
    // name a cycle with one or the other category, and a class has only one parent. Thus the two
    // categories accept a cycle.
    boolean isCycle =
        thrown instanceof A2uiIntegrityError
            && thrown.errorCode().filter(RECURSION_CODE::equals).isPresent();
    if (errorClasses.stream().noneMatch(errorClass -> errorClass.isInstance(thrown))
        && !("RecursionError".equals(category) && isCycle)) {
      throw new AssertionError(
          where
              + ": expected "
              + category
              + " ("
              + errorClasses.stream().map(Class::getSimpleName).toList()
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
    assertErrorContext(expected, thrown, where);
    if (expected.get("rendererError") != null) {
      assertRendererError(expected.get("rendererError"), outbound.get(), thrown, where);
    }
  }

  /** Examines the surface, the function call, the path, the code and the details of an error. */
  private static void assertErrorContext(
      Map<String, Object> expected, A2uiError thrown, String where) {
    if (expected.get("surfaceId") != null) {
      assertJsonEquals(
          expected.get("surfaceId"),
          thrown.surfaceId().orElse(null),
          "error surfaceId in " + where);
    }
    if (expected.get("path") != null) {
      assertJsonEquals(expected.get("path"), thrown.path().orElse(null), "error path in " + where);
    }
    if (expected.get("functionCallId") != null) {
      assertJsonEquals(
          expected.get("functionCallId"),
          thrown.functionCallId().orElse(null),
          "error functionCallId in " + where);
    }
    if (expected.get("code") != null) {
      assertJsonEquals(
          expected.get("code"), thrown.errorCode().orElse(null), "error code in " + where);
    }
    if (expected.get("details") == null) {
      return;
    }
    for (Object raw : asList(expected.get("details"), "expectError.details in " + where)) {
      Map<String, Object> detail = asObject(raw, "expectError.details entry in " + where);
      KnownKeys.assertKnownKeys(detail, DETAIL_KEYS, "expectError.details entry in " + where);
      // An expected detail gives some values. One detail of the error must have each of them.
      boolean found =
          thrown.details().stream()
              .anyMatch(actual ->
                  (detail.get("path") == null || detail.get("path").equals(actual.path()))
                      && (detail.get("code") == null || detail.get("code").equals(actual.code()))
                      && (detail.get("message") == null
                          || actual
                              .message()
                              .contains(asString(detail.get("message"), "detail message"))));
      if (!found) {
        throw new AssertionError(
            where + ": no detail " + detail + " in " + thrown.details(), thrown);
      }
      if (detail.get("surfaceId") != null) {
        assertJsonEquals(
            detail.get("surfaceId"),
            thrown.surfaceId().orElse(null),
            "detail surfaceId in " + where);
      }
    }
  }

  /** Examines the {@code error} message that the SDK sent to the agent. */
  private static void assertRendererError(
      Object rendererError, Map<String, Object> sent, A2uiError thrown, String where) {
    if (sent == null) {
      throw new AssertionError(
          where + ": expected an error message to the agent, but the SDK sent none", thrown);
    }
    Map<String, Object> wanted = asObject(rendererError, "rendererError in " + where);
    KnownKeys.assertKnownKeys(wanted, RENDERER_ERROR_KEYS, "rendererError in " + where);
    // The suites always give the two keys. Thus an absent key cannot hide a difference.
    assertJsonEquals(
        wanted.get("version"), sent.get("version"), "rendererError.version in " + where);
    Map<String, Object> wantedError =
        asObject(wanted.get("error"), "rendererError.error in " + where);
    Map<String, Object> sentError = asObject(sent.get("error"), "the error message of " + where);
    KnownKeys.assertKnownKeys(
        wantedError, RENDERER_ERROR_BODY_KEYS, "rendererError.error in " + where);
    wantedError.forEach((key, value) -> {
      if (key.equals("message")) {
        String text = asString(sentError.get("message"), "the sent message in " + where);
        if (!text.contains(asString(value, "rendererError.error.message in " + where))) {
          throw new AssertionError(
              where + ": the sent message '" + text + "' does not contain '" + value + "'", thrown);
        }
      } else {
        assertJsonEquals(value, sentError.get(key), "rendererError.error." + key + " in " + where);
      }
    });
    // An error message is about a surface or about a function call, not about the two.
    String other = wantedError.containsKey("functionCallId") ? "surfaceId" : "functionCallId";
    if (!wantedError.containsKey(other) && sentError.containsKey(other)) {
      throw new AssertionError(
          where + ": the sent error message has an unexpected '" + other + "': " + sentError,
          thrown);
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
