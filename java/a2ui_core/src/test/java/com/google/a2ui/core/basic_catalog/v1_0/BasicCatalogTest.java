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

package com.google.a2ui.core.basic_catalog.v1_0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.catalog.AllowedCallers;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.catalog.FunctionReturnType;
import com.google.a2ui.core.common.ProtocolVersion;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BasicCatalogTest {
  @Test
  void theCatalogHasThePublishedEntries() {
    Catalog<ComponentApi, FunctionApi> catalog = BasicCatalog.api();

    assertEquals(BasicCatalog.ID, catalog.id());
    assertEquals(Optional.of(ProtocolVersion.V1_0), catalog.protocolVersion());
    assertEquals(18, catalog.components().size());
    assertEquals(14, catalog.functions().size());
    assertFalse(catalog.instructions().orElseThrow().isEmpty());
    assertSame(catalog, BasicCatalog.api());
  }

  @Test
  void theFunctionsHaveThePublishedSignatures() {
    Map<String, FunctionApi> functions = BasicCatalog.api().functions();

    FunctionApi openUrl = functions.get("openUrl");
    assertEquals(FunctionReturnType.VOID, openUrl.returnType());
    assertEquals(AllowedCallers.RENDERER_ONLY, openUrl.allowedCallers());
    assertTrue(openUrl.requiresUserActivation());
    Object properties = openUrl.argumentSchema().orElseThrow().get("properties");
    assertTrue(properties instanceof Map<?, ?> map && map.keySet().equals(Set.of("url")));
    assertFalse(functions.get("formatString").requiresUserActivation());
  }

  @Test
  void aDerivedCatalogWritesTheUnionsForItsEntries() {
    Catalog<ComponentApi, FunctionApi> derived =
        BasicCatalog.api()
            .derive("urn:small", List.of("Text"), List.of(), List.of(), List.of(), null);

    Object definitions = derived.toJson().get("$defs");
    assertTrue(definitions instanceof Map<?, ?>);
    Map<?, ?> defs = (Map<?, ?>) definitions;
    assertEquals(
        Map.of(
            "oneOf",
            List.of(Map.of("$ref", "#/components/Text")),
            "discriminator",
            Map.of("propertyName", "component")),
        defs.get("anyComponent"));
    assertEquals(Map.of("not", Map.of()), defs.get("anyFunction"));
    assertTrue(defs.containsKey("Weight"));
    assertEquals(Set.of("Text"), Catalog.fromJson(derived.toJson()).components().keySet());
  }

  @Test
  void theCatalogAndItsDocumentAreReadOnly() {
    Catalog<ComponentApi, FunctionApi> catalog = BasicCatalog.api();

    assertThrows(UnsupportedOperationException.class, () -> catalog.toJson().put("x", 1L));
    assertThrows(UnsupportedOperationException.class, () -> catalog.components().clear());
    assertThrows(UnsupportedOperationException.class, () -> catalog.functions().clear());
  }
}
