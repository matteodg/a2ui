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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class CoercionTest {
  @Test
  void aBooleanIsItself() {
    assertTrue(Coercion.toBoolean(true));
    assertFalse(Coercion.toBoolean(false));
  }

  @Test
  void aStringIsTrueOnlyIfItIsTheWordTrue() {
    assertTrue(Coercion.toBoolean("true"));
    assertTrue(Coercion.toBoolean("TRUE"));
    assertFalse(Coercion.toBoolean("false"));
    assertFalse(Coercion.toBoolean("yes"));
    assertFalse(Coercion.toBoolean(""));
    assertFalse(Coercion.toBoolean(" true"));
  }

  @Test
  void aNumberIsTrueIfItIsNotZero() {
    assertTrue(Coercion.toBoolean(-1));
    assertTrue(Coercion.toBoolean(0.5));
    assertFalse(Coercion.toBoolean(0));
    assertFalse(Coercion.toBoolean(0.0));
    assertFalse(Coercion.toBoolean(Double.NaN));
  }

  @Test
  void aValidationResultIsItsValidity() {
    assertTrue(Coercion.toBoolean(Map.of("valid", true)));
    assertFalse(Coercion.toBoolean(Map.of("valid", false, "message", "Required")));
  }

  @Test
  void aNullValueAndEachOtherObjectOrListAreFalse() {
    assertFalse(Coercion.toBoolean(null));
    assertFalse(Coercion.toBoolean(Map.of()));
    assertFalse(Coercion.toBoolean(Map.of("valid", "true")));
    assertFalse(Coercion.toBoolean(List.of(1)));
  }

  @Test
  void parseNumberReadsNumbersBooleansAndDecimalStrings() {
    assertEquals(OptionalDouble.of(7), Coercion.parseNumber(7L));
    assertEquals(OptionalDouble.of(2.5), Coercion.parseNumber(2.5));
    assertEquals(OptionalDouble.of(1), Coercion.parseNumber(true));
    assertEquals(OptionalDouble.of(0), Coercion.parseNumber(false));
    assertEquals(OptionalDouble.of(4.5), Coercion.parseNumber(" 4.5 "));
    assertEquals(OptionalDouble.of(-1200), Coercion.parseNumber("-1.2e3"));
    assertEquals(OptionalDouble.of(0.5), Coercion.parseNumber(".5"));
    assertEquals(OptionalDouble.of(0), Coercion.parseNumber("  "));
  }

  @Test
  void parseNumberIsEmptyForAValueThatIsNotANumber() {
    assertEquals(OptionalDouble.empty(), Coercion.parseNumber(null));
    assertEquals(OptionalDouble.empty(), Coercion.parseNumber("x"));
    assertEquals(OptionalDouble.empty(), Coercion.parseNumber("1,5"));
    assertEquals(OptionalDouble.empty(), Coercion.parseNumber("0x10"));
    assertEquals(OptionalDouble.empty(), Coercion.parseNumber("NaN"));
    assertEquals(OptionalDouble.empty(), Coercion.parseNumber("1e999"));
    assertEquals(OptionalDouble.empty(), Coercion.parseNumber(Double.POSITIVE_INFINITY));
    assertEquals(OptionalDouble.empty(), Coercion.parseNumber(List.of(1)));
    assertEquals(OptionalDouble.empty(), Coercion.parseNumber(Map.of()));
  }

  @Test
  void toNumberIsZeroForAValueThatIsNotANumber() {
    assertEquals(0.0, Coercion.toNumber(null));
    assertEquals(0.0, Coercion.toNumber("x"));
    assertEquals(4.5, Coercion.toNumber("4.5"));
    assertEquals(3.0, Coercion.toNumber(3));
  }

  @Test
  void toTextWritesScalarsInTheSameWayInEachLocale() {
    assertEquals("", Coercion.toText(null));
    assertEquals("text", Coercion.toText("text"));
    assertEquals("true", Coercion.toText(true));
    assertEquals("7", Coercion.toText(7L));
    assertEquals("3", Coercion.toText(3.0));
    assertEquals("3.5", Coercion.toText(3.5));
    assertEquals("0", Coercion.toText(-0.0));
    assertEquals("0.1", Coercion.toText(0.1));
    assertEquals("1234567.25", Coercion.toText(1234567.25));
    assertEquals("1000000000000000", Coercion.toText(1.0E15));
    assertEquals("2.5", Coercion.toText(new BigDecimal("2.50")));
  }

  @Test
  void toTextWritesAnObjectOrAListAsJsonWithoutSpaces() {
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("a", Arrays.asList(1.0, 2.5, 3, null));
    object.put("b", Map.of("c", "d"));

    assertEquals("{\"a\":[1,2.5,3,null],\"b\":{\"c\":\"d\"}}", Coercion.toText(object));
    assertEquals("[true,\"x\"]", Coercion.toText(List.of(true, "x")));
  }
}
