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

package com.google.a2ui.core.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiDataError;
import com.google.a2ui.core.exceptions.A2uiIntegrityError;
import com.google.a2ui.core.exceptions.A2uiRecursionError;
import com.google.a2ui.core.exceptions.A2uiStateError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.state.ComponentModel;
import com.google.a2ui.core.state.SurfaceComponentsModel;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.core.validation.ValidationConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The behavior of {@link MessageProcessor} that the shared suites do not examine. The suites of the
 * message processor and of the validator examine the other behavior.
 */
class MessageProcessorTest {
  private final MessageProcessor<ComponentApi> processor =
      new MessageProcessor<>(List.of(BasicCatalog.api()));

  @Test
  void aPayloadIsOneMessageOrAListOrAWrapper() {
    processor.processMessages(create());
    processor.processMessages(List.of(update(text("root", "one"))));
    processor.processMessages(Map.of("messages", List.of(update(text("root", "two")))));
    processor.processMessages(null);
    processor.processMessages(List.of());

    assertEquals(Map.of("text", "two"), component("root").properties());
    assertThrows(A2uiValidationError.class, () -> processor.processMessages("createSurface"));
  }

  @Test
  void theOtherKeysOfAWrapperAreIgnoredButTheirPathsAreExamined() {
    processor.processMessages(map("messages", List.of(create()), "note", Map.of("@path", "/a~0b")));

    A2uiValidationError error =
        assertThrows(
            A2uiValidationError.class,
            () ->
                processor.processMessages(
                    map("messages", List.of(delete()), "note", Map.of("@path", "/a~2b"))));

    assertEquals(
        "Invalid path syntax in the message wrapper: '/a~2b' has a '~' that is not followed by"
            + " '0' or '1'.",
        error.getMessage());
    assertTrue(surface().isPresent());
  }

  @Test
  void theContainersAroundAMessageHaveADepthLimit() {
    Object atTheLimit = nested(delete(), 50);
    Object pastTheLimit = nested(delete(), 51);
    processor.processMessages(create());

    A2uiRecursionError error =
        assertThrows(A2uiRecursionError.class, () -> processor.processMessages(pastTheLimit));

    assertTrue(error.getMessage().startsWith("Global recursion limit exceeded: "));
    assertTrue(surface().isPresent());
    processor.processMessages(atTheLimit);
    assertFalse(surface().isPresent());
  }

  @Test
  void aMessageHasADepthLimitThatStartsAtTheMessage() {
    processor.processMessages(create());
    // The value of `updateDataModel` has the depth 2. Thus the scalar has the depth 50.
    Object atTheLimit = nested("leaf", 48);
    Object pastTheLimit = nested("leaf", 49);

    processor.processMessages(List.of(List.of(dataUpdate("/deep", atTheLimit))));
    A2uiRecursionError error =
        assertThrows(
            A2uiRecursionError.class,
            () -> processor.processMessages(dataUpdate("/deeper", pastTheLimit)));

    assertEquals(
        "Global recursion limit exceeded: the updateDataModel message nests values deeper than 50"
            + " levels.",
        error.getMessage());
    assertEquals(
        Set.of("deep"), ((Map<?, ?>) surface().orElseThrow().dataModel().get("/")).keySet());
  }

  @Test
  void aPathWithAnIncorrectEscapeIsRejected() {
    processor.processMessages(create());

    A2uiValidationError inAComponent =
        assertThrows(
            A2uiValidationError.class,
            () -> processor.processMessages(update(text("root", Map.of("@path", "/a~")))));
    A2uiValidationError inADataUpdate =
        assertThrows(
            A2uiValidationError.class, () -> processor.processMessages(dataUpdate("/a~2", 1)));

    assertEquals(
        "Invalid path syntax in the updateComponents message: '/a~' has a '~' that is not followed"
            + " by '0' or '1'.",
        inAComponent.getMessage());
    assertTrue(inADataUpdate.getMessage().startsWith("Invalid path syntax in "));
  }

