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

package com.google.a2ui.core.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The behavior that the shared suite {@code core/catalog.yaml} does not examine. */
class CatalogTest {
  private final ComponentApi text = new ComponentApi("Text", Map.of("type", "object"));
  private final ComponentApi row = new ComponentApi("Row", Map.of("type", "object"));
  private final FunctionApi upper = new FunctionApi("upper", Map.of("returnType", "string"));

  @Test
  void aNewV1CatalogWritesItsUnions() {
    Catalog<ComponentApi, FunctionApi> catalog =
        new Catalog<>("urn:test", List.of(text), List.of(upper), null, ProtocolVersion.V1_0);

    Map<String, Object> expected =
        Map.of(
            "protocolVersion",
            "1.0",
            "catalogId",
            "urn:test",
            "components",
            Map.of("Text", Map.of("type", "object")),
            "functions",
            Map.of("upper", Map.of("returnType", "string")),
            "$defs",
            Map.of(
                "anyComponent",
                Map.of(
                    "oneOf",
                    List.of(Map.of("$ref", "#/components/Text")),
                    "discriminator",
                    Map.of("propertyName", "component")),
                "anyFunction",
                Map.of("oneOf", List.of(Map.of("$ref", "#/functions/upper")))));
    assertEquals(expected, catalog.toJson());
    assertEquals(expected, Catalog.fromJson(catalog.toJson()).toJson());
  }

  @Test
  void aNewV1CatalogWithNoEntriesWritesUnionsThatAcceptNothing() {
    Catalog<ComponentApi, FunctionApi> catalog =
        new Catalog<>("urn:test", List.of(), List.of(), null, ProtocolVersion.V1_0);

    Map<String, Object> nothing = Map.of("not", Map.of());
    assertEquals(
        Map.of("anyComponent", nothing, "anyFunction", nothing), catalog.toJson().get("$defs"));
  }

  @Test
  void aNewCatalogWithNoVersionWritesNoUnions() {
    Map<String, Object> theme = Map.of("type", "object");
    Catalog<ComponentApi, FunctionApi> catalog =
        new Catalog<>("urn:test", List.of(text), List.of(), theme, null);

    assertEquals(Optional.empty(), catalog.protocolVersion());
    assertEquals(Optional.of(theme), catalog.themeSchema());
    assertEquals(Optional.empty(), catalog.instructions());
    assertEquals(
        Map.of(
            "theme", theme, "catalogId", "urn:test", "components", Map.of("Text", text.schema())),
        catalog.toJson());
    assertEquals("Catalog(urn:test)", catalog.toString());
  }

  @Test
  void theEntriesKeepTheSequenceOfTheirDeclaration() {
    Catalog<ComponentApi, FunctionApi> catalog = new Catalog<>("urn:test", List.of(text, row));

    assertEquals(List.of("Text", "Row"), List.copyOf(catalog.components().keySet()));
    assertEquals(Map.of(), catalog.functions());
    assertThrows(UnsupportedOperationException.class, () -> catalog.components().clear());
  }

  @Test
  void twoEntriesWithTheSameNameAndAnEmptyIdAreRejected() {
    assertThrows(A2uiCatalogError.class, () -> new Catalog<>("urn:test", List.of(text, text)));
    assertThrows(
        A2uiCatalogError.class,
        () -> new Catalog<>("urn:test", List.of(text), List.of(upper, upper)));
    assertThrows(A2uiCatalogError.class, () -> new Catalog<>("", List.of(text)));
  }

  @Test
  void aThemeSchemaThatIsNotJsonIsRejected() {
    A2uiCatalogError error =
        assertThrows(
            A2uiCatalogError.class,
            () ->
                new Catalog<>(
                    "urn:test", List.of(text), List.of(), Map.of("default", new Object()), null));

    assertTrue(
        error.getMessage().startsWith("The theme schema of catalog 'urn:test' is not JSON: "),
        error.getMessage());
  }

  @Test
  void withFunctionsReplacesTheFunctionsAndKeepsAllOtherParts() {
    Catalog<ComponentApi, FunctionApi> catalog =
        new Catalog<>("urn:test", List.of(text), List.of(upper), null, ProtocolVersion.V1_0);
    FunctionImplementation runnable =
        new FunctionImplementation(upper, (args, context) -> "RESULT");

    Catalog<ComponentApi, FunctionImplementation> result = catalog.withFunctions(List.of(runnable));

    assertEquals("urn:test", result.id());
    assertEquals(Optional.of(ProtocolVersion.V1_0), result.protocolVersion());
    assertEquals(catalog.components(), result.components());
    assertEquals(Map.of("upper", runnable), result.functions());
    assertEquals(catalog.toJson(), result.toJson());
    assertEquals(Map.of("upper", upper), catalog.functions());
  }

  @Test
  void withFunctionsRejectsTwoFunctionsWithTheSameName() {
    Catalog<ComponentApi, FunctionApi> catalog = new Catalog<>("urn:test", List.of(text));

    assertThrows(A2uiCatalogError.class, () -> catalog.withFunctions(List.of(upper, upper)));
  }

