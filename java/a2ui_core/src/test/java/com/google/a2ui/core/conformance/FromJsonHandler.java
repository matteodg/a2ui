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
import static com.google.a2ui.core.conformance.JsonValues.assertJsonEquals;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The action {@code from_json}: loads a catalog document and examines the catalog. */
final class FromJsonHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys(
          "action",
          "catalog",
          "catalogId",
          "catalogPath",
          "protocolVersion",
          "expect",
          "expectError");

  private static final Set<String> EXPECT_KEYS =
      Set.of(
          "catalogId",
          "componentMetadata",
          "components",
          "functions",
          "matchesBuiltinCatalog",
          "protocolVersion");

  @Override
  public void run(ConformanceCase testCase) throws Exception {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    Map<String, Object> document = CatalogCases.document(testCase);
    // If a case gives no version, a document that declares none is a v0.9 document. The harnesses
    // of the other SDKs do the same.
    ProtocolVersion caseVersion = CatalogCases.version(testCase);
    ProtocolVersion version = caseVersion != null ? caseVersion : ProtocolVersion.V0_9;
    String catalogId = CatalogCases.catalogId(testCase);

    Object expectError = ExpectedErrors.expectedError(fields);
    if (expectError != null) {
      ExpectedErrors.assertRaises(
          expectError, location, () -> Catalog.fromJson(document, catalogId, version));
      return;
    }

    Catalog<ComponentApi, FunctionApi> catalog = Catalog.fromJson(document, catalogId, version);
    Map<String, Object> expect =
        fields.get("expect") == null
            ? Map.of()
            : asObject(fields.get("expect"), "expect in " + location);
    KnownKeys.assertKnownKeys(expect, EXPECT_KEYS, "expect in " + location);
    Map<String, Object> written = catalog.toJson();

    if (expect.get("catalogId") != null) {
      assertJsonEquals(expect.get("catalogId"), catalog.id(), "catalogId in " + location);
    }
    if (expect.get("protocolVersion") != null) {
      assertJsonEquals(
          expect.get("protocolVersion"),
          catalog.protocolVersion().map(ProtocolVersion::wireValue).orElse(null),
          "protocolVersion in " + location);
    }
    if (expect.get("components") != null) {
      CatalogCases.assertEntries(
          "components", expect.get("components"), catalog.components().keySet(), written, location);
    }
    if (expect.get("functions") != null) {
      CatalogCases.assertEntries(
          "functions", expect.get("functions"), catalog.functions().keySet(), written, location);
    }
    if (expect.get("componentMetadata") != null) {
      Map<String, Object> metadata =
          asObject(expect.get("componentMetadata"), "componentMetadata in " + location);
      metadata.forEach((name, expected) ->
          assertJsonEquals(
              expected,
              metadataOf(catalog.components().get(name)),
              "metadata of '" + name + "' in " + location));
    }
    if (Boolean.TRUE.equals(expect.get("matchesBuiltinCatalog"))) {
      assertMatchesBuiltin(catalog, location);
    }
  }

  /** Converts the metadata of a component to the notation of the suite, or to null. */
  private static Map<String, Object> metadataOf(ComponentApi component) {
    if (component == null || component.metadata().isEmpty()) {
      return null;
    }
    A2uiMetadata metadata = component.metadata().get();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("extensions", metadata.extensions().orElse(null));
    return result;
  }

  /** Fails if a loaded catalog is not equivalent to the catalog of this SDK with the same ID. */
  private static void assertMatchesBuiltin(
      Catalog<ComponentApi, FunctionApi> loaded, String where) {
    if (!BasicCatalog.ID.equals(loaded.id())) {
      throw new AssertionError(where + ": the SDK has no catalog with the ID " + loaded.id());
    }
    Catalog<ComponentApi, FunctionApi> builtin = BasicCatalog.api();
    assertJsonEquals(
        builtin.protocolVersion(), loaded.protocolVersion(), "protocolVersion in " + where);
    assertJsonEquals(signatures(builtin), signatures(loaded), "function signatures in " + where);
    CatalogCases.assertSameDocument(builtin.toJson(), loaded.toJson(), "catalog in " + where);
  }

  private static Map<String, Object> signatures(Catalog<ComponentApi, FunctionApi> catalog) {
    Map<String, Object> signatures = new LinkedHashMap<>();
    catalog
        .functions()
        .forEach((name, function) ->
            signatures.put(
                name,
                List.of(
                    function.returnType(),
                    function.allowedCallers(),
                    function.requiresUserActivation())));
    return signatures;
  }
}
