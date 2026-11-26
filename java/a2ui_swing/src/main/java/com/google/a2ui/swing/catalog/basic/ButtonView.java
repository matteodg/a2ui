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
import com.google.a2ui.core.resolution.NodeAction;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.core.resolution.ValidationResult;
import com.google.a2ui.swing.catalog.ChildViews;
import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.BorderUIResource;

/**
 * The view of the {@code Button} component of the basic catalog: a {@code JButton} that shows the
 * view of one child and does an action.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code child}</td><td>The component that the button shows, usually a {@code Text}. The
 *       text in a button has no margin, and it has the text color of the button.</td></tr>
 *   <tr><td>{@code action}</td><td>The action that the button does when the user operates it. The
 *       view calls the action in the Swing listener of the gesture, and not later. Thus the action
 *       has the user activation that a function such as {@code openUrl} needs.</td></tr>
 *   <tr><td>{@code variant}</td><td>{@code default} is the button of the look and feel. {@code
 *       primary} has the accent color of the look and feel as its background, and a text color with
 *       contrast. {@code borderless} has no background and no border, and its text has the color of
 *       a link.</td></tr>
 *   <tr><td>{@code checks}</td><td>The user cannot operate the button while a check of the severity
 *       {@code error} fails. The messages of all the failed checks are the tool tip of the button,
 *       which is also its accessible description if the agent gave none.</td></tr>
 * </table>
 *
 * <p>The view paints the {@code primary} and the {@code borderless} variants itself, because not
 * each look and feel lets code set the background of a button. The colors come from the look and
 * feel. The three variants have the same size.
 *
 * <p>The mouse events on the content of the button go to the button. The accessible name of the
 * button is the visible text of its child, unless the agent gave a label.
 *
 * <p>The Swing component of the view is a panel that gives the margin of a control. The {@code
 * JButton} fills the panel on the horizontal axis. The accessibility attributes go to the {@code
 * JButton}.
 */
final class ButtonView implements SwingComponentView {
  /** The values of the property {@code variant}. */
  enum Variant {
    /** The button of the look and feel. */
    DEFAULT,
    /** The main button of a surface. */
    PRIMARY,
    /** A button that looks as a link. */
    BORDERLESS;

    /** Gets the variant for a value of the property. An unknown value gives {@link #DEFAULT}. */
    static Variant fromWire(Object value) {
      if ("primary".equals(value)) {
        return PRIMARY;
      }
      return "borderless".equals(value) ? BORDERLESS : DEFAULT;
    }
  }

  private final LeafPanel panel = new LeafPanel();
  private final ActionButton button = new ActionButton();
  private final Content content = new Content();
  private final ChildViews child;

  /** The action of the newest properties, or null if the component has none. */
  private NodeAction action;

  private final ActionListener listener =
      event -> {
        // The view does the action in the listener of the gesture. Thus the user activation of
        // the gesture is present.
        if (action != null) {
          action.invoke();
        }
      };

