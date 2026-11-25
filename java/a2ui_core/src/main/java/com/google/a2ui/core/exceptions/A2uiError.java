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

import java.io.Serial;

/** The base class of each failure that the A2UI core reports. */
public sealed class A2uiError extends RuntimeException
    permits A2uiValidationError,
        A2uiCatalogError,
        A2uiRpcError,
        A2uiDataError,
        A2uiStateError,
        A2uiParseError,
        A2uiExpressionError {
  @Serial private static final long serialVersionUID = 1L;

  /**
   * Makes an error with a message.
   *
   * @param message the description of the failure
   */
  public A2uiError(String message) {
    super(message);
  }

  /**
   * Makes an error with a message and a cause.
   *
   * @param message the description of the failure
   * @param cause the exception that caused this failure, or null
   */
  public A2uiError(String message, Throwable cause) {
    super(message, cause);
  }
}
