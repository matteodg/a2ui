/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.a2ui.swing.conformance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.testing.Edt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Makes sure that the handler of the scenarios fails when a scenario does not agree with the
 * adapter. Each test changes one part of a copy of a scenario of {@code conformance/e2e/}.
 */
class E2eScenarioHandlerTest {
  private static final String LOGIN = "e2e/login_form_action.yaml";

  /** Makes a copy of the first scenario of a suite that the test can change. */
  private static Map<String, Object> scenario(String suite) {
    return asMap(copy(ConformanceLoader.loadSuite(suite).getFirst().fields()));
  }

  private static Object copy(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, item) -> result.put((String) key, copy(item)));
      return result;
    }
    if (value instanceof List<?> list) {
      List<Object> result = new ArrayList<>();
      list.forEach(item -> result.add(copy(item)));
      return result;
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> asList(Object value) {
    return (List<Object>) value;
  }

  private static Map<String, Object> turn(Map<String, Object> scenario, int index) {
    return asMap(asList(scenario.get("turns")).get(index));
  }

  /** Gets the node at a sequence of child indexes below the tree of the surface of a turn. */
  private static Map<String, Object> viewNode(
      Map<String, Object> turn, String surfaceId, int... path) {
    Map<String, Object> node = asMap(asMap(turn.get("expectViewTree")).get(surfaceId));
    for (int index : path) {
      node = asMap(asList(node.get("children")).get(index));
    }
    return node;
  }

  private static void run(Map<String, Object> scenario) {
    Edt.run(() -> new E2eScenarioHandler().run(new ConformanceCase(LOGIN, scenario)));
  }

  /** Runs the login scenario with a change, and gets the message of its failure. */
  private static String failureOf(Consumer<Map<String, Object>> change) {
    Map<String, Object> scenario = scenario(LOGIN);
    change.accept(scenario);
    return assertThrows(AssertionError.class, () -> run(scenario)).getMessage();
  }

  @Test
  void theScenarioPassesWithNoChange() {
    assertDoesNotThrow(() -> run(scenario(LOGIN)));
  }

  @Test
  void aTextThatTheNodeDoesNotHaveFails() {
    String failure =
        failureOf(scenario -> viewNode(turn(scenario, 0), "login", 0, 0).put("text", "### Other"));

    assertTrue(failure.contains("text of 'heading'"), failure);
  }

  @Test
  void aLabelThatTheNodeDoesNotHaveFails() {
    String failure =
        failureOf(scenario -> viewNode(turn(scenario, 0), "login", 0, 1).put("label", "Name"));

    assertTrue(failure.contains("label of 'emailField'"), failure);
  }

  @Test
  void aValueThatTheControlDoesNotShowFails() {
    String failure =
        failureOf(scenario -> viewNode(turn(scenario, 0), "login", 0, 1).put("value", "a"));

    assertTrue(failure.contains("value of 'emailField'"), failure);
  }

  @Test
  void aControlThatIsNotDisabledFailsAnExpectationOfADisabledControl() {
    String failure =
        failureOf(scenario -> viewNode(turn(scenario, 0), "login", 0, 3).put("disabled", true));

    assertTrue(failure.contains("disabled of 'signInButton'"), failure);
  }

  @Test
  void aChildThatTheViewDoesNotShowFails() {
    String failure =
        failureOf(scenario ->
            asList(viewNode(turn(scenario, 0), "login", 0).get("children")).removeLast());

    assertTrue(failure.contains("children of 'form'"), failure);
  }

  @Test
  void aSurfaceThatExistsFailsAnExpectationOfNoSurface() {
    String failure =
        failureOf(scenario -> asMap(turn(scenario, 0).get("expectViewTree")).put("login", null));

    assertTrue(failure.contains("the surface 'login' exists"), failure);
  }

  @Test
  void aDataModelThatIsDifferentFails() {
    String failure =
        failureOf(scenario ->
            asMap(asMap(turn(scenario, 0).get("expectDataModel")).get("login")).put("email", "a"));

    assertTrue(failure.contains("expectDataModel"), failure);
  }

  @Test
  void anOutboundMessageThatIsDifferentFails() {
    String failure =
        failureOf(scenario -> {
          Map<String, Object> message =
              asMap(asList(turn(scenario, 0).get("expectOutbound")).get(0));
          asMap(asMap(message.get("action")).get("context")).put("email", "other@example.com");
        });

    assertTrue(failure.contains("expectOutbound"), failure);
  }

  @Test
  void aMessageThatTheScenarioDoesNotExpectFails() {
    String failure = failureOf(scenario -> turn(scenario, 0).put("expectOutbound", List.of()));

    assertTrue(failure.contains("expected 0 messages"), failure);
  }

  @Test
  void anErrorThatTheSurfaceDidNotReportFails() {
    String failure =
        failureOf(scenario ->
            turn(scenario, 0).put("expectErrors", List.of(Map.of("code", "VALIDATION_FAILED"))));

    assertTrue(failure.contains("expected 1 errors"), failure);
  }

  @Test
  void theInputOfTheUserGoesThroughTheControl() {
    // With no input in the password field, the data model keeps the empty password.
    String failure = failureOf(scenario -> asList(turn(scenario, 0).get("uiOps")).remove(1));

    assertTrue(failure.contains("expectDataModel"), failure);
  }

  @Test
  void anUnknownKeyOfATurnFails() {
    String failure = failureOf(scenario -> turn(scenario, 0).put("expectTheme", true));

    assertTrue(failure.contains("Unknown key 'expectTheme'"), failure);
  }

  @Test
  void anUnknownKeyOfAnOperationFails() {
    String failure =
        failureOf(scenario -> asMap(asList(turn(scenario, 0).get("uiOps")).get(0)).put("delay", 1));

    assertTrue(failure.contains("Unknown key 'delay'"), failure);
  }

  @Test
  void anOperationThatIsNotOfTheUserFails() {
    String failure =
        failureOf(scenario ->
            asMap(asList(turn(scenario, 0).get("uiOps")).get(0)).put("op", "unmount_surface"));

    assertTrue(failure.contains("Unknown op 'unmount_surface'"), failure);
  }

  @Test
  void anOperationOnASurfaceThatDoesNotExistFails() {
    String failure =
        failureOf(scenario ->
            asMap(asList(turn(scenario, 0).get("uiOps")).get(0)).put("surfaceId", "other"));

    assertTrue(failure.contains("there is no surface 'other'"), failure);
  }

  @Test
  void aCatalogThatIsNotTheBasicCatalogFails() {
    String failure =
        failureOf(scenario -> scenario.put("catalogPaths", List.of("catalogs/mcp/catalog.json")));

    assertTrue(failure.contains("catalogPaths"), failure);
  }
}
