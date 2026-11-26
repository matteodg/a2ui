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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.swing.conformance.SuiteRegistry.Supported;
import com.google.a2ui.swing.conformance.SuiteRegistry.Unsupported;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class SuiteRegistryTest {
  private static final Supported SURFACE_SUITE = new Supported(Set.of("render_surface"), Map.of());

  private static ConformanceCase caseOf(Map<String, Object> fields) {
    return new ConformanceCase("core/adapter_conformance.yaml", fields);
  }

  /** Makes a case whose one step makes a surface with components of the given types. */
  private static ConformanceCase surfaceCase(String version, String... componentTypes) {
    List<Map<String, Object>> components =
        List.of(componentTypes).stream()
            .map(type -> Map.<String, Object>of("id", type, "component", type))
            .toList();
    Map<String, Object> message =
        Map.of("version", "1.0", "createSurface", Map.of("components", components));
    return caseOf(
        Map.of(
            "name",
            "a",
            "action",
            "render_surface",
            "protocolVersion",
            version,
            "steps",
            List.of(Map.of("op", "process_messages", "messages", List.of(message)))));
  }

  @Test
  void theRegistryListsEachSuiteOfTheAdapterCorpus() throws IOException {
    Object corpora =
        ConformanceLoader.parse(
            Files.readString(ConformancePaths.conformancePath("manifest_corpora.json")));
    Object adapter = ((Map<?, ?>) corpora).get("adapter");
    Object include = ((Map<?, ?>) adapter).get("include");

    assertEquals(
        new TreeSet<>((List<?>) include), new TreeSet<>(SuiteRegistry.ADAPTER_SUITES.keySet()));
  }

  @Test
  void eachSuiteFileHasCases() {
    for (String suite : SuiteRegistry.ADAPTER_SUITES.keySet()) {
      assertTrue(ConformanceLoader.loadSuite(suite).size() > 0, suite);
    }
  }

  @Test
  void anUnsupportedSuiteSkipsEachCaseWithItsReason() {
    assertEquals(
        "reason", SuiteRegistry.skipReason(surfaceCase("1.0", "Text"), new Unsupported("reason")));
  }

  @Test
  void aCaseSkipsWithTheNamesOfTheBasicComponentsThatHaveNoView() {
    String reason =
        SuiteRegistry.skipReason(
            surfaceCase("1.0", "Text", "Divider", "Card", "StatusBadge"), SURFACE_SUITE);

    assertEquals("needs the Swing views of the basic catalog: Card, Divider", reason);
  }

  @Test
  void theHarnessReadsTheComponentsOfTheMessagesOfTheCaseAndOfEachStep() {
    ConformanceCase treeCase =
        ConformanceLoader.loadSuite("core/adapter_conformance.yaml").stream()
            .filter(testCase -> testCase.name().equals("tree_hierarchy_declared_order"))
            .findFirst()
            .orElseThrow();
    ConformanceCase accessibilityCase =
        ConformanceLoader.loadSuite("core/accessibility.yaml").stream()
            .filter(testCase -> testCase.name().equals("test_accessibility_hidden_subtree"))
            .findFirst()
            .orElseThrow();

    assertEquals(
        Set.of("Button", "Card", "Column", "Row", "Text"),
        SuiteRegistry.basicComponentsOf(treeCase));
    assertEquals(
        Set.of("Column", "Icon", "Text"), SuiteRegistry.basicComponentsOf(accessibilityCase));
  }

  @Test
  void aCaseOfADifferentProtocolVersionSkips() {
    String reason = SuiteRegistry.skipReason(surfaceCase("v0.9", "StatusBadge"), SURFACE_SUITE);

    assertTrue(reason.startsWith("Protocol version v0.9 is not supported"), reason);
  }

  @Test
  void aCaseWithAnUnknownActionFails() {
    ConformanceCase testCase = caseOf(Map.of("name", "a", "action", "render_everything"));

    AssertionError failure =
        assertThrows(AssertionError.class, () -> SuiteRegistry.skipReason(testCase, SURFACE_SUITE));

    assertEquals(
        "Unknown action 'render_everything' in core/adapter_conformance.yaml#a",
        failure.getMessage());
  }

  @Test
  void aCaseThatNeedsNoAbsentViewRunsAndFailsIfItsActionHasNoHandler() {
    ConformanceCase testCase = surfaceCase("1.0", "StatusBadge");

    assertNull(SuiteRegistry.skipReason(testCase, SURFACE_SUITE));
    AssertionError failure =
        assertThrows(AssertionError.class, () -> SuiteRegistry.handlerFor(testCase, SURFACE_SUITE));
    assertTrue(failure.getMessage().contains("has no handler"), failure.getMessage());
  }

  @Test
  void aCaseThatNeedsNoAbsentViewGetsTheHandlerOfItsAction() {
    CaseHandler handler = testCase -> {};
    Supported supported =
        new Supported(Set.of("render_surface"), Map.of("render_surface", handler));

    assertSame(handler, SuiteRegistry.handlerFor(surfaceCase("1.0", "StatusBadge"), supported));
  }

  @Test
  void eachCaseOfTheCorpusRunsWithAHandlerOrSkipsWithAReason() {
    SuiteRegistry.ADAPTER_SUITES.forEach((suite, support) -> {
      for (ConformanceCase testCase : ConformanceLoader.loadSuite(suite)) {
        String reason = SuiteRegistry.skipReason(testCase, support);
        if (reason == null) {
          assertNotNull(
              SuiteRegistry.handlerFor(testCase, (Supported) support), testCase.location());
        } else {
          assertFalse(reason.isBlank(), testCase.location());
        }
      }
    });
  }

  @Test
  void eachCaseOfTheMarkdownSuiteRuns() {
    String suite = "adapter/markdown_subset.yaml";
    for (ConformanceCase testCase : ConformanceLoader.loadSuite(suite)) {
      assertNull(
          SuiteRegistry.skipReason(testCase, SuiteRegistry.ADAPTER_SUITES.get(suite)),
          testCase.location());
    }
  }
}
