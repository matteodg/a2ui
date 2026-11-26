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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.resolution.ActionDispatchOptions;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.DataContext;
import com.google.a2ui.core.resolution.NodeResolver;
import com.google.a2ui.core.rpc.ManualScheduler;
import com.google.a2ui.core.rpc.RpcScheduler;
import com.google.a2ui.core.state.RendererAction;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * The routing of function calls to the agent: the behavior that the shared suite {@code
 * agent_function_routing.yaml} does not examine, and the threads of an answer.
 */
class AgentFunctionRoutingTest {
  private static final String SURFACE = "s";

  /** The {@code callAgentFunction} messages that the processors sent. */
  private final List<Map<String, Object>> sent = new ArrayList<>();

  /** The other messages that the processors sent. */
  private final List<Map<String, Object>> replies = new ArrayList<>();

  private final List<A2uiError> errors = new ArrayList<>();
  private final ManualScheduler scheduler = new ManualScheduler();

  /** The basic catalog and one function that the agent can call. */
  private final Catalog<ComponentApi, FunctionApi> catalog = catalog();

  private final MessageProcessor<ComponentApi> processor = processor();

  private static Catalog<ComponentApi, FunctionApi> catalog() {
    List<FunctionApi> functions =
        new ArrayList<>(BasicCatalog.withFunctions().functions().values());
    functions.add(
        new FunctionImplementation(
            "echo", Map.of("allowedCallers", "rendererOrAgent"), (args, context) -> args));
    return BasicCatalog.api().withFunctions(functions);
  }

  private MessageProcessor<ComponentApi> processor() {
    MessageProcessor<ComponentApi> made =
        new MessageProcessor<>(
            List.of(catalog),
            null,
            message -> (message.containsKey("callAgentFunction") ? sent : replies).add(message));
    made.rpc().setScheduler(scheduler);
    return made;
  }

