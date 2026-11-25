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

package com.google.a2ui.core.conformance;

import java.util.Map;
import java.util.Set;

/** Makes sure that a case uses only the keys that its handler reads. */
final class KnownKeys {
  /** The keys of a case that each handler accepts and ignores. */
  static final Set<String> CASE_METADATA_KEYS = Set.of("name", "description", "features");

  private KnownKeys() {}

  /**
   * Fails if a map has a key that is not permitted.
   *
   * <p>Thus a case cannot pass because the harness ignored an input or an expectation.
   *
   * @param map the map to examine
   * @param allowed the permitted keys
   * @param where the description of the map, for example {@code args in
   *     core/data_model.yaml#test_get}
   */
  static void assertKnownKeys(Map<String, ?> map, Set<String> allowed, String where) {
    for (String key : map.keySet()) {
      if (!allowed.contains(key)) {
        throw new AssertionError("Unknown key '" + key + "' in " + where);
      }
    }
  }
}
