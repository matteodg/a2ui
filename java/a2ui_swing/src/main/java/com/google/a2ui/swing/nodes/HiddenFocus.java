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

package com.google.a2ui.swing.nodes;

import java.awt.Component;
import java.awt.Container;
import java.awt.FocusTraversalPolicy;
import java.awt.KeyboardFocusManager;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Keeps the keyboard focus and the mouse out of the view of a node that the attribute {@code
 * hidden} hides from assistive tools. Each node host has one instance.
 *
 * <p>A control that assistive tools ignore must not get the keyboard focus. If it did, a user of a
 * screen reader would be on a control that has no name. The accessibility suite has the rule {@code
 * aria-hidden-focus} for this, and the web renderers make a hidden part {@code inert}: the user
 * cannot operate it.
 *
 * <p>While the view is hidden, this object does four things:
 *
 * <ul>
 *   <li>An {@link InputShield} takes the mouse events of the root component of the view. Thus a
 *       press, a click or a turn of the wheel reaches no component of the view and no component
 *       below it: no action runs and no control takes the focus.
 *   <li>The root component of the view gives a focus traversal policy that has no components. Thus
 *       the Tab key goes past the view and past all the components below it, also the views of the
 *       nodes below. This is correct in each window: the surface, and the dialog of a modal.
 *   <li>The root component and the control of the view are not focusable.
 *   <li>If a component of the view has the focus when the view becomes hidden, the focus goes to
 *       the subsequent component.
 * </ul>
 *
 * <p>A key event goes to the component that has the focus. No component of the hidden view can get
 * the focus from the user, thus no key event reaches the view.
 *
 * <p>The object puts each value back when the view is not hidden, and when the host disposes the
 * view.
 */
final class HiddenFocus {
  /** A policy that gives no component. The policy of the window then continues after the view. */
  private static final FocusTraversalPolicy NO_COMPONENTS =
      new FocusTraversalPolicy() {
        @Override
        public Component getComponentAfter(Container container, Component component) {
          return null;
        }

        @Override
        public Component getComponentBefore(Container container, Component component) {
          return null;
        }

        @Override
        public Component getFirstComponent(Container container) {
          return null;
        }

        @Override
        public Component getLastComponent(Container container) {
          return null;
        }

        @Override
        public Component getDefaultComponent(Container container) {
          return null;
        }
      };

  /** The root component and the control of the view while the view is hidden, or null. */
  private JComponent root;

  private JComponent control;

  /** Takes the mouse events of the root component while the view is hidden, or null. */
  private InputShield shield;

  /** The values of the components before this object changed them. */
  private boolean rootWasProvider;

  private FocusTraversalPolicy rootPolicy;
  private boolean rootWasFocusable;
  private boolean controlWasFocusable;

  /**
   * Tells if this object keeps the focus out of a view at this time.
   *
   * @return true while the view is hidden
   */
  boolean active() {
    return root != null;
  }

  /**
   * Applies the state of a view.
   *
   * @param viewRoot the root component of the view
   * @param viewControl the component of the view that has the accessibility attributes
   * @param hidden true if the attribute {@code hidden} hides the view
   */
  void apply(JComponent viewRoot, JComponent viewControl, boolean hidden) {
    if (root != null && (!hidden || root != viewRoot || control != viewControl)) {
      restore();
    }
    if (!hidden || root != null) {
      return;
    }
    root = viewRoot;
    control = viewControl;
    rootWasProvider = root.isFocusTraversalPolicyProvider();
    rootPolicy = root.isFocusTraversalPolicySet() ? root.getFocusTraversalPolicy() : null;
    rootWasFocusable = root.isFocusable();
    controlWasFocusable = control.isFocusable();

    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    boolean hadFocus = owner != null && SwingUtilities.isDescendingFrom(owner, root);
    root.setFocusTraversalPolicy(NO_COMPONENTS);
    root.setFocusTraversalPolicyProvider(true);
    root.setFocusable(false);
    control.setFocusable(false);
    shield = new InputShield(root);
    shield.install();
    if (hadFocus) {
      // A component that is not focusable does not keep the focus in each case. This call makes
      // sure that the focus goes to the component after the view.
      KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(root);
    }
  }

  /** Puts each value back, as for a view that is not hidden. A second call has no effect. */
  void dispose() {
    if (root != null) {
      restore();
    }
  }

  private void restore() {
    shield.uninstall();
    shield = null;
    root.setFocusTraversalPolicy(rootPolicy);
    root.setFocusTraversalPolicyProvider(rootWasProvider);
    control.setFocusable(controlWasFocusable);
    root.setFocusable(rootWasFocusable);
    root = null;
    control = null;
    rootPolicy = null;
  }
}
