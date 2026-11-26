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
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.core.resolution.DataContext;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The action {@code select_catalog}: the handler makes the surface of the case with its catalogs.
 * Then the surface resolves the catalog of each component, or a data context runs the function
 * call. The handler compares the catalog of the last selection with {@code expectSelected}.
 *
 * <p>Each catalog has a function with the name of the function call of the case. The function
 * records the ID of its catalog. Thus the handler sees the catalog in which the call ran.
 */
final class SelectCatalogHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys("action", "args", "catalog", "expectError", "expectSelected");

  private static final Set<String> ARG_KEYS =
      Set.of("catalogs", "components", "functionCall", "surface");

  private static final Set<String> SURFACE_KEYS =
      Set.of("defaultCatalogId", "id", "supportedCatalogIds");

  private static final Set<String> CALL_KEYS = Set.of("args", "call", "catalogId");

  private static final Set<String> CATALOG_KEYS = Set.of("protocolVersion");

  private static final Set<String> ERROR_KEYS = Set.of("category", "message");

  @Override
  public void run(ConformanceCase testCase) throws Exception {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    Map<String, Object> args = asObject(fields.get("args"), "args in " + location);
    KnownKeys.assertKnownKeys(args, ARG_KEYS, "args in " + location);
    Map<String, Object> surfaceArgs = asObject(args.get("surface"), "args.surface in " + location);
    KnownKeys.assertKnownKeys(surfaceArgs, SURFACE_KEYS, "args.surface in " + location);
    Map<String, Object> functionCall =
        args.get("functionCall") == null
            ? null
            : asObject(args.get("functionCall"), "args.functionCall in " + location);
    if (functionCall != null) {
      KnownKeys.assertKnownKeys(functionCall, CALL_KEYS, "args.functionCall in " + location);
    }
    boolean expectsError = fields.get("expectError") != null;
    if (expectsError == fields.containsKey("expectSelected")) {
      throw new AssertionError(location + " needs expectError or expectSelected, and not the two");
    }

    List<String> selections = new ArrayList<>();
    ExpectedErrors.Block select =
        () -> select(testCase, args, surfaceArgs, functionCall, selections);
    if (expectsError) {
      assertRejected(fields.get("expectError"), select, location);
      return;
    }
    select.run();
    if (selections.isEmpty()) {
      throw new AssertionError(location + ": the case selected no catalog");
    }
    assertJsonEquals(
        fields.get("expectSelected"), selections.getLast(), "selected catalog in " + location);
  }

  /** Makes the surface of a case and records the ID of each catalog that it selects. */
  private static void select(
      ConformanceCase testCase,
      Map<String, Object> args,
      Map<String, Object> surfaceArgs,
      Map<String, Object> functionCall,
      List<String> selections) {
    String location = testCase.location();
    ProtocolVersion surfaceVersion = version(testCase.protocolVersion(), location);
    String functionName =
        functionCall == null
            ? null
            : asString(functionCall.get("call"), "functionCall.call in " + location);
    String defaultId =
        asString(surfaceArgs.get("defaultCatalogId"), "defaultCatalogId in " + location);

    Map<String, Catalog<ComponentApi, FunctionImplementation>> catalogs = new LinkedHashMap<>();
    if (args.get("catalogs") != null) {
      asObject(args.get("catalogs"), "args.catalogs in " + location)
          .forEach((id, raw) -> {
            Map<String, Object> declared = asObject(raw, "catalog " + id + " in " + location);
            KnownKeys.assertKnownKeys(declared, CATALOG_KEYS, "catalog " + id + " in " + location);
            ProtocolVersion version =
                declared.get("protocolVersion") == null
                    ? surfaceVersion
                    : version(String.valueOf(declared.get("protocolVersion")), location);
            catalogs.put(id, catalog(id, version, functionName, selections));
          });
    } else {
      for (Object id :
          asList(surfaceArgs.get("supportedCatalogIds"), "supportedCatalogIds in " + location)) {
        String catalogId = asString(id, "supportedCatalogIds in " + location);
        catalogs.put(catalogId, catalog(catalogId, surfaceVersion, functionName, selections));
      }
    }

    SurfaceModel<ComponentApi> surface =
        new SurfaceModel<>(
            surfaceArgs.get("id") == null
                ? "main_surface"
                : asString(surfaceArgs.get("id"), "surface.id in " + location),
            surfaceVersion,
            catalogs.get(defaultId),
            catalogs,
            false,
            null);
    if (args.get("components") != null) {
      asObject(args.get("components"), "args.components in " + location)
          .forEach((id, raw) -> {
            Object catalogId = asObject(raw, "component " + id).get("catalogId");
            String named = catalogId == null ? null : asString(catalogId, "catalogId of " + id);
            selections.add(surface.resolveComponentCatalog(named, id).id());
          });
    }
    if (functionCall != null) {
      Map<String, Object> call = new LinkedHashMap<>();
      call.put("@call", functionName);
      if (functionCall.get("catalogId") != null) {
        call.put("catalogId", functionCall.get("catalogId"));
      }
      call.put("args", functionCall.get("args") == null ? Map.of() : functionCall.get("args"));
      new DataContext(surface).resolveDynamicValue(call);
    }
  }

  private static ProtocolVersion version(String text, String location) {
    return ProtocolVersion.tryParse(text == null ? "1.0" : text)
        .orElseThrow(() -> new AssertionError(location + ": unknown protocol version " + text));
  }

  /** Makes a catalog whose function records the ID of the catalog when it runs. */
  private static Catalog<ComponentApi, FunctionImplementation> catalog(
      String id, ProtocolVersion version, String functionName, List<String> selections) {
    List<FunctionImplementation> functions =
        functionName == null
            ? List.of()
            : List.of(
                new FunctionImplementation(
                    functionName,
                    Map.of("returnType", "any"),
                    (args, context) -> {
                      selections.add(id);
                      return null;
                    }));
    return new Catalog<>(id, List.of(), functions, null, version);
  }

  private static void assertRejected(
      Object expectError, ExpectedErrors.Block select, String location) throws Exception {
    Map<String, Object> expected = asObject(expectError, "expectError in " + location);
    KnownKeys.assertKnownKeys(expected, ERROR_KEYS, "expectError in " + location);
    ExpectedErrors.assertRaises(expected, location, select);
  }
}
