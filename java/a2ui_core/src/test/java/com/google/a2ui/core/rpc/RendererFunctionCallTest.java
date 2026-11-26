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

package com.google.a2ui.core.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.catalog.FunctionBody;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiDataError;
import com.google.a2ui.core.exceptions.A2uiRpcError;
import com.google.a2ui.core.exceptions.RpcErrorCode;
import com.google.a2ui.core.expressions.ExpressionParser;
import com.google.a2ui.core.resolution.DataContext;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The calls from the agent to a function of the renderer. */
class RendererFunctionCallTest {
  private static final String CATALOG_ID = "urn:test:rpc";
  private static final String LATE_ID = "urn:test:late";
  private static final String AGENT = "rendererOrAgent";

  /** The names of the functions that ran, in the sequence of the calls. */
  private final List<String> calls = new ArrayList<>();

  private final List<Map<String, Object>> sent = new ArrayList<>();
  private boolean userActivated;

  private final Catalog<ComponentApi, FunctionApi> catalog =
      new Catalog<>(
          CATALOG_ID,
          List.of(),
          List.of(
              function(
                  "double",
                  withArgs(AGENT, Map.of("value", Map.of("type", "number")), List.of("value")),
                  (args, context) -> ((Number) args.get("value")).longValue() * 2),
              function("echo", Map.of("allowedCallers", AGENT), (args, context) -> args),
              function("log", Map.of("allowedCallers", "agentOnly"), (args, context) -> null),
              function("private", Map.of(), (args, context) -> "secret"),
              function(
                  "beep",
                  Map.of("allowedCallers", AGENT, "requiresUserActivation", true),
                  (args, context) -> "beep"),
              function(
                  "render",
                  Map.of("allowedCallers", AGENT),
                  (args, context) -> {
                    StringBuilder text = new StringBuilder();
                    context
                        .evaluateTemplate(
                            ExpressionParser.parseTemplate(Coercion.toText(args.get("value"))))
                        .forEach(value -> text.append(Coercion.toText(value)));
                    return text.toString();
                  }),
              function(
                  "crash",
                  Map.of("allowedCallers", AGENT),
                  (args, context) -> {
                    throw new IllegalStateException("The password is 1234.");
                  }),
              function(
                  "reject",
                  Map.of("allowedCallers", AGENT),
                  (args, context) -> {
                    throw new A2uiDataError("The value is not a list.");
                  }),
              function(
                  "busy",
                  Map.of("allowedCallers", AGENT),
                  (args, context) -> {
                    throw new A2uiRpcError("Try again later.", RpcErrorCode.CANCELLED);
                  }),
              function(
                  "thread",
                  Map.of("allowedCallers", AGENT),
                  (args, context) -> Thread.currentThread()),
              new FunctionApi("described", Map.of("allowedCallers", AGENT))),
          null,
          ProtocolVersion.V1_0);

  /** A catalog that the handler has and the surface does not have. */
  private final Catalog<ComponentApi, FunctionApi> late =
      new Catalog<>(
          LATE_ID,
          List.of(),
          List.of(
              function("echo", Map.of("allowedCallers", AGENT), (args, context) -> args),
              function("name", Map.of("allowedCallers", AGENT), (args, context) -> "late")),
          null,
          ProtocolVersion.V1_0);

  private final Catalog<ComponentApi, FunctionApi> old =
      new Catalog<>(
          "urn:test:old",
          List.of(),
          List.of(function("echo", Map.of("allowedCallers", AGENT), (args, context) -> args)),
          null,
          ProtocolVersion.V0_9);

  private final SurfaceModel<ComponentApi> surface =
      new SurfaceModel<>(
          "s", ProtocolVersion.V1_0, catalog, Map.of(CATALOG_ID, catalog), false, null);

  private final RpcHandler rpc = handler();

  private RpcHandler handler() {
    Map<String, Catalog<ComponentApi, FunctionApi>> catalogs = new LinkedHashMap<>();
    catalogs.put(CATALOG_ID, catalog);
    catalogs.put(LATE_ID, late);
    catalogs.put(old.id(), old);
    RpcHandler handler = new RpcHandler(catalogs, sent::add);
    handler.setUserActivation(() -> userActivated);
    return handler;
  }

