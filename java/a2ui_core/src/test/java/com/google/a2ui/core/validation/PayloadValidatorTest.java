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

package com.google.a2ui.core.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The behavior that the shared suites {@code core/catalog.yaml} and {@code core/common_types.yaml}
 * do not examine.
 */
class PayloadValidatorTest {
  private final PayloadValidator validator = new PayloadValidator(BasicCatalog.api());

  @Test
  void aCorrectComponentIsAccepted() {
    validator.validateComponent(text());
    validator.validateComponent(text("text", Map.of("@path", "/greeting")));
    validator.validateComponent(
        Map.of("id", "c", "component", "Column", "children", List.of("t1", "t2")));
    Map<String, Object> template = Map.of("componentId", "row", "path", "/items");
    validator.validateComponent(Map.of("id", "l", "component", "List", "children", template));
  }

  @Test
  void theMessageNamesTheComponentAndEachProblem() {
    A2uiValidationError error =
        assertThrows(
            A2uiValidationError.class,
            () -> validator.validateComponent(text("text", 3, "unknownProperty", true)));

    String message = error.getMessage();
    assertTrue(message.startsWith("Component 't1' (Text) is invalid: "), message);
    assertTrue(message.contains("/text: "), message);
    assertTrue(message.contains("unknownProperty"), message);
  }

  @Test
  void aTypeThatIsUnknownOrAbsentIsRejected() {
    A2uiValidationError unknown =
        assertThrows(
            A2uiValidationError.class,
            () -> validator.validateComponent(Map.of("id", "x", "component", "Carousel")));
    A2uiValidationError absent =
        assertThrows(
            A2uiValidationError.class, () -> validator.validateComponent(Map.of("id", "x")));
    A2uiValidationError notAString =
        assertThrows(
            A2uiValidationError.class, () -> validator.validateComponent(Map.of("component", 7)));

    assertEquals(
        "Component 'x' (Carousel) is invalid: /component: Unrecognized component type 'Carousel' in catalog '"
            + BasicCatalog.ID
            + "'",
        unknown.getMessage());
    assertEquals(
        "Component 'x' (?) is invalid: /component: 'component' must name a type",
        absent.getMessage());
    assertEquals(
        "Component '?' (7) is invalid: /component: 'component' must name a type",
        notAString.getMessage());
  }

  @Test
  void aComponentMustHaveAnId() {
    assertThrows(
        A2uiValidationError.class,
        () -> validator.validateComponent(Map.of("component", "Text", "text", "Hello")));
  }

  @Test
  void theEnvelopeOfAComponentIsAccepted() {
    validator.validateComponent(
        text(
            "@catalogId",
            BasicCatalog.ID,
            "@accessibility",
            Map.of("label", "Greeting"),
            "@metadata",
            Map.of("extensions", Map.of("vendor_tag", 1))));
    assertThrows(
        A2uiValidationError.class,
        () -> validator.validateComponent(text("@metadata", Map.of("owner", "me"))));
    assertThrows(
        A2uiValidationError.class, () -> validator.validateComponent(text("@catalogId", 7)));
  }

  @Test
  void anExtensionKeyMustBeAUnicodeIdentifier() {
    validator.validateComponent(withExtension("vendor_tag"));
    validator.validateComponent(withExtension("größe"));
    // U+20BB7 is not in the Basic Multilingual Plane.
    validator.validateComponent(withExtension("𠮷"));
    assertThrows(
        A2uiValidationError.class, () -> validator.validateComponent(withExtension("has-dash")));
    assertThrows(
        A2uiValidationError.class, () -> validator.validateComponent(withExtension("1st")));
  }

  @Test
  void aCallWithNoCatalogIsNotExaminedForAComponentThatNamesItsCatalog() {
    Map<String, Object> call = Map.of("@call", "notInThisCatalog", "args", Map.of());

    A2uiValidationError error =
        assertThrows(
            A2uiValidationError.class, () -> validator.validateComponent(text("text", call)));
    validator.validateComponent(text("text", call, "@catalogId", "urn:other"));

    assertTrue(
        error.getMessage().contains("/text: Unrecognized function 'notInThisCatalog' in catalog '"),
        error.getMessage());
  }

  @Test
  void aCallThatNamesADifferentCatalogIsExaminedForItsNamesOnly() {
    validator.validateComponent(
        text("text", Map.of("@call", "shout", "catalogId", "urn:other", "args", Map.of("x", 1))));

    A2uiValidationError error =
        assertThrows(
            A2uiValidationError.class,
            () ->
                validator.validateComponent(
                    text(
                        "text",
                        Map.of(
                            "@call",
                            "sh-out",
                            "catalogId",
                            "urn:other",
                            "args",
                            Map.of("a-b", 1)))));

    String message = error.getMessage();
    assertTrue(
        message.contains("function name 'sh-out' is not a valid UAX #31 identifier"), message);
    assertTrue(
        message.contains("argument name 'a-b' of 'sh-out' is not a valid UAX #31 identifier"),
        message);
  }

