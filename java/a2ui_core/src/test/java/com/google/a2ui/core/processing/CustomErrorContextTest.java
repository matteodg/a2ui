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
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiErrorDetail;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.Json;
import com.google.a2ui.core.internal.JsonSchemaEngine;
import com.google.a2ui.core.internal.SchemaReferences;
import com.google.a2ui.core.resolution.NodeResolver;
import com.google.a2ui.core.schema.v1_0.SchemaDocuments;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.core.validation.ValidationConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The path and the details that the code of a custom function or of a custom component gives to an
 * error go to the listeners of the surface and to the {@code error} message for the agent.
 */
class CustomErrorContextTest {
  private static final String CATALOG_ID = "https://example.com/catalogs/shop";

  /** The schema of the messages from the renderer to the agent, with local references. */
  private static final JsonSchemaEngine SCHEMA = schema();

  private static final List<A2uiErrorDetail> DETAILS =
      List.of(
          new A2uiErrorDetail("/args/product", "unknown_product", "No product has the ID 'p9'."),
          new A2uiErrorDetail("/args/store", "closed_store", "The store 'north' is closed."));

  /** The details as the {@code error} message has them. */
  private static final List<Map<String, Object>> DETAILS_JSON =
      List.of(
          object(
              "path",
              "/args/product",
              "code",
              "unknown_product",
              "message",
              "No product has the ID 'p9'."),
          object(
              "path",
              "/args/store",
              "code",
              "closed_store",
              "message",
              "The store 'north' is closed."));

  private final List<Map<String, Object>> sent = new ArrayList<>();
  private final List<A2uiError> reported = new ArrayList<>();

  /** The error that the custom function throws. Each test makes it. */
  private A2uiError thrown;

  /**
   * The basic catalog with a custom function, {@code stockOf}, and a custom component, {@code
   * StockChart}.
   */
  private final Catalog<ComponentApi, FunctionImplementation> catalog = catalog();

  private final MessageProcessor<ComponentApi> processor =
      new MessageProcessor<>(List.of(catalog), ValidationConfig.STRICT, sent::add, true);

  private Catalog<ComponentApi, FunctionImplementation> catalog() {
    Catalog<ComponentApi, FunctionImplementation> basic = BasicCatalog.withFunctions();
    List<ComponentApi> components = new ArrayList<>(basic.components().values());
    components.add(
        new ComponentApi(
            "StockChart",
            object(
                "type",
                "object",
                "properties",
                object("component", object("const", "StockChart"), "series", object()))));
    List<FunctionImplementation> functions = new ArrayList<>(basic.functions().values());
    functions.add(
        new FunctionImplementation(
            "stockOf",
            Map.of("returnType", "string"),
            (args, context) -> {
              throw thrown;
            }));
    return new Catalog<>(CATALOG_ID, components, functions, null, ProtocolVersion.V1_0);
  }

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

  private static Map<String, Object> stockOf() {
    return object("@call", "stockOf", "args", object("product", "p9", "store", "north"));
  }

  /** Makes the surface {@code s} and listens for its failures. */
  private SurfaceModel<ComponentApi> surface(Object... components) {
    Map<String, Object> body =
        object("surfaceId", "s", "catalogId", CATALOG_ID, "components", List.of(components));
    processor.processMessages(object("version", "1.0", "createSurface", body));
    SurfaceModel<ComponentApi> surface = processor.model().getSurface("s").orElseThrow();
    surface.onError().subscribe(reported::add);
    return surface;
  }

  /** Gets the body of the one {@code error} message, and examines the message with the schema. */
  private Map<String, Object> sentError() {
    assertEquals(1, sent.size(), sent.toString());
    Map<String, Object> message = sent.getFirst();
    assertEquals(List.of(), SCHEMA.errors("", Json.freeze(message)));
    assertEquals(List.of("version", "error"), List.copyOf(message.keySet()));
    return Json.asObject(message.get("error"));
  }

  @Test
  void theDetailsOfAFunctionInABindingGoToTheSurfaceAndToTheAgent() {
    thrown = new A2uiExpressionError("The stock is not available.", "/args/product", DETAILS);
    SurfaceModel<ComponentApi> surface =
        surface(object("id", "root", "component", "Text", "text", stockOf()));

    NodeResolver<ComponentApi> resolver = new NodeResolver<>(surface);
    resolver.rootNode().get().orElseThrow().props().get();

    assertEquals(
        object(
            "code",
            "EXPRESSION_ERROR",
            "surfaceId",
            "s",
            "message",
            "The stock is not available.",
            "path",
            "/args/product",
            "details",
            DETAILS_JSON),
        sentError());
    assertEquals(1, reported.size());
    assertSame(thrown, reported.getFirst());
    assertEquals(Optional.of("/args/product"), thrown.path());
    assertEquals(DETAILS, thrown.details());
    // The core wrote the surface and the code.
    assertEquals(Optional.of("s"), thrown.surfaceId());
    assertEquals(Optional.of("EXPRESSION_ERROR"), thrown.errorCode());
    assertEquals(Optional.empty(), thrown.functionCallId());
    resolver.dispose();
  }

