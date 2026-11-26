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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.internal.Json;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The behavior of {@link Catalog#validationSchema()} that the golden files of the shared suite
 * {@code core/catalog.yaml} do not examine.
 */
class ValidationSchemaTest {
  @Test
  void theSchemaOfTheBasicCatalogHasTheCommonTypesThatItUses() {
    Map<String, Object> schema = BasicCatalog.api().validationSchema();

    Map<String, Object> definitions = Json.asObject(schema.get("$defs"));
    assertTrue(
        definitions
            .keySet()
            .containsAll(
                List.of("DynamicString", "ComponentId", "Weight", "anyComponent", "anyFunction")));
    // The schema of a component has the properties of ComponentCommon that the catalog gives to
    // the component. Thus nothing refers to the common type.
    assertFalse(definitions.containsKey("ComponentCommon"));
    assertEquals(BasicCatalog.ID, schema.get("catalogId"));
    assertEquals("1.0", schema.get("protocolVersion"));
    assertEquals("https://json-schema.org/draft/2020-12/schema", schema.get("$schema"));
    assertFalse(schema.containsKey("$id"));
  }

  @Test
  void theCatalogMakesTheSchemaOneTimeAndTheSchemaIsReadOnly() {
    Catalog<ComponentApi, FunctionApi> catalog = load("{\"components\": {\"Box\": {}}}");

    Map<String, Object> schema = catalog.validationSchema();

    assertSame(schema, catalog.validationSchema());
    assertThrows(UnsupportedOperationException.class, () -> schema.put("extra", 1));
    assertThrows(
        UnsupportedOperationException.class, () -> Json.asObject(schema.get("$defs")).clear());
  }

  @Test
  void aComponentBecomesOneObjectSchemaWithTheContentOfItsMixins() {
    Catalog<ComponentApi, FunctionApi> catalog =
        load(
            """
            {
              "components": {
                "Field": {
                  "type": "object",
                  "allOf": [
                    {"$ref": "common_types.json#/$defs/ComponentCommon"},
                    {"$ref": "common_types.json#/$defs/Checkable"},
                    {"$ref": "#/$defs/Sized"},
                    {
                      "properties": {
                        "component": {"const": "Field"},
                        "size": {"type": "integer"},
                        "label": {"$ref": "common_types.json#/$defs/DynamicString"}
                      },
                      "required": ["component", "label"],
                      "minProperties": 3
                    }
                  ],
                  "unevaluatedProperties": false
                }
              },
              "$defs": {
                "Sized": {
                  "properties": {"size": {"type": "number"}, "unit": {"type": "string"}},
                  "required": ["unit"]
                }
              }
            }
            """);

    Map<String, Object> schema = catalog.validationSchema();

    Map<String, Object> field = entry(schema, "components", "Field");
    Map<String, Object> properties = Json.asObject(field.get("properties"));
    assertEquals(
        Set.of(
            "id", "component", "label", "size", "unit", "@accessibility", "@metadata", "@checks"),
        properties.keySet());
    // The property that the component declares replaces the property of its mixin.
    assertEquals(Map.of("type", "integer"), properties.get("size"));
    assertEquals(Map.of("const", "Field"), properties.get("component"));
    assertEquals(List.of("id", "label", "unit", "component"), field.get("required"));
    assertEquals(false, field.get("unevaluatedProperties"));
    // A keyword that is not a property list stays in an allOf.
    assertEquals(List.of(Map.of("minProperties", 3L)), field.get("allOf"));
    Map<String, Object> definitions = Json.asObject(schema.get("$defs"));
    // After the merge, no reference names the mixin of the catalog.
    assertFalse(definitions.containsKey("Sized"));
    assertTrue(
        definitions.keySet().containsAll(List.of("CheckRule", "Extensions", "FunctionCall")));
  }

  @Test
  void aReferenceToACommonTypeGetsTheDescriptionOfTheType() {
    Catalog<ComponentApi, FunctionApi> catalog =
        load(
            """
            {
              "components": {
                "Label": {
                  "properties": {
                    "text": {"$ref": "common_types.json#/$defs/DynamicString"},
                    "hint": {
                      "$ref": "common_types.json#/$defs/DynamicString",
                      "description": "The hint."
                    }
                  }
                }
              }
            }
            """);

    Map<String, Object> properties =
        Json.asObject(entry(catalog.validationSchema(), "components", "Label").get("properties"));

    assertEquals(
        Map.of("$ref", "#/$defs/DynamicString", "description", "Represents a string"),
        properties.get("text"));
    assertEquals(
        Map.of("$ref", "#/$defs/DynamicString", "description", "The hint."),
        properties.get("hint"));
  }

  @Test
  void theSchemaAlwaysHasTheTwoUnions() {
    Map<String, Object> schema = load("{}").validationSchema();

    Map<String, Object> nothing = Map.of("not", Map.of());
    assertEquals(Map.of("anyComponent", nothing, "anyFunction", nothing), schema.get("$defs"));
    assertFalse(schema.containsKey("components"));
    assertFalse(schema.containsKey("functions"));
  }

  @Test
  void theThemeIsADefinitionThatAcceptsMoreProperties() {
    Catalog<ComponentApi, FunctionApi> catalog =
        load("{\"theme\": {\"type\": \"object\", \"properties\": {\"color\": {}}}}");

    Map<String, Object> schema = catalog.validationSchema();

    assertFalse(schema.containsKey("theme"));
    assertEquals(
        Map.of(
            "type",
            "object",
            "properties",
            Map.of("color", Map.of()),
            "additionalProperties",
            true),
        entry(schema, "$defs", "theme"));
  }

  @Test
  void theArgumentsOfAFunctionAcceptOnlyTheListedNames() {
    Catalog<ComponentApi, FunctionApi> catalog =
        load(
            """
            {
              "functions": {
                "closed": {
                  "returnType": "string",
                  "properties": {"@call": {"const": "closed"}, "args": {"type": "object"}}
                },
                "open": {
                  "returnType": "string",
                  "properties": {
                    "@call": {"const": "open"},
                    "args": {"type": "object", "additionalProperties": true}
                  }
                }
              }
            }
            """);

    Map<String, Object> schema = catalog.validationSchema();

    assertEquals(
        Map.of("type", "object", "unevaluatedProperties", false), arguments(schema, "closed"));
    assertEquals(
        Map.of("type", "object", "unevaluatedProperties", true), arguments(schema, "open"));
  }

  @Test
  void onlyAV1CatalogHasAValidationSchema() {
    ComponentApi box = new ComponentApi("Box", Map.of("type", "object"));

    A2uiCatalogError noVersion =
        assertThrows(
            A2uiCatalogError.class,
            () -> new Catalog<>("urn:old", List.of(box)).validationSchema());
    A2uiCatalogError oldVersion =
        assertThrows(
            A2uiCatalogError.class,
            () ->
                new Catalog<>("urn:old", List.of(box), List.of(), null, ProtocolVersion.V0_9)
                    .validationSchema());

    assertTrue(noVersion.getMessage().contains("an unstated version"), noVersion.getMessage());
    assertTrue(oldVersion.getMessage().contains("v1.0 catalogs only"), oldVersion.getMessage());
  }

  @Test
  void aDefinitionWithTheNameOfACommonTypeThatTheSchemaUsesIsRejected() {
    Catalog<ComponentApi, FunctionApi> usesItsOwnType =
        load("{\"$defs\": {\"Surface\": {\"type\": \"string\"}}}");
    Catalog<ComponentApi, FunctionApi> replacesACommonType =
        load("{\"$defs\": {\"DynamicNumber\": {\"type\": \"number\"}}}");

    assertTrue(
        Json.asObject(usesItsOwnType.validationSchema().get("$defs")).containsKey("Surface"));
    A2uiCatalogError error =
        assertThrows(A2uiCatalogError.class, replacesACommonType::validationSchema);
    assertTrue(error.getMessage().contains("$defs 'DynamicNumber'"), error.getMessage());
  }

  @Test
  void aReferenceToACommonTypeThatDoesNotExistIsRejected() {
    ComponentApi box =
        new ComponentApi(
            "Box",
            Map.of("properties", Map.of("a", Map.of("$ref", "common_types.json#/$defs/Missing"))));
    Catalog<ComponentApi, FunctionApi> catalog =
        new Catalog<>("urn:test", List.of(box), List.of(), null, ProtocolVersion.V1_0);

    A2uiCatalogError error = assertThrows(A2uiCatalogError.class, catalog::validationSchema);

    assertTrue(error.getMessage().contains("unknown common type 'Missing'"), error.getMessage());
  }

  /** Loads a v1.0 catalog document that has the given members and the ID {@code urn:test}. */
  private static Catalog<ComponentApi, FunctionApi> load(String members) {
    return Catalog.fromJson(Json.asObject(Json.parse(members)), "urn:test", ProtocolVersion.V1_0);
  }

  private static Map<String, Object> entry(
      Map<String, Object> schema, String section, String name) {
    return Json.asObject(Json.asObject(schema.get(section)).get(name));
  }

  private static Map<String, Object> arguments(Map<String, Object> schema, String function) {
    Map<String, Object> properties =
        Json.asObject(entry(schema, "functions", function).get("properties"));
    return Json.asObject(properties.get("args"));
  }
}
