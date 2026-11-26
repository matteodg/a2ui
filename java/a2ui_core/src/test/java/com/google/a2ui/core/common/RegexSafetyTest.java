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

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegexSafetyTest {
  private static Optional<RegexRejection> examine(String pattern) {
    try {
      return RegexSafety.examine(RegexParser.parse(pattern));
    } catch (RegexSyntaxException e) {
      throw new AssertionError(pattern + ": " + e.getMessage(), e);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "(a+)+$",
        "(a*)*",
        "(a*){2,5}",
        "(?:a+){2}",
        "(?:a+){0,2}",
        "((a+)b)*",
        "(a{2,})+",
        "(?:a|b+)*",
        "(?:x(?=a*))+",
        "(?:a*?)+?",
        // The rule 2 is first, although the pattern also does not obey the rules 3 and 4.
        "(?:a+a+|a)*"
      })
  void aRepeatedQuantifierThatHasNoUpperLimitIsRefused(String pattern) {
    assertEquals(Optional.of(RegexRejection.NESTED_QUANTIFIER), examine(pattern));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "(a|ab)*c",
        "(\\d|[0-5])+",
        "(?:\\w|a)+",
        "((a|b)|(b|c))*",
        "(ab|a?c|a)*",
        "(?:a|b?a){1,}",
        "(?:(?:(a|a)))*",
        "(?:a|[^b])*?",
        "(?:.|\\s)*",
        // The rule 3 is before the rule 4.
        "x*x*(?:a|a)*"
      })
  void aRepeatedAlternationWhoseBranchesCanStartWithTheSameCharacterIsRefused(String pattern) {
    assertEquals(Optional.of(RegexRejection.OVERLAPPING_ALTERNATION), examine(pattern));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\\d+\\d+",
        "\\d+\\s*\\d+",
        ".*.*",
        "a*(?:b?)a+",
        "\\w+\\b\\d*",
        "(a+)(a*)",
        "a+(?=x)a+",
        "a*^a*",
        "[a-f]+(?:)[f-z]{3,}",
        "(?:a*a*)?",
        "a+?a+?",
        "(?!\\d+\\d+)x"
      })
  void twoAdjacentQuantifiersThatCanMatchTheSameCharacterAreRefused(String pattern) {
    assertEquals(Optional.of(RegexRejection.ADJACENT_OVERLAP), examine(pattern));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "^[a-z]+@[a-z]+\\.com$",
        "^(?=.*\\d).{8,}$",
        "\\w+\\s+\\w+",
        // A maximum of 1 does not repeat.
        "(?:ab+)?c",
        "(a+)?b",
        "(?:a*){0,1}",
        "(?:a+){1}",
        // A counted quantifier has an upper limit.
        "(?:a{1,3}){1,1000}",
        "(?:a{1,65535})*",
        "a{0,9}a{0,9}",
        "a*a{0,9}",
        // The branches start with different characters.
        "(a|b)*c",
        "(?:ab|cd|\\d)+",
        "(?:[a-f]|[g-z]x?)*",
        // Not a repetition without an upper limit.
        "(?:a|ab){0,3}c",
        "(a|ab)?",
        "(?:(?:a|a)x)*",
        // The sets are different, or an item that cannot be empty is between the quantifiers.
        "^[a-z]+[0-9]+$",
        "a+ba+",
        "\\d+\\s+\\d+",
        "a*(?:b|c)a*",
        "(?:a+|b)a*",
        "a+(?=a+)b",
        "(?:xa*)(?:ya*)",
        // A quantifier with a maximum of 0 matches no character.
        "(?:a{0})*a*"
      })
  void aPatternThatObeysTheRulesIsSafe(String pattern) {
    assertEquals(Optional.empty(), examine(pattern));
  }
}
