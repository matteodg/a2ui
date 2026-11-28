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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ValidationConfigTest {
  @Test
  void strictAcceptsNoProblemAndRelaxedAcceptsEachProblem() {
    assertEquals(new ValidationConfig(false, false, false, false), ValidationConfig.STRICT);
    assertEquals(new ValidationConfig(true, true, true, true), ValidationConfig.RELAXED);
  }

  @Test
  void eachWithMethodChangesOneFlag() {
    ValidationConfig strict = ValidationConfig.STRICT;

    assertEquals(
        new ValidationConfig(true, false, false, false), strict.withAllowOrphanComponents(true));
    assertEquals(
        new ValidationConfig(false, true, false, false), strict.withAllowDanglingReferences(true));
    assertEquals(
        new ValidationConfig(false, false, true, false), strict.withAllowMissingRoot(true));
    assertEquals(
        new ValidationConfig(false, false, false, true), strict.withAllowUnknownElements(true));
    assertEquals(
        ValidationConfig.RELAXED,
        strict
            .withAllowOrphanComponents(true)
            .withAllowDanglingReferences(true)
            .withAllowMissingRoot(true)
            .withAllowUnknownElements(true));
  }
}
