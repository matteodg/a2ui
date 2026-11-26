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
import static com.google.a2ui.core.conformance.JsonValues.jsonEquals;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.core.resolution.NodeResolver;
import com.google.a2ui.core.resolution.ResolverHooks;
import com.google.a2ui.core.rpc.ManualScheduler;
import com.google.a2ui.core.state.ComponentModel;
import com.google.a2ui.core.state.RendererAction;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The action {@code route_agent_function}: the handler evaluates function calls on a surface. Only
 * the agent can run some of these calls. The handler examines the {@code callAgentFunction}
 * messages, the values, the errors and the actions that follow.
 *
 * <p>A call that a case evaluates as a binding is the {@code text} of a {@code Text} component of
 * the basic catalog. A call that a case evaluates as a check is the condition of a rule of a {@code
 * TextField}. Thus the two go through the node tree, as in a renderer. The handler adds the
 * components directly to the model of the surface and does not send them in a message. A message
 * processor rejects some of the calls of the cases, for example a call that names a catalog that
 * the surface does not have.
 *
 * <p>A component model and a data model of this SDK contain only JSON. Thus a binding cannot write
 * a number that is not finite. For such a number in a binding, the handler writes a call of the
 * function {@code nonFiniteNumber} of a catalog of the harness. That function returns the number.
 * Thus the resolved argument is the number that the case gives.
 */
final class RouteAgentFunctionHandler implements CaseHandler {
  private static final String SURFACE = "main";
  private static final String ROOT = "root";

  /** The catalog of the function that the handler uses for a number that is not finite. */
  private static final String HARNESS_CATALOG = "urn:a2ui:conformance:java-harness";

  private static final String NON_FINITE = "nonFiniteNumber";

  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys("action", "catalogs", "dataModel", "protocolVersion", "steps", "surface");

  private static final Set<String> CATALOG_KEYS =
      Set.of("agentFunctions", "catalogId", "localFunctions");

  private static final Set<String> FUNCTION_KEYS =
      Set.of("allowedCallers", "args", "requiresUserActivation", "returnType");

  private static final Set<String> SURFACE_KEYS = Set.of("catalogId", "outboundListener");

  private static final Set<String> STEP_KEYS =
      Set.of(
          "as",
          "cancelSurfaceCalls",
          "deleteSurface",
          "evaluate",
          "expectActions",
          "expectErrors",
          "expectOutbound",
          "expectValues",
          "outboundInAnyOrder",
          "respond",
          "updateData");

  private static final Set<String> RESPOND_KEYS = Set.of("call", "error", "value");

  private static final Set<String> UPDATE_KEYS = Set.of("path", "value");

  private static final Set<String> ERROR_KEYS = Set.of("code", "message");

  private static final Set<String> ACTION_KEYS = Set.of("context", "name", "userMessage");

  /** The basic catalog with the code of its functions. */
  private static final Catalog<ComponentApi, FunctionImplementation> BASIC =
      BasicCatalog.withFunctions(Locale.US, uri -> {});

  private static Catalog<ComponentApi, FunctionApi> harnessCatalog() {
    FunctionApi number =
        new FunctionImplementation(
            NON_FINITE,
            Map.of("returnType", "number"),
            (args, context) -> Double.valueOf((String) args.get("text")));
    return new Catalog<>(HARNESS_CATALOG, List.of(), List.of(number), null, ProtocolVersion.V1_0);
  }

