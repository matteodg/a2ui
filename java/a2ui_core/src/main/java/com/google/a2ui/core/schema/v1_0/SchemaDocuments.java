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

package com.google.a2ui.core.schema.v1_0;

import com.google.a2ui.core.internal.Json;
import java.util.Map;

/**
 * The JSON Schema documents of protocol v1.0, as the specification publishes them.
 *
 * <p>The build copies the files of the specification into the SDK. Thus the SDK and the
 * specification cannot have different schemas. Each document is read-only plain JSON. The SDK reads
 * a document at the first call of its method, and each subsequent call returns the same object.
 */
public final class SchemaDocuments {
  private SchemaDocuments() {}

  /**
   * Gets {@code common_types.json}: the types that catalogs and messages share, for example {@code
   * DynamicString}.
   *
   * @return the document
   */
  public static Map<String, Object> commonTypes() {
    return CommonTypes.DOCUMENT;
  }

  /**
   * Gets {@code agent_to_renderer.json}: one message from the agent to the renderer.
   *
   * @return the document
   */
  public static Map<String, Object> agentToRenderer() {
    return AgentToRenderer.DOCUMENT;
  }

  /**
   * Gets {@code renderer_to_agent.json}: one message from the renderer to the agent.
   *
   * @return the document
   */
  public static Map<String, Object> rendererToAgent() {
    return RendererToAgent.DOCUMENT;
  }

  /**
   * Gets {@code renderer_capabilities.json}: the features that a renderer supports, as it tells an
   * agent.
   *
   * @return the document
   */
  public static Map<String, Object> rendererCapabilities() {
    return RendererCapabilities.DOCUMENT;
  }

  /**
   * Gets {@code agent_capabilities.json}: the features that an agent supports, as it tells a
   * renderer.
   *
   * @return the document
   */
  public static Map<String, Object> agentCapabilities() {
    return AgentCapabilities.DOCUMENT;
  }

  /**
   * Gets {@code renderer_data_model.json}: the data models that a renderer sends to an agent.
   *
   * @return the document
   */
  public static Map<String, Object> rendererDataModel() {
    return RendererDataModel.DOCUMENT;
  }

  /**
   * Gets {@code catalog_definition.json}: the structure of a catalog document.
   *
   * @return the document
   */
  public static Map<String, Object> catalogDefinition() {
    return CatalogDefinition.DOCUMENT;
  }

  private static Map<String, Object> load(String name) {
    return Json.objectResource(SchemaDocuments.class, name);
  }

  // Each class below reads its document when the JVM initializes the class. That is at the first
  // call of the related method.

  private static final class CommonTypes {
    static final Map<String, Object> DOCUMENT = load("common_types.json");
  }

  private static final class AgentToRenderer {
    static final Map<String, Object> DOCUMENT = load("agent_to_renderer.json");
  }

  private static final class RendererToAgent {
    static final Map<String, Object> DOCUMENT = load("renderer_to_agent.json");
  }

  private static final class RendererCapabilities {
    static final Map<String, Object> DOCUMENT = load("renderer_capabilities.json");
  }

  private static final class AgentCapabilities {
    static final Map<String, Object> DOCUMENT = load("agent_capabilities.json");
  }

  private static final class RendererDataModel {
    static final Map<String, Object> DOCUMENT = load("renderer_data_model.json");
  }

  private static final class CatalogDefinition {
    static final Map<String, Object> DOCUMENT = load("catalog_definition.json");
  }
}