  private static Map<String, Object> message(String action, Object... namesAndValues) {
    Map<String, Object> body = new LinkedHashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      body.put((String) namesAndValues[i], namesAndValues[i + 1]);
    }
    return Map.of("version", "1.0", action, body);
  }

  private static Map<String, Object> call(String name, long id) {
    return Map.of("@call", name, "args", Map.of("id", id));
  }

  private static Map<String, Object> text(Object value) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("id", "root");
    root.put("component", "Text");
    root.put("text", value);
    return root;
  }

  /** Makes a surface with components, and the resolver that follows it. */
  private NodeResolver<ComponentApi> mount(
      MessageProcessor<ComponentApi> through, String surfaceId, Object... components) {
    through.processMessages(
        message(
            "createSurface",
            "surfaceId",
            surfaceId,
            "catalogId",
            catalog.id(),
            "components",
            List.of(components)));
    SurfaceModel<ComponentApi> surface = through.model().getSurface(surfaceId).orElseThrow();
    surface.onError().subscribe(errors::add);
    return new NodeResolver<>(surface);
  }

  /** Makes the surface {@code s} whose root is a {@code Text} that shows a value. */
  private NodeResolver<ComponentApi> mount(Object text) {
    return mount(processor, SURFACE, text(text));
  }

  private static ComponentNode<ComponentApi> root(NodeResolver<ComponentApi> resolver) {
    return resolver.rootNode().get().orElseThrow();
  }

  private static Object textOf(NodeResolver<ComponentApi> resolver) {
    return root(resolver).props().get().value("text");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> sentCall(int position) {
    return (Map<String, Object>) sent.get(position).get("callAgentFunction");
  }

  private Object callId(int position) {
    return sentCall(position).get("functionCallId");
  }

  private void answer(int position, Object value) {
    processor.processMessages(
        message("agentFunctionResponse", "functionCallId", callId(position), "value", value));
  }

  @Test
  void aBindingNotifiesItsNodeOneTimeWhenTheAgentAnswers() {
    NodeResolver<ComponentApi> resolver = mount(call("fetchTitle", 1));
    int[] notifications = {0};
    root(resolver).props().subscribe(props -> notifications[0]++);
    int before = notifications[0];

    assertNull(textOf(resolver));
    assertEquals(
        Map.of("@call", "fetchTitle", "catalogId", catalog.id(), "args", Map.of("id", 1L)),
        sentCall(0).get("callFunction"));
    answer(0, "Hello");

    assertEquals("Hello", textOf(resolver));
    assertEquals(before + 1, notifications[0]);
    assertEquals(0, processor.rpc().pendingCallCount());
    assertEquals(List.of(), errors);
  }

  @Test
  void aContextOfTheApplicationReadsAnAnswerAndSendsNothing() {
    NodeResolver<ComponentApi> resolver = mount(call("fetchTitle", 1));
    DataContext context = new DataContext(resolver.surface());

    A2uiExpressionError early =
        assertThrows(
            A2uiExpressionError.class, () -> context.resolveDynamicValue(call("fetchTitle", 1)));
    assertTrue(early.getMessage().contains("fetchTitle"));
    answer(0, "Hello");

    assertEquals("Hello", context.resolveDynamicValue(call("fetchTitle", 1)));
    assertThrows(
        A2uiExpressionError.class, () -> context.resolveDynamicValue(call("fetchTitle", 2)));
    assertEquals(1, sent.size());
  }

  @Test
  void theArgumentsOfACallOfTheAgentDoNotGoBackToTheAgent() {
    NodeResolver<ComponentApi> resolver = mount(call("fetchTitle", 1));
    answer(0, "Hello");
    Map<String, Object> callFunction =
        Map.of(
            "@call",
            "echo",
            "catalogId",
            catalog.id(),
            "args",
            Map.of("value", call("fetchTitle", 1)));

    processor.processMessages(
        message(
            "callRendererFunction",
            "functionCallId",
            "c1",
            "surfaceId",
            SURFACE,
            "callFunction",
            callFunction));

    Map<?, ?> response = (Map<?, ?>) replies.getLast().get("rendererFunctionResponse");
    assertNotNull(response.get("error"));
    assertFalse(response.containsKey("value"));
    assertEquals(1, sent.size());
    assertEquals("Hello", textOf(resolver));
  }

  @Test
  void aUnifiedProcessorSendsTheFailureOfAnAgentCallWithTheIdOfTheCall() {
    List<Map<String, Object>> all = new ArrayList<>();
    MessageProcessor<ComponentApi> unified =
        new MessageProcessor<>(List.of(catalog), null, all::add, true);
    unified.rpc().setScheduler(scheduler);
    NodeResolver<ComponentApi> resolver = mount(unified, "u", text(call("fetchTitle", 1)));

    assertNull(textOf(resolver));
    Object id = ((Map<?, ?>) all.getFirst().get("callAgentFunction")).get("functionCallId");
    unified.processMessages(
        message(
            "agentFunctionResponse",
            "functionCallId",
            id,
            "error",
            Map.of("code", "EXECUTION_ERROR", "message", "The lookup failed.")));

    // The failure is about the call. Thus the message names the call and not the surface.
    assertEquals(2, all.size());
    Map<?, ?> error = (Map<?, ?>) all.getLast().get("error");
    assertEquals("EXPRESSION_ERROR", error.get("code"));
    assertEquals(id, error.get("functionCallId"));
    assertFalse(error.containsKey("surfaceId"));
    assertEquals(1, errors.size());
    assertEquals(Optional.of("u"), errors.getFirst().surfaceId());
  }

  @Test
  void aTimeoutOnTheThreadOfTheProcessorIsReportedImmediately() {
    NodeResolver<ComponentApi> resolver = mount(call("fetchTitle", 1));

    scheduler.last().fire();

    assertNull(textOf(resolver));
    assertEquals(1, errors.size());
    A2uiError error = errors.getFirst();
    assertEquals(Optional.of("EXPRESSION_ERROR"), error.errorCode());
    assertEquals(Optional.of(callId(0)), error.functionCallId());
    assertEquals(Optional.of(SURFACE), error.surfaceId());
  }

  @Test
  void aTimeoutOnADifferentThreadWaitsForTheThreadOfTheProcessor() throws InterruptedException {
    Map<String, Object> rule = Map.of("condition", call("isFree", 1), "message", "Taken");
    Map<String, Object> field = new LinkedHashMap<>();
    field.put("id", "root");
    field.put("component", "TextField");
    field.put("label", "Name");
    field.put("@checks", List.of(rule));
    NodeResolver<ComponentApi> resolver = mount(processor, SURFACE, field);
    List<Thread> notified = new ArrayList<>();
    root(resolver).props().subscribe(props -> notified.add(Thread.currentThread()));
    notified.clear();

    // A scheduler that does not use the thread of the processor fires the timeout.
    Thread timer = new Thread(scheduler.last()::fire);
    timer.start();
    timer.join();

    // The other thread did not change the node, and it reported nothing.
    assertEquals(List.of(), notified);
    assertEquals(List.of(), errors);
    assertEquals(true, root(resolver).props().get().isValid());
    assertEquals(0, processor.rpc().pendingCallCount());

    // The subsequent work of the processor delivers the timeout on its thread.
    processor.processMessages(List.of());

    assertEquals(List.of(Thread.currentThread()), notified);
    assertEquals(false, root(resolver).props().get().isValid());
    assertEquals(1, errors.size());
    assertEquals(Optional.of("EXPRESSION_ERROR"), errors.getFirst().errorCode());
  }

  @Test
  void aSchedulerOnAnExecutorDeliversATimeoutOnTheThreadOfTheProcessor() {
    // The executor is the queue of the thread of the processor, as the queue of a UI toolkit is.
    List<Runnable> queue = new ArrayList<>();
    List<Runnable> timers = new ArrayList<>();
    RpcScheduler onExecutor =
        (delay, task) -> {
          timers.add(() -> queue.add(task));
          return () -> {};
        };
    processor.rpc().setScheduler(onExecutor);
    NodeResolver<ComponentApi> resolver = mount(call("fetchTitle", 1));

    timers.getFirst().run();
    assertEquals(List.of(), errors);
    queue.getFirst().run();

    assertNull(textOf(resolver));
    assertEquals(1, errors.size());
  }

  @Test
  void anActionContinuesWhenTheAgentAnswersItsCall() {
    Map<String, Object> event =
        Map.of("name", "buy", "context", Map.of("price", call("fetchPrice", 7)));
    Map<String, Object> button = new LinkedHashMap<>();
    button.put("id", "root");
    button.put("component", "Button");
    button.put("child", "label");
    button.put("action", Map.of("event", event));
    Map<String, Object> label = new LinkedHashMap<>(text("Buy"));
    label.put("id", "label");
    NodeResolver<ComponentApi> resolver = mount(processor, SURFACE, button, label);
    List<RendererAction> actions = new ArrayList<>();
    resolver.surface().onAction().subscribe(actions::add);

    root(resolver).props().get().action("action").orElseThrow().invoke(ActionDispatchOptions.NONE);
    assertEquals(List.of(), actions);
    root(resolver).props().get().action("action").orElseThrow().invoke(ActionDispatchOptions.NONE);
    assertEquals(2, sent.size());
    answer(0, 12L);

    assertEquals(1, actions.size());
    assertEquals(Map.of("price", 12L), actions.getFirst().context());
  }

  @Test
  void anApplicationCanSetItsOwnCaller() {
    MessageProcessor<ComponentApi> silent = new MessageProcessor<>(List.of(catalog), null);
    NodeResolver<ComponentApi> resolver = mount(silent, SURFACE, text(call("fetchTitle", 1)));
    assertEquals(Optional.of("EXPRESSION_ERROR"), errors.getFirst().errorCode());
    assertEquals(Optional.empty(), resolver.surface().agentFunctionCaller());
    List<Map<String, Object>> asked = new ArrayList<>();
    CompletableFuture<Object> pending = new CompletableFuture<>();

    resolver
        .surface()
        .setAgentFunctionCaller(call -> {
          asked.add(call);
          return pending;
        });
    silent.processMessages(
        message(
            "updateComponents",
            "surfaceId",
            SURFACE,
            "components",
            List.of(text(call("fetchTitle", 2)))));
    pending.complete("Hi");

    assertEquals(
        List.of(Map.of("@call", "fetchTitle", "catalogId", catalog.id(), "args", Map.of("id", 2L))),
        asked);
    assertEquals("Hi", textOf(resolver));
  }

  @Test
  void aDifferentCallerStartsWithoutTheAnswersOfThePreviousCaller() {
    NodeResolver<ComponentApi> resolver = mount(call("fetchTitle", 1));
    answer(0, "Hello");
    SurfaceModel<ComponentApi> surface = resolver.surface();

    surface.setAgentFunctionCaller(call -> new CompletableFuture<>());

    assertThrows(
        A2uiExpressionError.class,
        () -> new DataContext(surface).resolveDynamicValue(call("fetchTitle", 1)));
    // The node keeps the answer that it got. Thus the view does not change.
    assertEquals("Hello", textOf(resolver));
  }

  @Test
  void aDeletedSurfaceCancelsItsCallsAndReportsNothing() {
    mount(call("fetchTitle", 1));

    processor.processMessages(message("deleteSurface", "surfaceId", SURFACE));

    assertEquals(List.of(), errors);
    assertEquals(0, processor.rpc().pendingCallCount());
    assertTrue(scheduler.last().isCancelled());
  }
}
