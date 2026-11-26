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

import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.ErrorFields;
import com.google.a2ui.core.internal.ExtensionKeys;
import com.google.a2ui.core.internal.Json;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Does an action that a component declares. For an event, the surface reports the action for the
 * agent. For a function call, the runner runs the function.
 *
 * <p>The runner reports each failure of the action to the {@code onError} of the surface, and stops
 * the action. After a failure, the surface reports no action. The runner does not throw the
 * failure.
 *
 * <p>An action can call a function of the agent. The runner sends each such call when the action
 * gets to it, as a request of its own. The action continues when the agent answers. The runner
 * resolves the context entries of an event in their sequence, and then the user message. Thus it
 * sends a call only after the agent answered each call before it. If the surface is deleted while
 * an action waits, the action stops and reports nothing.
 *
 * <p>The reported error has one of these protocol error codes:
 *
 * <ul>
 *   <li>{@code INVALID_ACTION}: the action is not an event and not a function call, the event has
 *       no name, or its {@code userMessage} is not text.
 *   <li>{@code EXECUTION_ERROR}: a function ran and failed.
 *   <li>{@code EXPRESSION_ERROR}: the core refused a call before the function ran, a value of the
 *       event is not correct, or the agent failed a call or did not answer in time.
 *   <li>{@code CATALOG_ERROR}: the surface cannot use the catalog of a call.
 * </ul>
 */
final class ActionRunner {
  private static final String INVALID_ACTION = "INVALID_ACTION";

  private ActionRunner() {}

  /**
   * Does an action. The runner resolves the values of the action at the time of the call.
   *
   * @param surface the surface of the component
   * @param path the absolute data path at which the runner resolves the values of the action
   * @param index the position of the list item of the scope, or an empty value outside a list
   *     template
   * @param userActivated true if the action is the result of a user gesture
   * @param payload the action as a component declares it
   * @param sourceComponentId the ID of the component that the user operated
   * @param metadata the {@code action.metadata} to send with an event, or null
   * @return the result of a function call. It is a {@link Later} while the call waits for the
   *     agent. It is null for an event, and for an action that failed immediately.
   * @throws A2uiValidationError if a key of the extensions of the metadata is not a UAX #31
   *     identifier. This is an error of the caller, not of the agent.
   */
  static Object run(
      SurfaceModel<?> surface,
      String path,
      OptionalInt index,
      boolean userActivated,
      Map<String, ?> payload,
      String sourceComponentId,
      A2uiMetadata metadata) {
    ExtensionKeys.check(metadata);
    DataContext context = DataContext.forAction(surface, path, index, userActivated);
    if (payload.get("event") instanceof Map<?, ?> event) {
      new EventRun(surface, context, event, sourceComponentId, metadata).resume();
      return null;
    }
    try {
      if (!(payload.get("functionCall") instanceof Map<?, ?> call)) {
        throw invalid("The action is neither an event nor a function call.");
      }
      Object result = context.resolvePending(call);
      if (result instanceof Later later) {
        later.then(end -> {
          if (end.state() == Later.State.FAILED) {
            report(surface, end.error());
          }
        });
      }
      return result;
    } catch (A2uiError error) {
      report(surface, error);
      return null;
    }
  }

  /** Reports a failure of an action to the surface. */
  private static void report(SurfaceModel<?> surface, A2uiError error) {
    if (error.errorCode().isEmpty()) {
      ErrorFields.errorCode(error, codeOf(error));
    }
    surface.dispatchError(error);
  }

  /** Gets the protocol error code of a failure of an action that has no code of its own. */
  private static String codeOf(A2uiError error) {
    // The function ran and failed. This is different from a call that the context refused.
    return ErrorFields.isRaisedByFunction(error)
        ? "EXECUTION_ERROR"
        : NodeResolver.bindingCode(error);
  }

  private static A2uiValidationError invalid(String message) {
    return ErrorFields.errorCode(new A2uiValidationError(message), INVALID_ACTION);
  }

