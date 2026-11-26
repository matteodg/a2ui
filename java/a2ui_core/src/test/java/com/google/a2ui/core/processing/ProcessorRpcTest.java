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

package com.google.a2ui.core.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiRpcError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.exceptions.RpcErrorCode;
import com.google.a2ui.core.rpc.CallOptions;
import com.google.a2ui.core.rpc.ManualScheduler;
import com.google.a2ui.core.validation.ValidationConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

/** The processor gives the function-call messages of the agent to its RPC handler. */
class ProcessorRpcTest {
  private static final String CATALOG_ID = "urn:test:rpc";
  private static final Map<String, Object> CALL =
      Map.of("@call", "lookup", "catalogId", CATALOG_ID, "args", Map.of("id", 7L));

  private final List<Map<String, Object>> sent = new ArrayList<>();
  private int runs;

  private final Catalog<ComponentApi, FunctionImplementation> catalog = catalog(CATALOG_ID);

  private final MessageProcessor<ComponentApi> processor = processor(sent::add);

  private Catalog<ComponentApi, FunctionImplementation> catalog(String id) {
    Map<String, Object> schema = Map.of("allowedCallers", "rendererOrAgent");
    return new Catalog<>(
        id,
        List.of(),
        List.of(
            new FunctionImplementation(
                "echo",
                schema,
                (args, context) -> {
                  runs++;
                  return args;
                }),
            new FunctionImplementation("name", schema, (args, context) -> id)),
        null,
        ProtocolVersion.V1_0);
  }

  private MessageProcessor<ComponentApi> processor(OutboundMessageListener listener) {
    MessageProcessor<ComponentApi> made =
        new MessageProcessor<>(List.of(catalog), ValidationConfig.STRICT, listener);
    made.rpc().setScheduler(new ManualScheduler());
    return made;
  }

