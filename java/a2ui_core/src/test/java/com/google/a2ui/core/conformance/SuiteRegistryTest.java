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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.core.conformance.SuiteRegistry.Supported;
import com.google.a2ui.core.conformance.SuiteRegistry.Unsupported;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SuiteRegistryTest {
  @Test
  void theRegistryListsEachSuiteFileInTheCoreDirectory() throws IOException {
    Set<String> onDisk = new TreeSet<>();
    try (Stream<Path> files = Files.list(ConformancePaths.conformancePath("core"))) {
      files
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".yaml"))
          .forEach(name -> onDisk.add("core/" + name));
    }

    assertEquals(onDisk, new TreeSet<>(SuiteRegistry.CORE_SUITES.keySet()));
  }

  @Test
  void anUnsupportedSuiteSkipsEachCaseWithItsReason() {
    ConformanceCase testCase = caseOf(Map.of("name", "a", "protocolVersion", "1.0"));

    assertEquals("reason", SuiteRegistry.skipReason(testCase, new Unsupported("reason")));
  }

  @Test
  void aSupportedSuiteSkipsACaseOfADifferentProtocolVersion() {
    Supported supported = new Supported(Map.of());

    String reason =
        SuiteRegistry.skipReason(caseOf(Map.of("name", "a", "protocolVersion", "v0.9")), supported);

    assertTrue(reason.startsWith("Protocol version v0.9 is not supported"), reason);
  }

  @Test
  void aSupportedSuiteReadsTheProtocolVersionOfTheInlineCatalog() {
    Supported supported = new Supported(Map.of());
    ConformanceCase testCase =
        caseOf(Map.of("name", "a", "catalog", Map.of("protocolVersion", "v0.8")));

    assertTrue(SuiteRegistry.skipReason(testCase, supported).startsWith("Protocol version v0.8"));
  }

  @Test
  void aSupportedSuiteRunsACaseOfVersion1AndACaseWithNoVersion() {
    Supported supported = new Supported(Map.of());

    assertNull(
        SuiteRegistry.skipReason(caseOf(Map.of("name", "a", "protocolVersion", "1.0")), supported));
    assertNull(SuiteRegistry.skipReason(caseOf(Map.of("name", "a")), supported));
  }

  @Test
  void anUnsupportedActionSkipsItsCasesWithItsReason() {
    Supported supported =
        new Supported(Map.of(), SuiteRegistry.reasons("validate", "later"), Set.of());
    ConformanceCase testCase =
        caseOf(Map.of("name", "a", "action", "validate", "protocolVersion", "1.0"));

    assertEquals("later", SuiteRegistry.skipReason(testCase, supported));
  }

  @Test
  void anUnsupportedKeyPathSkipsOnlyTheCasesThatSetTheKey() {
    Supported supported =
        new Supported(Map.of(), SuiteRegistry.reasons("load.expect.deep", "later"), Set.of());
    Map<String, Object> deep = new LinkedHashMap<>();
    deep.put("deep", null);
    ConformanceCase setsKey = caseOf(Map.of("name", "a", "action", "load", "expect", deep));
    ConformanceCase otherKey =
        caseOf(Map.of("name", "b", "action", "load", "expect", Map.of("shallow", 1)));
    ConformanceCase otherAction =
        caseOf(Map.of("name", "c", "action", "save", "expect", Map.of("deep", 1)));

    assertEquals("later", SuiteRegistry.skipReason(setsKey, supported));
    assertNull(SuiteRegistry.skipReason(otherKey, supported));
    assertNull(SuiteRegistry.skipReason(otherAction, supported));
  }

  @Test
  void anUnsupportedKeyPathGoesThroughTheItemsOfAList() {
    Supported supported =
        new Supported(Map.of(), SuiteRegistry.reasons("load.steps.expect.deep", "later"), Set.of());
    ConformanceCase oneStepSetsKey =
        caseOf(
            Map.of(
                "name",
                "a",
                "action",
                "load",
                "steps",
                List.of(Map.of("input", 1), Map.of("expect", Map.of("deep", 1)))));
    ConformanceCase noStepSetsKey =
        caseOf(
            Map.of(
                "name", "b", "action", "load", "steps", List.of(Map.of("expect", Map.of("x", 1)))));

    assertEquals("later", SuiteRegistry.skipReason(oneStepSetsKey, supported));
    assertNull(SuiteRegistry.skipReason(noStepSetsKey, supported));
  }

  @Test
  void theFirstUnsupportedKeyThatAppliesGivesTheReason() {
    ConformanceCase testCase =
        caseOf(Map.of("name", "a", "action", "load", "expect", Map.of("first", 1, "second", 2)));
    Supported firstThenSecond =
        new Supported(
            Map.of(),
            SuiteRegistry.reasons("load.expect.first", "one", "load.expect.second", "two"),
            Set.of());
    Supported secondThenFirst =
        new Supported(
            Map.of(),
            SuiteRegistry.reasons("load.expect.second", "two", "load.expect.first", "one"),
            Set.of());

    assertEquals("one", SuiteRegistry.skipReason(testCase, firstThenSecond));
    assertEquals("two", SuiteRegistry.skipReason(testCase, secondThenFirst));
  }

  @Test
  void reasonsFailsForAKeyWithNoReasonAndForAKeyThatIsThereTwice() {
    assertThrows(IllegalArgumentException.class, () -> SuiteRegistry.reasons("load"));
    assertThrows(
        IllegalArgumentException.class, () -> SuiteRegistry.reasons("load", "one", "load", "two"));
  }

  @Test
  void eachRegisteredHandlerAndEachUnsupportedEntryMatchesACase() {
    SuiteRegistry.CORE_SUITES.forEach((suite, support) -> {
      if (!(support instanceof Supported supported)) {
        return;
      }
      List<ConformanceCase> cases = ConformanceLoader.loadSuite(suite);
      Set<String> withNoCase = new TreeSet<>(supported.actions().keySet());
      cases.forEach(testCase -> withNoCase.remove(testCase.action()));
      Set<String> unmatched = new TreeSet<>(supported.unsupported().keySet());
      unmatched.removeIf(target ->
          cases.stream().anyMatch(testCase -> SuiteRegistry.matchesUnsupported(testCase, target)));
      Set<String> anyVersionWithNoHandler = new TreeSet<>(supported.anyVersion());
      anyVersionWithNoHandler.removeAll(supported.actions().keySet());

      assertEquals(Set.of(), withNoCase, "Handlers for " + suite + " with no case");
      assertEquals(Set.of(), unmatched, "Unsupported entries for " + suite + " with no case");
      assertEquals(Set.of(), anyVersionWithNoHandler, "anyVersion in " + suite);
    });
  }

  @Test
  void anActionForEachVersionRunsACaseOfADifferentProtocolVersion() {
    Supported supported = new Supported(Map.of(), SuiteRegistry.reasons(), Set.of("data_model"));
    ConformanceCase testCase =
        caseOf(Map.of("name", "a", "action", "data_model", "protocolVersion", "v0.9"));

    assertNull(SuiteRegistry.skipReason(testCase, supported));
  }

  @Test
  void handlerForSelectsTheHandlerOfTheAction() {
    CaseHandler handler = testCase -> {};
    Supported supported = new Supported(Map.of("get", handler));

    assertSame(
        handler, SuiteRegistry.handlerFor(caseOf(Map.of("name", "a", "action", "get")), supported));
  }

  @Test
  void handlerForFailsForAnUnknownAction() {
    Supported supported = new Supported(Map.of());
    ConformanceCase testCase = caseOf(Map.of("name", "a", "action", "unknown"));

    AssertionError error =
        assertThrows(AssertionError.class, () -> SuiteRegistry.handlerFor(testCase, supported));

    assertEquals("Unknown action 'unknown' in core/example.yaml#a", error.getMessage());
  }

  @Test
  void assertKnownKeysFailsForAKeyThatIsNotPermitted() {
    AssertionError error =
        assertThrows(
            AssertionError.class,
            () -> KnownKeys.assertKnownKeys(Map.of("extra", 1), Set.of("name"), "the case"));

    assertEquals("Unknown key 'extra' in the case", error.getMessage());
  }

  @Test
  void theLoaderConvertsYamlToPlainValues() {
    Object parsed = ConformanceLoader.parse("- name: a\n  flag: yes\n  size: 1.0e+3\n  count: 2\n");

    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("name", "a");
    // YAML 1.2 reads `yes` as a string, not as a boolean.
    expected.put("flag", "yes");
    expected.put("size", 1000.0);
    expected.put("count", 2);
    assertEquals(List.of(expected), parsed);
  }

  @Test
  void theManifestWritesOneLineOfJson() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("case", "a \"quoted\" name");
    fields.put("reason", "line1\nline2\\");

    assertEquals(
        "{\"case\":\"a \\\"quoted\\\" name\",\"reason\":\"line1\\nline2\\\\\"}",
        ConformanceManifest.toJson(fields));
  }

  private static ConformanceCase caseOf(Map<String, Object> fields) {
    return new ConformanceCase("core/example.yaml", fields);
  }
}
