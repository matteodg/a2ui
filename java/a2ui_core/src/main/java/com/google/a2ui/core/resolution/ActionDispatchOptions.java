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
 * @param metadata the {@code action.metadata} to send to the agent with the action. The result is
 *     empty if the call sends none.
 */
public record ActionDispatchOptions(Optional<A2uiMetadata> metadata) {
  /** The options of a call that sends no metadata. */
  public static final ActionDispatchOptions NONE = new ActionDispatchOptions(Optional.empty());

  /** Makes the options. */
  public ActionDispatchOptions {
    Objects.requireNonNull(metadata, "metadata");
  }

  /**
   * Makes the options of a call that sends metadata.
   *
   * @param metadata the {@code action.metadata} to send to the agent with the action
   * @return the options
   */
  public static ActionDispatchOptions withMetadata(A2uiMetadata metadata) {
    return new ActionDispatchOptions(Optional.of(metadata));
  }
}