  @Test
  void withComponentsReplacesTheComponentsAndKeepsAllOtherParts() {
    Catalog<ComponentApi, FunctionApi> catalog =
        new Catalog<>("urn:test", List.of(text), List.of(upper), null, ProtocolVersion.V1_0);
    ComponentApi view = new ComponentApi("Text", text.schema()) {};

    Catalog<ComponentApi, FunctionApi> result = catalog.withComponents(List.of(view));

    assertEquals("urn:test", result.id());
    assertEquals(Optional.of(ProtocolVersion.V1_0), result.protocolVersion());
    assertSame(view, result.components().get("Text"));
    assertEquals(catalog.functions(), result.functions());
    assertEquals(catalog.toJson(), result.toJson());
    assertSame(text, catalog.components().get("Text"));
  }

  @Test
  void withComponentsRejectsTwoComponentsWithTheSameName() {
    Catalog<ComponentApi, FunctionApi> catalog = new Catalog<>("urn:test", List.of(text));

    assertThrows(A2uiCatalogError.class, () -> catalog.withComponents(List.of(text, text)));
  }

  @Test
  void deriveKeepsTheNamedEntriesAndAddsTheNewEntries() {
    Catalog<ComponentApi, FunctionApi> source =
        new Catalog<>("urn:source", List.of(text, row), List.of(upper), null, ProtocolVersion.V1_0);
    ComponentApi newRow = new ComponentApi("Row", Map.of("type", "object", "title", "New"));
    FunctionApi lower = new FunctionApi("lower", Map.of());

    Catalog<ComponentApi, FunctionApi> derived =
        source.derive(
            "urn:derived", List.of("Row"), List.of(newRow), null, List.of(lower), "Use it.");

    assertEquals("urn:derived", derived.id());
    assertEquals(Optional.of(ProtocolVersion.V1_0), derived.protocolVersion());
    assertEquals(Map.of("Row", newRow), derived.components());
    assertEquals(List.of("upper", "lower"), List.copyOf(derived.functions().keySet()));
    assertEquals(Optional.of("Use it."), derived.instructions());
    assertEquals(source.components(), source.derive("urn:all").components());
    assertEquals(
        List.of("Text"),
        List.copyOf(source.derive("urn:text", List.of("Text")).components().keySet()));
  }

  @Test
  void deriveRejectsANameThatIsNotAnEntryAndAnEmptyId() {
    Catalog<ComponentApi, FunctionApi> source = new Catalog<>("urn:source", List.of(text));

    A2uiCatalogError error =
        assertThrows(A2uiCatalogError.class, () -> source.derive("urn:derived", List.of("Slider")));

    assertEquals(
        "Catalog 'urn:source' has no component 'Slider' to derive catalog 'urn:derived' from.",
        error.getMessage());
    assertThrows(A2uiCatalogError.class, () -> source.derive(""));
  }

  @Test
  void documentsWithAMemberOfAnIncorrectTypeAreRejected() {
    assertThrows(A2uiCatalogError.class, () -> load("components", List.of("Text")));
    assertThrows(A2uiCatalogError.class, () -> load("components", Map.of("Text", "object")));
    assertThrows(A2uiCatalogError.class, () -> load("functions", List.of(Map.of("name", "f"))));
    assertThrows(A2uiCatalogError.class, () -> load("$defs", "none"));
    assertThrows(A2uiCatalogError.class, () -> load("protocolVersion", "7.3"));
    assertThrows(A2uiCatalogError.class, () -> load("protocolVersion", 1.0));
    assertThrows(A2uiCatalogError.class, () -> load("catalogId", ""));
    assertThrows(A2uiCatalogError.class, () -> load("theme", new Object()));
  }

  @Test
  void aLocalReferenceThatNamesNothingIsRejected() {
    Map<String, Object> component = Map.of("properties", Map.of("a", Map.of("$ref", "#/$defs/No")));

    A2uiCatalogError error =
        assertThrows(A2uiCatalogError.class, () -> load("components", Map.of("Text", component)));

    assertEquals(
        "Catalog 'urn:test' has a $ref '#/$defs/No' at /components/Text/properties/a/$ref that"
            + " names nothing.",
        error.getMessage());
  }

  @Test
  void aLocalReferenceCanNameACommonType() {
    Map<String, Object> component =
        Map.of("properties", Map.of("a", Map.of("$ref", "#/$defs/DynamicString")));
    Map<String, Object> inside =
        Map.of("properties", Map.of("a", Map.of("$ref", "#/$defs/Action/properties/event")));

    assertEquals(1, load("components", Map.of("Text", component)).components().size());
    assertThrows(A2uiCatalogError.class, () -> load("components", Map.of("Text", inside)));
  }

