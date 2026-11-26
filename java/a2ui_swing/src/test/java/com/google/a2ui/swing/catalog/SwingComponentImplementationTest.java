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

package com.google.a2ui.swing.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.common.ProtocolVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class SwingComponentImplementationTest {
  private static final Map<String, Object> SCHEMA =
      Map.of("type", "object", "properties", Map.of("text", Map.of("type", "string")));

  @Test
  void theFactoryKeepsTheNameAndTheSchemaOfTheDefinition() {
    ComponentApi api = new ComponentApi("Badge", SCHEMA);

    SwingComponentImplementation badge =
        SwingComponentImplementation.of(
            api, context -> SwingComponentView.of(new JLabel(), (node, props) -> {}));

    assertEquals("Badge", badge.name());
    assertEquals(SCHEMA, badge.schema());
    assertEquals("SwingComponentImplementation(Badge)", badge.toString());
  }

  @Test
  void theFactoryCallsItsFunctionForEachView() {
    List<ViewContext> contexts = new ArrayList<>();
    JLabel label = new JLabel();
    SwingComponentImplementation badge =
        SwingComponentImplementation.of(
            "Badge",
            SCHEMA,
            context -> {
              contexts.add(context);
              return SwingComponentView.of(label, (node, props) -> {});
            });

    SwingComponentView view = badge.createView(null);

    assertSame(label, view.component());
    assertEquals(1, contexts.size());
  }

  @Test
  void aViewFromFunctionsCallsItsFunctions() {
    List<String> calls = new ArrayList<>();
    SwingComponentView view =
        SwingComponentView.of(
            new JLabel(), (node, props) -> calls.add("update"), () -> calls.add("dispose"));

    view.update(null, null);
    view.dispose();

    assertEquals(List.of("update", "dispose"), calls);
  }

  @Test
  void aCatalogOfTheCoreCanGetSwingImplementations() {
    ComponentApi api = new ComponentApi("Badge", SCHEMA);
    Catalog<ComponentApi, FunctionApi> described =
        new Catalog<>("urn:test", List.of(api), List.of(), null, ProtocolVersion.V1_0);
    SwingComponentImplementation badge =
        SwingComponentImplementation.of(
            api, context -> SwingComponentView.of(new JLabel(), (node, props) -> {}));

    Catalog<SwingComponentImplementation, FunctionApi> catalog =
        described.withComponents(List.of(badge));

    assertSame(badge, catalog.components().get("Badge"));
  }

  @Test
  void theFactoryRejectsNullParts() {
    assertThrows(
        NullPointerException.class, () -> SwingComponentImplementation.of("Badge", SCHEMA, null));
    assertThrows(
        NullPointerException.class, () -> SwingComponentView.of(null, (node, props) -> {}));
  }
}
