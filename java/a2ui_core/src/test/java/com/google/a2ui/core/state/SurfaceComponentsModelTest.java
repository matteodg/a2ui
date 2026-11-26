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
import com.google.a2ui.core.exceptions.A2uiStateError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SurfaceComponentsModelTest {
  private final SurfaceComponentsModel model = new SurfaceComponentsModel();

  @Test
  void addComponentKeepsTheComponentAndNotifiesTheListeners() {
    List<ComponentModel> created = new ArrayList<>();
    model.onCreated().subscribe(created::add);
    ComponentModel root = text("root");
    ComponentModel title = text("title");

    model.addComponent(root);
    model.addComponent(title);

    assertEquals(List.of(root, title), created);
    assertEquals(List.of("root", "title"), List.copyOf(model.all().keySet()));
    assertEquals(Optional.of(title), model.get("title"));
    assertEquals(Optional.empty(), model.get("missing"));
    assertThrows(UnsupportedOperationException.class, () -> model.all().remove("root"));
  }

  @Test
  void addComponentRejectsAnIdThatTheSurfaceHas() {
    model.addComponent(text("root"));

    A2uiStateError error =
        assertThrows(A2uiStateError.class, () -> model.addComponent(text("root")));

    assertEquals("Component 'root' is already on the surface.", error.getMessage());
  }

  @Test
  void removeComponentNotifiesTheListenersAndRemovesTheListenersOfTheComponent() {
    ComponentModel root = text("root");
    model.addComponent(root);
    List<String> deleted = new ArrayList<>();
    List<ComponentModel> updated = new ArrayList<>();
    model.onDeleted().subscribe(deleted::add);
    root.onUpdated().subscribe(updated::add);

    model.removeComponent("root");
    model.removeComponent("root");
    root.replaceDefinition(Map.of("text", "changed"), null, null, null);

    assertEquals(List.of("root"), deleted);
    assertEquals(List.of(), updated);
    assertTrue(model.all().isEmpty());
  }

  @Test
  void disposeRemovesEachComponentAndSendsNoEvents() {
    model.addComponent(text("root"));
    List<String> deleted = new ArrayList<>();
    model.onDeleted().subscribe(deleted::add);

    model.dispose();

    assertTrue(model.all().isEmpty());
    assertEquals(List.of(), deleted);
  }

  private static ComponentModel text(String id) {
    return new ComponentModel(
        id, "Text", BasicCatalog.api(), Map.of("text", "Hello"), null, null, null);
  }
}
