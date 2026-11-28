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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.exceptions.A2uiParseError;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonTest {
  @Test
  void parseReadsEachJsonTypeIntoAPlainValue() {
    Object parsed =
        Json.parse(
            "{\"s\": \"a\", \"i\": 7, \"d\": 1.5, \"t\": true, \"n\": null, \"l\": [1, {}]}");

    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("s", "a");
    expected.put("i", 7L);
    expected.put("d", 1.5);
    expected.put("t", true);
    expected.put("n", null);
    expected.put("l", List.of(1L, Map.of()));
    assertEquals(expected, parsed);
  }

  @Test
  void parseKeepsTheSequenceOfTheKeys() {
    Map<String, Object> parsed = Json.asObject(Json.parse("{\"z\": 1, \"a\": 2, \"m\": 3}"));

    assertEquals(List.of("z", "a", "m"), List.copyOf(parsed.keySet()));
  }

  @Test
  void parseReadsAnIntegerAsALongAndOtherNumbersAsADouble() {
    assertEquals(9_007_199_254_740_993L, Json.parse("9007199254740993"));
    assertEquals(1.0, Json.parse("1.0"));
    assertEquals(1000.0, Json.parse("1e3"));
    assertEquals(1.0e20, Json.parse("100000000000000000000"));
  }

  @Test
  void parseReturnsReadOnlyContainers() {
    Map<String, Object> parsed = Json.asObject(Json.parse("{\"l\": []}"));

    assertThrows(UnsupportedOperationException.class, () -> parsed.put("x", 1L));
    @SuppressWarnings("unchecked")
    List<Object> list = (List<Object>) parsed.get("l");
    assertThrows(UnsupportedOperationException.class, () -> list.add(1L));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "{", "[1,]", "{\"a\": 1} 2", "{'a': 1}", "NaN", "1e999"})
  void parseThrowsAParseErrorForTextThatIsNotOneJsonValue(String text) {
    A2uiParseError error = assertThrows(A2uiParseError.class, () -> Json.parse(text));

    assertTrue(error.getMessage().startsWith("Invalid JSON: "), error.getMessage());
  }

  @Test
  void freezeConvertsEachNumberToALongOrADouble() {
    List<Object> numbers = new ArrayList<>();
    numbers.add(1);
    numbers.add((short) 2);
    numbers.add((byte) 3);
    numbers.add(4.5f);
    numbers.add(BigInteger.TEN);
    numbers.add(BigInteger.TEN.pow(20));
    numbers.add(new BigDecimal("0.25"));

    assertEquals(List.of(1L, 2L, 3L, 4.5, 10L, 1.0e20, 0.25), Json.freeze(numbers));
  }

  @Test
  void freezeMakesACopyThatDoesNotChangeWithTheSource() {
    Map<String, Object> source = new HashMap<>();
    source.put("list", new ArrayList<>(List.of("a")));

    Map<String, Object> frozen = Json.freezeObject(source);
    source.put("extra", true);

    assertEquals(Map.of("list", List.of("a")), frozen);
    assertThrows(UnsupportedOperationException.class, () -> frozen.remove("list"));
  }

  @Test
  void freezeRejectsAValueThatIsNotJson() {
    assertThrows(IllegalArgumentException.class, () -> Json.freeze(new Object()));
    assertThrows(IllegalArgumentException.class, () -> Json.freeze(Map.of(1, "a")));
    assertThrows(IllegalArgumentException.class, () -> Json.freeze(Double.NaN));
    assertThrows(
        IllegalArgumentException.class, () -> Json.freeze(List.of(Float.NEGATIVE_INFINITY)));
  }

  @Test
  void asObjectReturnsNullForAValueThatIsNotAMap() {
    assertNull(Json.asObject("text"));
    assertNull(Json.asObject(null));
  }

  @Test
  void pointerSegmentsEncodeTheTildeAndTheSlash() {
    assertEquals("a~1b~0c", Json.escapePointer("a/b~c"));
    assertEquals("a/b~c", Json.unescapePointer("a~1b~0c"));
    assertEquals("~1", Json.unescapePointer(Json.escapePointer("~1")));
  }

  @Test
  void writeMakesTextThatParseReadsBackToTheSameValue() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("s", "a \"b\" \n \\ ü");
    value.put("i", 7L);
    value.put("d", 1.5);
    value.put("t", true);
    value.put("n", null);
    value.put("l", List.of(1L, Map.of()));

    String text = Json.write(value);

    assertEquals(value, Json.parse(text));
    assertEquals(
        "{\"z\":1,\"a\":[true,null,\"x\"]}",
        Json.write(Json.parse("{\"z\": 1, \"a\": [true, null, \"x\"]}")));
  }

  @Test
  void writeFailsForAValueThatIsNotPlainJson() {
    assertThrows(IllegalArgumentException.class, () -> Json.write(Map.of("a", new Object())));
    assertThrows(IllegalArgumentException.class, () -> Json.write(List.of(1)));
    assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> Json.write(Map.of(1, "a")));
  }
}
