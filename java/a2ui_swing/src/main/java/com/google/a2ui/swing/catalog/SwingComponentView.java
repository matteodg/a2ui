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
import com.google.a2ui.core.resolution.NodeProps;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.swing.JComponent;

/**
 * The view of one component of a surface: a Swing component and the code that keeps it up to date.
 *
 * <p>A view has state. The adapter makes it one time, calls {@link #update(ComponentNode,
 * NodeProps)} after the creation and after each change, and calls {@link #dispose()} when the
 * surface stops to show the component. The Swing component is the same object for the life of the
 * view. Thus a text field keeps its caret and its focus through an update.
 *
 * <p>The adapter calls a view only on the event dispatch thread. These rules apply to a view:
 *
 * <ul>
 *   <li>Read the properties only from the {@code props} of {@link #update(ComponentNode,
 *       NodeProps)}. Do not subscribe to the node, and do not use a {@code DataContext}.
 *   <li>Write the input of the user with the {@code WritableBinding} of a property, while the user
 *       edits. When the properties come back with the value that the control shows, do not touch
 *       the control.
 *   <li>Call a {@code NodeAction} in the Swing listener of the gesture, and not later.
 *   <li>Take colors, fonts and borders from the look and feel ({@code UIManager}).
 *   <li>Do not make the views of children. Give the child nodes to the {@code children} helper of
 *       the {@link ViewContext}.
 * </ul>
 */
public interface SwingComponentView {
  /**
   * Gets the Swing component of this view.
   *
   * @return the component. It is the same object for the life of the view.
   */
  JComponent component();

  /**
   * Shows the current properties. The adapter calls this method one time after the creation of the
   * view, and then after each change of the properties.
   *
   * <p>The node can be a different object in a subsequent call, for example after the agent sends
   * the component again. Thus do not keep the node. The type of the component is always the same.
   *
   * @param node the node of the component
   * @param props the current properties of the node
   */
  void update(ComponentNode<?> node, NodeProps<?> props);

  /**
   * Gets the Swing component that gets the accessibility attributes of the component. The adapter
   * gives the {@code @accessibility} attributes of the node to this component after each update:
   * the label is its accessible name, and the description is its accessible description. Do not set
   * these two values in the view. To give a default name, override {@link
   * #inferredAccessibleName()}.
   *
   * <p>The default implementation returns {@link #component()}. A view whose control is in a panel
   * returns the control, because the element that gets the focus must have the attributes.
   *
   * <p>The result is usually the same object for the life of the view. A view can return a
   * different component after an update, for example if a property selects a different control.
   * Then the adapter gives the attributes to the new component.
   *
   * @return the component
   */
  default JComponent accessibleComponent() {
    return component();
  }

  /**
   * Gets the accessible name that the view infers from its visible text, for a component that has
   * no {@code @accessibility} label. Examples are the text of the child of a button, and the label
   * of a text field. The adapter calls this method after each update of the view, and after each
   * update of a view below it.
   *
   * <p>The default implementation returns an empty result. Then the Swing component keeps the name
   * that Swing gives it, for example the text of a {@code JLabel}.
   *
   * @return the name, or an empty result if the view infers none
   */
  default Optional<String> inferredAccessibleName() {
    return Optional.empty();
  }

  /**
   * Releases the listeners, the timers and the other resources of this view. The adapter calls this
   * method one time, when the surface stops to show the component. The default implementation does
   * nothing.
   */
  default void dispose() {}

  /**
   * Makes a view from a Swing component and a function that updates it.
   *
   * @param component the Swing component of the view
   * @param update shows the current properties. See {@link #update(ComponentNode, NodeProps)}.
   * @return the view
   */
  static SwingComponentView of(
      JComponent component, BiConsumer<? super ComponentNode<?>, ? super NodeProps<?>> update) {
    return of(component, update, () -> {});
  }

  /**
   * Makes a view from a Swing component, a function that updates it and the work of its disposal.
   *
   * @param component the Swing component of the view
   * @param update shows the current properties. See {@link #update(ComponentNode, NodeProps)}.
   * @param dispose releases the resources of the view. See {@link #dispose()}.
   * @return the view
   */
  static SwingComponentView of(
      JComponent component,
      BiConsumer<? super ComponentNode<?>, ? super NodeProps<?>> update,
      Runnable dispose) {
    Objects.requireNonNull(component, "component");
    Objects.requireNonNull(update, "update");
    Objects.requireNonNull(dispose, "dispose");
    return new SwingComponentView() {
      @Override
      public JComponent component() {
        return component;
      }

      @Override
      public void update(ComponentNode<?> node, NodeProps<?> props) {
        update.accept(node, props);
      }

      @Override
      public void dispose() {
        dispose.run();
      }
    };
  }
}
