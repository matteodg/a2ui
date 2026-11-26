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

import com.google.a2ui.core.exceptions.A2uiExpressionError;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A regular expression in the A2UI regex dialect that obeys the static safety rules of the
 * protocol.
 *
 * <p>An agent writes the pattern. Thus the core does not give the pattern to {@code
 * java.util.regex} as it is. {@link #compile(String)} does these steps:
 *
 * <ol>
 *   <li>It parses the pattern as the dialect, a subset of the ECMAScript patterns.
 *   <li>It refuses a pattern that can use exponential time for a match.
 *   <li>It translates the pattern for {@code java.util.regex}.
 * </ol>
 *
 * <p>A match is case-sensitive. {@code \d}, {@code \w} and {@code \b} know only ASCII characters.
 * {@code .} matches each code point that is not a line terminator. {@code ^} and {@code $} match
 * only at the two ends of the value.
 *
 * <p>If a function of your catalog has a pattern argument, compile the pattern with this class, as
 * the {@code regex} function of the basic catalog does. If your code uses a pattern many times,
 * keep the instance. The class does not keep the compiled patterns.
 *
 * <p>An instance is immutable. It is safe to use from more than one thread.
 */
public final class A2uiRegex {
  /** The maximum length of a pattern, in code points. */
  public static final int MAX_PATTERN_CODE_POINTS = 256;

  /** The maximum length of a value that can match, in code points. */
  public static final int MAX_VALUE_CODE_POINTS = 4096;

  /**
   * The stack size of the thread that completes a deep match. This is reserved address space. The
   * thread uses only the memory that the match needs.
   */
  private static final long DEEP_STACK_BYTES = 256L * 1024 * 1024;

  private final String pattern;
  private final Pattern compiled;

  private A2uiRegex(String pattern, Pattern compiled) {
    this.pattern = pattern;
    this.compiled = compiled;
  }

  /**
   * Examines and compiles a pattern.
   *
   * @param pattern the pattern in the A2UI regex dialect
   * @return the compiled pattern
   * @throws A2uiExpressionError if the pattern is refused. The message contains one token of the
   *     protocol: {@code PATTERN_TOO_LONG}, {@code UNSUPPORTED_SYNTAX}, {@code NESTED_QUANTIFIER},
   *     {@code OVERLAPPING_ALTERNATION} or {@code ADJACENT_OVERLAP}.
   */
  public static A2uiRegex compile(String pattern) {
    Objects.requireNonNull(pattern, "pattern");
    int length = pattern.codePointCount(0, pattern.length());
    if (length > MAX_PATTERN_CODE_POINTS) {
      throw refused(
          RegexRejection.PATTERN_TOO_LONG,
          "the pattern has " + length + " code points and the limit is " + MAX_PATTERN_CODE_POINTS);
    }
    RegexNode tree;
    try {
      tree = RegexParser.parse(pattern);
    } catch (RegexSyntaxException e) {
      throw refused(RegexRejection.UNSUPPORTED_SYNTAX, e.getMessage() + ": " + pattern);
    }
    Optional<RegexRejection> unsafe = RegexSafety.examine(tree);
    if (unsafe.isPresent()) {
      throw refused(unsafe.get(), "the pattern could be slow to match: " + pattern);
    }
    try {
      return new A2uiRegex(pattern, Pattern.compile(RegexTranslator.translate(tree)));
    } catch (PatternSyntaxException e) {
      // The translation uses only syntax that the engine knows. Thus this is a limit of the engine.
      throw refused(RegexRejection.UNSUPPORTED_SYNTAX, e.getDescription() + ": " + pattern);
    }
  }

  private static A2uiExpressionError refused(RegexRejection reason, String detail) {
    return new A2uiExpressionError(
        "Invalid or unsafe regex pattern (" + reason.name() + "): " + detail);
  }

  /**
   * Gets the pattern.
   *
   * @return the pattern as the agent wrote it
   */
  public String pattern() {
    return pattern;
  }

  /**
   * Tells if a part of a value matches the pattern, as {@code RegExp.prototype.test} of ECMAScript
   * does.
   *
   * @param value the value to examine
   * @return true if the value contains a match. A value that is longer than {@link
   *     #MAX_VALUE_CODE_POINTS} never matches.
   * @throws A2uiExpressionError if the match cannot complete
   */
  public boolean test(String value) {
    Objects.requireNonNull(value, "value");
    if (value.length() > MAX_VALUE_CODE_POINTS
        && value.codePointCount(0, value.length()) > MAX_VALUE_CODE_POINTS) {
      return false;
    }
    try {
      return compiled.matcher(value).find();
    } catch (StackOverflowError e) {
      return testOnDeepStack(value);
    }
  }

  /**
   * Does the match again on a thread that has a large stack.
   *
   * <p>{@code java.util.regex} uses one level of recursion for each repetition of a group such as
   * {@code (ab|c)*}. Thus a long value can use more stack than the thread of the caller has,
   * although the time of the match is linear.
   *
   * <p>The caller waits for the thread, and the thread has no access to the models. Thus the core
   * stays confined to the thread of the caller, and no lock is necessary.
   */
  private boolean testOnDeepStack(String value) {
    DeepMatch match = new DeepMatch(compiled, value);
    Thread thread = new Thread(null, match, "a2ui-regex", DEEP_STACK_BYTES);
    thread.setDaemon(true);
    thread.start();
    try {
      thread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new A2uiExpressionError("Interrupted while matching the regex pattern: " + pattern, e);
    }
    if (!match.completed) {
      throw new A2uiExpressionError("The regex pattern is too complex for this value: " + pattern);
    }
    return match.found;
  }

  /**
   * One match on the thread that has a large stack.
   *
   * <p>The caller reads the fields only after {@link Thread#join()} returns. Thus the fields need
   * no lock.
   */
  private static final class DeepMatch implements Runnable {
    private final Pattern compiled;
    private final String value;
    private boolean found;
    private boolean completed;

    DeepMatch(Pattern compiled, String value) {
      this.compiled = compiled;
      this.value = value;
    }

    @Override
    public void run() {
      try {
        found = compiled.matcher(value).find();
        completed = true;
      } catch (StackOverflowError e) {
        // The match is not completed. The caller reports it.
      }
    }
  }

  /**
   * Gets the pattern.
   *
   * @return the pattern as the agent wrote it
   */
  @Override
  public String toString() {
    return pattern;
  }
}
