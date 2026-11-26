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

package com.google.a2ui.core.conformance;

import static com.google.a2ui.core.conformance.JsonValues.asObject;
import static com.google.a2ui.core.conformance.JsonValues.asString;
import static com.google.a2ui.core.conformance.JsonValues.assertJsonEquals;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.resolution.ResolverHooks;
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
import java.util.OptionalInt;
import java.util.Set;

/**
 * The action {@code dispatch_action}: the handler makes a surface with the data of the case. Then
 * the action runner of the SDK does the {@code actionPayload}, as the action of a node does it. The
 * handler examines the action and the error that the surface reports, and the data.
 *
 * <p>The surface uses the basic catalog with two more functions that the cases call: {@code
 * incrementCounter} changes the data, and {@code failingAction} throws an exception.
 */
final class DispatchActionHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys(
          "action",
          "actionPayload",
          "dataModel",
          "expectDataModel",
          "expectDispatched",
          "expectError",
          "metadata",
          "protocolVersion",
          "scope",
          "sourceComponentId",
          "surfaceId",
          "userActivation");

  private static final Set<String> DISPATCHED_KEYS =
      Set.of(
          "context",
          "metadata",
          "name",
          "sourceComponentId",
          "surfaceId",
          "timestampIso8601",
          "userMessage");

  private static final Set<String> ERROR_KEYS = Set.of("category", "code", "message", "surfaceId");

  private static final Set<String> METADATA_KEYS = Set.of("extensions");

  private static final Catalog<ComponentApi, FunctionImplementation> CATALOG = catalog();

  private static Catalog<ComponentApi, FunctionImplementation> catalog() {
    Catalog<ComponentApi, FunctionImplementation> basic =
        BasicCatalog.withFunctions(Locale.US, uri -> {});
    List<FunctionImplementation> functions = new ArrayList<>(basic.functions().values());
    functions.add(
        new FunctionImplementation(
            "incrementCounter",
            Map.of("returnType", "void"),
            (args, context) -> {
              String path = (String) args.get("path");
              double step = args.get("step") == null ? 1 : Coercion.toNumber(args.get("step"));
              Object current = context.get(path);
              context.set(path, (current == null ? 0 : Coercion.toNumber(current)) + step);
              return null;
            }));
    functions.add(
        new FunctionImplementation(
            "failingAction",
            Map.of("returnType", "void"),
            (args, context) -> {
              throw new IllegalStateException("Action execution failed.");
            }));
    return basic.withFunctions(functions);
  }

  @Override
  public void run(ConformanceCase testCase) {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    if (testCase.protocolVersion() == null) {
      // The registry skips each such case by its name. A new case must get a decision.
      throw new AssertionError(
          location + " names no protocolVersion. Such a case is written for v0.9.");
    }
    String surfaceId =
        fields.get("surfaceId") == null
            ? "test_surface"
            : asString(fields.get("surfaceId"), "surfaceId in " + location);
    MessageProcessor<ComponentApi> processor =
        new MessageProcessor<>(List.of(CATALOG), ValidationConfig.RELAXED);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("surfaceId", surfaceId);
    body.put("catalogId", CATALOG.id());
    if (fields.get("dataModel") != null) {
      body.put("dataModel", fields.get("dataModel"));
    }
    processor.processMessages(
        Map.of("version", ProtocolVersion.V1_0.wireValue(), "createSurface", body));
    SurfaceModel<ComponentApi> surface = processor.model().getSurface(surfaceId).orElseThrow();

    List<RendererAction> dispatched = new ArrayList<>();
    List<A2uiError> errors = new ArrayList<>();
    surface.onAction().subscribe(dispatched::add);
    surface.onError().subscribe(errors::add);

    String scope =
        fields.get("scope") == null ? "/" : asString(fields.get("scope"), "scope in " + location);
    Map<String, Object> payload =
        asObject(fields.get("actionPayload"), "actionPayload in " + location);
    String source =
        fields.get("sourceComponentId") == null
            ? "test_component"
            : asString(fields.get("sourceComponentId"), "sourceComponentId in " + location);
    // An action is the result of a user gesture unless the case tells that it is not.
    boolean userActivated = !Boolean.FALSE.equals(fields.get("userActivation"));
    A2uiMetadata metadata = metadata(fields.get("metadata"), location);
    Instant started = Instant.now();
    Runnable action =
        () ->
            ResolverHooks.runAction(
                surface, scope, indexOf(scope), userActivated, payload, source, metadata);

    Map<String, Object> expectError =
        fields.get("expectError") == null
            ? null
            : asObject(fields.get("expectError"), "expectError in " + location);
    if (expectError != null) {
      KnownKeys.assertKnownKeys(expectError, ERROR_KEYS, "expectError in " + location);
    }
    if (expectError != null && expectError.get("category") != null) {
      // An error with a category is an error of the caller. The SDK throws it.
      assertThrown(expectError, action, location);
      if (!dispatched.isEmpty() || !errors.isEmpty()) {
        throw new AssertionError(
            location + ": the surface reported " + dispatched + " and " + errors);
      }
      return;
    }
    action.run();

    if (expectError != null) {
      assertReported(expectError, errors, location);
    } else if (!errors.isEmpty()) {
      throw new AssertionError(location + ": the surface reported the error " + errors.getFirst());
    }
    if (fields.containsKey("expectDispatched")) {
      assertDispatched(fields.get("expectDispatched"), dispatched, started, location);
    }
    if (fields.get("expectDataModel") != null) {
      assertJsonEquals(
          fields.get("expectDataModel"), surface.dataModel().get("/"), "data model in " + location);
    }
  }

  /** Makes the metadata that a case gives to the dispatch, or null if the case gives none. */
  private static A2uiMetadata metadata(Object raw, String location) {
    if (raw == null) {
      return null;
    }
    Map<String, Object> container = asObject(raw, "metadata in " + location);
    KnownKeys.assertKnownKeys(container, METADATA_KEYS, "metadata in " + location);
    return new A2uiMetadata(
        Optional.of(asObject(container.get("extensions"), "metadata.extensions in " + location)));
  }

  /**
   * Gets the position of the list item of a scope. The scope of a list item is the path of the
   * item, and the last segment of that path is the position.
   */
  private static OptionalInt indexOf(String scope) {
    String last = scope.substring(scope.lastIndexOf('/') + 1);
    return last.matches("\\d+") ? OptionalInt.of(Integer.parseInt(last)) : OptionalInt.empty();
  }

  private static void assertThrown(Map<String, Object> expected, Runnable action, String location) {
    if (!"ValidationError".equals(expected.get("category")) || expected.size() != 1) {
      throw new AssertionError(
          location + ": the handler does not know the expectation " + expected);
    }
    try {
      action.run();
    } catch (A2uiValidationError e) {
      return;
    }
    throw new AssertionError(location + ": expected a validation error");
  }

  private static void assertReported(
      Map<String, Object> expected, List<A2uiError> errors, String location) {
    if (errors.size() != 1) {
      throw new AssertionError(location + ": expected one error, but the errors are " + errors);
    }
    A2uiError error = errors.getFirst();
    if (expected.get("code") != null) {
      assertJsonEquals(
          expected.get("code"), error.errorCode().orElse(null), "error code in " + location);
    }
    if (expected.get("surfaceId") != null) {
      assertJsonEquals(
          expected.get("surfaceId"),
          error.surfaceId().orElse(null),
          "error surfaceId in " + location);
    }
    if (expected.get("message") != null
        && !String.valueOf(error.getMessage())
            .contains(asString(expected.get("message"), "expectError.message in " + location))) {
      throw new AssertionError(location + ": the message was " + error.getMessage());
    }
  }

  private static void assertDispatched(
      Object expected, List<RendererAction> dispatched, Instant started, String location) {
    if (expected == null) {
      if (!dispatched.isEmpty()) {
        throw new AssertionError(location + ": the surface reported the action " + dispatched);
      }
      return;
    }
    Map<String, Object> members = asObject(expected, "expectDispatched in " + location);
    KnownKeys.assertKnownKeys(members, DISPATCHED_KEYS, "expectDispatched in " + location);
    if (dispatched.size() != 1) {
      throw new AssertionError(
          location + ": expected one action, but the actions are " + dispatched);
    }
    RendererAction action = dispatched.getFirst();
    members.forEach((member, value) -> {
      Object actual =
          switch (member) {
            case "name" -> action.name();
            case "surfaceId" -> action.surfaceId();
            case "sourceComponentId" -> action.sourceComponentId();
            case "context" -> action.context();
            case "userMessage" -> action.userMessage().orElse(null);
            case "metadata" -> action.toJson().get("metadata");
            default -> {
              // timestampIso8601: the JSON has the time of this run in the ISO 8601 format.
              Instant written = Instant.parse((String) action.toJson().get("timestamp"));
              yield !written.isBefore(started.minusSeconds(1))
                  && !written.isAfter(Instant.now().plusSeconds(1));
            }
          };
      assertJsonEquals(value, actual, member + " of the action in " + location);
    });
  }
}
