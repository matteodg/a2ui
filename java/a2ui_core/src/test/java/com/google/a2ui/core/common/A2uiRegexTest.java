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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.exceptions.A2uiExpressionError;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The behavior of the regex dialect that the shared suite {@code core/functions.yaml} omits. */
class A2uiRegexTest {
  private static final String LINE_SEPARATOR = Character.toString(0x2028);
  private static final String BYTE_ORDER_MARK = Character.toString(0xFEFF);

  /** U+1F600 GRINNING FACE. It is two surrogates in UTF-16. */
  private static final String SMILE = Character.toString(0x1F600);

  private static boolean matches(String pattern, String value) {
    return A2uiRegex.compile(pattern).test(value);
  }

  private static void assertRefused(String token, String... patterns) {
    for (String pattern : patterns) {
      A2uiExpressionError error =
          assertThrows(A2uiExpressionError.class, () -> A2uiRegex.compile(pattern), pattern);
      assertTrue(error.getMessage().contains("(" + token + ")"), error.getMessage());
    }
  }

  @Test
  void theClassEscapesHaveTheMeaningOfTheDialectAndNotOfTheEngine() {
    assertTrue(matches("^\\s$", " "));
    assertTrue(matches("^\\s$", BYTE_ORDER_MARK));
    // U+001C is white space for Character.isWhitespace, but it is not for ECMAScript.
    assertFalse(matches("^\\s$", "\u001C"));
    assertTrue(matches("^\\S\\D\\W$", "x-é"));
    assertFalse(matches("^\\W$", "_"));
    assertTrue(matches("^[\\d\\s-]+$", "12 34-56"));
    // U+0661 ARABIC-INDIC DIGIT ONE.
    assertTrue(matches("^[^\\d]$", "١"));
    assertFalse(matches("^\\d$", "١"));
  }

  @Test
  void aLineTerminatorStopsTheDotAndDoesNotMoveTheAnchors() {
    assertFalse(matches("^.$", "\r"));
    assertFalse(matches("^.$", "\n"));
    assertFalse(matches("^.$", LINE_SEPARATOR));
    // U+0085 NEXT LINE is a line terminator for java.util.regex, but it is not for ECMAScript.
    assertTrue(matches("^.$", "\u0085"));
    assertFalse(matches("^b", "a\nb"));
    assertFalse(matches("a$", "a\nb"));
    assertFalse(matches("^a$", "a\n"));
    assertFalse(matches("^a$", "a\r\n"));
    assertFalse(matches("^a$", "a" + LINE_SEPARATOR));
    assertTrue(matches("a\\nb", "a\nb"));
  }

  @Test
  void aWordBoundaryKnowsOnlyAsciiWordCharacters() {
    assertTrue(matches("\\bcat\\b", "a cat sat"));
    assertFalse(matches("\\bcat\\b", "concat"));
    assertTrue(matches("^\\B$", ""));
    assertFalse(matches("\\b", ""));
    assertTrue(matches("\\Bat", "cat"));
    // The letter é is not a word character. Thus a boundary is between it and the letter b.
    assertTrue(matches("\\bb", "éb"));
    assertFalse(matches("\\b", "é"));
  }

  @Test
  void anEscapeMatchesTheCodePointThatItWrites() {
    assertTrue(matches("^\\x41\\u0042\\u{43}\\0$", "ABC\0"));
    assertTrue(matches("^[\\u{1F600}-\\u{1F64F}]{2}$", SMILE + Character.toString(0x1F64F)));
    assertTrue(matches("^[\\b]$", "\b"));
    assertTrue(matches("^\\^\\$\\.\\*\\+\\?\\(\\)\\[\\]\\{\\}\\|\\/\\\\$", "^$.*+?()[]{}|/\\"));
    assertTrue(matches("^[a\\-z]+$", "a-z"));
    assertTrue(matches("^[a-]+$", "a-"));
    assertFalse(matches("^[a\\-z]+$", "b"));
  }

