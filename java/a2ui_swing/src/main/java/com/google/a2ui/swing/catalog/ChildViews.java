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
import java.awt.Container;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;

/**
 * Keeps the views of the children of one component in one Swing container. A container component
 * gets this helper from its {@link ViewContext} and gives it the child nodes in each update. The
 * helper does all the other work:
 *
 * <ul>
 *   <li>It keeps one view for each child position. The instance ID of the node names the position.
 *       Thus a child that stays keeps its Swing component and its state.
 *   <li>It moves the Swing component of a child whose position in the list changed.
 *   <li>It disposes the views of the children that left the list.
 *   <li>It puts no component in the container for a child that shows nothing, for example a
 *       component that did not arrive. Such a child gets no slot, no gap and no weight. If the
 *       surface has a fallback for the child, the component of the fallback is a usual child.
 *   <li>It changes the Swing container only where the list changed, and then makes the container
 *       invalid one time.
 * </ul>
 *
 * <p>The helper owns the content of its container. Do not add other components to that container.
 * The adapter disposes the helper together with the view. Use the helper only on the event dispatch
 * thread.
 */
public interface ChildViews {
  /**
   * Shows a list of children in the container, in the sequence of the list.
   *
   * @param children the child nodes, for example {@code props.children("children")}
   */
  void sync(List<? extends ComponentNode<?>> children);

  /**
   * Shows one child, or no child, in the container.
   *
   * @param child the child node, for example {@code props.child("child")}
   */
  void sync(Optional<? extends ComponentNode<?>> child);

  /**
   * Gets the Swing container that this helper fills.
   *
   * @return the container
   */
  Container container();

  /**
   * Gets the Swing components of the children that show something, in the sequence of the list.
   *
   * @return the components. The list is a read-only copy.
   */
  List<JComponent> components();

  /**
   * Gets the Swing component of one child.
   *
   * @param child a node of the list of the last call of {@code sync}
   * @return the component, or an empty result if the child shows nothing or is not in the list
   */
  Optional<JComponent> componentOf(ComponentNode<?> child);

  /**
   * Disposes the views of all the children and removes their components from the container. The
   * adapter calls this method when it disposes the view. A second call has no effect.
   */
  void dispose();
}
