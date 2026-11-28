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
import com.google.a2ui.core.resolution.NodeState;
import com.google.a2ui.swing.catalog.ChildViews;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import com.google.a2ui.swing.surface.ModalPresenter;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Objects;
import java.util.Optional;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The view of the {@code Modal} component of the basic catalog: the view of a trigger in the
 * surface, and content that the {@code modalPresenter} service shows above the surface.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code trigger}</td><td>The component that shows in the surface, usually a {@code
 *       Button}. An operation of the trigger opens the modal.</td></tr>
 *   <tr><td>{@code content}</td><td>The component that shows while the modal is open.</td></tr>
 * </table>
 *
 * <p><b>The trigger.</b> The view opens the modal for these operations:
 *
 * <ul>
 *   <li>The user operates a {@code Button} in the trigger, with the mouse or with the keyboard. The
 *       button does its own action first, and then the modal opens. Thus the agent gets the event
 *       of the button, and a function action has its user activation. A button that the user cannot
 *       operate, because a check fails, does not open the modal.
 *   <li>The user clicks a different part of the trigger, for example a {@code Text}. The view
 *       ignores the click if a check of the trigger component fails. For this operation, each Swing
 *       component of the trigger has a mouse listener of the view.
 *   <li>If the trigger has no button, the trigger gets the keyboard focus, and the Space key and
 *       the Enter key open the modal. Then the trigger has the accessible role of a button and an
 *       accessible action, and its accessible name is its visible text.
 * </ul>
 *
 * <p>A modal in the trigger of a different modal opens only itself.
 *
 * <p><b>The content.</b> The view makes the view of the content when the modal opens, and the
 * adapter disposes it when the modal closes. Thus a modal that is closed uses no resources for its
 * content, and each opening starts with the state of the data model. While the modal is open, the
 * content follows the data model and the messages of the agent as each other component does. The
 * presenter gives the user the control that closes the modal. The default presenter shows a dialog,
 * which the user closes with the close control of the window or with the Escape key.
 *
 * <p>If the presenter throws an exception, the view writes it to the log, and the modal stays
 * closed. The default presenter needs a display.
 *
 * <p>The Swing component of the view is the panel of the trigger. It has no margin and no padding
 * of its own. The accessibility attributes go to this panel.
 */
final class ModalView implements SwingComponentView {
  /**
   * The key of the client property of the panel of a trigger. The value is a {@code Runnable} that
   * opens the modal. The view of a button runs the nearest one above it after its own action.
   */
  static final String OPENER_PROPERTY = "a2ui.modal.opener";

  /** The key of the action of the panel of a trigger that opens the modal. */
  static final String OPEN_ACTION = "a2ui.modal.open";

  private static final System.Logger LOG = System.getLogger("com.google.a2ui.swing");

  private final ModalPresenter presenter;
  private final Trigger trigger = new Trigger();
  private final FlexPanel content = new FlexPanel(FlexLayout.Axis.VERTICAL);
  private final ChildViews triggerChild;
  private final ChildViews contentChild;

  /** The nodes of the newest properties. */
  private Optional<? extends ComponentNode<?>> triggerNode = Optional.empty();

  private Optional<? extends ComponentNode<?>> contentNode = Optional.empty();

  /** True while the presenter shows the content. */
  private boolean open;

  /** The handle of the presenter, or null. */
  private ModalPresenter.Handle handle;

  private boolean disposed;

  /**
   * Makes the view.
   *
   * @param context the context of the view. It gives the {@code modalPresenter} service.
   */
  ModalView(ViewContext context) {
    presenter = context.services().modalPresenter();
    // The content fills the area that the presenter gives it.
    content.flexLayout().setJustify(FlexLayout.Justify.STRETCH);
    triggerChild = context.children(trigger);
    // The helper of the content is the second one. Thus the content is after the trigger in the
    // tree of the views.
    contentChild = context.children(content);
    trigger.putClientProperty(OPENER_PROPERTY, (Runnable) this::open);
  }

  @Override
  public JComponent component() {
    return trigger;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    triggerNode = props.child("trigger");
    contentNode = props.child("content");
    triggerChild.sync(triggerNode);
    if (open) {
      contentChild.sync(contentNode);
      content.revalidate();
      content.repaint();
    }
    trigger.revalidate();
    trigger.repaint();
  }

  /** Gets the visible text of a trigger that has no button, as its name for assistive tools. */
  @Override
  public Optional<String> inferredAccessibleName() {
    if (!trigger.actsAsButton()) {
      return Optional.empty();
    }
    String text = VisibleText.of(trigger);
    return text.isEmpty() ? Optional.empty() : Optional.of(text);
  }

  @Override
  public void dispose() {
    disposed = true;
    trigger.putClientProperty(OPENER_PROPERTY, null);
    trigger.release();
    if (handle != null) {
      ModalPresenter.Handle closing = handle;
      handle = null;
      try {
        closing.close();
      } catch (RuntimeException e) {
        LOG.log(System.Logger.Level.ERROR, "The modal presenter failed to close a modal.", e);
      }
    }
    open = false;
  }

  /**
   * Runs the opener of the nearest trigger above a component. The view of a button calls this
   * method after its own action.
   *
   * @param source the Swing component that the user operated
   */
  static void activated(Component source) {
    for (Component above = source; above != null; above = above.getParent()) {
      if (above instanceof JComponent swing
          && swing.getClientProperty(OPENER_PROPERTY) instanceof Runnable opener) {
        opener.run();
        return;
      }
    }
  }

  /** Opens the modal for an operation of the trigger that is not the operation of a button. */
  private void activate() {
    boolean valid =
        triggerNode
            .filter(node -> node.state() == NodeState.RESOLVED)
            .map(node -> node.props().get().isValid())
            .orElse(false);
    if (valid) {
      open();
    }
  }

