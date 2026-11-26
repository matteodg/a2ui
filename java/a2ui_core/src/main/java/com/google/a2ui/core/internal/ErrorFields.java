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

package com.google.a2ui.core.internal;

import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiErrorDetail;
import java.util.List;
import java.util.Objects;

/**
 * Writes the context of an {@link A2uiError}: the surface, the function call, the path, the
 * protocol error code and the details.
 *
 * <p>Only the core writes the context of an error. The public API of the error can only read it.
 * The error class gives its writer to this class, because this package is not exported. Thus code
 * outside the module cannot change an error.
 *
 * <p>Each method returns its error. Thus a {@code throw} statement can make an error and write its
 * context in one expression.
 */
public final class ErrorFields {
  /** The operations that write the context of an error. The error class implements them. */
  public interface Writer {
    /**
     * Writes the surface of an error.
     *
     * @param error the error to change
     * @param surfaceId the ID of the surface, or null
     */
    void surfaceId(A2uiError error, String surfaceId);

    /**
     * Writes the function call of an error.
     *
     * @param error the error to change
     * @param functionCallId the ID of the function call, or null
     */
    void functionCallId(A2uiError error, String functionCallId);

    /**
     * Writes the path of an error.
     *
     * @param error the error to change
     * @param path the path, or null
     */
    void path(A2uiError error, String path);

    /**
     * Writes the protocol error code of an error.
     *
     * @param error the error to change
     * @param errorCode the code, or null
     */
    void errorCode(A2uiError error, String errorCode);

    /**
     * Writes the details of an error.
     *
     * @param error the error to change
     * @param details the problems of the failure
     */
    void details(A2uiError error, List<A2uiErrorDetail> details);
  }

  /** The writer of the error class. The initialization of that class sets it. */
  private static volatile Writer writer;

  private ErrorFields() {}

  /**
   * Sets the writer. The initialization of {@link A2uiError} calls this method one time.
   *
   * @param errorWriter the writer of the error class
   * @throws IllegalStateException if a writer is already set
   */
  public static void register(Writer errorWriter) {
    if (writer != null) {
      throw new IllegalStateException("The writer of the error context is already set.");
    }
    writer = Objects.requireNonNull(errorWriter, "errorWriter");
  }

  /**
   * Writes the surface that the failed message was for.
   *
   * @param <E> the type of the error
   * @param error the error to change
   * @param surfaceId the ID of the surface, or null
   * @return the same error
   */
  public static <E extends A2uiError> E surfaceId(E error, String surfaceId) {
    writer.surfaceId(error, surfaceId);
    return error;
  }

  /**
   * Writes the function call that the failed message was about.
   *
   * @param <E> the type of the error
   * @param error the error to change
   * @param functionCallId the ID of the function call, or null
   * @return the same error
   */
  public static <E extends A2uiError> E functionCallId(E error, String functionCallId) {
    writer.functionCallId(error, functionCallId);
    return error;
  }

  /**
   * Writes the location of the problem.
   *
   * @param <E> the type of the error
   * @param error the error to change
   * @param path the JSON Pointer into the body of the failed message, or a data model path, or null
   * @return the same error
   */
  public static <E extends A2uiError> E path(E error, String path) {
    writer.path(error, path);
    return error;
  }

  /**
   * Writes the protocol error code of the failure.
   *
   * @param <E> the type of the error
   * @param error the error to change
   * @param errorCode the code, for example {@code UNALLOWED_PARENT}, or null
   * @return the same error
   */
  public static <E extends A2uiError> E errorCode(E error, String errorCode) {
    writer.errorCode(error, errorCode);
    return error;
  }

  /**
   * Writes the problems of the failure.
   *
   * @param <E> the type of the error
   * @param error the error to change
   * @param details the problems. The error keeps a read-only copy.
   * @return the same error
   */
  public static <E extends A2uiError> E details(E error, List<A2uiErrorDetail> details) {
    writer.details(error, details);
    return error;
  }
}
