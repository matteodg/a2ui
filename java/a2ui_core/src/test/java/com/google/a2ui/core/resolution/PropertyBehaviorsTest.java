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

package com.google.a2ui.core.resolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.resolution.PropertyBehavior.Dynamic;
import com.google.a2ui.core.resolution.PropertyBehavior.Sequence;
import com.google.a2ui.core.resolution.PropertyBehavior.Structure;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PropertyBehaviorsTest {
  private static final Dynamic VALUE = new Dynamic(false);
  private static final Dynamic FLAG = new Dynamic(true);

  private final PropertyBehaviors basic = new PropertyBehaviors(BasicCatalog.api());

  private static Map<String, Object> ref(String name) {
    return Map.of("$ref", "common_types.json#/$defs/" + name);
  }

  /** Makes the reader for a catalog that has one component type, {@code Probe}. */
  private static PropertyBehavior.Structure probe(Map<String, Object> properties) {
    Map<String, Object> component = Map.of("type", "object", "properties", properties);
    Map<String, Object> document =
        Map.of(
            "catalogId", "urn:test:probe",
            "protocolVersion", "1.0",
            "components", Map.of("Probe", component));
    return new PropertyBehaviors(Catalog.fromJson(document)).component("Probe");
  }

  @Test
  void aPropertyOfADynamicTypeIsDynamic() {
    assertEquals(VALUE, basic.component("Text").member("text"));
    assertEquals(VALUE, basic.component("ChoicePicker").member("value"));
    assertEquals(VALUE, basic.component("Slider").member("value"));
  }

  @Test
  void aPropertyOfTheTypeDynamicBooleanIsABoolean() {
    assertEquals(FLAG, basic.component("CheckBox").member("value"));
  }

  @Test
  void aPropertyWithNoDynamicTypeIsStatic() {
    assertSame(PropertyBehavior.STATIC, basic.component("Text").member("variant"));
    assertSame(PropertyBehavior.STATIC, basic.component("Text").member("notInTheSchema"));
  }

  @Test
  void aPropertyOfTheTypeActionIsAnAction() {
    assertSame(PropertyBehavior.ACTION, basic.component("Button").member("action"));
  }

  @Test
  void aPropertyOfAChildTypeContainsChildren() {
    assertSame(PropertyBehavior.CHILD, basic.component("Button").member("child"));
    assertSame(PropertyBehavior.CHILD, basic.component("Modal").member("trigger"));
    assertSame(PropertyBehavior.CHILD_LIST, basic.component("Column").member("children"));
  }

  @Test
  void aListOfObjectsGivesTheBehaviorOfEachMember() {
    Sequence tabs = (Sequence) basic.component("Tabs").member("tabs");
    Structure tab = (Structure) tabs.element();

    assertEquals(VALUE, tab.member("title"));
    assertSame(PropertyBehavior.CHILD, tab.member("child"));
    assertSame(PropertyBehavior.STATIC, tab.member("other"));
  }

  @Test
  void theAccessibilityAttributesHaveTheirOwnBehaviors() {
    Structure attributes = (Structure) PropertyBehaviors.accessibility();

    assertEquals(VALUE, attributes.member("label"));
    assertEquals(VALUE, attributes.member("description"));
    assertEquals(FLAG, attributes.member("hidden"));
    assertSame(PropertyBehavior.STATIC, attributes.member("live"));
  }

  @Test
  void anUnknownComponentTypeHasOnlyStaticProperties() {
    Structure unknown = basic.component("NoSuchType");

    assertEquals(Map.of(), unknown.properties());
    assertSame(PropertyBehavior.STATIC, unknown.member("text"));
  }

  @Test
  void aDirectReferenceToABindingOrToACallIsDynamic() {
    Structure probe = probe(Map.of("bound", ref("DataBinding"), "computed", ref("FunctionCall")));

    assertEquals(VALUE, probe.member("bound"));
    assertEquals(VALUE, probe.member("computed"));
  }

  @Test
  void anInlineUnionIsABooleanOnlyIfEachLiteralTypeIsBoolean() {
    Map<String, Object> flag =
        Map.of(
            "oneOf", List.of(Map.of("type", "boolean"), ref("DataBinding"), ref("FunctionCall")));
    Map<String, Object> optionalFlag =
        Map.of(
            "anyOf",
            List.of(Map.of("type", "null"), Map.of("type", "boolean"), ref("DataBinding")));
    Map<String, Object> mixed =
        Map.of(
            "oneOf",
            List.of(Map.of("type", "boolean"), Map.of("type", "string"), ref("DataBinding")));
    Map<String, Object> noLiteral = Map.of("oneOf", List.of(ref("DataBinding")));

    Structure probe =
        probe(
            Map.of("flag", flag, "optionalFlag", optionalFlag, "mixed", mixed, "none", noLiteral));

    assertEquals(FLAG, probe.member("flag"));
    assertEquals(FLAG, probe.member("optionalFlag"));
    assertEquals(VALUE, probe.member("mixed"));
    assertEquals(VALUE, probe.member("none"));
  }

  @Test
  void thePropertyNameDoesNotGiveTheBehavior() {
    Map<String, Object> option =
        Map.of(
            "type",
            "object",
            "properties",
            Map.of("event", Map.of("type", "string"), "child", ref("DynamicString")));
    Structure probe =
        probe(
            Map.of(
                "event", Map.of("type", "string"),
                "action", Map.of("type", "string"),
                "children", Map.of("type", "array", "items", Map.of("type", "string")),
                "options", Map.of("type", "array", "items", option),
                "press", ref("Action")));

    assertSame(PropertyBehavior.STATIC, probe.member("event"));
    assertSame(PropertyBehavior.STATIC, probe.member("action"));
    assertEquals(new Sequence(PropertyBehavior.STATIC), probe.member("children"));
    Structure item = (Structure) ((Sequence) probe.member("options")).element();
    assertSame(PropertyBehavior.STATIC, item.member("event"));
    assertEquals(VALUE, item.member("child"));
    assertSame(PropertyBehavior.ACTION, probe.member("press"));
  }

  @Test
  void theSchemaOfTheOtherMembersGivesTheBehaviorOfAMemberWithNoName() {
    Map<String, Object> labels =
        Map.of("type", "object", "additionalProperties", ref("DynamicString"));

    Structure probe = probe(Map.of("labels", labels));

    assertEquals(VALUE, ((Structure) probe.member("labels")).member("anyName"));
  }
}
