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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class Uax31Test {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "root",
        "_private",
        "submit_button",
        "item1",
        "café",
        "名前",
        "αβγ",
        // U+1D4D0 MATHEMATICAL BOLD SCRIPT CAPITAL A, which is two surrogates in UTF-16.
        "𝓐b"
      })
  void isIdentifierAcceptsValidIdentifiers(String text) {
    assertTrue(Uax31.isIdentifier(text));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "user-card",
        "1st",
        "a b",
        "a.b",
        "@index",
        "a/b",
        "·a",
        // U+1F600 GRINNING FACE.
        "a😀"
      })
  void isIdentifierRejectsTextThatIsNotAnIdentifier(String text) {
    assertFalse(Uax31.isIdentifier(text));
  }

  @Test
  void anUnderscoreCanStartAnIdentifier() {
    assertTrue(Uax31.isIdentifierStart('_'));
    assertTrue(Uax31.isIdentifierContinue('_'));
  }

  @Test
  void aDigitCanContinueAnIdentifierButCannotStartIt() {
    assertFalse(Uax31.isIdentifierStart('1'));
    assertTrue(Uax31.isIdentifierContinue('1'));
  }

  @Test
  void aMiddleDotCanContinueAnIdentifierButCannotStartIt() {
    assertFalse(Uax31.isIdentifierStart(0x00B7));
    assertTrue(Uax31.isIdentifierContinue(0x00B7));
  }
}
