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

import com.google.a2ui.core.resolution.ValidationResult;
import com.google.a2ui.core.resolution.ValidationSeverity;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * The panel of an input view: a label, a control, and the messages of the failed checks below the
 * control. The views of {@code TextField}, {@code CheckBox}, {@code ChoicePicker}, {@code Slider}
 * and {@code DateTimeInput} use it.
 *
 * <p>The three parts are in a column, with a gap of {@value #GAP} pixels. The panel is a {@link
 * LeafPanel}. Thus it has the margin of a control with an outline.
 *
 * <ul>
 *   <li>The label is a {@code JLabel} above the control. An empty label uses no space. The label is
 *       the {@code labelFor} of the control.
 *   <li>The control is one component that the view gives, for example a text field or a panel of
 *       options. It fills the width of the panel.
 *   <li>A message of the severity {@code error} has the error color. A message of the severity
 *       {@code warning} or {@code info} is a hint: it has a weaker color. The messages of the
 *       errors are also the tool tip of the control. The control has the client property {@code
 *       JComponent.outline} with the value {@code error} while an error shows. A look and feel can
 *       show it, as FlatLaf does.
 * </ul>
 *
 * <p>The messages have a smaller font than the text of the surface. The panel gives them the font
 * and the colors again when the font or the colors above it change.
 */
final class LabeledControl extends LeafPanel {
  private static final long serialVersionUID = 1L;

  /** The space between the label, the control and the messages, in pixels. */
  static final int GAP = 3;

  /** The font size of a message, as a multiple of the base font size. */
  private static final float MESSAGE_SCALE = 0.9f;

  /**
   * One message of a failed check.
   *
   * @param text the text for the user
   * @param severity the importance of the failure
   */
  private record Message(String text, ValidationSeverity severity) {}

  private final JLabel label = new JLabel();
  private final FlexPanel messages = new FlexPanel(FlexLayout.Axis.VERTICAL);

  /** The component of the control in the panel, or null before the first call of setControl. */
  private JComponent holder;

  /** The component that gets the tool tip and the error state, or null. */
  private JComponent control;

  private transient List<Message> shown = List.of();

  /** The font and the colors that the messages have. */
  private transient Font styledFont;

  private transient Color styledText;
  private transient Color styledBackground;
  private boolean styled;

  /** Makes a panel with no label, no control and no messages. */
  LabeledControl() {
    flexLayout().setGap(GAP);
    label.setVisible(false);
    messages.setVisible(false);
    add(label);
    add(messages);
  }

  /**
   * Sets the text of the label. An empty text hides the label.
   *
   * @param text the text
   */
  void setLabel(String text) {
    Objects.requireNonNull(text, "text");
    label.setText(text);
    label.setVisible(!text.isEmpty());
  }

  /**
   * Gets the text of the label, as the accessible name of the control.
   *
   * @return the text, or an empty result if the label is empty
   */
  Optional<String> labelText() {
    String text = label.getText();
    return text == null || text.isEmpty() ? Optional.empty() : Optional.of(text);
  }

  /**
   * Gets the label component.
   *
   * @return the label
   */
  JLabel label() {
    return label;
  }

  /**
   * Puts a control between the label and the messages. The control before goes out of the panel,
   * and the messages go away. Thus the next call of {@link #showChecks(List)} shows them on the new
   * control.
   *
   * @param holder the component to put in the panel: the control, or a component that contains it
   * @param control the component that gets the focus, the tool tip and the error state
   */
  void setControl(JComponent holder, JComponent control) {
    Objects.requireNonNull(holder, "holder");
    Objects.requireNonNull(control, "control");
    if (this.holder != null) {
      clearToolTip();
      remove(this.holder);
    }
    this.holder = holder;
    this.control = control;
    label.setLabelFor(control);
    add(holder, null, 1);
    shown = List.of();
    messages.removeAll();
    messages.setVisible(false);
  }

  /**
   * Shows the messages of the failed checks below the control.
   *
   * @param failed the results of the failed checks, in the sequence of the rules
   */
  void showChecks(List<ValidationResult> failed) {
    List<Message> next = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    for (ValidationResult result : failed) {
      String text = result.message().orElse("");
      if (!text.isBlank()) {
        next.add(new Message(text, result.severity()));
        if (result.severity() == ValidationSeverity.ERROR) {
          errors.add(text);
        }
      }
    }
    if (control != null) {
      control.putClientProperty("JComponent.outline", errors.isEmpty() ? null : "error");
      control.setToolTipText(errors.isEmpty() ? null : String.join(" ", errors));
    }
    if (next.equals(shown)) {
      return;
    }
    shown = List.copyOf(next);
    messages.removeAll();
    for (Message message : shown) {
      WrappingLabel line = new WrappingLabel();
      line.setText(message.text());
      messages.add(line);
    }
    messages.setVisible(!shown.isEmpty());
    styled = false;
  }

  /** Removes the tool tip of the errors from the control, for the disposal of the view. */
  void clearToolTip() {
    if (control != null) {
      control.setToolTipText(null);
    }
  }

  /** Applies the margin, and gives the font and the colors of the look and feel to the messages. */
  @Override
  void sync() {
    super.sync();
    if (messages == null) {
      // The constructor of the panel is not complete.
      return;
    }
    Font font = BasicStyle.fontOf(this);
    Color text = BasicStyle.foregroundOf(this);
    Color background = BasicStyle.backgroundOf(this);
    if (styled
        && font.equals(styledFont)
        && text.equals(styledText)
        && background.equals(styledBackground)) {
      return;
    }
    styled = true;
    styledFont = font;
    styledText = text;
    styledBackground = background;
    Font small = font.deriveFont(font.getSize2D() * MESSAGE_SCALE);
    Component[] lines = messages.getComponents();
    for (int i = 0; i < lines.length && i < shown.size(); i++) {
      lines[i].setFont(small);
      lines[i].setForeground(
          switch (shown.get(i).severity()) {
            case ERROR -> BasicStyle.error(this);
            case WARNING -> BasicStyle.warning(this);
            case INFO -> BasicStyle.muted(this);
          });
    }
  }
}
