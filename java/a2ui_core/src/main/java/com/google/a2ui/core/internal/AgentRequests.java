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

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.exceptions.A2uiRpcError;
import com.google.a2ui.core.exceptions.RpcErrorCode;
import com.google.a2ui.core.state.AgentFunctionCaller;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The calls that one surface sent to the agent: the requests that wait for an answer, and the
 * answers that the surface keeps.
 *
 * <p>A binding shares the request of an identical call that waits. A binding also uses an answer
 * that the surface keeps. An action does not: each call of an action is a request of its own.
 *
 * <p>Two calls are identical if they have the same catalog, the same function and the same
 * arguments. The sequence of the keys of an object is not important.
 *
 * <p>The surface class gives its requests to this class, because this package is not exported. Thus
 * the requests are not part of the public API.
 *
 * <p>An instance is not thread-safe. Use it on the thread that owns its surface.
 */
public final class AgentRequests {
  /** The number of answers that a surface keeps. The surface removes the oldest answer first. */
  public static final int MAX_ANSWERS = 256;

  private static final String EXPRESSION_ERROR = "EXPRESSION_ERROR";

  /** The end of a call to the agent. */
  public sealed interface Outcome {
    /**
     * The agent answered.
     *
     * @param value the JSON value of the answer, or null. Maps and lists are read-only.
     */
    record Value(Object value) implements Outcome {}

    /**
     * The agent reported an error, or did not answer in time.
     *
     * @param error the failure, with the code {@code EXPRESSION_ERROR}
     */
    record Failed(A2uiExpressionError error) implements Outcome {}

    /** The call was withdrawn, for example because its surface was deleted. It reports nothing. */
    record Cancelled() implements Outcome {}
  }

  /** The outcome of each withdrawn call. */
  private static final Outcome CANCELLED = new Outcome.Cancelled();

  /** One request that waits for its answer, and the listeners that share it. */
  private static final class Request {
    private final List<Consumer<Outcome>> listeners = new ArrayList<>();
  }

  /** Gets the requests of a surface. The initialization of the surface class sets it. */
  private static volatile Function<SurfaceModel<?>, AgentRequests> accessor;

  private final String surfaceId;
  private final Map<String, Request> waiting = new HashMap<>();
  private final LinkedHashMap<String, Outcome.Value> answers = new LinkedHashMap<>();

  /**
   * The checks of each catalog whose declared functions the surface sent. The value is empty for a
   * catalog that has no validation schema.
   */
  private final Map<Catalog<?, ?>, Optional<CatalogChecks>> checks = new IdentityHashMap<>();

  private AgentFunctionCaller caller;
  private boolean disposed;

  /**
   * Makes the requests of one surface. The surface has no caller.
   *
   * @param surfaceId the ID of the surface
   */
  public AgentRequests(String surfaceId) {
    this.surfaceId = Objects.requireNonNull(surfaceId, "surfaceId");
  }

  /**
   * Sets the function that gets the requests of a surface. The initialization of {@link
   * SurfaceModel} calls this method one time.
   *
   * @param requestsOfSurface the function of the surface class
   * @throws IllegalStateException if a function is already set
   */
  public static void register(Function<SurfaceModel<?>, AgentRequests> requestsOfSurface) {
    if (accessor != null) {
      throw new IllegalStateException("The accessor of the agent requests is already set.");
    }
    accessor = Objects.requireNonNull(requestsOfSurface, "requestsOfSurface");
  }

  /**
   * Gets the requests of a surface.
   *
   * @param surface the surface
   * @return the requests
   */
  public static AgentRequests of(SurfaceModel<?> surface) {
    // The caller has a surface. Thus the surface class is initialized and the accessor is set.
    return accessor.apply(Objects.requireNonNull(surface, "surface"));
  }

  /**
   * Gets the caller that sends the calls of the surface.
   *
   * @return the caller, or null if the surface cannot send a call to the agent
   */
  public AgentFunctionCaller caller() {
    return caller;
  }

  /**
   * Sets the caller that sends the calls of the surface. A different caller removes each shared
   * request and each answer, because they are the property of the previous caller.
   *
   * @param newCaller the caller, or null if the surface cannot send a call to the agent
   */
  public void caller(AgentFunctionCaller newCaller) {
    if (disposed || caller == newCaller) {
      return;
    }
    caller = newCaller;
    waiting.clear();
    answers.clear();
  }

  /**
   * Tells if the surface of these requests is disposed.
   *
   * @return true after {@link #dispose()}
   */
  public boolean isDisposed() {
    return disposed;
  }

  /**
   * Gets the answer that the surface keeps for a call.
   *
   * @param key the key of the call. See {@link #keyOf(Map)}.
   * @return the answer, or null if the surface keeps none
   */
  public Outcome.Value answer(String key) {
    return answers.get(key);
  }

  /**
   * Tells a listener the end of a call. The method sends the call, unless an identical call already
   * waits for its answer.
   *
   * <p>The listener can run before this method returns, if the caller completes the call
   * immediately.
   *
   * @param key the key of the call. See {@link #keyOf(Map)}.
   * @param call the call to send
   * @param listener receives the outcome of the call
   * @return the registration of the listener. If the caller cancels it, the listener receives
   *     nothing. The request continues.
   */
  public Subscription share(String key, Map<String, Object> call, Consumer<Outcome> listener) {
    Request existing = waiting.get(key);
    if (existing != null) {
      existing.listeners.add(listener);
      return () -> existing.listeners.remove(listener);
    }
    Request created = new Request();
    created.listeners.add(listener);
    waiting.put(key, created);
    AgentFunctionCaller sentBy = caller;
    send(
        call,
        outcome -> {
          // The surface removes a failed call before a listener knows about the failure. Thus a
          // new evaluation sends the call again.
          waiting.remove(key, created);
          if (outcome instanceof Outcome.Value value && !disposed && caller == sentBy) {
            remember(key, value);
          }
          List.copyOf(created.listeners).forEach(shared -> shared.accept(outcome));
        });
    return () -> created.listeners.remove(listener);
  }