  @Test
  void theEnvelopeOfAMessageIsExamined() {
    Map<String, Object> noVersion = Map.of("deleteSurface", Map.of("surfaceId", "s"));
    Map<String, Object> oldVersion =
        map("version", "v0.9", "deleteSurface", Map.of("surfaceId", "s"));
    Map<String, Object> twoActions = new LinkedHashMap<>(create());
    twoActions.put("deleteSurface", Map.of("surfaceId", "s"));
    Map<String, Object> oldAction = map("version", "1.0", "beginRendering", Map.of());
    Map<String, Object> noAction = map("version", "1.0");
    Map<String, Object> unknownField = message("deleteSurface", "surfaceId", "s", "extra", 1);

    assertEquals("Missing required version field in message.", failure(noVersion));
    assertEquals(
        "Unsupported protocol version 'v0.9': this SDK processes version 1.0.",
        failure(oldVersion));
    assertEquals(
        "Message contains multiple conflicting update actions: [createSurface, deleteSurface]",
        failure(twoActions));
    assertTrue(failure(oldAction).startsWith("The action 'beginRendering' is not supported"));
    assertTrue(failure(noAction).startsWith("Message has no action."));
    assertTrue(failure(unknownField).startsWith("Invalid deleteSurface message: "));
    assertEquals(
        "'deleteSurface' must be an object.", failure(map("version", "1.0", "deleteSurface", 1)));
    assertEquals(
        "surfaceId must be a string in 'deleteSurface', but got: 7",
        failure(message("deleteSurface", "surfaceId", 7)));
  }

  @Test
  void aRejectedUpdateDoesNotChangeTheSurface() {
    processor.processMessages(List.of(create(), update(text("root", "kept"))));
    Map<String, Object> column =
        Map.of("id", "root", "component", "Column", "children", List.of("gone"));

    assertThrows(A2uiIntegrityError.class, () -> processor.processMessages(update(column)));
    assertThrows(
        A2uiValidationError.class, () -> processor.processMessages(update(text("root", 5))));

    assertEquals(Set.of("root"), components().all().keySet());
    assertEquals("kept", component("root").properties().get("text"));
  }

  @Test
  void aRejectedCreateSurfaceMakesNoSurface() {
    List<String> created = new ArrayList<>();
    processor.model().onSurfaceCreated().subscribe(surface -> created.add(surface.id()));

    assertThrows(
        A2uiValidationError.class,
        () -> processor.processMessages(create("components", List.of(text("root", 5)))));

    assertEquals(Optional.empty(), surface());
    assertEquals(List.of(), created);
  }

  @Test
  void createSurfaceMergesItsDataBeforeItsComponentsBecomeVisible() {
    List<Object> seen = new ArrayList<>();
    processor
        .model()
        .onSurfaceCreated()
        .subscribe(surface -> {
          seen.add(surface.dataModel().get("/"));
          seen.add(surface.componentsModel().all().size());
        });

    processor.processMessages(
        create(
            "dataModel",
            Map.of("a", 1, "b", Map.of("c", 2)),
            "components",
            List.of(text("root", "x"))));

    assertEquals(List.of(Map.of("a", 1L, "b", Map.of("c", 2L)), 0), seen);
    assertEquals(1, components().all().size());
  }

  @Test
  void createSurfaceKeepsTheFlagsAndTheMetadataOfTheMessage() {
    processor.processMessages(
        create("sendDataModel", true, "metadata", Map.of("extensions", Map.of("vendor", 1))));

    SurfaceModel<ComponentApi> surface = surface().orElseThrow();
    assertTrue(surface.sendDataModel());
    assertEquals(Optional.of(Map.of("vendor", 1L)), surface.metadata().orElseThrow().extensions());
    assertEquals(ProtocolVersion.V1_0, surface.protocolVersion());
    assertSame(BasicCatalog.api(), surface.defaultCatalog().orElseThrow());
    assertThrows(A2uiIntegrityError.class, () -> processor.processMessages(create()));
  }

  @Test
  void aThemeIsRemovedOnlyIfUnknownElementsAreAccepted() {
    Map<String, Object> withTheme = create("theme", Map.of("primaryColor", "#00f"));

    assertThrows(A2uiValidationError.class, () -> processor.processMessages(withTheme));
    processor.processMessages(withTheme, ValidationConfig.STRICT.withAllowUnknownElements(true));

    assertTrue(surface().isPresent());
  }

