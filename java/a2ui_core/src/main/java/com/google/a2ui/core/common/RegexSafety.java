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
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The static safety rules of the A2UI regex dialect for the structure of a pattern.
 *
 * <p>A backtracking engine can use exponential time for some patterns. The rules refuse the
 * structures that cause this, before a match starts. The rules examine the tree of the pattern, not
 * its text.
 */
final class RegexSafety {
  private RegexSafety() {}

  /**
   * Examines a pattern with the rules 2, 3 and 4 of the protocol.
   *
   * @param tree the tree of the pattern
   * @return the first rule that the pattern does not obey, or an empty result for a safe pattern
   */
  static Optional<RegexRejection> examine(RegexNode tree) {
    if (any(tree, RegexSafety::nestedQuantifier)) {
      return Optional.of(RegexRejection.NESTED_QUANTIFIER);
    }
    if (any(tree, RegexSafety::overlappingAlternation)) {
      return Optional.of(RegexRejection.OVERLAPPING_ALTERNATION);
    }
    if (any(tree, RegexSafety::adjacentOverlap)) {
      return Optional.of(RegexRejection.ADJACENT_OVERLAP);
    }
    return Optional.empty();
  }

  // The rules. Each rule examines one node.

  /** Rule 2. A maximum of 1 does not repeat, thus {@code (a+)?} is safe. */
  private static boolean nestedQuantifier(RegexNode node) {
    return node instanceof RegexNode.Repeat repeat
        && repeat.max() > 1
        && any(repeat.child(), RegexSafety::unbounded);
  }

  /** Rule 3. The rule compares the first characters of the branches, without their groups. */
  private static boolean overlappingAlternation(RegexNode node) {
    if (!(node instanceof RegexNode.Repeat repeat)
        || !repeat.unbounded()
        || !(unwrap(repeat.child()) instanceof RegexNode.Alt alternation)) {
      return false;
    }
    List<List<CodePointRange>> firsts =
        alternation.branches().stream().map(branch -> first(unwrap(branch))).toList();
    for (int i = 0; i < firsts.size(); i++) {
      for (int j = i + 1; j < firsts.size(); j++) {
        if (CodePointRange.overlap(firsts.get(i), firsts.get(j))) {
          return true;
        }
      }
    }
    return false;
  }

  /** Rule 4. A group that contains only one quantified item is that item. */
  private static boolean adjacentOverlap(RegexNode node) {
    if (!(node instanceof RegexNode.Concat(List<RegexNode> items))) {
      return false;
    }
    for (int i = 0; i < items.size(); i++) {
      if (!(unwrap(items.get(i)) instanceof RegexNode.Repeat left) || !left.unbounded()) {
        continue;
      }
      List<CodePointRange> leftSet = consumed(left.child());
      for (RegexNode follower : items.subList(i + 1, items.size())) {
        if (unwrap(follower) instanceof RegexNode.Repeat right
            && right.unbounded()
            && CodePointRange.overlap(leftSet, consumed(right.child()))) {
          return true;
        }
        if (!nullable(follower)) {
          break;
        }
      }
    }
    return false;
  }

  // The properties of a node.

  private static boolean unbounded(RegexNode node) {
    return node instanceof RegexNode.Repeat repeat && repeat.unbounded();
  }

  /** Removes the groups around a node. */
  private static RegexNode unwrap(RegexNode node) {
    RegexNode current = node;
    while (current instanceof RegexNode.Group group) {
      current = group.child();
    }
    return current;
  }

  private static List<RegexNode> children(RegexNode node) {
    return switch (node) {
      case RegexNode.Group group -> List.of(group.child());
      case RegexNode.Lookahead lookahead -> List.of(lookahead.child());
      case RegexNode.Repeat repeat -> List.of(repeat.child());
      case RegexNode.Concat concat -> concat.items();
      case RegexNode.Alt alternation -> alternation.branches();
      case RegexNode.Empty _, RegexNode.CharSet _, RegexNode.Anchor _ -> List.of();
    };
  }

  /** Tells if the test is true for a node or for a node below it. */
  private static boolean any(RegexNode node, Predicate<RegexNode> test) {
    return test.test(node) || children(node).stream().anyMatch(child -> any(child, test));
  }

  /** Tells if a node can match the empty string. */
  private static boolean nullable(RegexNode node) {
    return switch (node) {
      case RegexNode.Empty _, RegexNode.Anchor _, RegexNode.Lookahead _ -> true;
      case RegexNode.CharSet _ -> false;
      case RegexNode.Group group -> nullable(group.child());
      case RegexNode.Repeat repeat -> repeat.min() == 0 || nullable(repeat.child());
      case RegexNode.Alt alternation ->
          alternation.branches().stream().anyMatch(RegexSafety::nullable);
      case RegexNode.Concat concat -> concat.items().stream().allMatch(RegexSafety::nullable);
    };
  }

  /** Gets the code points that can start a match of a node, if the match is not empty. */
  private static List<CodePointRange> first(RegexNode node) {
    return switch (node) {
      case RegexNode.CharSet set -> set.ranges();
      case RegexNode.Group group -> first(group.child());
      case RegexNode.Repeat repeat -> repeat.max() == 0 ? List.of() : first(repeat.child());
      case RegexNode.Alt alternation -> {
        List<CodePointRange> sets = new ArrayList<>();
        for (RegexNode branch : alternation.branches()) {
          sets.addAll(first(branch));
        }
        yield CodePointRange.normalize(sets);
      }
      case RegexNode.Concat concat -> {
        // An item can start the match if each item before it can match the empty string.
        List<CodePointRange> sets = new ArrayList<>();
        for (RegexNode item : concat.items()) {
          sets.addAll(first(item));
          if (!nullable(item)) {
            break;
          }
        }
        yield CodePointRange.normalize(sets);
      }
      case RegexNode.Empty _, RegexNode.Anchor _, RegexNode.Lookahead _ -> List.of();
    };
  }

  /** Gets each code point that a match of a node can contain. A lookahead contains none. */
  private static List<CodePointRange> consumed(RegexNode node) {
    return switch (node) {
      case RegexNode.CharSet set -> set.ranges();
      case RegexNode.Repeat repeat when repeat.max() == 0 -> List.of();
      case RegexNode.Group _, RegexNode.Repeat _, RegexNode.Alt _, RegexNode.Concat _ -> {
        List<CodePointRange> sets = new ArrayList<>();
        for (RegexNode child : children(node)) {
          sets.addAll(consumed(child));
        }
        yield CodePointRange.normalize(sets);
      }
      case RegexNode.Empty _, RegexNode.Anchor _, RegexNode.Lookahead _ -> List.of();
    };
  }
}
