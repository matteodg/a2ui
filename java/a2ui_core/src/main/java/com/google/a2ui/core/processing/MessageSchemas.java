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

package com.google.a2ui.core.processing;

import com.google.a2ui.core.internal.Json;
import com.google.a2ui.core.internal.JsonSchemaEngine;
import com.google.a2ui.core.internal.Problem;
import com.google.a2ui.core.internal.SchemaReferences;
import com.google.a2ui.core.schema.v1_0.SchemaDocuments;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The schemas of the v1.0 message envelopes, without the parts that need a catalog.
 *
 * <p>These schemas examine a component in a message only for its {@code id} and its {@code
 * component}. The message processor examines the properties subsequently, against the catalog of
 * the component.
 */
final class MessageSchemas {
  /** The action keys of v1.0, each with the schema definition of its message. */
  static final Map<String, String> ACTIONS = actions();

  /**
   * The action keys that only other protocol versions define. Thus an error can name the action of
   * a message for a different version.
   */
  static final Set<String> OTHER_VERSION_ACTIONS =
      Set.of("beginRendering", "surfaceUpdate", "dataModelUpdate");

  private MessageSchemas() {}

  /**
   * Finds the problems of a message as a message of one action.
   *
   * @param action the action key of the message. It is a key of {@link #ACTIONS}.
   * @param message the message, as {@code Json.freeze} makes it
   * @return the problems, each with its JSON Pointer in the message. The list is empty if the
   *     envelope is correct.
   */
  static List<Problem> problems(String action, Map<String, Object> message) {
    return Engine.INSTANCE.problems("/$defs/" + ACTIONS.get(action), message);
  }

  private static Map<String, String> actions() {
    Map<String, String> actions = new LinkedHashMap<>();
    actions.put("createSurface", "CreateSurfaceMessage");
    actions.put("updateComponents", "UpdateComponentsMessage");
    actions.put("updateDataModel", "UpdateDataModelMessage");
    actions.put("deleteSurface", "DeleteSurfaceMessage");
    actions.put("callRendererFunction", "CallRendererFunctionMessage");
    actions.put("agentFunctionResponse", "AgentFunctionResponseMessage");
    return Collections.unmodifiableMap(actions);
  }

  /** Makes the engine when the JVM initializes the class. That is at the first message. */
  private static final class Engine {
    static final JsonSchemaEngine INSTANCE = new JsonSchemaEngine(document());

    private static Map<String, Object> document() {
      Map<String, Object> definitions = new LinkedHashMap<>(SchemaReferences.validationTypes());
      Map<String, Object> published = Json.asObject(SchemaDocuments.agentToRenderer().get("$defs"));
      published.forEach((name, definition) ->
          definitions.put(name, SchemaReferences.localize(definition, type -> {})));
      // The published definition composes each component with the catalog of the surface.
      definitions.put(
          "Component",
          Map.of(
              "type",
              "object",
              "properties",
              Map.of("id", Map.of("type", "string"), "component", Map.of("type", "string")),
              "required",
              List.of("id", "component")));
      return Json.freezeObject(Map.of("$defs", definitions));
    }
  }
}
