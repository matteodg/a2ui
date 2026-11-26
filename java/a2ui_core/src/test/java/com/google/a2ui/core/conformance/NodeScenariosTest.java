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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the shared node resolution scenarios whose fixture uses a catalog from before v1.0. The test
 * writes them again in the v1.0 syntax.
 *
 * <p>Most cases of {@code node_resolution.yaml} use fixtures of the v0.9.1 basic catalog. This SDK
 * cannot load such a fixture. Thus the conformance run records those cases as skipped. The behavior
 * that they examine (identity, notification counts, templates, placeholders, checks) is the same in
 * each version, and this test examines it.
 *
 * <p>These runs are unit tests of this SDK. They are not in the conformance manifest.
 */
class NodeScenariosTest {
  private static final String V1_BASIC_CATALOG = "../catalogs/basic/v1/catalog.json";

  /** The names that the envelope of a component has from v1.0, by their names before v1.0. */
  private static final Map<String, String> ENVELOPE_KEYS =
      Map.of("checks", "@checks", "accessibility", "@accessibility");

  @TestFactory
  List<DynamicTest> theScenariosFromBeforeVersion1RunInTheSyntaxOfVersion1() {
    List<DynamicTest> tests = new ArrayList<>();
    for (ConformanceCase testCase : ConformanceLoader.loadSuite("core/node_resolution.yaml")) {
      String version = testCase.protocolVersion();
      if (version == null || SuiteRegistry.SUPPORTED_PROTOCOL_VERSIONS.contains(version)) {
        // The conformance run does this case.
        continue;
      }
      tests.add(
          dynamicTest(
              testCase.name(),
              () -> {
                Map<String, Object> fixture =
                    new LinkedHashMap<>(ResolveNodesHandler.fixture(testCase.fields()));
                fixture.put("catalog", V1_BASIC_CATALOG);
                fixture.put("components", rewriteComponents(fixture.get("components")));
                Map<String, Object> fields = new LinkedHashMap<>(testCase.fields());
                List<Object> steps = new ArrayList<>();
                for (Object step : asList(fields.get("steps"), "steps")) {
                  steps.add(rewriteStep(step));
                }
                fields.put("steps", steps);
                new NodeScenario(testCase.location() + " (as v1.0)", fixture, fields).run();
              }));
    }
    // If each fixture uses a v1.0 catalog, the conformance run does this work. Then remove this
    // test.
    assertFalse(tests.isEmpty(), "No scenario uses a fixture from before v1.0");
    return tests;
  }

  private static Object rewriteStep(Object raw) {
    Map<String, Object> step = new LinkedHashMap<>(asObject(raw, "step"));
    if (step.get("components") != null) {
      step.put("components", rewriteComponents(step.get("components")));
    }
    if (step.get("messages") != null) {
      List<Object> messages = new ArrayList<>();
      for (Object message : asList(step.get("messages"), "messages")) {
        Map<String, Object> rewritten = new LinkedHashMap<>(asObject(message, "message"));
        rewritten.put("version", "1.0");
        if (rewritten.get("updateComponents") != null) {
          Map<String, Object> body =
              new LinkedHashMap<>(asObject(rewritten.get("updateComponents"), "updateComponents"));
          body.put("components", rewriteComponents(body.get("components")));
          rewritten.put("updateComponents", body);
        }
        messages.add(rewritten);
      }
      step.put("messages", messages);
    }
    return step;
  }

  /** Writes a list of component definitions from before v1.0 in the v1.0 syntax. */
  private static List<Object> rewriteComponents(Object components) {
    List<Object> rewritten = new ArrayList<>();
    if (components == null) {
      return rewritten;
    }
    for (Object raw : asList(components, "components")) {
      Map<String, Object> component = new LinkedHashMap<>();
      asObject(raw, "component")
          .forEach((key, value) ->
              component.put(ENVELOPE_KEYS.getOrDefault(key, key), rewriteValue(value)));
      rewritten.add(component);
    }
    return rewritten;
  }

  private static Object rewriteValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      // A data binding has the key `@path` from v1.0. A template keeps its key `path`.
      if (map.keySet().equals(Set.of("path"))) {
        return Map.of("@path", map.get("path"));
      }
      Map<Object, Object> rewritten = new LinkedHashMap<>();
      map.forEach((key, member) ->
          rewritten.put(
              key.equals("call") && member instanceof String ? "@call" : key,
              rewriteValue(member)));
      return rewritten;
    }
    if (value instanceof List<?> list) {
      List<Object> rewritten = new ArrayList<>();
      list.forEach(item -> rewritten.add(rewriteValue(item)));
      return rewritten;
    }
    return value;
  }
}