  private FunctionImplementation function(
      String name, Map<String, Object> schema, FunctionBody body) {
    return new FunctionImplementation(
        name,
        schema,
        (args, context) -> {
          calls.add(name);
          return body.execute(args, context);
        });
  }

  /** Makes the definition of a function that describes its arguments. */
  private static Map<String, Object> withArgs(
      String callers, Map<String, Object> properties, List<String> required) {
    Map<String, Object> args =
        Map.of("type", "object", "properties", properties, "required", required);
    return Map.of("allowedCallers", callers, "properties", Map.of("args", args));
  }

  private static Map<String, Object> call(String catalogId, String name, Map<String, Object> args) {
    return Map.of("@call", name, "catalogId", catalogId, "args", args);
  }

  private static Map<String, Object> call(String name, Map<String, Object> args) {
    return call(CATALOG_ID, name, args);
  }

  private static Map<String, Object> message(Map<String, Object> callFunction) {
    return Map.of(
        "version",
        "1.0",
        "callRendererFunction",
        Map.of("functionCallId", "c1", "surfaceId", "s", "callFunction", callFunction));
  }

  /** Gives a call to the handler and gets the body of the response. */
  @SuppressWarnings("unchecked")
  private Map<String, Object> respond(Map<String, Object> callFunction) {
    Map<String, Object> response =
        rpc.handleCallRendererFunction(message(callFunction), new DataContext(surface));
    return (Map<String, Object>) response.get("rendererFunctionResponse");
  }

  /** Gives a call to the handler and gets the error of the response. */
  @SuppressWarnings("unchecked")
  private Map<String, Object> error(Map<String, Object> callFunction) {
    Map<String, Object> body = respond(callFunction);
    assertFalse(body.containsKey("value"), () -> "The response has a value: " + body);
    return (Map<String, Object>) body.get("error");
  }

  private static Map<String, Object> refusal(String message) {
    return Map.of("code", "INVALID_FUNCTION_CALL", "message", message);
  }

  @Test
  void theHandlerRunsTheFunctionAndSendsItsValue() {
    Map<String, Object> response =
        rpc.handleCallRendererFunction(
            message(call("double", Map.of("value", 21L))), new DataContext(surface));

    assertEquals(
        Map.of(
            "version",
            "1.0",
            "rendererFunctionResponse",
            Map.of("functionCallId", "c1", "value", 42L)),
        response);
    assertEquals(List.of("version", "rendererFunctionResponse"), List.copyOf(response.keySet()));
    assertEquals(List.of(response), sent);
    assertSame(response, sent.getFirst());
    assertThrows(UnsupportedOperationException.class, () -> response.put("more", 1));
  }

  @Test
  void aFunctionThatReturnsNothingGetsAResponseWithANullValue() {
    Map<String, Object> body = respond(call("log", Map.of("line", "started")));

    assertTrue(body.containsKey("value"));
    assertNull(body.get("value"));
    assertFalse(body.containsKey("error"));
    assertEquals(List.of("log"), calls);
  }

  @Test
  void theArgumentsReadTheDataOfTheSurface() {
    surface.dataModel().set("/", Map.of("count", 4L, "label", "items"));

    Map<String, Object> body =
        respond(
            call(
                "echo",
                Map.of(
                    "twice",
                    call("double", Map.of("value", Map.of("@path", "/count"))),
                    "label",
                    Map.of("@path", "/label"))));

    assertEquals(Map.of("twice", 8L, "label", "items"), body.get("value"));
  }

  @Test
  void theHandlerRefusesACallThatItCannotFind() {
    assertEquals(refusal("Function not found: missing"), error(call("missing", Map.of())));
    assertEquals(
        refusal("Catalog not found: urn:other"), error(call("urn:other", "echo", Map.of())));
    assertEquals(
        refusal(
            "Catalog 'urn:test:old' specification version (v0.9) does not match message protocol"
                + " version (1.0)."),
        error(call("urn:test:old", "echo", Map.of())));
    assertEquals(
        refusal(
            "Function 'described' of catalog '"
                + CATALOG_ID
                + "' has no implementation in this renderer."),
        error(call("described", Map.of())));
    assertEquals(List.of(), calls);
    assertEquals(4, sent.size());
  }

