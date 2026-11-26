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
import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseWheelEvent;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * The view of the {@code List} component of the basic catalog: a {@link FlexPanel} in a {@code
 * JScrollPane} that scrolls in the direction of the list.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code children}</td><td>The children, in their sequence. A list of IDs and a template
 *       are the same for the view, because the core gives one node for each child. A child that
 *       stays in the list keeps its Swing component.</td></tr>
 *   <tr><td>{@code direction}</td><td>{@code vertical}, the default, puts each child below the
 *       child before it. {@code horizontal} puts the children side by side.</td></tr>
 *   <tr><td>{@code align}</td><td>The position of each child on the cross axis: {@code stretch},
 *       the default, {@code start}, {@code center} or {@code end}.</td></tr>
 * </table>
 *
 * <p>The list has no padding, no gap and no border, as the leaf margin strategy of the
 * implementation guide specifies. It has no background of its own.
 *
 * <p><b>Size and scroll.</b> The preferred size of the list is the size of its content. A vertical
 * list has the width that its parent gives it, and its text breaks at that width.
 *
 * <ul>
 *   <li>A parent that has no limit on the main axis gives the list its preferred size. Then the
 *       list shows all its children and has no scroll bar. This is the usual condition: a list in a
 *       {@code Column} of a surface that is in a scroll pane. The outer scroll pane scrolls the
 *       surface. A turn of the mouse wheel on the list goes to the outer scroll pane.
 *   <li>A parent that has a limit gives the list less than its preferred size. Then the list
 *       scrolls its content, and a scroll bar shows. Examples are a list that is the root of a
 *       surface in a window, and a list in a {@code Column} that is higher than its window. A list
 *       can become as small as {@value #MINIMUM_EXTENT} pixels on its main axis. When the list is
 *       at the end of its content, a turn of the mouse wheel goes to the outer scroll pane.
 * </ul>
 *
 * <p>A horizontal list gives each child its preferred width. Thus a long text in a horizontal list
 * has one line. The scroll bar of a horizontal list shows only when the content is wider than the
 * list, because a mouse cannot scroll a list that has no scroll bar.
 *
 * <p><b>Limit.</b> The list is not virtualized: each child has a view and a Swing component, also a
 * child that is not in the visible area. A list of some hundred children is satisfactory. Do not
 * use the list for some thousand children.
 *
 * <p>The Swing component of the view is the scroll pane. The accessibility attributes go to the
 * panel of the children, which has the accessible role of a list.
 */
final class ListView implements SwingComponentView {
  /** The least size of a list on its main axis, in pixels. */
  static final int MINIMUM_EXTENT = 48;

  /** The distance of one step of a scroll bar, in pixels. */
  private static final int SCROLL_UNIT = 16;

  private final Content content = new Content();
  private final Scroller scroller = new Scroller(content);
  private final ChildViews children;

  /**
   * Makes the view.
   *
   * @param context the context of the view
   */
  ListView(ViewContext context) {
    children = context.children(content);
    scroller.setAxis(FlexLayout.Axis.VERTICAL);
  }

  @Override
  public JComponent component() {
    return scroller;
  }

  @Override
  public JComponent accessibleComponent() {
    return content;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    scroller.setAxis(
        FlexLayout.Axis.fromWire(props.value("direction")).orElse(FlexLayout.Axis.VERTICAL));
    content
        .flexLayout()
        .setAlign(FlexLayout.Align.fromWire(props.value("align")).orElse(FlexLayout.Align.STRETCH));
    children.sync(props.children("children"));
    content.revalidate();
    scroller.repaint();
  }

  /** The panel of the children. It is the view of the viewport of the scroll pane. */
  private static final class Content extends FlexPanel implements Scrollable {
    private static final long serialVersionUID = 1L;

    Content() {
      super(FlexLayout.Axis.VERTICAL);
    }

    boolean vertical() {
      return flexLayout().axis() == FlexLayout.Axis.VERTICAL;
    }

    /** Gets the size of the content with no line breaks. */
    Dimension naturalSize() {
      return super.getPreferredSize();
    }

    /**
     * Gets the space that the scroll pane has for this panel when no scroll bar shows.
     *
     * @return the size, or null if the panel is not in the scroll pane of a list that has a size
     */
    private Dimension paneSpace() {
      if (!(getParent() instanceof JViewport viewport)
          || !(viewport.getParent() instanceof Scroller pane)
          || pane.getWidth() <= 0) {
        return null;
      }
      Insets insets = pane.getInsets();
      return new Dimension(
          Math.max(0, pane.getWidth() - insets.left - insets.right),
          Math.max(0, pane.getHeight() - insets.top - insets.bottom));
    }

    /**
     * Gets the preferred size. In the viewport of a vertical list, it is the size of the content at
     * the width that the scroll pane has for it: the full width if the content then is not higher
     * than the scroll pane, and the width without the scroll bar if it is higher.
     *
     * <p>This result, and the results of the two {@code getScrollableTracks} methods, come from the
     * size of the scroll pane, and not from the size of the viewport. The layout of a scroll pane
     * asks for them before it gives a size to the viewport. Thus one layout pass gives the correct
     * result, and the result does not depend on the layout before.
     */
    @Override
    public Dimension getPreferredSize() {
      Dimension natural = super.getPreferredSize();
      Dimension space = isPreferredSizeSet() || !vertical() ? null : paneSpace();
      if (space == null) {
        return natural;
      }
      int height = preferredHeightFor(space.width);
      if (height <= space.height) {
        return new Dimension(space.width, height);
      }
      JScrollPane pane = (JScrollPane) getParent().getParent();
      int narrow = Math.max(0, space.width - pane.getVerticalScrollBar().getPreferredSize().width);
      return new Dimension(narrow, preferredHeightFor(narrow));
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return naturalSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
      return SCROLL_UNIT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      int visible = orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
      return Math.max(SCROLL_UNIT, visible - SCROLL_UNIT);
    }

    /**
     * Tells if the panel has the width of the viewport. A vertical list always has it. Thus its
     * text breaks at the width of the list. A horizontal list has it only when the content is not
     * wider than the list.
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
      if (vertical()) {
        return true;
      }
      Dimension space = paneSpace();
      return space != null && naturalSize().width <= space.width;
    }

    /**
     * Tells if the panel has the height of the viewport. A horizontal list always has it. Thus its
     * children can fill the height of the list. A vertical list has it only when the content is not
     * higher than the list.
     */
    @Override
    public boolean getScrollableTracksViewportHeight() {
      if (!vertical()) {
        return true;
      }
      Dimension space = paneSpace();
      return space != null && preferredHeightFor(space.width) <= space.height;
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleList();
      }
      return accessibleContext;
    }

    /** The accessible context of the panel. It has the role of a list. */
    private final class AccessibleList extends AccessibleJPanel {
      private static final long serialVersionUID = 1L;

      @Override
      public AccessibleRole getAccessibleRole() {
        return AccessibleRole.LIST;
      }
    }
  }

  /** The scroll pane of the view. Its preferred height depends on its width. */
  private static final class Scroller extends JScrollPane implements HeightForWidth {
    private static final long serialVersionUID = 1L;

    private final Content content;

    Scroller(Content content) {
      super(content);
      this.content = content;
      // A border that is not from the look and feel stays when the look and feel changes.
      setBorder(BorderFactory.createEmptyBorder());
      setViewportBorder(null);
      setOpaque(false);
      getViewport().setOpaque(false);
    }

    /** Sets the direction of the list, and thus the direction of the scroll. */
    void setAxis(FlexLayout.Axis axis) {
      boolean vertical = axis == FlexLayout.Axis.VERTICAL;
      int verticalPolicy =
          vertical
              ? ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
              : ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER;
      int horizontalPolicy =
          vertical
              ? ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
              : ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
      if (content.flexLayout().axis() == axis
          && getVerticalScrollBarPolicy() == verticalPolicy
          && getHorizontalScrollBarPolicy() == horizontalPolicy) {
        return;
      }
      content.flexLayout().setAxis(axis);
      setVerticalScrollBarPolicy(verticalPolicy);
      setHorizontalScrollBarPolicy(horizontalPolicy);
      content.revalidate();
    }

    /**
     * Tells that this scroll pane is not the root of a layout. A change of the size of the content
     * changes the preferred size of the list. Thus the parent of the list must do its layout again.
     *
     * @return false
     */
    @Override
    public boolean isValidateRoot() {
      return false;
    }

    @Override
    public Dimension getPreferredSize() {
      if (isPreferredSizeSet()) {
        return super.getPreferredSize();
      }
      Dimension natural = content.naturalSize();
      Insets insets = getInsets();
      return new Dimension(
          natural.width + insets.left + insets.right, natural.height + insets.top + insets.bottom);
    }

    /**
     * Gets the minimum size. On the main axis, it is the smaller of the content and of {@link
     * #MINIMUM_EXTENT}. Thus a parent that has not sufficient space makes the list smaller, and the
     * list scrolls.
     */
    @Override
    public Dimension getMinimumSize() {
      if (isMinimumSizeSet()) {
        return super.getMinimumSize();
      }
      Dimension natural = content.naturalSize();
      Insets insets = getInsets();
      int width =
          content.vertical()
              ? Math.min(natural.width, content.getMinimumSize().width)
              : Math.min(natural.width, MINIMUM_EXTENT);
      int height = content.vertical() ? Math.min(natural.height, MINIMUM_EXTENT) : natural.height;
      return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
    }

    @Override
    public int preferredHeightFor(int width) {
      if (isPreferredSizeSet()) {
        return super.getPreferredSize().height;
      }
      Insets insets = getInsets();
      int inner = Math.max(0, width - insets.left - insets.right);
      int height;
      if (content.vertical()) {
        height = content.preferredHeightFor(inner);
      } else {
        Dimension natural = content.naturalSize();
        height = natural.height;
        if (natural.width > inner) {
          // The scroll bar shows below the children.
          height += getHorizontalScrollBar().getPreferredSize().height;
        }
      }
      return height + insets.top + insets.bottom;
    }

    /**
     * Handles a turn of the mouse wheel. If the list cannot scroll in the direction of the turn,
     * the event goes to the parent. Thus a list in a scroll pane does not stop the scroll of that
     * scroll pane.
     */
    @Override
    protected void processMouseWheelEvent(MouseWheelEvent event) {
      Container parent = getParent();
      if (parent != null && !canScroll(event)) {
        parent.dispatchEvent(SwingUtilities.convertMouseEvent(this, event, parent));
        return;
      }
      super.processMouseWheelEvent(event);
    }

    /** Tells if a turn of the mouse wheel can move the content of this scroll pane. */
    private boolean canScroll(MouseWheelEvent event) {
      JScrollBar vertical = getVerticalScrollBar();
      JScrollBar bar =
          event.isShiftDown() || !vertical.isVisible() ? getHorizontalScrollBar() : vertical;
      if (!isWheelScrollingEnabled() || bar == null || !bar.isVisible()) {
        return false;
      }
      return event.getWheelRotation() < 0
          ? bar.getValue() > bar.getMinimum()
          : bar.getValue() + bar.getVisibleAmount() < bar.getMaximum();
    }
  }
}