  @Test
  void aSystemFunctionIsExaminedAgainstTheProtocol() {
    validator.validateComponent(text("text", Map.of("@call", "@index")));
    validator.validateComponent(
        text("text", Map.of("@call", "@index", "args", Map.of("offset", 1))));

    A2uiValidationError unknown =
        assertThrows(
            A2uiValidationError.class,
            () -> validator.validateComponent(text("text", Map.of("@call", "@now"))));
    A2uiValidationError withCatalog =
        assertThrows(
            A2uiValidationError.class,
            () ->
                validator.validateComponent(
                    text("text", Map.of("@call", "@index", "catalogId", BasicCatalog.ID))));
    assertThrows(
        A2uiValidationError.class,
        () ->
            validator.validateComponent(
                text("text", Map.of("@call", "@index", "args", Map.of("step", 2)))));

    assertTrue(unknown.getMessage().contains("unknown system function '@now'"));
    assertTrue(
        withCatalog.getMessage().contains("system function '@index' must not name a catalogId"));
  }

  @Test
  void aFunctionCallIsExaminedAgainstItsSchema() {
    validator.validateFunction("openUrl", Map.of("url", "https://example.com"));
    validator.validateFunction("openUrl", Map.of("url", Map.of("@path", "/link")));

    A2uiValidationError noArgument =
        assertThrows(
            A2uiValidationError.class, () -> validator.validateFunction("openUrl", Map.of()));
    A2uiValidationError unknown =
        assertThrows(
            A2uiValidationError.class, () -> validator.validateFunction("teleport", Map.of()));
    A2uiValidationError nested =
        assertThrows(
            A2uiValidationError.class,
            () ->
                validator.validateFunction("openUrl", Map.of("url", Map.of("@call", "teleport"))));
    assertThrows(
        A2uiValidationError.class,
        () ->
            validator.validateFunction(
                "openUrl", Map.of("url", "https://example.com", "extra", 1)));

    assertTrue(noArgument.getMessage().startsWith("Call to 'openUrl' is invalid: "));
    assertEquals(
        "Call to 'teleport' is invalid: Unrecognized function 'teleport' in catalog '"
            + BasicCatalog.ID
            + "'",
        unknown.getMessage());
    assertTrue(nested.getMessage().contains("/args/url: Unrecognized function 'teleport'"));
  }

  @Test
  void aValueThatIsNotJsonIsRejected() {
    A2uiValidationError error =
        assertThrows(
            A2uiValidationError.class,
            () -> validator.validateComponent(text("text", new Object())));

    assertTrue(error.getMessage().startsWith("The value to validate is not JSON: "));
  }

  @Test
  void onlyAV1CatalogHasAValidator() {
    ComponentApi box = new ComponentApi("Box", Map.of("type", "object"));

    assertThrows(
        A2uiCatalogError.class, () -> new PayloadValidator(new Catalog<>("urn:old", List.of(box))));
    PayloadValidator boxes =
        new PayloadValidator(
            new Catalog<>("urn:new", List.of(box), List.of(), null, ProtocolVersion.V1_0));
    boxes.validateComponent(Map.of("id", "b", "component", "Box"));
    assertThrows(
        A2uiValidationError.class,
        () -> boxes.validateComponent(Map.of("id", "b", "component", "Box", "undeclared", 1)));
  }

  @Test
  void aComponentThatClosesItselfAcceptsTheEnvelope() {
    Catalog<ComponentApi, FunctionApi> catalog =
        Catalog.fromJson(
            Json.asObject(
                Json.parse(
                    """
                    {
                      "catalogId": "urn:closed",
                      "protocolVersion": "1.0",
                      "components": {
                        "Badge": {
                          "type": "object",
                          "allOf": [
                            {"$ref": "common_types.json#/$defs/ComponentCommon"},
                            {
                              "properties": {
                                "component": {"const": "Badge"},
                                "label": {"type": "string"}
                              },
                              "required": ["component", "label"]
                            }
                          ],
                          "unevaluatedProperties": false
                        },
                        "Open": {"type": "object", "unevaluatedProperties": true}
                      }
                    }
                    """)));
    PayloadValidator badges = new PayloadValidator(catalog);

    badges.validateComponent(
        Map.of("id", "b", "component", "Badge", "label", "New", "@catalogId", "urn:closed"));
    badges.validateComponent(Map.of("id", "o", "component", "Open", "anything", 1));
    assertThrows(
        A2uiValidationError.class,
        () -> badges.validateComponent(Map.of("id", "b", "component", "Badge", "label", 1)));
    assertThrows(
        A2uiValidationError.class,
        () ->
            badges.validateComponent(
                Map.of("id", "b", "component", "Badge", "label", "New", "extra", 1)));
  }

  @Test
  void aReferenceOfTheCatalogThatNamesNothingIsACatalogError() {
    ComponentApi box =
        new ComponentApi(
            "Box", Map.of("properties", Map.of("size", Map.of("$ref", "#/$defs/Missing"))));
    PayloadValidator boxes =
        new PayloadValidator(
            new Catalog<>("urn:new", List.of(box), List.of(), null, ProtocolVersion.V1_0));

    assertThrows(
        A2uiCatalogError.class,
        () -> boxes.validateComponent(Map.of("id", "b", "component", "Box", "size", 1)));
  }

  /** Makes a Text component with the ID {@code t1} and with more members. */
  private static Map<String, Object> text(Object... keysAndValues) {
    Map<String, Object> component = new LinkedHashMap<>();
    component.put("id", "t1");
    component.put("component", "Text");
    component.put("text", "Hello");
    for (int i = 0; i < keysAndValues.length; i += 2) {
      component.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return component;
  }

  private static Map<String, Object> withExtension(String key) {
    return text("@metadata", Map.of("extensions", Map.of(key, 1)));
  }
}
