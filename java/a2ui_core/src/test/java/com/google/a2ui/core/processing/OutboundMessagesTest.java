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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.Json;
import com.google.a2ui.core.internal.JsonSchemaEngine;
import com.google.a2ui.core.internal.SchemaReferences;
import com.google.a2ui.core.resolution.ActionDispatchOptions;
import com.google.a2ui.core.resolution.NodeAction;
import com.google.a2ui.core.resolution.NodeResolver;
import com.google.a2ui.core.schema.v1_0.SchemaDocuments;
import com.google.a2ui.core.state.RendererAction;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.core.validation.ValidationConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A processor with a unified outbound listener sends the actions and the failures of its surfaces
 * to the agent.
 */
class OutboundMessagesTest {
  /** The schema of the messages from the renderer to the agent, with local references. */
  private static final JsonSchemaEngine SCHEMA = schema();

  private final List<Map<String, Object>> sent = new ArrayList<>();

  /** The basic catalog. Its function {@code openUrl} fails for each URL. */
  private final Catalog<ComponentApi, FunctionImplementation> catalog =
      BasicCatalog.withFunctions(
          Locale.US,
          uri -> {
            throw new IllegalStateException("This computer has no browser.");
          });

  private final MessageProcessor<ComponentApi> processor =
      new MessageProcessor<>(List.of(catalog), ValidationConfig.STRICT, sent::add, true);

  private static JsonSchemaEngine schema() {
    Map<String, Object> document =
        new LinkedHashMap<>(
            Json.asObject(
                SchemaReferences.localize(SchemaDocuments.rendererToAgent(), name -> {})));
    document.put("$defs", SchemaReferences.commonTypes());
    return new JsonSchemaEngine(Json.freezeObject(document));
  }

