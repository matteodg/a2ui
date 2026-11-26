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

/**
 * A resolved action property of a node. A view calls it when the user operates the component, for
 * example from the click listener of a button.
 *
 * <p>For an event action, the surface reports the action to the listeners of its {@code onAction}.
 * The action resolves the context of the event at the time of the call, in the data scope of the
 * node. For a function call action, the action runs the function and the surface reports no action.
 *
 * <p>An action does not throw the failure of the event or of the function. It reports the failure
 * to the listeners of the {@code onError} of the surface.
 */
@FunctionalInterface
public interface NodeAction {
  /**
   * Does the action.
   *
   * @param options the options of this call
   */
  void invoke(ActionDispatchOptions options);

  /** Does the action with no options. */
  default void invoke() {
    invoke(ActionDispatchOptions.NONE);
  }
}
