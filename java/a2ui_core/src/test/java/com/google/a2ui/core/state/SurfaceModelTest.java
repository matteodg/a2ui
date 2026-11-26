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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SurfaceModelTest {
  private static final Catalog<ComponentApi, FunctionApi> BASIC = BasicCatalog.api();
  private static final Catalog<ComponentApi, FunctionApi> OTHER =
      new Catalog<>("urn:other", List.of(new ComponentApi("Gauge", Map.of("type", "object"))));

  @Test
  void aSurfaceKeepsItsValues() {
    A2uiMetadata metadata = new A2uiMetadata(Optional.of(Map.of("vendor", 1L)));
    Map<String, Catalog<ComponentApi, FunctionApi>> catalogs = new HashMap<>();
    catalogs.put(BASIC.id(), BASIC);
    SurfaceModel<ComponentApi> surface =
        new SurfaceModel<>("s", ProtocolVersion.V1_0, BASIC, catalogs, true, metadata);

    catalogs.put(OTHER.id(), OTHER);

    assertEquals("s", surface.id());
    assertEquals(ProtocolVersion.V1_0, surface.protocolVersion());
    assertEquals(Optional.of(BASIC), surface.defaultCatalog());
    assertEquals(Map.of(BASIC.id(), BASIC), surface.availableCatalogs());
    assertTrue(surface.sendDataModel());
    assertEquals(Optional.of(metadata), surface.metadata());
    assertEquals("root", surface.rootId());
    assertEquals("root", SurfaceModel.DEFAULT_ROOT_ID);
    assertEquals("SurfaceModel(s)", surface.toString());
    assertTrue(surface.componentsModel().all().isEmpty());
    assertEquals(Map.of(), surface.dataModel().get("/"));
  }

  @Test
  void resolveCatalogGivesTheNamedCatalogOrTheDefaultCatalog() {
    SurfaceModel<ComponentApi> surface = surface(BASIC);

    assertSame(OTHER, surface.resolveCatalog("urn:other"));
    assertSame(BASIC, surface.resolveCatalog(null));
  }

  @Test
  void resolveCatalogFailsForACatalogThatTheSurfaceCannotUse() {
    SurfaceModel<ComponentApi> surface = surface(BASIC);

    A2uiCatalogError byDefault =
        assertThrows(A2uiCatalogError.class, () -> surface.resolveCatalog("urn:missing"));
    A2uiCatalogError withSubject =
        assertThrows(
            A2uiCatalogError.class,
            () -> surface.resolveCatalog("urn:missing", "Function call 'upper'"));

    assertEquals("Catalog not found: urn:missing (Item on surface 's').", byDefault.getMessage());
    assertEquals(
        "Catalog not found: urn:missing (Function call 'upper' on surface 's').",
        withSubject.getMessage());
  }

  @Test
  void resolveCatalogFailsIfNothingNamesACatalog() {
    SurfaceModel<ComponentApi> surface = surface(null);

    A2uiCatalogError error =
        assertThrows(
            A2uiCatalogError.class, () -> surface.resolveCatalog(null, "Component 'root'"));

    assertEquals(Optional.empty(), surface.defaultCatalog());
    assertFalse(surface.sendDataModel());
    assertEquals(
        "Component 'root' names no catalogId and surface 's' has no default catalogId.",
        error.getMessage());
  }

  @Test
  void disposeRemovesTheComponentsAndTheListenersOfTheData() {
    SurfaceModel<ComponentApi> surface = surface(BASIC);
    surface
        .componentsModel()
        .addComponent(new ComponentModel("root", "Text", BASIC, Map.of(), null, null, null));
    List<Object> values = new ArrayList<>();
    surface.dataModel().subscribe("/a", values::add);

    surface.dispose();
    surface.dataModel().set("/a", 1);

    assertTrue(surface.componentsModel().all().isEmpty());
    assertEquals(List.of(), values);
  }

  private static SurfaceModel<ComponentApi> surface(Catalog<ComponentApi, FunctionApi> defaults) {
    return new SurfaceModel<>(
        "s",
        ProtocolVersion.V1_0,
        defaults,
        Map.of(BASIC.id(), BASIC, OTHER.id(), OTHER),
        false,
        null);
  }
}