  /** Makes a catalog of a case. */
  private static Catalog<ComponentApi, FunctionApi> catalog(
      Map<String, Object> entry, String where) {
    KnownKeys.assertKnownKeys(entry, CATALOG_KEYS, where);
    List<FunctionApi> functions = new ArrayList<>();
    if (entry.get("localFunctions") != null) {
      for (Object name : asList(entry.get("localFunctions"), "localFunctions")) {
        FunctionImplementation local = BASIC.functions().get(asString(name, "localFunctions"));
        if (local == null) {
          throw new AssertionError(where + ": the basic catalog has no function '" + name + "'");
        }
        functions.add(local);
      }
    }
    if (entry.get("agentFunctions") != null) {
      Map<String, Object> declared = asObject(entry.get("agentFunctions"), "agentFunctions");
      declared.forEach((name, raw) -> {
        Map<String, Object> declaration = asObject(raw, "agentFunctions." + name);
        KnownKeys.assertKnownKeys(declaration, FUNCTION_KEYS, where + " agentFunctions." + name);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("@call", Map.of("const", name));
        if (declaration.get("args") != null) {
          properties.put("args", declaration.get("args"));
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put(
            "returnType",
            declaration.get("returnType") == null ? "any" : declaration.get("returnType"));
        if (declaration.get("allowedCallers") != null) {
          schema.put("allowedCallers", declaration.get("allowedCallers"));
        }
        if (declaration.get("requiresUserActivation") != null) {
          schema.put("requiresUserActivation", declaration.get("requiresUserActivation"));
        }
        schema.put("properties", properties);
        schema.put("required", List.of("@call"));
        // The catalog declares the function and has no code for it. Only the agent can run it.
        functions.add(new FunctionApi(name, schema));
      });
    }
    return new Catalog<>(
        asString(entry.get("catalogId"), "catalogId"),
        List.of(),
        functions,
        null,
        ProtocolVersion.V1_0);
  }

  /** One evaluation of a case that has a value: a binding or a check. */
  private record Probe(String id, boolean check) {}

  /** The state of one case. */
  private static final class Run {
    final String location;
    final List<Map<String, Object>> sent = new ArrayList<>();
    final List<A2uiError> errors = new ArrayList<>();
    final List<RendererAction> actions = new ArrayList<>();
    final List<Probe> probes = new ArrayList<>();
    MessageProcessor<ComponentApi> processor;
    SurfaceModel<ComponentApi> surface;
    NodeResolver<ComponentApi> resolver;
    boolean deleted;

    Run(String location) {
      this.location = location;
    }

    void process(String kind, Map<String, Object> body) {
      processor.processMessages(Map.of("version", "1.0", kind, body));
    }

    private void add(String id, String type, Map<String, Object> properties, List<Object> checks) {
      surface
          .componentsModel()
          .addComponent(new ComponentModel(id, type, BASIC, properties, null, null, checks));
    }

    void start() {
      add(ROOT, "Column", Map.of("children", List.of()), null);
      resolver = new NodeResolver<>(surface);
    }

    /** Adds a component that evaluates a call as its text, or as the condition of a check. */
    void probe(Object call, boolean check) {
      String id = "probe" + probes.size();
      probes.add(new Probe(id, check));
      Object written = withoutNonFiniteNumbers(call);
      if (check) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("condition", written);
        rule.put("message", "The check failed.");
        add(id, "TextField", Map.of("label", "Probe"), List.of(rule));
      } else {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("text", written);
        add(id, "Text", properties, null);
      }
      List<String> children = probes.stream().map(Probe::id).toList();
      surface
          .componentsModel()
          .get(ROOT)
          .orElseThrow()
          .replaceDefinition(Map.of("children", children), null, null, null);
    }

    /** Gets the value of each probe: the value of a binding, or true if a check passes. */
    List<Object> values() {
      ComponentNode<ComponentApi> root =
          resolver
              .rootNode()
              .get()
              .orElseThrow(() -> new AssertionError(location + ": the surface has no root node"));
      List<ComponentNode<ComponentApi>> nodes = root.props().get().children("children");
      List<Object> values = new ArrayList<>();
      for (int position = 0; position < probes.size(); position++) {
        Probe probe = probes.get(position);
        ComponentNode<ComponentApi> node = nodes.get(position);
        if (!node.componentId().equals(probe.id())) {
          throw new AssertionError(location + ": the node of " + probe.id() + " is not there");
        }
        NodeProps<ComponentApi> props = node.props().get();
        values.add(probe.check() ? props.get(NodeProps.IS_VALID) : props.value("text"));
      }
      return values;
    }
  }

