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

package com.google.a2ui.core.conformance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/** Reads conformance suites with a YAML 1.2 parser and converts them to plain values. */
final class ConformanceLoader {
  private static final LoadSettings SETTINGS =
      LoadSettings.builder()
          .setCodePointLimit(64 * 1024 * 1024)
          .setMaxAliasesForCollections(10_000)
          .build();

  private ConformanceLoader() {}

  /**
   * Loads the cases of a suite.
   *
   * <p>The method fails if the file contains no cases. Thus an incorrect path cannot pass as an
   * empty suite.
   *
   * @param suite the path of the suite relative to {@code conformance/}
   */
  static List<ConformanceCase> loadSuite(String suite) {
    String text;
    try {
      text = Files.readString(ConformancePaths.conformancePath(suite));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (!(parse(text) instanceof List<?> items)) {
      throw new IllegalStateException(suite + " is not a top-level list of cases");
    }
    if (items.isEmpty()) {
      throw new IllegalStateException(suite + " contains no cases");
    }
    List<ConformanceCase> cases = new ArrayList<>(items.size());
    for (Object item : items) {
      if (!(item instanceof Map<?, ?>)) {
        throw new IllegalStateException(suite + " has a case that is not a map: " + item);
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> fields = (Map<String, Object>) item;
      cases.add(new ConformanceCase(suite, fields));
    }
    return cases;
  }

  /** Reads one YAML document into maps with string keys, lists and scalar values. */
  static Object parse(String yaml) {
    return toPlain(new Load(SETTINGS).loadFromString(yaml));
  }

  private static Object toPlain(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>(map.size());
      map.forEach((key, item) -> result.put(String.valueOf(key), toPlain(item)));
      return result;
    }
    if (value instanceof Iterable<?> iterable) {
      List<Object> result = new ArrayList<>();
      iterable.forEach(item -> result.add(toPlain(item)));
      return result;
    }
    return value;
  }
}
