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

import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.validation.CommonTypeValidator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The action {@code validate_common_type}: the SDK accepts or rejects the {@code value} of each
 * step as the common type that the case names.
 *
 * <p>The suite also tells an SDK to write an accepted value again and to compare it with the input.
 * This SDK keeps a value as plain JSON and has no model that it can write. Thus an accepted value
 * is always the value of the step.
 */
final class ValidateCommonTypeHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys("action", "definition", "protocolVersion", "steps");

  private static final Set<String> STEP_KEYS = Set.of("expectError", "value");

  @Override
  public void run(ConformanceCase testCase) throws Exception {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    String definition = asString(fields.get("definition"), "definition in " + location);
    List<Object> steps = asList(fields.get("steps"), "steps in " + location);
    for (int index = 0; index < steps.size(); index++) {
      String where = "step " + index + " of " + location;
      Map<String, Object> step = asObject(steps.get(index), where);
      KnownKeys.assertKnownKeys(step, STEP_KEYS, where);
      Object value = step.get("value");
      Object expectError = ExpectedErrors.expectedError(step);
      if (expectError != null) {
        ExpectedErrors.assertRaises(
            expectError, where, () -> CommonTypeValidator.validate(definition, value));
        continue;
      }
      try {
        CommonTypeValidator.validate(definition, value);
      } catch (A2uiValidationError e) {
        throw new AssertionError(
            where + ": expected <" + value + "> to be valid: " + e.getMessage(), e);
      }
    }
  }
}
