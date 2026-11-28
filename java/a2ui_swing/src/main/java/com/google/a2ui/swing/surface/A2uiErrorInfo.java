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

package com.google.a2ui.swing.surface;

import java.util.Objects;
import java.util.Optional;

/**
 * Describes a component that cannot show unless the agent sends a change, for the {@code error}
 * fallback.
 *
 * @param componentId the ID of the component
 * @param parentComponentType the type of the component whose child this is. It is empty for the
 *     root.
 * @param error the reason why the component cannot show
 */
public record A2uiErrorInfo(
    String componentId, Optional<String> parentComponentType, A2uiFallbackError error) {
  /** Makes the description. */
  public A2uiErrorInfo {
    Objects.requireNonNull(componentId, "componentId");
    Objects.requireNonNull(parentComponentType, "parentComponentType");
    Objects.requireNonNull(error, "error");
  }
}
