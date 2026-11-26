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

import com.google.a2ui.core.common.RegexNode.AnchorKind;
import java.util.ArrayList;
import java.util.List;

/**
 * The parser of the A2UI regex dialect.
 *
 * <p>The dialect is a subset of the ECMAScript patterns in Unicode mode. The parser reads code
 * points, not {@code char} values. It refuses each construct that the dialect does not permit, for
 * example a backreference, a lookbehind, a named group and {@code \p{...}}.
 *
 * <p>The meaning of each character set comes from the dialect:
 *
 * <ul>
 *   <li>{@code \d} is an ASCII digit, and {@code \w} is an ASCII letter, an ASCII digit or an
 *       underscore.
 *   <li>{@code \s} is the ECMAScript sets WhiteSpace and LineTerminator.
 *   <li>{@code .} is each code point that is not a line terminator.
 * </ul>
 */
final class RegexParser {
  /** The largest number that a quantifier can contain. */
  private static final int MAX_QUANTIFIER_BOUND = 65535;

  private static final List<CodePointRange> DIGIT = CodePointRange.setOf('0', '9');
  private static final List<CodePointRange> WORD =
      CodePointRange.setOf('0', '9', 'A', 'Z', '_', '_', 'a', 'z');
  private static final List<CodePointRange> SPACE =
      CodePointRange.setOf(
          0x09, 0x0D, 0x20, 0x20, 0xA0, 0xA0, 0x1680, 0x1680, 0x2000, 0x200A, 0x2028, 0x2029,
          0x202F, 0x202F, 0x205F, 0x205F, 0x3000, 0x3000, 0xFEFF, 0xFEFF);
  private static final List<CodePointRange> NOT_DIGIT = CodePointRange.complement(DIGIT);
  private static final List<CodePointRange> NOT_WORD = CodePointRange.complement(WORD);
  private static final List<CodePointRange> NOT_SPACE = CodePointRange.complement(SPACE);

  /** Each code point that is not a line terminator. */
  private static final List<CodePointRange> DOT =
      CodePointRange.complement(CodePointRange.setOf(0x0A, 0x0A, 0x0D, 0x0D, 0x2028, 0x2029));

  /** The characters that a backslash can make literal. */
  private static final String SYNTAX_CHARACTERS = "^$\\.*+?()[]{}|/";

  private static final String MALFORMED_BRACES = "Malformed brace quantifier";
  private static final String UNSUPPORTED_ESCAPE = "Unsupported escape sequence";

  /** The value of {@link #peek()} at the end of the pattern. */
  private static final int END = -1;

  private final int[] codePoints;
  private int position;

  private RegexParser(int[] codePoints) {
    this.codePoints = codePoints;
  }

  /**
   * Parses a pattern.
   *
   * @param pattern the pattern as the agent wrote it
   * @return the tree of the pattern
   * @throws RegexSyntaxException if the pattern is not in the dialect
   */
  static RegexNode parse(String pattern) throws RegexSyntaxException {
    int[] codePoints = pattern.codePoints().toArray();
    for (int codePoint : codePoints) {
      if (CodePointRange.isSurrogate(codePoint)) {
        throw new RegexSyntaxException("Lone surrogate in pattern");
      }
    }
    RegexParser parser = new RegexParser(codePoints);
    RegexNode tree = parser.disjunction();
    if (!parser.atEnd()) {
      throw new RegexSyntaxException("Unmatched closing parenthesis");
    }
    return tree;
  }

  // The scanner.

  private boolean atEnd() {
    return position >= codePoints.length;
  }

  private int peek() {
    return peek(0);
  }

  private int peek(int offset) {
    int index = position + offset;
    return index < codePoints.length ? codePoints[index] : END;
  }

  private int next() throws RegexSyntaxException {
    if (atEnd()) {
      throw new RegexSyntaxException("Unexpected end of pattern");
    }
    return codePoints[position++];
  }

  private boolean eat(char character) {
    if (peek() != character) {
      return false;
    }
    position++;
    return true;
  }

  // The grammar.

  private RegexNode disjunction() throws RegexSyntaxException {
    List<RegexNode> branches = new ArrayList<>();
    branches.add(alternative());
    while (eat('|')) {
      branches.add(alternative());
    }
    return branches.size() == 1 ? branches.getFirst() : new RegexNode.Alt(List.copyOf(branches));
  }

  private RegexNode alternative() throws RegexSyntaxException {
    List<RegexNode> items = new ArrayList<>();
    while (!atEnd() && peek() != '|' && peek() != ')') {
      items.add(term());
    }
    return switch (items.size()) {
      case 0 -> new RegexNode.Empty();
      case 1 -> items.getFirst();
      default -> new RegexNode.Concat(List.copyOf(items));
    };
  }

  private static boolean startsQuantifier(int codePoint) {
    return codePoint == '*' || codePoint == '+' || codePoint == '?' || codePoint == '{';
  }

