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
import static com.google.a2ui.core.conformance.JsonValues.asString;
import static com.google.a2ui.core.conformance.JsonValues.assertJsonEquals;

import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.expressions.Expression;
import com.google.a2ui.core.expressions.ExpressionParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The action {@code parse_expression_template}: reads {@code input} and compares the parts with
 * {@code expect}.
 */
final class ParseExpressionTemplateHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      Set.of(
          "name",
          "description",
          "features",
          "action",
          "expect",
          "expectError",
          "expect_error",
          "input");

  /** The suite uses the category {@code ParseError} for a template that is not correct. */
  private static final Map<String, Class<? extends A2uiError>> CATEGORIES =
      Map.of("ParseError", A2uiExpressionError.class);

  @Override
  public void run(ConformanceCase testCase) throws Exception {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    String input = asString(fields.get("input"), "input in " + location);

    Object expectError = ExpectedErrors.expectedError(fields);
    if (expectError != null) {
      ExpectedErrors.assertRaises(
          expectError, location, CATEGORIES, () -> ExpressionParser.parseTemplate(input));
      return;
    }

    List<Object> expected = asList(fields.get("expect"), "expect in " + location);
    List<Object> actual = new ArrayList<>();
    for (Expression part : ExpressionParser.parseTemplate(input)) {
      actual.add(toSuiteForm(part));
    }
    assertJsonEquals(expected, joinLiterals(actual), location);
  }

  /**
   * Converts a part to the notation of the suite: a literal value, {@code {path}}, or {@code {call,
   * args, returnType}}.
   */
  private static Object toSuiteForm(Expression part) {
    return switch (part) {
      case Expression.Literal literal -> literal.value();
      case Expression.Path path -> Map.of("path", path.path());
      case Expression.Call call -> {
        Map<String, Object> args = new LinkedHashMap<>();
        call.args().forEach((name, value) -> args.put(name, toSuiteForm(value)));
        yield Map.of("call", call.name(), "args", args, "returnType", "any");
      }
    };
  }

  /**
   * Joins adjacent string parts and removes empty strings.
   *
   * <p>A case gives the meaning of a template. It does not give the positions where a parser
   * divides the literal text.
   */
  private static List<Object> joinLiterals(List<Object> parts) {
    List<Object> joined = new ArrayList<>();
    for (Object part : parts) {
      int last = joined.size() - 1;
      if (part instanceof String text && last >= 0 && joined.get(last) instanceof String previous) {
        joined.set(last, previous + text);
      } else {
        joined.add(part);
      }
    }
    joined.removeIf(""::equals);
    return joined;
  }
}