  /**
   * Makes the view.
   *
   * @param context the context of the view
   */
  ButtonView(ViewContext context) {
    // The button has its own padding. Thus the text in it has no margin.
    content.putClientProperty(BasicStyle.LEAF_MARGIN_PROPERTY, 0);
    button.add(content);
    button.content = content;
    panel.add(button);
    child = context.children(content);
    button.addActionListener(listener);
    button.restyle();
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public JComponent accessibleComponent() {
    return button;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    action = props.action("action").orElse(null);
    button.setVariant(Variant.fromWire(props.value("variant")));
    button.setEnabled(props.isValid());
    button.setToolTipText(toolTip(props.validationResults()));
    button.restyle();
    child.sync(props.child("child"));
    panel.revalidate();
    panel.repaint();
  }

  /** Gets the visible text of the child, as the name of the button for assistive tools. */
  @Override
  public Optional<String> inferredAccessibleName() {
    String text = VisibleText.of(content);
    return text.isEmpty() ? Optional.empty() : Optional.of(text);
  }

  @Override
  public void dispose() {
    button.removeActionListener(listener);
    button.setToolTipText(null);
    action = null;
  }

  /** Makes the tool tip for the failed checks, or null if no check failed. */
  private static String toolTip(List<ValidationResult> failed) {
    List<String> messages = new ArrayList<>();
    for (ValidationResult result : failed) {
      result.message().filter(message -> !message.isBlank()).ifPresent(messages::add);
    }
    if (messages.isEmpty()) {
      return null;
    }
    if (messages.size() == 1) {
      return messages.getFirst();
    }
    // A tool tip has more than one line only as HTML.
    StringBuilder html = new StringBuilder("<html>");
    for (int i = 0; i < messages.size(); i++) {
      html.append(i == 0 ? "" : "<br>");
      html.append(messages.get(i).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
    }
    return html.append("</html>").toString();
  }

  /** The panel in the button that has the view of the child. */
  private static final class Content extends FlexPanel {
    private static final long serialVersionUID = 1L;

    Content() {
      super(
          new FlexLayout(
              FlexLayout.Axis.HORIZONTAL, FlexLayout.Justify.CENTER, FlexLayout.Align.CENTER, 0));
    }

    /**
     * Tells that no point is in the panel. Thus AWT gives the mouse events on the content to the
     * button, also if a component of the content has a mouse listener.
     */
    @Override
    public boolean contains(int x, int y) {
      return false;
    }

    /**
     * Applies the look and feel. The panel then has no text color of its own. Thus the button gives
     * the text color of its variant to the panel again. Swing applies a new look and feel to a
     * button before it applies it to the content of the button.
     */
    @Override
    public void updateUI() {
      super.updateUI();
      if (getParent() instanceof ActionButton button) {
        button.restyle();
      }
    }
  }

  /** The button of the view. It has a layout for its content and paints two of the variants. */
  private static final class ActionButton extends JButton implements HeightForWidth {
    private static final long serialVersionUID = 1L;

    /** The radius of the corners of the variants that the button paints. */
    private static final int ARC = 8;

    /** The variant. It is null while the constructor of {@code JButton} runs. */
    private Variant variant = Variant.DEFAULT;

    private Content content;

    ActionButton() {
      setLayout(
          new FlexLayout(
              FlexLayout.Axis.HORIZONTAL, FlexLayout.Justify.CENTER, FlexLayout.Align.CENTER, 0));
      setRolloverEnabled(true);
    }

    void setVariant(Variant next) {
      if (next == variant) {
        return;
      }
      variant = next;
      updateUI();
    }

    /** Applies the look and feel, and then the parts of the variant that replace it. */
    @Override
    public void updateUI() {
      if (variant == null) {
        super.updateUI();
        return;
      }
      // A border that is a UI resource lets the look and feel set its own border again.
      setBorder(new BorderUIResource(BorderFactory.createEmptyBorder()));
      super.updateUI();
      boolean fromLookAndFeel = variant == Variant.DEFAULT;
      // Each variant has the insets of the button of the look and feel, and thus the same size.
      Insets insets = getInsets();
      setContentAreaFilled(fromLookAndFeel);
      setBorderPainted(fromLookAndFeel);
      if (!fromLookAndFeel) {
        int side = variant == Variant.BORDERLESS ? Math.min(insets.left, 6) : insets.left;
        setBorder(BorderFactory.createEmptyBorder(insets.top, side, insets.bottom, side));
      }
      setCursor(
          variant == Variant.BORDERLESS
              ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
              : Cursor.getDefaultCursor());
      restyle();
    }

    /** Gives the text color of the variant and of the state to the content. */
    void restyle() {
      if (content == null) {
        return;
      }
      Color color =
          switch (variant) {
            // The content gets the color of the button from the look and feel.
            case DEFAULT -> isEnabled() ? null : BasicStyle.disabled(this);
            case PRIMARY ->
                isEnabled()
                    ? BasicStyle.onAccent()
                    : BasicStyle.mix(BasicStyle.onAccent(), fill(), 0.4);
            case BORDERLESS -> isEnabled() ? BasicStyle.link(this) : BasicStyle.disabled(this);
          };
      content.setForeground(color);
      repaint();
    }

    @Override
    public void setEnabled(boolean enabled) {
      super.setEnabled(enabled);
      restyle();
    }

    /** Gets the background color of the primary variant for the state of the button. */
    private Color fill() {
      Color accent = BasicStyle.accent();
      ButtonModel model = getModel();
      if (!isEnabled()) {
        return BasicStyle.mix(accent, BasicStyle.backgroundOf(this), 0.6);
      }
      if (model.isArmed() && model.isPressed()) {
        return BasicStyle.mix(accent, Color.BLACK, 0.2);
      }
      return model.isRollover() ? BasicStyle.mix(accent, Color.WHITE, 0.12) : accent;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      if (variant == Variant.DEFAULT) {
        super.paintComponent(graphics);
        return;
      }
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        ButtonModel model = getModel();
        Color focus;
        if (variant == Variant.PRIMARY) {
          g.setColor(fill());
          g.fillRoundRect(0, 0, width, height, ARC, ARC);
          focus = BasicStyle.onAccent();
        } else {
          Color link = BasicStyle.link(this);
          boolean pressed = model.isArmed() && model.isPressed();
          if (isEnabled() && (pressed || model.isRollover())) {
            g.setColor(
                new Color(link.getRed(), link.getGreen(), link.getBlue(), pressed ? 60 : 30));
            g.fillRoundRect(0, 0, width, height, ARC, ARC);
          }
          focus = link;
        }
        if (isFocusOwner() && isFocusPainted()) {
          g.setColor(focus);
          g.setStroke(
              new BasicStroke(
                  1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[] {2f, 2f}, 0f));
          g.drawRoundRect(2, 2, width - 5, height - 5, ARC - 2, ARC - 2);
        }
      } finally {
        g.dispose();
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return isPreferredSizeSet()
          ? super.getPreferredSize()
          : getLayout().preferredLayoutSize(this);
    }

    @Override
    public Dimension getMinimumSize() {
      return isMinimumSizeSet() ? super.getMinimumSize() : getLayout().minimumLayoutSize(this);
    }

    @Override
    public int preferredHeightFor(int width) {
      return isPreferredSizeSet() || !(getLayout() instanceof FlexLayout layout)
          ? getPreferredSize().height
          : layout.preferredHeight(this, width);
    }
  }
}
