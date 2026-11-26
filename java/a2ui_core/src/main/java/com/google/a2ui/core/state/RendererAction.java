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

package com.google.a2ui.core.state;

import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.internal.Json;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An operation of the user that the agent asked to know about: the {@code action} of a message from
 * the renderer to the agent.
 *
 * @param name the name of the event, from the {@code action.event.name} of the component
 * @param surfaceId the ID of the surface that the action came from
 * @param sourceComponentId the ID of the component that the user operated
 * @param timestamp the time of the action
 * @param context the context of the event. Each binding is resolved. The map is read-only.
 * @param userMessage the resolved {@code userMessage} of the event. The result is empty if the
 *     event has none.
 * @param catalogId the ID of the catalog of the source component. The result is empty if that
 *     catalog is the default catalog of the surface.
 * @param metadata the {@code action.metadata} that the caller gave at dispatch. The result is empty
 *     if the caller gave none.
 */
public record RendererAction(
    String name,
    String surfaceId,
    String sourceComponentId,
    Instant timestamp,
    Map<String, Object> context,
    Optional<String> userMessage,
    Optional<String> catalogId,
    Optional<A2uiMetadata> metadata) {
  /**
   * Makes an action and makes a read-only copy of the context.
   *
   * @throws IllegalArgumentException if the context is not JSON
   */
  public RendererAction {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(surfaceId, "surfaceId");
    Objects.requireNonNull(sourceComponentId, "sourceComponentId");
    Objects.requireNonNull(timestamp, "timestamp");
    context = Json.freezeObject(Objects.requireNonNull(context, "context"));
    Objects.requireNonNull(userMessage, "userMessage");
    Objects.requireNonNull(catalogId, "catalogId");
    Objects.requireNonNull(metadata, "metadata");
  }

  /**
   * Writes the action as the {@code action} object of a message from the renderer to the agent.
   *
   * @return the JSON object. The map is read-only.
   */
  public Map<String, Object> toJson() {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("name", name);
    userMessage.ifPresent(message -> json.put("userMessage", message));
    json.put("surfaceId", surfaceId);
    json.put("sourceComponentId", sourceComponentId);
    json.put("timestamp", timestamp.toString());
    json.put("context", context);
    catalogId.ifPresent(id -> json.put("catalogId", id));
    metadata.ifPresent(container ->
        json.put("metadata", Map.of("extensions", container.extensions().orElse(Map.of()))));
    return Collections.unmodifiableMap(json);
  }
}
