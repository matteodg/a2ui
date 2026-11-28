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

/** The core cannot decode raw input into a payload. */
public final class A2uiParseError extends A2uiError {
  @Serial private static final long serialVersionUID = 1L;

  /**
   * Makes an error with a message.
   *
   * @param message the description of the failure
   */
  public A2uiParseError(String message) {
    super(message);
  }

  /**
   * Makes an error with a message and a cause.
   *
   * @param message the description of the failure
   * @param cause the exception that caused this failure, or null
   */
  public A2uiParseError(String message, Throwable cause) {
    super(message, cause);
  }
}