  @Test
  void theHandlerRefusesACallForASurfaceThatDoesNotExist() {
    Map<String, Object> message = message(call("echo", Map.of()));
    SurfaceModel<ComponentApi> other =
        new SurfaceModel<>(
            "t", ProtocolVersion.V1_0, catalog, Map.of(CATALOG_ID, catalog), false, null);

    Object withNoContext =
        rpc.handleCallRendererFunction(message, null).get("rendererFunctionResponse");
    Object withOtherSurface =
        rpc.handleCallRendererFunction(message, new DataContext(other))
            .get("rendererFunctionResponse");

    Map<String, Object> expected =
        Map.of("functionCallId", "c1", "error", refusal("Surface not found: s"));
    assertEquals(expected, withNoContext);
    assertEquals(expected, withOtherSurface);
    assertEquals(List.of(), calls);
  }

  @Test
  void theAgentCallsOnlyAFunctionThatPermitsAgentCallers() {
    assertEquals(
        refusal("Function 'private' cannot be called by agent (allowedCallers is rendererOnly)."),
        error(call("private", Map.of())));
    assertEquals(List.of(), calls);
  }

  @Test
  void aFunctionThatRequiresUserActivationRunsOnlyWhenTheApplicationTellsThatItIsPresent() {
    assertEquals(
        refusal("Function 'beep' requires user activation context to execute."),
        error(call("beep", Map.of())));

    userActivated = true;

    assertEquals("beep", respond(call("beep", Map.of())).get("value"));
    assertEquals("beep", respond(call("render", Map.of("value", "${beep()}"))).get("value"));
  }

  @Test
  void aRefusedCallInAnArgumentStopsTheCallBeforeAnArgumentRuns() {
    Map<String, Object> arguments = new LinkedHashMap<>();
    arguments.put("first", call("log", Map.of()));
    arguments.put("second", List.of(Map.of("deep", call("private", Map.of()))));

    assertEquals(
        refusal("Function 'private' cannot be called by agent (allowedCallers is rendererOnly)."),
        error(call("echo", arguments)));
    assertEquals(
        refusal("Function 'beep' requires user activation context to execute."),
        error(
            call("echo", Map.of("value", call("echo", Map.of("inner", call("beep", Map.of())))))));
    assertEquals(List.of(), calls);
  }

  @Test
  void aCallInAnArgumentCanNameAFunctionOfTheAgentOnly() {
    Map<String, Object> body =
        respond(call("echo", Map.of("logged", call("log", Map.of()), "label", "x")));

    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("logged", null);
    expected.put("label", "x");
    assertEquals(expected, body.get("value"));
    assertTrue(calls.contains("log"));
  }

  @Test
  void aRefusedCallInATemplateIsARefusalOfTheCall() {
    assertEquals(
        refusal("Function 'private' cannot be called by agent (allowedCallers is rendererOnly)."),
        error(call("render", Map.of("value", "${missing()}${private()}"))));
    // The function that evaluates the template started. No part of the template ran.
    assertEquals(List.of("render"), calls);
  }

  @Test
  void argumentsThatDoNotAgreeWithTheSchemaAreRefused() {
    Map<String, Object> wrongType = error(call("double", Map.of("value", "many")));
    Map<String, Object> absent = error(call("double", Map.of()));
    Map<String, Object> resolved =
        error(call("double", Map.of("value", call("render", Map.of("value", "many")))));

    for (Map<String, Object> error : List.of(wrongType, absent, resolved)) {
      assertEquals("INVALID_FUNCTION_CALL", error.get("code"));
      assertTrue(
          ((String) error.get("message")).startsWith("Invalid arguments for function 'double': "),
          () -> "The message was " + error.get("message"));
    }
    assertFalse(calls.contains("double"));
  }