  /**
   * Replaces each number that is not finite with a call of the harness function that returns it.
   */
  private static Object withoutNonFiniteNumbers(Object value) {
    return switch (value) {
      case Double number when !Double.isFinite(number) -> {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("@call", NON_FINITE);
        call.put("catalogId", HARNESS_CATALOG);
        call.put("args", Map.of("text", number.toString()));
        yield call;
      }
      case Map<?, ?> map -> {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, item) -> copy.put((String) key, withoutNonFiniteNumbers(item)));
        yield copy;
      }
      case List<?> list -> {
        List<Object> copy = new ArrayList<>();
        list.forEach(item -> copy.add(withoutNonFiniteNumbers(item)));
        yield copy;
      }
      case null, default -> value;
    };
  }

  @Override
  public void run(ConformanceCase testCase) {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    Map<String, Object> setup =
        fields.get("surface") == null ? Map.of() : asObject(fields.get("surface"), "surface");
    KnownKeys.assertKnownKeys(setup, SURFACE_KEYS, "surface in " + location);
    List<Catalog<ComponentApi, FunctionApi>> catalogs = new ArrayList<>();
    for (Object entry : asList(fields.get("catalogs"), "catalogs")) {
      catalogs.add(catalog(asObject(entry, "catalog"), location));
    }
    catalogs.add(harnessCatalog());

    Run run = new Run(location);
    run.processor =
        Boolean.FALSE.equals(setup.get("outboundListener"))
            ? new MessageProcessor<>(catalogs, null)
            : new MessageProcessor<>(
                catalogs,
                null,
                message -> {
                  if (message.containsKey("callAgentFunction")) {
                    run.sent.add(message);
                  }
                });
    // The cases use no timer. Thus no timeout runs.
    run.processor.rpc().setScheduler(new ManualScheduler());
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("surfaceId", SURFACE);
    if (setup.get("catalogId") != null) {
      body.put("catalogId", setup.get("catalogId"));
    }
    if (fields.get("dataModel") != null) {
      body.put("dataModel", fields.get("dataModel"));
    }
    run.process("createSurface", body);
    run.surface = run.processor.model().getSurface(SURFACE).orElseThrow();
    run.surface.onError().subscribe(run.errors::add);
    run.surface.onAction().subscribe(run.actions::add);
    run.start();

    List<Object> steps = asList(fields.get("steps"), "steps");
    for (int position = 0; position < steps.size(); position++) {
      Map<String, Object> step = asObject(steps.get(position), "step");
      String where = location + " step " + position;
      KnownKeys.assertKnownKeys(step, STEP_KEYS, where);
      int sentBefore = run.sent.size();
      run.errors.clear();
      run.actions.clear();
      perform(run, step, where);
      verify(run, step, run.sent.subList(sentBefore, run.sent.size()), where);
    }
  }

  private static void perform(Run run, Map<String, Object> step, String where) {
    if (step.containsKey("evaluate")) {
      Object written = step.get("evaluate");
      String kind = step.get("as") == null ? "binding" : asString(step.get("as"), "as");
      switch (kind) {
        case "binding" -> run.probe(written, false);
        case "check" -> run.probe(written, true);
        case "action" -> action(run, "functionCall", written);
        case "event" -> action(run, "event", written);
        default -> throw new AssertionError(where + ": unknown 'as' value '" + kind + "'");
      }
    } else if (step.containsKey("respond")) {
      Map<String, Object> respond = asObject(step.get("respond"), "respond");
      KnownKeys.assertKnownKeys(respond, RESPOND_KEYS, where + " respond");
      Map<String, Object> call = run.sent.get(((Number) respond.get("call")).intValue());
      Map<String, Object> response = new LinkedHashMap<>();
      response.put(
          "functionCallId",
          asObject(call.get("callAgentFunction"), "callAgentFunction").get("functionCallId"));
      if (respond.containsKey("error")) {
        response.put("error", respond.get("error"));
      } else {
        response.put("value", respond.get("value"));
      }
      run.process("agentFunctionResponse", response);
    } else if (step.containsKey("updateData")) {
      Map<String, Object> update = asObject(step.get("updateData"), "updateData");
      KnownKeys.assertKnownKeys(update, UPDATE_KEYS, where + " updateData");
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("surfaceId", SURFACE);
      body.put("path", update.get("path"));
      body.put("value", update.get("value"));
      run.process("updateDataModel", body);
    } else if (Boolean.TRUE.equals(step.get("cancelSurfaceCalls"))) {
      run.processor.rpc().cancelSurfaceCalls(SURFACE);
    } else if (Boolean.TRUE.equals(step.get("deleteSurface"))) {
      run.process("deleteSurface", Map.of("surfaceId", SURFACE));
      run.deleted = true;
    } else {
      throw new AssertionError(where + ": the harness does not know the work of the step");
    }
  }

  /** Does an action at the root of the data, with a user gesture, as the action of a node. */
  private static void action(Run run, String key, Object written) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(key, written);
    ResolverHooks.runAction(run.surface, "/", OptionalInt.empty(), true, payload, ROOT, null);
  }

  private static void verify(
      Run run, Map<String, Object> step, List<Map<String, Object>> sent, String where) {
    List<Object> calls = new ArrayList<>();
    for (Map<String, Object> message : sent) {
      Map<String, Object> body = asObject(message.get("callAgentFunction"), "callAgentFunction");
      if (!SURFACE.equals(body.get("surfaceId"))) {
        throw new AssertionError(where + ": a call names a different surface: " + body);
      }
      calls.add(body.get("callFunction"));
    }
    List<Object> expectedCalls =
        step.get("expectOutbound") == null
            ? List.of()
            : asList(step.get("expectOutbound"), "expectOutbound");
    if (Boolean.TRUE.equals(step.get("outboundInAnyOrder"))) {
      List<Object> unmatched = new ArrayList<>(calls);
      for (Object expected : expectedCalls) {
        int match = -1;
        for (int position = 0; position < unmatched.size() && match < 0; position++) {
          if (jsonEquals(expected, unmatched.get(position))) {
            match = position;
          }
        }
        if (match < 0) {
          throw new AssertionError(where + ": no call <" + expected + "> in <" + calls + ">");
        }
        unmatched.remove(match);
      }
      if (!unmatched.isEmpty()) {
        throw new AssertionError(where + ": calls that the step does not expect: " + unmatched);
      }
    } else {
      assertJsonEquals(expectedCalls, calls, where + " outbound calls");
    }

    List<Object> expectedErrors =
        step.get("expectErrors") == null
            ? List.of()
            : asList(step.get("expectErrors"), "expectErrors");
    List<String> reported =
        run.errors.stream()
            .map(error -> error.errorCode().orElse(null) + ": " + error.getMessage())
            .toList();
    if (expectedErrors.size() != run.errors.size()) {
      throw new AssertionError(
          where + ": expected " + expectedErrors.size() + " errors but got " + reported);
    }
    for (int position = 0; position < expectedErrors.size(); position++) {
      Map<String, Object> expected = asObject(expectedErrors.get(position), "expectErrors");
      KnownKeys.assertKnownKeys(expected, ERROR_KEYS, where + " expectErrors");
      A2uiError actual = run.errors.get(position);
      String fragment =
          expected.get("message") == null ? "" : asString(expected.get("message"), "message");
      if (!expected.get("code").equals(actual.errorCode().orElse(null))
          || !String.valueOf(actual.getMessage()).contains(fragment)) {
        throw new AssertionError(
            where + ": expected error <" + expected + "> but got <" + reported.get(position) + ">");
      }
    }

    if (step.containsKey("expectValues")) {
      if (run.deleted) {
        throw new AssertionError(where + ": a deleted surface has no values");
      }
      assertJsonEquals(step.get("expectValues"), run.values(), where + " values");
    }

    List<Object> expectedActions =
        step.get("expectActions") == null
            ? List.of()
            : asList(step.get("expectActions"), "expectActions");
    for (Object expected : expectedActions) {
      KnownKeys.assertKnownKeys(
          asObject(expected, "expectActions"), ACTION_KEYS, where + " expectActions");
    }
    List<Object> dispatched = new ArrayList<>();
    for (RendererAction action : run.actions) {
      Map<String, Object> shown = new LinkedHashMap<>();
      shown.put("name", action.name());
      shown.put("context", action.context());
      action.userMessage().ifPresent(message -> shown.put("userMessage", message));
      dispatched.add(shown);
    }
    assertJsonEquals(expectedActions, dispatched, where + " actions");
  }
}
