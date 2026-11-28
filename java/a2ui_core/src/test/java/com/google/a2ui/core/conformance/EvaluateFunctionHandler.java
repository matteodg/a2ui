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
import static com.google.a2ui.core.conformance.JsonValues.jsonEquals;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.resolution.DataContext;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.core.validation.ValidationConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The action {@code evaluate_function}.
 *
 * <p>A case with no {@code surface} block runs the code of a function of the basic catalog with the
 * {@code args} of the case. This does not examine the permitted callers and the user gesture.
 *
 * <p>A case with a {@code surface} block gives its catalogs to a message processor and makes a
 * surface. Then the data context of the surface evaluates the call. Thus the case examines the
 * catalog of the call, the permitted callers and the coercion of the arguments.
 */
final class EvaluateFunctionHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys(
          "action",
          "args",
          "catalogId",
          "catalogPaths",
          "dataModel",
          "expect",
          "expectError",
          "function",
          "protocolVersion",
          "surface");

  private static final Set<String> SURFACE_KEYS = Set.of("catalogId");

  private static final String SURFACE_ID = "s";

  /** The basic catalog with its functions. The cases only examine that a URL is accepted. */
  private static final Catalog<ComponentApi, FunctionImplementation> BASIC =
      BasicCatalog.withFunctions(Locale.US, uri -> {});

  @Override
  public void run(ConformanceCase testCase) throws Exception {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    String function = asString(fields.get("function"), "function in " + location);
    Map<String, Object> args =
        fields.get("args") == null ? Map.of() : asObject(fields.get("args"), "args in " + location);
    boolean onSurface = fields.containsKey("surface");

    Object expectError = ExpectedErrors.expectedError(fields);
    if (expectError != null) {
      ExpectedErrors.assertRaises(
          expectError,
          location,
          () -> {
            if (onSurface) {
              onSurface(testCase, function, args);
            } else {
              direct(function, args, location);
            }
          });
      return;
    }
    if (!fields.containsKey("expect")) {
      throw new AssertionError(location + " has no 'expect' and no 'expectError'");
    }
    Object actual =
        onSurface ? onSurface(testCase, function, args) : direct(function, args, location);
    Object expected = fields.get("expect");
    if (!matches(expected, actual, testCase.protocolVersion() == null)) {
      throw new AssertionError(location + ": expected <" + expected + "> but was <" + actual + ">");
    }
  }

  /**
   * Tells if a result is the expected value.
   *
   * <p>From v1.0, a validation function returns a validation result: an object with a boolean
   * {@code valid}. There are two special comparisons for such a result:
   *
   * <ul>
   *   <li>A case that gives no protocol version can expect a boolean, because the function returns
   *       a boolean before v1.0. Then the {@code valid} of the result must be that boolean.
   *   <li>A case can expect a validation result. Then each member that the case gives must be equal
   *       in the result. The result can have more members, for example a message, because the suite
   *       does not specify them.
   * </ul>
   *
   * @param anyVersion true if the case gives no protocol version
   */
  private static boolean matches(Object expected, Object actual, boolean anyVersion) {
    if (actual instanceof Map<?, ?> result && result.get("valid") instanceof Boolean valid) {
      if (expected instanceof Boolean flag && anyVersion) {
        return flag.equals(valid);
      }
      if (expected instanceof Map<?, ?> members && members.get("valid") instanceof Boolean) {
        return members.entrySet().stream()
            .allMatch(member -> jsonEquals(member.getValue(), result.get(member.getKey())));
      }
    }
    return jsonEquals(expected, actual);
  }

  /** Runs the code of a function of the basic catalog. */
  private static Object direct(String function, Map<String, Object> args, String location) {
    FunctionImplementation implementation = BASIC.functions().get(function);
    if (implementation == null) {
      throw new AssertionError(
          "The basic catalog has no function '" + function + "' in " + location);
    }
    SurfaceModel<ComponentApi> surface =
        new SurfaceModel<>(
            SURFACE_ID, ProtocolVersion.V1_0, BASIC, Map.of(BASIC.id(), BASIC), false, null);
    return implementation.execute(args, new DataContext(surface));
  }

  /** Evaluates the call through the data context of a surface that a message processor makes. */
  private static Object onSurface(
      ConformanceCase testCase, String function, Map<String, Object> args) {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    Map<String, Object> block = asObject(fields.get("surface"), "surface in " + location);
    KnownKeys.assertKnownKeys(block, SURFACE_KEYS, "surface in " + location);

    List<Catalog<ComponentApi, ?>> catalogs = new ArrayList<>();
    if (fields.get("catalogPaths") != null) {
      for (Object path : asList(fields.get("catalogPaths"), "catalogPaths in " + location)) {
        Map<String, Object> document =
            CatalogCases.jsonFile(asString(path, "catalogPaths entry in " + location));
        // The catalog document has signatures only. Thus the basic catalog with its code replaces
        // the document of the basic catalog.
        catalogs.add(
            BASIC.id().equals(document.get("catalogId")) ? BASIC : Catalog.fromJson(document));
      }
    }
    MessageProcessor<ComponentApi> processor =
        new MessageProcessor<>(catalogs, ValidationConfig.RELAXED);
    Map<String, Object> createSurface = new LinkedHashMap<>();
    createSurface.put("surfaceId", SURFACE_ID);
    if (block.get("catalogId") != null) {
      createSurface.put("catalogId", block.get("catalogId"));
    }
    if (fields.get("dataModel") != null) {
      createSurface.put("dataModel", fields.get("dataModel"));
    }
    processor.processMessages(
        Map.of("version", ProtocolVersion.V1_0.wireValue(), "createSurface", createSurface));
    SurfaceModel<ComponentApi> surface = processor.model().getSurface(SURFACE_ID).orElseThrow();

    Map<String, Object> call = new LinkedHashMap<>();
    call.put("@call", function);
    if (fields.get("catalogId") != null) {
      call.put("catalogId", fields.get("catalogId"));
    }
    call.put("args", args);
    return new DataContext(surface).resolveDynamicValue(call);
  }
}
