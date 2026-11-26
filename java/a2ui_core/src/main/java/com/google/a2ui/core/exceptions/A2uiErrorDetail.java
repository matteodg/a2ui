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

import java.io.Serializable;
import java.util.Objects;

/**
 * One problem of a failure.
 *
 * @param path the location of the problem, relative to the item that the SDK examined
 * @param code the type of the problem, for example {@code missing_field} or {@code type_mismatch}
 * @param message the description of the problem
 */
public record A2uiErrorDetail(String path, String code, String message) implements Serializable {
  /** Makes a detail. Each value is necessary. */
  public A2uiErrorDetail {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
  }
}