  /**
   * Sends a call as a request of its own, and tells a listener the end of the call. The surface
   * does not keep the answer.
   *
   * @param call the call to send
   * @param listener receives the outcome of the call
   */
  public void fresh(Map<String, Object> call, Consumer<Outcome> listener) {
    send(call, listener);
  }

  /**
   * Gets the checks of a catalog, for the arguments of a function that the catalog declares.
   *
   * @param catalog a catalog of the surface
   * @return the checks, or an empty result if the catalog has no validation schema
   */
  public Optional<CatalogChecks> checksFor(Catalog<?, ?> catalog) {
    return checks.computeIfAbsent(
        catalog,
        key -> {
          try {
            return Optional.of(new CatalogChecks(key));
          } catch (A2uiCatalogError error) {
            return Optional.empty();
          }
        });
  }

  /** Removes the caller, each shared request and each answer. A subsequent call sends nothing. */
  public void dispose() {
    disposed = true;
    caller = null;
    waiting.clear();
    answers.clear();
  }

  private void remember(String key, Outcome.Value answer) {
    answers.remove(key);
    Iterator<String> oldest = answers.keySet().iterator();
    while (answers.size() >= MAX_ANSWERS && oldest.hasNext()) {
      oldest.next();
      oldest.remove();
    }
    answers.put(key, answer);
  }

  private void send(Map<String, Object> call, Consumer<Outcome> done) {
    AgentFunctionCaller current = caller;
    if (disposed || current == null) {
      done.accept(CANCELLED);
      return;
    }
    CompletableFuture<Object> future;
    try {
      future = Objects.requireNonNull(current.call(call), "The caller returned no future.");
    } catch (RuntimeException error) {
      done.accept(outcome(call, null, error));
      return;
    }
    future.whenComplete((value, error) -> done.accept(outcome(call, value, error)));
  }

  private Outcome outcome(Map<String, Object> call, Object value, Throwable error) {
    if (error == null) {
      try {
        return new Outcome.Value(Json.freeze(value));
      } catch (IllegalArgumentException notJson) {
        return failed(
            "The answer to '" + call.get("@call") + "' is not JSON: " + notJson.getMessage(),
            notJson);
      }
    }
    Throwable cause =
        error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    boolean withdrawn =
        cause instanceof CancellationException
            || cause instanceof A2uiRpcError rpc
                && (rpc.code() == RpcErrorCode.CANCELLED || rpc.code() == RpcErrorCode.DISPOSED);
    if (withdrawn) {
      return CANCELLED;
    }
    return failed(
        cause.getMessage() != null
            ? cause.getMessage()
            : "The call to '" + call.get("@call") + "' failed.",
        cause);
  }

  private Outcome.Failed failed(String message, Throwable cause) {
    A2uiExpressionError error = new A2uiExpressionError(message, cause);
    ErrorFields.errorCode(error, EXPRESSION_ERROR);
    ErrorFields.surfaceId(error, surfaceId);
    if (cause instanceof A2uiError known) {
      ErrorFields.functionCallId(error, known.functionCallId().orElse(null));
    }
    return new Outcome.Failed(ErrorFields.failedByAgent(error));
  }

  /**
   * Makes the key that identifies a call. Two calls have the same key if they have the same
   * members. The sequence of the keys of an object is not important. The sequence of a list is
   * important. A number has one key for each value: 1 and 1.0 are the same.
   *
   * @param call the call: its {@code @call}, its {@code catalogId} and its {@code args}
   * @return the key, or null if the call is not JSON and thus cannot go to the agent
   */
  public static String keyOf(Map<String, ?> call) {
    StringBuilder key = new StringBuilder();
    return canonical(call, key) ? key.toString() : null;
  }

  private static boolean canonical(Object value, StringBuilder out) {
    switch (value) {
      case null -> out.append("null");
      case String text -> out.append(Json.write(text));
      case Boolean flag -> out.append(flag);
      case Double number -> {
        return canonicalNumber(number, out);
      }
      case Float number -> {
        return canonicalNumber(number.doubleValue(), out);
      }
      case Long number -> out.append(number.longValue());
      case Integer number -> out.append(number.longValue());
      case Short number -> out.append(number.longValue());
      case Byte number -> out.append(number.longValue());
      case Map<?, ?> map -> {
        TreeSet<String> names = new TreeSet<>();
        for (Object name : map.keySet()) {
          if (!(name instanceof String text)) {
            return false;
          }
          names.add(text);
        }
        out.append('{');
        for (String name : names) {
          out.append(Json.write(name)).append(':');
          if (!canonical(map.get(name), out)) {
            return false;
          }
          out.append(',');
        }
        out.append('}');
      }
      case Collection<?> items -> {
        out.append('[');
        for (Object item : items) {
          if (!canonical(item, out)) {
            return false;
          }
          out.append(',');
        }
        out.append(']');
      }
      default -> {
        return false;
      }
    }
    return true;
  }

  private static boolean canonicalNumber(double number, StringBuilder out) {
    if (!Double.isFinite(number)) {
      return false;
    }
    if (number == Math.rint(number) && Math.abs(number) < 1e15) {
      // An integer has one form. Thus 1 and 1.0 are the same argument.
      out.append((long) number);
    } else {
      out.append(number);
    }
    return true;
  }
}
