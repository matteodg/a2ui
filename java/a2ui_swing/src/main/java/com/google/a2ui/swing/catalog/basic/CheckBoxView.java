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
import com.google.a2ui.swing.binding.ValueBinding;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import java.awt.event.ItemListener;
import java.util.Optional;
import javax.swing.JCheckBox;
import javax.swing.JComponent;

/**
 * The view of the {@code CheckBox} component of the basic catalog: a {@code JCheckBox} of the look
 * and feel with its label next to it, and the messages of the checks below it.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code label}</td><td>The text next to the check box. It breaks into lines at the width
 *       of the view. A click on the text operates the check box. The text is also the accessible
 *       name of the check box, unless the agent gave a label.</td></tr>
 *   <tr><td>{@code value}</td><td>The state of the check box. The view writes {@code true} or
 *       {@code false} to the data model each time the user operates the check box. When the data
 *       model gives back the state that the check box shows, the view does not touch the check box.
 *       A value that is not a boolean is read as {@code Coercion.toBoolean} reads it.</td></tr>
 *   <tr><td>{@code checks}</td><td>The messages of the failed checks show below the check box, as
 *       in the view of {@code TextField}. The user can always operate the check box.</td></tr>
 * </table>
 *
 * <p>A {@code value} with no data path is a state that only the check box has. The user can change
 * it. The view writes nothing, and the next update of the properties shows the value again.
 *
 * <p>The Swing component of the view is a {@link LabeledControl}. It gives the margin of a control.
 * The accessibility attributes go to the {@code JCheckBox}.
 */
final class CheckBoxView implements SwingComponentView {
  private final LabeledControl panel = new LabeledControl();
  private final JCheckBox box = new JCheckBox();
  private final ToggleRow row = new ToggleRow(box);
  private final ValueBinding<Boolean> value = new ValueBinding<>(box::isSelected, box::setSelected);
  private final ItemListener listener = event -> value.edited(box.isSelected());

  /**
   * Makes the view.
   *
   * @param context the context of the view
   */
  CheckBoxView(ViewContext context) {
    panel.setControl(row, box);
    box.addItemListener(listener);
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public JComponent accessibleComponent() {
    return box;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    row.setText(Coercion.toText(props.value("label")));
    value.update(Coercion.toBoolean(props.value("value")), props.writableBinding("value"));
    panel.showChecks(props.validationResults());
    panel.sync();
    panel.revalidate();
    panel.repaint();
  }

  /** Gets the label, as the name of the check box for assistive tools. */
  @Override
  public Optional<String> inferredAccessibleName() {
    String text = row.getText();
    return text.isEmpty() ? Optional.empty() : Optional.of(text);
  }

  @Override
  public void dispose() {
    box.removeItemListener(listener);
    value.dispose();
    panel.clearToolTip();
  }
}
