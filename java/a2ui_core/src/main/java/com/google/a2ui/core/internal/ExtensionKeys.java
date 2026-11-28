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

package com.google.a2ui.core.internal;

import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.common.Uax31;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import java.util.Map;

/** The rule for the keys of the {@code extensions} of a metadata container. */
public final class ExtensionKeys {
  private ExtensionKeys() {}

  /**
   * Makes sure that each key of the extensions of a metadata container is a UAX #31 identifier.
   *
   * @param metadata the container to examine, or null
   * @throws A2uiValidationError if a key is not a UAX #31 identifier
   */
  public static void check(A2uiMetadata metadata) {
    if (metadata == null) {
      return;
    }
    for (String key : metadata.extensions().orElse(Map.of()).keySet()) {
      if (!Uax31.isIdentifier(key)) {
        throw new A2uiValidationError("Extension key '" + key + "' is not a UAX #31 identifier.");
      }
    }
  }
}