  @Test
  void theDetailsOfAFunctionInAnActionGoToTheSurfaceAndToTheAgent() {
    IllegalStateException cause = new IllegalStateException("The database is closed.");
    thrown = new A2uiExpressionError("The stock is not available.", null, DETAILS, cause);
    SurfaceModel<ComponentApi> surface =
        surface(
            object(
                "id",
                "root",
                "component",
                "Button",
                "child",
                "label",
                "action",
                object("functionCall", stockOf())),
            object("id", "label", "component", "Text", "text", "Go"));

    NodeResolver<ComponentApi> resolver = new NodeResolver<>(surface);
    resolver.rootNode().get().orElseThrow().props().get().action("action").orElseThrow().invoke();

    assertEquals(
        object(
            "code",
            "EXECUTION_ERROR",
            "surfaceId",
            "s",
            "message",
            "The stock is not available.",
            "details",
            DETAILS_JSON),
        sentError());
    assertEquals(List.of(thrown), reported);
    assertEquals(Optional.empty(), thrown.path());
    assertSame(cause, thrown.getCause());
    resolver.dispose();
  }

  @Test
  void theDetailsOfAValidationErrorOfAFunctionGoToTheAgent() {
    thrown = new A2uiValidationError("The arguments are not correct.", null, DETAILS);
    SurfaceModel<ComponentApi> surface =
        surface(object("id", "root", "component", "Text", "text", stockOf()));

    NodeResolver<ComponentApi> resolver = new NodeResolver<>(surface);
    resolver.rootNode().get().orElseThrow().props().get();

    Map<String, Object> body = sentError();
    assertEquals(DETAILS_JSON, body.get("details"));
    assertEquals(List.of("code", "surfaceId", "message", "details"), List.copyOf(body.keySet()));
    assertEquals(List.of(thrown), reported);
    resolver.dispose();
  }

  @Test
  void theDetailsOfACustomComponentGoToTheSurfaceAndToTheAgent() {
    SurfaceModel<ComponentApi> surface =
        surface(object("id", "root", "component", "StockChart", "series", List.of(1L, "x")));
    List<A2uiErrorDetail> details =
        List.of(new A2uiErrorDetail("/series/1", "type_mismatch", "A point must be a number."));

    // The view of the custom component reports a problem of its own properties.
    A2uiExpressionError error =
        new A2uiExpressionError("StockChart 'root' cannot show its series.", "/series", details);
    surface.dispatchError(error);

    assertEquals(
        object(
            "code",
            "EXPRESSION_ERROR",
            "surfaceId",
            "s",
            "message",
            "StockChart 'root' cannot show its series.",
            "path",
            "/series",
            "details",
            List.of(
                object(
                    "path",
                    "/series/1",
                    "code",
                    "type_mismatch",
                    "message",
                    "A point must be a number."))),
        sentError());
    assertEquals(List.of(error), reported);
    assertEquals(details, reported.getFirst().details());
    assertEquals(Optional.of("s"), error.surfaceId());
  }

  @Test
  void aValidationErrorWithAPathHasTheLocatedShapeThatPermitsNoDetails() {
    SurfaceModel<ComponentApi> surface =
        surface(object("id", "root", "component", "StockChart", "series", "none"));

    A2uiValidationError error =
        new A2uiValidationError("The series is not a list.", "/components/0/series", DETAILS);
    surface.dispatchError(error);

    // The schema of VALIDATION_FAILED permits only these four properties.
    assertEquals(
        object(
            "code",
            "VALIDATION_FAILED",
            "surfaceId",
            "s",
            "path",
            "/components/0/series",
            "message",
            "The series is not a list."),
        sentError());
    // The listeners of the surface get the details.
    assertEquals(DETAILS, reported.getFirst().details());
  }

  @Test
  void aValidationErrorWithDetailsAndNoPathHasTheGenericShape() {
    SurfaceModel<ComponentApi> surface =
        surface(object("id", "root", "component", "StockChart", "series", "none"));

    surface.dispatchError(new A2uiValidationError("The series is not correct.", null, DETAILS));

    Map<String, Object> body = sentError();
    assertEquals("SCHEMA_VALIDATION_FAILED", body.get("code"));
    assertEquals(DETAILS_JSON, body.get("details"));
    assertEquals(List.of("code", "surfaceId", "message", "details"), List.copyOf(body.keySet()));
  }
}
