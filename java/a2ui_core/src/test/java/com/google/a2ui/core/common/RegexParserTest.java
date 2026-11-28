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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.common.RegexNode.AnchorKind;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegexParserTest {
  private static RegexNode parse(String pattern) {
    try {
      return RegexParser.parse(pattern);
    } catch (RegexSyntaxException e) {
      throw new AssertionError(pattern + ": " + e.getMessage(), e);
    }
  }

  private static RegexNode.CharSet set(int... limits) {
    return new RegexNode.CharSet(CodePointRange.setOf(limits));
  }

  private static RegexNode.CharSet literal(int codePoint) {
    return set(codePoint, codePoint);
  }

  /** Tells if the pattern is one character set that contains the code point. */
  private static boolean contains(String pattern, int codePoint) {
    RegexNode.CharSet set = (RegexNode.CharSet) parse(pattern);
    return CodePointRange.overlap(set.ranges(), List.of(CodePointRange.of(codePoint)));
  }

  private static String syntaxProblem(String pattern) {
    return assertThrows(RegexSyntaxException.class, () -> RegexParser.parse(pattern), pattern)
        .getMessage();
  }

  @Test
  void aSetIsSortedAndMergedAndHasNoSurrogates() {
    List<CodePointRange> set = CodePointRange.setOf('x', 'z', 'a', 'c', 'd', 'f', 'b', 'b');

    assertEquals(List.of(new CodePointRange('a', 'f'), new CodePointRange('x', 'z')), set);
    assertEquals(
        List.of(new CodePointRange(0xD000, 0xD7FF), new CodePointRange(0xE000, 0xE001)),
        CodePointRange.setOf(0xD000, 0xE001));
    assertEquals(List.of(), CodePointRange.setOf(0xD800, 0xDFFF));
    assertEquals(List.of(), CodePointRange.setOf('b', 'a'));
  }

  @Test
  void theComplementOfASetHasEachOtherCodePointThatIsNoSurrogate() {
    assertEquals(
        List.of(
            new CodePointRange(0, 'a' - 1),
            new CodePointRange('z' + 1, 0xD7FF),
            new CodePointRange(0xE000, 0x10FFFF)),
        CodePointRange.complement(CodePointRange.setOf('a', 'z')));
    assertEquals(List.of(), CodePointRange.complement(CodePointRange.setOf(0, 0x10FFFF)));
    assertEquals(
        CodePointRange.setOf(0, 0x10FFFF), CodePointRange.complement(CodePointRange.setOf()));
  }

  @Test
  void twoSetsOverlapIfTheyHaveACodePointInCommon() {
    List<CodePointRange> letters = CodePointRange.setOf('a', 'f', 'x', 'z');

    assertTrue(CodePointRange.overlap(letters, CodePointRange.setOf('0', '9', 'f', 'h')));
    assertTrue(CodePointRange.overlap(CodePointRange.setOf('y', 'y'), letters));
    assertFalse(CodePointRange.overlap(letters, CodePointRange.setOf('g', 'w')));
    assertFalse(CodePointRange.overlap(letters, List.of()));
  }

  @Test
  void aPatternIsATreeOfSequencesAndBranches() {
    assertEquals(new RegexNode.Empty(), parse(""));
    assertEquals(literal('a'), parse("a"));
    assertEquals(new RegexNode.Concat(List.of(literal('a'), literal('b'))), parse("ab"));
    assertEquals(
        new RegexNode.Alt(
            List.of(
                new RegexNode.Concat(List.of(literal('a'), literal('b'))),
                new RegexNode.Empty(),
                literal('c'))),
        parse("ab||c"));
    assertEquals(
        new RegexNode.Concat(
            List.of(
                new RegexNode.Anchor(AnchorKind.START),
                new RegexNode.Anchor(AnchorKind.WORD_BOUNDARY),
                new RegexNode.Anchor(AnchorKind.NOT_WORD_BOUNDARY),
                new RegexNode.Anchor(AnchorKind.END))),
        parse("^\\b\\B$"));
  }

  @Test
  void aGroupIsCapturingOrNotAndALookaheadIsNegatedOrNot() {
    assertEquals(new RegexNode.Group(true, literal('a')), parse("(a)"));
    assertEquals(new RegexNode.Group(false, literal('a')), parse("(?:a)"));
    assertEquals(new RegexNode.Lookahead(false, literal('a')), parse("(?=a)"));
    assertEquals(new RegexNode.Lookahead(true, literal('a')), parse("(?!a)"));
    assertEquals(new RegexNode.Group(true, new RegexNode.Empty()), parse("()"));
  }

  @Test
  void aQuantifierHasItsLimitsAndCanBeLazy() {
    int unbounded = RegexNode.Repeat.UNBOUNDED;

    assertEquals(new RegexNode.Repeat(literal('a'), 0, unbounded, false), parse("a*"));
    assertEquals(new RegexNode.Repeat(literal('a'), 1, unbounded, false), parse("a+"));
    assertEquals(new RegexNode.Repeat(literal('a'), 0, 1, false), parse("a?"));
    assertEquals(new RegexNode.Repeat(literal('a'), 2, 2, false), parse("a{2}"));
    assertEquals(new RegexNode.Repeat(literal('a'), 2, unbounded, false), parse("a{2,}"));
    assertEquals(new RegexNode.Repeat(literal('a'), 2, 65535, false), parse("a{2,65535}"));
    assertEquals(new RegexNode.Repeat(literal('a'), 0, unbounded, true), parse("a*?"));
    assertEquals(new RegexNode.Repeat(literal('a'), 0, 1, true), parse("a??"));
    assertEquals(new RegexNode.Repeat(literal('a'), 2, 3, true), parse("a{2,3}?"));
    assertTrue(((RegexNode.Repeat) parse("a{0,}")).unbounded());
    assertFalse(((RegexNode.Repeat) parse("a{0,65535}")).unbounded());
  }

  @Test
  void theClassEscapesHaveTheSetsOfTheDialect() {
    assertEquals(set('0', '9'), parse("\\d"));
    assertEquals(set('0', '9', 'A', 'Z', '_', '_', 'a', 'z'), parse("\\w"));
    assertTrue(contains("\\s", 0xFEFF));
    assertTrue(contains("\\s", 0x2028));
    assertFalse(contains("\\s", 0x1C));
    assertFalse(contains("\\s", 0x85));
    // U+0661 ARABIC-INDIC DIGIT ONE is a digit for Unicode, but it is not one for the dialect.
    assertTrue(contains("\\D", 0x0661));
    assertTrue(contains("\\W", 0xE9));
    assertFalse(contains("\\W", '_'));
    assertTrue(contains("\\S", 'x'));
    assertFalse(contains("\\S", 0xA0));
  }

  @Test
  void theDotIsEachCodePointThatIsNotALineTerminator() {
    assertEquals(
        new RegexNode.CharSet(
            CodePointRange.complement(
                CodePointRange.setOf(0x0A, 0x0A, 0x0D, 0x0D, 0x2028, 0x2029))),
        parse("."));
    assertTrue(contains(".", 0x85));
    assertTrue(contains(".", 0x1F600));
    assertFalse(contains(".", '\r'));
  }

  @Test
  void anEscapeIsTheCodePointThatItWrites() {
    assertEquals(literal('A'), parse("\\x41"));
    assertEquals(literal('B'), parse("\\u0042"));
    assertEquals(literal('C'), parse("\\u{43}"));
    assertEquals(literal(0x1F600), parse("\\u{01F600}"));
    assertEquals(literal(0), parse("\\0"));
    assertEquals(literal('\t'), parse("\\t"));
    assertEquals(literal('\n'), parse("\\n"));
    assertEquals(literal(0x0B), parse("\\v"));
    assertEquals(literal('\f'), parse("\\f"));
    assertEquals(literal('\r'), parse("\\r"));
    for (char syntax : "^$\\.*+?()[]{}|/".toCharArray()) {
      assertEquals(literal(syntax), parse("\\" + syntax));
    }
    // U+1F600 GRINNING FACE is two surrogates in UTF-16, and it is one literal.
    assertEquals(literal(0x1F600), parse("😀"));
  }

  @Test
  void aCharacterClassIsTheUnionOfItsMembers() {
    assertEquals(set('a', 'c', 'x', 'x'), parse("[xa-c]"));
    assertEquals(set('0', '9', ' ', ' ', '-', '-'), parse("[\\d -]"));
    assertEquals(set('-', '-', 'a', 'a'), parse("[-a]"));
    assertEquals(set('-', '-', 'a', 'a', 'z', 'z'), parse("[a\\-z]"));
    assertEquals(set(0x08, 0x08), parse("[\\b]"));
    assertEquals(set('^', '^', 'a', 'a'), parse("[a^]"));
    assertEquals(set('.', '.', '*', '*', '(', '('), parse("[.*(]"));
    assertEquals(set(0x1F600, 0x1F64F), parse("[\\u{1F600}-\\u{1F64F}]"));
    assertEquals(set(0x1F600, 0x1F64F), parse("[😀-🙏]"));
    assertEquals(
        new RegexNode.CharSet(CodePointRange.complement(CodePointRange.setOf('0', '9'))),
        parse("[^\\d]"));
    assertEquals(new RegexNode.CharSet(List.of()), parse("[^\\d\\D]"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // Constructs that the dialect does not permit.
        "(a)\\1",
        "\\k<n>",
        "(?<=a)b",
        "(?<!a)b",
        "(?<n>a)",
        "(?i)a",
        "\\p{L}",
        "\\P{L}",
        "\\cA",
        "\\01",
        "\\a",
        "\\-",
        "\\ ",
        "\\é",
        // Quantifiers.
        "a++",
        "a{2}{3}",
        "a**",
        "^*",
        "$+",
        "\\b?",
        "(?=a)+",
        "(?!a){2}",
        "a{",
        "a{,2}",
        "a{2",
        "a{2,3",
        "a{2,x}",
        "a{3,2}",
        "a{70000}",
        "a{1,70000}",
        "a{99999999999}",
        "*a",
        "+",
        "?",
        "(*)",
        "a|*",
        "{2}",
        "x}",
        "x]",
        // Character classes.
        "[]",
        "[^]",
        "[z-a]",
        "[\\d-z]",
        "[a-\\d]",
        "[a[b]]",
        "[a&&b]",
        "[a--b]",
        "[\\B]",
        "[a",
        "[a-",
        "[invalid(",
        // Groups.
        "(a",
        "a)",
        "(?:a",
        "(?",
        // Escapes.
        "\\",
        "\\x4",
        "\\x4g",
        "\\u004",
        "\\u{}",
        "\\u{41",
        "\\u{4g}",
        "\\u{110000}",
        "\\uD800",
        "\\u{DFFF}",
        // A surrogate that has no partner.
        "\uD800",
        "a\uDC00"
      })
  void aPatternThatIsNotInTheDialectIsASyntaxProblem(String pattern) {
    assertFalse(syntaxProblem(pattern).isEmpty());
  }

  @Test
  void theMessageOfASyntaxProblemNamesTheConstruct() {
    assertEquals("Backreference is not supported", syntaxProblem("(a)\\1"));
    assertEquals(
        "Lookbehind, named groups and inline flags are not supported", syntaxProblem("(?<=a)b"));
    assertEquals("Unsupported escape sequence", syntaxProblem("\\p{L}"));
    assertEquals("Octal escape is not supported", syntaxProblem("\\01"));
    assertEquals("Quantifier on an assertion is not supported", syntaxProblem("^*"));
    assertEquals("Possessive or chained quantifiers are not supported", syntaxProblem("a++"));
    assertEquals("Quantifier bound exceeds 65535", syntaxProblem("a{70000}"));
    assertEquals("Quantifier minimum exceeds maximum", syntaxProblem("a{3,2}"));
    assertEquals("Malformed brace quantifier", syntaxProblem("a{,2}"));
    assertEquals("Nothing to repeat", syntaxProblem("*a"));
    assertEquals("Unescaped brace or bracket is not supported", syntaxProblem("x}"));
    assertEquals("Empty character class", syntaxProblem("[]"));
    assertEquals("Unclosed character class", syntaxProblem("[invalid("));
    assertEquals("Character class range out of order", syntaxProblem("[z-a]"));
    assertEquals("Character class escape cannot form a range", syntaxProblem("[\\d-z]"));
    assertEquals("Nested character class is not supported", syntaxProblem("[a[b]]"));
    assertEquals("Character class intersection is not supported", syntaxProblem("[a&&b]"));
    assertEquals("Character class subtraction is not supported", syntaxProblem("[a--b]"));
    assertEquals("\\B is not supported inside a character class", syntaxProblem("[\\B]"));
    assertEquals("Unclosed group", syntaxProblem("(a"));
    assertEquals("Unmatched closing parenthesis", syntaxProblem("a)"));
    assertEquals("Trailing backslash in pattern", syntaxProblem("\\"));
    assertEquals("Invalid hexadecimal escape", syntaxProblem("\\x4"));
    assertEquals("Malformed braced Unicode escape", syntaxProblem("\\u{}"));
    assertEquals("Unicode escape exceeds U+10FFFF", syntaxProblem("\\u{110000}"));
    assertEquals("Surrogate code point escape is not supported", syntaxProblem("\\uD800"));
    assertEquals("Lone surrogate in pattern", syntaxProblem("\uD800"));
  }

  @Test
  void aPatternOfTheMaximumLengthWithNestedGroupsIsParsed() {
    String pattern = "(".repeat(128) + ")".repeat(128);

    RegexNode node = parse(pattern);
    int depth = 0;
    while (node instanceof RegexNode.Group group) {
      node = group.child();
      depth++;
    }
    assertEquals(128, depth);
    assertEquals(new RegexNode.Empty(), node);
  }
}