  /** Mounts the content and gives it to the presenter. */
  private void open() {
    if (open || disposed) {
      return;
    }
    open = true;
    contentChild.sync(contentNode);
    ModalPresenter.Handle presented;
    try {
      presented =
          Objects.requireNonNull(
              presenter.present(trigger, content, this::closed), "present returned null");
    } catch (RuntimeException e) {
      LOG.log(System.Logger.Level.ERROR, "The modal presenter did not show the modal.", e);
      closed();
      return;
    }
    // A presenter can close the content before it returns.
    if (open) {
      handle = presented;
    }
  }

  /** Disposes the view of the content after the user closed the modal. */
  private void closed() {
    if (!open) {
      return;
    }
    open = false;
    handle = null;
    if (!disposed) {
      contentChild.sync(Optional.empty());
    }
  }

  /** Tells if a component, or a component below it, is a button that shows. */
  private static boolean hasButton(Component component) {
    if (!component.isVisible()) {
      return false;
    }
    if (component instanceof AbstractButton) {
      return true;
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        if (hasButton(child)) {
          return true;
        }
      }
    }
    return false;
  }

  /** The panel that has the view of the trigger. It finds the operations that open the modal. */
  private final class Trigger extends FlexPanel {
    private static final long serialVersionUID = 1L;

    /**
     * Opens the modal for a click. Each component of the trigger has this listener. A listener of
     * the panel only is not sufficient: AWT gives a click only to the deepest component that has a
     * mouse listener, for example the text component of a {@code Text}.
     */
    private final transient MouseListener clicks =
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            if (SwingUtilities.isLeftMouseButton(event)) {
              activate();
            }
          }
        };

    /** Gives the listener of the clicks to each component that comes to the trigger. */
    private final transient ContainerListener tracker =
        new ContainerListener() {
          @Override
          public void componentAdded(ContainerEvent event) {
            watch(event.getChild());
          }

          @Override
          public void componentRemoved(ContainerEvent event) {
            unwatch(event.getChild());
          }
        };

    private final transient FocusListener focus =
        new FocusListener() {
          @Override
          public void focusGained(FocusEvent event) {
            repaint();
          }

          @Override
          public void focusLost(FocusEvent event) {
            repaint();
          }
        };

    Trigger() {
      super(FlexLayout.Axis.VERTICAL);
      // The trigger fills the panel.
      flexLayout().setJustify(FlexLayout.Justify.STRETCH);
      // The keys of a button. A panel that has key bindings is a stop of the Tab key.
      getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), OPEN_ACTION);
      getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), OPEN_ACTION);
      getActionMap()
          .put(
              OPEN_ACTION,
              new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent event) {
                  if (actsAsButton()) {
                    activate();
                  }
                }
              });
      addFocusListener(focus);
      watch(this);
    }

    /**
     * Starts to listen for the clicks on a component and on the components below it. The method
     * ignores a button, because a click on a button is the operation of the button. It ignores the
     * trigger of a different modal, because a click in it is for that modal.
     */
    private void watch(Component component) {
      if (component instanceof AbstractButton
          || component != this
              && component instanceof JComponent swing
              && swing.getClientProperty(OPENER_PROPERTY) != null) {
        return;
      }
      component.addMouseListener(clicks);
      if (component instanceof Container container) {
        container.addContainerListener(tracker);
        for (Component child : container.getComponents()) {
          watch(child);
        }
      }
    }

    /** Stops to listen for the clicks on a component and on the components below it. */
    private void unwatch(Component component) {
      component.removeMouseListener(clicks);
      if (component instanceof Container container) {
        container.removeContainerListener(tracker);
        for (Component child : container.getComponents()) {
          unwatch(child);
        }
      }
    }

    /**
     * Tells if the panel operates as a button: the trigger has no button of its own. Then the panel
     * gets the keyboard focus and has the accessible role of a button.
     */
    boolean actsAsButton() {
      return getComponentCount() > 0 && !hasButton(this);
    }

    /** Removes the listeners of the panel. */
    void release() {
      removeFocusListener(focus);
      unwatch(this);
    }

    @Override
    public boolean isFocusable() {
      return super.isFocusable() && actsAsButton();
    }

    /** Paints the content, and then a line around a trigger that has the keyboard focus. */
    @Override
    public void paint(Graphics graphics) {
      super.paint(graphics);
      if (!isFocusOwner() || !actsAsButton()) {
        return;
      }
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.setColor(BasicStyle.link(this));
        g.setStroke(
            new BasicStroke(
                1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[] {2f, 2f}, 0f));
        g.drawRect(2, 2, getWidth() - 5, getHeight() - 5);
      } finally {
        g.dispose();
      }
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleTrigger();
      }
      return accessibleContext;
    }

    /** The accessible context of the panel. A trigger with no button is a button. */
    private final class AccessibleTrigger extends AccessibleJPanel implements AccessibleAction {
      private static final long serialVersionUID = 1L;

      @Override
      public AccessibleRole getAccessibleRole() {
        return actsAsButton() ? AccessibleRole.PUSH_BUTTON : AccessibleRole.PANEL;
      }

      @Override
      public AccessibleAction getAccessibleAction() {
        return actsAsButton() ? this : null;
      }

      @Override
      public int getAccessibleActionCount() {
        return 1;
      }

      @Override
      public String getAccessibleActionDescription(int index) {
        return index == 0 ? UIManager.getString("AbstractButton.clickText") : null;
      }

      @Override
      public boolean doAccessibleAction(int index) {
        if (index != 0 || !actsAsButton()) {
          return false;
        }
        activate();
        return true;
      }
    }
  }
}