  private RegexNode term() throws RegexSyntaxException {
    RegexNode atom = atom();
    if (!startsQuantifier(peek())) {
      return atom;
    }
    if (atom instanceof RegexNode.Anchor || atom instanceof RegexNode.Lookahead) {
      throw new RegexSyntaxException("Quantifier on an assertion is not supported");
    }
    int min;
    int max;
    switch (next()) {
      case '*' -> {
        min = 0;
        max = RegexNode.Repeat.UNBOUNDED;
      }
      case '+' -> {
        min = 1;
        max = RegexNode.Repeat.UNBOUNDED;
      }
      case '?' -> {
        min = 0;
        max = 1;
      }
      default -> {
        min = decimal();
        if (eat('}')) {
          max = min;
        } else if (!eat(',')) {
          throw new RegexSyntaxException(MALFORMED_BRACES);
        } else if (eat('}')) {
          max = RegexNode.Repeat.UNBOUNDED;
        } else {
          max = decimal();
          if (!eat('}')) {
            throw new RegexSyntaxException(MALFORMED_BRACES);
          }
          if (max < min) {
            throw new RegexSyntaxException("Quantifier minimum exceeds maximum");
          }
        }
      }
    }
    boolean lazy = eat('?');
    if (startsQuantifier(peek())) {
      throw new RegexSyntaxException("Possessive or chained quantifiers are not supported");
    }
    return new RegexNode.Repeat(atom, min, max, lazy);
  }

  private static boolean isDigit(int codePoint) {
    return codePoint >= '0' && codePoint <= '9';
  }

  /** Reads the decimal number of a quantifier. */
  private int decimal() throws RegexSyntaxException {
    if (!isDigit(peek())) {
      throw new RegexSyntaxException(MALFORMED_BRACES);
    }
    int value = 0;
    while (isDigit(peek())) {
      value = value * 10 + (next() - '0');
      if (value > MAX_QUANTIFIER_BOUND) {
        throw new RegexSyntaxException("Quantifier bound exceeds " + MAX_QUANTIFIER_BOUND);
      }
    }
    return value;
  }

  private static RegexNode.CharSet single(int codePoint) {
    return new RegexNode.CharSet(List.of(CodePointRange.of(codePoint)));
  }

  private RegexNode atom() throws RegexSyntaxException {
    int codePoint = next();
    return switch (codePoint) {
      case '^' -> new RegexNode.Anchor(AnchorKind.START);
      case '$' -> new RegexNode.Anchor(AnchorKind.END);
      case '.' -> new RegexNode.CharSet(DOT);
      case '[' -> characterClass();
      case '(' -> group();
      case '\\' ->
          switch (escape(false)) {
            case Escape.Assertion assertion -> new RegexNode.Anchor(assertion.kind());
            case Escape.Ranges set -> new RegexNode.CharSet(set.ranges());
            case Escape.Single literal -> single(literal.codePoint());
          };
      case '{', '}', ']' ->
          throw new RegexSyntaxException("Unescaped brace or bracket is not supported");
      case '*', '+', '?' -> throw new RegexSyntaxException("Nothing to repeat");
      default -> single(codePoint);
    };
  }

  private RegexNode group() throws RegexSyntaxException {
    char modifier = ' ';
    if (eat('?')) {
      if (eat(':')) {
        modifier = ':';
      } else if (eat('=')) {
        modifier = '=';
      } else if (eat('!')) {
        modifier = '!';
      } else {
        throw new RegexSyntaxException(
            "Lookbehind, named groups and inline flags are not supported");
      }
    }
    RegexNode body = disjunction();
    if (!eat(')')) {
      throw new RegexSyntaxException("Unclosed group");
    }
    return switch (modifier) {
      case ' ' -> new RegexNode.Group(true, body);
      case ':' -> new RegexNode.Group(false, body);
      default -> new RegexNode.Lookahead(modifier == '!', body);
    };
  }

  // Escapes.

  /** The meaning of the text after a backslash. */
  private sealed interface Escape {
    /** One code point. */
    record Single(int codePoint) implements Escape {}

    /** A class escape, for example {@code \d}. */
    record Ranges(List<CodePointRange> ranges) implements Escape {}

    /** {@code \b} or {@code \B} outside a character class. */
    record Assertion(AnchorKind kind) implements Escape {}
  }

  private static int hexDigit(int codePoint) {
    return codePoint >= 0 && codePoint < 128 ? Character.digit(codePoint, 16) : -1;
  }

  private int fixedHex(int digits) throws RegexSyntaxException {
    int value = 0;
    for (int i = 0; i < digits; i++) {
      int digit = hexDigit(peek());
      if (digit < 0) {
        throw new RegexSyntaxException("Invalid hexadecimal escape");
      }
      position++;
      value = value * 16 + digit;
    }
    return value;
  }

