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

import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.swing.binding.TextBinding;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;

/**
 * The view of the {@code TextField} component of the basic catalog: a label, a text control of the
 * look and feel, and the messages of the checks.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code label}</td><td>The text above the control. It is also the accessible name of the
 *       control, unless the agent gave a label. An empty label uses no space.</td></tr>
 *   <tr><td>{@code value}</td><td>The text of the control. The view writes each edit of the user to
 *       the data model immediately. When the data model gives back the text that the control shows,
 *       the view does not touch the control. Thus the caret and the selection stay.</td></tr>
 *   <tr><td>{@code placeholder}</td><td>A text that the control shows in a weak color while it is
 *       empty.</td></tr>
 *   <tr><td>{@code variant}</td><td>{@code shortText}, the default, is a {@code JTextField}. {@code
 *       longText} is a {@code JTextArea} of three lines in a scroll pane. {@code number} is a
 *       {@code JTextField} that accepts only the text of a decimal number. {@code obscured} is a
 *       {@code JPasswordField}.</td></tr>
 *   <tr><td>{@code checks}</td><td>The messages of the failed checks show below the control. A
 *       message of the severity {@code error} has the error color. A message of the severity
 *       {@code warning} or {@code info} is a hint: it has a weaker color, and it does not stop the
 *       user. The messages of the errors are also the tool tip of the control.</td></tr>
 * </table>
 *
 * <p>The {@code number} variant accepts an optional minus sign, digits, and one decimal point. It
 * refuses each other edit of the user. The value in the data model stays a string, as the catalog
 * specifies. A value from the data model that is not a number shows as it is.
 *
 * <p>The Swing component of the view is a {@link LabeledControl}. It gives the margin of a control.
 * The accessibility attributes go to the text control. A change of the variant replaces the
 * control. Then the caret and the focus of the control before are lost.
 */
final class TextFieldView implements SwingComponentView {
  /** The values of the property {@code variant}. */
  enum Variant {
    /** One line of text. */
    SHORT_TEXT,
    /** Many lines of text. */
    LONG_TEXT,
    /** A decimal number. */
    NUMBER,
    /** Text that the control does not show, for example a password. */
    OBSCURED;

    /** Gets the variant for a value of the property. An unknown value gives {@link #SHORT_TEXT}. */
    static Variant fromWire(Object value) {
      if ("longText".equals(value)) {
        return LONG_TEXT;
      }
      if ("number".equals(value)) {
        return NUMBER;
      }
      return "obscured".equals(value) ? OBSCURED : SHORT_TEXT;
    }
  }

  /** The key of the client property of a text control that has its placeholder text. */
  static final String PLACEHOLDER_PROPERTY = "a2ui.placeholder";

  /** The number of columns that gives the preferred width of a control. */
  private static final int COLUMNS = 16;

  /** The number of lines of the control of the {@code longText} variant. */
  private static final int ROWS = 3;

  private final LabeledControl panel = new LabeledControl();
  private final NumberFilter numbers = new NumberFilter();

  /** The variant of the control, or null before the first update. */
  private Variant variant;

  private JTextComponent control;

  private TextBinding binding;