  @Test
  void theSameTypeIsUpdatedAndADifferentTypeReplacesTheComponent() {
    processor.processMessages(List.of(create(), update(text("root", "one"))));
    ComponentModel first = component("root");
    List<ComponentModel> updates = new ArrayList<>();
    first.onUpdated().subscribe(updates::add);
    List<String> deleted = new ArrayList<>();
    components().onDeleted().subscribe(deleted::add);

    processor.processMessages(update(text("root", "two")));
    processor.processMessages(update(text("root", "two")));

    assertSame(first, component("root"));
    assertEquals(List.of(first), updates);

    processor.processMessages(update(Map.of("id", "root", "component", "Divider")));

    assertNotSame(first, component("root"));
    assertEquals("Divider", component("root").type());
    assertEquals(List.of("root"), deleted);
  }

  @Test
  void theEnvelopeKeysAreNotProperties() {
    Map<String, Object> component = new LinkedHashMap<>(text("root", "x"));
    component.put("@metadata", Map.of("extensions", Map.of("vendor", 1)));
    component.put("@accessibility", Map.of("label", "Greeting"));
    processor.processMessages(List.of(create(), update(component)));

    ComponentModel root = component("root");
    assertEquals(Set.of("text"), root.properties().keySet());
    assertEquals(Optional.of(Map.of("vendor", 1L)), root.metadata().orElseThrow().extensions());
    assertEquals(Optional.of(Map.of("label", "Greeting")), root.accessibility());
    assertSame(BasicCatalog.api(), root.catalog());
  }

  @Test
  void anEnvelopeKeyWithoutItsPrefixIsReadAsTheEnvelopeKey() {
    Map<String, Object> legacy = new LinkedHashMap<>(text("root", "x"));
    legacy.put("metadata", Map.of("extensions", Map.of("vendor", 1)));
    legacy.put("catalogId", BasicCatalog.ID);
    Map<String, Object> both = new LinkedHashMap<>(legacy);
    both.put("@metadata", Map.of());
    processor.processMessages(List.of(create(), update(legacy)));

    A2uiValidationError error =
        assertThrows(A2uiValidationError.class, () -> processor.processMessages(update(both)));

    assertEquals(Set.of("text"), component("root").properties().keySet());
    assertTrue(component("root").metadata().isPresent());
    assertEquals(
        "Component 'root' has both '@metadata' and 'metadata', which mean the same in the"
            + " envelope.",
        error.getMessage());
  }

  @Test
  void theDataModelMessagesWriteAndDelete() {
    processor.processMessages(List.of(create(), update(text("root", "x"))));

    processor.processMessages(dataUpdate(null, Map.of("a", 1)));
    processor.processMessages(dataUpdate("/b", 2));
    processor.processMessages(dataUpdate("/a", null));

    assertEquals(Map.of("b", 2L), surface().orElseThrow().dataModel().get("/"));
    assertThrows(A2uiDataError.class, () -> processor.processMessages(dataUpdate("/b/c", 3)));
  }

  @Test
  void thePathOfADataModelMessageIsAbsolute() {
    processor.processMessages(create());

    A2uiValidationError error =
        assertThrows(
            A2uiValidationError.class, () -> processor.processMessages(dataUpdate("a/b", 1)));
    processor.processMessages(dataUpdate("", Map.of("a", 1)));

    assertEquals(
        "Invalid path syntax in the updateDataModel message: 'a/b' is not a JSON Pointer that"
            + " starts with '/'.",
        error.getMessage());
    assertEquals(Map.of("a", 1L), surface().orElseThrow().dataModel().get("/"));
  }

  @Test
  void theDeletionOfASurfaceNotifiesTheListenersAndRemovesTheSurface() {
    List<String> deleted = new ArrayList<>();
    processor.model().onSurfaceDeleted().subscribe(deleted::add);
    processor.processMessages(List.of(create(), delete()));

    assertEquals(Optional.empty(), surface());
    assertEquals(List.of("s"), deleted);
    assertThrows(
        A2uiIntegrityError.class, () -> processor.processMessages(update(text("root", "x"))));
    assertThrows(A2uiIntegrityError.class, () -> processor.processMessages(dataUpdate("/a", 1)));
  }

