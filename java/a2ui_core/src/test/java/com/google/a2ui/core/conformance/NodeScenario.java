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
import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.Reactive;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.resolution.ActionDispatchOptions;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeAction;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.core.resolution.NodeResolver;
import com.google.a2ui.core.resolution.ResolvedBinding;
import com.google.a2ui.core.resolution.ResolverHooks;
import com.google.a2ui.core.resolution.ValidationResult;
import com.google.a2ui.core.resolution.WritableBinding;
import com.google.a2ui.core.state.ComponentModel;
import com.google.a2ui.core.state.RendererAction;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.core.validation.ValidationConfig;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/**
 * One run of a node resolution scenario: a fixture, an initial expectation and steps. The contract
 * of the operations and of the expectations is in {@code conformance/core/node_resolution.md}.
 */
final class NodeScenario {
  private static final String SURFACE = "s";
  private static final Set<String> FIXTURE_KEYS =
      Set.of("catalog", "data", "components", "surface");
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys("action", "fixture", "expect", "steps");
  private static final Set<String> STEP_KEYS =
      Set.of(
          "op",
          "expect",
          "path",
          "value",
          "components",
          "component_id",
          "node",
          "property",
          "metadata",
          "messages");
  private static final Set<String> EXPECT_KEYS =
      Set.of(
          "nodes",
          "emissions",
          "created_emissions",
          "metadata_emissions",
          "same_nodes",
          "replaced_nodes",
          "destroyed",
          "data",
          "events",
          "functions",
          "errors");
  private static final Set<String> NODE_KEYS =
      Set.of(
          "component_id",
          "type",
          "state",
          "data_path",
          "props",
          "metadata",
          "isValid",
          "validationErrors",
          "validationResults");

  /** The keys of a component that are not its properties. */
  private static final Set<String> ENVELOPE =
      Set.of("id", "component", "metadata", "@metadata", "@accessibility", "@checks", "@catalogId");

  /** The events that the scenario saw on one node. */
  private static final class Observed {
    private String position;
    private int emissions;
    private int metadataEmissions;
    private int destroyed;
    private boolean createdInStep;
    private NodeProps<ComponentApi> lastEmitted;
  }

  private final String where;
  private final Map<String, Object> fixture;
  private final Map<String, Object> fields;
  private final List<Map<String, Object>> functions = new ArrayList<>();
  private final List<RendererAction> events = new ArrayList<>();
  private final List<A2uiError> errors = new ArrayList<>();
  private final Map<ComponentNode<ComponentApi>, Observed> observed = new IdentityHashMap<>();
  private final Catalog<ComponentApi, FunctionImplementation> catalog;
  private final MessageProcessor<ComponentApi> processor;
  private Map<String, ComponentNode<ComponentApi>> positions = Map.of();
  private NodeResolver<ComponentApi> resolver;

  /**
   * Makes a scenario.
   *
   * @param where the location of the case for failure messages
   * @param fixture the content of the fixture
   * @param fields the content of the case
   */
  NodeScenario(String where, Map<String, Object> fixture, Map<String, Object> fields) {
    this.where = where;
    this.fixture = fixture;
    this.fields = fields;
    this.catalog = loadCatalog();
    this.processor = new MessageProcessor<>(List.of(catalog), ValidationConfig.RELAXED);
  }

  private Catalog<ComponentApi, FunctionImplementation> loadCatalog() {
    String path = asString(fixture.get("catalog"), "catalog of the fixture of " + where);
    Map<String, Object> document = ResolveNodesHandler.catalogDocument(path);
    Catalog<ComponentApi, FunctionApi> described =
        BasicCatalog.ID.equals(document.get("catalogId"))
            ? BasicCatalog.api()
            : Catalog.fromJson(document);
    // Each declared function records its call and returns null. The cases examine that a function
    // ran. They do not examine its effect.
    List<FunctionImplementation> recorders = new ArrayList<>();
    for (FunctionApi api : described.functions().values()) {
      recorders.add(
          new FunctionImplementation(
              api,
              (args, context) -> {
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("name", api.name());
                call.put("args", args);
                functions.add(call);
                return null;
              }));
    }
    return described.withFunctions(recorders);
  }