  /**
   * One event action before the surface reports it. The runner resolves the entries of the context
   * in their sequence, one at a time, and then the user message. The first failure stops the
   * resolution.
   */
  private static final class EventRun {
    private final SurfaceModel<?> surface;
    private final DataContext context;
    private final Map<?, ?> event;
    private final String sourceComponentId;
    private final A2uiMetadata metadata;
    private final List<Map.Entry<?, ?>> written = new ArrayList<>();
    private final Map<String, Object> entries = new LinkedHashMap<>();
    private final Map<String, Object> resolved = new LinkedHashMap<>();

    /** The position of the part to resolve. After the last entry, the part is the user message. */
    private int position;

    private EventRun(
        SurfaceModel<?> surface,
        DataContext context,
        Map<?, ?> event,
        String sourceComponentId,
        A2uiMetadata metadata) {
      this.surface = surface;
      this.context = context;
      this.event = event;
      this.sourceComponentId = sourceComponentId;
      this.metadata = metadata;
      if (event.get("context") instanceof Map<?, ?> map) {
        written.addAll(map.entrySet());
      }
    }

    /**
     * Resolves the parts of the event from the current position. If a part waits for the agent,
     * this method returns, and the answer of the agent calls it again. After the last part, the
     * surface reports the action.
     */
    private void resume() {
      try {
        if (!(event.get("name") instanceof String)) {
          throw invalid("An event action needs a name.");
        }
        while (position <= written.size()) {
          Object result;
          try {
            result = context.resolvePending(raw());
          } catch (A2uiError error) {
            throw named(error);
          }
          if (result instanceof Later later) {
            later.then(this::settled);
            return;
          }
          accept(result);
        }
      } catch (A2uiError error) {
        report(surface, error);
        return;
      }
      // The failure of a listener of the surface is not a failure of the action.
      if (!surface.isDisposed()) {
        surface.dispatchAction(Map.of("event", resolved), sourceComponentId, metadata);
      }
    }

    /** Continues the event after the agent answered the call of the current part. */
    private void settled(Later end) {
      switch (end.state()) {
        case FAILED -> report(surface, named(end.error()));
        case VALUE -> {
          try {
            accept(end.value());
          } catch (A2uiError error) {
            report(surface, error);
            return;
          }
          resume();
        }
        // The call was withdrawn. The action stops and reports nothing.
        default -> {}
      }
    }

    private boolean isUserMessage() {
      return position == written.size();
    }

    /** Gets the current part as the event writes it. */
    private Object raw() {
      return isUserMessage() ? event.get("userMessage") : written.get(position).getValue();
    }

    /** Gets the name of the current part, for the message of an error. */
    private String part() {
      return isUserMessage()
          ? "userMessage"
          : "context entry '" + written.get(position).getKey() + "'";
    }

    /** Keeps the value of the current part and goes to the subsequent part. */
    private void accept(Object value) {
      Object json;
      try {
        // The action goes to the agent as JSON. Thus each value must be JSON.
        json = Json.freeze(value);
      } catch (IllegalArgumentException error) {
        throw new A2uiExpressionError(
            "Action event " + part() + " is invalid: " + error.getMessage(), error);
      }
      if (!isUserMessage()) {
        entries.put(String.valueOf(written.get(position).getKey()), json);
      } else {
        resolved.put("name", event.get("name"));
        resolved.put("context", entries);
        // A user message with no value, for example a binding to an absent path, is absent.
        if (json instanceof String text) {
          resolved.put("userMessage", text);
        } else if (json != null) {
          throw invalid("The userMessage of event '" + event.get("name") + "' is not a string.");
        }
      }
      position++;
    }

    /**
     * Makes the error of the current part from a failure. A failure that a function or the agent
     * caused stays as it is.
     */
    private A2uiError named(A2uiError error) {
      if (!(error instanceof A2uiExpressionError)
          || ErrorFields.isRaisedByFunction(error)
          || ErrorFields.isFailedByAgent(error)) {
        return error;
      }
      return new A2uiExpressionError(
          "Action event " + part() + " is invalid: " + error.getMessage(), error);
    }
  }
}