  @Test
  void aCodePointOutsideTheBasicPlaneIsOneCharacter() {
    assertTrue(matches("^.$", SMILE));
    assertTrue(matches("^" + SMILE + "{2}$", SMILE + SMILE));
    assertTrue(matches("^[^a]$", SMILE));
    assertFalse(matches("^..$", SMILE));
    // A surrogate that has no partner is not a Unicode scalar value. No character set contains it.
    assertFalse(matches(".", "\uD83D"));
    assertFalse(matches("\\W", "\uDE00"));
  }

  @Test
  void theMatchIsCaseSensitiveAndCanBeAnywhereInTheValue() {
    assertFalse(matches("^abc$", "ABC"));
    assertFalse(matches("[a-z]", "ABC"));
    assertTrue(matches("abc", "xxabcxx"));
    assertTrue(matches("", "anything"));
    assertTrue(matches("", ""));
  }

  @Test
  void groupsLookaheadsAndQuantifiersBehaveAsInEcmaScript() {
    assertTrue(matches("^(?:ab){2,3}$", "ababab"));
    assertFalse(matches("^(?:ab){2,3}$", "ab"));
    assertTrue(matches("^a{2}$", "aa"));
    assertFalse(matches("^a{2}$", "aaa"));
    assertTrue(matches("^a{2,}?b$", "aaab"));
    assertTrue(matches("^(?!admin$)[a-z]+$", "administrator"));
    assertFalse(matches("^(?!admin$)[a-z]+$", "admin"));
    assertTrue(matches("^(?=.*\\d)(?=.*[a-z]).{8,}$", "secret42"));
    assertFalse(matches("^(?=.*\\d)(?=.*[a-z]).{8,}$", "12345678"));
    assertTrue(matches("^(cat|dog)s?$", "dogs"));
    assertFalse(matches("^(cat|dog)s?$", "cats!"));
    assertTrue(matches("^(|a)b$", "b"));
    assertTrue(matches("^(a|b|\\d)+$", "a1b2"));
    assertFalse(matches("^(a|b|\\d)+$", "a1c2"));
  }

  @Test
  void aPatternThatIsNotInTheDialectIsRefused() {
    assertRefused(
        "UNSUPPORTED_SYNTAX",
        "(a)\\1",
        "\\k<n>",
        "(?<=a)b",
        "(?<n>a)",
        "(?i)a",
        "\\p{L}",
        "\\cA",
        "\\01",
        "a++",
        "a{,2}",
        "[]",
        "[a&&b]",
        "[invalid(",
        "(a",
        "\\",
        "\\uD800",
        "\uD800");
  }

  @Test
  void aPatternThatCanBacktrackWithoutLimitIsRefused() {
    assertRefused("NESTED_QUANTIFIER", "(a*)*", "(?:a+){2}", "((a+)b)*", "(a{2,})+");
    assertRefused("OVERLAPPING_ALTERNATION", "(?:\\w|a)+", "((a|b)|(b|c))*", "(ab|a?c|a)*");
    assertRefused("ADJACENT_OVERLAP", ".*.*", "a*(?:b?)a+", "\\w+\\b\\d*", "(a+)(a*)");
  }

  @Test
  void theMessageOfARefusedPatternHasTheTokenAndThePattern() {
    A2uiExpressionError unsafe =
        assertThrows(A2uiExpressionError.class, () -> A2uiRegex.compile("(a+)+$"));
    A2uiExpressionError syntax =
        assertThrows(A2uiExpressionError.class, () -> A2uiRegex.compile("(a)\\1"));
    A2uiExpressionError length =
        assertThrows(A2uiExpressionError.class, () -> A2uiRegex.compile("a".repeat(257)));

    assertEquals(
        "Invalid or unsafe regex pattern (NESTED_QUANTIFIER): the pattern could be slow to match:"
            + " (a+)+$",
        unsafe.getMessage());
    assertEquals(
        "Invalid or unsafe regex pattern (UNSUPPORTED_SYNTAX): Backreference is not supported:"
            + " (a)\\1",
        syntax.getMessage());
    assertEquals(
        "Invalid or unsafe regex pattern (PATTERN_TOO_LONG): the pattern has 257 code points and"
            + " the limit is 256",
        length.getMessage());
  }

