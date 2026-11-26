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

import static com.google.a2ui.core.conformance.JsonValues.asList;
import static com.google.a2ui.core.conformance.JsonValues.asObject;
import static com.google.a2ui.core.conformance.JsonValues.asString;
import static com.google.a2ui.core.conformance.JsonValues.assertJsonEquals;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.core.resolution.NodeResolver;
import com.google.a2ui.core.resolution.ResolverHooks;
import com.google.a2ui.core.resolution.ValidationResult;
import com.google.a2ui.core.rpc.ManualScheduler;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The {@code handle_rpc} cases that start from a component: a binding, a check or an action calls a
 * function that only the agent can run ({@code uiBindingScenario}, {@code
 * uiActionFallbackScenario}).
 *
 * <p>The components are components of the basic catalog, with the catalog ID of the scenario. The
 * handler sends them to a processor that has no validation configuration. Such a processor accepts
 * a call of a function that no catalog declares.
 */
final class UiRoutingScenarios {
  private static final String BINDING = "uiBindingScenario";
  private static final String ACTION = "uiActionFallbackScenario";

  private static final Set<String> SCENARIO_KEYS =
      Set.of(
          "actionPayload",
          "catalogId",
          "component",
          "components",
          "inboundResponseError",
          "inboundResponseValue",
          "localFunctions",
          "observe",
          "surfaceId");

  private static final Set<String> EXPECT_KEYS =
      Set.of(
          "dataModel",
          "initialValue",
          "outboundCall",
          "resolvedValue",
          "result",
          "siblings",
          "surfaceError",
          "validationResult");

  private static final Set<String> OBSERVE_KEYS = Set.of("componentId", "property");

  private static final Set<String> LOCAL_FUNCTION_KEYS = Set.of("effect", "returnType");

  private static final Set<String> SURFACE_ERROR_KEYS = Set.of("code", "functionCallId");

  private static final Set<String> VALIDATION_KEYS = Set.of("message", "severity", "valid");

  private UiRoutingScenarios() {}

  /**
   * Tells if the arguments of a case describe one of these scenarios.
   *
   * @param args the {@code args} of a {@code handle_rpc} case
   */
  static boolean applies(Map<String, Object> args) {
    return args.containsKey(BINDING) || args.containsKey(ACTION);
  }

  /** Makes a function that does one write to the data, as an entry of {@code localFunctions}. */
  private static FunctionImplementation local(
      String name, Map<String, Object> declaration, String where) {
    KnownKeys.assertKnownKeys(declaration, LOCAL_FUNCTION_KEYS, where);
    Map<String, Object> effect = asObject(declaration.get("effect"), "effect");
    KnownKeys.assertKnownKeys(effect, Set.of("setData"), where + " effect");
    Map<String, Object> setData = asObject(effect.get("setData"), "setData");
    KnownKeys.assertKnownKeys(setData, Set.of("path", "value"), where + " setData");
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put(
        "returnType",
        declaration.get("returnType") == null ? "any" : declaration.get("returnType"));
    schema.put(
        "properties", Map.of("@call", Map.of("const", name), "args", Map.of("type", "object")));
    schema.put("required", List.of("@call"));
    return new FunctionImplementation(
        name,
        schema,
        (args, context) -> {
          context.set(asString(setData.get("path"), "path"), setData.get("value"));
          return true;
        });
  }

  /** Finds the node of a component in the tree below a node. */
  private static ComponentNode<ComponentApi> find(Object value, String componentId) {
    return switch (value) {
      case ComponentNode<?> node -> {
        @SuppressWarnings("unchecked")
        ComponentNode<ComponentApi> typed = (ComponentNode<ComponentApi>) node;
        yield typed.componentId().equals(componentId)
            ? typed
            : find(typed.props().get().asMap(), componentId);
      }
      case Map<?, ?> map -> {
        ComponentNode<ComponentApi> found = null;
        for (Object item : map.values()) {
          found = found != null ? found : find(item, componentId);
        }
        yield found;
      }
      case List<?> list -> {
        ComponentNode<ComponentApi> found = null;
        for (Object item : list) {
          found = found != null ? found : find(item, componentId);
        }
        yield found;
      }
      case null, default -> null;
    };
  }

