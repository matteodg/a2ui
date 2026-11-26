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

package com.google.a2ui.core.resolution;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.state.ComponentModel;
import com.google.a2ui.core.state.RendererAction;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.core.validation.ValidationConfig;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A surface of the basic catalog with its resolver, for the tests of the node resolver. The
 * functions are the functions of the basic catalog with their code.
 */
final class NodeFixture {
  static final String SURFACE_ID = "s";

  /** The URLs that the function {@code openUrl} opened. */
  final List<URI> opened = new ArrayList<>();

  /** The errors that the surface reported. */
  final List<A2uiError> errors = new ArrayList<>();

  /** The actions that the group of the surfaces reported. */
  final List<RendererAction> actions = new ArrayList<>();

  private final MessageProcessor<ComponentApi> processor =
      new MessageProcessor<>(
          List.of(BasicCatalog.withFunctions(Locale.US, opened::add)), ValidationConfig.RELAXED);

  /** Makes a component as a message writes it. */
  static Map<String, Object> component(String id, String type, Object... namesAndValues) {
    Map<String, Object> component = object(namesAndValues);
    component.put("id", id);
    component.put("component", type);
    return component;
  }

  /** Makes a JSON object from names and values. */
  static Map<String, Object> object(Object... namesAndValues) {
    Map<String, Object> object = new LinkedHashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      object.put((String) namesAndValues[i], namesAndValues[i + 1]);
    }
    return object;
  }

  /** Makes a data binding. */
  static Map<String, Object> path(String path) {
    return Map.of("@path", path);
  }

  /** Makes a function call. */
  static Map<String, Object> call(String name, Object... namesAndValues) {
    return Map.of("@call", name, "args", object(namesAndValues));
  }

  /** Makes a list template. */
  static Map<String, Object> template(String componentId, String path) {
    return Map.of("componentId", componentId, "path", path);
  }

  /** Makes the surface with data and components, and then makes its resolver. */
  NodeResolver<ComponentApi> resolve(Map<String, Object> data, Object... components) {
    processor.processMessages(
        Map.of(
            "version",
            "1.0",
            "createSurface",
            object(
                "surfaceId",
                SURFACE_ID,
                "catalogId",
                BasicCatalog.ID,
                "dataModel",
                data,
                "components",
                List.of(components))));
    surface().onError().subscribe(errors::add);
    processor.model().onAction().subscribe(actions::add);
    return new NodeResolver<>(surface());
  }

  /** Makes the surface with data and components, and then gets the root node of its resolver. */
  ComponentNode<ComponentApi> root(Map<String, Object> data, Object... components) {
    return resolve(data, components).rootNode().get().orElseThrow();
  }

  SurfaceModel<ComponentApi> surface() {
    return processor.model().getSurface(SURFACE_ID).orElseThrow();
  }

  /** Processes an {@code updateComponents} message. */
  void update(Object... components) {
    processor.processMessages(
        Map.of(
            "version",
            "1.0",
            "updateComponents",
            Map.of("surfaceId", SURFACE_ID, "components", List.of(components))));
  }

  /** Processes a {@code deleteSurface} message. */
  void deleteSurface() {
    processor.processMessages(
        Map.of("version", "1.0", "deleteSurface", Map.of("surfaceId", SURFACE_ID)));
  }

  /**
   * Adds a component to the model of the surface, or replaces its properties. The message processor
   * does not examine the component. Thus a test can make a cycle of references.
   */
  void put(String id, String type, Object... namesAndValues) {
    Map<String, Object> properties = object(namesAndValues);
    ComponentModel existing = surface().componentsModel().get(id).orElse(null);
    if (existing != null) {
      existing.replaceDefinition(properties, null, null, null);
    } else {
      surface()
          .componentsModel()
          .addComponent(
              new ComponentModel(
                  id,
                  type,
                  surface().defaultCatalog().orElseThrow(),
                  properties,
                  null,
                  null,
                  null));
    }
  }

  void setData(String path, Object value) {
    surface().dataModel().set(path, value);
  }

  /** Counts the notifications of the properties of a node. */
  static int[] countProps(ComponentNode<ComponentApi> node) {
    int[] count = {0};
    node.props().subscribe(props -> count[0]++);
    return count;
  }
}
