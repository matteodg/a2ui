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

package com.google.a2ui.core.validation;

/**
 * The problems that message processing accepts. Each flag is false in {@link #STRICT} and true in
 * {@link #RELAXED}.
 *
 * <p>A renderer that receives a surface in parts usually uses {@link #RELAXED}. To change one flag,
 * start from one of the two constants: {@code ValidationConfig.STRICT.withAllowMissingRoot(true)}.
 *
 * @param allowOrphanComponents true to accept a component that the root does not reach
 * @param allowDanglingReferences true to accept a reference to a component that does not exist
 * @param allowMissingRoot true to accept a surface that has no root component
 * @param allowUnknownElements true to accept a component type or a function that no catalog
 *     defines, without an examination, and to remove the message fields that this protocol version
 *     does not define
 */
public record ValidationConfig(
    boolean allowOrphanComponents,
    boolean allowDanglingReferences,
    boolean allowMissingRoot,
    boolean allowUnknownElements) {
  /** The configuration that accepts no problem. */
  public static final ValidationConfig STRICT = new ValidationConfig(false, false, false, false);

  /** The configuration that accepts each problem that a surface in parts can have. */
  public static final ValidationConfig RELAXED = new ValidationConfig(true, true, true, true);

  /**
   * Makes a copy with a different value of {@link #allowOrphanComponents()}.
   *
   * @param allow the new value
   * @return the copy
   */
  public ValidationConfig withAllowOrphanComponents(boolean allow) {
    return new ValidationConfig(
        allow, allowDanglingReferences, allowMissingRoot, allowUnknownElements);
  }

  /**
   * Makes a copy with a different value of {@link #allowDanglingReferences()}.
   *
   * @param allow the new value
   * @return the copy
   */
  public ValidationConfig withAllowDanglingReferences(boolean allow) {
    return new ValidationConfig(
        allowOrphanComponents, allow, allowMissingRoot, allowUnknownElements);
  }

  /**
   * Makes a copy with a different value of {@link #allowMissingRoot()}.
   *
   * @param allow the new value
   * @return the copy
   */
  public ValidationConfig withAllowMissingRoot(boolean allow) {
    return new ValidationConfig(
        allowOrphanComponents, allowDanglingReferences, allow, allowUnknownElements);
  }

  /**
   * Makes a copy with a different value of {@link #allowUnknownElements()}.
   *
   * @param allow the new value
   * @return the copy
   */
  public ValidationConfig withAllowUnknownElements(boolean allow) {
    return new ValidationConfig(
        allowOrphanComponents, allowDanglingReferences, allowMissingRoot, allow);
  }
}
