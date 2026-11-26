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
 * The reason why a component cannot show, for the {@code error} fallback.
 *
 * @param type the type of the error: {@link #UNKNOWN_TYPE}, {@link #CYCLIC}, {@link #NO_VIEW}, or a
 *     type that a later version adds. Thus a fallback must accept a type that it does not know.
 * @param componentType the type that the component declares, for {@link #UNKNOWN_TYPE} and for
 *     {@link #NO_VIEW}. It is empty for the other errors.
 */
public record A2uiFallbackError(String type, Optional<String> componentType) {
  /** The type of the error for a component whose type no catalog of the surface declares. */
  public static final String UNKNOWN_TYPE = "unknown-type";

  /** The type of the error for a reference that repeats a parent of the component. */
  public static final String CYCLIC = "cyclic";

  /**
   * The type of the error for a component that a catalog declares, but that the adapter has no view
   * for. The adapter finds this error itself. The core reports nothing for it.
   */
  public static final String NO_VIEW = "no-view";

  /** Makes the reason. */
  public A2uiFallbackError {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(componentType, "componentType");
  }
}
