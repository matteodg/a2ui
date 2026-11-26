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

package com.google.a2ui.core.resolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionBody;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.exceptions.A2uiRpcError;
import com.google.a2ui.core.exceptions.RpcErrorCode;
import com.google.a2ui.core.expressions.ExpressionParser;
import com.google.a2ui.core.internal.AgentCalls;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * A context for a call of the agent applies the caller boundary of the agent to each call that it
 * evaluates. A context of the renderer applies the caller boundary of the renderer.
 */
class AgentCallContextTest {
  private static final String CATALOG_ID = "urn:test:callers";
  private static final String OTHER_ID = "urn:test:other";

  /** The names of the functions that ran, in the sequence of the calls. */
  private final List<String> calls = new ArrayList<>();

  private final Catalog<ComponentApi, FunctionImplementation> catalog =
      new Catalog<>(
          CATALOG_ID,
          List.of(),
          List.of(
              function("forAll", Map.of("allowedCallers", "rendererOrAgent"), args -> "all"),
              function("forAgent", Map.of("allowedCallers", "agentOnly"), args -> "agent"),
              function("forRenderer", Map.of(), args -> "renderer"),
              function(
                  "beep",
                  Map.of("allowedCallers", "rendererOrAgent", "requiresUserActivation", true),
                  args -> "beep"),
              function("echo", Map.of("allowedCallers", "rendererOrAgent"), args -> args),
              // Evaluates a template in the context of the call, as `formatString` does.
              new FunctionImplementation(
                  "render",
                  Map.of("allowedCallers", "rendererOrAgent"),
                  (args, context) -> {
                    calls.add("render");
                    StringBuilder text = new StringBuilder();
                    context
                        .evaluateTemplate(
                            ExpressionParser.parseTemplate(Coercion.toText(args.get("value"))))
                        .forEach(value -> text.append(Coercion.toText(value)));
                    return text.toString();
                  })),
          null,
          ProtocolVersion.V1_0);

  /** A catalog that has a function {@code forAll} with a different result. */
  private final Catalog<ComponentApi, FunctionImplementation> other =
      new Catalog<>(
          OTHER_ID,
          List.of(),
          List.of(function("forAll", Map.of("allowedCallers", "rendererOrAgent"), args -> "other")),
          null,
          ProtocolVersion.V1_0);

  private final SurfaceModel<ComponentApi> surface =
      new SurfaceModel<>(
          "s",
          ProtocolVersion.V1_0,
          other,
          Map.of(CATALOG_ID, catalog, OTHER_ID, other),
          false,
          null);

  private final DataContext renderer = new DataContext(surface);
  private final DataContext agent = AgentCalls.context(renderer, catalog, false);

  private FunctionImplementation function(
      String name, Map<String, Object> schema, Function<Map<String, Object>, Object> body) {
    FunctionBody recorded =
        (args, context) -> {
          calls.add(name);
          return body.apply(args);
        };
    return new FunctionImplementation(name, schema, recorded);
  }

  private static Map<String, Object> call(String name) {
    return Map.of("@call", name, "catalogId", CATALOG_ID, "args", Map.of());
  }

  private static Map<String, Object> call(String name, String argument, Object value) {
    return Map.of("@call", name, "catalogId", CATALOG_ID, "args", Map.of(argument, value));
  }

  private static A2uiRpcError refusal(DataContext context, Object value) {
    A2uiRpcError error = assertThrows(A2uiRpcError.class, () -> context.resolveDynamicValue(value));
    assertEquals(RpcErrorCode.INVALID_FUNCTION_CALL, error.code());
    return error;
  }

  @Test
  void theAgentCallsAFunctionThatPermitsAgentCallers() {
    assertEquals("all", agent.resolveDynamicValue(call("forAll")));
    assertEquals("agent", agent.resolveDynamicValue(call("forAgent")));
    assertEquals(
        Map.of("token", "agent"),
        agent.resolveDynamicValue(call("echo", "token", call("forAgent"))));
  }

