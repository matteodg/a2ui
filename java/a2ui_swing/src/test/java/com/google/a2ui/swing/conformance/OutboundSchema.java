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

import com.google.a2ui.core.internal.Json;
import com.google.a2ui.core.internal.JsonSchemaEngine;
import com.google.a2ui.core.internal.SchemaReferences;
import com.google.a2ui.core.schema.v1_0.SchemaDocuments;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes sure that a message for the agent is correct for the schema {@code renderer_to_agent.json}
 * of protocol v1.0. The class uses the JSON Schema engine of the core, which the tests of the core
 * also use for this schema.
 */
final class OutboundSchema {
  private static final JsonSchemaEngine SCHEMA = schema();

  private OutboundSchema() {}

  private static JsonSchemaEngine schema() {
    Map<String, Object> document =
        new LinkedHashMap<>(
            Json.asObject(
                SchemaReferences.localize(SchemaDocuments.rendererToAgent(), name -> {})));
    Map<String, Object> definitions = new LinkedHashMap<>(SchemaReferences.commonTypes());
    // The schema takes the function calls of a message from a catalog. A case can call a function
    // of each catalog. Thus the harness accepts each function call that has a name.
    definitions.put(
        "anyFunction",
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "@call", Map.of("type", "string"),
                "args", Map.of("type", "object"),
                "catalogId", Map.of("type", "string")),
            "required",
            List.of("@call")));
    document.put("$defs", definitions);
    return new JsonSchemaEngine(Json.freezeObject(document));
  }

  /**
   * Fails if a message is not correct for the schema.
   *
   * @param message the message
   * @param where the location of the message, for the failure message
   */
  static void assertValid(Map<String, Object> message, String where) {
    List<String> errors = SCHEMA.errors("", message);
    if (!errors.isEmpty()) {
      throw new AssertionError(where + " is not correct for renderer_to_agent.json: " + errors);
    }
  }
}
