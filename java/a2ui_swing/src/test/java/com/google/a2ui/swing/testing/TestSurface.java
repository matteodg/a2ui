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

package com.google.a2ui.swing.testing;

import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.state.ComponentModel;
import com.google.a2ui.core.state.RendererAction;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.core.validation.ValidationConfig;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A message processor with the {@link TestCatalog} and one surface, for the tests of the adapter.
 * The processor accepts a reference to a component that did not arrive, and a component of an
 * unknown type. Use it on the event dispatch thread.
 */
public final class TestSurface {
  /** The ID of the surface. */
  public static final String ID = "s";

  /** The catalog of the surface, with the records of its views. */
  public final TestCatalog catalog = new TestCatalog();

  /** The errors that the surface reported. */
  public final List<A2uiError> errors = new ArrayList<>();

  /** The actions that the surface reported. */
  public final List<RendererAction> actions = new ArrayList<>();

  /** The processor that owns the surface. */
  public final MessageProcessor<SwingComponentImplementation> processor =
      new MessageProcessor<>(List.of(catalog.catalog()), ValidationConfig.RELAXED);

  /** Makes a JSON object from names and values. */
  public static Map<String, Object> object(Object... namesAndValues) {
    Map<String, Object> object = new LinkedHashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      object.put((String) namesAndValues[i], namesAndValues[i + 1]);
    }
    return object;
  }

  /** Makes a component as a message writes it. */
  public static Map<String, Object> component(String id, String type, Object... namesAndValues) {
    Map<String, Object> component = object(namesAndValues);
    component.put("id", id);
    component.put("component", type);
    return component;
  }

  /** Makes a data binding. */
  public static Map<String, Object> path(String path) {
    return Map.of("@path", path);
  }

  /** Makes a function call. */
  public static Map<String, Object> call(String name, Object... namesAndValues) {
    return Map.of("@call", name, "args", object(namesAndValues));
  }

  /** Makes a list template. */
  public static Map<String, Object> template(String componentId, String path) {
    return Map.of("componentId", componentId, "path", path);
  }

  /** Makes an event action. */
  public static Map<String, Object> event(String name, Object... contextNamesAndValues) {
    return Map.of("event", object("name", name, "context", object(contextNamesAndValues)));
  }

  /** Makes the surface with data and components. */
  public TestSurface create(Map<String, Object> data, Object... components) {
    processor.processMessages(
        Map.of(
            "version",
            "1.0",
            "createSurface",
            object(
                "surfaceId",
                ID,
                "catalogId",
                TestCatalog.ID,
                "dataModel",
                data,
                "components",
                List.of(components))));
    model().onError().subscribe(errors::add);
    model().onAction().subscribe(actions::add);
    return this;
  }

  /** Processes an {@code updateComponents} message. */
  public void update(Object... components) {
    processor.processMessages(
        Map.of(
            "version",
            "1.0",
            "updateComponents",
            object("surfaceId", ID, "components", List.of(components))));
  }

  /** Processes an {@code updateDataModel} message. */
  public void setData(String path, Object value) {
    processor.processMessages(
        Map.of(
            "version",
            "1.0",
            "updateDataModel",
            object("surfaceId", ID, "path", path, "value", value)));
  }

  /**
   * Puts a component in the surface without the checks of the processor. A test uses this method to
   * make a cycle of references, which the processor does not accept.
   */
  public void put(String id, String type, Object... namesAndValues) {
    Map<String, Object> properties = object(namesAndValues);
    ComponentModel existing = model().componentsModel().get(id).orElse(null);
    if (existing != null) {
      existing.replaceDefinition(properties, null, null, null);
    } else {
      model()
          .componentsModel()
          .addComponent(
              new ComponentModel(
                  id, type, model().defaultCatalog().orElseThrow(), properties, null, null, null));
    }
  }

  /** Processes a {@code deleteSurface} message. */
  public void delete() {
    processor.processMessages(Map.of("version", "1.0", "deleteSurface", object("surfaceId", ID)));
  }

  /** Gets the surface. */
  public SurfaceModel<SwingComponentImplementation> model() {
    return processor.model().getSurface(ID).orElseThrow();
  }
}
