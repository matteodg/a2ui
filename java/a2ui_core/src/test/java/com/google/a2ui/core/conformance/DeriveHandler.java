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

import static com.google.a2ui.core.conformance.JsonValues.asList;
import static com.google.a2ui.core.conformance.JsonValues.asObject;
import static com.google.a2ui.core.conformance.JsonValues.asString;
import static com.google.a2ui.core.conformance.JsonValues.assertJsonEquals;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * The action {@code derive}: derives a catalog from a loaded catalog, then examines the derived
 * catalog and its document.
 */
final class DeriveHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys(
          "action",
          "catalog",
          "catalogId",
          "catalogPath",
          "protocolVersion",
          "args",
          "expect",
          "expectError");

  private static final Set<String> ARGS_KEYS =
      Set.of("catalogId", "components", "functions", "instructions");

  private static final Set<String> EXPECT_KEYS =
      Set.of(
          "absentKeys",
          "catalogId",
          "components",
          "functions",
          "instructions",
          "protocolVersion",
          "theme");

  @Override
  public void run(ConformanceCase testCase) throws Exception {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    Catalog<ComponentApi, FunctionApi> source =
        CatalogCases.load(testCase, CatalogCases.document(testCase));
    Map<String, Object> args = asObject(fields.get("args"), "args in " + location);
    KnownKeys.assertKnownKeys(args, ARGS_KEYS, "args in " + location);

    Object expectError = ExpectedErrors.expectedError(fields);
    if (expectError != null) {
      ExpectedErrors.assertRaises(expectError, location, () -> derive(source, args, location));
      return;
    }

    Catalog<ComponentApi, FunctionApi> derived = derive(source, args, location);
    Map<String, Object> document = derived.toJson();
    Map<String, Object> expect = asObject(fields.get("expect"), "expect in " + location);
    KnownKeys.assertKnownKeys(expect, EXPECT_KEYS, "expect in " + location);

    if (expect.get("catalogId") != null) {
      assertJsonEquals(expect.get("catalogId"), derived.id(), "catalogId in " + location);
    }
    if (expect.get("protocolVersion") != null) {
      assertJsonEquals(
          expect.get("protocolVersion"),
          derived.protocolVersion().map(ProtocolVersion::wireValue).orElse(null),
          "protocolVersion in " + location);
    }
    if (expect.get("components") != null) {
      CatalogCases.assertEntries(
          "components",
          expect.get("components"),
          derived.components().keySet(),
          document,
          location);
    }
    if (expect.get("functions") != null) {
      CatalogCases.assertEntries(
          "functions", expect.get("functions"), derived.functions().keySet(), document, location);
    }
    if (expect.get("theme") != null) {
      CatalogCases.assertSameDocument(
          expect.get("theme"), document.get("theme"), "theme in " + location);
    }
    if (expect.containsKey("instructions")) {
      Object instructions = expect.get("instructions");
      assertJsonEquals(instructions, document.get("instructions"), "instructions in " + location);
      assertJsonEquals(
          instructions,
          derived.instructions().orElse(null),
          "instructions property in " + location);
    }
    if (expect.get("absentKeys") != null) {
      for (Object key : asList(expect.get("absentKeys"), "absentKeys in " + location)) {
        if (document.containsKey(asString(key, "absentKeys in " + location))) {
          throw new AssertionError(location + ": the derived document must not have '" + key + "'");
        }
      }
    }
  }

  /**
   * Calls {@code derive} with the arguments of a case.
   *
   * <p>A case gives the names to keep and the new entries in one list. A name keeps the entry of
   * the source. A map is a set of new entries: each key is a name and each value is a definition.
   */
  private static Catalog<ComponentApi, FunctionApi> derive(
      Catalog<ComponentApi, FunctionApi> source, Map<String, Object> args, String location) {
    List<Object> components =
        args.get("components") == null
            ? null
            : asList(args.get("components"), "args.components in " + location);
    List<Object> functions =
        args.get("functions") == null
            ? null
            : asList(args.get("functions"), "args.functions in " + location);
    return source.derive(
        asString(args.get("catalogId"), "args.catalogId in " + location),
        kept(components),
        added(components, ComponentApi::new),
        kept(functions),
        added(functions, FunctionApi::new),
        args.get("instructions") instanceof String instructions ? instructions : null);
  }

  /** Gets the names in a list of a case, or null if the case gives no list. */
  private static List<String> kept(List<Object> listed) {
    if (listed == null) {
      return null;
    }
    List<String> names = new ArrayList<>();
    for (Object item : listed) {
      if (item instanceof String name) {
        names.add(name);
      } else if (!(item instanceof Map<?, ?>)) {
        throw new AssertionError("An entry of a derive list must be a name or a map: " + item);
      }
    }
    return names;
  }

  /** Makes the new entries in a list of a case. */
  private static <E> List<E> added(
      List<Object> listed, BiFunction<String, Map<String, Object>, E> create) {
    List<E> entries = new ArrayList<>();
    if (listed == null) {
      return entries;
    }
    for (Object item : listed) {
      if (item instanceof Map<?, ?>) {
        asObject(item, "new entry")
            .forEach((name, schema) -> entries.add(create.apply(name, asObject(schema, name))));
      }
    }
    return entries;
  }
}
