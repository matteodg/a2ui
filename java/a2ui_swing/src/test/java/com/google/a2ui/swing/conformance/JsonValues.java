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

package com.google.a2ui.swing.conformance;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Comparisons and conversions of JSON values for the conformance handlers. */
final class JsonValues {
  private JsonValues() {}

  /**
   * Tells if two values are the same JSON value.
   *
   * <p>The sequence of the keys of an object is not important. The comparison of numbers uses their
   * value. Thus the {@code Integer} 5 of the YAML loader is equal to the {@code Long} 5 and to the
   * {@code Double} 5.0 of the SDK.
   */
  static boolean jsonEquals(Object a, Object b) {
    if (a instanceof Number x && b instanceof Number y) {
      return decimal(x).compareTo(decimal(y)) == 0;
    }
    if (a instanceof Map<?, ?> x && b instanceof Map<?, ?> y) {
      if (x.size() != y.size()) {
        return false;
      }
      for (Map.Entry<?, ?> entry : x.entrySet()) {
        if (!y.containsKey(entry.getKey())
            || !jsonEquals(entry.getValue(), y.get(entry.getKey()))) {
          return false;
        }
      }
      return true;
    }
    if (a instanceof List<?> x && b instanceof List<?> y) {
      if (x.size() != y.size()) {
        return false;
      }
      for (int i = 0; i < x.size(); i++) {
        if (!jsonEquals(x.get(i), y.get(i))) {
          return false;
        }
      }
      return true;
    }
    return Objects.equals(a, b);
  }

  /** Fails if the actual value is not the expected JSON value. */
  static void assertJsonEquals(Object expected, Object actual, String where) {
    if (!jsonEquals(expected, actual)) {
      throw new AssertionError(where + ": expected <" + expected + "> but was <" + actual + ">");
    }
  }

  /** Makes a deep copy of a JSON value. The maps and the lists of the copy can change. */
  static Object mutableCopy(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<Object, Object> copy = new LinkedHashMap<>();
      map.forEach((key, item) -> copy.put(key, mutableCopy(item)));
      return copy;
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>();
      list.forEach(item -> copy.add(mutableCopy(item)));
      return copy;
    }
    return value;
  }

  /** Reads a value as a map with string keys. Fails with the location if the value is not one. */
  @SuppressWarnings("unchecked")
  static Map<String, Object> asObject(Object value, String where) {
    if (!(value instanceof Map<?, ?> map)
        || !map.keySet().stream().allMatch(String.class::isInstance)) {
      throw new AssertionError(where + " must be an object, but was <" + value + ">");
    }
    return (Map<String, Object>) map;
  }

  /** Reads a value as a list. Fails with the location if the value is not one. */
  @SuppressWarnings("unchecked")
  static List<Object> asList(Object value, String where) {
    if (!(value instanceof List<?> list)) {
      throw new AssertionError(where + " must be a list, but was <" + value + ">");
    }
    return (List<Object>) list;
  }

  /** Reads a value as a string. Fails with the location if the value is not one. */
  static String asString(Object value, String where) {
    if (!(value instanceof String text)) {
      throw new AssertionError(where + " must be a string, but was <" + value + ">");
    }
    return text;
  }

  private static BigDecimal decimal(Number number) {
    return switch (number) {
      case BigDecimal big -> big;
      case BigInteger big -> new BigDecimal(big);
      case Double d -> BigDecimal.valueOf(d);
      case Float f -> BigDecimal.valueOf(f.doubleValue());
      default -> BigDecimal.valueOf(number.longValue());
    };
  }
}