  @Test
  void aLocalReferenceCanNameAnItemOfAList() {
    Map<String, Object> component =
        Map.of(
            "oneOf",
            List.of(Map.of("type", "object")),
            "properties",
            Map.of("a", Map.of("$ref", "#/components/Text/oneOf/0")));
    Map<String, Object> dangling =
        Map.of(
            "oneOf",
            List.of(Map.of("type", "object")),
            "properties",
            Map.of("a", Map.of("$ref", "#/components/Text/oneOf/1")));

    assertEquals(1, load("components", Map.of("Text", component)).components().size());
    assertThrows(A2uiCatalogError.class, () -> load("components", Map.of("Text", dangling)));
  }

  @Test
  void thePropertyNamesThroughLocalReferencesAreExamined() {
    Map<String, Object> document =
        Map.of(
            "catalogId",
            "urn:test",
            "protocolVersion",
            "1.0",
            "components",
            Map.of("Box", Map.of("allOf", List.of(Map.of("$ref", "#/$defs/Mixin")))),
            "$defs",
            Map.of("Mixin", Map.of("properties", Map.of("bad-name", true))));

    A2uiCatalogError error = assertThrows(A2uiCatalogError.class, () -> Catalog.fromJson(document));

    assertEquals(
        "Invalid UAX #31 property identifier in component 'Box' 'bad-name' in catalog 'urn:test'.",
        error.getMessage());
  }

  @Test
  void theArgumentNamesOfAFunctionAreExamined() {
    Map<String, Object> shorthand = Map.of("parameters", Map.of("@value", true, "bad-name", true));
    Map<String, Object> callSchema =
        Map.of("properties", Map.of("args", Map.of("properties", Map.of("@value", true))));

    A2uiCatalogError error =
        assertThrows(A2uiCatalogError.class, () -> load("functions", Map.of("f", shorthand)));

    assertTrue(error.getMessage().contains("argument identifier in function 'f' 'bad-name'"));
    assertEquals(1, load("functions", Map.of("f", callSchema)).functions().size());
  }

  @Test
  void aFunctionNameInTheReservedNamespaceIsRejected() {
    A2uiCatalogError error =
        assertThrows(A2uiCatalogError.class, () -> load("functions", Map.of("@index", Map.of())));

    assertTrue(error.getMessage().contains("names starting with '@' are reserved"));
  }

  @Test
  void aMetadataExtensionKeyThatIsNotAnIdentifierIsAValidationError() {
    Map<String, Object> component =
        Map.of("metadata", Map.of("extensions", Map.of("bad-key", true)));

    assertThrows(A2uiValidationError.class, () -> load("components", Map.of("Text", component)));
  }

  @Test
  void theVersionOfTheCallAppliesOnlyIfTheDocumentDeclaresNone() {
    Map<String, Object> unversioned =
        Map.of("catalogId", "urn:test", "components", Map.of("a-b", Map.of()));
    Map<String, Object> declared = new LinkedHashMap<>(unversioned);
    declared.put("protocolVersion", "v0.9");

    assertEquals(Optional.empty(), Catalog.fromJson(unversioned).protocolVersion());
    assertEquals(
        Optional.of(ProtocolVersion.V0_9),
        Catalog.fromJson(unversioned, null, ProtocolVersion.V0_9).protocolVersion());
    assertThrows(
        A2uiCatalogError.class, () -> Catalog.fromJson(unversioned, null, ProtocolVersion.V1_0));
    assertEquals(
        Optional.of(ProtocolVersion.V0_9),
        Catalog.fromJson(declared, null, ProtocolVersion.V1_0).protocolVersion());
  }

  @Test
  void theIdOfTheCallReplacesTheIdOfTheDocument() {
    Map<String, Object> document = Map.of("catalogId", "urn:document");

    Catalog<ComponentApi, FunctionApi> catalog = Catalog.fromJson(document, "urn:call", null);

    assertEquals("urn:call", catalog.id());
    assertEquals(Map.of("catalogId", "urn:call"), catalog.toJson());
    assertThrows(A2uiCatalogError.class, () -> Catalog.fromJson(Map.of()));
    assertFalse(Catalog.fromJson(Map.of(), "urn:call", null).toJson().containsKey("catalogId"));
  }

  @Test
  void aUnionThatListsSomeEntriesLimitsTheCatalog() {
    Map<String, Object> document =
        Map.of(
            "catalogId",
            "urn:test",
            "components",
            Map.of("Text", Map.of(), "Row", Map.of()),
            "$defs",
            Map.of("anyComponent", Map.of("oneOf", List.of(Map.of("$ref", "#/components/Row")))));

    Catalog<ComponentApi, FunctionApi> catalog = Catalog.fromJson(document);

    assertEquals(List.of("Row"), List.copyOf(catalog.components().keySet()));
  }

  /** Loads a v1.0 document that has the given member and the ID {@code urn:test}. */
  private static Catalog<ComponentApi, FunctionApi> load(String key, Object value) {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("catalogId", "urn:test");
    document.put("protocolVersion", "1.0");
    document.put(key, value);
    return Catalog.fromJson(document);
  }
}
