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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.internal.CatalogSchemaIndex.ChildReference;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CatalogSchemaIndexTest {
  private final CatalogSchemaIndex basic = new CatalogSchemaIndex(BasicCatalog.api());

  @Test
  void declaredPropertiesHasTheNamesOfTheSchemaAndOfItsLocalDefinitions() {
    Set<String> text = basic.declaredProperties("Text");

    assertTrue(text.containsAll(Set.of("component", "text", "variant", "weight")), text.toString());
    assertFalse(text.contains("children"), text.toString());
    assertEquals(Set.of(), basic.declaredProperties("Mystery"));
  }

  @Test
  void theFieldOfAReferenceIsAlsoAJsonPointer() {
    assertEquals("/child", new ChildReference("a", "child").pointer());
    assertEquals("/children/2", new ChildReference("a", "children[2]").pointer());
    assertEquals("/tabs/0/child", new ChildReference("a", "tabs[0].child").pointer());
  }

  @Test
  void childrenFindsTheIdsOfAStaticChildList() {
    Map<String, Object> column =
        Map.of("id", "root", "component", "Column", "children", List.of("a", "b", "a"));

    assertEquals(
        List.of(new ChildReference("a", "children[0]"), new ChildReference("b", "children[1]")),
        basic.children("Column", column));
  }

  @Test
  void childrenFindsTheTemplateOfAChildListAndASingleChild() {
    Map<String, Object> list =
        Map.of(
            "id",
            "root",
            "component",
            "List",
            "children",
            Map.of("componentId", "item", "path", "/items"));
    Map<String, Object> card = Map.of("id", "card", "component", "Card", "child", "body");

    assertEquals(List.of(new ChildReference("item", "children")), basic.children("List", list));
    assertEquals(List.of(new ChildReference("body", "child")), basic.children("Card", card));
  }

  @Test
  void childrenFindsAReferenceInAnItemOfAList() {
    Map<String, Object> tabs =
        Map.of(
            "id",
            "root",
            "component",
            "Tabs",
            "tabs",
            List.of(
                Map.of("title", "One", "child", "first"),
                Map.of("title", "Two", "child", "second")));

    assertEquals(
        List.of(
            new ChildReference("first", "tabs[0].child"),
            new ChildReference("second", "tabs[1].child")),
        basic.children("Tabs", tabs));
  }

  @Test
  void theIdOfTheComponentAndATextThatIsNotAReferenceAreNotChildren() {
    Map<String, Object> text = Map.of("id", "root", "component", "Text", "text", "root");

    assertEquals(List.of(), basic.children("Text", text));
    assertEquals(
        List.of(), basic.children("Carousel", Map.of("id", "c", "children", List.of("a"))));
  }

  @Test
  void aPropertyWithALegacyEnvelopeNameIsAPropertyOfTheComponent() {
    CatalogSchemaIndex index =
        new CatalogSchemaIndex(
            load(
                """
                {
                  "components": {
                    "Plain": {"properties": {"label": {"type": "string"}}},
                    "Owner": {
                      "allOf": [{"$ref": "#/$defs/Shared"}],
                      "properties": {"metadata": {"type": "string"}}
                    },
                    "Inline": {
                      "properties": {
                        "id": {"type": "string"},
                        "catalogId": {"type": "string"},
                        "metadata": {
                          "type": "object",
                          "properties": {"extensions": {"type": "object"}}
                        },
                        "checks": {"type": "array"}
                      }
                    }
                  },
                  "$defs": {"Shared": {"properties": {"catalogId": {"type": "number"}}}}
                }
                """));

    assertFalse(index.ownsLegacyEnvelopeName("Plain", "metadata"));
    assertTrue(index.ownsLegacyEnvelopeName("Owner", "metadata"));
    assertTrue(index.ownsLegacyEnvelopeName("Owner", "catalogId"));
    assertFalse(index.ownsLegacyEnvelopeName("Owner", "checks"));
    // The schema writes the envelope adjacent to `id`. These names are not its own properties.
    assertFalse(index.ownsLegacyEnvelopeName("Inline", "catalogId"));
    assertFalse(index.ownsLegacyEnvelopeName("Inline", "metadata"));
    assertTrue(index.ownsLegacyEnvelopeName("Inline", "checks"));
    assertFalse(index.ownsLegacyEnvelopeName("Missing", "metadata"));
  }

  private static Catalog<?, ?> load(String members) {
    return Catalog.fromJson(Json.asObject(Json.parse(members)), "urn:test", ProtocolVersion.V1_0);
  }
}
