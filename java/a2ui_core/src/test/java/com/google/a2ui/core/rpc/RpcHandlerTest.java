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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.exceptions.A2uiRpcError;
import com.google.a2ui.core.exceptions.RpcErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * The calls from the renderer to the agent. The tests use a scheduler that they control. Thus no
 * test of a timeout waits.
 */
class RpcHandlerTest {
  private static final Map<String, Object> CALL =
      Map.of("@call", "lookup", "catalogId", "urn:test", "args", Map.of("id", 7L));

  private final List<Map<String, Object>> sent = new ArrayList<>();
  private final ManualScheduler scheduler = new ManualScheduler();
  private final RpcHandler rpc = handler();

  private RpcHandler handler() {
    RpcHandler handler = new RpcHandler(Map.of(), sent::add);
    handler.setScheduler(scheduler);
    return handler;
  }

  private static A2uiRpcError failure(CompletableFuture<Object> future) {
    ExecutionException thrown = assertThrows(ExecutionException.class, future::get);
    return assertInstanceOf(A2uiRpcError.class, thrown.getCause());
  }

  private static CallOptions id(String functionCallId) {
    return CallOptions.withFunctionCallId(functionCallId);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> sentCall(int index) {
    return (Map<String, Object>) sent.get(index).get("callAgentFunction");
  }

  private boolean respond(String functionCallId, String member, Object content) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("functionCallId", functionCallId);
    body.put(member, content);
    return rpc.handleAgentFunctionResponse(Map.of("version", "1.0", "agentFunctionResponse", body));
  }

  @Test
  void aCallIsSentWithANewIdAndCompletesWithItsAnswer() throws Exception {
    CompletableFuture<Object> first = rpc.callAgentFunction("s", CALL);
    CompletableFuture<Object> second = rpc.callAgentFunction("s", CALL);

    assertEquals(
        List.of("1.0", "1.0"), sent.stream().map(message -> message.get("version")).toList());
    assertEquals(
        List.of("surfaceId", "functionCallId", "callFunction"), List.copyOf(sentCall(0).keySet()));
    assertEquals("s", sentCall(0).get("surfaceId"));
    assertEquals(CALL, sentCall(0).get("callFunction"));
    assertNotEquals(sentCall(0).get("functionCallId"), sentCall(1).get("functionCallId"));
    assertEquals(2, rpc.pendingCallCount());
    assertThrows(UnsupportedOperationException.class, () -> sent.getFirst().put("more", 1));

    assertTrue(respond((String) sentCall(1).get("functionCallId"), "value", Map.of("name", "Ada")));

    assertEquals(Map.of("name", "Ada"), second.get());
    assertFalse(first.isDone());
    assertEquals(1, rpc.pendingCallCount());
  }

  @Test
  void aResponseWithANullValueCompletesTheCallWithNull() throws Exception {
    CompletableFuture<Object> future = rpc.callAgentFunction("s", CALL, id("a"));

    assertTrue(respond("a", "value", null));

    assertTrue(future.isDone());
    assertEquals(null, future.get());
  }

  @Test
  void anErrorOfTheAgentFailsTheCallWithItsCodeAndMessage() {
    CompletableFuture<Object> failed = rpc.callAgentFunction("s", CALL, id("a"));
    CompletableFuture<Object> unknownCode = rpc.callAgentFunction("s", CALL, id("b"));

    respond("a", "error", Map.of("code", "INVALID_FUNCTION_CALL", "message", "No such item."));
    respond("b", "error", Map.of("code", "SOMETHING_NEW", "message", "Odd."));

    A2uiRpcError error = failure(failed);
    assertEquals(RpcErrorCode.INVALID_FUNCTION_CALL, error.code());
    assertEquals("No such item.", error.getMessage());
    assertEquals(Optional.of("s"), error.surfaceId());
    assertEquals(Optional.of("a"), error.functionCallId());
    assertEquals(RpcErrorCode.EXECUTION_ERROR, failure(unknownCode).code());
  }

