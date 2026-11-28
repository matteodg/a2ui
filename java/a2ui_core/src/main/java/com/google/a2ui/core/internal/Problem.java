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

import com.google.a2ui.core.exceptions.A2uiErrorDetail;
import java.util.List;
import java.util.function.Predicate;

/**
 * One problem of a value that the core examined.
 *
 * @param path the location of the problem: a JSON Pointer relative to the value. It is empty for
 *     the value itself.
 * @param code the type of the problem: {@code missing_field}, {@code type_mismatch} or {@code
 *     invalid_value}
 * @param message the description of the problem
 * @param unevaluated true if the problem reports a property that no subschema used
 */
public record Problem(String path, String code, String message, boolean unevaluated) {
  /** The code of a property that is necessary and absent. */
  public static final String MISSING_FIELD = "missing_field";

  /** The code of a value that has an incorrect JSON type. */
  public static final String TYPE_MISMATCH = "type_mismatch";

  /** The code of each other problem. */
  public static final String INVALID_VALUE = "invalid_value";

  /**
   * Makes a problem that does not report an unused property.
   *
   * @param path the location of the problem
   * @param code the type of the problem
   * @param message the description of the problem
   */
  public Problem(String path, String code, String message) {
    this(path, code, message, false);
  }

  /**
   * Gets this problem as a value that contains the examined value sees it.
   *
   * @param prefix the JSON Pointer of the examined value in the container
   * @return the problem with the prefix before its path
   */
  public Problem under(String prefix) {
    return prefix.isEmpty() ? this : new Problem(prefix + path, code, message, unevaluated);
  }

  /**
   * Tells if this problem is only a result of a different problem.
   *
   * <p>If a subschema fails, the validator also reports each property of that subschema as a
   * property that no subschema used. Such a report is a result of the failure if the list has a
   * different type of problem and the schema declares the property.
   *
   * @param all each problem of the examined value
   * @param declared tells if the schema declares a property name
   * @return true if a report without this problem tells the cause more clearly
   */
  public boolean followsFrom(List<Problem> all, Predicate<String> declared) {
    return unevaluated
        && all.stream().anyMatch(problem -> !problem.unevaluated())
        && declared.test(Json.unescapePointer(path.substring(path.lastIndexOf('/') + 1)));
  }

  /**
   * Gets this problem as a detail of an error.
   *
   * @return the detail with the path, the code and the message of this problem
   */
  public A2uiErrorDetail toDetail() {
    return new A2uiErrorDetail(path, code, message);
  }

  /** Gets the message, with the path before it if the problem is not at the examined value. */
  @Override
  public String toString() {
    return path.isEmpty() ? message : path + ": " + message;
  }
}
