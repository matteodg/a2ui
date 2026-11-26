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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Does an action that a component declares. For an event, the surface reports the action for the
 * agent. For a function call, the runner runs the function.
 *
 * <p>The runner reports each failure of the action to the {@code onError} of the surface, and stops
 * the action. After a failure, the surface reports no action. The runner does not throw the
 * failure.
 *
 * <p>The reported error has one of these protocol error codes:
 *
 * <ul>
 *   <li>{@code INVALID_ACTION}: the action is not an event and not a function call, the event has
 *       no name, or its {@code userMessage} is not text.
 *   <li>{@code EXECUTION_ERROR}: a function ran and failed.
 *   <li>{@code EXPRESSION_ERROR}: the core refused a call before the function ran, or a value of
 *       the event is not correct.
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
   * @param context the scope in which the runner resolves the values of the action
   * @param payload the action as a component declares it
   * @param sourceComponentId the ID of the component that the user operated
   * @param metadata the {@code action.metadata} to send with an event, or null
   * @throws A2uiValidationError if a key of the extensions of the metadata is not a UAX #31
   *     identifier. This is an error of the caller, not of the agent.
   */
  static void run(
      SurfaceModel<?> surface,
      DataContext context,
      Map<String, ?> payload,
      String sourceComponentId,
      A2uiMetadata metadata) {
    ExtensionKeys.check(metadata);
    Map<String, Object> event;
    try {
      if (payload.get("event") instanceof Map<?, ?> written) {
        event = resolveEvent(context, written);
      } else if (payload.get("functionCall") instanceof Map<?, ?> call) {
        context.resolveDynamicValue(call);
        return;
      } else {
        throw invalid("The action is neither an event nor a function call.");
      }
    } catch (A2uiError error) {
      if (error.errorCode().isEmpty()) {
        ErrorFields.errorCode(error, codeOf(error));
      }
      surface.dispatchError(error);
      return;
    }
    // The failure of a listener of the surface is not a failure of the action.
    surface.dispatchAction(Map.of("event", event), sourceComponentId, metadata);
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
   * Resolves the context and the user message of an event. The runner resolves the entries of the
   * context in their sequence, one at a time, and then the user message. The first failure stops
   * the resolution.
   */
  private static Map<String, Object> resolveEvent(DataContext context, Map<?, ?> event) {
    if (!(event.get("name") instanceof String name)) {
      throw invalid("An event action needs a name.");
    }
    Map<String, Object> resolved = new LinkedHashMap<>();
    resolved.put("name", name);

    Map<String, Object> entries = new LinkedHashMap<>();
    if (event.get("context") instanceof Map<?, ?> written) {
      written.forEach((key, value) ->
          entries.put(
              String.valueOf(key), resolvePart(context, value, "context entry '" + key + "'")));
    }
    resolved.put("context", entries);

    Object message = resolvePart(context, event.get("userMessage"), "userMessage");
    // A user message with no value, for example a binding to an absent path, is absent.
    if (message instanceof String text) {
      resolved.put("userMessage", text);
    } else if (message != null) {
      throw invalid("The userMessage of event '" + name + "' is not a string.");
    }
    return resolved;
  }

  /**
   * Resolves one value of an event.
   *
   * @param part the name of the value, for the message of an error
   */
  private static Object resolvePart(DataContext context, Object value, String part) {
    try {
      // The action goes to the agent as JSON. Thus each value must be JSON.
      return Json.freeze(context.resolveDynamicValue(value));
    } catch (A2uiExpressionError error) {
      if (ErrorFields.isRaisedByFunction(error)) {
        throw error;
      }
      throw new A2uiExpressionError(
          "Action event " + part + " is invalid: " + error.getMessage(), error);
    } catch (IllegalArgumentException error) {
      throw new A2uiExpressionError(
          "Action event " + part + " is invalid: " + error.getMessage(), error);
    }
  }
}
