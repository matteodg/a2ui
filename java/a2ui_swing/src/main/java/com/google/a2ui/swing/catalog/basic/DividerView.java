/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

/**
 * The view of the {@code Divider} component of the basic catalog: a {@code JSeparator}, which is
 * the line of the look and feel.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code axis}</td><td>{@code horizontal}, the default, is a line from the left side to
 *       the right side of the component. {@code vertical} is a line from the top to the bottom of
 *       the component. An unknown value gives a horizontal line.</td></tr>
 * </table>
 *
 * <p>The view fills the cross axis of its container, with each value of the {@code align} of the
 * container. Thus a horizontal divider in a {@code Column} has the width of the column, and a
 * vertical divider in a {@code Row} has the height of the row, as the implementation guide
 * specifies. The preferred length of the line is {@value #LENGTH} pixels. A divider has this length
 * when the container gives no more space in the direction of the line, for example a horizontal
 * divider in a {@code Row}. Thus a divider always shows.
 *
 * <p>The view has the margin of a leaf. Thus a line does not touch the text or the outline that is
 * adjacent to it. The line is in the center of the other direction.
 *
 * <p>The Swing component of the view is a panel that gives the margin. The accessibility attributes
 * go to the {@code JSeparator}, which has the accessible role of a separator.
 */
final class DividerView implements SwingComponentView {
  /** The preferred length of the line, in pixels. A container usually makes the line longer. */
  static final int LENGTH = 16;

  private final LeafPanel panel = new LeafPanel();
  private final Line line = new Line();

  /**
   * Makes the view.
   *
   * @param context the context of the view
   */
  DividerView(ViewContext context) {
    // The main axis of the panel is across the line. Thus the line fills the panel in its own
    // direction, and it is in the center of the other direction.
    panel.flexLayout().setJustify(FlexLayout.Justify.CENTER);
    panel.flexLayout().setAlign(FlexLayout.Align.STRETCH);
    panel.add(line);
    // The line has the size of its container, with each value of the align of the container.
    panel.putClientProperty(FlexLayout.STRETCH_PROPERTY, Boolean.TRUE);
    apply(SwingConstants.HORIZONTAL);
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public JComponent accessibleComponent() {
    return line;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    apply(
        FlexLayout.Axis.fromWire(props.value("axis")).orElse(FlexLayout.Axis.HORIZONTAL)
                == FlexLayout.Axis.VERTICAL
            ? SwingConstants.VERTICAL
            : SwingConstants.HORIZONTAL);
  }

  /** Gives an orientation to the line and to the layout of the panel. */
  private void apply(int orientation) {
    FlexLayout.Axis across =
        orientation == SwingConstants.VERTICAL
            ? FlexLayout.Axis.HORIZONTAL
            : FlexLayout.Axis.VERTICAL;
    if (line.getOrientation() == orientation && panel.flexLayout().axis() == across) {
      return;
    }
    line.setOrientation(orientation);
    panel.flexLayout().setAxis(across);
    panel.revalidate();
    panel.repaint();
  }

  /** The line of the view. It has a preferred length, and it can become as small as its line. */
  private static final class Line extends JSeparator {
    private static final long serialVersionUID = 1L;

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      if (isPreferredSizeSet()) {
        return size;
      }
      return getOrientation() == SwingConstants.VERTICAL
          ? new Dimension(Math.max(1, size.width), Math.max(size.height, LENGTH))
          : new Dimension(Math.max(size.width, LENGTH), Math.max(1, size.height));
    }

    /**
     * Gets the minimum size: the thickness of the line in each direction. The look and feel gives
     * no minimum size to a separator.
     */
    @Override
    public Dimension getMinimumSize() {
      if (isMinimumSizeSet()) {
        return super.getMinimumSize();
      }
      Dimension preferred = getPreferredSize();
      int thickness =
          getOrientation() == SwingConstants.VERTICAL ? preferred.width : preferred.height;
      return new Dimension(thickness, thickness);
    }
  }
}
