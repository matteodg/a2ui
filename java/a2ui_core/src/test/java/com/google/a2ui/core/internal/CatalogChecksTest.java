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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
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
        List.of("Unrecognized component type 'Carousel' in catalog '" + BasicCatalog.ID + "'"),
        checks.componentErrors(carousel, true, false));
    assertEquals(List.of(), checks.componentErrors(carousel, true, true));
  }

  @Test
  void theCallsOfAComponentOfAnUnknownTypeAreExamined() {
    Map<String, Object> carousel =
        Json.freezeObject(
            Map.of("id", "c", "component", "Carousel", "label", Map.of("@call", "sh-out")));

    List<String> errors = checks.componentErrors(carousel, true, true);

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
        checks.componentErrors(text, true, false));
    assertEquals(List.of(), checks.componentErrors(text, true, true));
    assertEquals(List.of(), checks.componentErrors(text, false, false));
  }

  @Test
  void callErrorsExaminesOneCallInThisCatalogAndNotTheCallsInItsArguments() {
    Map<String, Object> inner = Map.of("@call", "teleport");
    Map<String, Object> call =
        Json.freezeObject(
            Map.of("@call", "openUrl", "catalogId", "urn:other", "args", Map.of("url", inner)));
    Map<String, Object> noArguments =
        Json.freezeObject(Map.of("@call", "openUrl", "catalogId", "urn:other"));

    assertEquals(List.of(), checks.callErrors(call, "/action", false));
    List<String> errors = checks.callErrors(noArguments, "/action", false);
    assertEquals(1, errors.size(), errors.toString());
    assertTrue(errors.get(0).startsWith("/action: "), errors.toString());
  }

  @Test
  void nestedCallErrorsExaminesEachCallBelowAValue() {
    Object value =
        Json.freeze(
            List.of(
                Map.of("@call", "teleport"), Map.of("a", Map.of("@call", "@now")), "plain text"));

    assertEquals(
        List.of(
            "/0: Unrecognized function 'teleport' in catalog '" + BasicCatalog.ID + "'",
            "/1/a: unknown system function '@now'"),
        checks.nestedCallErrors(value, true, false));
    assertEquals(
        List.of("/1/a: unknown system function '@now'"),
        checks.nestedCallErrors(value, true, true));
  }
}
