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
 * Describes a component that did not arrive at this time, for the {@code pending} fallback.
 *
 * @param componentId the ID of the component that did not arrive. Before the root arrives, it is
 *     the ID of the root of the surface.
 * @param parentComponentType the type of the component whose child this is. It is empty for the
 *     root.
 */
public record A2uiPendingInfo(String componentId, Optional<String> parentComponentType) {
  /** Makes the description. */
  public A2uiPendingInfo {
    Objects.requireNonNull(componentId, "componentId");
    Objects.requireNonNull(parentComponentType, "parentComponentType");
  }
}
