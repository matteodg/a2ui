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

package com.google.a2ui.core.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The behavior of {@link CatalogChecks} that the tests of {@code PayloadValidator} do not examine.
 */
class CatalogChecksTest {
  private final CatalogChecks checks = new CatalogChecks(BasicCatalog.api());

  @Test
  void anUnknownComponentTypeIsAProblemUnlessUnknownElementsAreAllowed() {
    Map<String, Object> carousel = Map.of("id", "c", "component", "Carousel");

    assertEquals(
        List.of(
            "/component: Unrecognized component type 'Carousel' in catalog '"
                + BasicCatalog.ID
                + "'"),
        texts(checks.componentProblems(carousel, true, false)));
    assertEquals(List.of(), texts(checks.componentProblems(carousel, true, true)));
  }

  @Test
  void theCallsOfAComponentOfAnUnknownTypeAreExamined() {
    Map<String, Object> carousel =
        Json.freezeObject(
            Map.of("id", "c", "component", "Carousel", "label", Map.of("@call", "sh-out")));

    List<String> errors = texts(checks.componentProblems(carousel, true, true));

    assertEquals(
        List.of("/label: function name 'sh-out' is not a valid UAX #31 identifier"), errors);
  }

  @Test
  void anUnknownFunctionIsAProblemUnlessUnknownElementsAreAllowed() {
    Map<String, Object> text =
        Json.freezeObject(
            Map.of("id", "t", "component", "Text", "text", Map.of("@call", "teleport")));

    assertEquals(
        List.of("/text: Unrecognized function 'teleport' in catalog '" + BasicCatalog.ID + "'"),
        texts(checks.componentProblems(text, true, false)));
    assertEquals(List.of(), texts(checks.componentProblems(text, true, true)));
    assertEquals(List.of(), texts(checks.componentProblems(text, false, false)));
  }

  @Test
  void callProblemsExaminesOneCallInThisCatalogAndNotTheCallsInItsArguments() {
    Map<String, Object> inner = Map.of("@call", "teleport");
    Map<String, Object> call =
        Json.freezeObject(
            Map.of("@call", "openUrl", "catalogId", "urn:other", "args", Map.of("url", inner)));
    Map<String, Object> noArguments =
        Json.freezeObject(Map.of("@call", "openUrl", "catalogId", "urn:other"));

    assertEquals(List.of(), texts(checks.callProblems(call, "/action", false)));
    List<String> errors = texts(checks.callProblems(noArguments, "/action", false));
    assertEquals(1, errors.size(), errors.toString());
    assertTrue(errors.get(0).startsWith("/action/args: "), errors.toString());
  }

  @Test
  void nestedCallProblemsExaminesEachCallBelowAValue() {
    Object value =
        Json.freeze(
            List.of(
                Map.of("@call", "teleport"), Map.of("a", Map.of("@call", "@now")), "plain text"));

    assertEquals(
        List.of(
            "/0: Unrecognized function 'teleport' in catalog '" + BasicCatalog.ID + "'",
            "/1/a: unknown system function '@now'"),
        texts(checks.nestedCallProblems(value, true, false)));
    assertEquals(
        List.of("/1/a: unknown system function '@now'"),
        texts(checks.nestedCallProblems(value, true, true)));
  }

  @Test
  void aProblemOfAPropertyIsReportedWithoutTheUnusedPropertiesThatFollowFromIt() {
    Map<String, Object> text = Json.freezeObject(Map.of("id", "t", "component", "Text", "text", 5));

    List<Problem> problems = checks.componentProblems(text, true, false);

    assertFalse(problems.isEmpty());
    assertTrue(
        problems.stream().allMatch(problem -> problem.path().equals("/text")), problems.toString());
    assertTrue(problems.stream().noneMatch(Problem::unevaluated), problems.toString());
  }

  @Test
  void anAbsentPropertyAndAnUnknownPropertyHaveTheirOwnPath() {
    Map<String, Object> absent = Json.freezeObject(Map.of("id", "t", "component", "Text"));
    Map<String, Object> unknown =
        Json.freezeObject(Map.of("id", "t", "component", "Text", "text", "x", "bogus", 1));

    List<Problem> missing = checks.componentProblems(absent, true, false);
    List<Problem> extra = checks.componentProblems(unknown, true, false);

    assertEquals(1, missing.size(), missing.toString());
    assertEquals("/text", missing.get(0).path());
    assertEquals(Problem.MISSING_FIELD, missing.get(0).code());
    assertEquals(1, extra.size(), extra.toString());
    assertEquals("/bogus", extra.get(0).path());
    assertEquals(Problem.INVALID_VALUE, extra.get(0).code());
  }

  @Test
  void aProblemOfTheEnvelopeIsReportedWithoutTheUnusedPropertiesThatFollowFromIt() {
    Map<String, Object> text =
        Json.freezeObject(
            Map.of("id", "t", "component", "Text", "text", "x", "@metadata", Map.of("bogus", 1)));

    List<Problem> problems = checks.componentProblems(text, true, false);

    assertEquals(1, problems.size(), problems.toString());
    assertEquals("/@metadata/bogus", problems.get(0).path());
  }

  @Test
  void aProblemOfAnArgumentIsReportedWithoutTheUnusedPropertiesThatFollowFromIt() {
    Map<String, Object> call =
        Json.freezeObject(
            Map.of("@call", "formatDate", "args", Map.of("value", "now", "format", 5)));

    List<Problem> problems = checks.nestedCallProblems(Map.of("label", call), true, false);

    assertFalse(problems.isEmpty());
    assertTrue(
        problems.stream().allMatch(problem -> problem.path().equals("/label/args/format")),
        problems.toString());
  }

  @Test
  void aComponentCannotCallAFunctionThatOnlyTheAgentCanCall() {
    CatalogChecks custom =
        new CatalogChecks(
            Catalog.fromJson(
                Json.asObject(
                    Json.parse(
                        """
                        {
                          "catalogId": "urn:custom",
                          "protocolVersion": "1.0",
                          "functions": {
                            "audit": {
                              "returnType": "string",
                              "allowedCallers": "agentOnly",
                              "properties": {"@call": {"const": "audit"}},
                              "required": ["@call"]
                            }
                          }
                        }
                        """))));
    Object value = Json.freeze(Map.of("label", Map.of("@call", "audit")));

    assertEquals(
        List.of("/label: Function 'audit' is agentOnly, so the renderer cannot call it"),
        texts(custom.nestedCallProblems(value, true, false)));
  }

  private static List<String> texts(List<Problem> problems) {
    return problems.stream().map(Problem::toString).toList();
  }
}
