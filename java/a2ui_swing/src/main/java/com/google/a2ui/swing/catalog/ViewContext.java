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

package com.google.a2ui.swing.catalog;

import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.swing.surface.A2uiServices;

/**
 * The objects that the adapter gives to one {@link SwingComponentView}. A context belongs to one
 * view. Use it only on the event dispatch thread, and only during the life of the view.
 */
public interface ViewContext {
  /**
   * Gets the surface that shows the component. A view can read the ID and the metadata of the
   * surface. It does not read the components or the data of the surface, because the node has the
   * resolved properties.
   *
   * @return the surface
   */
  SurfaceModel<? extends SwingComponentImplementation> surface();

  /**
   * Gets the services of the surface, for the functions that plain Swing does not have.
   *
   * @return the services
   */
  A2uiServices services();
}
