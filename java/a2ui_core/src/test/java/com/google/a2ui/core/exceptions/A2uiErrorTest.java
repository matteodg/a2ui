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
}
