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

package com.google.a2ui.swing.conformance;

import java.util.Map;

/**
 * One case of a conformance suite.
 *
 * @param suite the path of the suite relative to {@code conformance/}, with forward slashes
 * @param fields the case as the suite writes it, as plain values
 */
record ConformanceCase(String suite, Map<String, Object> fields) {
  ConformanceCase {
    if (!(fields.get("name") instanceof String)) {
      throw new IllegalStateException("A case in " + suite + " has no string 'name': " + fields);
    }
  }

  /** Gets the name of the case. */
  String name() {
    return (String) fields.get("name");
  }

  /** Gets the action of the case, or null if the case has no action. */
  String action() {
    return fields.get("action") instanceof String action ? action : null;
  }

  /** Gets the protocol version of the case, or null if the case gives none. */
  String protocolVersion() {
    Object version = fields.get("protocolVersion");
    return version == null ? null : version.toString();
  }

  /** Gets the location of the case for failure messages: {@code <suite>#<case>}. */
  String location() {
    return suite + "#" + name();
  }
}
