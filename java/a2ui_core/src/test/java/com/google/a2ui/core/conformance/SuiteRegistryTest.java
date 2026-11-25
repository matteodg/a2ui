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