  private static Map<String, Object> object(Object... namesAndValues) {
    Map<String, Object> object = new LinkedHashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      object.put((String) namesAndValues[i], namesAndValues[i + 1]);
    }
    return object;
  }

  private static Map<String, Object> createSurface(
      MessageProcessor<ComponentApi> target, Object... components) {
    Map<String, Object> body =
        object(
            "surfaceId",
            "s",
            "catalogId",
            BasicCatalog.ID,
            "dataModel",
            object("name", "Ada"),
            "components",
            List.of(components));
    Map<String, Object> message = object("version", "1.0", "createSurface", body);
    target.processMessages(message);
    return message;
  }

  /** Makes the surface with one button, and gets the action of the button. */
  private NodeAction button(Map<String, Object> action) {
    return button(processor, action);
  }

  private static NodeAction button(
      MessageProcessor<ComponentApi> target, Map<String, Object> action) {
    createSurface(
        target,
        object("id", "root", "component", "Button", "child", "label", "action", action),
        object("id", "label", "component", "Text", "text", "Go"));
    return rootAction(target);
  }

  private static NodeAction rootAction(MessageProcessor<ComponentApi> target) {
    SurfaceModel<ComponentApi> surface = target.model().getSurface("s").orElseThrow();
    return new NodeResolver<>(surface)
        .rootNode()
        .get()
        .orElseThrow()
        .props()
        .get()
        .action("action")
        .orElseThrow();
  }

  /** Gets the body of the one message that the processor sent, and examines its schema. */
  private Map<String, Object> sentBody(String key) {
    assertEquals(1, sent.size(), sent.toString());
    Map<String, Object> message = sent.getFirst();
    assertEquals(List.of(), SCHEMA.errors("", Json.freeze(message)));
    assertEquals(List.of("version", key), List.copyOf(message.keySet()));
    assertEquals("1.0", message.get("version"));
    return Json.asObject(message.get(key));
  }

  @Test
  void anEventActionIsSentAsAnActionMessage() {
    Map<String, Object> context = object("who", Map.of("@path", "/name"));
    NodeAction action = button(Map.of("event", object("name", "save", "context", context)));
    A2uiMetadata metadata = new A2uiMetadata(Optional.of(Map.of("com_example_trace", "t1")));
    List<RendererAction> reported = new ArrayList<>();
    processor.model().onAction().subscribe(reported::add);

    action.invoke(ActionDispatchOptions.withMetadata(metadata));

    Map<String, Object> body = sentBody("action");
    assertEquals("save", body.get("name"));
    assertEquals("s", body.get("surfaceId"));
    assertEquals("root", body.get("sourceComponentId"));
    assertEquals(Map.of("who", "Ada"), body.get("context"));
    assertEquals(Map.of("extensions", Map.of("com_example_trace", "t1")), body.get("metadata"));
    assertFalse(body.containsKey("userMessage"));
    assertFalse(Instant.parse((String) body.get("timestamp")).isAfter(Instant.now()));
    assertThrows(UnsupportedOperationException.class, () -> sent.getFirst().clear());
    // The listeners of the model continue to receive the action.
    assertEquals(1, reported.size());
  }

  @Test
  void aFunctionCallActionSendsNoMessage() {
    Map<String, Object> call =
        object("@call", "formatString", "args", object("value", "Hello ${/name}"));

    button(Map.of("functionCall", call)).invoke();

    assertEquals(List.of(), sent);
  }

  @Test
  void aFunctionThatFailsInAnActionIsSentAsAnExecutionError() {
    Map<String, Object> call =
        object("@call", "openUrl", "args", object("url", "https://example.com"));

    button(Map.of("functionCall", call)).invoke();

    Map<String, Object> body = sentBody("error");
    assertEquals("EXECUTION_ERROR", body.get("code"));
    assertEquals("s", body.get("surfaceId"));
    assertEquals("Function 'openUrl' failed: This computer has no browser.", body.get("message"));
    assertEquals(List.of("code", "surfaceId", "message"), List.copyOf(body.keySet()));
  }

  @Test
  void aCallThatTheContextRefusesInAnActionIsSentAsAnExpressionError() {
    // The function @index has no value outside a list template. Thus no function runs.
    button(Map.of("functionCall", object("@call", "@index", "args", object()))).invoke();

    assertEquals("EXPRESSION_ERROR", sentBody("error").get("code"));
  }

  @Test
  void aUserMessageThatIsNotTextIsSentAsAnInvalidAction() {
    Map<String, Object> event = object("name", "save", "userMessage", Map.of("@path", "/name"));

    button(Map.of("event", event)).invoke();

    assertEquals("Ada", sentBody("action").get("userMessage"));

    sent.clear();
    processor.model().getSurface("s").orElseThrow().dataModel().set("/name", 7L);
    rootAction(processor).invoke();

    assertEquals("INVALID_ACTION", sentBody("error").get("code"));
  }

  @Test
  void aBindingThatCannotBeEvaluatedIsSentAsAnExpressionErrorOneTime() {
    Map<String, Object> broken =
        object("@call", "regex", "args", object("value", Map.of("@path", "/name"), "pattern", "("));
    createSurface(processor, object("id", "root", "component", "Text", "text", broken));
    SurfaceModel<ComponentApi> surface = processor.model().getSurface("s").orElseThrow();

    assertEquals(List.of(), sent);

    NodeResolver<ComponentApi> resolver = new NodeResolver<>(surface);
    surface.dataModel().set("/name", "Grace");

    Map<String, Object> body = sentBody("error");
    assertEquals("EXPRESSION_ERROR", body.get("code"));
    assertEquals("s", body.get("surfaceId"));
    assertTrue(((String) body.get("message")).contains("UNSUPPORTED_SYNTAX"), body.toString());
    resolver.dispose();
  }

  @Test
  void aFailureThatAViewReportsIsSentWithItsCode() {
    createSurface(processor, object("id", "root", "component", "Text", "text", "Hello"));
    SurfaceModel<ComponentApi> surface = processor.model().getSurface("s").orElseThrow();
    List<A2uiError> reported = new ArrayList<>();
    surface.onError().subscribe(reported::add);

    surface.dispatchError(new A2uiValidationError("The image has no source."));

    Map<String, Object> body = sentBody("error");
    assertEquals("SCHEMA_VALIDATION_FAILED", body.get("code"));
    assertEquals("s", body.get("surfaceId"));
    assertEquals("The image has no source.", body.get("message"));
    assertEquals(1, reported.size());
  }

  @Test
  void aSurfaceThatTheApplicationAddsAlsoSendsItsMessages() {
    SurfaceModel<ComponentApi> surface =
        new SurfaceModel<>(
            "own", ProtocolVersion.V1_0, catalog, Map.of(catalog.id(), catalog), false, null);
    processor.model().addSurface(surface);

    surface.dispatchAction(Map.of("event", Map.of("name", "ping")), "root");

    assertEquals("own", sentBody("action").get("surfaceId"));
  }

  @Test
  void aDeletedSurfaceSendsNothing() {
    NodeAction action = button(Map.of("event", Map.of("name", "save")));

    processor.processMessages(object("version", "1.0", "deleteSurface", object("surfaceId", "s")));
    action.invoke();

    assertEquals(List.of(), sent);
  }

  @Test
  void aProcessorThatIsNotUnifiedSendsOnlyTheErrorOfARejectedMessage() {
    MessageProcessor<ComponentApi> plain =
        new MessageProcessor<>(List.of(catalog), ValidationConfig.STRICT, sent::add);
    List<RendererAction> reported = new ArrayList<>();
    plain.model().onAction().subscribe(reported::add);
    NodeAction action = button(plain, Map.of("event", Map.of("name", "save")));

    action.invoke();
    plain.model().getSurface("s").orElseThrow().dispatchError(new A2uiValidationError("Bad."));

    assertEquals(1, reported.size());
    assertEquals(List.of(), sent);

    assertThrows(A2uiValidationError.class, () -> plain.processMessages(Map.of("version", "1.0")));

    assertTrue(sentBody("error").containsKey("code"));
  }

  @Test
  void theSchemaOfTheseTestsRejectsAnActionThatIsNotComplete() {
    Map<String, Object> message = object("version", "1.0", "action", object("name", "save"));

    assertFalse(SCHEMA.errors("", Json.freeze(message)).isEmpty());
  }

  @Test
  void aUnifiedProcessorNeedsAListener() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new MessageProcessor<>(List.of(catalog), ValidationConfig.STRICT, null, true));
  }
}