  @Test
  void withNoConfigurationTheCatalogAndGraphChecksStopButTheCatalogsResolve() {
    MessageProcessor<ComponentApi> trusting =
        new MessageProcessor<>(List.of(BasicCatalog.api()), null);
    trusting.processMessages(create());

    trusting.processMessages(update(text("lonely", 5), Map.of("id", "x", "component", "Mystery")));

    assertEquals(
        Set.of("lonely", "x"),
        trusting.model().getSurface("s").orElseThrow().componentsModel().all().keySet());
    Map<String, Object> elsewhere = new LinkedHashMap<>(text("y", "v"));
    elsewhere.put("@catalogId", "urn:missing");
    assertThrows(A2uiCatalogError.class, () -> trusting.processMessages(update(elsewhere)));
    Map<String, Object> call = Map.of("@call", "upper", "catalogId", "urn:missing");
    assertThrows(A2uiCatalogError.class, () -> trusting.processMessages(update(text("z", call))));
  }

  @Test
  void theConfigurationOfACallReplacesTheConfigurationOfTheProcessor() {
    processor.processMessages(create());
    Map<String, Object> orphan = update(text("root", "x"), text("orphan", "y"));

    assertThrows(A2uiIntegrityError.class, () -> processor.processMessages(orphan));
    processor.processMessages(orphan, ValidationConfig.STRICT.withAllowOrphanComponents(true));

    assertEquals(2, components().all().size());
  }

  @Test
  void theProblemsOfAllTheComponentsAreInOneError() {
    processor.processMessages(create());

    A2uiValidationError error =
        assertThrows(
            A2uiValidationError.class,
            () ->
                processor.processMessages(
                    update(text("root", 5), Map.of("id", "x", "component", "Mystery"))));

    String message = error.getMessage();
    assertTrue(
        message.startsWith("Invalid components for surface 's': component 'root': "), message);
    assertTrue(message.contains("; component 'x': Unrecognized component type 'Mystery'"), message);
    assertThrows(
        A2uiIntegrityError.class,
        () -> processor.processMessages(update(text("root", "a"), text("root", "b"))));
  }

  @Test
  void aCallIsExaminedAgainstTheCatalogInWhichItRuns() {
    Catalog<ComponentApi, FunctionApi> custom = customCatalog();
    MessageProcessor<ComponentApi> mixed =
        new MessageProcessor<>(List.of(BasicCatalog.api(), custom));
    mixed.processMessages(create());
    Map<String, Object> shout = Map.of("@call", "shout", "catalogId", "urn:custom");
    Map<String, Object> badShout =
        Map.of("@call", "shout", "catalogId", "urn:custom", "args", Map.of("extra", 1));
    Map<String, Object> gauge =
        map("id", "g", "component", "Gauge", "@catalogId", "urn:custom", "label", "x");
    Map<String, Object> gaugeWithBasicCall = new LinkedHashMap<>(gauge);
    gaugeWithBasicCall.put("label", Map.of("@call", "notInBasic"));

    mixed.processMessages(update(text("root", shout)));
    mixed.processMessages(update(gauge), ValidationConfig.STRICT.withAllowOrphanComponents(true));
    A2uiValidationError inCustom =
        assertThrows(
            A2uiValidationError.class, () -> mixed.processMessages(update(text("root", badShout))));
    A2uiValidationError inBasic =
        assertThrows(
            A2uiValidationError.class,
            () ->
                mixed.processMessages(
                    update(gaugeWithBasicCall),
                    ValidationConfig.STRICT.withAllowOrphanComponents(true)));

    assertTrue(inCustom.getMessage().contains("/components/0/text: "), inCustom.getMessage());
    assertTrue(
        inBasic
            .getMessage()
            .contains("/components/0/label: Unrecognized function 'notInBasic' in catalog '"),
        inBasic.getMessage());
    assertSame(
        custom,
        mixed
            .model()
            .getSurface("s")
            .orElseThrow()
            .componentsModel()
            .get("g")
            .orElseThrow()
            .catalog());
  }

  @Test
  void aSurfaceUsesOnlyTheV1Catalogs() {
    ComponentApi box = new ComponentApi("Box", Map.of("type", "object"));
    Catalog<ComponentApi, FunctionApi> old =
        new Catalog<>("urn:old", List.of(box), List.of(), null, ProtocolVersion.V0_9);
    MessageProcessor<ComponentApi> mixed = new MessageProcessor<>(List.of(BasicCatalog.api(), old));

    mixed.processMessages(create());

    assertEquals(
        Set.of(BasicCatalog.ID),
        mixed.model().getSurface("s").orElseThrow().availableCatalogs().keySet());
    A2uiCatalogError error =
        assertThrows(
            A2uiCatalogError.class,
            () ->
                mixed.processMessages(
                    message("createSurface", "surfaceId", "t", "catalogId", "urn:old")));
    assertEquals(
        "Catalog not found: urn:old is not a v1.0 catalog, so surface 't' cannot use it.",
        error.getMessage());
    assertThrows(
        A2uiCatalogError.class,
        () -> new MessageProcessor<>(List.of(BasicCatalog.api(), BasicCatalog.api())));
  }

