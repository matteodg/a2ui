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

package com.google.a2ui.core.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.core.exceptions.A2uiRpcError;
import com.google.a2ui.core.exceptions.RpcErrorCode;
import com.google.a2ui.core.internal.AgentRequests.Outcome;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class AgentRequestsTest {
  private final AgentRequests requests = new AgentRequests("s");

  /** The calls that the caller received, in their sequence. */
  private final List<Map<String, Object>> sent = new ArrayList<>();

  /** The future of each call that the caller received, in the same sequence. */
  private final List<CompletableFuture<Object>> futures = new ArrayList<>();

  private final List<Outcome> outcomes = new ArrayList<>();

  private CompletableFuture<Object> record(Map<String, Object> call) {
    sent.add(call);
    CompletableFuture<Object> future = new CompletableFuture<>();
    futures.add(future);
    return future;
  }

  private static Map<String, Object> call(String name) {
    return Map.of("@call", name, "catalogId", "urn:c", "args", Map.of());
  }

  @Test
  void identicalCallsShareOneRequestWhileItWaits() {
    requests.caller(this::record);

    requests.share("k", call("f"), outcomes::add);
    requests.share("k", call("f"), outcomes::add);
    assertEquals(List.of(call("f")), sent);
    assertNull(requests.answer("k"));
    futures.getFirst().complete(List.of(1));

    assertEquals(List.of(new Outcome.Value(List.of(1L)), new Outcome.Value(List.of(1L))), outcomes);
    assertEquals(new Outcome.Value(List.of(1L)), requests.answer("k"));
  }

  @Test
  void aListenerThatCancelsItsRegistrationReceivesNothing() {
    requests.caller(this::record);
    Subscription first = requests.share("k", call("f"), outcomes::add);
    List<Outcome> others = new ArrayList<>();
    requests.share("k", call("f"), others::add);

    first.cancel();
    futures.getFirst().complete("x");

    assertEquals(List.of(), outcomes);
    assertEquals(List.of(new Outcome.Value("x")), others);
  }

  @Test
  void aFailedCallIsNotKeptAndASubsequentCallSendsAgain() {
    requests.caller(this::record);
    A2uiRpcError cause = new A2uiRpcError("No such function", RpcErrorCode.INVALID_FUNCTION_CALL);
    ErrorFields.functionCallId(cause, "c1");

    requests.share("k", call("f"), outcomes::add);
    futures.getFirst().completeExceptionally(cause);
    requests.share("k", call("f"), outcomes::add);

    Outcome.Failed failed = assertInstanceOf(Outcome.Failed.class, outcomes.getFirst());
    assertEquals("No such function", failed.error().getMessage());
    assertEquals(Optional.of("EXPRESSION_ERROR"), failed.error().errorCode());
    assertEquals(Optional.of("s"), failed.error().surfaceId());
    assertEquals(Optional.of("c1"), failed.error().functionCallId());
    assertTrue(ErrorFields.isFailedByAgent(failed.error()));
    assertSame(cause, failed.error().getCause());
    assertNull(requests.answer("k"));
    assertEquals(2, sent.size());
  }

  @Test
  void aWithdrawnCallIsCancelledAndNotAFailure() {
    requests.caller(this::record);

    requests.share("a", call("a"), outcomes::add);
    requests.share("b", call("b"), outcomes::add);
    requests.share("c", call("c"), outcomes::add);
    futures.get(0).cancel(false);
    futures.get(1).completeExceptionally(new A2uiRpcError("gone", RpcErrorCode.CANCELLED));
    futures.get(2).completeExceptionally(new A2uiRpcError("gone", RpcErrorCode.DISPOSED));

    assertEquals(3, outcomes.size());
    outcomes.forEach(outcome -> assertInstanceOf(Outcome.Cancelled.class, outcome));
  }

  @Test
  void aCallerThatThrowsAndAnAnswerThatIsNotJsonAreFailures() {
    requests.caller(call -> {
      throw new IllegalStateException("The connection is closed.");
    });
    requests.fresh(call("f"), outcomes::add);
    requests.caller(call -> CompletableFuture.completedFuture(new Object()));
    requests.fresh(call("g"), outcomes::add);

    Outcome.Failed thrown = assertInstanceOf(Outcome.Failed.class, outcomes.get(0));
    assertEquals("The connection is closed.", thrown.error().getMessage());
    Outcome.Failed notJson = assertInstanceOf(Outcome.Failed.class, outcomes.get(1));
    assertTrue(notJson.error().getMessage().startsWith("The answer to 'g' is not JSON"));
  }

  @Test
  void aFreshCallIsNotSharedAndItsAnswerIsNotKept() {
    requests.caller(this::record);

    requests.fresh(call("f"), outcomes::add);
    requests.fresh(call("f"), outcomes::add);
    futures.get(0).complete(1L);
    futures.get(1).complete(2L);

    assertEquals(List.of(new Outcome.Value(1L), new Outcome.Value(2L)), outcomes);
    assertNull(requests.answer(AgentRequests.keyOf(call("f"))));
  }

  @Test
  void aSurfaceWithNoCallerAndADisposedSurfaceSendNothing() {
    requests.fresh(call("f"), outcomes::add);
    requests.caller(this::record);
    requests.share("k", call("f"), outcomes::add);
    futures.getFirst().complete("kept");
    requests.dispose();
    requests.caller(this::record);
    requests.share("k2", call("g"), outcomes::add);

    assertInstanceOf(Outcome.Cancelled.class, outcomes.get(0));
    assertInstanceOf(Outcome.Cancelled.class, outcomes.get(2));
    assertEquals(1, sent.size());
    assertNull(requests.answer("k"));
    assertNull(requests.caller());
    assertTrue(requests.isDisposed());
  }

  @Test
  void aDifferentCallerStartsWithNoAnswerAndAnOldAnswerIsNotKept() {
    requests.caller(this::record);
    requests.share("k", call("f"), outcomes::add);
    futures.getFirst().complete("old");
    requests.share("late", call("g"), outcomes::add);

    requests.caller(call -> new CompletableFuture<>());
    futures.get(1).complete("late");

    assertNull(requests.answer("k"));
    assertNull(requests.answer("late"));
    assertEquals(List.of(new Outcome.Value("old"), new Outcome.Value("late")), outcomes);
  }

  @Test
  void aSurfaceKeepsALimitedNumberOfAnswers() {
    requests.caller(call -> CompletableFuture.completedFuture(call.get("@call")));

    for (int position = 0; position <= AgentRequests.MAX_ANSWERS; position++) {
      requests.share("key" + position, call("f" + position), outcomes::add);
    }

    assertNull(requests.answer("key0"));
    assertEquals(new Outcome.Value("f1"), requests.answer("key1"));
    assertEquals(
        new Outcome.Value("f" + AgentRequests.MAX_ANSWERS),
        requests.answer("key" + AgentRequests.MAX_ANSWERS));
  }

  @Test
  void theKeyOfACallIgnoresTheSequenceOfTheKeysOfAnObject() {
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("a", 1L);
    args.put("b", List.of("x", "y"));
    Map<String, Object> reversed = new LinkedHashMap<>();
    reversed.put("b", List.of("x", "y"));
    reversed.put("a", 1.0);

    String key = AgentRequests.keyOf(Map.of("@call", "f", "args", args));

    assertEquals(key, AgentRequests.keyOf(Map.of("args", reversed, "@call", "f")));
    assertNotEquals(
        key,
        AgentRequests.keyOf(Map.of("@call", "f", "args", Map.of("a", 1L, "b", List.of("y", "x")))));
    assertNotEquals(
        key,
        AgentRequests.keyOf(
            Map.of("@call", "f", "args", Map.of("a", 1.5, "b", List.of("x", "y")))));
    assertNull(AgentRequests.keyOf(Map.of("@call", "f", "args", Map.of("a", Double.NaN))));
    assertNull(AgentRequests.keyOf(Map.of("@call", "f", "args", Map.of("a", new Object()))));
  }
}