  private Optional<SurfaceModel<ComponentApi>> surface() {
    return processor.model().getSurface(SURFACE);
  }

  /** Runs the scenario. Throws if the SDK does not behave as the case expects. */
  void run() {
    KnownKeys.assertKnownKeys(fixture, FIXTURE_KEYS, "fixture of " + where);
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, where);
    if (Boolean.FALSE.equals(fixture.get("surface"))) {
      // The scenario makes the surface in a step. The resolver starts when the surface arrives.
      processor
          .model()
          .onSurfaceCreated()
          .subscribe(created -> {
            if (created.id().equals(SURFACE)) {
              attach(created);
            }
          });
    } else {
      Map<String, Object> createSurface = new LinkedHashMap<>();
      createSurface.put("surfaceId", SURFACE);
      createSurface.put("catalogId", catalog.id());
      processor.processMessages(Map.of("version", "1.0", "createSurface", createSurface));
      // The data and the components arrive before the resolver starts. The work at the time of a
      // subscription is setup, not an update.
      SurfaceModel<ComponentApi> seeded = surface().orElseThrow();
      if (fixture.get("data") instanceof Map<?, ?> data && !data.isEmpty()) {
        seeded.dataModel().set("/", data);
      }
      updateComponents(seeded, listOrEmpty(fixture.get("components"), "components of the fixture"));
      attach(seeded);
    }
    settle();
    resetCounters();
    Map<String, Object> initial = asObject(fields.get("expect"), "expect in " + where);
    KnownKeys.assertKnownKeys(initial, Set.of("nodes"), "expect in " + where);
    assertNodes(asObject(initial.get("nodes"), "expect.nodes"), "initial state of " + where);
    if (!events.isEmpty() || !functions.isEmpty()) {
      throw new AssertionError(where + ": an action ran during resolution");
    }

