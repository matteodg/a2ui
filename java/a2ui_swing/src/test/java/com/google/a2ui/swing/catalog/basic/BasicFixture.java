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

package com.google.a2ui.swing.catalog.basic;

import static com.google.a2ui.swing.testing.TestSurface.object;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.state.RendererAction;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import com.google.a2ui.swing.surface.A2uiFallbacks;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.A2uiSurface;
import com.google.a2ui.swing.surface.MountedNode;
import com.google.a2ui.swing.testing.Headless;
import java.awt.Rectangle;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * A message processor with the basic catalog and one mounted surface, for the tests of the views of
 * the basic catalog. Use it on the event dispatch thread.
 */
final class BasicFixture {
  /** The ID of the surface. */
  static final String ID = "s";

  /** The width of the mounted surface. */
  static final int WIDTH = 400;

  /** The height of the mounted surface. */
  static final int HEIGHT = 300;

  /** The view of each component of the basic catalog that the adapter can show. */
  private static final Map<String, Function<ViewContext, SwingComponentView>> VIEWS =
      Map.of("Text", TextView::new);

  /** The URLs that the function {@code openUrl} opened. */
  final List<URI> opened = new ArrayList<>();

  /** The errors that the surface reported. */
  final List<A2uiError> errors = new ArrayList<>();

  /** The actions that the surface reported. */
  final List<RendererAction> actions = new ArrayList<>();

  /** The processor that owns the surface. */
  final MessageProcessor<SwingComponentImplementation> processor =
      new MessageProcessor<>(List.of(catalog()));

  private final A2uiServices services;

  /** The mounted surface component. */
  A2uiSurface view;

  /** Makes a fixture with the default services. */
  BasicFixture() {
    this(A2uiServices.DEFAULT);
  }

  /** Makes a fixture with services. */
  BasicFixture(A2uiServices services) {
    this.services = services;
  }

  private Catalog<SwingComponentImplementation, FunctionImplementation> catalog() {
    Catalog<ComponentApi, FunctionImplementation> functions =
        BasicCatalog.withFunctions(Locale.US, opened::add);
    List<SwingComponentImplementation> components = new ArrayList<>();
    for (ComponentApi api : functions.components().values()) {
      Function<ViewContext, SwingComponentView> factory = VIEWS.get(api.name());
      components.add(
          factory == null
              ? SwingComponentImplementation.withoutView(api)
              : SwingComponentImplementation.of(api, factory));
    }
    return functions.withComponents(components);
  }

  /** Makes the surface with data and components, and mounts it at the default size. */
  BasicFixture create(Map<String, Object> data, Object... components) {
    Map<String, Object> createSurface =
        object(
            "surfaceId",
            ID,
            "catalogId",
            BasicCatalog.ID,
            "dataModel",
            data,
            "components",
            List.of(components));
    processor.processMessages(Map.of("version", "1.0", "createSurface", createSurface));
    model().onError().subscribe(errors::add);
    model().onAction().subscribe(actions::add);
    view = Headless.mount(new A2uiSurface(model(), A2uiFallbacks.NONE, services), WIDTH, HEIGHT);
    return this;
  }

  /** Processes an {@code updateComponents} message and does the layout again. */
  void update(Object... components) {
    processor.processMessages(
        Map.of(
            "version",
            "1.0",
            "updateComponents",
            object("surfaceId", ID, "components", List.of(components))));
    view.validate();
  }

  /** Processes an {@code updateDataModel} message and does the layout again. */
  void setData(String path, Object value) {
    processor.processMessages(
        Map.of(
            "version",
            "1.0",
            "updateDataModel",
            object("surfaceId", ID, "path", path, "value", value)));
    view.validate();
  }

  /** Gives the surface component a size and does the layout. */
  void resize(int width, int height) {
    view.setSize(width, height);
    view.validate();
  }

  /** Gets the surface. */
  SurfaceModel<SwingComponentImplementation> model() {
    return processor.model().getSurface(ID).orElseThrow();
  }

  /** Gets the value at a path of the data of the surface. */
  Object data(String path) {
    return model().dataModel().get(path);
  }

  /** Gets the position of a component in the tree that the surface shows. */
  MountedNode node(String componentId) {
    return view.viewTree().orElseThrow().find(componentId).orElseThrow();
  }

  /** Gets the view of a component. */
  SwingComponentView viewOf(String componentId) {
    return node(componentId).view().orElseThrow();
  }

  /** Gets the Swing component of a component. */
  JComponent componentOf(String componentId) {
    return node(componentId).component().orElseThrow();
  }

  /** Gets the bounds of a component in the coordinates of the surface component. */
  Rectangle bounds(String componentId) {
    JComponent component = componentOf(componentId);
    return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), view);
  }
}