  /** Reads the digits of a Unicode escape: four digits, or one or more digits between braces. */
  private int unicodeEscape() throws RegexSyntaxException {
    int value;
    if (eat('{')) {
      value = 0;
      int count = 0;
      while (!atEnd() && peek() != '}') {
        int digit = hexDigit(next());
        if (digit < 0) {
          throw new RegexSyntaxException("Invalid braced Unicode escape");
        }
        value = value * 16 + digit;
        count++;
        if (value > CodePointRange.MAX_CODE_POINT) {
          throw new RegexSyntaxException("Unicode escape exceeds U+10FFFF");
        }
      }
      if (count == 0 || !eat('}')) {
        throw new RegexSyntaxException("Malformed braced Unicode escape");
      }
    } else {
      value = fixedHex(4);
    }
    if (CodePointRange.isSurrogate(value)) {
      throw new RegexSyntaxException("Surrogate code point escape is not supported");
    }
    return value;
  }

  /**
   * Reads the text after a backslash.
   *
   * @param inClass true if the backslash is in a character class
   */
  private Escape escape(boolean inClass) throws RegexSyntaxException {
    if (atEnd()) {
      throw new RegexSyntaxException("Trailing backslash in pattern");
    }
    int codePoint = next();
    return switch (codePoint) {
      case 'd' -> new Escape.Ranges(DIGIT);
      case 'D' -> new Escape.Ranges(NOT_DIGIT);
      case 'w' -> new Escape.Ranges(WORD);
      case 'W' -> new Escape.Ranges(NOT_WORD);
      case 's' -> new Escape.Ranges(SPACE);
      case 'S' -> new Escape.Ranges(NOT_SPACE);
      case 't' -> new Escape.Single(0x09);
      case 'n' -> new Escape.Single(0x0A);
      case 'v' -> new Escape.Single(0x0B);
      case 'f' -> new Escape.Single(0x0C);
      case 'r' -> new Escape.Single(0x0D);
      // In a character class, \b is the backspace character.
      case 'b' ->
          inClass ? new Escape.Single(0x08) : new Escape.Assertion(AnchorKind.WORD_BOUNDARY);
      case 'B' -> {
        if (inClass) {
          throw new RegexSyntaxException("\\B is not supported inside a character class");
        }
        yield new Escape.Assertion(AnchorKind.NOT_WORD_BOUNDARY);
      }
      case '0' -> {
        if (isDigit(peek())) {
          throw new RegexSyntaxException("Octal escape is not supported");
        }
        yield new Escape.Single(0);
      }
      case '1', '2', '3', '4', '5', '6', '7', '8', '9' ->
          throw new RegexSyntaxException("Backreference is not supported");
      case 'x' -> new Escape.Single(fixedHex(2));
      case 'u' -> new Escape.Single(unicodeEscape());
      default -> {
        if (SYNTAX_CHARACTERS.indexOf(codePoint) < 0 && !(inClass && codePoint == '-')) {
          throw new RegexSyntaxException(UNSUPPORTED_ESCAPE);
        }
        yield new Escape.Single(codePoint);
      }
    };
  }

  // Character classes.

  /**
   * One member of a character class.
   *
   * @param codePoint the code point of the member, or {@link #END} if the member is a class escape
   * @param ranges the set of the member
   */
  private record ClassAtom(int codePoint, List<CodePointRange> ranges) {}

  private ClassAtom classAtom() throws RegexSyntaxException {
    if (eat('\\')) {
      return switch (escape(true)) {
        case Escape.Single literal ->
            new ClassAtom(literal.codePoint(), List.of(CodePointRange.of(literal.codePoint())));
        case Escape.Ranges set -> new ClassAtom(END, set.ranges());
        case Escape.Assertion _ -> throw new RegexSyntaxException(UNSUPPORTED_ESCAPE);
      };
    }
    if (peek() == '[') {
      throw new RegexSyntaxException("Nested character class is not supported");
    }
    if (peek() == '&' && peek(1) == '&') {
      throw new RegexSyntaxException("Character class intersection is not supported");
    }
    int literal = next();
    return new ClassAtom(literal, List.of(CodePointRange.of(literal)));
  }

  /** Reads a character class. The opening bracket is before the position. */
  private RegexNode.CharSet characterClass() throws RegexSyntaxException {
    boolean negated = eat('^');
    List<CodePointRange> ranges = new ArrayList<>();
    while (!atEnd() && peek() != ']') {
      ClassAtom first = classAtom();
      // A hyphen before the closing bracket is a literal.
      if (peek() != '-' || peek(1) == ']') {
        ranges.addAll(first.ranges());
        continue;
      }
      position++;
      if (peek() == '-') {
        throw new RegexSyntaxException("Character class subtraction is not supported");
      }
      ClassAtom second = classAtom();
      if (first.codePoint() == END || second.codePoint() == END) {
        throw new RegexSyntaxException("Character class escape cannot form a range");
      }
      if (first.codePoint() > second.codePoint()) {
        throw new RegexSyntaxException("Character class range out of order");
      }
      ranges.add(new CodePointRange(first.codePoint(), second.codePoint()));
    }
    if (!eat(']')) {
      throw new RegexSyntaxException("Unclosed character class");
    }
    if (ranges.isEmpty()) {
      throw new RegexSyntaxException("Empty character class");
    }
    return new RegexNode.CharSet(
        negated ? CodePointRange.complement(ranges) : CodePointRange.normalize(ranges));
  }
}
