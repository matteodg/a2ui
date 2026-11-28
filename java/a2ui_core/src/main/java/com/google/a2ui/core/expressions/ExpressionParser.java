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

package com.google.a2ui.core.expressions;

import com.google.a2ui.core.common.Uax31;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads the <code>${...}</code> template syntax that {@code formatString} interpolates.
 *
 * <p>Between <code>${</code> and <code>}</code> there is one expression:
 *
 * <ul>
 *   <li>a quoted string, a number, {@code true}, {@code false} or {@code null}
 *   <li>a data path such as {@code /user/name} or {@code name}
 *   <li>a function call with named arguments, such as {@code add(a: 1, b: /count)}
 *   <li>a different interpolation
 * </ul>
 *
 * <p>The text <code>\${</code> is a literal <code>${</code>.
 */
public final class ExpressionParser {
  /**
   * The largest nesting depth of a template. An interpolation in an interpolation and a function
   * argument each add one level. Thus this limit is also the limit of the recursion.
   */
  public static final int MAX_DEPTH = 100;

  /** The largest length of a template, in UTF-16 code units. */
  public static final int MAX_TEMPLATE_LENGTH = 10_000;

  /** The largest number of parts in a template. */
  public static final int MAX_TEMPLATE_PARTS = 1_000;

  private static final int END = -1;
  private static final String OPEN = "${";
  private static final String ESCAPED_OPEN = "\\${";

  /** The result for empty content. The parser counts it and then removes it. */
  private static final Expression EMPTY = new Expression.Literal("");

  /**
   * An optional sign, a mantissa and an optional exponent.
   *
   * <p>The mantissa is digits with an optional point and more digits ({@code 5}, {@code 5.}, {@code
   * 5.25}), or a point and digits ({@code .5}). The parser does not use the number parser of the
   * platform. Thus each A2UI implementation accepts the same literals.
   */
  private static final Pattern NUMBER_LITERAL =
      Pattern.compile("[+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

  private ExpressionParser() {}

  /**
   * Divides a template into its parts, in sequence.
   *
   * <p>The text that is not in an interpolation becomes {@link Expression.Literal} strings. Empty
   * content, such as <code>${null}</code>, gives no part.
   *
   * @param template the template to read
   * @return the parts of the template, as a read-only list
   * @throws A2uiExpressionError if the template is not correct or is larger than a limit
   */
  public static List<Expression> parseTemplate(String template) {
    if (template.length() > MAX_TEMPLATE_LENGTH) {
      throw new A2uiExpressionError(
          "Expression template length ("
              + template.length()
              + ") exceeds maximum limit ("
              + MAX_TEMPLATE_LENGTH
              + ")");
    }
    PartList parts = new PartList();
    StringBuilder text = new StringBuilder();
    Scanner scanner = new Scanner(template);
    while (!scanner.atEnd()) {
      if (scanner.startsWith(OPEN)) {
        parts.addText(text);
        scanner.pos += OPEN.length();
        parts.add(parseExpression(interpolationContent(scanner), 1));
      } else if (scanner.startsWith(ESCAPED_OPEN)) {
        text.append(OPEN);
        scanner.pos += ESCAPED_OPEN.length();
      } else {
        text.appendCodePoint(scanner.advance());
      }
    }
    parts.addText(text);
    return Collections.unmodifiableList(parts.parts);
  }

  /** Collects the parts of a template and applies the limit on their number. */
  private static final class PartList {
    private final List<Expression> parts = new ArrayList<>();
    private int count;

    private void add(Expression part) {
      // The parser counts empty content against the limit and then removes it. The other A2UI
      // implementations do the same, thus each one accepts or rejects the same templates.
      count++;
      if (count > MAX_TEMPLATE_PARTS) {
        throw new A2uiExpressionError(
            "Expression parts count exceeds maximum limit (" + MAX_TEMPLATE_PARTS + ")");
      }
      if (!part.equals(EMPTY)) {
        parts.add(part);
      }
    }

    private void addText(StringBuilder text) {
      if (!text.isEmpty()) {
        add(new Expression.Literal(text.toString()));
        text.setLength(0);
      }
    }
  }

  /**
   * Gets the text between a <code>${</code> and its <code>}</code>.
   *
   * <p>The scanner must be immediately after the <code>${</code>. The method moves the scanner
   * after the closing brace. It ignores the braces in quoted strings.
   */
  private static String interpolationContent(Scanner scanner) {
    int start = scanner.pos;
    int openBraces = 1;
    while (!scanner.atEnd() && openBraces > 0) {
      int c = scanner.advance();
      if (c == '{') {
        openBraces++;
      } else if (c == '}') {
        openBraces--;
      } else if (c == '\'' || c == '"') {
        skipQuoted(scanner, c);
      }
    }
    if (openBraces > 0) {
      throw new A2uiExpressionError("Unclosed interpolation: missing '}'");
    }
    return scanner.input.substring(start, scanner.pos - 1);
  }

  private static void skipQuoted(Scanner scanner, int quote) {
    while (!scanner.atEnd()) {
      int c = scanner.advance();
      if (c == quote) {
        return;
      }
      if (c == '\\') {
        if (scanner.atEnd()) {
          break;
        }
        scanner.advance();
      }
    }
    throw new A2uiExpressionError(
        "Unclosed string literal in unclosed interpolation: missing " + Character.toString(quote));
  }

  private static Expression parseExpression(String source, int depth) {
    String trimmed = source.strip();
    if (trimmed.isEmpty()) {
      return EMPTY;
    }
    Scanner scanner = new Scanner(trimmed);
    Expression result = parseValue(scanner, depth);
    scanner.skipWhitespace();
    if (!scanner.atEnd()) {
      throw new A2uiExpressionError(
          "Unexpected characters at end of expression: '" + trimmed.substring(scanner.pos) + "'");
    }
    return result;
  }

  private static Expression parseValue(Scanner scanner, int depth) {
    if (depth > MAX_DEPTH) {
      throw new A2uiExpressionError("Max recursion depth reached in parse");
    }
    scanner.skipWhitespace();
    if (scanner.atEnd()) {
      return EMPTY;
    }
    if (scanner.startsWith(OPEN)) {
      scanner.pos += OPEN.length();
      return parseExpression(interpolationContent(scanner), depth + 1);
    }
    int first = scanner.peek();
    if (first == '\'' || first == '"') {
      return new Expression.Literal(parseStringLiteral(scanner));
    }
    if (isNumberStart(scanner)) {
      return new Expression.Literal(parseNumberLiteral(scanner));
    }
    if (scanner.takeKeyword("true")) {
      return new Expression.Literal(true);
    }
    if (scanner.takeKeyword("false")) {
      return new Expression.Literal(false);
    }
    if (scanner.takeKeyword("null")) {
      return EMPTY;
    }

    String token = scanPathOrName(scanner);
    scanner.skipWhitespace();
    if (scanner.peek() == '(') {
      if (token.indexOf('~') >= 0) {
        throw new A2uiExpressionError(
            "Invalid function name '" + token + "': '~' is not allowed in function names");
      }
      return parseCall(token, scanner, depth);
    }
    if (token.startsWith("@")) {
      throw new A2uiExpressionError("Expected '(' after function name '" + token + "'");
    }
    return token.isEmpty() ? EMPTY : new Expression.Path(token);
  }

  /** Reads a data path, a function name or a system function name that starts with {@code @}. */
  private static String scanPathOrName(Scanner scanner) {
    int start = scanner.pos;
    if (scanner.peek() == '@') {
      int next = scanner.peekAt(scanner.pos + 1);
      if (!isAsciiLetter(next) && next != '_') {
        throw new A2uiExpressionError("Invalid identifier starting with '@' in expression");
      }
      scanner.advance();
      while (true) {
        int c = scanner.peek();
        if (!isAsciiLetter(c) && !isDigit(c) && c != '_') {
          break;
        }
        scanner.advance();
      }
      return scanner.input.substring(start, scanner.pos);
    }
    while (!scanner.atEnd()) {
      int c = scanner.peek();
      if (c == '~') {
        int next = scanner.peekAt(scanner.pos + 1);
        if (next != '0' && next != '1') {
          String found = next == END ? "" : Character.toString(next);
          throw new A2uiExpressionError(
              "Invalid escape sequence '~" + found + "' in path: expected '~0' or '~1'");
        }
        scanner.pos += 2;
      } else if (isPathCharacter(c)) {
        scanner.advance();
      } else {
        break;
      }
    }
    return scanner.input.substring(start, scanner.pos);
  }

  private static Expression parseCall(String name, Scanner scanner, int depth) {
    // Go after the opening parenthesis.
    scanner.advance();
    scanner.skipWhitespace();
    Map<String, Expression> args = new LinkedHashMap<>();
    while (!scanner.atEnd() && scanner.peek() != ')') {
      int nameStart = scanner.pos;
      while (!scanner.atEnd() && Uax31.isIdentifierContinue(scanner.peek())) {
        scanner.advance();
      }
      String argName = scanner.input.substring(nameStart, scanner.pos);
      scanner.skipWhitespace();
      if (scanner.peek() != ':') {
        throw new A2uiExpressionError(
            "Expected ':' after argument name '" + argName + "' in function '" + name + "'");
      }
      scanner.advance();
      scanner.skipWhitespace();
      args.put(argName, parseValue(scanner, depth + 1));
      scanner.skipWhitespace();
      if (scanner.peek() == ',') {
        scanner.advance();
        scanner.skipWhitespace();
      }
    }
    if (scanner.peek() != ')') {
      throw new A2uiExpressionError("Expected ')' after function arguments for '" + name + "'");
    }
    scanner.advance();
    return new Expression.Call(name, args);
  }

  private static String parseStringLiteral(Scanner scanner) {
    int quote = scanner.advance();
    StringBuilder result = new StringBuilder();
    while (!scanner.atEnd()) {
      int c = scanner.advance();
      if (c == quote) {
        return result.toString();
      }
      if (c != '\\') {
        result.appendCodePoint(c);
        continue;
      }
      if (scanner.atEnd()) {
        throw new A2uiExpressionError(
            "Unclosed string literal: trailing backslash before end of input");
      }
      int escaped = scanner.advance();
      switch (escaped) {
        case 'n' -> result.append('\n');
        case 't' -> result.append('\t');
        case 'r' -> result.append('\r');
        default -> result.appendCodePoint(escaped);
      }
    }
    throw new A2uiExpressionError("Unclosed string literal: missing " + Character.toString(quote));
  }

  /**
   * Tells if the scanner is at a number literal.
   *
   * <p>A number literal starts with a digit, with a point before a digit, or with a sign before one
   * of these. The syntax has no arithmetic operators, thus a sign at this position is always part
   * of a literal. The path scanner reads a {@code -} or a {@code .} that is in a path such as
   * {@code a-1}.
   */
  private static boolean isNumberStart(Scanner scanner) {
    int first = scanner.peek();
    int at = first == '-' || first == '+' ? scanner.pos + 1 : scanner.pos;
    if (isDigit(scanner.peekAt(at))) {
      return true;
    }
    return scanner.peekAt(at) == '.' && isDigit(scanner.peekAt(at + 1));
  }

  private static Object parseNumberLiteral(Scanner scanner) {
    int start = scanner.pos;
    if (scanner.peek() == '-' || scanner.peek() == '+') {
      scanner.advance();
    }
    // Read each point and an incorrect exponent also. Thus the parser reports `1.2.3` and `1e` as
    // invalid number literals and not as unexpected characters.
    while (isDigit(scanner.peek()) || scanner.peek() == '.') {
      scanner.advance();
    }
    if (scanner.peek() == 'e' || scanner.peek() == 'E') {
      scanner.advance();
      if (scanner.peek() == '+' || scanner.peek() == '-') {
        scanner.advance();
      }
      while (isDigit(scanner.peek())) {
        scanner.advance();
      }
    }
    String text = scanner.input.substring(start, scanner.pos);
    if (!NUMBER_LITERAL.matcher(text).matches()) {
      throw new A2uiExpressionError("Invalid number literal: '" + text + "'");
    }
    boolean isInteger = text.indexOf('.') < 0 && text.indexOf('e') < 0 && text.indexOf('E') < 0;
    if (isInteger) {
      try {
        return Long.parseLong(text.startsWith("+") ? text.substring(1) : text);
      } catch (NumberFormatException e) {
        // The integer is too large for a long. Read it as a double.
      }
    }
    double value = Double.parseDouble(text);
    if (!Double.isFinite(value)) {
      throw new A2uiExpressionError("Number literal is out of range: '" + text + "'");
    }
    // Write negative zero as zero.
    return value == 0.0 ? 0.0 : value;
  }

  private static boolean isPathCharacter(int c) {
    return c == '/' || c == '.' || c == '-' || (c != END && Uax31.isIdentifierContinue(c));
  }

  private static boolean isDigit(int c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isAsciiLetter(int c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
  }

  /**
   * Reads the input by code point. Thus a character out of the Basic Multilingual Plane is one
   * unit.
   */
  private static final class Scanner {
    private final String input;
    private int pos;

    private Scanner(String input) {
      this.input = input;
    }

    private boolean atEnd() {
      return pos >= input.length();
    }

    /** Gets the code point at the current position, or {@link #END}. */
    private int peek() {
      return peekAt(pos);
    }

    /** Gets the code point at an index, or {@link #END}. */
    private int peekAt(int index) {
      return index < input.length() ? input.codePointAt(index) : END;
    }

    /** Gets the code point at the current position and moves after it. */
    private int advance() {
      int c = input.codePointAt(pos);
      pos += Character.charCount(c);
      return c;
    }

    private boolean startsWith(String prefix) {
      return input.startsWith(prefix, pos);
    }

    /** Moves after a keyword if it is at this position and is not the start of a longer path. */
    private boolean takeKeyword(String keyword) {
      if (!startsWith(keyword)) {
        return false;
      }
      int next = peekAt(pos + keyword.length());
      if (next != END && Uax31.isIdentifierContinue(next)) {
        return false;
      }
      pos += keyword.length();
      return true;
    }

    private void skipWhitespace() {
      while (!atEnd()) {
        char c = input.charAt(pos);
        if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
          break;
        }
        pos++;
      }
    }
  }
}
