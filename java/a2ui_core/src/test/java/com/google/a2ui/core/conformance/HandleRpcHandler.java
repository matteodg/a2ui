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

import static com.google.a2ui.core.conformance.JsonValues.asObject;
import static com.google.a2ui.core.conformance.JsonValues.asString;
import static com.google.a2ui.core.conformance.JsonValues.assertJsonEquals;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionBody;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiRpcError;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.rpc.CallOptions;
import com.google.a2ui.core.rpc.ManualScheduler;
import com.google.a2ui.core.validation.ValidationConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The action {@code handle_rpc}: a function call of the agent and the response that the agent gets,
 * or a function call of the renderer and its result.
 *
 * <p>Each case gets a message processor with the catalogs that the case describes. The functions of
 * {@code functionMetadata} get the behavior that their name tells. The timeouts use a scheduler
 * that the handler controls. Thus no case waits.
 *
 * <p>The registry skips the cases that start a call from a binding or from an action ({@code
 * uiBindingScenario}, {@code uiActionFallbackScenario}).
 */
final class HandleRpcHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys("action", "args", "catalog", "expect", "expectError", "protocolVersion");

  private static final Set<String> ARG_KEYS =
      Set.of(
          "afterCall",
          "catalogId",
          "catalogVersion",
          "dataModel",
          "functionMetadata",
          "inboundResponse",
          "lateInboundResponse",
          "message",
          "outboundCall",
          "outboundListener",
          "secondOutboundCall",
          "surfaceId",
          "userActivationPresent");

  private static final Set<String> EXPECT_KEYS =
      Set.of(
          "correlatedCallId",
          "error",
          "inFlightError",
          "lateResponseIgnored",
          "response",
          "result");

  private static final Set<String> METADATA_KEYS =
      Set.of("allowedCallers", "catalogId", "parameters", "requiresUserActivation");

  private static final Set<String> OUTBOUND_CALL_KEYS =
      Set.of("callFunction", "functionCallId", "surfaceId", "timeoutMs");

  private static final Set<String> RESPONSE_KEYS = Set.of("rendererFunctionResponse", "version");

  private static final Set<String> RESPONSE_BODY_KEYS = Set.of("error", "functionCallId", "value");

  private static final Set<String> RESPONSE_ERROR_KEYS = Set.of("code", "message");

  private static final Set<String> ERROR_KEYS = Set.of("code", "functionCallId", "message");

  private static final Set<String> CODE_KEYS = Set.of("code");

  private static final String DEFAULT_CATALOG_ID = "basic";

  /** The code of {@code formatString}. The function {@code renderTemplate} of the cases uses it. */
  private static final FunctionImplementation FORMAT_STRING =
      BasicCatalog.withFunctions(Locale.US, uri -> {}).functions().get("formatString");

  /** The state of one case. */
  private static final class Run {
    final String location;
    final List<Map<String, Object>> sent = new ArrayList<>();
    final List<A2uiError> surfaceErrors = new ArrayList<>();
    final ManualScheduler scheduler = new ManualScheduler();
    MessageProcessor<ComponentApi> processor;

    Run(String location) {
      this.location = location;
    }
  }

  /** Gets the behavior of a function of {@code functionMetadata} from its name. */
  private static FunctionBody behavior(String name) {
    return switch (name) {
      case "playMedia" -> (args, context) -> Map.of("playing", true, "timestamp", 0L);
      case "openExternalUrl" -> (args, context) -> Map.of("opened", true);
      case "failingFunction" ->
          (args, context) -> {
            throw new IllegalStateException("An error occurred during function execution.");
          };
      case "calculateTax" -> (args, context) -> Coercion.toNumber(args.get("amount")) * 0.1;
      case "echoArgs" -> (args, context) -> args;
      case "renderTemplate" -> FORMAT_STRING::execute;
      case "tagValue" -> (args, context) -> "tagged:" + Coercion.toText(args.get("value"));
      case "secretRendererOnly" -> (args, context) -> "SECRET";
      case "agentSecret" -> (args, context) -> "AGENT_OK";
      case "internalStateReset", "syncState" -> (args, context) -> null;
      default -> throw new AssertionError("The handler does not know the function '" + name + "'");
    };
  }

  /** Makes a function from its entry in {@code functionMetadata}. */
  private static FunctionImplementation function(
      String name, Map<String, Object> metadata, String where) {
    KnownKeys.assertKnownKeys(metadata, METADATA_KEYS, where);
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("returnType", "any");
    schema.put(
        "allowedCallers",
        metadata.get("allowedCallers") == null
            ? "rendererOrAgent"
            : metadata.get("allowedCallers"));
    schema.put(
        "requiresUserActivation", Boolean.TRUE.equals(metadata.get("requiresUserActivation")));
    // A function with no `parameters` describes no arguments. Thus it accepts all arguments.
    if (metadata.get("parameters") != null) {
      schema.put("properties", Map.of("args", metadata.get("parameters")));
    }
    return new FunctionImplementation(name, schema, behavior(name));
  }

  @Override
  public void run(ConformanceCase testCase) throws Exception {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    Map<String, Object> args = asObject(fields.get("args"), "args in " + location);
    KnownKeys.assertKnownKeys(args, ARG_KEYS, "args in " + location);
    Map<String, Object> expected = optionalObject(fields.get("expect"), "expect in " + location);
    if (expected != null) {
      KnownKeys.assertKnownKeys(expected, EXPECT_KEYS, "expect in " + location);
    }
    Object expectError = fields.get("expectError");
    Map<String, Object> message = optionalObject(args.get("message"), "message in " + location);
    Map<String, Object> outboundCall =
        optionalObject(args.get("outboundCall"), "outboundCall in " + location);
    if (message == null && outboundCall == null) {
      throw new AssertionError(location + " has no message and no outboundCall");
    }

    Run run = new Run(location);
    run.processor = processor(args, message, outboundCall, expected, expectError, run);
    if (args.get("surfaceId") != null) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("surfaceId", asString(args.get("surfaceId"), "surfaceId in " + location));
      body.put("catalogId", run.processor.catalogs().keySet().iterator().next());
      if (args.get("dataModel") != null) {
        body.put("dataModel", args.get("dataModel"));
      }
      run.processor.processMessages(Map.of("version", "1.0", "createSurface", body));
    } else if (args.containsKey("dataModel")) {
      throw new AssertionError(location + " has a dataModel and no surfaceId");
    }

    if (message != null) {
      inbound(message, expected, expectError, run);
    }
    if (outboundCall != null) {
      outbound(args, outboundCall, expected, expectError, run);
    }
    run.processor.dispose();
  }

  private static Map<String, Object> optionalObject(Object value, String where) {
    return value == null ? null : asObject(value, where);
  }

  /** Makes the processor of a case, with the catalogs and the functions that the case describes. */
  private static MessageProcessor<ComponentApi> processor(
      Map<String, Object> args,
      Map<String, Object> message,
      Map<String, Object> outboundCall,
      Map<String, Object> expected,
      Object expectError,
      Run run) {
    String location = run.location;
    String primaryId = catalogId(args, message, outboundCall, expected, expectError);
    Optional<ProtocolVersion> version =
        ProtocolVersion.tryParse(
            args.get("catalogVersion") == null
                ? "1.0"
                : asString(args.get("catalogVersion"), "catalogVersion in " + location));
    if (version.isEmpty()) {
      throw new AssertionError(location + ": unknown catalogVersion " + args.get("catalogVersion"));
    }

    // The first catalog is the catalog of the case. A function can name a different catalog.
    Map<String, List<FunctionImplementation>> functions = new LinkedHashMap<>();
    functions.put(primaryId, new ArrayList<>());
    Map<String, Object> metadata =
        args.get("functionMetadata") == null
            ? Map.of()
            : asObject(args.get("functionMetadata"), "functionMetadata in " + location);
    metadata.forEach((name, raw) -> {
      String where = "functionMetadata." + name + " in " + location;
      Map<String, Object> entry = asObject(raw, where);
      String owner =
          entry.get("catalogId") == null ? primaryId : asString(entry.get("catalogId"), where);
      functions.computeIfAbsent(owner, id -> new ArrayList<>()).add(function(name, entry, where));
    });
    List<Catalog<ComponentApi, FunctionImplementation>> catalogs = new ArrayList<>();
    functions.forEach(
        (id, list) -> catalogs.add(new Catalog<>(id, List.of(), list, null, version.get())));

    MessageProcessor<ComponentApi> processor;
    if (Boolean.FALSE.equals(args.get("outboundListener"))) {
      processor = new MessageProcessor<>(catalogs);
    } else if (args.get("outboundListener") == null) {
      processor = new MessageProcessor<>(catalogs, ValidationConfig.STRICT, run.sent::add);
    } else {
      throw new AssertionError(
          location
              + ": the handler does not know outboundListener "
              + args.get("outboundListener"));
    }
    Object activation = args.get("userActivationPresent");
    if (activation != null && !(activation instanceof Boolean)) {
      throw new AssertionError(location + ": userActivationPresent must be a boolean");
    }
    processor.rpc().setUserActivation(() -> Boolean.TRUE.equals(activation));
    processor.rpc().setScheduler(run.scheduler);
    processor
        .model()
        .onSurfaceCreated()
        .subscribe(surface -> surface.onError().subscribe(run.surfaceErrors::add));
    return processor;
  }

  /**
   * Gets the ID of the catalog of a case: {@code args.catalogId}, or the catalog that the call
   * names. A case that expects that the catalog does not exist does not get that catalog.
   */
  private static String catalogId(
      Map<String, Object> args,
      Map<String, Object> message,
      Map<String, Object> outboundCall,
      Map<String, Object> expected,
      Object expectError) {
    if (args.get("catalogId") instanceof String id) {
      return id;
    }
    if (message != null
        && message.get("callRendererFunction") instanceof Map<?, ?> body
        && body.get("callFunction") instanceof Map<?, ?> call
        && call.get("catalogId") instanceof String id
        && !String.valueOf(expected).contains("Catalog not found")
        && !String.valueOf(expectError).contains("Catalog not found")) {
      return id;
    }
    if (outboundCall != null
        && outboundCall.get("callFunction") instanceof Map<?, ?> call
        && call.get("catalogId") instanceof String id) {
      return id;
    }
    return DEFAULT_CATALOG_ID;
  }

  // The agent sends a message.

  private static void inbound(
      Map<String, Object> message, Map<String, Object> expected, Object expectError, Run run)
      throws Exception {
    String location = run.location;
    Map<String, Object> errorSpec =
        expected == null ? null : optionalObject(expected.get("error"), "expect.error");
    if (errorSpec != null || expectError != null) {
      rejected(message, errorSpec, expectError, run);
      return;
    }
    if (expected == null || !expected.containsKey("response")) {
      throw new AssertionError(location + " expects no response and no error for its message");
    }
    run.processor.processMessages(message);
    if (expected.get("response") == null) {
      // The processor ignored the message.
      assertQuiet(run, 0, "the message");
      return;
    }
    if (run.sent.size() != 1) {
      throw new AssertionError(location + ": expected one sent message, but got " + run.sent);
    }
    Map<String, Object> wanted =
        asObject(expected.get("response"), "expect.response in " + location);
    KnownKeys.assertKnownKeys(wanted, RESPONSE_KEYS, "expect.response in " + location);
    Map<String, Object> actual = run.sent.getFirst();
    assertJsonEquals(wanted.get("version"), actual.get("version"), "version in " + location);
    Map<String, Object> wantedBody =
        asObject(wanted.get("rendererFunctionResponse"), "the expected response of " + location);
    KnownKeys.assertKnownKeys(
        wantedBody, RESPONSE_BODY_KEYS, "the expected response of " + location);
    Map<String, Object> body =
        asObject(actual.get("rendererFunctionResponse"), "the sent message of " + location);
    assertJsonEquals(
        wantedBody.get("functionCallId"),
        body.get("functionCallId"),
        "functionCallId in " + location);
    // A response has a value or an error, not the two.
    if (body.containsKey("value") == body.containsKey("error")) {
      throw new AssertionError(location + ": the response must have a value or an error: " + body);
    }
    if (wantedBody.containsKey("value")) {
      if (!body.containsKey("value")) {
        throw new AssertionError(location + ": expected a value, but got " + body);
      }
      assertJsonEquals(wantedBody.get("value"), body.get("value"), "value in " + location);
    }
    if (wantedBody.get("error") != null) {
      Map<String, Object> wantedError = asObject(wantedBody.get("error"), "error in " + location);
      KnownKeys.assertKnownKeys(
          wantedError, RESPONSE_ERROR_KEYS, "the expected error of " + location);
      if (!body.containsKey("error")) {
        throw new AssertionError(location + ": expected an error, but got " + body);
      }
      Map<String, Object> error = asObject(body.get("error"), "the sent error of " + location);
      assertJsonEquals(wantedError.get("code"), error.get("code"), "error code in " + location);
      assertContains(error.get("message"), wantedError.get("message"), location);
    }
  }

  /** Runs a message that the processor must reject, and examines the error for the agent. */
  private static void rejected(
      Map<String, Object> message, Map<String, Object> errorSpec, Object expectError, Run run)
      throws Exception {
    String location = run.location;
    Object expectation =
        expectError != null
            ? expectError
            : Map.of("message", asString(errorSpec.get("message"), "expect.error.message"));
    A2uiError[] thrown = new A2uiError[1];
    ExpectedErrors.assertRaises(
        expectation,
        location,
        Map.of(),
        () -> run.sent.isEmpty() ? null : run.sent.getLast(),
        () -> {
          try {
            run.processor.processMessages(message);
          } catch (A2uiError e) {
            thrown[0] = e;
            throw e;
          }
        });
    // A rejected message gets an `error` message and no response.
    if (run.sent.size() != 1 || !run.sent.getFirst().containsKey("error")) {
      throw new AssertionError(location + ": expected one error message, but got " + run.sent);
    }
    if (errorSpec == null) {
      return;
    }
    KnownKeys.assertKnownKeys(errorSpec, ERROR_KEYS, "expect.error in " + location);
    Map<String, Object> emitted = asObject(run.sent.getFirst().get("error"), "the sent error");
    assertContains(thrown[0].getMessage(), errorSpec.get("message"), location);
    assertContains(emitted.get("message"), errorSpec.get("message"), location);
    for (String key : List.of("code", "functionCallId")) {
      if (errorSpec.get(key) != null) {
        assertJsonEquals(errorSpec.get(key), emitted.get(key), "error " + key + " in " + location);
      }
    }
  }

  private static void assertContains(Object actual, Object part, String location) {
    if (part != null
        && !String.valueOf(actual).contains(asString(part, "message in " + location))) {
      throw new AssertionError(
          location + ": the message '" + actual + "' does not contain '" + part + "'");
    }
  }

  /** Fails if the processor sent a message, reported a surface error or has a pending call. */
  private static void assertQuiet(Run run, int sentBefore, String cause) {
    if (run.sent.size() != sentBefore) {
      throw new AssertionError(run.location + ": " + cause + " caused the message " + run.sent);
    }
    if (!run.surfaceErrors.isEmpty()) {
      throw new AssertionError(run.location + ": a surface reported " + run.surfaceErrors);
    }
    if (run.processor.rpc().pendingCallCount() != 0) {
      throw new AssertionError(run.location + ": a call is pending");
    }
  }

  // The renderer calls the agent.

  private static CompletableFuture<Object> call(Map<String, Object> outbound, Run run) {
    String where = "outboundCall in " + run.location;
    KnownKeys.assertKnownKeys(outbound, OUTBOUND_CALL_KEYS, where);
    Optional<Duration> timeout =
        outbound.get("timeoutMs") instanceof Number millis
            ? Optional.of(Duration.ofMillis(millis.longValue()))
            : Optional.empty();
    Optional<String> functionCallId =
        Optional.ofNullable(outbound.get("functionCallId")).map(id -> asString(id, where));
    int scheduled = run.scheduler.scheduled().size();
    CompletableFuture<Object> future =
        run.processor.callAgentFunction(
            asString(outbound.get("surfaceId"), where),
            asObject(outbound.get("callFunction"), where),
            new CallOptions(timeout, functionCallId));
    // A call that waits has a timeout: the time of the case, or the default of 30 seconds.
    if (!future.isDone()) {
      Duration wanted = timeout.orElse(Duration.ofSeconds(30));
      if (run.scheduler.scheduled().size() != scheduled + 1
          || !run.scheduler.last().delay().equals(wanted)) {
        throw new AssertionError(run.location + ": the call has no timeout of " + wanted);
      }
    }
    return future;
  }

  private static void outbound(
      Map<String, Object> args,
      Map<String, Object> outbound,
      Map<String, Object> expected,
      Object expectError,
      Run run) {
    String location = run.location;
    Object afterCall = args.get("afterCall");
    if (args.get("inboundResponse") != null) {
      if (expectError != null || afterCall != null || args.get("secondOutboundCall") != null) {
        throw new AssertionError(location + ": the handler does not know this combination");
      }
      answered(args, outbound, expected, run);
      return;
    }
    Map<String, Object> wantedError = asObject(expectError, "expectError in " + location);
    KnownKeys.assertKnownKeys(wantedError, CODE_KEYS, "expectError in " + location);
    Object code = wantedError.get("code");

    CompletableFuture<Object> settled;
    if (args.get("secondOutboundCall") != null) {
      CompletableFuture<Object> first = call(outbound, run);
      settled =
          call(asObject(args.get("secondOutboundCall"), "secondOutboundCall in " + location), run);
      assertFails(settled, code, location);
      if (first.isDone() || run.sent.size() != 1) {
        throw new AssertionError(location + ": the refused call changed the first call");
      }
      return;
    }
    if ("cancel".equals(afterCall)) {
      settled = call(outbound, run);
      run.processor.rpc().cancelSurfaceCalls(asString(outbound.get("surfaceId"), "surfaceId"));
      assertFails(settled, code, location);
    } else if ("dispose".equals(afterCall)) {
      CompletableFuture<Object> inFlight = call(outbound, run);
      run.processor.rpc().dispose();
      Map<String, Object> inFlightError =
          asObject(expected == null ? null : expected.get("inFlightError"), "expect.inFlightError");
      KnownKeys.assertKnownKeys(inFlightError, CODE_KEYS, "expect.inFlightError in " + location);
      assertFails(inFlight, inFlightError.get("code"), location);
      settled = call(outbound, run);
      assertFails(settled, code, location);
    } else if (afterCall != null) {
      throw new AssertionError(location + ": the handler does not know afterCall " + afterCall);
    } else {
      settled = call(outbound, run);
      if (!settled.isDone()) {
        // The call waits. The time of its timeout ends now.
        run.scheduler.last().fire();
      }
      assertFails(settled, code, location);
    }

    Object late = args.get("lateInboundResponse");
    boolean ignored = expected != null && Boolean.TRUE.equals(expected.get("lateResponseIgnored"));
    if ((late != null) != ignored) {
      throw new AssertionError(location + ": lateInboundResponse needs lateResponseIgnored: true");
    }
    if (late != null) {
      int before = run.sent.size();
      run.processor.processMessages(late);
      assertQuiet(run, before, "the late response");
      // The result of the call that ended before did not change.
      assertFails(settled, code, location);
    }
  }

  /** Runs a call that the agent answers, and examines the message and the result. */
  private static void answered(
      Map<String, Object> args,
      Map<String, Object> outbound,
      Map<String, Object> expected,
      Run run) {
    String location = run.location;
    CompletableFuture<Object> future = call(outbound, run);
    if (run.sent.size() != 1) {
      throw new AssertionError(location + ": expected one sent message, but got " + run.sent);
    }
    assertJsonEquals("1.0", run.sent.getFirst().get("version"), "version in " + location);
    Map<String, Object> sentCall =
        asObject(run.sent.getFirst().get("callAgentFunction"), "the sent message of " + location);
    for (String key : List.of("surfaceId", "functionCallId", "callFunction")) {
      assertJsonEquals(outbound.get(key), sentCall.get(key), "sent " + key + " in " + location);
    }
    if (expected.get("correlatedCallId") != null) {
      assertJsonEquals(
          expected.get("correlatedCallId"),
          sentCall.get("functionCallId"),
          "correlatedCallId in " + location);
    }
    run.processor.processMessages(args.get("inboundResponse"));
    if (!future.isDone() || future.isCompletedExceptionally()) {
      throw new AssertionError(location + ": the response did not complete the call");
    }
    assertJsonEquals(expected.get("result"), future.join(), "result in " + location);
    assertQuiet(run, 1, "the response");
  }

  /** Fails if a call did not end with an RPC error of the given code. */
  private static void assertFails(CompletableFuture<Object> future, Object code, String location) {
    if (code == null) {
      throw new AssertionError(location + ": the expectation gives no error code");
    }
    if (!future.isDone()) {
      throw new AssertionError(location + ": the call did not end");
    }
    try {
      future.join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof A2uiRpcError error) {
        assertJsonEquals(code, error.code().name(), "RPC error code in " + location);
        return;
      }
      throw new AssertionError(location + ": the call failed with " + e.getCause(), e);
    }
    throw new AssertionError(location + ": expected the call to fail with " + code);
  }
}
