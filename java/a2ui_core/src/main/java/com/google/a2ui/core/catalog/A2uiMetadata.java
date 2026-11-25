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

package com.google.a2ui.core.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@code metadata} container of the v1.0 schemas.
 *
 * @param extensions the values that a vendor defines. Each key is a UAX #31 identifier. The result
 *     is empty if the container declares no extensions.
 */
public record A2uiMetadata(Optional<Map<String, Object>> extensions) {
  /** Makes a container and makes a read-only copy of the map of the extensions. */
  public A2uiMetadata {
    Objects.requireNonNull(extensions, "extensions");
    extensions = extensions.map(map -> Collections.unmodifiableMap(new LinkedHashMap<>(map)));
  }
}
