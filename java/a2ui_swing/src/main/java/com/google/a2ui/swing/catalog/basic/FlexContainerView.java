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

import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.swing.catalog.ChildViews;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import javax.swing.JComponent;

/**
 * The view of the {@code Row} and of the {@code Column} components of the basic catalog: a {@link
 * FlexPanel} that shows the children in one line.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code children}</td><td>The children, in their sequence. A list of IDs and a template
 *       are the same for the view, because the core gives one node for each child.</td></tr>
 *   <tr><td>{@code justify}</td><td>The use of the free space on the main axis: {@code start}, the
 *       default, {@code center}, {@code end}, {@code spaceBetween}, {@code spaceAround}, {@code
 *       spaceEvenly} or {@code stretch}.</td></tr>
 *   <tr><td>{@code align}</td><td>The position of each child on the cross axis: {@code stretch},
 *       the default, {@code start}, {@code center} or {@code end}.</td></tr>
 * </table>
 *
 * <p>The property {@code weight} of a child is its layout constraint. The children that have a
 * weight share the space that the other children do not use, in proportion to their weights. Then
 * {@code justify} has no free space to use.
 *
 * <p>The panel has no padding, no gap and no background, as the leaf margin strategy of the
 * implementation guide specifies. The space between two children is the sum of their margins. A
 * child that shows nothing, for example a component that did not arrive, has no position in the
 * line.
 *
 * <p>The panel fills the space that its parent gives it. Its preferred width is the width of its
 * content with no line breaks. Thus a row in a window that is too small makes its text narrower,
 * and the text gets more lines.
 */
final class FlexContainerView implements SwingComponentView {
  private final FlexPanel panel;
  private final ChildViews children;

  /**
   * Makes the view.
   *
   * @param context the context of the view
   * @param axis the direction of the line: horizontal for a {@code Row}, vertical for a {@code
   *     Column}
   */
  FlexContainerView(ViewContext context, FlexLayout.Axis axis) {
    panel = new FlexPanel(axis);
    children = context.children(panel, FlexContainerView::weightOf);
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    FlexLayout layout = panel.flexLayout();
    layout.setJustify(
        FlexLayout.Justify.fromWire(props.value("justify")).orElse(FlexLayout.Justify.START));
    layout.setAlign(
        FlexLayout.Align.fromWire(props.value("align")).orElse(FlexLayout.Align.STRETCH));
    children.sync(props.children("children"));
    panel.revalidate();
    panel.repaint();
  }

  /**
   * Gets the layout constraint of a child: its weight.
   *
   * @param child the node of the child
   * @return the weight, or null if the child has no weight that a layout can use
   */
  static Double weightOf(ComponentNode<?> child) {
    return child.props().get().value("weight") instanceof Number number
            && Double.isFinite(number.doubleValue())
            && number.doubleValue() > 0
        ? number.doubleValue()
        : null;
  }
}
