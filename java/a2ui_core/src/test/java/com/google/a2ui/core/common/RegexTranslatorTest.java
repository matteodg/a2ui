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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RegexTranslatorTest {
  private static final String WORD = "[A-Za-z0-9_]";

  private static String translate(String pattern) {
    try {
      return RegexTranslator.translate(RegexParser.parse(pattern));
    } catch (RegexSyntaxException e) {
      throw new AssertionError(pattern + ": " + e.getMessage(), e);
    }
  }

  @Test
  void aCharacterSetIsAClassOfExplicitCodePointRanges() {
    assertEquals("[\\x{61}]", translate("a"));
    assertEquals("[\\x{30}-\\x{39}]", translate("\\d"));
    assertEquals("[\\x{30}-\\x{39}\\x{41}-\\x{5A}\\x{5F}\\x{61}-\\x{7A}]", translate("\\w"));
    assertEquals(
        "[\\x{0}-\\x{9}\\x{B}-\\x{C}\\x{E}-\\x{2027}\\x{202A}-\\x{D7FF}\\x{E000}-\\x{10FFFF}]",
        translate("."));
    assertEquals("[\\x{2D}\\x{5D}\\x{61}-\\x{63}]", translate("[a-c\\]-]"));
    assertEquals("[\\x{1F600}]", translate("😀"));
    assertEquals("[^\\x{0}-\\x{10FFFF}]", translate("[^\\d\\D]"));
  }

  @Test
  void theAnchorsMatchOnlyAtTheEndsOfTheValue() {
    assertEquals("\\A[\\x{61}]\\z", translate("^a$"));
  }

  @Test
  void aWordBoundaryIsAnAssertionAboutTheAsciiWordCharacters() {
    assertEquals(
        "(?:(?<=" + WORD + ")(?!" + WORD + ")|(?<!" + WORD + ")(?=" + WORD + "))",
        translate("\\b"));
    assertEquals(
        "(?:(?<=" + WORD + ")(?=" + WORD + ")|(?<!" + WORD + ")(?!" + WORD + "))",
        translate("\\B"));
  }

  @Test
  void aQuantifierKeepsItsLimits() {
    assertEquals("[\\x{61}]*", translate("a*"));
    assertEquals("[\\x{61}]+", translate("a+"));
    assertEquals("[\\x{61}]?", translate("a?"));
    assertEquals("[\\x{61}]{3}", translate("a{3}"));
    assertEquals("[\\x{61}]{3,}", translate("a{3,}"));
    assertEquals("[\\x{61}]{3,5}", translate("a{3,5}"));
    assertEquals("[\\x{61}]{0,5}", translate("a{0,5}"));
    assertEquals("[\\x{61}]{0}", translate("a{0}"));
    assertEquals("[\\x{61}]{1}", translate("a{1}"));
    assertEquals("[\\x{61}]+", translate("a{1,}"));
    assertEquals("[\\x{61}]*?[\\x{62}]+?[\\x{63}]??[\\x{64}]{2,3}?", translate("a*?b+?c??d{2,3}?"));
  }

  @Test
  void eachGroupIsNonCapturingAndALookaheadStaysALookahead() {
    assertEquals("(?:[\\x{61}][\\x{62}])", translate("(ab)"));
    assertEquals("(?:[\\x{61}][\\x{62}])", translate("(?:ab)"));
    assertEquals("(?:[\\x{61}][\\x{62}])", translate("(?:((ab)))"));
    assertEquals("(?=[\\x{61}][\\x{62}])", translate("(?=ab)"));
    assertEquals("(?![\\x{61}])", translate("(?!a)"));
    assertEquals("(?:)", translate("()"));
    assertEquals("(?:[\\x{61}][\\x{62}]){2}", translate("(ab){2}"));
    assertEquals("(?:[\\x{61}]{2})*", translate("(a{2})*"));
  }

  @Test
  void anAlternationKeepsItsBranches() {
    assertEquals("[\\x{61}][\\x{62}]|[\\x{63}]", translate("ab|c"));
    assertEquals("(?:|[\\x{61}][\\x{62}])[\\x{63}]", translate("(|ab)c"));
    assertEquals("(?:[\\x{61}][\\x{62}]|[\\x{63}][\\x{64}])*", translate("(ab|cd)*"));
  }

  @Test
  void aGroupThatMatchesExactlyOneCodePointIsOneClass() {
    assertEquals("[\\x{61}-\\x{62}]*", translate("(a|b)*"));
    assertEquals("[\\x{61}]+", translate("(?:(a))+"));
    assertEquals("[\\x{30}-\\x{39}\\x{61}\\x{78}-\\x{7A}]", translate("a|\\d|(?:[x-z])"));
    assertEquals("(?=[\\x{61}-\\x{62}])", translate("(?=a|b)"));
    // A branch that can match the empty string, or two code points, is not a class.
    assertEquals("(?:[\\x{61}]|)*", translate("(a|)*"));
    assertEquals("(?:[\\x{61}]|[\\x{62}][\\x{63}])*", translate("(a|bc)*"));
    assertEquals("(?:[\\x{61}]|[\\x{62}]?)", translate("(a|b?)"));
  }

  @Test
  void theEngineCompilesTheTranslationWithoutFlags() {
    Pattern compiled = Pattern.compile(translate("^(?:()|(?:)*x|[^\\d\\D]|\\b\\B)(a|b){2,}?$"));

    assertTrue(compiled.matcher("ab").find());
    assertTrue(compiled.matcher("xba").find());
    assertFalse(compiled.matcher("xb").find());
    assertFalse(compiled.matcher("ab\n").find());
  }
}
