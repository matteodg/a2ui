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

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeAction;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.swing.binding.TextBinding;
import com.google.a2ui.swing.catalog.ChildViews;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import com.google.a2ui.swing.catalog.basic.FlexLayout;
import java.awt.BorderLayout;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * A small custom catalog for the tests of the adapter. It shows that the public API is sufficient
 * to write components: a label, a text field with a two-way binding and checks, a button with an
 * action and one child, and a container with a list of children.
 *
 * <p>The catalog has the functions of the basic catalog with their code. It has none of the
 * components of the basic catalog.
 */
public final class TestCatalog {
  /** The ID of the catalog. */
  public static final String ID = "https://example.com/catalogs/swing-test/v1/catalog.json";

  /** The URLs that the function {@code openUrl} opened. */
  public final List<URI> opened = new ArrayList<>();

  /** Each view that the catalog made, in the sequence of the creation. */
  public final List<SwingComponentView> created = new ArrayList<>();

  /** The component ID of each view that the adapter disposed, in the sequence of the disposal. */
  public final List<String> disposed = new ArrayList<>();

  private final Catalog<SwingComponentImplementation, FunctionImplementation> catalog;

  /** Makes the catalog. */
  public TestCatalog() {
    List<SwingComponentImplementation> components =
        List.of(
            SwingComponentImplementation.of(
                "Label",
                schema("Label", Map.of("text", ref("DynamicString"))),
                context -> track(new LabelView())),
            SwingComponentImplementation.of(
                "Field",
                schema(
                    "Field", Map.of("label", ref("DynamicString"), "value", ref("DynamicString"))),
                context -> track(new FieldView())),
            SwingComponentImplementation.of(
                "Button",
                schema(
                    "Button",
                    Map.of(
                        "label", ref("DynamicString"),
                        "child", ref("Child"),
                        "action", ref("Action"))),
                context -> track(new ButtonView(context))),
            SwingComponentImplementation.of(
                "Box",
                schema(
                    "Box",
                    Map.of(
                        "children", ref("ChildList"),
                        "direction", Map.of("type", "string"),
                        "justify", Map.of("type", "string"),
                        "align", Map.of("type", "string"),
                        "gap", Map.of("type", "number"))),
                context -> track(new BoxView(context))));
    this.catalog =
        BasicCatalog.withFunctions(Locale.US, opened::add)
            .derive(ID, List.of(), List.of(), null, List.of(), null)
            .withComponents(components);
  }

  /**
   * Gets the catalog.
   *
   * @return the catalog
   */
  public Catalog<SwingComponentImplementation, FunctionImplementation> catalog() {
    return catalog;
  }

  private <V extends TestView> V track(V view) {
    view.owner = this;
    created.add(view);
    return view;
  }

  /**
   * Makes the schema of a component. Each component of this catalog can have a weight and checks.
   */
  static Map<String, Object> schema(String name, Map<String, Object> properties) {
    Map<String, Object> all = new LinkedHashMap<>();
    all.put("component", Map.of("const", name));
    all.put("weight", Map.of("type", "number"));
    all.putAll(properties);
    return Map.of(
        "type",
        "object",
        "allOf",
        List.of(
            ref("Checkable"),
            Map.of("type", "object", "properties", all, "required", List.of("component"))));
  }

  /** Makes a reference to a common type of the protocol. */
  static Map<String, Object> ref(String type) {
    return Map.of("$ref", "common_types.json#/$defs/" + type);
  }

  /** A view that records its disposal with the ID of the component that it showed last. */
  public abstract static class TestView implements SwingComponentView {
    TestCatalog owner;
    private String componentId = "";

    /** The number of the calls of {@link #update(ComponentNode, NodeProps)}. */
    public int updates;

    @Override
    public final void update(ComponentNode<?> node, NodeProps<?> props) {
      componentId = node.componentId();
      updates++;
      show(props);
    }

    /** Shows the properties. */
    abstract void show(NodeProps<?> props);

