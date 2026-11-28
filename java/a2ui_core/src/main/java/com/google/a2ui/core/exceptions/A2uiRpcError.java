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
import java.util.Objects;

/** A remote procedure call failed, timed out, was rejected or had no listener. */
public final class A2uiRpcError extends A2uiError {
  @Serial private static final long serialVersionUID = 1L;

  /** The cause of the failure. */
  private final RpcErrorCode code;

  /**
   * Makes an error with the code {@link RpcErrorCode#EXECUTION_ERROR}.
   *
   * @param message the description of the failure
   */
  public A2uiRpcError(String message) {
    this(message, RpcErrorCode.EXECUTION_ERROR, null);
  }

  /**
   * Makes an error with a message and a code.
   *
   * @param message the description of the failure
   * @param code the cause of the failure
   */
  public A2uiRpcError(String message, RpcErrorCode code) {
    this(message, code, null);
  }

  /**
   * Makes an error with a message, a code and a cause.
   *
   * @param message the description of the failure
   * @param code the cause of the failure
   * @param cause the exception that caused this failure, or null
   */
  public A2uiRpcError(String message, RpcErrorCode code, Throwable cause) {
    super(message, cause);
    this.code = Objects.requireNonNull(code, "code");
  }

  /**
   * Gets the cause of the failure.
   *
   * @return the error code
   */
  public RpcErrorCode code() {
    return code;
  }
}
