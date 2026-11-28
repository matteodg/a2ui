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

package com.google.a2ui.core.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.internal.ErrorFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class A2uiErrorTest {
  @Test
  void eachErrorIsAnA2uiError() {
    assertInstanceOf(A2uiError.class, new A2uiValidationError("m"));
    assertInstanceOf(A2uiError.class, new A2uiCatalogError("m"));
    assertInstanceOf(A2uiError.class, new A2uiRpcError("m"));
    assertInstanceOf(A2uiError.class, new A2uiDataError("m"));
    assertInstanceOf(A2uiError.class, new A2uiStateError("m"));
    assertInstanceOf(A2uiError.class, new A2uiParseError("m"));
    assertInstanceOf(A2uiError.class, new A2uiExpressionError("m"));
  }

  @Test
  void integrityAndRecursionErrorsAreValidationErrors() {
    assertInstanceOf(A2uiValidationError.class, new A2uiIntegrityError("m"));
    assertInstanceOf(A2uiValidationError.class, new A2uiRecursionError("m"));
  }

  @Test
  void anErrorKeepsItsMessageAndItsCause() {
    IllegalStateException cause = new IllegalStateException("cause");
    A2uiDataError error = new A2uiDataError("message", cause);
    assertEquals("message", error.getMessage());
    assertSame(cause, error.getCause());
    assertNull(new A2uiDataError("message").getCause());
  }

  @Test
  void anRpcErrorHasTheExecutionErrorCodeByDefault() {
    assertEquals(RpcErrorCode.EXECUTION_ERROR, new A2uiRpcError("m").code());
    assertEquals(RpcErrorCode.TIMEOUT, new A2uiRpcError("m", RpcErrorCode.TIMEOUT).code());
  }

  @Test
  void anErrorHasNoContextByDefault() {
    A2uiError error = new A2uiValidationError("m");
    assertTrue(error.surfaceId().isEmpty());
    assertTrue(error.functionCallId().isEmpty());
    assertTrue(error.path().isEmpty());
    assertTrue(error.errorCode().isEmpty());
    assertTrue(error.details().isEmpty());
  }

  @Test
  void customCodeGivesThePathAndTheDetailsToAConstructor() {
    A2uiErrorDetail detail = new A2uiErrorDetail("/text", "type_mismatch", "not a string");
    List<A2uiErrorDetail> details = new ArrayList<>(List.of(detail));
    IllegalStateException cause = new IllegalStateException("cause");

    A2uiExpressionError expression = new A2uiExpressionError("m", "/a", details);
    A2uiValidationError validation = new A2uiValidationError("m", "/b", details, cause);
    A2uiExpressionError noPath = new A2uiExpressionError("m", null, List.of(), cause);
    details.clear();

    assertEquals("m", expression.getMessage());
    assertEquals(Optional.of("/a"), expression.path());
    assertEquals(List.of(detail), expression.details());
    assertNull(expression.getCause());
    assertEquals(Optional.of("/b"), validation.path());
    assertEquals(List.of(detail), validation.details());
    assertSame(cause, validation.getCause());
    assertTrue(noPath.path().isEmpty());
    assertTrue(noPath.details().isEmpty());
    assertSame(cause, noPath.getCause());
    assertThrows(UnsupportedOperationException.class, () -> expression.details().clear());
    // Only the core writes the other parts of the context.
    assertTrue(expression.surfaceId().isEmpty());
    assertTrue(expression.functionCallId().isEmpty());
    assertTrue(expression.errorCode().isEmpty());
  }

  @Test
  void aConstructorRefusesDetailsThatAreNull() {
    List<A2uiErrorDetail> withNull = new ArrayList<>();
    withNull.add(null);

    assertThrows(NullPointerException.class, () -> new A2uiExpressionError("m", "/a", null));
    assertThrows(NullPointerException.class, () -> new A2uiValidationError("m", "/a", withNull));
  }

  @Test
  void theCoreWritesTheContextOfAnError() {
    A2uiErrorDetail detail = new A2uiErrorDetail("/text", "type_mismatch", "not a string");
    A2uiValidationError error = new A2uiValidationError("m");

    assertSame(error, ErrorFields.surfaceId(error, "s"));
    ErrorFields.functionCallId(error, "call-1");
    ErrorFields.path(error, "/components/0/text");
    ErrorFields.errorCode(error, "UNALLOWED_CHILD");
    ErrorFields.details(error, List.of(detail));

    assertEquals(Optional.of("s"), error.surfaceId());
    assertEquals(Optional.of("call-1"), error.functionCallId());
    assertEquals(Optional.of("/components/0/text"), error.path());
    assertEquals(Optional.of("UNALLOWED_CHILD"), error.errorCode());
    assertEquals(List.of(detail), error.details());
  }

  @Test
  void theDetailsOfAnErrorAreAReadOnlyCopy() {
    List<A2uiErrorDetail> details = new ArrayList<>();
    details.add(new A2uiErrorDetail("/a", "missing_field", "a is missing"));
    A2uiError error = ErrorFields.details(new A2uiStateError("m"), details);
    details.clear();

    assertEquals(1, error.details().size());
    assertThrows(UnsupportedOperationException.class, () -> error.details().clear());
  }

  @Test
  void aDataErrorKeepsItsPath() {
    assertEquals(Optional.of("/a/b"), new A2uiDataError("m", "/a/b").path());
    assertTrue(new A2uiDataError("m").path().isEmpty());
  }

  @Test
  void aDetailNeedsEachValue() {
    assertThrows(NullPointerException.class, () -> new A2uiErrorDetail(null, "code", "m"));
    assertThrows(NullPointerException.class, () -> new A2uiErrorDetail("/a", null, "m"));
    assertThrows(NullPointerException.class, () -> new A2uiErrorDetail("/a", "code", null));
  }
}