  /**
   * Makes the view.
   *
   * @param context the context of the view
   */
  TextFieldView(ViewContext context) {
    useVariant(Variant.SHORT_TEXT);
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public JComponent accessibleComponent() {
    return control;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    useVariant(Variant.fromWire(props.value("variant")));
    panel.setLabel(Coercion.toText(props.value("label")));
    setPlaceholder(Coercion.toText(props.value("placeholder")));
    // The filter refuses only the edits of the user. A value of the data model shows as it is.
    numbers.active = false;
    try {
      binding.update(props, "value");
    } finally {
      numbers.active = variant == Variant.NUMBER;
    }
    panel.showChecks(props.validationResults());
    panel.sync();
    panel.revalidate();
    panel.repaint();
  }

  /** Gets the label, as the name of the control for assistive tools. */
  @Override
  public Optional<String> inferredAccessibleName() {
    return panel.labelText();
  }

  @Override
  public void dispose() {
    binding.dispose();
    panel.clearToolTip();
  }

  /** Makes the control of a variant, if the view does not have it. */
  private void useVariant(Variant next) {
    if (next == variant) {
      return;
    }
    Object placeholder = null;
    if (control != null) {
      placeholder = control.getClientProperty(PLACEHOLDER_PROPERTY);
      binding.dispose();
    }
    variant = next;
    control =
        switch (next) {
          case SHORT_TEXT, NUMBER -> new Field();
          case OBSCURED -> new SecretField();
          case LONG_TEXT -> new Area();
        };
    // The component of the control in the panel: the control, or the scroll pane around it.
    JComponent holder =
        next == Variant.LONG_TEXT
            ? new JScrollPane(
                control,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
            : control;
    if (control.getDocument() instanceof AbstractDocument document) {
      document.setDocumentFilter(numbers);
    }
    numbers.active = next == Variant.NUMBER;
    control.putClientProperty(PLACEHOLDER_PROPERTY, placeholder);
    panel.setControl(holder, control);
    binding = new TextBinding(control);
  }

  private void setPlaceholder(String text) {
    Object next = text.isEmpty() ? null : text;
    if (!Objects.equals(next, control.getClientProperty(PLACEHOLDER_PROPERTY))) {
      control.putClientProperty(PLACEHOLDER_PROPERTY, next);
      control.repaint();
    }
  }

  /** Paints the placeholder of a text control that is empty. */
  private static void paintPlaceholder(JTextComponent control, Graphics graphics) {
    if (!(control.getClientProperty(PLACEHOLDER_PROPERTY) instanceof String text)
        || control.getDocument().getLength() > 0) {
      return;
    }
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      BasicStyle.textHints(g);
      Insets insets = control.getInsets();
      FontMetrics metrics = control.getFontMetrics(control.getFont());
      int baseline = control.getBaseline(control.getWidth(), control.getHeight());
      if (baseline < 0) {
        baseline = insets.top + metrics.getAscent();
      }
      int width = control.getWidth() - insets.left - insets.right;
      g.clipRect(insets.left, insets.top, width, control.getHeight() - insets.top - insets.bottom);
      g.setFont(control.getFont());
      g.setColor(BasicStyle.mix(control.getForeground(), control.getBackground(), 0.5));
      int x =
          control.getComponentOrientation().isLeftToRight()
              ? insets.left
              : control.getWidth() - insets.right - metrics.stringWidth(text);
      g.drawString(text, x, baseline);
    } finally {
      g.dispose();
    }
  }

  /** The control of the {@code shortText} and of the {@code number} variants. */
  private static final class Field extends JTextField {
    private static final long serialVersionUID = 1L;

    Field() {
      super(COLUMNS);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      paintPlaceholder(this, graphics);
    }
  }

  /** The control of the {@code obscured} variant. */
  private static final class SecretField extends JPasswordField {
    private static final long serialVersionUID = 1L;

    SecretField() {
      super(COLUMNS);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      paintPlaceholder(this, graphics);
    }
  }

  /** The control of the {@code longText} variant. */
  private static final class Area extends JTextArea {
    private static final long serialVersionUID = 1L;

    Area() {
      super(ROWS, COLUMNS);
      setLineWrap(true);
      setWrapStyleWord(true);
      // The Tab key moves the focus, as in the other controls of a form.
      setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
      setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
    }

    /** Applies the look and feel, and then the font of a text field of one line. */
    @Override
    public void updateUI() {
      super.updateUI();
      // Some look and feels give a text area a font with a fixed width.
      Font font = UIManager.getFont("TextField.font");
      if (font != null) {
        setFont(font);
      }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      paintPlaceholder(this, graphics);
    }
  }

  /**
   * A filter that accepts an edit of the user only if the text of the control stays the start of a
   * decimal number: an optional minus sign, digits, and one decimal point.
   */
  private static final class NumberFilter extends DocumentFilter {
    private static final Pattern NUMBER_START = Pattern.compile("-?[0-9]*(?:\\.[0-9]*)?");

    /** True while the filter examines the edits. */
    private boolean active;

    @Override
    public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes)
        throws BadLocationException {
      replace(bypass, offset, 0, text, attributes);
    }

    @Override
    public void replace(
        FilterBypass bypass, int offset, int length, String text, AttributeSet attributes)
        throws BadLocationException {
      if (active) {
        Document document = bypass.getDocument();
        String current = document.getText(0, document.getLength());
        String next =
            current.substring(0, offset)
                + (text == null ? "" : text)
                + current.substring(offset + length);
        // The user can always remove text. Thus text that is not a number can leave the control.
        boolean removal = text == null || text.isEmpty();
        if (!removal && !NUMBER_START.matcher(next).matches()) {
          if (!GraphicsEnvironment.isHeadless()) {
            UIManager.getLookAndFeel().provideErrorFeedback(null);
          }
          return;
        }
      }
      super.replace(bypass, offset, length, text, attributes);
    }
  }
}