  @Test
  void aCallTimesOutWhenTheSchedulerRunsItsTask() {
    CompletableFuture<Object> future = rpc.callAgentFunction("s", CALL, id("a"));
    rpc.callAgentFunction("s", CALL, CallOptions.withTimeout(Duration.ofMillis(250)));
    rpc.setDefaultTimeout(Duration.ofSeconds(5));
    rpc.callAgentFunction("s", CALL);

    assertEquals(Duration.ofSeconds(30), RpcHandler.DEFAULT_TIMEOUT);
    assertEquals(
        List.of(Duration.ofSeconds(30), Duration.ofMillis(250), Duration.ofSeconds(5)),
        scheduler.scheduled().stream().map(ManualScheduler.Scheduled::delay).toList());
    assertEquals(Duration.ofSeconds(5), rpc.defaultTimeout());

    scheduler.scheduled().getFirst().fire();

    A2uiRpcError error = failure(future);
    assertEquals(RpcErrorCode.TIMEOUT, error.code());
    assertEquals("Agent function 'lookup' did not answer within 30000 ms.", error.getMessage());
    assertEquals(Optional.of("a"), error.functionCallId());
    assertEquals(2, rpc.pendingCallCount());
    // The response comes too late. The handler ignores it.
    assertFalse(respond("a", "value", 1L));
    assertEquals(RpcErrorCode.TIMEOUT, failure(future).code());
  }

  @Test
  void aResponseCancelsTheTimeoutOfItsCall() throws Exception {
    CompletableFuture<Object> future = rpc.callAgentFunction("s", CALL, id("a"));

    respond("a", "value", "done");

    assertTrue(scheduler.last().isCancelled());
    // A scheduler that does not obey the cancellation changes nothing.
    scheduler.last().fire();
    assertEquals("done", future.get());
  }

  @Test
  void aCallWithATimeoutOfZeroOrLessWaitsWithNoLimit() {
    rpc.callAgentFunction("s", CALL, CallOptions.withTimeout(Duration.ZERO));
    rpc.callAgentFunction("s", CALL, CallOptions.withTimeout(Duration.ofSeconds(-1)));

    assertEquals(List.of(), scheduler.scheduled());
    assertEquals(2, rpc.pendingCallCount());
  }

  @Test
  void aSchedulerThatRunsTheTaskImmediatelyEndsTheCallBeforeTheHandlerSendsIt() {
    rpc.setScheduler((delay, task) -> {
      task.run();
      return () -> {};
    });

    CompletableFuture<Object> future = rpc.callAgentFunction("s", CALL);

    assertEquals(RpcErrorCode.TIMEOUT, failure(future).code());
    assertEquals(List.of(), sent);
    assertEquals(0, rpc.pendingCallCount());
  }

  @Test
  void theCallerOfAFutureCanCancelTheCall() {
    CompletableFuture<Object> future = rpc.callAgentFunction("s", CALL, id("a"));
    rpc.callAgentFunction("s", CALL, id("b"));

    future.cancel(true);

    assertEquals(1, rpc.pendingCallCount());
    assertTrue(scheduler.scheduled().getFirst().isCancelled());
    assertFalse(scheduler.last().isCancelled());
    assertFalse(respond("a", "value", 1L));
    assertTrue(future.isCancelled());
  }

  @Test
  void cancelSurfaceCallsCancelsOnlyTheCallsOfThatSurface() {
    CompletableFuture<Object> ofS = rpc.callAgentFunction("s", CALL, id("a"));
    CompletableFuture<Object> ofT = rpc.callAgentFunction("t", CALL, id("b"));

    rpc.cancelSurfaceCalls("s");

    A2uiRpcError error = failure(ofS);
    assertEquals(RpcErrorCode.CANCELLED, error.code());
    assertEquals("The calls of surface 's' were cancelled.", error.getMessage());
    assertEquals(Optional.of("a"), error.functionCallId());
    assertTrue(scheduler.scheduled().getFirst().isCancelled());
    assertFalse(ofT.isDone());
    assertEquals(1, rpc.pendingCallCount());
  }

  @Test
  void aCallThatStartsWhileTheHandlerCancelsStaysPending() {
    List<CompletableFuture<Object>> started = new ArrayList<>();
    CompletableFuture<Object> first = rpc.callAgentFunction("s", CALL, id("a"));
    first.whenComplete((value, error) -> started.add(rpc.callAgentFunction("s", CALL, id("b"))));

    rpc.cancelSurfaceCalls("s");

    assertTrue(first.isCompletedExceptionally());
    assertFalse(started.getFirst().isDone());
    assertEquals(1, rpc.pendingCallCount());
  }

  @Test
  void disposeCancelsEachCallAndRefusesEachNewCall() {
    CompletableFuture<Object> pending = rpc.callAgentFunction("s", CALL, id("a"));
    assertFalse(rpc.isDisposed());

    rpc.dispose();
    rpc.dispose();

    assertTrue(rpc.isDisposed());
    A2uiRpcError cancelled = failure(pending);
    assertEquals(RpcErrorCode.CANCELLED, cancelled.code());
    assertEquals("The RPC handler was disposed.", cancelled.getMessage());
    assertEquals(RpcErrorCode.DISPOSED, failure(rpc.callAgentFunction("s", CALL, id("a"))).code());
    assertEquals(1, sent.size());
    assertEquals(0, rpc.pendingCallCount());
    assertFalse(respond("a", "value", 1L));
  }

