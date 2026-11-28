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
import java.util.List;

/** The core cannot calculate the value of a bound expression. */
public final class A2uiExpressionError extends A2uiError {
  @Serial private static final long serialVersionUID = 1L;

  /**
   * Makes an error with a message.
   *
   * @param message the description of the failure
   */
  public A2uiExpressionError(String message) {
    super(message);
  }

  /**
   * Makes an error with a message and a cause.
   *
   * @param message the description of the failure
   * @param cause the exception that caused this failure, or null
   */
  public A2uiExpressionError(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Makes an error with a message, a path and details. The code of a catalog function and the code
   * of a component use this constructor to tell the agent where the problem is.
   *
   * <p>The core writes the surface, the function call and the protocol error code of the error. The
   * {@code error} message for the agent has the path and the details, if the schema of the message
   * permits them for the protocol error code.
   *
   * @param message the description of the failure
   * @param path the location of the problem, or null. The location is a JSON Pointer, for example
   *     {@code /components/0/text}, or a data model path.
   * @param details the problems of the failure. The error keeps a read-only copy. The list can be
   *     empty.
   * @throws NullPointerException if the list is null or contains null
   */
  public A2uiExpressionError(String message, String path, List<A2uiErrorDetail> details) {
    super(message, path, details, null);
  }

  /**
   * Makes an error with a message, a path, details and a cause. The code of a catalog function and
   * the code of a component use this constructor to tell the agent where the problem is.
   *
   * @param message the description of the failure
   * @param path the location of the problem, or null. The location is a JSON Pointer, for example
   *     {@code /components/0/text}, or a data model path.
   * @param details the problems of the failure. The error keeps a read-only copy. The list can be
   *     empty.
   * @param cause the exception that caused this failure, or null
   * @throws NullPointerException if the list is null or contains null
   */
  public A2uiExpressionError(
      String message, String path, List<A2uiErrorDetail> details, Throwable cause) {
    super(message, path, details, cause);
  }
}
