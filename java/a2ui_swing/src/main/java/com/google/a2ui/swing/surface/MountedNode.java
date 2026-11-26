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

package com.google.a2ui.swing.surface;

import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.swing.catalog.SwingComponentView;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JComponent;

/**
 * One node of the tree that an {@link A2uiSurface} shows, at the time of the snapshot. See {@link
 * A2uiSurface#viewTree()}.
 *
 * @param node the node of the core at this position
 * @param view the view of the node. It is empty if the node is not resolved.
 * @param component the Swing component at this position: the component of the view, or of a
 *     fallback. It is empty if the position shows nothing. Then the position uses no layout space.
 * @param children the positions of the children that the view shows through the {@code children}
 *     helpers of its context, in their sequence. A child that shows nothing is also in the list.
 *     The list is read-only.
 */
public record MountedNode(
    ComponentNode<?> node,
    Optional<SwingComponentView> view,
    Optional<JComponent> component,
    List<MountedNode> children) {
  /** Makes the snapshot of a position. */
  public MountedNode {
    Objects.requireNonNull(node, "node");
    Objects.requireNonNull(view, "view");
    Objects.requireNonNull(component, "component");
    Objects.requireNonNull(children, "children");
  }

  /**
   * Tells if the position uses layout space.
   *
   * @return true if the position has a Swing component
   */
  public boolean occupiesLayout() {
    return component.isPresent();
  }

  /**
   * Finds the first position of a component in this position and below it.
   *
   * @param componentId the ID of the component in the messages of the agent
   * @return the position, or an empty result if the tree does not show the component
   */
  public Optional<MountedNode> find(String componentId) {
    Objects.requireNonNull(componentId, "componentId");
    if (node.componentId().equals(componentId)) {
      return Optional.of(this);
    }
    for (MountedNode child : children) {
      Optional<MountedNode> found = child.find(componentId);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }
}
