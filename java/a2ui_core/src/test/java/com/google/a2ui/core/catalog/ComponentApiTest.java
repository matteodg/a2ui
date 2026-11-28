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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.exceptions.A2uiCatalogError;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ComponentApiTest {
  @Test
  void theSchemaIsAReadOnlyCopy() {
    Map<String, Object> schema = new HashMap<>(Map.of("type", "object"));

    ComponentApi component = new ComponentApi("Box", schema);
    schema.put("type", "string");

    assertEquals("Box", component.name());
    assertEquals(Map.of("type", "object"), component.schema());
    assertThrows(UnsupportedOperationException.class, () -> component.schema().put("x", 1L));
    assertEquals("ComponentApi(Box)", component.toString());
  }

  @Test
  void aSchemaThatIsNotJsonIsRejected() {
    A2uiCatalogError error =
        assertThrows(
            A2uiCatalogError.class, () -> new ComponentApi("Box", Map.of("default", new Object())));

    assertTrue(
        error.getMessage().startsWith("The definition of component 'Box' is not JSON: "),
        error.getMessage());
  }

  @Test
  void theMetadataGivesTheExtensionsOfTheDefinition() {
    ComponentApi none = new ComponentApi("A", Map.of());
    ComponentApi empty = new ComponentApi("B", Map.of("metadata", Map.of()));
    ComponentApi full =
        new ComponentApi("C", Map.of("metadata", Map.of("extensions", Map.of("vendor", true))));

    assertEquals(Optional.empty(), none.metadata());
    assertEquals(Optional.of(new A2uiMetadata(Optional.empty())), empty.metadata());
    assertEquals(Optional.of(Map.of("vendor", true)), full.metadata().orElseThrow().extensions());
  }

  @Test
  void aMetadataThatIsNotAnObjectIsRejected() {
    assertThrows(A2uiCatalogError.class, () -> new ComponentApi("A", Map.of("metadata", "text")));
  }

  @Test
  void theCompositionListsGiveTheNamesOfTheDefinition() {
    ComponentApi open = new ComponentApi("Text", Map.of());
    ComponentApi limited =
        new ComponentApi(
            "Tab",
            Map.of("allowedParents", List.of("Tabs"), "allowedChildren", List.of("Text", "Icon")));

    assertEquals(Optional.empty(), open.allowedParents());
    assertEquals(Optional.empty(), open.allowedChildren());
    assertEquals(Optional.of(List.of("Tabs")), limited.allowedParents());
    assertEquals(Optional.of(List.of("Text", "Icon")), limited.allowedChildren());
  }
}