    List<Object> steps = listOrEmpty(fields.get("steps"), "steps in " + where);
    for (int index = 0; index < steps.size(); index++) {
      String at = "step " + index + " of " + where;
      step(asObject(steps.get(index), at), at);
    }
  }

  private static List<Object> listOrEmpty(Object value, String what) {
    return value == null ? List.of() : asList(value, what);
  }

  private void attach(SurfaceModel<ComponentApi> target) {
    target.onAction().subscribe(events::add);
    target.onError().subscribe(errors::add);
    NodeResolver<ComponentApi> created = new NodeResolver<>(target);
    ResolverHooks.onNodeCreated(created, node -> observe(node, true));
    resolver = created;
    walk(created.rootNode().get().orElse(null), "root", (position, node) -> observe(node, true));
  }

  private void observe(ComponentNode<ComponentApi> node, boolean createdInStep) {
    if (observed.containsKey(node)) {
      return;
    }
    Observed entry = new Observed();
    entry.createdInStep = createdInStep;
    observed.put(node, entry);
    node.props()
        .subscribe(props -> {
          entry.emissions++;
          entry.lastEmitted = props;
        });
    node.metadata().subscribe(metadata -> entry.metadataEmissions++);
    node.onDestroyed().subscribe(destroyed -> entry.destroyed++);
  }

  /** Visits each mounted node with its position, for example {@code root/children/0}. */
  private void walk(
      ComponentNode<ComponentApi> node,
      String position,
      BiConsumer<String, ComponentNode<ComponentApi>> visit) {
    if (node == null) {
      return;
    }
    visit.accept(position, node);
    node.props()
        .get()
        .asMap()
        .forEach((name, value) -> descend(value, position + "/" + name, visit));
  }

  @SuppressWarnings("unchecked")
  private void descend(
      Object value, String at, BiConsumer<String, ComponentNode<ComponentApi>> visit) {
    switch (value) {
      case ComponentNode<?> node -> walk((ComponentNode<ComponentApi>) node, at, visit);
      case Map<?, ?> map -> map.forEach((key, member) -> descend(member, at + "/" + key, visit));
      case List<?> list -> {
        for (int index = 0; index < list.size(); index++) {
          descend(list.get(index), at + "/" + index, visit);
        }
      }
      case null, default -> {}
    }
  }

  /** Records the position of each mounted node at this time. */
  private void settle() {
    Map<String, ComponentNode<ComponentApi>> current = new LinkedHashMap<>();
    Map<ComponentNode<ComponentApi>, String> seen = new IdentityHashMap<>();
    ComponentNode<ComponentApi> root =
        resolver == null || resolver.isDisposed() ? null : resolver.rootNode().get().orElse(null);
    walk(
        root,
        "root",
        (position, node) -> {
          String other = seen.put(node, position);
          if (other != null) {
            throw new AssertionError(
                where + ": one node is mounted at " + other + " and at " + position);
          }
          current.put(position, node);
          observe(node, true);
          observed.get(node).position = position;
        });
    positions = current;
  }

  private void resetCounters() {
    for (Observed entry : observed.values()) {
      entry.emissions = 0;
      entry.metadataEmissions = 0;
      entry.destroyed = 0;
      entry.createdInStep = false;
      entry.lastEmitted = null;
    }
    events.clear();
    functions.clear();
    errors.clear();
  }

  private void step(Map<String, Object> step, String at) {
    KnownKeys.assertKnownKeys(step, STEP_KEYS, at);
    Map<String, ComponentNode<ComponentApi>> before = positions;
    resetCounters();
    operate(step, before, at);
    settle();

    Map<String, Object> expect = asObject(step.get("expect"), "expect in " + at);
    KnownKeys.assertKnownKeys(expect, EXPECT_KEYS, "expect in " + at);
    assertNodes(asObject(expect.get("nodes"), "expect.nodes in " + at), at);

    Map<String, ComponentNode<ComponentApi>> retained = new LinkedHashMap<>();
    positions.forEach((position, node) -> {
      if (!observed.get(node).createdInStep) {
        retained.put(position, node);
      }
    });
    if (expect.get("emissions") != null) {
      assertCounts(expect.get("emissions"), retained, "emissions in " + at, e -> e.emissions);
    }
    assertCounts(
        expect.get("metadata_emissions") == null ? Map.of() : expect.get("metadata_emissions"),
        retained,
        "metadata_emissions in " + at,
        e -> e.metadataEmissions);
    if (expect.get("created_emissions") != null) {
      asObject(expect.get("created_emissions"), "created_emissions in " + at)
          .forEach((position, count) -> {
            Observed entry = observed.get(nodeAt(positions, position, at));
            if (!entry.createdInStep) {
              throw new AssertionError(
                  at + ": the node at " + position + " was not created in this step");
            }
            assertJsonEquals(
                count, entry.emissions, "created_emissions of " + position + " in " + at);
          });
    }
    retained.forEach((position, node) -> {
      Observed entry = observed.get(node);
      if (entry.emissions > 0 && !node.props().get().equals(entry.lastEmitted)) {
        throw new AssertionError(
            at + ": the last props that " + position + " emitted are not its final props");
      }
    });
    for (Object position : listOrEmpty(expect.get("same_nodes"), "same_nodes in " + at)) {
      if (before.get(position) == null || before.get(position) != positions.get(position)) {
        throw new AssertionError(at + ": expected the same node at " + position);
      }
    }
    for (Object position : listOrEmpty(expect.get("replaced_nodes"), "replaced_nodes in " + at)) {
      if (before.get(position) == null
          || positions.get(position) == null
          || before.get(position) == positions.get(position)) {
        throw new AssertionError(at + ": expected a replaced node at " + position);
      }
    }

    Map<String, Object> destroyed = new LinkedHashMap<>();
    observed.forEach((node, entry) -> {
      if (entry.destroyed == 0) {
        return;
      }
      if (!node.isDisposed()) {
        throw new AssertionError(at + ": a destroyed node is not disposed");
      }
      // A node that came and went in one step was never at a position that a case can name.
      if (entry.position != null) {
        destroyed.put(entry.position, entry.destroyed);
      }
    });
    assertJsonEquals(
        expect.get("destroyed") == null ? Map.of() : expect.get("destroyed"),
        destroyed,
        "destroyed in " + at);

    if (expect.get("data") != null) {
      asObject(expect.get("data"), "data in " + at)
          .forEach((path, value) ->
              assertJsonEquals(
                  value,
                  surface().map(model -> model.dataModel().get(path)).orElse(null),
                  "data at " + path + " in " + at));
    }
    List<Object> expectedEvents = listOrEmpty(expect.get("events"), "events in " + at);
    List<Object> actualEvents = new ArrayList<>();
    for (int index = 0; index < events.size(); index++) {
      RendererAction event = events.get(index);
      Map<String, Object> actual = new LinkedHashMap<>();
      actual.put("name", event.name());
      actual.put("source_component_id", event.sourceComponentId());
      actual.put("context", event.context());
      boolean named =
          index < expectedEvents.size()
              && expectedEvents.get(index) instanceof Map<?, ?> expected
              && expected.containsKey("metadata");
      if (named || event.metadata().isPresent()) {
        actual.put("metadata", event.metadata().map(NodeScenario::metadataJson).orElse(null));
      }
      actualEvents.add(actual);
    }
    assertJsonEquals(expectedEvents, actualEvents, "events in " + at);
    assertJsonEquals(
        listOrEmpty(expect.get("functions"), "functions in " + at),
        functions,
        "functions in " + at);
    if (expect.get("errors") != null) {
      List<Object> actual = new ArrayList<>();
      for (A2uiError error : errors) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("code", error.errorCode().orElse(null));
        entry.put("surfaceId", error.surfaceId().orElse(null));
        actual.add(entry);
      }
      assertJsonEquals(expect.get("errors"), actual, "errors in " + at);
    }
  }

  private void operate(
      Map<String, Object> step, Map<String, ComponentNode<ComponentApi>> before, String at) {
    String op = asString(step.get("op"), "op in " + at);
    switch (op) {
      case "set_data" ->
          Reactive.batch(() ->
              surface()
                  .orElseThrow()
                  .dataModel()
                  .set(asString(step.get("path"), "path in " + at), step.get("value")));
      case "update_components" ->
          Reactive.batch(() ->
              updateComponents(
                  surface().orElseThrow(), asList(step.get("components"), "components in " + at)));
      case "remove_component" ->
          Reactive.batch(() ->
              surface()
                  .orElseThrow()
                  .componentsModel()
                  .removeComponent(asString(step.get("component_id"), "component_id in " + at)));
      case "write" -> {
        if (!(property(before, step, at) instanceof WritableBinding binding)) {
          throw new AssertionError(at + ": the property is not writable");
        }
        binding.set(step.get("value"));
      }
      case "invoke" -> {
        if (!(property(before, step, at) instanceof NodeAction action)) {
          throw new AssertionError(at + ": the property is not an action");
        }
        if (step.get("metadata") == null) {
          action.invoke();
        } else {
          Map<String, Object> metadata = asObject(step.get("metadata"), "metadata in " + at);
          action.invoke(
              ActionDispatchOptions.withMetadata(
                  new A2uiMetadata(
                      Optional.of(asObject(metadata.get("extensions"), "extensions in " + at)))));
        }
      }
      case "process_messages" -> processor.processMessages(step.get("messages"));
      case "dispose" -> resolver.dispose();
      default -> throw new AssertionError("Unknown op '" + op + "' in " + at);
    }
  }

  private static ComponentNode<ComponentApi> nodeAt(
      Map<String, ComponentNode<ComponentApi>> nodes, Object position, String at) {
    ComponentNode<ComponentApi> node = nodes.get(position);
    if (node == null) {
      throw new AssertionError(at + ": no node at " + position);
    }
    return node;
  }

  private static Object property(
      Map<String, ComponentNode<ComponentApi>> before, Map<String, Object> step, String at) {
    return nodeAt(before, step.get("node"), at)
        .props()
        .get()
        .get(asString(step.get("property"), "property in " + at));
  }

  /** Adds components to the model, or replaces their properties. It does not use the processor. */
  @SuppressWarnings("unchecked")
  private void updateComponents(SurfaceModel<ComponentApi> target, List<Object> components) {
    for (Object raw : components) {
      Map<String, Object> written = asObject(raw, "component");
      String id = asString(written.get("id"), "component id");
      String type = asString(written.get("component"), "component type");
      Map<String, Object> properties = new LinkedHashMap<>(written);
      properties.keySet().removeAll(ENVELOPE);
      Object rawMetadata =
          written.get("@metadata") != null ? written.get("@metadata") : written.get("metadata");
      A2uiMetadata metadata =
          rawMetadata instanceof Map<?, ?> container
              ? new A2uiMetadata(
                  Optional.ofNullable((Map<String, Object>) container.get("extensions")))
              : null;
      Map<String, Object> accessibility =
          written.get("@accessibility") == null
              ? null
              : asObject(written.get("@accessibility"), "@accessibility");
      List<Object> checks =
          written.get("@checks") == null ? null : asList(written.get("@checks"), "@checks");
      Optional<ComponentModel> existing = target.componentsModel().get(id);
      if (existing.isPresent()) {
        existing.get().replaceDefinition(properties, metadata, accessibility, checks);
      } else {
        target
            .componentsModel()
            .addComponent(
                new ComponentModel(id, type, catalog, properties, metadata, accessibility, checks));
      }
    }
  }

  private void assertNodes(Map<String, Object> expected, String at) {
    if (!expected.keySet().equals(positions.keySet())) {
      throw new AssertionError(
          at
              + ": expected nodes at "
              + expected.keySet()
              + " but they are at "
              + positions.keySet());
    }
    Map<ComponentNode<ComponentApi>, String> located = new IdentityHashMap<>();
    positions.forEach((position, node) -> located.put(node, position));
    expected.forEach((position, raw) -> {
      ComponentNode<ComponentApi> node = positions.get(position);
      String what = "node " + position + " in " + at;
      Map<String, Object> expectedFields = asObject(raw, what);
      KnownKeys.assertKnownKeys(expectedFields, NODE_KEYS, what);
      Map<String, Object> props = node.props().get().asMap();
      expectedFields.forEach((field, value) -> {
        Object actual =
            switch (field) {
              case "component_id" -> node.componentId();
              case "type" -> node.type();
              case "state" -> node.state().wireValue();
              case "data_path" -> node.dataPath();
              case "metadata" -> node.metadata().get().map(NodeScenario::metadataJson).orElse(null);
              case "props" -> {
                Map<String, Object> named = new LinkedHashMap<>();
                asObject(value, "props of " + position)
                    .keySet()
                    .forEach(name -> named.put(name, plain(props.get(name), located)));
                yield named;
              }
              default -> plain(props.get(field), located);
            };
        assertJsonEquals(value, actual, field + " of " + position + " in " + at);
      });
      List<String> siblings = new ArrayList<>();
      collectInstanceIds(props, siblings);
      if (siblings.size() != Set.copyOf(siblings).size()) {
        throw new AssertionError(
            at + ": children of " + position + " share an instanceId: " + siblings);
      }
    });
  }

  private static void collectInstanceIds(Object value, List<String> ids) {
    switch (value) {
      case ComponentNode<?> node -> ids.add(node.instanceId());
      case Map<?, ?> map -> map.values().forEach(member -> collectInstanceIds(member, ids));
      case List<?> list -> list.forEach(item -> collectInstanceIds(item, ids));
      case null, default -> {}
    }
  }

  private static Map<String, Object> metadataJson(A2uiMetadata metadata) {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("extensions", metadata.extensions().orElse(null));
    return json;
  }

  /** Writes a resolved value in the form that the cases use. */
  private static Object plain(Object value, Map<ComponentNode<ComponentApi>, String> located) {
    return switch (value) {
      case ResolvedBinding binding -> {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("value", binding.value());
        json.put("writable", binding.isWritable());
        yield json;
      }
      case ComponentNode<?> node -> {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("node", located.get(node));
        yield json;
      }
      case NodeAction _ -> Map.of("action", true);
      case ValidationResult result -> result.toJson();
      case Map<?, ?> map -> {
        Map<Object, Object> json = new LinkedHashMap<>();
        map.forEach((key, member) -> json.put(key, plain(member, located)));
        yield json;
      }
      case List<?> list -> list.stream().map(item -> plain(item, located)).toList();
      case null, default -> value;
    };
  }

  private void assertCounts(
      Object expected,
      Map<String, ComponentNode<ComponentApi>> retained,
      String what,
      ToIntFunction<Observed> count) {
    Map<String, Object> actual = new LinkedHashMap<>();
    retained.forEach((position, node) -> {
      int counted = count.applyAsInt(observed.get(node));
      if (counted > 0) {
        actual.put(position, counted);
      }
    });
    assertJsonEquals(expected, actual, what);
  }
}
