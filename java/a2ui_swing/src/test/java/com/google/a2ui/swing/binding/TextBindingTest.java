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

package com.google.a2ui.swing.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeResolver;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.swing.testing.Edt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.junit.jupiter.api.Test;

class TextBindingTest {
  private final MessageProcessor<ComponentApi> processor =
      new MessageProcessor<>(List.of(BasicCatalog.withFunctions()));

  /** Makes a surface whose root is a text field, and gets the node of the field. */
  private ComponentNode<ComponentApi> field(Object value) {
    processor.processMessages(
        Map.of(
            "version",
            "1.0",
            "createSurface",
            Map.of(
                "surfaceId",
                "s",
                "catalogId",
                BasicCatalog.ID,
                "dataModel",
                Map.of("name", "Ada", "count", 3),
                "components",
                List.of(
                    Map.of(
                        "id",
                        "root",
                        "component",
                        "TextField",
                        "label",
                        "Name",
                        "value",
                        value)))));
    return new NodeResolver<>(surface()).rootNode().get().orElseThrow();
  }

  private SurfaceModel<ComponentApi> surface() {
    return processor.model().getSurface("s").orElseThrow();
  }

  /** Connects a text field to the property {@code value} of a node, as a view does. */
  private static TextBinding bind(JTextField component, ComponentNode<ComponentApi> node) {
    TextBinding binding = new TextBinding(component);
    binding.update(node.props().get(), "value");
    node.props().subscribe(props -> binding.update(props, "value"));
    return binding;
  }

  /** Counts the changes of the text of a component. */
  private static List<String> changesOf(JTextField component) {
    List<String> changes = new ArrayList<>();
    component
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent event) {
                changes.add("insert");
              }

              @Override
              public void removeUpdate(DocumentEvent event) {
                changes.add("remove");
              }

              @Override
              public void changedUpdate(DocumentEvent event) {}
            });
    return changes;
  }

  @Test
  void itShowsTheValueOfTheProperty() {
    Edt.run(() -> {
      JTextField component = new JTextField();

      bind(component, field(Map.of("@path", "/name")));

      assertEquals("Ada", component.getText());
    });
  }

  @Test
  void itShowsAValueThatIsNotAStringAsText() {
    Edt.run(() -> {
      JTextField component = new JTextField();

      bind(component, field(Map.of("@path", "/count")));

      assertEquals("3", component.getText());
    });
  }

  @Test
  void anEditOfTheUserWritesTheDataAndDoesNotTouchTheComponentAgain() {
    Edt.run(() -> {
      JTextField component = new JTextField();
      bind(component, field(Map.of("@path", "/name")));
      List<String> changes = changesOf(component);
      component.setCaretPosition(1);

      component.getDocument().insertString(1, "x", null);

      assertEquals("Axda", surface().dataModel().get("/name"));
      assertEquals("Axda", component.getText());
      assertEquals(List.of("insert"), changes);
      assertEquals(2, component.getCaretPosition());
    });
  }

  @Test
  void aChangeOfTheDataSetsTheTextOfTheComponent() {
    Edt.run(() -> {
      JTextField component = new JTextField();
      bind(component, field(Map.of("@path", "/name")));

      surface().dataModel().set("/name", "Grace");

      assertEquals("Grace", component.getText());
      assertEquals("Grace", surface().dataModel().get("/name"));
    });
  }

  @Test
  void aChangeOfTheDataToTheShownTextDoesNotTouchTheComponent() {
    Edt.run(() -> {
      JTextField component = new JTextField();
      TextBinding binding = bind(component, field(Map.of("@path", "/name")));
      List<String> changes = changesOf(component);

      binding.update("Ada", Optional.empty());

      assertEquals(List.of(), changes);
    });
  }

  @Test
  void anEditOfAPropertyWithNoDataPathChangesOnlyTheComponent() {
    Edt.run(() -> {
      JTextField component = new JTextField();
      bind(component, field("Literal"));

      component.setText("Edited");

      assertEquals("Edited", component.getText());
      assertEquals("Ada", surface().dataModel().get("/name"));
    });
  }

  @Test
  void aDisposedBindingWritesNothing() {
    Edt.run(() -> {
      JTextField component = new JTextField();
      TextBinding binding = bind(component, field(Map.of("@path", "/name")));

      binding.dispose();
      component.setText("Edited");

      assertEquals("Ada", surface().dataModel().get("/name"));
    });
  }

  @Test
  void aDifferentTextFromTheDataDuringAWriteArrivesAfterTheEdit() {
    JTextField component = new JTextField();
    Edt.run(() -> {
      ComponentNode<ComponentApi> node = field(Map.of("@path", "/name"));
      bind(component, node);
      // This listener changes the data again during the write of the edit, as a second component
      // of the surface can do.
      surface()
          .dataModel()
          .subscribe(
              "/name",
              value -> {
                if ("lower".equals(value)) {
                  surface().dataModel().set("/name", "LOWER");
                }
              });

      component.setText("lower");
    });
    Edt.flush();

    Edt.run(() -> {
      assertEquals("LOWER", surface().dataModel().get("/name"));
      assertEquals("LOWER", component.getText());
    });
  }
}