  @Test
  void theAgentCannotCallAFunctionOfTheRendererOnly() {
    A2uiRpcError direct = refusal(agent, call("forRenderer"));
    A2uiRpcError nested = refusal(agent, call("echo", "leaked", call("forRenderer")));

    assertEquals(
        "Function 'forRenderer' cannot be called by agent (allowedCallers is rendererOnly).",
        direct.getMessage());
    assertEquals(direct.getMessage(), nested.getMessage());
    assertEquals(List.of(), calls);
  }

  @Test
  void theRendererContextKeepsTheBoundaryOfTheRenderer() {
    assertEquals("renderer", renderer.resolveDynamicValue(call("forRenderer")));

    A2uiExpressionError error =
        assertThrows(
            A2uiExpressionError.class, () -> renderer.resolveDynamicValue(call("forAgent")));
    assertEquals(
        "Function 'forAgent' is agentOnly and cannot be called by renderer code.",
        error.getMessage());
  }

  @Test
  void aFunctionThatRequiresAUserGestureRunsForTheAgentOnlyWithAGesture() {
    A2uiRpcError error = refusal(agent, call("beep"));

    assertEquals(
        "Function 'beep' requires user activation context to execute.", error.getMessage());
    assertEquals(
        "beep", AgentCalls.context(renderer, catalog, true).resolveDynamicValue(call("beep")));
  }

  @Test
  void aTemplateCallOfTheAgentRunsInTheCatalogOfTheAgentCall() {
    // The default catalog of the surface has a different `forAll`.
    assertEquals(
        List.of("all"), agent.evaluateTemplate(ExpressionParser.parseTemplate("${forAll()}")));
    assertEquals(
        List.of("other"), renderer.evaluateTemplate(ExpressionParser.parseTemplate("${forAll()}")));
  }

  @Test
  void aTemplateOfTheAgentIsExaminedBeforeAPartRuns() {
    A2uiRpcError error =
        refusal(agent, call("render", "value", "${forAll()} ${echo(value: ${forRenderer()})}"));

    assertEquals(
        "Function 'forRenderer' cannot be called by agent (allowedCallers is rendererOnly).",
        error.getMessage());
    // The function that evaluates the template started. No part of the template ran.
    assertEquals(List.of("render"), calls);
  }

  @Test
  void aCallerRefusalComesBeforeADifferentFailureOfAnEarlierPart() {
    A2uiRpcError boundary = refusal(agent, call("render", "value", "${missing()}${forRenderer()}"));
    A2uiRpcError gesture = refusal(agent, call("render", "value", "${missing()}${beep()}"));

    assertEquals(
        "Function 'forRenderer' cannot be called by agent (allowedCallers is rendererOnly).",
        boundary.getMessage());
    assertEquals(
        "Function 'beep' requires user activation context to execute.", gesture.getMessage());
  }

  @Test
  void aTemplateWithNoRefusalReportsItsOwnFailure() {
    A2uiExpressionError error =
        assertThrows(
            A2uiExpressionError.class,
            () -> agent.resolveDynamicValue(call("render", "value", "${forAgent()}${missing()}")));

    assertEquals("Unknown function 'missing' in catalog '" + CATALOG_ID + "'.", error.getMessage());
    assertEquals(List.of("render", "forAgent"), calls);
  }

  @Test
  void aContextBelowAnAgentContextIsAnAgentContext() {
    surface.dataModel().set("/items", List.of(Map.of("id", 1L)));

    DataContext item = agent.nested("/items/0", 0);

    assertEquals("agent", item.resolveDynamicValue(call("forAgent")));
    assertInstanceOf(
        A2uiRpcError.class,
        assertThrows(RuntimeException.class, () -> item.resolveDynamicValue(call("forRenderer"))));
    assertEquals("beep", agent.withUserActivation().resolveDynamicValue(call("beep")));
  }
}
