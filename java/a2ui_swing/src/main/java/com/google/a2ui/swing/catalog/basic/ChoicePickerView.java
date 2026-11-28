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
import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The view of the {@code ChoicePicker} component of the basic catalog: a label, a group of options,
 * and the messages of the checks.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code label}</td><td>The text above the options. It is also the accessible name of the
 *       group, unless the agent gave a label. An empty label uses no space.</td></tr>
 *   <tr><td>{@code options}</td><td>The options, in their sequence. The {@code label} of an option
 *       is its text. The {@code value} is the string that the data model gets.</td></tr>
 *   <tr><td>{@code value}</td><td>The values of the selected options. The data model has a list of
 *       strings, also for one selection. The view writes the list each time the user changes the
 *       selection. The values in the list have the sequence of the options. When the data model
 *       gives back the selection that the view shows, the view does not touch the options.</td></tr>
 *   <tr><td>{@code variant}</td><td>{@code mutuallyExclusive}, the default, lets the user select
 *       one option. {@code multipleSelection} lets the user select each number of options.</td></tr>
 *   <tr><td>{@code displayStyle}</td><td>{@code checkbox}, the default, shows a {@code
 *       JRadioButton} for each option of a {@code mutuallyExclusive} picker, and a {@code
 *       JCheckBox} for each option of a {@code multipleSelection} picker. The text of an option
 *       breaks into lines at the width of its column. {@code chips} shows a {@code JToggleButton}
 *       for each option. The chips are side by side, and they go to the next line when the width
 *       is not sufficient.</td></tr>
 *   <tr><td>{@code filterable}</td><td>If true, a text field is above the options. The view shows
 *       only the options whose label contains the text of the field, in upper case or in lower
 *       case. The filter does not change the selection. A selected option that the filter hides
 *       stays selected.</td></tr>
 *   <tr><td>{@code checks}</td><td>The messages of the failed checks show below the options, as in
 *       the view of {@code TextField}.</td></tr>
 * </table>
 *
 * <p><b>Many options.</b> The {@code checkbox} style puts the options in columns of the same width
 * when there are more than {@value #ROWS_IN_ONE_COLUMN} options and the width is sufficient. The
 * sequence goes down each column. Thus 30 options in a wide view show in some columns of 10 rows or
 * less, and the view does not need a scroll bar. In a narrow view, the options are in one column.
 *
 * <p><b>Selection.</b> A value of the data model that is not the value of an option does not show.
 * When the options change, each selected value that is still the value of an option stays selected.
 * A {@code mutuallyExclusive} picker shows only the first value of the list. The view does not
 * change the data model for these reasons. It writes only when the user changes the selection: then
 * the list has the values of the options that show as selected. A {@code value} with no data path
 * is a selection that only the view has, until the next update.
 *
 * <p>The Swing component of the view is a {@link LabeledControl}. The accessibility attributes go
 * to the panel of the options, which has the accessible role of a group box. Each option has its
 * label as its accessible name.
 */
final class ChoicePickerView implements SwingComponentView {
  /** The most options that the {@code checkbox} style puts in one column. */
  static final int ROWS_IN_ONE_COLUMN = 6;

  /** The space between two columns of options, in pixels. */
  private static final int COLUMN_GAP = 16;

  /** The space between two chips, in pixels. */
  private static final int CHIP_GAP = 6;

  /** The number of columns that gives the preferred width of the filter field. */
  private static final int FILTER_COLUMNS = 12;

  /**
   * The kind of the controls of the options.
   *
   * @param exclusive true if the user can select one option only
   * @param chips true for the {@code chips} display style
   */
  private record Kind(boolean exclusive, boolean chips) {}

  /**
   * One option of the newest properties.
   *
   * @param label the text of the option
   * @param value the value for the data model
   */
  private record Option(String label, String value) {}

  private final LabeledControl panel = new LabeledControl();
  private final FlexPanel content = new FlexPanel(FlexLayout.Axis.VERTICAL);
  private final JTextField filter = new JTextField(FILTER_COLUMNS);
  private final Group group = new Group();

  /** The control of each option, in the sequence of the options. */
  private final List<AbstractButton> buttons = new ArrayList<>();

  private final ValueBinding<Set<String>> selection =
      new ValueBinding<>(this::shownSelection, this::showSelection);

  private final ActionListener listener = event -> selected();

  /** The kind of the controls, or null before the first update. */
  private Kind kind;

  private ButtonGroup exclusive = new ButtonGroup();
  private List<Option> options = List.of();

  /** The selection of the newest properties, or of the newest change of the user. */
  private Set<String> current = Set.of();

  /**
   * Makes the view.
   *
   * @param context the context of the view
   */
  ChoicePickerView(ViewContext context) {
    content.flexLayout().setGap(LabeledControl.GAP);
    filter.setVisible(false);
    filter
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent event) {
                applyFilter();
              }

              @Override
              public void removeUpdate(DocumentEvent event) {
                applyFilter();
              }

              @Override
              public void changedUpdate(DocumentEvent event) {
                // Only the attributes of the text changed.
              }
            });
    content.add(filter);
    content.add(group);
    panel.setControl(content, group);
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public JComponent accessibleComponent() {
    return group;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    String label = Coercion.toText(props.value("label"));
    panel.setLabel(label);
    filter.getAccessibleContext().setAccessibleName(label.isEmpty() ? null : label);
    useKind(
        new Kind(
            !"multipleSelection".equals(props.value("variant")),
            "chips".equals(props.value("displayStyle"))));
    List<Option> next = new ArrayList<>();
    for (NodeProps<?> option : props.objects("options")) {
      next.add(
          new Option(
              Coercion.toText(option.value("label")), Coercion.toText(option.value("value"))));
    }
    showOptions(next);
    boolean filterable = Coercion.toBoolean(props.value("filterable"));
    if (filterable != filter.isVisible()) {
      filter.setVisible(filterable);
      if (!filterable) {
        // The text of a hidden filter has no effect.
        filter.setText("");
      }
    }
    applyFilter();
    current = modelSelection(props.value("value"));
    selection.update(current, props.writableBinding("value"));
    panel.showChecks(props.validationResults());
    panel.sync();
    panel.revalidate();
    panel.repaint();
  }

  /** Gets the label, as the name of the group for assistive tools. */
  @Override
  public Optional<String> inferredAccessibleName() {
    return panel.labelText();
  }

  @Override
  public void dispose() {
    for (AbstractButton button : buttons) {
      button.removeActionListener(listener);
    }
    selection.dispose();
    panel.clearToolTip();
  }

  /** Makes new controls for the options if the kind of the controls changes. */
  private void useKind(Kind next) {
    if (next.equals(kind)) {
      return;
    }
    kind = next;
    for (AbstractButton button : buttons) {
      button.removeActionListener(listener);
    }
    buttons.clear();
    group.removeAll();
    exclusive = new ButtonGroup();
    group.optionsLayout.flow = next.chips();
    options = List.of();
  }

  /** Gives the options to the controls, and adds or removes controls at the end. */
  private void showOptions(List<Option> next) {
    while (buttons.size() > next.size()) {
      AbstractButton button = buttons.removeLast();
      button.removeActionListener(listener);
      exclusive.remove(button);
      group.remove(group.getComponentCount() - 1);
    }
    while (buttons.size() < next.size()) {
      AbstractButton button;
      if (kind.chips()) {
        button = new JToggleButton();
        group.add(button);
      } else {
        button = kind.exclusive() ? new JRadioButton() : new JCheckBox();
        group.add(new ToggleRow(button));
      }
      if (kind.exclusive()) {
        exclusive.add(button);
      }
      button.addActionListener(listener);
      buttons.add(button);
    }
    for (int i = 0; i < next.size(); i++) {
      Option option = next.get(i);
      AbstractButton button = buttons.get(i);
      button.setActionCommand(option.value());
      if (kind.chips()) {
        String text = TabsView.plainTitle(option.label());
        if (!text.equals(button.getText())) {
          button.setText(text);
        }
      } else {
        ((ToggleRow) group.getComponent(i)).setText(option.label());
        button.getAccessibleContext().setAccessibleName(option.label());
      }
    }
    options = List.copyOf(next);
  }

  /** Shows only the options whose label contains the text of the filter. */
  private void applyFilter() {
    String text = filter.isVisible() ? filter.getText().strip().toLowerCase(Locale.ROOT) : "";
    boolean changed = false;
    for (int i = 0; i < buttons.size() && i < options.size(); i++) {
      boolean shows = options.get(i).label().toLowerCase(Locale.ROOT).contains(text);
      Component item = group.getComponent(i);
      if (item.isVisible() != shows) {
        item.setVisible(shows);
        changed = true;
      }
    }
    if (changed) {
      group.revalidate();
      group.repaint();
    }
  }

  /** Gets the values of the data model that the options can show as selected. */
  private Set<String> modelSelection(Object value) {
    List<String> wanted = new ArrayList<>();
    switch (value) {
      case List<?> list -> list.forEach(item -> wanted.add(Coercion.toText(item)));
      case String text -> wanted.add(text);
      case null, default -> {}
    }
    Set<String> known = new LinkedHashSet<>();
    for (Option option : options) {
      known.add(option.value());
    }
    Set<String> result = new LinkedHashSet<>();
    for (String item : wanted) {
      if (known.contains(item)) {
        result.add(item);
        if (kind.exclusive()) {
          break;
        }
      }
    }
    return result;
  }

  /** Gets the values of the options that show as selected, in the sequence of the options. */
  private Set<String> shownSelection() {
    Set<String> result = new LinkedHashSet<>();
    for (AbstractButton button : buttons) {
      if (button.isSelected()) {
        result.add(button.getActionCommand());
      }
    }
    return result;
  }

  private void showSelection(Set<String> values) {
    if (kind.exclusive()) {
      exclusive.clearSelection();
      for (AbstractButton button : buttons) {
        if (values.contains(button.getActionCommand())) {
          button.setSelected(true);
          return;
        }
      }
      return;
    }
    for (AbstractButton button : buttons) {
      button.setSelected(values.contains(button.getActionCommand()));
    }
  }

  /** Writes the selection after the user operated an option. */
  private void selected() {
    Set<String> shown = shownSelection();
    // A click on a selected radio button changes nothing.
    if (selection.isShowing() || shown.equals(current)) {
      return;
    }
    current = shown;
    selection.edited(List.copyOf(shown));
  }

  /** The panel of the options. It has the accessible role of a group box. */
  private static final class Group extends JPanel implements HeightForWidth {
    private static final long serialVersionUID = 1L;

    private final transient OptionsLayout optionsLayout;

    Group() {
      this(new OptionsLayout());
    }

    private Group(OptionsLayout layout) {
      super(layout);
      optionsLayout = layout;
      setOpaque(false);
      inheritTextStyle();
    }

    @Override
    public int preferredHeightFor(int width) {
      return isPreferredSizeSet() ? getPreferredSize().height : optionsLayout.heightAt(this, width);
    }

    /** Applies the look and feel, and then removes the font and the color that it set. */
    @Override
    public void updateUI() {
      super.updateUI();
      inheritTextStyle();
    }

    private void inheritTextStyle() {
      setFont(null);
      setForeground(null);
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleGroup();
      }
      return accessibleContext;
    }

    /** The accessible context of the panel. It has the role of a group box. */
    private final class AccessibleGroup extends AccessibleJPanel {
      private static final long serialVersionUID = 1L;

      @Override
      public AccessibleRole getAccessibleRole() {
        return AccessibleRole.GROUP_BOX;
      }
    }
  }

  /**
   * The layout of the options. It puts the options in columns of the same width, or it puts chips
   * side by side in lines. The height depends on the width.
   */
  private static final class OptionsLayout implements LayoutManager {
    /** True for chips in lines, false for options in columns. */
    private boolean flow;

    @Override
    public void addLayoutComponent(String name, Component component) {
      // The layout keeps no data for a component.
    }

    @Override
    public void removeLayoutComponent(Component component) {
      // The layout keeps no data for a component.
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      synchronized (parent.getTreeLock()) {
        Insets insets = parent.getInsets();
        List<Component> items = visible(parent);
        int width = 0;
        if (flow) {
          for (Component item : items) {
            width += item.getPreferredSize().width;
          }
          width += CHIP_GAP * Math.max(0, items.size() - 1);
        } else if (!items.isEmpty()) {
          int columns = columns(items.size(), Integer.MAX_VALUE, columnWidth(items));
          width = columns * columnWidth(items) + (columns - 1) * COLUMN_GAP;
        }
        int outer = width + insets.left + insets.right;
        return new Dimension(outer, heightAt(parent, outer));
      }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      synchronized (parent.getTreeLock()) {
        Insets insets = parent.getInsets();
        int width = 0;
        for (Component item : visible(parent)) {
          width = Math.max(width, flow ? item.getPreferredSize().width : minimumWidth(item));
        }
        int outer = width + insets.left + insets.right;
        return new Dimension(outer, heightAt(parent, outer));
      }
    }

    /**
     * Gets the preferred height of the options at a width.
     *
     * @param parent the panel of the options
     * @param width the width of the panel, with its insets
     * @return the height, with the insets
     */
    int heightAt(Container parent, int width) {
      synchronized (parent.getTreeLock()) {
        Insets insets = parent.getInsets();
        int inner = Math.max(1, width - insets.left - insets.right);
        return place(parent, inner, false) + insets.top + insets.bottom;
      }
    }

    @Override
    public void layoutContainer(Container parent) {
      synchronized (parent.getTreeLock()) {
        Insets insets = parent.getInsets();
        place(parent, Math.max(1, parent.getWidth() - insets.left - insets.right), true);
      }
    }

    /**
     * Calculates the positions of the options at a width, and gives them to the options.
     *
     * @param parent the panel of the options
     * @param inner the width without the insets
     * @param apply true to set the bounds of the options
     * @return the height of the content
     */
    private int place(Container parent, int inner, boolean apply) {
      List<Component> items = visible(parent);
      if (items.isEmpty()) {
        return 0;
      }
      Insets insets = parent.getInsets();
      boolean mirror = !parent.getComponentOrientation().isLeftToRight();
      List<int[]> bounds = new ArrayList<>();
      int height;
      if (flow) {
        int x = 0;
        int y = 0;
        int line = 0;
        for (Component item : items) {
          Dimension size = item.getPreferredSize();
          int width = Math.min(size.width, inner);
          if (x > 0 && x + width > inner) {
            x = 0;
            y += line + CHIP_GAP;
            line = 0;
          }
          bounds.add(new int[] {x, y, width, size.height});
          x += width + CHIP_GAP;
          line = Math.max(line, size.height);
        }
        height = y + line;
      } else {
        int columns = columns(items.size(), inner, columnWidth(items));
        int rows = (items.size() + columns - 1) / columns;
        int width = (inner - (columns - 1) * COLUMN_GAP) / columns;
        int[] rowHeights = new int[rows];
        for (int i = 0; i < items.size(); i++) {
          rowHeights[i % rows] = Math.max(rowHeights[i % rows], heightOf(items.get(i), width));
        }
        int[] rowTops = new int[rows];
        height = 0;
        for (int row = 0; row < rows; row++) {
          rowTops[row] = height;
          height += rowHeights[row];
        }
        for (int i = 0; i < items.size(); i++) {
          int column = i / rows;
          int row = i % rows;
          bounds.add(
              new int[] {
                column * (width + COLUMN_GAP), rowTops[row], width, heightOf(items.get(i), width)
              });
        }
      }
      if (apply) {
        for (int i = 0; i < items.size(); i++) {
          int[] b = bounds.get(i);
          int x = mirror ? inner - b[0] - b[2] : b[0];
          items.get(i).setBounds(insets.left + x, insets.top + b[1], b[2], b[3]);
        }
      }
      return height;
    }

    /** Gets the number of columns for a number of options in the {@code checkbox} style. */
    private static int columns(int count, int inner, int columnWidth) {
      int wanted = (count + ROWS_IN_ONE_COLUMN - 1) / ROWS_IN_ONE_COLUMN;
      long fit = ((long) inner + COLUMN_GAP) / Math.max(1, columnWidth + COLUMN_GAP);
      return (int) Math.max(1, Math.min(wanted, fit));
    }

    /** Gets the width of a column: the preferred width of the widest option. */
    private static int columnWidth(List<Component> items) {
      int width = 1;
      for (Component item : items) {
        width = Math.max(width, item.getPreferredSize().width);
      }
      return width;
    }

    private static int minimumWidth(Component item) {
      return item.getMinimumSize().width;
    }

    private static int heightOf(Component item, int width) {
      return item instanceof HeightForWidth wrapping
          ? wrapping.preferredHeightFor(width)
          : item.getPreferredSize().height;
    }

    private static List<Component> visible(Container parent) {
      List<Component> items = new ArrayList<>();
      for (Component component : parent.getComponents()) {
        if (component.isVisible()) {
          items.add(component);
        }
      }
      return items;
    }
  }
}
