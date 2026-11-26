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

package com.google.a2ui.swing.testing;

import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * Sends the mouse events and the key events of a user to the components of a root pane that is in
 * no window. Use it on the event dispatch thread.
 *
 * <p>A window finds the component that gets a mouse event from the position of the pointer. A test
 * with no display has no window. Thus this class finds the component with the rule of AWT: the
 * frontmost component that is visible, that contains the point and that listens for mouse events.
 * Then it sends real events to that component. A component that is covered by a different component
 * thus gets no event, as on the screen.
 *
 * <p>A window gives a key event to the component that has the keyboard focus. A component in no
 * window cannot have the focus. Thus {@link #clickAndType(Component, int, char)} does what a user
 * does: a click, and then the keys if the click reached a component that can take the focus.
 */
public final class Pointer {
  private Pointer() {}

  /**
   * Finds the component that gets a mouse event at a point of a root pane.
   *
   * @param rootPane the root pane
   * @param x the horizontal position in the root pane
   * @param y the vertical position in the root pane
   * @return the component, or null if no component at the point listens for mouse events
   */
  public static Component target(JRootPane rootPane, int x, int y) {
    return target((Container) rootPane, x, y);
  }

  /** Does the search of {@code java.awt.Container.getMouseEventTarget}. */
  private static Component target(Container container, int x, int y) {
    for (Component child : container.getComponents()) {
      int childX = x - child.getX();
      int childY = y - child.getY();
      if (!child.isVisible() || !child.contains(childX, childY)) {
        continue;
      }
      if (child instanceof Container below) {
        Component deeper = target(below, childX, childY);
        if (deeper != null) {
          return deeper;
        }
      } else if (listens(child)) {
        return child;
      }
    }
    return container.contains(x, y) && listens(container) ? container : null;
  }

  private static boolean listens(Component component) {
    return component.getMouseListeners().length > 0
        || component.getMouseMotionListeners().length > 0
        || component.getMouseWheelListeners().length > 0;
  }

  /**
   * Finds the component that gets a mouse event at the center of a component.
   *
   * @param component a component in a root pane
   * @return the component that gets the event, or null
   */
  public static Component targetAt(Component component) {
    return targetAt(component, component.getWidth() / 2, component.getHeight() / 2);
  }

  private static Component targetAt(Component component, int x, int y) {
    JRootPane rootPane = SwingUtilities.getRootPane(component);
    Point point = SwingUtilities.convertPoint(component, x, y, rootPane);
    return target(rootPane, point.x, point.y);
  }

  /**
   * Presses, releases and clicks the first mouse button at the center of a component.
   *
   * @param component a component in a root pane
   * @return the component that got the events, or null if no component got them
   */
  public static Component click(Component component) {
    return click(component, component.getWidth() / 2, component.getHeight() / 2);
  }

  /**
   * Presses, releases and clicks the first mouse button at a point of a component.
   *
   * @param component a component in a root pane
   * @param x the horizontal position in the component
   * @param y the vertical position in the component
   * @return the component that got the events, or null if no component got them
   */
  public static Component click(Component component, int x, int y) {
    Component target = targetAt(component, x, y);
    if (target == null) {
      return null;
    }
    Point point = SwingUtilities.convertPoint(component, x, y, target);
    target.dispatchEvent(mouse(target, MouseEvent.MOUSE_MOVED, point, 0, 0));
    target.dispatchEvent(
        mouse(target, MouseEvent.MOUSE_PRESSED, point, MouseEvent.BUTTON1_DOWN_MASK, 1));
    target.dispatchEvent(mouse(target, MouseEvent.MOUSE_RELEASED, point, 0, 1));
    target.dispatchEvent(mouse(target, MouseEvent.MOUSE_CLICKED, point, 0, 1));
    return target;
  }

  private static MouseEvent mouse(
      Component target, int id, Point point, int modifiers, int clickCount) {
    return new MouseEvent(
        target,
        id,
        System.currentTimeMillis(),
        modifiers,
        point.x,
        point.y,
        clickCount,
        false,
        clickCount == 0 ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1);
  }

  /**
   * Turns the mouse wheel at the center of a component. If the component that gets the event does
   * not listen for the wheel, the event goes to the nearest component above it that listens, as in
   * a window.
   *
   * @param component a component in a root pane
   * @param rotation the number of steps. A positive number scrolls down.
   * @return the component that got the event, or null if no component got it
   */
  public static Component wheel(Component component, int rotation) {
    Component target = targetAt(component);
    while (target != null && target.getMouseWheelListeners().length == 0) {
      target = target.getParent();
    }
    if (target == null) {
      return null;
    }
    Point point =
        SwingUtilities.convertPoint(
            component, component.getWidth() / 2, component.getHeight() / 2, target);
    target.dispatchEvent(
        new MouseWheelEvent(
            target,
            MouseEvent.MOUSE_WHEEL,
            System.currentTimeMillis(),
            0,
            point.x,
            point.y,
            0,
            false,
            MouseWheelEvent.WHEEL_UNIT_SCROLL,
            3,
            rotation));
    return target;
  }

  /**
   * Clicks at the center of a component and then presses, types and releases one key.
   *
   * <p>The key events go to the component that the click reached, as the keyboard focus does after
   * a click. They go nowhere if the click did not reach the component or a component in it, or if
   * the component that the click reached cannot take the focus.
   *
   * @param component a component in a root pane
   * @param keyCode the code of the key, for example {@code KeyEvent.VK_SPACE}
   * @param character the character of the key, or {@code KeyEvent.CHAR_UNDEFINED}
   * @return true if a component got the key events
   */
  public static boolean clickAndType(Component component, int keyCode, char character) {
    Component target = click(component);
    if (target == null
        || !SwingUtilities.isDescendingFrom(target, component)
        || !target.isFocusable()) {
      return false;
    }
    long now = System.currentTimeMillis();
    key(new KeyEvent(target, KeyEvent.KEY_PRESSED, now, 0, keyCode, character));
    if (character != KeyEvent.CHAR_UNDEFINED) {
      key(new KeyEvent(target, KeyEvent.KEY_TYPED, now, 0, KeyEvent.VK_UNDEFINED, character));
    }
    key(new KeyEvent(target, KeyEvent.KEY_RELEASED, now, 0, keyCode, character));
    return true;
  }

  /**
   * Gives a key event to its component as the keyboard focus manager gives it to the component that
   * has the focus. The manager gives no key event to a component that is in no window.
   */
  private static void key(KeyEvent event) {
    KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .redispatchEvent(event.getComponent(), event);
  }
}
