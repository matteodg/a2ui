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

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;

/**
 * The identifier syntax of Unicode Standard Annex #31.
 *
 * <p>Protocol v1.0 uses this syntax for component IDs, property keys and function names.
 *
 * <p>The methods use code points, not {@code char} values. Thus a character that is not in the
 * Basic Multilingual Plane is one unit, not two surrogates.
 */
public final class Uax31 {
  private Uax31() {}

  /**
   * Tells if a code point can start an identifier.
   *
   * @param codePoint the code point to examine
   * @return true if the code point has the property {@code XID_Start} or is an underscore
   */
  public static boolean isIdentifierStart(int codePoint) {
    return codePoint == '_' || UCharacter.hasBinaryProperty(codePoint, UProperty.XID_START);
  }

  /**
   * Tells if a code point can continue an identifier.
   *
   * @param codePoint the code point to examine
   * @return true if the code point has the property {@code XID_Continue}
   */
  public static boolean isIdentifierContinue(int codePoint) {
    return UCharacter.hasBinaryProperty(codePoint, UProperty.XID_CONTINUE);
  }

  /**
   * Tells if a text is an identifier.
   *
   * @param text the text to examine, or null
   * @return true if the text is not empty and each code point is permitted at its position
   */
  public static boolean isIdentifier(String text) {
    if (text == null || text.isEmpty()) {
      return false;
    }
    int index = 0;
    while (index < text.length()) {
      int codePoint = text.codePointAt(index);
      boolean valid = index == 0 ? isIdentifierStart(codePoint) : isIdentifierContinue(codePoint);
      if (!valid) {
        return false;
      }
      index += Character.charCount(codePoint);
    }
    return true;
  }
}
