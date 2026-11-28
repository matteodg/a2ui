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

import com.google.a2ui.core.internal.Json;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * The rules of the protocol that read a resolved value as a boolean, as a number or as text.
 *
 * <p>These rules apply to a component property and to a function argument whose type is {@code
 * DynamicBoolean}, {@code DynamicNumber} or {@code DynamicString}.
 */
public final class Coercion {
  /** A decimal number as JSON writes it. A sign and an absent integer part are also correct. */
  private static final Pattern DECIMAL =
      Pattern.compile("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?");

  /** A {@code double} holds each integer below this value exactly. */
  private static final double EXACT_INTEGER_LIMIT = 9.007199254740992E15;

  /** The values below this limit and not below {@link #SMALL_LIMIT} have no exponent as text. */
  private static final double LARGE_LIMIT = 1.0E21;

  private static final double SMALL_LIMIT = 1.0E-6;

  private Coercion() {}

  /**
   * Reads a value as a boolean.
   *
   * <ul>
   *   <li>A boolean is itself.
   *   <li>A validation result is its {@code valid}. A validation result is an object that has a
   *       boolean {@code valid}.
   *   <li>A string is true only if it is {@code true} in upper case or lower case letters.
   *   <li>A number is true if it is not zero.
   *   <li>A null value, each other object and each list are false.
   * </ul>
   *
   * @param value the value to read, or null
   * @return the boolean
   */
  public static boolean toBoolean(Object value) {
    return switch (value) {
      case Boolean flag -> flag;
      case Number number -> {
        double amount = number.doubleValue();
        yield amount != 0 && !Double.isNaN(amount);
      }
      case String text -> text.equalsIgnoreCase("true");
      case Map<?, ?> object -> object.get("valid") instanceof Boolean valid && valid;
      case null, default -> false;
    };
  }

  /**
   * Reads a value as a number. A value that is not a number is zero.
   *
   * <p>Use {@link #parseNumber(Object)} if the caller must know that the value is not a number.
   *
   * @param value the value to read, or null
   * @return the number, or zero if {@link #parseNumber(Object)} gives an empty result
   */
  public static double toNumber(Object value) {
    return parseNumber(value).orElse(0);
  }

  /**
   * Reads a value as a number, if it is one.
   *
   * <ul>
   *   <li>A number is itself.
   *   <li>A boolean is 1 or 0.
   *   <li>A string that contains only spaces is 0.
   *   <li>Each other string is the decimal number that it writes, without the spaces around it.
   * </ul>
   *
   * @param value the value to read, or null
   * @return the number. The result is empty for a null value, for a string that is not a decimal
   *     number, for a number that is not finite, for an object and for a list.
   */
  public static OptionalDouble parseNumber(Object value) {
    return switch (value) {
      case Number number -> finite(number.doubleValue());
      case Boolean flag -> OptionalDouble.of(flag ? 1 : 0);
      case String text -> {
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
          yield OptionalDouble.of(0);
        }
        yield DECIMAL.matcher(trimmed).matches()
            ? finite(Double.parseDouble(trimmed))
            : OptionalDouble.empty();
      }
      case null, default -> OptionalDouble.empty();
    };
  }

  /**
   * Writes a value as text. The text is the same in each locale.
   *
   * <ul>
   *   <li>A null value is the empty string.
   *   <li>A string is itself.
   *   <li>A number that has no fraction has no decimal point: 3.0 is {@code 3}.
   *   <li>A boolean is {@code true} or {@code false}.
   *   <li>An object or a list is JSON text without spaces.
   * </ul>
   *
   * @param value the value to write, or null
   * @return the text
   */
  public static String toText(Object value) {
    return switch (value) {
      case null -> "";
      case String text -> text;
      case Double number -> numberText(number);
      case Float number -> numberText(number.doubleValue());
      case BigDecimal number -> numberText(number.doubleValue());
      case Map<?, ?> object -> Json.write(jsonValue(object));
      case Collection<?> list -> Json.write(jsonValue(list));
      default -> value.toString();
    };
  }

  private static OptionalDouble finite(double value) {
    return Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
  }

  private static String numberText(double value) {
    double size = Math.abs(value);
    if (!Double.isFinite(value) || size >= LARGE_LIMIT || (size != 0 && size < SMALL_LIMIT)) {
      return Double.toString(value);
    }
    // `valueOf` uses the shortest digits that identify the value. Thus 0.1 is "0.1".
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
  }

  /** Converts a value to the types that {@link Json#write(Object)} accepts. */
  private static Object jsonValue(Object value) {
    return switch (value) {
      case null -> null;
      case String text -> text;
      case Boolean flag -> flag;
      case Long number -> number;
      case Integer number -> number.longValue();
      case Short number -> number.longValue();
      case Byte number -> number.longValue();
      case BigInteger number when number.bitLength() <= 63 -> number.longValue();
      case Number number -> jsonNumber(number.doubleValue());
      case Map<?, ?> object -> {
        Map<String, Object> copy = new LinkedHashMap<>();
        object.forEach((key, item) -> copy.put(String.valueOf(key), jsonValue(item)));
        yield copy;
      }
      case Collection<?> list -> {
        List<Object> copy = new ArrayList<>(list.size());
        list.forEach(item -> copy.add(jsonValue(item)));
        yield copy;
      }
      default -> value.toString();
    };
  }

  /** JSON has no value for a number that is not finite. Thus such a number becomes null. */
  private static Object jsonNumber(double value) {
    if (!Double.isFinite(value)) {
      return null;
    }
    boolean isInteger = value == Math.rint(value) && Math.abs(value) < EXACT_INTEGER_LIMIT;
    return isInteger ? (Object) (long) value : (Object) value;
  }
}