    /** Releases the resources of the view. */
    void release() {}

    @Override
    public final void dispose() {
      release();
      owner.disposed.add(componentId);
    }
  }

  /** The view of a {@code Label}: a label that shows the property {@code text}. */
  public static final class LabelView extends TestView {
    /** The Swing component. */
    public final JLabel label = new JLabel();

    @Override
    public JComponent component() {
      return label;
    }

    @Override
    void show(NodeProps<?> props) {
      label.setText(Coercion.toText(props.value("text")));
    }
  }

  /**
   * The view of a {@code Field}: a text field that shows and writes the property {@code value}. It
   * shows the messages of the failed checks as its tool tip.
   */
  public static final class FieldView extends TestView {
    /** The Swing component. */
    public final JTextField field = new JTextField(12);

    private final TextBinding value = new TextBinding(field);
    private String label = "";

    @Override
    public JComponent component() {
      return field;
    }

    @Override
    void show(NodeProps<?> props) {
      value.update(props, "value");
      label = Coercion.toText(props.value("label"));
      List<String> errors = props.validationErrors();
      field.setToolTipText(errors.isEmpty() ? null : String.join(" ", errors));
    }

    @Override
    public Optional<String> inferredAccessibleName() {
      return label.isEmpty() ? Optional.empty() : Optional.of(label);
    }

    @Override
    void release() {
      value.dispose();
    }
  }

  /**
   * The view of a {@code Button}: a button that shows the property {@code label} or the child of
   * the property {@code child}, and that does the property {@code action} when the user operates
   * it. The button is disabled while a check of the component fails.
   */
  public static final class ButtonView extends TestView {
    /** The Swing component. */
    public final JButton button = new JButton();

    private final ChildViews child;
    private NodeAction action;

    ButtonView(ViewContext context) {
      button.setLayout(new BorderLayout());
      child = context.children(button);
      // The listener calls the action in the gesture of the user.
      button.addActionListener(event -> {
        if (action != null) {
          action.invoke();
        }
      });
    }

    @Override
    public JComponent component() {
      return button;
    }

    @Override
    void show(NodeProps<?> props) {
      button.setText(Coercion.toText(props.value("label")));
      button.setEnabled(props.isValid());
      action = props.action("action").orElse(null);
      child.sync(props.child("child"));
    }

    /** Gets the text of the child, as a button gets its name from its visible text. */
    @Override
    public Optional<String> inferredAccessibleName() {
      return child.components().stream()
          .filter(JLabel.class::isInstance)
          .map(component -> ((JLabel) component).getText())
          .findFirst();
    }
  }

  /**
   * The view of a {@code Box}: a panel with a {@link FlexLayout} that shows the children of the
   * property {@code children}. The property {@code weight} of a child is its layout constraint.
   */
  public static final class BoxView extends TestView {
    /** The Swing component. */
    public final JPanel panel = new JPanel();

    /** The layout of the panel. */
    public final FlexLayout layout = new FlexLayout(FlexLayout.Axis.VERTICAL);

    /** The helper that keeps the views of the children. */
    public final ChildViews children;

    BoxView(ViewContext context) {
      panel.setLayout(layout);
      panel.setOpaque(false);
      children = context.children(panel, child -> child.props().get().value("weight"));
    }

    @Override
    public JComponent component() {
      return panel;
    }

    @Override
    void show(NodeProps<?> props) {
      layout.setAxis(
          FlexLayout.Axis.fromWire(props.value("direction")).orElse(FlexLayout.Axis.VERTICAL));
      layout.setJustify(
          FlexLayout.Justify.fromWire(props.value("justify")).orElse(FlexLayout.Justify.START));
      layout.setAlign(
          FlexLayout.Align.fromWire(props.value("align")).orElse(FlexLayout.Align.STRETCH));
      layout.setGap(props.value("gap") instanceof Number gap ? gap.intValue() : 0);
      children.sync(props.children("children"));
      panel.revalidate();
    }
  }
}
