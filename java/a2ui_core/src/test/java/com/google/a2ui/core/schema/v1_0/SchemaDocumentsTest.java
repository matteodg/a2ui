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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaDocumentsTest {
  private static final String BASE = "https://a2ui.org/specification/v1_0/";

  @Test
  void eachDocumentIsTheV1DocumentOfItsName() {
    Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
    documents.put("common_types.json", SchemaDocuments.commonTypes());
    documents.put("agent_to_renderer.json", SchemaDocuments.agentToRenderer());
    documents.put("renderer_to_agent.json", SchemaDocuments.rendererToAgent());
    documents.put("renderer_capabilities.json", SchemaDocuments.rendererCapabilities());
    documents.put("agent_capabilities.json", SchemaDocuments.agentCapabilities());
    documents.put("renderer_data_model.json", SchemaDocuments.rendererDataModel());
    documents.put("catalog_definition.json", SchemaDocuments.catalogDefinition());

    documents.forEach((name, document) -> assertEquals(BASE + name, document.get("$id")));
  }

  @Test
  void theCommonTypesDefineDynamicString() {
    Object definitions = SchemaDocuments.commonTypes().get("$defs");

    assertTrue(definitions instanceof Map<?, ?> map && map.containsKey("DynamicString"));
  }

  @Test
  void aDocumentIsReadOnlyAndEachCallReturnsTheSameObject() {
    Map<String, Object> document = SchemaDocuments.commonTypes();

    assertSame(document, SchemaDocuments.commonTypes());
    assertThrows(UnsupportedOperationException.class, () -> document.put("x", 1L));
  }
}
