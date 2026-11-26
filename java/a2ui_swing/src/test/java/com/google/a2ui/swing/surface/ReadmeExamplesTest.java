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

package com.google.a2ui.swing.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeAction;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.core.state.RendererAction;
import com.google.a2ui.swing.binding.TextBinding;
import com.google.a2ui.swing.catalog.ChildViews;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import com.google.a2ui.swing.catalog.basic.FlexLayout;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.Headless;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

/** Makes sure that the examples of the README of this module compile and operate. */
class ReadmeExamplesTest {
  // The views of the section "Write a component".

  static final class FieldView implements SwingComponentView {
    private final JTextField field = new JTextField(12);
    private final TextBinding value = new TextBinding(field);

    @Override
    public JComponent component() {
      return field;
    }

    @Override
    public void update(ComponentNode<?> node, NodeProps<?> props) {
      value.update(props, "value");
      List<String> errors = props.validationErrors();
      field.setToolTipText(errors.isEmpty() ? null : String.join(" ", errors));
    }

    @Override
    public void dispose() {
      value.dispose();
    }
  }

  static final class ButtonView implements SwingComponentView {
    private final JButton button = new JButton();
    private NodeAction action;

    ButtonView() {
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
    public void update(ComponentNode<?> node, NodeProps<?> props) {
      button.setText(Coercion.toText(props.value("label")));
      button.setEnabled(props.isValid());
      action = props.action("action").orElse(null);
    }
  }

  static final class BoxView implements SwingComponentView {
    private final JPanel panel = new JPanel(new FlexLayout(FlexLayout.Axis.VERTICAL));
    private final ChildViews children;

    BoxView(ViewContext context) {
      children = context.children(panel, child -> child.props().get().value("weight"));
    }

    @Override
    public JComponent component() {
      return panel;
    }

    @Override
    public void update(ComponentNode<?> node, NodeProps<?> props) {
      children.sync(props.children("children"));
    }
  }

  private static Map<String, Object> ref(String type) {
    return Map.of("$ref", "common_types.json#/$defs/" + type);
  }

  private static Map<String, Object> schema(String name, Map<String, Object> properties) {
    Map<String, Object> all = new LinkedHashMap<>(properties);
    all.put("component", Map.of("const", name));
    all.put("weight", Map.of("type", "number"));
    return Map.of(
        "type",
        "object",
        "allOf",
        List.of(
            ref("Checkable"),
            Map.of("type", "object", "properties", all, "required", List.of("component"))));
  }

  @Test
  void theExampleOfTheSectionShowASurfaceShowsItsText() {
    Edt.run(() -> {
      SwingComponentImplementation greeting =
          SwingComponentImplementation.of(
              "Greeting",
              Map.of(
                  "type", "object",
                  "properties",
                      Map.of(
                          "component", Map.of("const", "Greeting"),
                          "text", Map.of("$ref", "common_types.json#/$defs/DynamicString")),
                  "required", List.of("component", "text")),
              context -> {
                JLabel label = new JLabel();
                return SwingComponentView.of(
                    label, (node, props) -> label.setText(Coercion.toText(props.value("text"))));
              });
      Catalog<SwingComponentImplementation, FunctionApi> catalog =
          new Catalog<>(
              "https://example.com/catalogs/demo/v1/catalog.json",
              List.of(greeting),
              List.of(),
              null,
              ProtocolVersion.V1_0);

      MessageProcessor<SwingComponentImplementation> processor =
          new MessageProcessor<>(List.of(catalog));
      A2uiSwing.install(processor);
      // The example puts the surface component in a JFrame. A test has no window.
      List<A2uiSurface> shown = new ArrayList<>();
      processor
          .model()
          .onSurfaceCreated()
          .subscribe(surface -> shown.add(Headless.mount(new A2uiSurface(surface))));

      processor.processMessages(
          Map.of(
              "version",
              "1.0",
              "createSurface",
              Map.of(
                  "surfaceId", "main",
                  "catalogId", catalog.id(),
                  "components",
                      List.of(Map.of("id", "root", "component", "Greeting", "text", "Hello")))));

      assertEquals("Hello", ((JLabel) shown.get(0).getComponent(0)).getText());
    });
  }

  @Test
  void theViewsOfTheSectionWriteAComponentOperateInASurface() {
    Edt.run(() -> {
      List<SwingComponentImplementation> components =
          List.of(
              SwingComponentImplementation.of(
                  "Field",
                  schema("Field", Map.of("value", ref("DynamicString"))),
                  context -> new FieldView()),
              SwingComponentImplementation.of(
                  "Button",
                  schema("Button", Map.of("label", ref("DynamicString"), "action", ref("Action"))),
                  context -> new ButtonView()),
              SwingComponentImplementation.of(
                  "Box", schema("Box", Map.of("children", ref("ChildList"))), BoxView::new));
      Catalog<SwingComponentImplementation, FunctionImplementation> catalog =
          BasicCatalog.withFunctions()
              .derive("urn:example:readme", List.of(), List.of(), null, List.of(), null)
              .withComponents(components);
      MessageProcessor<SwingComponentImplementation> processor =
          new MessageProcessor<>(List.of(catalog));
      List<RendererAction> actions = new ArrayList<>();
      processor.model().onAction().subscribe(actions::add);
      Map<String, Object> required =
          Map.of(
              "condition",
              Map.of("@call", "required", "args", Map.of("value", Map.of("@path", "/name"))),
              "message",
              "Enter a name.");
      processor.processMessages(
          Map.of(
              "version",
              "1.0",
              "createSurface",
              Map.of(
                  "surfaceId",
                  "main",
                  "catalogId",
                  catalog.id(),
                  "dataModel",
                  Map.of("name", ""),
                  "components",
                  List.of(
                      Map.of("id", "root", "component", "Box", "children", List.of("name", "ok")),
                      Map.of(
                          "id",
                          "name",
                          "component",
                          "Field",
                          "value",
                          Map.of("@path", "/name"),
                          "weight",
                          1,
                          "@checks",
                          List.of(required)),
                      Map.of(
                          "id",
                          "ok",
                          "component",
                          "Button",
                          "label",
                          "OK",
                          "action",
                          Map.of("event", Map.of("name", "confirm")),
                          "@checks",
                          List.of(required))))));
      A2uiSurface view =
          Headless.mount(new A2uiSurface(processor.model().getSurface("main").orElseThrow()));
      JPanel box = (JPanel) view.getComponent(0);
      JTextField field = (JTextField) box.getComponent(0);
      JButton button = (JButton) box.getComponent(1);

      assertEquals("Enter a name.", field.getToolTipText());
      assertFalse(button.isEnabled());
      assertEquals(1.0, ((FlexLayout) box.getLayout()).weightOf(field));

      field.setText("Ada");
      button.doClick(0);

      assertTrue(button.isEnabled());
      assertEquals(1, actions.size());
      assertEquals("confirm", actions.get(0).name());
    });
  }
}
