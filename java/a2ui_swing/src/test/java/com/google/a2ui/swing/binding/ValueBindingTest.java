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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.core.resolution.NodeResolver;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.swing.testing.Edt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JCheckBox;
import org.junit.jupiter.api.Test;

class ValueBindingTest {
  private final MessageProcessor<ComponentApi> processor =
      new MessageProcessor<>(List.of(BasicCatalog.withFunctions()));

  /** The values that the binding gave to the check box. */
  private final List<Boolean> shown = new ArrayList<>();

  /** Makes a surface whose root is a check box, and gets the node of the check box. */
  private ComponentNode<ComponentApi> checkBox(Object value) {
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
                Map.of("agreed", false),
                "components",
                List.of(
                    Map.of(
                        "id",
                        "root",
                        "component",
                        "CheckBox",
                        "label",
                        "Agree",
                        "value",
                        value)))));
    return new NodeResolver<>(surface()).rootNode().get().orElseThrow();
  }

  private SurfaceModel<ComponentApi> surface() {
    return processor.model().getSurface("s").orElseThrow();
  }

  /** Connects a check box to the property {@code value} of a node, as a view does. */
  private ValueBinding<Boolean> bind(JCheckBox box, ComponentNode<ComponentApi> node) {
    ValueBinding<Boolean> binding =
        new ValueBinding<>(
            box::isSelected,
            selected -> {
              shown.add(selected);
              box.setSelected(selected);
            });
    box.addItemListener(event -> binding.edited(box.isSelected()));
    show(binding, node.props().get());
    node.props().subscribe(props -> show(binding, props));
    return binding;
  }

  private static void show(ValueBinding<Boolean> binding, NodeProps<?> props) {
    binding.update(Coercion.toBoolean(props.value("value")), props.writableBinding("value"));
  }

  @Test
  void itShowsTheValueOfTheProperty() {
    Edt.run(() -> {
      JCheckBox box = new JCheckBox();
      box.setSelected(true);

      ValueBinding<Boolean> binding = bind(box, checkBox(Map.of("@path", "/agreed")));

      assertFalse(box.isSelected());
      assertEquals(List.of(false), shown);
      assertTrue(binding.isWritable());
      assertFalse(binding.isShowing());
    });
  }

  @Test
  void aChangeOfTheUserWritesTheDataAndDoesNotTouchTheControlAgain() {
    Edt.run(() -> {
      JCheckBox box = new JCheckBox();
      bind(box, checkBox(Map.of("@path", "/agreed")));

      box.doClick(0);

      assertEquals(true, surface().dataModel().get("/agreed"));
      assertTrue(box.isSelected());
      // The value came back from the data model, and it was equal to the value of the control.
      assertEquals(List.of(), shown);
    });
  }

  @Test
  void aChangeOfTheDataSetsTheControlAndWritesNothing() {
    Edt.run(() -> {
      JCheckBox box = new JCheckBox();
      bind(box, checkBox(Map.of("@path", "/agreed")));
      List<Object> writes = new ArrayList<>();
      surface().dataModel().subscribe("/agreed", writes::add);

      surface().dataModel().set("/agreed", true);

      assertTrue(box.isSelected());
      assertEquals(List.of(true), shown);
      // Only the change of the data model itself. The control did not write it again.
      assertEquals(List.of(true), writes);
    });
  }

  @Test
  void aValueThatTheControlShowsDoesNotTouchTheControl() {
    Edt.run(() -> {
      JCheckBox box = new JCheckBox();
      ValueBinding<Boolean> binding = bind(box, checkBox(Map.of("@path", "/agreed")));
      shown.clear();

      binding.update(false, Optional.empty());

      assertEquals(List.of(), shown);
      assertFalse(binding.isWritable());
    });
  }

  @Test
  void aChangeOfAPropertyWithNoDataPathChangesOnlyTheControl() {
    Edt.run(() -> {
      JCheckBox box = new JCheckBox();
      ValueBinding<Boolean> binding = bind(box, checkBox(true));

      box.doClick(0);

      assertFalse(box.isSelected());
      assertFalse(binding.isWritable());
      assertEquals(false, surface().dataModel().get("/agreed"));
    });
  }

  @Test
  void aDisposedBindingWritesNothingAndDoesNotTouchTheControl() {
    Edt.run(() -> {
      JCheckBox box = new JCheckBox();
      ValueBinding<Boolean> binding = bind(box, checkBox(Map.of("@path", "/agreed")));

      binding.dispose();
      box.doClick(0);
      binding.update(false, Optional.empty());

      assertTrue(box.isSelected());
      assertFalse(binding.isWritable());
      assertEquals(false, surface().dataModel().get("/agreed"));
    });
  }

  @Test
  void aDifferentValueFromTheDataDuringAWriteArrivesAfterTheListener() {
    JCheckBox box = new JCheckBox();
    Edt.run(() -> {
      bind(box, checkBox(Map.of("@path", "/agreed")));
      // This listener changes the data again during the write, as a second component of the
      // surface can do.
      surface()
          .dataModel()
          .subscribe(
              "/agreed",
              value -> {
                if (Boolean.TRUE.equals(value)) {
                  surface().dataModel().set("/agreed", false);
                }
              });

      box.doClick(0);

      assertTrue(box.isSelected());
    });
    Edt.flush();

    Edt.run(() -> {
      assertEquals(false, surface().dataModel().get("/agreed"));
      assertFalse(box.isSelected());
      assertEquals(List.of(false), shown);
    });
  }
}
