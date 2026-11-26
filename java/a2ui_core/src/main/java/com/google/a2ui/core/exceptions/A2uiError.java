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

import com.google.a2ui.core.internal.ErrorFields;
import java.io.Serial;
import java.util.List;
import java.util.Optional;

/**
 * The base class of each failure that the A2UI core reports.
 *
 * <p>An error can have a context: the surface, the function call, the path, the protocol error code
 * and the details. The core writes the context. For example, the message processor writes the
 * surface of the message that it rejects.
 */
public sealed class A2uiError extends RuntimeException
    permits A2uiValidationError,
        A2uiCatalogError,
        A2uiRpcError,
        A2uiDataError,
        A2uiStateError,
        A2uiParseError,
        A2uiExpressionError {
  @Serial private static final long serialVersionUID = 1L;

  static {
    // The core writes the context of an error through a package that the module does not export.
    ErrorFields.register(
        new ErrorFields.Writer() {
          @Override
          public void surfaceId(A2uiError error, String surfaceId) {
            error.surfaceId = surfaceId;
          }

          @Override
          public void functionCallId(A2uiError error, String functionCallId) {
            error.functionCallId = functionCallId;
          }

          @Override
          public void path(A2uiError error, String path) {
            error.path = path;
          }

          @Override
          public void errorCode(A2uiError error, String errorCode) {
            error.errorCode = errorCode;
          }

          @Override
          public void details(A2uiError error, List<A2uiErrorDetail> details) {
            error.details = details.toArray(A2uiErrorDetail[]::new);
          }

          @Override
          public void raisedByFunction(A2uiError error) {
            error.raisedByFunction = true;
          }

          @Override
          public boolean isRaisedByFunction(A2uiError error) {
            return error.raisedByFunction;
          }

          @Override
          public void failedByAgent(A2uiError error) {
            error.failedByAgent = true;
          }

          @Override
          public boolean isFailedByAgent(A2uiError error) {
            return error.failedByAgent;
          }
        });
  }

  private static final A2uiErrorDetail[] NO_DETAILS = {};

  /** The surface that the failed message was for, or null. */
  private String surfaceId;

  /** The function call that the failed message was about, or null. */
  private String functionCallId;

  /** The location of the problem, or null. */
  private String path;

  /** The protocol error code of the failure, or null. */
  private String errorCode;

  /** The problems of the failure. */
  private A2uiErrorDetail[] details = NO_DETAILS;

  /**
   * True if the code of a function caused this error. False for a call that the core refused before
   * the function ran.
   */
  private boolean raisedByFunction;

  /**
   * True if the agent failed a call that the renderer sent to it, or did not answer the call in
   * time.
   */
  private boolean failedByAgent;

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

  /**
   * Makes an error with a message, a path and a cause.
   *
   * @param message the description of the failure
   * @param path the location of the problem, or null
   * @param cause the exception that caused this failure, or null
   */
  A2uiError(String message, String path, Throwable cause) {
    super(message, cause);
    this.path = path;
  }

  /**
   * Gets the surface that the failed message was for.
   *
   * @return the ID of the surface, or an empty result if the failure is not about one surface
   */
  public Optional<String> surfaceId() {
    return Optional.ofNullable(surfaceId);
  }

  /**
   * Gets the function call that the failed message was about.
   *
   * @return the ID of the function call, or an empty result if the failure is not about a call
   */
  public Optional<String> functionCallId() {
    return Optional.ofNullable(functionCallId);
  }

  /**
   * Gets the location of the problem. The location is a JSON Pointer into the body of the failed
   * message, for example {@code /components/0/text}, or a data model path.
   *
   * @return the path, or an empty result if the failure has no location
   */
  public Optional<String> path() {
    return Optional.ofNullable(path);
  }

  /**
   * Gets the protocol error code of the failure, for example {@code UNALLOWED_PARENT}.
   *
   * @return the code, or an empty result if the class of the error gives the code
   */
  public Optional<String> errorCode() {
    return Optional.ofNullable(errorCode);
  }

  /**
   * Gets each problem of the failure. A failure has details if it reports more than one problem, or
   * if it gives the location of its problems.
   *
   * @return the problems, as a read-only list. The list is empty if the failure has no details.
   */
  public List<A2uiErrorDetail> details() {
    return List.of(details);
  }
}
