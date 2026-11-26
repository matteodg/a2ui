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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiIntegrityError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SurfaceGroupModelTest {
  private final SurfaceGroupModel<ComponentApi> model = new SurfaceGroupModel<>();

  @Test
  void addSurfaceKeepsTheSurfaceAndNotifiesTheListeners() {
    List<SurfaceModel<ComponentApi>> created = new ArrayList<>();
    model.onSurfaceCreated().subscribe(created::add);
    SurfaceModel<ComponentApi> first = surface("first");
    SurfaceModel<ComponentApi> second = surface("second");

    model.addSurface(first);
    model.addSurface(second);

    assertEquals(List.of(first, second), created);
    assertEquals(List.of("first", "second"), List.copyOf(model.all().keySet()));
    assertEquals(Optional.of(second), model.getSurface("second"));
    assertEquals(Optional.empty(), model.getSurface("missing"));
    assertThrows(UnsupportedOperationException.class, () -> model.all().remove("first"));
  }

  @Test
  void addSurfaceRejectsAnIdThatExists() {
    model.addSurface(surface("s"));

    A2uiIntegrityError error =
        assertThrows(A2uiIntegrityError.class, () -> model.addSurface(surface("s")));

    assertEquals("Surface already exists: s", error.getMessage());
  }

  @Test
  void deleteSurfaceNotifiesTheListenersAndRemovesTheContentOfTheSurface() {
    SurfaceModel<ComponentApi> surface = surface("s");
    surface
        .componentsModel()
        .addComponent(
            new ComponentModel("root", "Text", BasicCatalog.api(), Map.of(), null, null, null));
    model.addSurface(surface);
    List<String> deleted = new ArrayList<>();
    model.onSurfaceDeleted().subscribe(deleted::add);

    model.deleteSurface("s");
    model.deleteSurface("s");

    assertEquals(List.of("s"), deleted);
    assertEquals(Optional.empty(), model.getSurface("s"));
    assertTrue(surface.componentsModel().all().isEmpty());
  }

  @Test
  void disposeDeletesEachSurfaceAndSendsNoEvents() {
    model.addSurface(surface("s"));
    List<String> deleted = new ArrayList<>();
    model.onSurfaceDeleted().subscribe(deleted::add);

    model.dispose();

    assertTrue(model.all().isEmpty());
    assertEquals(List.of(), deleted);
  }

  @Test
  void theModelSendsTheActionsOfEachSurfaceAgain() {
    SurfaceModel<ComponentApi> first = surface("first");
    SurfaceModel<ComponentApi> second = surface("second");
    model.addSurface(first);
    model.addSurface(second);
    List<String> actions = new ArrayList<>();
    model.onAction().subscribe(action -> actions.add(action.surfaceId() + ":" + action.name()));

    first.dispatchAction(Map.of("event", Map.of("name", "save")), "root");
    second.dispatchAction(Map.of("event", Map.of("name", "open")), "root");
    model.deleteSurface("first");
    first.dispatchAction(Map.of("event", Map.of("name", "late")), "root");

    assertEquals(List.of("first:save", "second:open"), actions);
  }

  private static SurfaceModel<ComponentApi> surface(String id) {
    return new SurfaceModel<>(
        id,
        ProtocolVersion.V1_0,
        BasicCatalog.api(),
        Map.of(BasicCatalog.ID, BasicCatalog.api()),
        false,
        null);
  }
}