  /**
   * Runs one scenario.
   *
   * @param testCase the case
   * @param args the {@code args} of the case
   */
  static void run(ConformanceCase testCase, Map<String, Object> args) {
    String location = testCase.location();
    KnownKeys.assertKnownKeys(args, Set.of(BINDING, ACTION), "args in " + location);
    boolean binding = args.containsKey(BINDING);
    Map<String, Object> scenario = asObject(args.get(binding ? BINDING : ACTION), "scenario");
    KnownKeys.assertKnownKeys(scenario, SCENARIO_KEYS, "scenario in " + location);
    Map<String, Object> expect = asObject(testCase.fields().get("expect"), "expect");
    KnownKeys.assertKnownKeys(expect, EXPECT_KEYS, "expect in " + location);
    String surfaceId = asString(scenario.get("surfaceId"), "surfaceId");
    String catalogId = asString(scenario.get("catalogId"), "catalogId");

    List<FunctionImplementation> locals = new ArrayList<>();
    if (scenario.get("localFunctions") != null) {
      asObject(scenario.get("localFunctions"), "localFunctions")
          .forEach((name, declaration) ->
              locals.add(
                  local(
                      name,
                      asObject(declaration, "localFunctions." + name),
                      "localFunctions." + name + " in " + location)));
    }
    Catalog<ComponentApi, FunctionImplementation> catalog =
        BasicCatalog.withFunctions(Locale.US, uri -> {})
            .derive(catalogId, null, List.of(), null, locals, null);
    List<Map<String, Object>> sent = new ArrayList<>();
    List<A2uiError> errors = new ArrayList<>();
    MessageProcessor<ComponentApi> processor =
        new MessageProcessor<>(
            List.of(catalog),
            null,
            message -> {
              if (message.containsKey("callAgentFunction")) {
                sent.add(message);
              }
            });
    processor.rpc().setScheduler(new ManualScheduler());

    List<Object> components = new ArrayList<>();
    if (scenario.get("components") != null) {
      components.addAll(asList(scenario.get("components"), "components"));
    } else if (scenario.get("component") != null) {
      components.add(scenario.get("component"));
    }
    Map<String, Object> create = new LinkedHashMap<>();
    create.put("surfaceId", surfaceId);
    create.put("catalogId", catalogId);
    if (!components.isEmpty()) {
      create.put("components", components);
    }
    processor.processMessages(Map.of("version", "1.0", "createSurface", create));
    SurfaceModel<ComponentApi> surface = processor.model().getSurface(surfaceId).orElseThrow();
    surface.onError().subscribe(errors::add);

    // The scenario observes a property of one component, or the result of an action.
    NodeResolver<ComponentApi> resolver = new NodeResolver<>(surface);
    Map<String, Object> observe =
        scenario.get("observe") == null ? Map.of() : asObject(scenario.get("observe"), "observe");
    KnownKeys.assertKnownKeys(observe, OBSERVE_KEYS, "observe in " + location);
    String observedId =
        observe.get("componentId") == null
            ? SurfaceModel.DEFAULT_ROOT_ID
            : asString(observe.get("componentId"), "componentId");
    String observedProperty =
        observe.get("property") == null ? "text" : asString(observe.get("property"), "property");
    Supplier<Object> result = () -> null;
    if (binding) {
      if (expect.containsKey("initialValue")) {
        assertJsonEquals(
            expect.get("initialValue"),
            props(resolver, observedId, location).value(observedProperty),
            location + " initialValue");
      }
    } else {
      result =
          ResolverHooks.runAction(
              surface,
              "/",
              OptionalInt.empty(),
              true,
              asObject(scenario.get("actionPayload"), "actionPayload"),
              SurfaceModel.DEFAULT_ROOT_ID,
              null);
    }

    if (sent.size() != 1) {
      throw new AssertionError(location + ": expected one call to the agent, but got " + sent);
    }
    Map<String, Object> call =
        new LinkedHashMap<>(asObject(sent.getFirst().get("callAgentFunction"), "call"));
    Object functionCallId = call.remove("functionCallId");
    if (expect.get("outboundCall") != null) {
      Map<String, Object> shown = new LinkedHashMap<>();
      shown.put("version", sent.getFirst().get("version"));
      shown.put("callAgentFunction", call);
      assertJsonEquals(expect.get("outboundCall"), shown, location + " outboundCall");
    }

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("functionCallId", functionCallId);
    if (scenario.containsKey("inboundResponseError")) {
      response.put("error", scenario.get("inboundResponseError"));
    } else {
      response.put("value", scenario.get("inboundResponseValue"));
    }
    processor.processMessages(Map.of("version", "1.0", "agentFunctionResponse", response));

    if (expect.containsKey("resolvedValue")) {
      assertJsonEquals(
          expect.get("resolvedValue"),
          props(resolver, observedId, location).value(observedProperty),
          location + " resolvedValue");
    }
    if (expect.containsKey("result")) {
      assertJsonEquals(expect.get("result"), result.get(), location + " result");
    }
    if (expect.get("siblings") != null) {
      asObject(expect.get("siblings"), "siblings")
          .forEach((componentId, expected) -> {
            NodeProps<ComponentApi> actual = props(resolver, componentId, location);
            asObject(expected, "siblings." + componentId)
                .forEach((name, value) ->
                    assertJsonEquals(
                        value,
                        actual.value(name),
                        location + " sibling " + componentId + "." + name));
          });
    }
    if (expect.get("validationResult") != null) {
      Map<String, Object> expected = asObject(expect.get("validationResult"), "validationResult");
      KnownKeys.assertKnownKeys(expected, VALIDATION_KEYS, "validationResult in " + location);
      List<ValidationResult> failed = props(resolver, observedId, location).validationResults();
      Map<String, Object> actual =
          failed.isEmpty() ? Map.of("valid", true) : failed.getFirst().toJson();
      expected.forEach((name, value) ->
          assertJsonEquals(value, actual.get(name), location + " validationResult." + name));
    }
    if (expect.get("dataModel") != null) {
      assertJsonEquals(
          expect.get("dataModel"), surface.dataModel().get("/"), location + " dataModel");
    }
    if (expect.get("surfaceError") != null) {
      Map<String, Object> expected = asObject(expect.get("surfaceError"), "surfaceError");
      KnownKeys.assertKnownKeys(expected, SURFACE_ERROR_KEYS, "surfaceError in " + location);
      Object wantedId = expected.get("functionCallId");
      if (wantedId != null && !"$outboundCallId".equals(wantedId)) {
        throw new AssertionError(location + ": unknown functionCallId value " + wantedId);
      }
      boolean found =
          errors.stream()
              .anyMatch(error ->
                  expected.get("code").equals(error.errorCode().orElse(null))
                      && (wantedId == null
                          || functionCallId.equals(error.functionCallId().orElse(null))));
      if (!found) {
        List<String> reported =
            errors.stream()
                .map(error ->
                    error.errorCode().orElse(null)
                        + " "
                        + error.functionCallId().orElse(null)
                        + ": "
                        + error.getMessage())
                .toList();
        throw new AssertionError(
            location + ": expected <" + expected + "> but the surface reported " + reported);
      }
    }
  }

  private static NodeProps<ComponentApi> props(
      NodeResolver<ComponentApi> resolver, String componentId, String location) {
    ComponentNode<ComponentApi> node = find(resolver.rootNode().get().orElse(null), componentId);
    if (node == null) {
      throw new AssertionError(location + ": the tree has no node of '" + componentId + "'");
    }
    return node.props().get();
  }
}
