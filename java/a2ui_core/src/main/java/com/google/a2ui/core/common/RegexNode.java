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

import java.util.List;

/**
 * A node of a parsed pattern of the A2UI regex dialect.
 *
 * <p>The safety rules and the translation to {@code java.util.regex} read the same tree. Thus the
 * pattern that runs is the pattern that the rules examined.
 */
sealed interface RegexNode {
  /** The position that an assertion examines. */
  enum AnchorKind {
    /** The start of the value: {@code ^}. */
    START,
    /** The end of the value: {@code $}. */
    END,
    /** A position between a word character and a character that is not one: {@code \b}. */
    WORD_BOUNDARY,
    /** A position that is not a word boundary: {@code \B}. */
    NOT_WORD_BOUNDARY
  }

  /** The pattern that matches the empty string, for example an empty branch. */
  record Empty() implements RegexNode {}

  /**
   * One code point from a set. A literal, {@code .}, a class escape and a character class are
   * character sets.
   *
   * @param ranges the normalized set
   */
  record CharSet(List<CodePointRange> ranges) implements RegexNode {}

  /**
   * A group.
   *
   * @param capturing true for {@code ( )}, false for {@code (?: )}
   * @param child the contents of the group
   */
  record Group(boolean capturing, RegexNode child) implements RegexNode {}

  /**
   * A lookahead. It matches no characters.
   *
   * @param negated true for {@code (?! )}, false for {@code (?= )}
   * @param child the pattern that the lookahead examines
   */
  record Lookahead(boolean negated, RegexNode child) implements RegexNode {}

  /**
   * An assertion about a position. It matches no characters.
   *
   * @param kind the position
   */
  record Anchor(AnchorKind kind) implements RegexNode {}

  /**
   * A sequence.
   *
   * @param items the items, in sequence. There are two or more.
   */
  record Concat(List<RegexNode> items) implements RegexNode {}

  /**
   * An alternation.
   *
   * @param branches the branches, in sequence. There are two or more.
   */
  record Alt(List<RegexNode> branches) implements RegexNode {}

  /**
   * A quantifier and the item that it repeats.
   *
   * @param child the item
   * @param min the minimum number of repetitions
   * @param max the maximum number of repetitions, or {@link #UNBOUNDED}
   * @param lazy true if a {@code ?} follows the quantifier
   */
  record Repeat(RegexNode child, int min, int max, boolean lazy) implements RegexNode {
    /** The maximum of a quantifier that has no upper limit. */
    static final int UNBOUNDED = Integer.MAX_VALUE;

    /**
     * Tells if the quantifier has no upper limit.
     *
     * @return true for {@code *}, {@code +} and <code>{n,}</code>
     */
    boolean unbounded() {
      return max == UNBOUNDED;
    }
  }
}
