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

package com.google.a2ui.samples.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/**
 * The left column of the gallery: the list of the examples. An example that has a component with no
 * Swing view shows the number of those component types.
 */
final class ExampleListPanel {
  private final JPanel panel = new JPanel(new BorderLayout());
  private final JList<Example> list;

  /**
   * Makes the column.
   *
   * @param examples the examples, in the sequence of the list
   * @param onSelect receives the example that the user selects
   */
  ExampleListPanel(List<Example> examples, Consumer<Example> onSelect) {
    list = new JList<>(examples.toArray(Example[]::new));
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new Renderer());
    list.addListSelectionListener(event -> {
      Example selected = list.getSelectedValue();
      if (!event.getValueIsAdjusting() && selected != null) {
        onSelect.accept(selected);
      }
    });
    long complete = examples.stream().filter(Example::complete).count();
    JLabel header = new JLabel(examples.size() + " examples, " + complete + " complete");
    header.setToolTipText(
        "A complete example uses only components that have a Swing view. The other examples show"
            + " a placeholder for each component with no view.");
    header.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    panel.add(header, BorderLayout.NORTH);
    panel.add(new JScrollPane(list), BorderLayout.CENTER);
  }

  /**
   * Gets the Swing component of the column.
   *
   * @return the component
   */
  JComponent component() {
    return panel;
  }

  /**
   * Selects an example, as the user does.
   *
   * @param id the ID of the example
   * @return false if the list has no example with this ID
   */
  boolean select(String id) {
    for (int index = 0; index < list.getModel().getSize(); index++) {
      if (list.getModel().getElementAt(index).id().equals(id)) {
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
        return true;
      }
    }
    return false;
  }

  /**
   * Gets the example that is selected.
   *
   * @return the example, or an empty result
   */
  Optional<Example> selected() {
    return Optional.ofNullable(list.getSelectedValue());
  }

  /**
   * Gets the text of the list entry of an example.
   *
   * @param example the example
   * @return the ID of the example, with a mark for an example that cannot show completely
   */
  static String entryText(Example example) {
    if (example.problem().isPresent()) {
      return example.id() + "  [cannot read]";
    }
    int absent = example.absentViews().size();
    return absent == 0 ? example.id() : example.id() + "  [" + absent + " with no view]";
  }

  /**
   * Gets the tool tip of the list entry of an example.
   *
   * @param example the example
   * @return the name of the example and the reason why it cannot show completely
   */
  static String entryToolTip(Example example) {
    if (example.problem().isPresent()) {
      return example.problem().get();
    }
    return example.absentViews().isEmpty()
        ? example.name()
        : example.name() + ". No Swing view: " + String.join(", ", example.absentViews());
  }

  /** Shows one example of the list. An example that cannot show completely is in italic type. */
  private static final class Renderer extends DefaultListCellRenderer {
    private static final long serialVersionUID = 1L;

    @Override
    public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof Example example) {
        setText(entryText(example));
        setToolTipText(entryToolTip(example));
        setFont(list.getFont().deriveFont(example.complete() ? Font.PLAIN : Font.ITALIC));
        setEnabled(example.complete() || isSelected);
        setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
      }
      return this;
    }
  }
}
