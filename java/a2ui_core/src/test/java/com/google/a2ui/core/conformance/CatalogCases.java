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

import static com.google.a2ui.core.conformance.JsonValues.asObject;
import static com.google.a2ui.core.conformance.JsonValues.asString;
import static com.google.a2ui.core.conformance.JsonValues.jsonEquals;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.internal.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The code that the catalog actions share: it reads the catalog of a case and compares documents.
 */
final class CatalogCases {
  private CatalogCases() {}

  /**
   * Gets the catalog document of a case. The case gives it inline ({@code catalog}) or as a path in
   * the repository ({@code catalogPath}).
   */
  static Map<String, Object> document(ConformanceCase testCase) {
    Object inline = testCase.fields().get("catalog");
    if (inline != null) {
      return asObject(inline, "catalog in " + testCase.location());
    }
    return jsonFile(
        asString(testCase.fields().get("catalogPath"), "catalogPath in " + testCase.location()));
  }

  /**
   * Reads the JSON object in a file.
   *
   * @param path the path of the file relative to the root of the repository
   */
  static Map<String, Object> jsonFile(String path) {
    try {
      return asObject(Json.parse(Files.readString(ConformancePaths.repoPath(path))), path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Gets the version that a case gives at its top level.
   *
   * @return the version, or null if the case gives none
   */
  static ProtocolVersion version(ConformanceCase testCase) {
    if (!(testCase.fields().get("protocolVersion") instanceof String text)) {
      return null;
    }
    return ProtocolVersion.tryParse(text)
        .orElseThrow(() -> new AssertionError("Unknown version in " + testCase.location()));
  }

  /** Loads a document with the {@code catalogId} and the version of a case. */
  static Catalog<ComponentApi, FunctionApi> load(
      ConformanceCase testCase, Map<String, Object> document) {
    return Catalog.fromJson(document, catalogId(testCase), version(testCase));
  }

  /** Gets the {@code catalogId} that a case gives at its top level, or null. */
  static String catalogId(ConformanceCase testCase) {
    return testCase.fields().get("catalogId") instanceof String id ? id : null;
  }

  /**
   * Fails if the actual value is not the expected document.
   *
   * <p>The sequence of the keys of an object is not important. The sequence of the values of {@code
   * enum} and of the names of {@code required} is also not important.
   */
  static void assertSameDocument(Object expected, Object actual, String where) {
    String difference = difference(expected, actual, "", null);
    if (difference != null) {
      throw new AssertionError(where + " differs at " + difference);
    }
  }

  /**
   * Compares the {@code components} or the {@code functions} of an expectation with a catalog.
   *
   * <p>The names must be the names of the catalog. If the expectation gives definitions, each
   * definition must be equal to the definition that {@code toJson} writes.
   *
   * @param section {@code components} or {@code functions}
   * @param listed the expectation: a list of names, or a map from a name to a definition
   * @param actualNames the names of the entries of the catalog
   * @param document the result of {@code toJson}
   * @param where the location for failure messages
   */
  static void assertEntries(
      String section,
      Object listed,
      Set<String> actualNames,
      Map<String, Object> document,
      String where) {
    Set<String> expectedNames = names(listed, section + " in " + where);
    if (!expectedNames.equals(actualNames)) {
      throw new AssertionError(
          where + ": expected " + section + " " + expectedNames + " but was " + actualNames);
    }
    if (listed instanceof Map<?, ?> definitions) {
      Map<String, Object> written =
          asObject(document.get(section), section + " that toJson writes in " + where);
      definitions.forEach((name, definition) ->
          assertSameDocument(
              definition, written.get(name), section + " '" + name + "' in " + where));
    }
  }

  /** Gets the entry names of an expectation: a list of names, or the keys of a map. */
  private static Set<String> names(Object listed, String where) {
    Set<String> names = new LinkedHashSet<>();
    if (listed instanceof Map<?, ?> map) {
      names.addAll(asObject(map, where).keySet());
    } else if (listed instanceof List<?> list) {
      list.forEach(item -> names.add(asString(item, where)));
    } else {
      throw new AssertionError(where + " must be a list of names or an object");
    }
    return names;
  }

  /**
   * Finds the first difference between two documents.
   *
   * @param key the key of the object that contains the values, or null
   * @return the description of the difference, or null if the documents are the same
   */
  private static String difference(Object expected, Object actual, String path, String key) {
    if (expected instanceof Map<?, ?> x && actual instanceof Map<?, ?> y) {
      Set<Object> names = new LinkedHashSet<>(x.keySet());
      names.addAll(y.keySet());
      for (Object name : names) {
        if (!y.containsKey(name)) {
          return path + "/" + name + ": missing, expected <" + x.get(name) + ">";
        }
        if (!x.containsKey(name)) {
          return path + "/" + name + ": unexpected <" + y.get(name) + ">";
        }
        String difference =
            difference(x.get(name), y.get(name), path + "/" + name, String.valueOf(name));
        if (difference != null) {
          return difference;
        }
      }
      return null;
    }
    if (expected instanceof List<?> x && actual instanceof List<?> y && x.size() == y.size()) {
      if ("enum".equals(key) || "required".equals(key)) {
        List<Object> unmatched = new ArrayList<>(y);
        for (Object item : x) {
          int match = indexOf(unmatched, item);
          if (match < 0) {
            return path + ": expected <" + x + "> in any order but was <" + y + ">";
          }
          unmatched.remove(match);
        }
        return null;
      }
      for (int index = 0; index < x.size(); index++) {
        String difference = difference(x.get(index), y.get(index), path + "/" + index, null);
        if (difference != null) {
          return difference;
        }
      }
      return null;
    }
    return jsonEquals(expected, actual)
        ? null
        : path + ": expected <" + expected + "> but was <" + actual + ">";
  }

  /** Gets the index of the first value of a list that is the same JSON value as an item, or -1. */
  private static int indexOf(List<Object> values, Object item) {
    for (int index = 0; index < values.size(); index++) {
      if (jsonEquals(item, values.get(index))) {
        return index;
      }
    }
    return -1;
  }
}
