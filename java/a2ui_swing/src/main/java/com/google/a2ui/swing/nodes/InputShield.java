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

package com.google.a2ui.swing.nodes;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * A component that paints nothing and takes the mouse events of one other component: the covered
 * component. It keeps the mouse out of the view of a node that the attribute {@code hidden} hides.
 *
 * <p>The shield is not above the covered component in the tree of Swing components. A component
 * between a view and its parent would change the component of the view, its layout and its
 * identity. The shield is in the layered pane of the root pane that has the covered component, in
 * the same layer and in front. A window, a dialog and an internal frame each have a root pane.
 *
 * <p>AWT gives a mouse event to the frontmost component that contains the point of the event and
 * that has a mouse listener. The shield has listeners that do nothing, and its {@link
 * #contains(int, int)} is true for each point in the part of the covered component that its parents
 * show. The method calculates that part for each event. Thus the shield has no bounds to keep
 * correct, and it is correct after a layout, after a scroll, and when a tab hides the covered
 * component.
 *
 * <p>Thus a press, a click, a move and a turn of the wheel at a point of the covered component go
 * to the shield. No component below gets them: no action runs, no control takes the focus, no
 * rollover shows, and no tool tip shows. The shield gives a turn of the wheel to the nearest
 * component above the hidden part that listens for the wheel. Thus a list scrolls also when the
 * pointer is on a hidden item.
 *
 * <p>The shield follows the covered component when a parent of that component changes. It has no
 * effect while the covered component is in no root pane. Code that sends an event directly to a
 * covered component, for example {@code doClick}, still operates that component.
 *
 * <p>The shield never changes a layered pane in the event that asks for the change. AWT goes
 * through the components of a layered pane when a window opens and when it closes, and a view can
 * come or go at that time. A change of the list in that loop is an error. Thus the shield goes into
 * its layered pane, and out of it, in a later event of the event dispatch thread. A shield that
 * {@link #uninstall()} stopped takes no event from that moment, also while it is in the pane.
 */
final class InputShield extends JComponent {
  private static final long serialVersionUID = 1L;

  /** The key of the client property of a covered component. The value is its shield. */
  private static final String SHIELD_PROPERTY = "a2ui.inputShield";

  private final JComponent covered;

  /** True between {@link #install()} and {@link #uninstall()}. */
  private boolean installed;

  /** True while a later event waits to put the shield in its place. */
  private boolean scheduled;

  /** Moves the shield when the covered component gets a different parent, at each level. */
  private final transient HierarchyListener follower =
      event -> {
        if ((event.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
          schedule();
        }
      };

  /**
   * Makes a shield. It has no effect before {@link #install()}.
   *
   * @param covered the component whose mouse events the shield takes
   */
  InputShield(JComponent covered) {
    this.covered = covered;
    setFocusable(false);
    // AWT gives mouse events only to a component that has a listener for them.
    addMouseListener(new MouseAdapter() {});
    addMouseMotionListener(new MouseMotionAdapter() {});
    addMouseWheelListener(this::forwardWheel);
  }

  /**
   * Starts to take the mouse events of the covered component. The shield takes them after the
   * subsequent event of the event dispatch thread.
   */
  void install() {
    installed = true;
    covered.putClientProperty(SHIELD_PROPERTY, this);
    covered.addHierarchyListener(follower);
    schedule();
  }

  /**
   * Stops to take the mouse events, at once, and removes each change that the shield made. The
   * shield goes out of its layered pane in a later event.
   */
  void uninstall() {
    installed = false;
    covered.removeHierarchyListener(follower);
    if (covered.getClientProperty(SHIELD_PROPERTY) == this) {
      covered.putClientProperty(SHIELD_PROPERTY, null);
    }
    schedule();
  }

  /** Asks for one later event that puts the shield in its place. */
  private void schedule() {
    if (scheduled) {
      return;
    }
    scheduled = true;
    SwingUtilities.invokeLater(() -> {
      scheduled = false;
      if (installed) {
        attach();
      } else {
        detach();
      }
    });
  }

  /** Puts the shield in the layered pane of the covered component, or removes it. */
  private void attach() {
    JRootPane rootPane = SwingUtilities.getRootPane(covered);
    JLayeredPane pane = rootPane == null ? null : rootPane.getLayeredPane();
    Component top = pane == null ? null : childAbove(pane);
    if (top == null) {
      detach();
      return;
    }
    int layer = pane.getLayer(top);
    if (getParent() == pane && JLayeredPane.getLayer(this) == layer) {
      return;
    }
    detach();
    // Position 0 is the front of the layer.
    pane.add(this, Integer.valueOf(layer), 0);
  }

  private void detach() {
    Container parent = getParent();
    if (parent != null) {
      parent.remove(this);
    }
  }

  /** Gets the component of the layered pane that has the covered component, or null. */
  private Component childAbove(JLayeredPane pane) {
    for (Component c = covered; c != null; c = c.getParent()) {
      if (c.getParent() == pane) {
        return c;
      }
    }
    return null;
  }

  /**
   * Tells if a point is in the part of the covered component that its parents show.
   *
   * @param x the horizontal position, in the coordinates of the shield
   * @param y the vertical position, in the coordinates of the shield
   */
  @Override
  public boolean contains(int x, int y) {
    Rectangle area = installed ? coveredArea() : null;
    return area != null && area.contains(x + getX(), y + getY());
  }

  /**
   * Gets the part of the covered component that its parents show, in the coordinates of the layered
   * pane. The result is null if a parent hides the covered component or cuts it off fully.
   */
  private Rectangle coveredArea() {
    Container pane = getParent();
    if (pane == null) {
      return null;
    }
    Rectangle area = new Rectangle(0, 0, covered.getWidth(), covered.getHeight());
    for (Component c = covered; c != pane; c = c.getParent()) {
      Container parent = c.getParent();
      if (parent == null || !c.isVisible()) {
        return null;
      }
      area.translate(c.getX(), c.getY());
      area = area.intersection(new Rectangle(0, 0, parent.getWidth(), parent.getHeight()));
      if (area.isEmpty()) {
        return null;
      }
    }
    return area;
  }

  /**
   * Gives a turn of the mouse wheel to the nearest component above the hidden part that listens for
   * the wheel, as AWT does for a component that does not listen for it.
   */
  private void forwardWheel(MouseWheelEvent event) {
    Container pane = getParent();
    // A covered component can be in a covered component. The event goes above all of them.
    Component outermost = covered;
    for (Component c = covered.getParent(); c != null && c != pane; c = c.getParent()) {
      if (c instanceof JComponent swing && swing.getClientProperty(SHIELD_PROPERTY) != null) {
        outermost = c;
      }
    }
    for (Component c = outermost.getParent(); c != null && c != pane; c = c.getParent()) {
      if (c.getMouseWheelListeners().length > 0) {
        c.dispatchEvent(SwingUtilities.convertMouseEvent(this, event, c));
        return;
      }
    }
  }
}