  @Test
  void theLengthRuleIsBeforeTheSyntaxOfThePattern() {
    assertRefused("PATTERN_TOO_LONG", "(".repeat(A2uiRegex.MAX_PATTERN_CODE_POINTS + 1));
  }

  @Test
  void aRepetitionWithAnUpperLimitAndDifferentNeighborsAreAccepted() {
    assertTrue(matches("^(?:a{1,3}){1,1000}$", "aaaa"));
    assertTrue(matches("^(a+)?b$", "aab"));
    assertTrue(matches("^(?:a|ab){0,3}c$", "abac"));
    assertTrue(matches("^[a-z]+[0-9]+$", "abc123"));
    assertTrue(matches("^a+ba+$", "aabaa"));
  }

  @Test
  void theLengthLimitsCountCodePoints() {
    assertTrue(matches(SMILE.repeat(A2uiRegex.MAX_PATTERN_CODE_POINTS), SMILE.repeat(300)));
    assertRefused("PATTERN_TOO_LONG", "a".repeat(A2uiRegex.MAX_PATTERN_CODE_POINTS + 1));

    assertTrue(matches("^.+$", SMILE.repeat(A2uiRegex.MAX_VALUE_CODE_POINTS)));
    assertFalse(matches("^.+$", SMILE.repeat(A2uiRegex.MAX_VALUE_CODE_POINTS + 1)));
    assertFalse(matches("", "a".repeat(A2uiRegex.MAX_VALUE_CODE_POINTS + 1)));
  }

  @Test
  void aRepeatedGroupMatchesTheLongestValue() {
    String value = "ab".repeat(A2uiRegex.MAX_VALUE_CODE_POINTS / 2);

    assertTrue(matches("^(a|b)*$", value));
    assertFalse(matches("^(a|b)*$", value.substring(1) + "c"));
    // java.util.regex uses one level of recursion for each repetition of these groups. That is
    // more than the stack of a default thread permits.
    assertTrue(matches("^(ab|cd)*$", value));
    assertFalse(matches("^(ab|cd)*$", value.substring(2) + "ac"));
    assertTrue(matches("^(?:ab?)*$", value));
    assertTrue(matches("^(?:(?:ab|cd)|(?:e|f){1,9}){1,5000}$", value));
    assertTrue(matches("^(?:(?:(?:(?:(?:(?:(?:(?:ab|c)x?)y?)z?)u?)v?)w?)q?)+?$", value));
  }

  @Test
  void aRepeatedGroupMatchesOnAThreadThatHasASmallStack() throws InterruptedException {
    String value = "ab".repeat(A2uiRegex.MAX_VALUE_CODE_POINTS / 2);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread small =
        new Thread(null, () -> assertTrue(matches("^(ab|cd)*$", value)), "small", 128 * 1024);
    small.setUncaughtExceptionHandler((thread, error) -> failure.set(error));

    small.start();
    small.join();

    assertNull(failure.get());
  }

  @Test
  void anInstanceGivesItsPattern() {
    A2uiRegex regex = A2uiRegex.compile("^[a-z]+$");

    assertEquals("^[a-z]+$", regex.pattern());
    assertEquals("^[a-z]+$", regex.toString());
    assertTrue(regex.test("abc"));
    assertFalse(regex.test("ABC"));
    assertThrows(NullPointerException.class, () -> regex.test(null));
    assertThrows(NullPointerException.class, () -> A2uiRegex.compile(null));
  }
}
