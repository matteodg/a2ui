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

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiRpcError;
import com.google.a2ui.core.exceptions.RpcErrorCode;
import com.google.a2ui.core.internal.AgentCalls;
import com.google.a2ui.core.internal.CatalogChecks;
import com.google.a2ui.core.internal.ComponentGraph;
import com.google.a2ui.core.internal.ErrorFields;
import com.google.a2ui.core.internal.Json;
import com.google.a2ui.core.internal.Problem;
import com.google.a2ui.core.processing.OutboundMessageListener;
import com.google.a2ui.core.resolution.DataContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The function calls between the renderer and the agent, in the two directions.
 *
 * <ul>
 *   <li>The agent calls a function of a renderer catalog with a {@code callRendererFunction}
 *       message. The handler runs the function and sends a {@code rendererFunctionResponse} message
 *       to the outbound listener. It always sends a response: the value of the function, or the
 *       cause of the failure.
 *   <li>{@link #callAgentFunction(String, Map, CallOptions)} sends a {@code callAgentFunction}
 *       message to the outbound listener and returns a future. The {@code agentFunctionResponse}
 *       message with the same {@code functionCallId} completes the future.
 * </ul>
 *
 * <p>A message processor owns one handler and gives it the messages of the agent. An application
 * gets the handler from the processor.
 *
 * <p><b>Threads.</b> A handler is not thread-safe. Call each method on the thread that owns the
 * message processor. The task that the handler gives to its {@link RpcScheduler} is the only
 * exception: the task can run on a different thread.
 *
 * <p>The future of a call completes on the thread that ends the call:
 *
 * <ul>
 *   <li>For a response, a cancellation or a disposal, that is the thread of the caller of the
 *       processor or of the handler.
 *   <li>For a timeout, that is the thread on which the scheduler runs its task. With the default
 *       scheduler, that is the timer thread of the SDK. See {@link RpcScheduler}.
 * </ul>
 *
 * <p>Code that an application attaches to the future, for example with {@code thenAccept}, runs on
 * that thread.
 */
public final class RpcHandler {
  /** The time that a call to the agent waits for its response if nothing sets a different time. */
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  private static final String VERSION = ProtocolVersion.V1_0.wireValue();

  private static final String CALL_RENDERER_FUNCTION = "callRendererFunction";

  /** The message for the agent when the code of a function throws an unexpected exception. */
  private static final String EXECUTION_FAILED = "An error occurred during function execution.";

  /** The message for the agent when an argument fails and its error has no message. */
  private static final String UNRESOLVED_ARGUMENT =
      "An argument of the function call could not be resolved.";

  private final Map<String, ? extends Catalog<?, ?>> catalogs;

  /**
   * The calls that wait for a response, by {@code functionCallId}.
   *
   * <p>This map is the only state that a different thread can use: the task of a timeout removes
   * its call. The map is a concurrent map, because the default scheduler runs that task on the
   * timer thread. The thread that removes a call from the map completes the future of the call.
   * Thus each call ends one time.
   */
  private final Map<String, PendingCall> pending = new ConcurrentHashMap<>();

  private final Map<Catalog<?, ?>, CatalogChecks> checks = new IdentityHashMap<>();
  private OutboundMessageListener outboundListener;
  private Duration defaultTimeout = DEFAULT_TIMEOUT;
  private RpcScheduler scheduler = RpcScheduler.timerThread();
  private BooleanSupplier userActivation = () -> false;
  private boolean disposed;

  /** One call that waits for its response. */
  private static final class PendingCall {
    final String surfaceId;
    final CompletableFuture<Object> future;

    /** The registration of the timeout, or null. Only the owner thread uses this field. */
    Subscription timeout;

    PendingCall(String surfaceId, CompletableFuture<Object> future) {
      this.surfaceId = surfaceId;
      this.future = future;
    }

    void cancelTimeout() {
      if (timeout != null) {
        timeout.cancel();
        timeout = null;
      }
    }
  }

  /**
   * Makes a handler.
   *
   * @param catalogs the catalogs of the renderer by ID. The handler does not copy the map. Thus the
   *     handler uses a catalog that the owner of the map adds subsequently.
   * @param outboundListener receives the messages for the agent, or null. Without a listener, a
   *     call to the agent fails with {@link RpcErrorCode#NO_LISTENER}.
   */
  public RpcHandler(
      Map<String, ? extends Catalog<?, ?>> catalogs, OutboundMessageListener outboundListener) {
    this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
    this.outboundListener = outboundListener;
  }

  /**
   * Gets the time that a call to the agent waits for its response, if its options give no timeout.
   *
   * @return the time. It is {@link #DEFAULT_TIMEOUT} for a new handler.
   */
  public Duration defaultTimeout() {
    return defaultTimeout;
  }

  /**
   * Sets the time that a call to the agent waits for its response, if its options give no timeout.
   * The change applies to each subsequent call.
   *
   * @param defaultTimeout the time. With a time of zero or less, a call waits with no limit.
   */
  public void setDefaultTimeout(Duration defaultTimeout) {
    this.defaultTimeout = Objects.requireNonNull(defaultTimeout, "defaultTimeout");
  }

  /**
   * Sets the scheduler that runs the timeout of each subsequent call to the agent.
   *
   * <p>A new handler uses {@link RpcScheduler#timerThread()}. An application that attaches code to
   * the future of a call sets a scheduler that runs its tasks on the thread of the application.
   *
   * @param scheduler the scheduler
   */
  public void setScheduler(RpcScheduler scheduler) {
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
  }

  /**
   * Sets the test that tells if a user gesture is present.
   *
   * <p>The agent can call a function that requires user activation only while this test gives true.
   * The handler asks one time for each {@code callRendererFunction} message. For a new handler, the
   * test always gives false. Thus the agent cannot call such a function, until the application
   * supplies its own test of a recent user gesture.
   *
   * @param userActivation the test
   */
  public void setUserActivation(BooleanSupplier userActivation) {
    this.userActivation = Objects.requireNonNull(userActivation, "userActivation");
  }

  /**
   * Gets the number of the calls to the agent that wait for a response.
   *
   * @return the number
   */
  public int pendingCallCount() {
    return pending.size();
  }

  /**
   * Tells if the handler is disposed.
   *
   * @return true after {@link #dispose()}
   */
  public boolean isDisposed() {
    return disposed;
  }

  /**
   * Calls a function of the agent with the default options. See {@link #callAgentFunction(String,
   * Map, CallOptions)}.
   *
   * @param surfaceId the ID of the surface that makes the call
   * @param call the call as the protocol writes it
   * @return the future of the result
   */
  public CompletableFuture<Object> callAgentFunction(String surfaceId, Map<String, ?> call) {
    return callAgentFunction(surfaceId, call, CallOptions.DEFAULT);
  }

  /**
   * Calls a function of the agent.
   *
   * <p>The handler sends a {@code callAgentFunction} message to the outbound listener. The future
   * completes with the {@code value} of the {@code agentFunctionResponse} message that has the same
   * {@code functionCallId}. Maps and lists in the value are read-only.
   *
   * <p>The future fails with an {@link A2uiRpcError}. Its code tells the cause:
   *
   * <ul>
   *   <li>The code of the {@code error} of the response, if the agent reported an error. A code
   *       that the SDK does not know becomes {@link RpcErrorCode#EXECUTION_ERROR}.
   *   <li>{@link RpcErrorCode#TIMEOUT}: no response came in the permitted time.
   *   <li>{@link RpcErrorCode#CANCELLED}: the surface was deleted, {@link
   *       #cancelSurfaceCalls(String)} cancelled its calls, or the handler was disposed.
   *   <li>{@link RpcErrorCode#DISPOSED}: the call started after the disposal of the handler.
   *   <li>{@link RpcErrorCode#NO_LISTENER}: the handler has no outbound listener.
   *   <li>{@link RpcErrorCode#DUPLICATE}: a call with the same ID waits for a response.
   *   <li>{@link RpcErrorCode#INVALID_FUNCTION_CALL}: the call has no name or is not JSON.
   *   <li>{@link RpcErrorCode#EXECUTION_ERROR}: the outbound listener threw an exception.
   * </ul>
   *
   * <p>The method throws no error for these causes. The future that it returns is already failed.
   *
   * <p>If the caller cancels the future, the handler forgets the call and ignores its response.
   * Cancel the future on the thread that owns the handler. The class comment tells on which thread
   * the future completes.
   *
   * @param surfaceId the ID of the surface that makes the call
   * @param call the call as the protocol writes it: <code>{"@call": name, "catalogId": id, "args":
   *     {...}}</code>. The arguments are resolved values.
   * @param options the timeout and the ID of the call
   * @return the future of the result
   */
  public CompletableFuture<Object> callAgentFunction(
      String surfaceId, Map<String, ?> call, CallOptions options) {
    Objects.requireNonNull(surfaceId, "surfaceId");
    Objects.requireNonNull(call, "call");
    Objects.requireNonNull(options, "options");
    String functionCallId = options.functionCallId().orElseGet(() -> UUID.randomUUID().toString());
    CompletableFuture<Object> future = new CompletableFuture<>();
    if (disposed) {
      return failed(
          future, RpcErrorCode.DISPOSED, "The RPC handler is disposed.", surfaceId, functionCallId);
    }
    OutboundMessageListener listener = outboundListener;
    if (listener == null) {
      return failed(
          future,
          RpcErrorCode.NO_LISTENER,
          "Cannot call an agent function: the processor has no outbound listener.",
          surfaceId,
          functionCallId);
    }
    if (!(call.get("@call") instanceof String name) || name.isEmpty()) {
      return failed(
          future,
          RpcErrorCode.INVALID_FUNCTION_CALL,
          "A call to the agent must name its function with '@call'.",
          surfaceId,
          functionCallId);
    }
    Map<String, Object> callFunction;
    try {
      callFunction = Json.freezeObject(call);
    } catch (IllegalArgumentException e) {
      return failed(
          future,
          RpcErrorCode.INVALID_FUNCTION_CALL,
          "The call to agent function '" + name + "' is not JSON: " + e.getMessage(),
          surfaceId,
          functionCallId);
    }
    PendingCall entry = new PendingCall(surfaceId, future);
    if (pending.putIfAbsent(functionCallId, entry) != null) {
      return failed(
          future,
          RpcErrorCode.DUPLICATE,
          "A call with the functionCallId '" + functionCallId + "' is already pending.",
          surfaceId,
          functionCallId);
    }
    // The caller can cancel the future. Then the handler forgets the call. If the handler ended
    // the call, the call is already not in the map and this step does nothing.
    future.whenComplete((value, error) -> {
      if (pending.remove(functionCallId, entry)) {
        entry.cancelTimeout();
      }
    });

    Duration timeout = options.timeout().orElse(defaultTimeout);
    if (timeout.isPositive()) {
      Subscription registration =
          scheduler.schedule(
              timeout,
              () -> {
                // This task can run on a different thread. It uses only the concurrent map and
                // the future.
                if (pending.remove(functionCallId, entry)) {
                  future.completeExceptionally(
                      rpcError(
                          RpcErrorCode.TIMEOUT,
                          "Agent function '"
                              + name
                              + "' did not answer within "
                              + timeout.toMillis()
                              + " ms.",
                          surfaceId,
                          functionCallId,
                          null));
                }
              });
      if (future.isDone()) {
        // The timeout or the caller already ended the call. The handler sends nothing.
        registration.cancel();
        return future;
      }
      entry.timeout = registration;
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("surfaceId", surfaceId);
    body.put("functionCallId", functionCallId);
    body.put("callFunction", callFunction);
    try {
      listener.onMessage(message("callAgentFunction", body));
    } catch (RuntimeException e) {
      if (settle(functionCallId, entry)) {
        future.completeExceptionally(
            rpcError(
                RpcErrorCode.EXECUTION_ERROR,
                "Could not send the call to agent function '" + name + "': " + e.getMessage(),
                surfaceId,
                functionCallId,
                e));
      }
    }
    return future;
  }

  /**
   * Completes the call that an {@code agentFunctionResponse} message answers.
   *
   * <p>The message processor calls this method after it examined the message. An application does
   * not call it.
   *
   * @param message the message as plain JSON values
   * @return true if the response completed a call. The result is false if no call with the ID of
   *     the response waits: the call timed out, was cancelled or was not made. The handler ignores
   *     such a response.
   */
  public boolean handleAgentFunctionResponse(Map<String, ?> message) {
    Objects.requireNonNull(message, "message");
    if (!(message.get("agentFunctionResponse") instanceof Map<?, ?> response)
        || !(response.get("functionCallId") instanceof String functionCallId)) {
      return false;
    }
    PendingCall entry = pending.get(functionCallId);
    if (entry == null || !settle(functionCallId, entry)) {
      return false;
    }
    if (response.get("error") instanceof Map<?, ?> error) {
      String text =
          error.get("message") instanceof String written ? written : "The agent reported an error.";
      entry.future.completeExceptionally(
          rpcError(codeOf(error.get("code")), text, entry.surfaceId, functionCallId, null));
    } else {
      entry.future.complete(Json.freeze(response.get("value")));
    }
    return true;
  }

  /**
   * Fails each call of one surface that waits for a response, with {@link RpcErrorCode#CANCELLED}.
   *
   * <p>The message processor calls this method when a surface is deleted. An application can call
   * it for a surface that it keeps.
   *
   * @param surfaceId the ID of the surface
   */
  public void cancelSurfaceCalls(String surfaceId) {
    Objects.requireNonNull(surfaceId, "surfaceId");
    cancel(
        "The calls of surface '" + surfaceId + "' were cancelled.",
        entry -> entry.surfaceId.equals(surfaceId));
  }

  /**
   * Disposes the handler. Each call that waits for a response fails with {@link
   * RpcErrorCode#CANCELLED}. Each subsequent call fails with {@link RpcErrorCode#DISPOSED}. The
   * handler sends no more messages to the outbound listener.
   *
   * <p>A second call has no effect.
   */
  public void dispose() {
    if (disposed) {
      return;
    }
    disposed = true;
    outboundListener = null;
    cancel("The RPC handler was disposed.", entry -> true);
  }

  private void cancel(String message, Predicate<PendingCall> selected) {
    // The code that an application attached to a future can start a new call. Thus the handler
    // first collects the calls to cancel.
    List<Map.Entry<String, PendingCall>> chosen = new ArrayList<>();
    pending.forEach((functionCallId, entry) -> {
      if (selected.test(entry)) {
        chosen.add(Map.entry(functionCallId, entry));
      }
    });
    for (Map.Entry<String, PendingCall> call : chosen) {
      PendingCall entry = call.getValue();
      if (settle(call.getKey(), entry)) {
        entry.future.completeExceptionally(
            rpcError(RpcErrorCode.CANCELLED, message, entry.surfaceId, call.getKey(), null));
      }
    }
  }

  /**
   * Removes a call from the calls that wait, and cancels its timeout.
   *
   * @return true if this caller removed the call. Then this caller completes the future. The result
   *     is false if the timeout or the caller of the future already ended the call.
   */
  private boolean settle(String functionCallId, PendingCall entry) {
    if (!pending.remove(functionCallId, entry)) {
      return false;
    }
    entry.cancelTimeout();
    return true;
  }

  /**
   * Runs the function that a {@code callRendererFunction} message calls, and sends the {@code
   * rendererFunctionResponse} message to the outbound listener.
   *
   * <p>The message processor calls this method after it examined the message. An application does
   * not call it.
   *
   * <p>The response has the {@code value} of the function, also if the function returns nothing. If
   * the function does not run or fails, the response has an {@code error} with one of these codes:
   *
   * <ul>
   *   <li>{@code INVALID_FUNCTION_CALL}: the handler refused the call. The surface, the catalog or
   *       the function does not exist, the catalog is not a v1.0 catalog, the function has no code,
   *       or the arguments do not agree with the schema of the function. The handler also refuses a
   *       function that only the renderer can call, and a function that requires user activation
   *       when {@link #setUserActivation(BooleanSupplier)} tells that there is none. These two
   *       rules also apply to each call in the arguments and to each call in a template that the
   *       function evaluates. The handler examines the calls that the arguments write before it
   *       evaluates an argument.
   *   <li>{@code EXECUTION_ERROR}: an argument or the function failed.
   *   <li>The code of an {@link A2uiRpcError} that the code of the function threw.
   * </ul>
   *
   * @param message the message as plain JSON values
   * @param context the root context of the surface that the message names. For a message that names
   *     no surface, it is the root context of an empty surface, because such a call reads the data
   *     of no surface. It is null if the surface that the message names does not exist.
   * @return the response message that the handler sent, as read-only plain JSON values. If the
   *     handler has no outbound listener, it sends nothing.
   * @throws IllegalArgumentException if the message has no {@code callRendererFunction} with a
   *     {@code functionCallId}
   */
  public Map<String, Object> handleCallRendererFunction(
      Map<String, ?> message, DataContext context) {
    Objects.requireNonNull(message, "message");
    if (!(message.get(CALL_RENDERER_FUNCTION) instanceof Map<?, ?> body)
        || !(body.get("functionCallId") instanceof String functionCallId)) {
      throw new IllegalArgumentException(
          "The message has no callRendererFunction with a functionCallId.");
    }
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("functionCallId", functionCallId);
    try {
      response.put("value", run(body, context));
    } catch (A2uiRpcError e) {
      Map<String, Object> error = new LinkedHashMap<>();
      error.put("code", e.code().name());
      error.put("message", textOf(e, EXECUTION_FAILED));
      response.put("error", Collections.unmodifiableMap(error));
    }
    Map<String, Object> sent = message("rendererFunctionResponse", response);
    if (outboundListener != null) {
      outboundListener.onMessage(sent);
    }
    return sent;
  }

  /**
   * Runs the function of a {@code callRendererFunction} body.
   *
   * @return the result of the function as a read-only JSON value
   * @throws A2uiRpcError if the function does not run or fails. The code is the code for the agent.
   */
  private Object run(Map<?, ?> body, DataContext context) {
    if (disposed) {
      throw new A2uiRpcError("The RPC handler is disposed.", RpcErrorCode.DISPOSED);
    }
    if (!(body.get("callFunction") instanceof Map<?, ?> call)
        || !(call.get("@call") instanceof String name)
        || !(call.get("catalogId") instanceof String catalogId)) {
      throw AgentCalls.refused(
          "The callFunction of a callRendererFunction message must have '@call' and 'catalogId'.");
    }
    Object written = call.get("args");
    if (written != null && !(written instanceof Map<?, ?>)) {
      throw AgentCalls.refused(
          "The args of the call to function '" + name + "' must be an object.");
    }
    Object surfaceId = body.get("surfaceId");
    if (context == null || (surfaceId != null && !surfaceId.equals(context.surface().id()))) {
      throw AgentCalls.refused("Surface not found: " + surfaceId);
    }

    // The catalog that the call names, and no other catalog. A default catalog can have a
    // different function with the same name.
    Catalog<?, ?> catalog = context.surface().availableCatalogs().get(catalogId);
    if (catalog == null) {
      catalog = catalogs.get(catalogId);
    }
    if (catalog == null) {
      throw AgentCalls.refused("Catalog not found: " + catalogId);
    }
    if (catalog.protocolVersion().orElse(null) != ProtocolVersion.V1_0) {
      throw AgentCalls.refused(
          "Catalog '"
              + catalogId
              + "' specification version ("
              + catalog.protocolVersion().map(ProtocolVersion::wireValue).orElse("none")
              + ") does not match message protocol version ("
              + VERSION
              + ").");
    }
    FunctionApi function = catalog.functions().get(name);
    if (function == null) {
      throw AgentCalls.refused("Function not found: " + name);
    }
    boolean userActivated = userActivation.getAsBoolean();
    AgentCalls.examine(function, userActivated);
    if (!(function instanceof FunctionImplementation implementation)) {
      throw AgentCalls.refused(
          "Function '"
              + name
              + "' of catalog '"
              + catalogId
              + "' has no implementation in this renderer.");
    }

    // Each call that this context evaluates is a call of the agent.
    DataContext scope = AgentCalls.context(context, catalog, userActivated);
    Map<?, ?> arguments = written instanceof Map<?, ?> map ? map : Map.of();
    examineNestedCalls(arguments, scope, catalog, userActivated);
    Map<String, Object> resolved = resolveArguments(name, arguments, scope);

    List<Problem> problems;
    try {
      problems =
          checks.computeIfAbsent(catalog, CatalogChecks::new).argumentProblems(name, resolved);
    } catch (A2uiCatalogError e) {
      throw new A2uiRpcError(
          "Cannot examine the arguments of function '" + name + "': " + e.getMessage(),
          RpcErrorCode.INVALID_FUNCTION_CALL,
          e);
    }
    if (!problems.isEmpty()) {
      throw AgentCalls.refused(
          "Invalid arguments for function '"
              + name
              + "': "
              + problems.stream().map(Problem::toString).collect(Collectors.joining("; ")));
    }

    Object result;
    try {
      result = implementation.execute(resolved, scope);
    } catch (A2uiRpcError e) {
      // The context refused a call of a template, or the function selected the code for the agent.
      throw e;
    } catch (A2uiError e) {
      throw new A2uiRpcError(textOf(e, EXECUTION_FAILED), RpcErrorCode.EXECUTION_ERROR, e);
    } catch (RuntimeException e) {
      // The message of such an exception can contain internal data. The agent does not get it.
      throw new A2uiRpcError(EXECUTION_FAILED, RpcErrorCode.EXECUTION_ERROR, e);
    }
    try {
      return Json.freeze(result);
    } catch (IllegalArgumentException e) {
      throw new A2uiRpcError(
          "The result of function '" + name + "' is not JSON: " + e.getMessage(),
          RpcErrorCode.EXECUTION_ERROR,
          e);
    }
  }

  /**
   * Examines each function call that the arguments write, against the caller boundary of the agent.
   * The method runs no function. Thus no argument runs if the agent cannot make one of the calls.
   *
   * <p>The method does not examine a call whose catalog or function it cannot find, because the
   * evaluation reports that problem.
   *
   * @throws A2uiRpcError with {@code INVALID_FUNCTION_CALL} if the agent cannot make a call, or
   *     with {@code EXECUTION_ERROR} if the arguments nest deeper than the limit of the core
   */
  private static void examineNestedCalls(
      Map<?, ?> arguments, DataContext scope, Catalog<?, ?> active, boolean userActivated) {
    boolean[] tooDeep = {false};
    arguments
        .values()
        .forEach(value -> examineNode(value, 1, scope, active, userActivated, tooDeep));
    // A refusal comes before this failure. Thus the method examines all the calls that it can.
    if (tooDeep[0]) {
      throw new A2uiRpcError(
          "The arguments of the call nest values deeper than "
              + ComponentGraph.MAX_DEPTH
              + " levels.",
          RpcErrorCode.EXECUTION_ERROR);
    }
  }

  private static void examineNode(
      Object node,
      int depth,
      DataContext scope,
      Catalog<?, ?> active,
      boolean userActivated,
      boolean[] tooDeep) {
    if (depth > ComponentGraph.MAX_DEPTH) {
      tooDeep[0] = true;
      return;
    }
    if (node instanceof List<?> list) {
      list.forEach(item -> examineNode(item, depth + 1, scope, active, userActivated, tooDeep));
    } else if (node instanceof Map<?, ?> map) {
      Object children = map;
      if (map.get("@call") instanceof String name) {
        if (!name.startsWith("@")) {
          Catalog<?, ?> catalog = nestedCatalog(map, scope, active);
          FunctionApi function = catalog == null ? null : catalog.functions().get(name);
          if (function != null) {
            AgentCalls.examine(function, userActivated);
          }
        }
        children = map.get("args");
      }
      if (children instanceof Map<?, ?> members) {
        members
            .values()
            .forEach(value -> examineNode(value, depth + 1, scope, active, userActivated, tooDeep));
      } else {
        examineNode(children, depth + 1, scope, active, userActivated, tooDeep);
      }
    }
  }

  /**
   * Gets the catalog in which a call in an argument runs, as the data context selects it.
   *
   * @return the catalog, or null if the method cannot find it
   */
  private static Catalog<?, ?> nestedCatalog(
      Map<?, ?> call, DataContext scope, Catalog<?, ?> active) {
    Object catalogId = call.get("catalogId");
    if (catalogId == null) {
      return scope.surface().defaultCatalog().orElse(null);
    }
    if (catalogId.equals(active.id())) {
      return active;
    }
    return scope.surface().availableCatalogs().get(catalogId);
  }

  /** Resolves the data bindings and the function calls of the arguments. */
  private static Map<String, Object> resolveArguments(
      String name, Map<?, ?> arguments, DataContext scope) {
    Map<String, Object> resolved = new LinkedHashMap<>();
    try {
      arguments.forEach(
          (key, value) -> resolved.put(String.valueOf(key), scope.resolveDynamicValue(value)));
    } catch (A2uiRpcError e) {
      // The context refused a call that the static examination cannot see.
      throw e;
    } catch (A2uiError e) {
      throw new A2uiRpcError(textOf(e, UNRESOLVED_ARGUMENT), RpcErrorCode.EXECUTION_ERROR, e);
    }
    try {
      return Json.freezeObject(resolved);
    } catch (IllegalArgumentException e) {
      throw new A2uiRpcError(
          "An argument of the call to function '" + name + "' is not JSON: " + e.getMessage(),
          RpcErrorCode.EXECUTION_ERROR,
          e);
    }
  }

  /** Gets the message of an error, or a default text if the error has no message. */
  private static String textOf(A2uiError error, String fallback) {
    String message = error.getMessage();
    return message == null || message.isEmpty() ? fallback : message;
  }

  private static RpcErrorCode codeOf(Object written) {
    for (RpcErrorCode code : RpcErrorCode.values()) {
      if (code.name().equals(written)) {
        return code;
      }
    }
    return RpcErrorCode.EXECUTION_ERROR;
  }

  private static CompletableFuture<Object> failed(
      CompletableFuture<Object> future,
      RpcErrorCode code,
      String message,
      String surfaceId,
      String functionCallId) {
    future.completeExceptionally(rpcError(code, message, surfaceId, functionCallId, null));
    return future;
  }

  private static A2uiRpcError rpcError(
      RpcErrorCode code, String message, String surfaceId, String functionCallId, Throwable cause) {
    A2uiRpcError error = new A2uiRpcError(message, code, cause);
    ErrorFields.surfaceId(error, surfaceId);
    return ErrorFields.functionCallId(error, functionCallId);
  }

  /** Makes a message for the agent. */
  private static Map<String, Object> message(String action, Map<String, Object> body) {
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("version", VERSION);
    message.put(action, Collections.unmodifiableMap(body));
    return Collections.unmodifiableMap(message);
  }
}
