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
import com.google.a2ui.swing.catalog.ChildViews;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JComponent;

/**
 * The view of the {@code Card} component of the basic catalog: a panel with a rounded outline that
 * groups the view of one child.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code child}</td><td>The component that the card shows. To show more than one
 *       component, the agent gives a {@code Column} or a {@code Row} as the child. A child that did
 *       not arrive shows nothing, and the card stays.</td></tr>
 * </table>
 *
 * <p>The card has the margin of a control with an outline, as the leaf margin strategy of the
 * implementation guide specifies. The outline has rounded corners and the border color of the look
 * and feel. The card has no background of its own. Thus the background of the surface shows, and
 * the card is correct in each look and feel.
 *
 * <p>The padding between the outline and the child is {@value #PADDING} pixels. A leaf in the card
 * has its own margin of 8 pixels. Thus the space between the outline and a text is 16 pixels, which
 * is the padding that the implementation guide recommends.
 *
 * <p>The outline fills the space that the parent gives to the card. The preferred height of the
 * card depends on its width, as that of the other containers. Thus text in a card breaks at the
 * width of the card.
 *
 * <p>The Swing component of the view is the panel that gives the margin. The accessibility
 * attributes go to this panel. The card has no accessible name of its own: an assistive tool reads
 * the content.
 */
final class CardView implements SwingComponentView {
  /** The space between the outline and the child, in pixels. */
  static final int PADDING = 8;

  /** The diameter of the corners of the outline, in pixels. */
  static final int ARC = 12;

  private final LeafPanel panel = new LeafPanel();
  private final Body body = new Body();
  private final ChildViews child;

  /**
   * Makes the view.
   *
   * @param context the context of the view
   */
  CardView(ViewContext context) {
    // The outline fills the panel. Thus two cards in a row have the same height.
    panel.flexLayout().setJustify(FlexLayout.Justify.STRETCH);
    panel.add(body);
    child = context.children(body);
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    child.sync(props.child("child"));
    panel.revalidate();
    panel.repaint();
  }

  /** The panel that paints the outline and has the view of the child. */
  private static final class Body extends FlexPanel {
    private static final long serialVersionUID = 1L;

    Body() {
      super(FlexLayout.Axis.VERTICAL);
      // The child fills the area in the outline.
      flexLayout().setJustify(FlexLayout.Justify.STRETCH);
      // One more pixel is for the line of the outline.
      int inset = PADDING + 1;
      setBorder(BorderFactory.createEmptyBorder(inset, inset, inset, inset));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BasicStyle.outline(this));
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
      } finally {
        g.dispose();
      }
    }
  }
}
