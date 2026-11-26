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
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.ErrorFields;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SurfaceModelTest {
  private static final Catalog<ComponentApi, FunctionApi> BASIC = BasicCatalog.api();
  private static final Catalog<ComponentApi, FunctionApi> OTHER =
      new Catalog<>(
          "urn:other",
          List.of(new ComponentApi("Gauge", Map.of("type", "object"))),
          List.of(),
          null,
          ProtocolVersion.V1_0);

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

  @Test
  void disposeNotifiesTheListenersBeforeItRemovesTheComponents() {
    SurfaceModel<ComponentApi> surface = surface(BASIC);
    surface
        .componentsModel()
        .addComponent(new ComponentModel("root", "Text", BASIC, Map.of(), null, null, null));
    List<Integer> counts = new ArrayList<>();
    surface.onDisposing().subscribe(model -> counts.add(model.componentsModel().all().size()));

    surface.dispose();
    surface.dispose();

    assertEquals(List.of(1), counts);
  }

  @Test
  void dispatchActionReportsAnEventWithItsContext() {
    SurfaceModel<ComponentApi> surface = surface(BASIC);
    surface
        .componentsModel()
        .addComponent(new ComponentModel("save", "Button", BASIC, Map.of(), null, null, null));
    List<RendererAction> actions = new ArrayList<>();
    surface.onAction().subscribe(actions::add);
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("who", "Ada");
    context.put("count", 2L);
    Instant before = Instant.now();

    surface.dispatchAction(
        Map.of("event", Map.of("name", "submit", "context", context, "userMessage", 7L)), "save");

    assertEquals(1, actions.size());
    RendererAction action = actions.getFirst();
    assertEquals("submit", action.name());
    assertEquals("s", action.surfaceId());
    assertEquals("save", action.sourceComponentId());
    assertFalse(action.timestamp().isBefore(before));
    assertEquals(context, action.context());
    assertThrows(UnsupportedOperationException.class, () -> action.context().put("x", 1L));
    assertEquals(Optional.of("7"), action.userMessage());
    assertEquals(Optional.empty(), action.catalogId());
    assertEquals(Optional.empty(), action.metadata());
    assertEquals(
        List.of("name", "userMessage", "surfaceId", "sourceComponentId", "timestamp", "context"),
        List.copyOf(action.toJson().keySet()));
    assertEquals(action.timestamp().toString(), action.toJson().get("timestamp"));
  }

  @Test
  void dispatchActionNamesTheCatalogOfTheSourceIfItIsNotTheDefaultCatalog() {
    SurfaceModel<ComponentApi> surface = surface(BASIC);
    surface
        .componentsModel()
        .addComponent(new ComponentModel("gauge", "Gauge", OTHER, Map.of(), null, null, null));
    List<RendererAction> actions = new ArrayList<>();
    surface.onAction().subscribe(actions::add);
    A2uiMetadata metadata = new A2uiMetadata(Optional.of(Map.of("com_example_trace", "t1")));

    surface.dispatchAction(Map.of("event", Map.of("name", "read")), "gauge", metadata);

    RendererAction action = actions.getFirst();
    assertEquals(Map.of(), action.context());
    assertEquals(Optional.empty(), action.userMessage());
    assertEquals(Optional.of("urn:other"), action.catalogId());
    assertEquals(Optional.of(metadata), action.metadata());
    assertEquals("urn:other", action.toJson().get("catalogId"));
    assertEquals(
        Map.of("extensions", Map.of("com_example_trace", "t1")), action.toJson().get("metadata"));
  }

  @Test
  void dispatchActionReportsNothingForAFunctionCall() {
    SurfaceModel<ComponentApi> surface = surface(BASIC);
    List<RendererAction> actions = new ArrayList<>();
    surface.onAction().subscribe(actions::add);

    surface.dispatchAction(Map.of("functionCall", Map.of("@call", "openUrl")), "root");

    assertEquals(List.of(), actions);
  }

  @Test
  void dispatchActionRejectsAnEventWithNoNameAndAnExtensionKeyThatIsNoIdentifier() {
    SurfaceModel<ComponentApi> surface = surface(BASIC);
    List<RendererAction> actions = new ArrayList<>();
    surface.onAction().subscribe(actions::add);
    A2uiMetadata metadata = new A2uiMetadata(Optional.of(Map.of("not an identifier", 1L)));

    A2uiValidationError noName =
        assertThrows(
            A2uiValidationError.class,
            () -> surface.dispatchAction(Map.of("event", Map.of()), "root"));
    A2uiValidationError badKey =
        assertThrows(
            A2uiValidationError.class,
            () -> surface.dispatchAction(Map.of("event", Map.of("name", "a")), "root", metadata));

    assertEquals("An event action needs a name.", noName.getMessage());
    assertEquals(
        "Extension key 'not an identifier' is not a UAX #31 identifier.", badKey.getMessage());
    assertEquals(List.of(), actions);
  }

  @Test
  void dispatchErrorWritesTheSurfaceOfAnErrorThatNamesNone() {
    SurfaceModel<ComponentApi> surface = surface(BASIC);
    List<A2uiError> errors = new ArrayList<>();
    surface.onError().subscribe(errors::add);
    A2uiError plain = new A2uiExpressionError("The binding failed.");
    A2uiError named = ErrorFields.surfaceId(new A2uiExpressionError("Other."), "other");

    surface.dispatchError(plain);
    surface.dispatchError(named);

    assertEquals(List.of(plain, named), errors);
    assertEquals(Optional.of("s"), plain.surfaceId());
    assertEquals(Optional.of("other"), named.surfaceId());
  }

  @Test
  void allTheCatalogsOfASurfaceAreForItsProtocolVersion() {
    Catalog<ComponentApi, FunctionApi> old =
        new Catalog<>("urn:old", List.of(), List.of(), null, ProtocolVersion.V0_9);
    Catalog<ComponentApi, FunctionApi> unversioned = new Catalog<>("urn:plain", List.of());

    A2uiCatalogError available =
        assertThrows(
            A2uiCatalogError.class,
            () ->
                new SurfaceModel<>(
                    "s",
                    ProtocolVersion.V1_0,
                    BASIC,
                    Map.of(BASIC.id(), BASIC, old.id(), old),
                    false,
                    null));
    A2uiCatalogError defaults =
        assertThrows(
            A2uiCatalogError.class,
            () ->
                new SurfaceModel<>("s", ProtocolVersion.V1_0, unversioned, Map.of(), false, null));

    assertEquals(
        "Protocol version mismatch: cannot mix catalog 'urn:old' (v0.9) with surface version 1.0.",
        available.getMessage());
    assertEquals(
        "Protocol version mismatch: cannot mix unversioned catalog 'urn:plain' with surface"
            + " version 1.0.",
        defaults.getMessage());
    // A surface of a different version accepts the catalogs of that version.
    assertEquals(
        Optional.of(old),
        new SurfaceModel<>("s", ProtocolVersion.V0_9, old, Map.of(old.id(), old), false, null)
            .defaultCatalog());
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
