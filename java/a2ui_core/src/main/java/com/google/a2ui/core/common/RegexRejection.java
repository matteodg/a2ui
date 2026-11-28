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

package com.google.a2ui.core.common;

/**
 * The reason why the core refuses a pattern.
 *
 * <p>The name of a constant is the token of the protocol. The message of the error contains it.
 */
enum RegexRejection {
  /** The pattern is not in the dialect. */
  UNSUPPORTED_SYNTAX,
  /** Rule 1: the pattern is longer than 256 code points. */
  PATTERN_TOO_LONG,
  /**
   * Rule 2: a quantifier that can repeat more than one time contains a quantifier that has no upper
   * limit.
   */
  NESTED_QUANTIFIER,
  /**
   * Rule 3: a quantifier that has no upper limit repeats an alternation, and two branches can start
   * with the same character.
   */
  OVERLAPPING_ALTERNATION,
  /**
   * Rule 4: two quantifiers that have no upper limit can match the same character, and only items
   * that can match the empty string are between them.
   */
  ADJACENT_OVERLAP
}
