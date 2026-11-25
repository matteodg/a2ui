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

package com.google.a2ui.core.expressions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.expressions.Expression.Call;
import com.google.a2ui.core.expressions.Expression.Literal;
import com.google.a2ui.core.expressions.Expression.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExpressionParserTest {
  @Test
  void textWithNoInterpolationIsOneLiteral() {
    assertEquals(List.of(new Literal("Hello")), ExpressionParser.parseTemplate("Hello"));
    assertEquals(List.of(), ExpressionParser.parseTemplate(""));
  }

  @Test
  void anInterpolationGivesAPathBetweenTheLiterals() {
    assertEquals(
        List.of(new Literal("Hello, "), new Path("/user/name"), new Literal("!")),
        ExpressionParser.parseTemplate("Hello, ${/user/name}!"));
  }

  @Test
  void aRelativePathCanContainDotsAndHyphens() {
    assertEquals(
        List.of(new Path("first-name.x")), ExpressionParser.parseTemplate("${first-name.x}"));
  }

  @Test
  void anEscapedOpeningIsLiteralText() {
    assertEquals(List.of(new Literal("${name}")), ExpressionParser.parseTemplate("\\${name}"));
  }

  @Test
  void theParserReadsLiteralValues() {
    assertEquals(List.of(new Literal("a}b")), ExpressionParser.parseTemplate("${'a}b'}"));
    assertEquals(List.of(new Literal("a\nb")), ExpressionParser.parseTemplate("${\"a\\nb\"}"));
    assertEquals(List.of(new Literal(42L)), ExpressionParser.parseTemplate("${42}"));
    assertEquals(List.of(new Literal(-1.5)), ExpressionParser.parseTemplate("${-1.5}"));
    assertEquals(List.of(new Literal(1000.0)), ExpressionParser.parseTemplate("${1e3}"));
    assertEquals(List.of(new Literal(true)), ExpressionParser.parseTemplate("${true}"));
    assertEquals(List.of(new Literal(false)), ExpressionParser.parseTemplate("${ false }"));
  }

  @Test
  void emptyContentGivesNoPart() {
    assertEquals(List.of(), ExpressionParser.parseTemplate("${null}"));
    assertEquals(List.of(), ExpressionParser.parseTemplate("${}"));
    assertEquals(List.of(), ExpressionParser.parseTemplate("${''}"));
  }

  @Test
  void aKeywordAtTheStartOfALongerNameIsAPath() {
    assertEquals(List.of(new Path("trueValue")), ExpressionParser.parseTemplate("${trueValue}"));
    assertEquals(List.of(new Path("nullable")), ExpressionParser.parseTemplate("${nullable}"));
  }

  @Test
  void aCallKeepsItsNamedArgumentsInSequence() {
    Map<String, Expression> args = new LinkedHashMap<>();
    args.put("value", new Path("/date"));
    args.put("format", new Literal("yyyy-MM-dd"));

    List<Expression> parts =
        ExpressionParser.parseTemplate("${formatDate(value: ${/date}, format: 'yyyy-MM-dd')}");

    assertEquals(List.of(new Call("formatDate", args)), parts);
    Call call = (Call) parts.get(0);
    assertEquals(List.of("value", "format"), new ArrayList<>(call.args().keySet()));
  }

  @Test
  void aCallCanHaveNoArgumentsAndCanBeASystemFunction() {
    assertEquals(List.of(new Call("now", Map.of())), ExpressionParser.parseTemplate("${now()}"));
    assertEquals(
        List.of(new Call("@index", Map.of("offset", new Literal(1L)))),
        ExpressionParser.parseTemplate("${@index(offset: 1)}"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "${/name",
        "${'open}",
        "${1.2.3}",
        "${1e}",
        "${a b}",
        "${@index}",
        "${@1()}",
        "${/a~2}",
        "${f(a 1)}",
        "${f(a: 1}",
        "${a~0b()}"
      })
  void theParserRejectsAnIncorrectTemplate(String template) {
    assertThrows(A2uiExpressionError.class, () -> ExpressionParser.parseTemplate(template));
  }

  @Test
  void theParserRejectsATemplateThatIsTooLong() {
    String template = "a".repeat(ExpressionParser.MAX_TEMPLATE_LENGTH + 1);

    A2uiExpressionError error =
        assertThrows(A2uiExpressionError.class, () -> ExpressionParser.parseTemplate(template));

    assertTrue(error.getMessage().contains("exceeds maximum limit"), error.getMessage());
  }

  @Test
  void theParserRejectsATemplateWithTooManyParts() {
    String template = "${a}".repeat(ExpressionParser.MAX_TEMPLATE_PARTS + 1);

    assertThrows(A2uiExpressionError.class, () -> ExpressionParser.parseTemplate(template));
  }

  @Test
  void theParserRejectsATemplateThatIsTooDeep() {
    int depth = ExpressionParser.MAX_DEPTH + 1;
    String template = "${".repeat(depth) + "a" + "}".repeat(depth);

    A2uiExpressionError error =
        assertThrows(A2uiExpressionError.class, () -> ExpressionParser.parseTemplate(template));

    assertEquals("Max recursion depth reached in parse", error.getMessage());
  }
}
