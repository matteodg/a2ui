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

import com.google.a2ui.core.exceptions.A2uiValidationError;

/**
 * A resolved action property of a node. A view calls it when the user operates the component, for
 * example from the click listener of a button.
 *
 * <p>For an event action, the surface reports the action to the listeners of its {@code onAction}.
 * The action resolves the context of the event at the time of the call, in the data scope of the
 * node. For a function call action, the action runs the function and the surface reports no action.
 *
 * <p>An action does not throw the failure of the event or of the function. It reports the failure
 * to the listeners of the {@code onError} of the surface, and the surface reports no action. The
 * error has a protocol error code:
 *
 * <ul>
 *   <li>{@code INVALID_ACTION}: the event has no name, or its {@code userMessage} is not text.
 *   <li>{@code EXECUTION_ERROR}: a function ran and failed.
 *   <li>{@code EXPRESSION_ERROR}: the core refused a call before the function ran, for example a
 *       call of a function that the catalog does not have.
 *   <li>{@code CATALOG_ERROR}: the surface cannot use the catalog of a call.
 * </ul>
 *
 * <p>A call counts as the result of a gesture of the user, because a view calls the action in the
 * listener of the gesture. Thus a function that requires a user gesture can run. Code that calls an
 * action with no gesture gives {@link ActionDispatchOptions#WITHOUT_USER_ACTIVATION}. Then such a
 * function does not run, and the surface reports an {@code EXPRESSION_ERROR}.
 *
 * <p>Metadata that is not correct is an error of the caller. Thus the action throws it.
 */
@FunctionalInterface
public interface NodeAction {
  /**
   * Does the action.
   *
   * @param options the options of this call
   * @throws A2uiValidationError if a key of the extensions of the metadata of the options is not a
   *     UAX #31 identifier
   */
  void invoke(ActionDispatchOptions options);

  /** Does the action as the result of a user gesture, with no metadata. */
  default void invoke() {
    invoke(ActionDispatchOptions.NONE);
  }
}
