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
import java.util.Comparator;
import java.util.List;

/**
 * A range of code points in a pattern of the A2UI regex dialect.
 *
 * <p>A character set is a list of ranges. A normalized set is sorted, its ranges do not touch each
 * other, and it contains no surrogate. The static methods of this record calculate with sets.
 *
 * @param start the first code point of the range
 * @param end the last code point of the range. The range includes it.
 */
record CodePointRange(int start, int end) {
  /** The largest code point of Unicode. */
  static final int MAX_CODE_POINT = Character.MAX_CODE_POINT;

  private static final Comparator<CodePointRange> ORDER =
      Comparator.comparingInt(CodePointRange::start).thenComparingInt(CodePointRange::end);

  /**
   * Makes the range that contains one code point.
   *
   * @param codePoint the code point
   * @return the range
   */
  static CodePointRange of(int codePoint) {
    return new CodePointRange(codePoint, codePoint);
  }

  /**
   * Makes a set from pairs of limits.
   *
   * @param limits the first and the last code point of each range, in sequence
   * @return the normalized set
   */
  static List<CodePointRange> setOf(int... limits) {
    List<CodePointRange> ranges = new ArrayList<>();
    for (int i = 0; i < limits.length; i += 2) {
      ranges.add(new CodePointRange(limits[i], limits[i + 1]));
    }
    return normalize(ranges);
  }

  /**
   * Tells if a code point is a surrogate.
   *
   * @param codePoint the code point to examine
   * @return true if the code point is between U+D800 and U+DFFF
   */
  static boolean isSurrogate(int codePoint) {
    return codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE;
  }

  /**
   * Normalizes a set.
   *
   * @param ranges the ranges, in any sequence. They can touch or overlap each other.
   * @return the same code points without the surrogates, as a sorted list of ranges that do not
   *     touch each other. The list is read-only.
   */
  static List<CodePointRange> normalize(List<CodePointRange> ranges) {
    List<CodePointRange> sorted = new ArrayList<>();
    for (CodePointRange range : ranges) {
      // The part below the surrogates, then the part above them.
      int below = Math.min(range.end, Character.MIN_SURROGATE - 1);
      int above = Math.max(range.start, Character.MAX_SURROGATE + 1);
      if (range.start <= below) {
        sorted.add(new CodePointRange(range.start, below));
      }
      if (above <= range.end) {
        sorted.add(new CodePointRange(above, range.end));
      }
    }
    sorted.sort(ORDER);
    List<CodePointRange> merged = new ArrayList<>();
    for (CodePointRange range : sorted) {
      CodePointRange last = merged.isEmpty() ? null : merged.getLast();
      if (last != null && range.start <= last.end + 1) {
        merged.set(
            merged.size() - 1, new CodePointRange(last.start, Math.max(last.end, range.end)));
      } else {
        merged.add(range);
      }
    }
    return List.copyOf(merged);
  }

  /**
   * Calculates the complement of a set.
   *
   * @param ranges the set. It is not necessary to normalize it.
   * @return the normalized set of each code point that is not in the set and is not a surrogate
   */
  static List<CodePointRange> complement(List<CodePointRange> ranges) {
    List<CodePointRange> result = new ArrayList<>();
    int cursor = 0;
    for (CodePointRange range : normalize(ranges)) {
      if (range.start > cursor) {
        result.add(new CodePointRange(cursor, range.start - 1));
      }
      cursor = range.end + 1;
    }
    if (cursor <= MAX_CODE_POINT) {
      result.add(new CodePointRange(cursor, MAX_CODE_POINT));
    }
    return normalize(result);
  }

  /**
   * Tells if two sets have a code point in common.
   *
   * @param first a normalized set
   * @param second a normalized set
   * @return true if a code point is in the two sets
   */
  static boolean overlap(List<CodePointRange> first, List<CodePointRange> second) {
    int i = 0;
    int j = 0;
    while (i < first.size() && j < second.size()) {
      if (first.get(i).end < second.get(j).start) {
        i++;
      } else if (second.get(j).end < first.get(i).start) {
        j++;
      } else {
        return true;
      }
    }
    return false;
  }
}
