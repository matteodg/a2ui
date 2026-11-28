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

package com.google.a2ui.core.internal;

import com.ibm.icu.text.UnicodeSet;
import com.networknt.schema.regex.RegularExpression;
import com.networknt.schema.regex.RegularExpressionFactory;
import java.util.regex.Pattern;

/**
 * Compiles the patterns of a schema with {@code java.util.regex}.
 *
 * <p>The A2UI schemas use the Unicode properties {@code \p{XID_Start}} and {@code
 * \p{XID_Continue}}. {@code java.util.regex} does not know these properties. Thus this class
 * replaces each of them with a character class that has the same code points. The code points come
 * from the Unicode data of ICU.
 */
final class UnicodeRegularExpressions implements RegularExpressionFactory {
  private static final String XID_START = "\\p{XID_Start}";
  private static final String XID_CONTINUE = "\\p{XID_Continue}";

  @Override
  public RegularExpression getRegularExpression(String regex) {
    Pattern pattern = Pattern.compile(expand(regex));
    // A schema pattern matches if a part of the value matches it. The pattern itself has the
    // anchors that it needs.
    return value -> pattern.matcher(value).find();
  }

  /**
   * Replaces the two identifier properties in a pattern.
   *
   * @param regex the pattern as the schema writes it
   * @return the pattern for {@code java.util.regex}
   */
  static String expand(String regex) {
    if (!regex.contains("\\p{XID_")) {
      return regex;
    }
    return regex
        .replace(XID_START, Expansions.XID_START)
        .replace(XID_CONTINUE, Expansions.XID_CONTINUE);
  }

  /**
   * Makes a character class for {@code java.util.regex} that has the code points of an ICU set.
   *
   * @param icuSet the set in the ICU notation, for example {@code [:XID_Start:]}
   */
  private static String characterClass(String icuSet) {
    UnicodeSet set = new UnicodeSet(icuSet);
    StringBuilder result = new StringBuilder("[");
    for (int range = 0; range < set.getRangeCount(); range++) {
      result.append("\\x{").append(Integer.toHexString(set.getRangeStart(range))).append('}');
      result.append("-\\x{").append(Integer.toHexString(set.getRangeEnd(range))).append('}');
    }
    return result.append(']').toString();
  }

  /** Makes the two character classes when the JVM initializes the class, at the first use. */
  private static final class Expansions {
    static final String XID_START = characterClass("[:XID_Start:]");
    static final String XID_CONTINUE = characterClass("[:XID_Continue:]");
  }
}