  @Test
  void theFailureOfTheFunctionIsAnExecutionError() {
    assertEquals(
        Map.of(
            "code", "EXECUTION_ERROR", "message", "An error occurred during function execution."),
        error(call("crash", Map.of())));
    assertEquals(
        Map.of("code", "EXECUTION_ERROR", "message", "The value is not a list."),
        error(call("reject", Map.of())));
    assertEquals(
        Map.of("code", "CANCELLED", "message", "Try again later."), error(call("busy", Map.of())));
    assertEquals(
        Map.of(
            "code",
            "EXECUTION_ERROR",
            "message",
            "The result of function 'thread' is not JSON: Not a JSON value: java.lang.Thread"),
        error(call("thread", Map.of())));
  }

  @Test
  void theFailureOfAnArgumentIsAnExecutionError() {
    assertEquals(
        Map.of(
            "code",
            "EXECUTION_ERROR",
            "message",
            "Unknown function 'missing' in catalog '" + CATALOG_ID + "'."),
        error(call("echo", Map.of("value", call("missing", Map.of())))));
    assertEquals(
        Map.of(
            "code",
            "EXECUTION_ERROR",
            "message",
            "The @index function can only be evaluated inside a collection template iteration"
                + " scope."),
        error(call("echo", Map.of("position", Map.of("@call", "@index")))));
    assertEquals(
        Map.of(
            "code", "EXECUTION_ERROR", "message", "Function 'crash' failed: The password is 1234."),
        error(call("echo", Map.of("value", call("crash", Map.of())))));
    assertFalse(calls.contains("echo"));
  }

  @Test
  void argumentsThatNestTooDeepAreAnExecutionError() {
    Object nested = "leaf";
    for (int level = 0; level < 60; level++) {
      nested = List.of(nested);
    }

    Map<String, Object> error = error(call("echo", Map.of("value", nested)));

    assertEquals("EXECUTION_ERROR", error.get("code"));
    assertEquals(
        "The arguments of the call nest values deeper than 50 levels.", error.get("message"));
    assertEquals(List.of(), calls);
  }

  @Test
  void theAgentCanCallAFunctionOfACatalogThatTheSurfaceDoesNotHave() {
    Map<String, Object> body =
        respond(call(LATE_ID, "echo", Map.of("name", call(LATE_ID, "name", Map.of()))));

    assertEquals(Map.of("name", "late"), body.get("value"));
  }

  @Test
  void withoutAListenerTheFunctionRunsAndNothingIsSent() {
    RpcHandler silent = new RpcHandler(Map.of(CATALOG_ID, catalog), null);

    Map<String, Object> response =
        silent.handleCallRendererFunction(
            message(call("double", Map.of("value", 2L))), new DataContext(surface));

    assertEquals(
        Map.of("functionCallId", "c1", "value", 4L), response.get("rendererFunctionResponse"));
    assertEquals(List.of("double"), calls);
  }

  @Test
  void aDisposedHandlerRunsNoFunctionAndSendsNothing() {
    rpc.dispose();

    Map<String, Object> error = error(call("double", Map.of("value", 2L)));

    assertEquals(Map.of("code", "DISPOSED", "message", "The RPC handler is disposed."), error);
    assertEquals(List.of(), calls);
    assertEquals(List.of(), sent);
  }

  @Test
  void aMessageWithNoFunctionCallIdIsNotACallRendererFunctionMessage() {
    DataContext context = new DataContext(surface);

    assertThrows(
        IllegalArgumentException.class,
        () -> rpc.handleCallRendererFunction(Map.of("version", "1.0"), context));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            rpc.handleCallRendererFunction(
                Map.of("version", "1.0", "callRendererFunction", Map.of()), context));
    assertEquals(
        refusal(
            "The callFunction of a callRendererFunction message must have '@call' and 'catalogId'."),
        ((Map<?, ?>)
                rpc.handleCallRendererFunction(
                        Map.of(
                            "version",
                            "1.0",
                            "callRendererFunction",
                            Map.of(
                                "functionCallId", "c1", "callFunction", Map.of("@call", "echo"))),
                        context)
                    .get("rendererFunctionResponse"))
            .get("error"));
  }
}
