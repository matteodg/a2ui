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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.exceptions.A2uiValidationError;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SemVerTest {
  @Test
  void parseReadsEachPart() {
    assertEquals(new SemVer(1, 2, 3), SemVer.parse("1.2.3"));
  }

  @Test
  void anAbsentPartIsZero() {
    assertEquals(new SemVer(1, 0, 0), SemVer.parse("1"));
    assertEquals(new SemVer(1, 0, 0), SemVer.parse("1.0"));
    assertEquals(SemVer.parse("1"), SemVer.parse("1.0.0"));
  }

  @Test
  void parseAcceptsALeadingVAndSpaces() {
    assertEquals(new SemVer(0, 9, 1), SemVer.parse("v0.9.1"));
    assertEquals(new SemVer(1, 0, 0), SemVer.parse(" V1.0 "));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "1.", "1.0.0.0", "a.b", "1.0-beta", "-1", "1.x", "12345678901"})
  void tryParseReturnsAnEmptyResultForTextThatIsNotAVersion(String text) {
    assertEquals(Optional.empty(), SemVer.tryParse(text));
  }

  @Test
  void parseThrowsAValidationErrorForTextThatIsNotAVersion() {
    A2uiValidationError error = assertThrows(A2uiValidationError.class, () -> SemVer.parse("abc"));
    assertEquals("Invalid version: 'abc'", error.getMessage());
  }

  @Test
  void theConstructorRejectsANegativePart() {
    assertThrows(A2uiValidationError.class, () -> new SemVer(1, -1, 0));
  }

  @Test
  void versionsCompareByMajorThenMinorThenPatch() {
    assertTrue(SemVer.parse("1.0").compareTo(SemVer.parse("0.9.1")) > 0);
    assertTrue(SemVer.parse("0.9").compareTo(SemVer.parse("0.9.1")) < 0);
    assertTrue(SemVer.parse("0.10").compareTo(SemVer.parse("0.9")) > 0);
    assertEquals(0, SemVer.parse("1.0").compareTo(SemVer.parse("1")));
  }

  @Test
  void toStringShowsThreeParts() {
    assertEquals("1.0.0", SemVer.parse("v1").toString());
  }
}
