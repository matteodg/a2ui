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
import java.util.Objects;
import java.util.Optional;

/**
 * The options for one call of a {@link NodeAction}.
 *
 * <p>A view that calls an action in the listener of a gesture of the user needs no options, or only
 * the metadata. Code that calls an action with no gesture of the user, for example a test or an
 * automation, uses {@link #WITHOUT_USER_ACTIVATION}. Then a function that requires a user gesture,
 * for example {@code openUrl}, does not run, and the surface reports an {@code EXPRESSION_ERROR}.
 *
 * @param metadata the {@code action.metadata} to send to the agent with the action. The result is
 *     empty if the call sends none.
 * @param userActivation true if the call is the result of a gesture of the user. False for a call
 *     that code makes with no gesture.
 */
public record ActionDispatchOptions(Optional<A2uiMetadata> metadata, boolean userActivation) {
  /** The options of a call that is the result of a user gesture and that sends no metadata. */
  public static final ActionDispatchOptions NONE =
      new ActionDispatchOptions(Optional.empty(), true);

  /** The options of a call that is not the result of a user gesture and that sends no metadata. */
  public static final ActionDispatchOptions WITHOUT_USER_ACTIVATION =
      new ActionDispatchOptions(Optional.empty(), false);

  /** Makes the options. */
  public ActionDispatchOptions {
    Objects.requireNonNull(metadata, "metadata");
  }

  /**
   * Makes the options of a call that is the result of a user gesture.
   *
   * @param metadata the {@code action.metadata} to send to the agent with the action. The value is
   *     empty if the call sends none.
   */
  public ActionDispatchOptions(Optional<A2uiMetadata> metadata) {
    this(metadata, true);
  }

  /**
   * Makes the options of a call that is the result of a user gesture and that sends metadata.
   *
   * @param metadata the {@code action.metadata} to send to the agent with the action
   * @return the options
   */
  public static ActionDispatchOptions withMetadata(A2uiMetadata metadata) {
    return new ActionDispatchOptions(Optional.of(metadata), true);
  }

  /**
   * Makes a copy of these options that tells if the call is the result of a user gesture.
   *
   * @param userActivation true if the call is the result of a gesture of the user
   * @return the new options
   */
  public ActionDispatchOptions withUserActivation(boolean userActivation) {
    return new ActionDispatchOptions(metadata, userActivation);
  }
}