  @Test
  void anIdOfAPendingCallCannotStartASecondCall() throws Exception {
    CompletableFuture<Object> first = rpc.callAgentFunction("s", CALL, id("a"));

    A2uiRpcError error = failure(rpc.callAgentFunction("t", CALL, id("a")));

    assertEquals(RpcErrorCode.DUPLICATE, error.code());
    assertEquals("A call with the functionCallId 'a' is already pending.", error.getMessage());
    assertEquals(1, sent.size());
    // The refused call does not change the first call.
    respond("a", "value", 1L);
    assertEquals(1L, first.get());
    // The ID is free after its call ended.
    assertFalse(rpc.callAgentFunction("s", CALL, id("a")).isDone());
  }

  @Test
  void withoutAListenerACallFailsWithNoListener() {
    RpcHandler silent = new RpcHandler(Map.of(), null);

    A2uiRpcError error = failure(silent.callAgentFunction("s", CALL));

    assertEquals(RpcErrorCode.NO_LISTENER, error.code());
    assertEquals(0, silent.pendingCallCount());
  }

  @Test
  void aCallMustNameItsFunctionAndMustBeJson() {
    A2uiRpcError unnamed = failure(rpc.callAgentFunction("s", Map.of("catalogId", "urn:test")));
    A2uiRpcError notJson =
        failure(rpc.callAgentFunction("s", Map.of("@call", "lookup", "args", Map.of("x", this))));

    assertEquals(RpcErrorCode.INVALID_FUNCTION_CALL, unnamed.code());
    assertEquals(RpcErrorCode.INVALID_FUNCTION_CALL, notJson.code());
    assertEquals(List.of(), sent);
    assertEquals(0, rpc.pendingCallCount());
  }

  @Test
  void anExceptionOfTheListenerFailsTheCall() {
    IllegalStateException closed = new IllegalStateException("The connection is closed.");
    RpcHandler broken =
        new RpcHandler(
            Map.of(),
            message -> {
              throw closed;
            });
    broken.setScheduler(scheduler);

    A2uiRpcError error = failure(broken.callAgentFunction("s", CALL));

    assertEquals(RpcErrorCode.EXECUTION_ERROR, error.code());
    assertEquals(
        "Could not send the call to agent function 'lookup': The connection is closed.",
        error.getMessage());
    assertSame(closed, error.getCause());
    assertEquals(0, broken.pendingCallCount());
    assertTrue(scheduler.last().isCancelled());
  }

  @Test
  void aMessageThatIsNotAResponseCompletesNoCall() {
    rpc.callAgentFunction("s", CALL, id("a"));

    assertFalse(rpc.handleAgentFunctionResponse(Map.of("version", "1.0")));
    assertFalse(
        rpc.handleAgentFunctionResponse(
            Map.of("version", "1.0", "agentFunctionResponse", Map.of("value", 1L))));
    assertEquals(1, rpc.pendingCallCount());
  }

  @Test
  void theDefaultSchedulerEndsACallOnTheTimerThread() throws Exception {
    RpcHandler real = new RpcHandler(Map.of(), sent::add);
    CompletableFuture<Object> future =
        real.callAgentFunction("s", CALL, CallOptions.withTimeout(Duration.ofMillis(1)));

    ExecutionException thrown =
        assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));

    assertEquals(
        RpcErrorCode.TIMEOUT, assertInstanceOf(A2uiRpcError.class, thrown.getCause()).code());
    assertEquals(0, real.pendingCallCount());
  }

  @Test
  void anExecutorSchedulerEndsACallOnTheThreadOfTheExecutor() throws Exception {
    // The queue is the event queue of an application. This thread is its event thread.
    LinkedBlockingQueue<Runnable> events = new LinkedBlockingQueue<>();
    RpcHandler real = new RpcHandler(Map.of(), sent::add);
    real.setScheduler(RpcScheduler.onExecutor(events::add));
    List<Thread> completedOn = new ArrayList<>();
    CompletableFuture<Object> future =
        real.callAgentFunction("s", CALL, CallOptions.withTimeout(Duration.ofMillis(1)));
    future.whenComplete((value, error) -> completedOn.add(Thread.currentThread()));

    Runnable timeout = events.poll(5, TimeUnit.SECONDS);
    assertFalse(future.isDone());
    timeout.run();

    assertEquals(RpcErrorCode.TIMEOUT, failure(future).code());
    assertEquals(List.of(Thread.currentThread()), completedOn);
  }
}