  @Test
  void aSurfaceWithNoDefaultCatalogNeedsACatalogForEachComponent() {
    processor.processMessages(message("createSurface", "surfaceId", "s"));
    Map<String, Object> named = new LinkedHashMap<>(text("root", "x"));
    named.put("@catalogId", BasicCatalog.ID);

    A2uiCatalogError error =
        assertThrows(
            A2uiCatalogError.class, () -> processor.processMessages(update(text("root", "x"))));
    processor.processMessages(update(named));

    assertEquals(
        "Component 'root' names no catalogId and surface 's' has no default catalogId.",
        error.getMessage());
    assertEquals(Optional.empty(), surface().orElseThrow().defaultCatalog());
    assertSame(BasicCatalog.api(), component("root").catalog());
  }

  @Test
  void theRpcMessagesAreNotHandledYet() {
    Map<String, Object> call =
        Map.of("@call", "openUrl", "catalogId", BasicCatalog.ID, "args", Map.of("url", "u"));

    assertThrows(
        A2uiStateError.class,
        () ->
            processor.processMessages(
                message("callRendererFunction", "functionCallId", "c1", "callFunction", call)));
  }

  @Test
  void disposeDeletesEachSurface() {
    processor.processMessages(create());

    processor.dispose();

    assertTrue(processor.model().all().isEmpty());
  }

  private String failure(Object payload) {
    return assertThrows(A2uiValidationError.class, () -> processor.processMessages(payload))
        .getMessage();
  }

  private Optional<SurfaceModel<ComponentApi>> surface() {
    return processor.model().getSurface("s");
  }

  private SurfaceComponentsModel components() {
    return surface().orElseThrow().componentsModel();
  }

  private ComponentModel component(String id) {
    return components().get(id).orElseThrow();
  }

  /** Makes a v1.0 catalog that has the component {@code Gauge} and the function {@code shout}. */
  private static Catalog<ComponentApi, FunctionApi> customCatalog() {
    ComponentApi gauge =
        new ComponentApi(
            "Gauge",
            Map.of(
                "type",
                "object",
                "properties",
                Map.of("label", Map.of("$ref", "common_types.json#/$defs/DynamicString"))));
    FunctionApi shout =
        new FunctionApi(
            "shout",
            Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                    "@call",
                    Map.of("const", "shout"),
                    "args",
                    Map.of("type", "object", "additionalProperties", false)),
                "returnType",
                "string"));
    return new Catalog<>("urn:custom", List.of(gauge), List.of(shout), null, ProtocolVersion.V1_0);
  }

  /** Puts a value in the given number of lists. */
  private static Object nested(Object value, int lists) {
    Object result = value;
    for (int level = 0; level < lists; level++) {
      result = List.of(result);
    }
    return result;
  }

  /** Makes a map from keys and values. A value can be null. */
  private static Map<String, Object> map(Object... keysAndValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }

  private static Map<String, Object> message(String action, Object... body) {
    return map("version", "1.0", action, map(body));
  }

  private static Map<String, Object> create(Object... extra) {
    Map<String, Object> body = map("surfaceId", "s", "catalogId", BasicCatalog.ID);
    body.putAll(map(extra));
    return map("version", "1.0", "createSurface", body);
  }

  private static Map<String, Object> delete() {
    return message("deleteSurface", "surfaceId", "s");
  }

  private static Map<String, Object> text(String id, Object value) {
    return map("id", id, "component", "Text", "text", value);
  }

  /** Makes an {@code updateComponents} message. Each argument is one component. */
  private static Map<String, Object> update(Object... components) {
    return message("updateComponents", "surfaceId", "s", "components", List.of(components));
  }

  /** Makes an {@code updateDataModel} message. A null path gives a message that has no path. */
  private static Map<String, Object> dataUpdate(String path, Object value) {
    Map<String, Object> body = map("surfaceId", "s");
    if (path != null) {
      body.put("path", path);
    }
    body.put("value", value);
    return map("version", "1.0", "updateDataModel", body);
  }
}
