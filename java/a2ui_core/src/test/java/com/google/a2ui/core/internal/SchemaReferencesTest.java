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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class SchemaReferencesTest {
  @Test
  void localizeMakesLocalTheReferencesToTheCommonTypesAndToTheCatalog() {
    Object schema =
        Json.parse(
            """
            {
              "a": {"$ref": "common_types.json#/$defs/DynamicString"},
              "b": [{"$ref": "https://a2ui.org/specification/v1_0/common_types.json#/$defs/Action"}],
              "c": {"$ref": "catalog.json#/$defs/anyFunction"},
              "d": {"$ref": "#/$defs/Weight"},
              "e": {"$ref": "https://example.com/other.json#/$defs/Thing"},
              "$ref": 3
            }
            """);
    List<String> commonTypes = new ArrayList<>();

    Object local = SchemaReferences.localize(schema, commonTypes::add);

    assertEquals(
        Json.parse(
            """
            {
              "a": {"$ref": "#/$defs/DynamicString"},
              "b": [{"$ref": "#/$defs/Action"}],
              "c": {"$ref": "#/$defs/anyFunction"},
              "d": {"$ref": "#/$defs/Weight"},
              "e": {"$ref": "https://example.com/other.json#/$defs/Thing"},
              "$ref": 3
            }
            """),
        local);
    assertEquals(List.of("DynamicString", "Action"), commonTypes);
  }

  @Test
  void localizeMakesACopyThatCanChange() {
    Map<String, Object> schema = Json.asObject(Json.parse("{\"items\": [{\"type\": \"string\"}]}"));

    Map<String, Object> copy = Json.asObject(SchemaReferences.localize(schema, name -> {}));
    copy.put("extra", true);

    assertEquals(Set.of("items"), schema.keySet());
  }

  @Test
  void definitionNameReadsTheFirstNameAfterTheDefinitions() {
    assertEquals("Action", SchemaReferences.definitionName("#/$defs/Action"));
    assertEquals("Action", SchemaReferences.definitionName("#/$defs/Action/properties/event"));
    assertEquals("a/b", SchemaReferences.definitionName("#/$defs/a~1b"));
    assertNull(SchemaReferences.definitionName("#/components/Text"));
    assertNull(SchemaReferences.definitionName("common_types.json#/$defs/Action"));
    assertNull(SchemaReferences.definitionName(3));
    assertNull(SchemaReferences.definitionName(null));
  }

  @Test
  void collectDefinitionNamesFindsEachLocalReference() {
    Object schema =
        Json.parse(
            """
            {
              "oneOf": [{"$ref": "#/$defs/A"}, {"items": {"$ref": "#/$defs/B/properties/x"}}],
              "properties": {"c": {"$ref": "#/components/C"}}
            }
            """);
    Set<String> names = new TreeSet<>();

    SchemaReferences.collectDefinitionNames(schema, names);

    assertEquals(Set.of("A", "B"), names);
  }

  @Test
  void theCommonTypesHaveOnlyLocalReferences() {
    Map<String, Object> commonTypes = SchemaReferences.commonTypes();

    assertTrue(commonTypes.containsKey("DynamicString"));
    assertTrue(Json.write(commonTypes).contains("\"$ref\":\"#/$defs/anyFunction\""));
    assertTrue(!Json.write(commonTypes).contains(".json#"));
    assertThrows(UnsupportedOperationException.class, () -> commonTypes.put("Extra", Map.of()));
  }

  @Test
  void theValidationTypesHaveOnlyTheEnvelopeOfAFunctionCall() {
    Map<String, Object> types = SchemaReferences.validationTypes();
    Map<String, Object> call = Json.asObject(types.get("FunctionCall"));

    assertEquals(SchemaReferences.commonTypes().keySet(), types.keySet());
    assertEquals(SchemaReferences.commonTypes().get("Action"), types.get("Action"));
    assertEquals(List.of("@call"), call.get("required"));
    assertEquals(
        Set.of("@call", "catalogId", "args"), Json.asObject(call.get("properties")).keySet());
    assertThrows(UnsupportedOperationException.class, () -> types.put("Extra", Map.of()));
  }
}
