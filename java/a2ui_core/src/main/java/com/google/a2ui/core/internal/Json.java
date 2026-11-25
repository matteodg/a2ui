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

import com.google.a2ui.core.exceptions.A2uiParseError;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;

/**
 * JSON as plain JVM values: {@code Map<String, Object>}, {@code List<Object>}, {@code String},
 * {@code Long}, {@code Double}, {@code Boolean} and {@code null}.
 *
 * <p>Only this class uses the JSON parser library. Thus the library is not visible in the public
 * API.
 */
public final class Json {
  private static final JsonFactory FACTORY = new JsonFactory();

  private Json() {}

  /**
   * Reads JSON text into read-only plain values.
   *
   * <p>An integer becomes a {@code Long}. A number with a fraction or an exponent becomes a {@code
   * Double}, and an integer that is too large for a {@code Long} also becomes a {@code Double}. If
   * an object has the same key two times, the last value stays.
   *
   * @param text the text to read
   * @return the value. Maps and lists are read-only.
   * @throws A2uiParseError if the text is not one JSON value, or if a number is too large
   */
  public static Object parse(String text) {
    try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), text)) {
      Object value = read(parser, parser.nextToken());
      if (parser.nextToken() != null) {
        throw new A2uiParseError("Invalid JSON: there is content after the first value.");
      }
      return value;
    } catch (JacksonException e) {
      throw new A2uiParseError("Invalid JSON: " + e.getOriginalMessage(), e);
    }
  }

  /**
   * Reads the JSON object in a resource of the module.
   *
   * @param owner the class whose package contains the resource
   * @param name the file name of the resource
   * @return the object. It is read-only.
   * @throws IllegalStateException if the resource is absent or is not a JSON object
   */
  public static Map<String, Object> objectResource(Class<?> owner, String name) {
    String text;
    try (InputStream stream = owner.getResourceAsStream(name)) {
      if (stream == null) {
        throw new IllegalStateException(
            "The resource " + name + " is not in " + owner.getPackageName() + ".");
      }
      text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (!(parse(text) instanceof Map<?, ?> map)) {
      throw new IllegalStateException("The resource " + name + " is not a JSON object.");
    }
    return asObject(map);
  }

  /**
   * Makes a deep, read-only copy of a JSON value.
   *
   * <p>In the copy, each integer is a {@code Long} and each other number is a {@code Double}.
   *
   * @param value the value to copy
   * @return the copy
   * @throws IllegalArgumentException if the value contains an object that is not a JSON value
   */
  public static Object freeze(Object value) {
    return switch (value) {
      case null -> null;
      case String s -> s;
      case Boolean b -> b;
      case Long l -> l;
      case Integer i -> i.longValue();
      case Short s -> s.longValue();
      case Byte b -> b.longValue();
      case Double d -> finite(d);
      case Float f -> finite(f.doubleValue());
      case BigInteger big ->
          big.bitLength() <= 63 ? (Object) big.longValue() : finite(big.doubleValue());
      case BigDecimal big -> finite(big.doubleValue());
      case Map<?, ?> map -> {
        Map<String, Object> copy = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          if (!(entry.getKey() instanceof String key)) {
            throw new IllegalArgumentException(
                "A JSON object key must be a string, but got: " + entry.getKey());
          }
          copy.put(key, freeze(entry.getValue()));
        }
        yield Collections.unmodifiableMap(copy);
      }
      case Collection<?> collection -> {
        List<Object> copy = new ArrayList<>(collection.size());
        for (Object item : collection) {
          copy.add(freeze(item));
        }
        yield Collections.unmodifiableList(copy);
      }
      default ->
          throw new IllegalArgumentException("Not a JSON value: " + value.getClass().getName());
    };
  }

  /**
   * Makes a deep, read-only copy of a JSON object. See {@link #freeze(Object)}.
   *
   * @param object the object to copy
   * @return the copy
   * @throws IllegalArgumentException if the object contains an object that is not a JSON value
   */
  public static Map<String, Object> freezeObject(Map<String, ?> object) {
    return asObject((Map<?, ?>) freeze(object));
  }

  /**
   * Reads a value as a JSON object.
   *
   * <p>Use this method only for a value that {@link #parse(String)} or {@link #freeze(Object)}
   * made. Each key of such a map is a string.
   *
   * @param value the value to read
   * @return the object, or null if the value is not a map
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> asObject(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
  }

  /**
   * Encodes a text for use as one segment of a JSON Pointer.
   *
   * @param segment the text to encode
   * @return the encoded segment
   */
  public static String escapePointer(String segment) {
    return segment.replace("~", "~0").replace("/", "~1");
  }

  /**
   * Decodes one segment of a JSON Pointer. This is the opposite of {@link #escapePointer(String)}.
   *
   * @param segment the encoded segment
   * @return the decoded text
   */
  public static String unescapePointer(String segment) {
    return segment.replace("~1", "/").replace("~0", "~");
  }

  private static Object read(JsonParser parser, JsonToken token) {
    if (token == null) {
      throw new A2uiParseError("Invalid JSON: the text contains no value.");
    }
    return switch (token) {
      case START_OBJECT -> {
        Map<String, Object> map = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
          String key = parser.currentName();
          map.put(key, read(parser, parser.nextToken()));
        }
        yield Collections.unmodifiableMap(map);
      }
      case START_ARRAY -> {
        List<Object> list = new ArrayList<>();
        for (JsonToken item = parser.nextToken();
            item != JsonToken.END_ARRAY;
            item = parser.nextToken()) {
          list.add(read(parser, item));
        }
        yield Collections.unmodifiableList(list);
      }
      case VALUE_STRING -> parser.getString();
      case VALUE_NUMBER_INT ->
          parser.getNumberType() == JsonParser.NumberType.BIG_INTEGER
              ? (Object) parsedNumber(parser.getBigIntegerValue().doubleValue(), parser)
              : (Object) parser.getLongValue();
      case VALUE_NUMBER_FLOAT -> parsedNumber(parser.getDoubleValue(), parser);
      case VALUE_TRUE -> Boolean.TRUE;
      case VALUE_FALSE -> Boolean.FALSE;
      case VALUE_NULL -> null;
      default -> throw new A2uiParseError("Invalid JSON: unexpected token " + token + ".");
    };
  }

  private static Double parsedNumber(double value, JsonParser parser) {
    if (!Double.isFinite(value)) {
      throw new A2uiParseError("Invalid JSON: the number " + parser.getString() + " is too large.");
    }
    return value;
  }

  private static Double finite(double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("A JSON number must be finite, but got " + value);
    }
    return value;
  }
}
