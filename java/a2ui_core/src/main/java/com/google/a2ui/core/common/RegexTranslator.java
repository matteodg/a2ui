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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes the tree of a pattern of the A2UI regex dialect as a pattern of {@code java.util.regex}.
 *
 * <p>The result uses only constructs that have the same meaning in the two engines:
 *
 * <ul>
 *   <li>Each character set is a class of explicit code point ranges. Thus {@code \d}, {@code \w},
 *       {@code \s} and {@code .} have the meaning of the dialect, not the meaning of the engine.
 *   <li>{@code ^} is {@code \A} and {@code $} is {@code \z}. Thus the two anchors match only at the
 *       ends of the value. The {@code $} of the engine also matches before a last line terminator.
 *   <li>{@code \b} and {@code \B} are assertions about the ASCII word characters.
 * </ul>
 *
 * <p>The core uses the pattern only to tell if a value contains a match. The dialect has no
 * backreferences. Thus the translation is free to change the groups, if the set of the values that
 * match stays the same:
 *
 * <ul>
 *   <li>Each group is a non-capturing group.
 *   <li>A group or an alternation that always matches exactly one code point is one class. For
 *       example {@code (a|b)} is {@code [ab]}. The engine repeats a class in a loop, but it repeats
 *       an alternation with one level of recursion for each repetition.
 * </ul>
 */
final class RegexTranslator {
  private static final String WORD = "[A-Za-z0-9_]";
  private static final String WORD_BOUNDARY =
      "(?:(?<=" + WORD + ")(?!" + WORD + ")|(?<!" + WORD + ")(?=" + WORD + "))";
  private static final String NOT_WORD_BOUNDARY =
      "(?:(?<=" + WORD + ")(?=" + WORD + ")|(?<!" + WORD + ")(?!" + WORD + "))";

  /** A class that matches no code point. */
  private static final String EMPTY_SET = "[^\\x{0}-\\x{10FFFF}]";

  private RegexTranslator() {}

  /**
   * Translates a tree.
   *
   * @param tree the tree of the pattern
   * @return the pattern for {@link java.util.regex.Pattern#compile(String)}, without flags
   */
  static String translate(RegexNode tree) {
    StringBuilder out = new StringBuilder();
    append(tree, out);
    return out.toString();
  }

  private static void append(RegexNode node, StringBuilder out) {
    List<CodePointRange> set = singleSet(node);
    if (set != null) {
      appendSet(set, out);
      return;
    }
    switch (node) {
      case RegexNode.Empty _ -> {}
      case RegexNode.CharSet charSet -> appendSet(charSet.ranges(), out);
      case RegexNode.Anchor anchor ->
          out.append(
              switch (anchor.kind()) {
                case START -> "\\A";
                case END -> "\\z";
                case WORD_BOUNDARY -> WORD_BOUNDARY;
                case NOT_WORD_BOUNDARY -> NOT_WORD_BOUNDARY;
              });
      case RegexNode.Group group -> appendGroup("(?:", group.child(), out);
      case RegexNode.Lookahead lookahead ->
          appendGroup(lookahead.negated() ? "(?!" : "(?=", lookahead.child(), out);
      case RegexNode.Concat concat -> concat.items().forEach(item -> append(item, out));
      case RegexNode.Alt alternation -> {
        String separator = "";
        for (RegexNode branch : alternation.branches()) {
          out.append(separator);
          append(branch, out);
          separator = "|";
        }
      }
      case RegexNode.Repeat repeat -> {
        RegexNode child = repeat.child();
        if (child instanceof RegexNode.Group || singleSet(child) != null) {
          append(child, out);
        } else {
          appendGroup("(?:", child, out);
        }
        appendQuantifier(repeat, out);
      }
    }
  }

  private static void appendGroup(String open, RegexNode child, StringBuilder out) {
    // A group in a group is one group.
    RegexNode inner = child;
    while (inner instanceof RegexNode.Group group) {
      inner = group.child();
    }
    out.append(open);
    append(inner, out);
    out.append(')');
  }

  /**
   * Gets the set of a node that always matches exactly one code point.
   *
   * @return the normalized set, or null if the node is not a character set, a group that contains
   *     one, or an alternation of them
   */
  private static List<CodePointRange> singleSet(RegexNode node) {
    return switch (node) {
      case RegexNode.CharSet charSet -> charSet.ranges();
      case RegexNode.Group group -> singleSet(group.child());
      case RegexNode.Alt alternation -> {
        List<CodePointRange> union = new ArrayList<>();
        for (RegexNode branch : alternation.branches()) {
          List<CodePointRange> set = singleSet(branch);
          if (set == null) {
            yield null;
          }
          union.addAll(set);
        }
        yield CodePointRange.normalize(union);
      }
      default -> null;
    };
  }

  private static void appendSet(List<CodePointRange> ranges, StringBuilder out) {
    if (ranges.isEmpty()) {
      out.append(EMPTY_SET);
      return;
    }
    out.append('[');
    for (CodePointRange range : ranges) {
      appendCodePoint(range.start(), out);
      if (range.end() != range.start()) {
        out.append('-');
        appendCodePoint(range.end(), out);
      }
    }
    out.append(']');
  }

  private static void appendCodePoint(int codePoint, StringBuilder out) {
    out.append("\\x{").append(Integer.toHexString(codePoint).toUpperCase(Locale.ROOT)).append('}');
  }

  private static void appendQuantifier(RegexNode.Repeat repeat, StringBuilder out) {
    int min = repeat.min();
    int max = repeat.max();
    if (repeat.unbounded()) {
      switch (min) {
        case 0 -> out.append('*');
        case 1 -> out.append('+');
        default -> out.append('{').append(min).append(",}");
      }
    } else if (min == 0 && max == 1) {
      out.append('?');
    } else if (min == max) {
      out.append('{').append(min).append('}');
    } else {
      out.append('{').append(min).append(',').append(max).append('}');
    }
    if (repeat.lazy()) {
      out.append('?');
    }
  }
}
