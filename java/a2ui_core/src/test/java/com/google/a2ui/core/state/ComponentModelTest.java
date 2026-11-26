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

package com.google.a2ui.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.A2uiMetadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ComponentModelTest {
  @Test
  void aComponentKeepsItsValues() {
    A2uiMetadata metadata = new A2uiMetadata(Optional.of(Map.of("vendor", 1L)));
    ComponentModel component =
        new ComponentModel(
            "title",
            "Text",
            BasicCatalog.api(),
            Map.of("text", "Hello"),
            metadata,
            Map.of("label", "Greeting"),
            List.of(Map.of("message", "Required")));

    assertEquals("title", component.id());
    assertEquals("Text", component.type());
    assertSame(BasicCatalog.api(), component.catalog());
    assertEquals(Map.of("text", "Hello"), component.properties());
    assertEquals(Optional.of(metadata), component.metadata());
    assertEquals(Optional.of(Map.of("label", "Greeting")), component.accessibility());
    assertEquals(Optional.of(List.of(Map.of("message", "Required"))), component.checks());
    assertEquals("ComponentModel(title: Text)", component.toString());
  }

  @Test
  void aComponentWithNoEnvelopeHasEmptyResults() {
    ComponentModel component = text("Hello");

    assertEquals(Optional.empty(), component.metadata());
    assertEquals(Optional.empty(), component.accessibility());
    assertEquals(Optional.empty(), component.checks());
  }

  @Test
  void aComponentMakesAReadOnlyCopyOfItsProperties() {
    Map<String, Object> properties = new HashMap<>(Map.of("text", "Hello", "count", 1));
    ComponentModel component =
        new ComponentModel("t", "Text", BasicCatalog.api(), properties, null, null, null);

    properties.put("text", "Changed");

    assertEquals(Map.of("text", "Hello", "count", 1L), component.properties());
    assertThrows(
        UnsupportedOperationException.class, () -> component.properties().put("text", "x"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ComponentModel(
                "t", "Text", BasicCatalog.api(), Map.of("text", new Object()), null, null, null));
  }

  @Test
  void aNewDefinitionNotifiesTheListenersOnlyIfItIsDifferent() {
    ComponentModel component = text("one");
    List<ComponentModel> events = new ArrayList<>();
    component.onUpdated().subscribe(events::add);

    component.replaceDefinition(Map.of("text", "one"), null, null, null);
    assertEquals(List.of(), events);

    component.replaceDefinition(Map.of("text", "two"), null, null, null);
    component.replaceDefinition(Map.of("text", "two"), null, Map.of("label", "L"), null);

    assertEquals(List.of(component, component), events);
    assertEquals(Map.of("text", "two"), component.properties());
    assertEquals(Optional.of(Map.of("label", "L")), component.accessibility());
  }

  @Test
  void disposeRemovesTheListeners() {
    ComponentModel component = text("one");
    List<ComponentModel> events = new ArrayList<>();
    component.onUpdated().subscribe(events::add);

    component.dispose();
    component.replaceDefinition(Map.of("text", "two"), null, null, null);

    assertEquals(List.of(), events);
  }

  private static ComponentModel text(String value) {
    return new ComponentModel(
        "t", "Text", BasicCatalog.api(), Map.of("text", value), null, null, null);
  }
}
