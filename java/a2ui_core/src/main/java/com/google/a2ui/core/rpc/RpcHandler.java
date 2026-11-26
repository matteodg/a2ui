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
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.core.exceptions.A2uiRpcError;
import com.google.a2ui.core.exceptions.RpcErrorCode;
import com.google.a2ui.core.internal.ErrorFields;
import com.google.a2ui.core.internal.Json;
import com.google.a2ui.core.processing.OutboundMessageListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The function calls from the renderer to the agent.
 *
 * <p>{@link #callAgentFunction(String, Map, CallOptions)} sends a {@code callAgentFunction} message
 * to the outbound listener and returns a future. The {@code agentFunctionResponse} message with the
 * same {@code functionCallId} completes the future.
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

  private OutboundMessageListener outboundListener;
  private Duration defaultTimeout = DEFAULT_TIMEOUT;
  private RpcScheduler scheduler = RpcScheduler.timerThread();
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