  private static Map<String, Object> message(String action, Object... namesAndValues) {
    Map<String, Object> body = new LinkedHashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      body.put((String) namesAndValues[i], namesAndValues[i + 1]);
    }
    return Map.of("version", "1.0", action, body);
  }

  private void createSurface(String surfaceId, Map<String, Object> data) {
    processor.processMessages(
        message(
            "createSurface", "surfaceId", surfaceId, "catalogId", CATALOG_ID, "dataModel", data));
  }

  private static Map<String, Object> echo(String catalogId, Map<String, Object> args) {
    return Map.of("@call", "echo", "catalogId", catalogId, "args", args);
  }

  /** Gets the body of the last response that the processor sent. */
  @SuppressWarnings("unchecked")
  private Map<String, Object> lastResponse() {
    return (Map<String, Object>) sent.getLast().get("rendererFunctionResponse");
  }

  private static A2uiRpcError failure(CompletableFuture<Object> future) {
    ExecutionException thrown = assertThrows(ExecutionException.class, future::get);
    return assertInstanceOf(A2uiRpcError.class, thrown.getCause());
  }

  @Test
  void aFunctionCallOfTheAgentReadsTheDataOfItsSurface() {
    createSurface("s", Map.of("user", "Ada"));

    processor.processMessages(
        message(
            "callRendererFunction",
            "functionCallId",
            "c1",
            "surfaceId",
            "s",
            "callFunction",
            echo(CATALOG_ID, Map.of("user", Map.of("@path", "/user")))));

    assertEquals(Map.of("functionCallId", "c1", "value", Map.of("user", "Ada")), lastResponse());
    assertEquals(1, sent.size());
  }

  @Test
  void aCallThatNamesNoSurfaceReadsTheDataOfNoSurface() {
    createSurface("s", Map.of("user", "Ada"));
    Map<String, Object> nested = Map.of("@call", "name");

    processor.processMessages(
        message(
            "callRendererFunction",
            "functionCallId",
            "c1",
            "callFunction",
            echo(CATALOG_ID, Map.of("user", Map.of("@path", "/user"), "catalog", nested))));

    // A call in the arguments that names no catalog runs in the catalog of the agent call.
    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("user", null);
    expected.put("catalog", CATALOG_ID);
    assertEquals(expected, lastResponse().get("value"));
  }

  @Test
  void aCallForASurfaceThatDoesNotExistGetsAnErrorResponse() {
    processor.processMessages(
        message(
            "callRendererFunction",
            "functionCallId",
            "c1",
            "surfaceId",
            "missing",
            "callFunction",
            echo(CATALOG_ID, Map.of())));

    assertEquals(
        Map.of("code", "INVALID_FUNCTION_CALL", "message", "Surface not found: missing"),
        lastResponse().get("error"));
    assertEquals(0, runs);
  }

  @Test
  void aMessageWithAnIncorrectEnvelopeGetsAnErrorMessageAndNoResponse() {
    Map<String, Object> noCatalog = Map.of("@call", "echo", "args", Map.of());

    assertThrows(
        A2uiValidationError.class,
        () ->
            processor.processMessages(
                message(
                    "callRendererFunction", "functionCallId", "c1", "callFunction", noCatalog)));

    assertEquals(0, runs);
    assertEquals(1, sent.size());
    Map<?, ?> error = (Map<?, ?>) sent.getFirst().get("error");
    assertEquals("INVALID_FUNCTION_CALL", error.get("code"));
    assertEquals("c1", error.get("functionCallId"));
  }

  @Test
  void theAgentCanCallAFunctionOfACatalogThatTheProcessorGotSubsequently() {
    createSurface("s", Map.of());
    processor.addCatalog(catalog("urn:test:late"));

    processor.processMessages(
        message(
            "callRendererFunction",
            "functionCallId",
            "c1",
            "surfaceId",
            "s",
            "callFunction",
            echo("urn:test:late", Map.of("x", 1L))));

    assertEquals(Map.of("x", 1L), lastResponse().get("value"));
  }

  @Test
  void aResponseOfTheAgentCompletesTheCallOfTheProcessor() throws Exception {
    CompletableFuture<Object> future =
        processor.callAgentFunction("s", CALL, CallOptions.withFunctionCallId("a"));
    CompletableFuture<Object> other = processor.callAgentFunction("s", CALL);

    assertEquals(2, processor.rpc().pendingCallCount());
    assertTrue(sent.getFirst().containsKey("callAgentFunction"));

    processor.processMessages(
        message("agentFunctionResponse", "functionCallId", "a", "value", List.of("Ada")));

    assertEquals(List.of("Ada"), future.get());
    assertFalse(other.isDone());
  }

  @Test
  void aResponseForNoPendingCallIsIgnored() {
    processor.processMessages(
        message("agentFunctionResponse", "functionCallId", "unknown", "value", 1L));

    assertEquals(List.of(), sent);
    assertEquals(0, processor.rpc().pendingCallCount());
  }

  @Test
  void theDeletionOfASurfaceCancelsItsCallsOnly() {
    createSurface("s", Map.of());
    createSurface("t", Map.of());
    createSurface("u", Map.of());
    CompletableFuture<Object> ofS = processor.callAgentFunction("s", CALL);
    CompletableFuture<Object> ofT = processor.callAgentFunction("t", CALL);
    CompletableFuture<Object> ofU = processor.callAgentFunction("u", CALL);

    processor.processMessages(message("deleteSurface", "surfaceId", "s"));
    // The application deletes this surface from the model.
    processor.model().deleteSurface("u");

    assertEquals(RpcErrorCode.CANCELLED, failure(ofS).code());
    assertEquals(RpcErrorCode.CANCELLED, failure(ofU).code());
    assertFalse(ofT.isDone());
  }

  @Test
  void disposeCancelsTheCallsAndRefusesNewCalls() {
    CompletableFuture<Object> pending = processor.callAgentFunction("s", CALL);

    processor.dispose();

    assertEquals(RpcErrorCode.CANCELLED, failure(pending).code());
    assertEquals(RpcErrorCode.DISPOSED, failure(processor.callAgentFunction("s", CALL)).code());
    assertTrue(processor.rpc().isDisposed());
  }

  @Test
  void withoutAListenerAFunctionOfTheAgentRunsAndACallToTheAgentFails() {
    MessageProcessor<ComponentApi> silent = new MessageProcessor<>(List.of(catalog));

    silent.processMessages(
        message(
            "callRendererFunction",
            "functionCallId",
            "c1",
            "callFunction",
            echo(CATALOG_ID, Map.of())));

    assertEquals(1, runs);
    assertEquals(RpcErrorCode.NO_LISTENER, failure(silent.callAgentFunction("s", CALL)).code());
    assertSame(silent.rpc(), silent.rpc());
  }
}
