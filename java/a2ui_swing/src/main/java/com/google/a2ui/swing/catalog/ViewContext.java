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

import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.swing.surface.A2uiServices;
import java.awt.Container;
import java.util.function.Function;

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

  /**
   * Makes the helper that keeps the views of children in a Swing container. A component that has
   * children calls this method one time for each container, when it makes its view.
   *
   * @param container the Swing container that gets the components of the children. The helper owns
   *     its content.
   * @return the helper
   * @throws IllegalStateException if the view of this context is disposed
   */
  ChildViews children(Container container);

  /**
   * Makes the helper that keeps the views of children in a Swing container, with a layout
   * constraint for each child.
   *
   * <p>The helper calls the function when it adds the component of a child, and again after each
   * update of the child. When the result changes, the helper gives the new constraint to the layout
   * of the container. This needs a {@code LayoutManager2} that accepts a second call of {@code
   * addLayoutComponent} for a component, as {@code FlexLayout} does.
   *
   * <pre>{@code
   * ChildViews children =
   *     context.children(panel, child -> child.props().get().value("weight"));
   * }</pre>
   *
   * @param container the Swing container that gets the components of the children. The helper owns
   *     its content.
   * @param constraints gives the layout constraint of a child from its node, or null for none
   * @return the helper
   * @throws IllegalStateException if the view of this context is disposed
   */
  ChildViews children(Container container, Function<? super ComponentNode<?>, ?> constraints);
}
