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

import static com.google.a2ui.swing.conformance.JsonValues.asList;
import static com.google.a2ui.swing.conformance.JsonValues.asObject;
import static com.google.a2ui.swing.conformance.JsonValues.assertJsonEquals;

import com.google.a2ui.core.validation.ValidationConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the action {@code accessibility_check} of {@code core/accessibility.yaml}.
 *
 * <p>The handler mounts the surface from the messages of the case, or from the messages of each
 * step. Then it reads the accessibility attributes from the Swing component that has them: the
 * accessible name and the accessible description of its {@code AccessibleContext}, its accessible
 * role, and the {@code live} and {@code hidden} attributes that the adapter keeps as a client
 * property. For a hidden component, the handler also makes sure that the adapter hides the
 * component and each component below it: each one has the hidden mark of the adapter and an empty
 * accessible name.
 *
 * <p>The handler does not use the key {@code surface}, which is for a harness with no views. It
 * does not run the {@code axeCore} rules, which need a DOM. The top level assertions of a case with
 * steps describe bindings for a harness with no views. Thus the handler uses the assertions of the
 * steps for such a case.
 */
final class AccessibilityCheckHandler implements SuiteRegistry.CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys(
          "action", "protocolVersion", "catalogPath", "messages", "steps", "surface", "assertions");

  private static final Set<String> STEP_KEYS = Set.of("messages", "assertions");
  private static final Set<String> ASSERTION_KEYS = Set.of("axeCore", "accessibilityTree");

  /** The axe-core rules of the suite. The handler accepts them and does not run them. */
  private static final Set<String> AXE_RULES =
      Set.of("button-name", "aria-valid-attr-value", "aria-allowed-attr", "aria-hidden-focus");

  @Override
  public void run(ConformanceCase testCase) {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    assertJsonEquals(
        AdapterSession.BASIC_CATALOG_PATH, fields.get("catalogPath"), "catalogPath in " + location);
    AdapterSession session = new AdapterSession(location, null, ValidationConfig.STRICT);
    Map<String, Object> assertions =
        asObject(fields.get("assertions"), "assertions in " + location);
    assertKnown(assertions, "assertions in " + location);
    if (fields.containsKey("steps")) {
      List<Object> steps = asList(fields.get("steps"), "steps in " + location);
      for (int i = 0; i < steps.size(); i++) {
        String where = "step " + i + " of " + location;
        Map<String, Object> step = asObject(steps.get(i), where);
        KnownKeys.assertKnownKeys(step, STEP_KEYS, where);
        session.startStep();
        session.process(step.get("messages"));
        check(session, asObject(step.get("assertions"), "assertions of " + where), where);
      }
    } else {
      session.process(fields.get("messages"));
      check(session, assertions, location);
    }
    if (!session.errors.isEmpty()) {
      throw new AssertionError(location + ": the surface reported " + session.errors);
    }
    session.view.removeNotify();
  }

  private static void assertKnown(Map<String, Object> assertions, String where) {
    KnownKeys.assertKnownKeys(assertions, ASSERTION_KEYS, where);
    if (assertions.containsKey("axeCore")) {
      for (Object rule : asList(assertions.get("axeCore"), "axeCore of " + where)) {
        if (!AXE_RULES.contains(rule)) {
          throw new AssertionError("Unknown axeCore rule '" + rule + "' in " + where);
        }
      }
    }
  }

  private static void check(AdapterSession session, Map<String, Object> assertions, String where) {
    assertKnown(assertions, "assertions of " + where);
    if (assertions.containsKey("accessibilityTree")) {
      String at = "accessibilityTree of " + where;
      session.assertAccessibility(asObject(assertions.get("accessibilityTree"), at), at);
    }
  }
}
