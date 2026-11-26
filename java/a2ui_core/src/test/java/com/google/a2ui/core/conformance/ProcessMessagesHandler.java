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
import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.core.resolution.NodeResolver;
import com.google.a2ui.core.resolution.ResolvedBinding;
import com.google.a2ui.core.resolution.ValidationResult;
import com.google.a2ui.core.state.ComponentModel;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.core.validation.ValidationConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The actions {@code process_messages} and {@code validate}: one {@link MessageProcessor} gets the
 * messages of a case. Then the handler examines the surfaces, or the error.
 *
 * <p>A {@code validate} case and a case with {@code strictMode: true} use the strict validation.
 * Each other case uses the relaxed validation.
 *
 * <p>A property of a component is correct if it is equal to the expected value as the message
 * writes it, or as the node tree of the surface resolves it. The handler reads the results of the
 * checks of a component from the node tree.
 */
final class ProcessMessagesHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys(
          "action",
          "catalog",
          "catalogPaths",
          "catalogs",
          "expect",
          "expectError",
          "messages",
          "payload",
          "protocolVersion",
          "steps",
          "strictMode",
          "validationConfig");

  private static final Set<String> STEP_KEYS =
      Set.of(
          "addCatalogPaths",
          "description",
          "expect",
          "expectError",
          "messages",
          "payload",
          "validationConfig");

  private static final Set<String> EXPECT_KEYS = Set.of("surfaces");

  /** The expectations about the checks of the components, by component ID. */
  private static final List<String> VALIDATION_FIELDS =
      List.of("isValid", "validationErrors", "validationResults", "validationResult");

  private static final Set<String> SURFACE_KEYS =
      Set.of(
          "components",
          "dataModel",
          "exists",
          "metadata",
          "sendDataModel",
          "isValid",
          "validationErrors",
          "validationResults",
          "validationResult");

  /** The keys of an expected component that are not properties of the component. */
  private static final Set<String> IDENTITY_KEYS = Set.of("id", "component", "@metadata");

  private static final Set<String> CONFIG_KEYS =
      Set.of(
          "allowDanglingReferences",
          "allowMissingRoot",
          "allowOrphanComponents",
          "allowUnknownElements");

  /**
   * A case that expects a {@code ValidationError} from processing also accepts a catalog error, as
   * the harnesses of the other SDKs do. These cases do not specify which of the two errors a
   * catalog reference that is not usable causes.
   */
  private static final Map<String, List<Class<? extends A2uiError>>> PROCESSING_CATEGORIES =
      Map.of("ValidationError", List.of(A2uiValidationError.class, A2uiCatalogError.class));

  private final boolean alwaysStrict;

  /**
   * Makes a handler.
   *
   * @param alwaysStrict true if each case uses the strict validation, as {@code validate} cases do
   */
  ProcessMessagesHandler(boolean alwaysStrict) {
    this.alwaysStrict = alwaysStrict;
  }

  @Override
  public void run(ConformanceCase testCase) throws Exception {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    // A `validate` case is strict unless the case tells that it is not.
    boolean strict =
        Boolean.TRUE.equals(fields.get("strictMode"))
            || (alwaysStrict && !Boolean.FALSE.equals(fields.get("strictMode")));
    ValidationConfig config =
        fields.get("validationConfig") != null
            ? flags(fields.get("validationConfig"), location)
            : strict ? ValidationConfig.STRICT : ValidationConfig.RELAXED;
    // The messages that the processor sent to the agent for the payload that it processes now.
    List<Map<String, Object>> sent = new ArrayList<>();
    Supplier<Map<String, Object>> lastSent = () -> sent.isEmpty() ? null : sent.getLast();
    MessageProcessor<ComponentApi> processor =
        new MessageProcessor<>(catalogsFor(testCase), config, sent::add);

    if (fields.get("steps") == null) {
      Object payload = payload(fields);
      Object expectError = ExpectedErrors.expectedError(fields);
      if (expectError != null) {
        ExpectedErrors.assertRaises(
            expectError,
            location,
            PROCESSING_CATEGORIES,
            lastSent,
            () -> processor.processMessages(payload));
        return;
      }
      processor.processMessages(payload);
    } else {
      List<Object> steps = asList(fields.get("steps"), "steps in " + location);
      for (int index = 0; index < steps.size(); index++) {
        String where = "step " + index + " of " + location;
        Map<String, Object> step = asObject(steps.get(index), where);
        KnownKeys.assertKnownKeys(step, STEP_KEYS, where);
        ExpectedErrors.Block process =
            () -> {
              sent.clear();
              runStep(processor, step, where);
            };
        Object expectError = ExpectedErrors.expectedError(step);
        if (expectError == null && index == steps.size() - 1) {
          expectError = ExpectedErrors.expectedError(fields);
        }
        if (expectError != null) {
          ExpectedErrors.assertRaises(expectError, where, PROCESSING_CATEGORIES, lastSent, process);
        } else {
          process.run();
          if (step.get("expect") != null) {
            checkSurfaces(processor, step.get("expect"), where);
          }
        }
      }
    }
    if (fields.get("expect") != null) {
      checkSurfaces(processor, fields.get("expect"), location);
    }
  }

  /** Adds the catalogs of a step to the processor, and then processes the messages of the step. */
  private static void runStep(
      MessageProcessor<ComponentApi> processor, Map<String, Object> step, String where) {
    if (step.get("addCatalogPaths") != null) {
      for (Object path : asList(step.get("addCatalogPaths"), "addCatalogPaths in " + where)) {
        processor.addCatalog(
            Catalog.fromJson(CatalogCases.jsonFile(asString(path, "addCatalogPaths in " + where))));
      }
    }
    Object payload = payload(step);
    if (step.get("validationConfig") != null) {
      processor.processMessages(payload, flags(step.get("validationConfig"), where));
    } else {
      processor.processMessages(payload);
    }
  }

  /** Gets the messages of a case or of a step. The suites use two names for this key. */
  private static Object payload(Map<String, Object> fields) {
    Object messages = fields.get("messages");
    return messages != null ? messages : fields.get("payload");
  }

  /** Makes the configuration that a {@code validationConfig} gives. An absent rule is strict. */
  private static ValidationConfig flags(Object raw, String where) {
    Map<String, Object> rules = asObject(raw, "validationConfig in " + where);
    KnownKeys.assertKnownKeys(rules, CONFIG_KEYS, "validationConfig in " + where);
    return new ValidationConfig(
        Boolean.TRUE.equals(rules.get("allowOrphanComponents")),
        Boolean.TRUE.equals(rules.get("allowDanglingReferences")),
        Boolean.TRUE.equals(rules.get("allowMissingRoot")),
        Boolean.TRUE.equals(rules.get("allowUnknownElements")));
  }

  /**
   * Gets the catalogs of a case. The selection is the selection of the harnesses of the other SDKs.
   *
   * <ul>
   *   <li>One {@code catalogPaths} document and no inline catalog: that document, with each ID that
   *       the {@code createSurface} messages of the case name. Many cases name a placeholder ID. A
   *       case that only makes surfaces and expects a catalog error is about the ID or the version
   *       that the surface names. Thus the document keeps its own ID and version.
   *   <li>More than one document, or inline {@code catalogs}: each catalog as it is, and the basic
   *       catalog with the ID {@code basic}.
   *   <li>An inline {@code catalog} that has components or an ID, or that has the document in its
   *       {@code catalogSchema}: that catalog, with the first ID that the messages name.
   *   <li>None of these: the basic catalog with the first ID that the messages name. A case that
   *       expects a missing catalog gets the basic catalog with a different ID.
   * </ul>
   *
   * <p>A catalog never gets the ID of a catalog that a step adds with {@code addCatalogPaths}.
   */
  static List<Catalog<ComponentApi, FunctionApi>> catalogsFor(ConformanceCase testCase) {
    Map<String, Object> fields = testCase.fields();
    ProtocolVersion declared = CatalogCases.version(testCase);
    ProtocolVersion version = declared != null ? declared : ProtocolVersion.V1_0;
    List<Map<String, Object>> inline = new ArrayList<>();
    if (fields.get("catalogs") != null) {
      for (Object entry : asList(fields.get("catalogs"), "catalogs in " + testCase.location())) {
        inline.add(asObject(entry, "catalogs entry in " + testCase.location()));
      }
    }
    List<Map<String, Object>> documents = new ArrayList<>();
    if (fields.get("catalogPaths") != null) {
      for (Object path : asList(fields.get("catalogPaths"), "catalogPaths")) {
        documents.add(CatalogCases.jsonFile(asString(path, "catalogPaths entry")));
      }
    }
    Set<String> named = surfaceCatalogIds(testCase);
    named.removeAll(addedCatalogIds(testCase));

    boolean expectsCatalogError =
        ExpectedErrors.expectedError(fields) instanceof Map<?, ?> expected
            && "CatalogError".equals(expected.get("category"));
    List<Catalog<ComponentApi, FunctionApi>> catalogs = new ArrayList<>();
    if (documents.size() == 1 && inline.isEmpty()) {
      Map<String, Object> document = documents.getFirst();
      if (expectsCatalogError && onlyCreatesSurfaces(fields)) {
        return List.of(Catalog.fromJson(document));
      }
      Set<String> ids = named.isEmpty() ? Set.of((String) document.get("catalogId")) : named;
      for (String id : ids) {
        catalogs.add(Catalog.fromJson(document, id, version));
      }
      return catalogs;
    }
    if (!documents.isEmpty() || !inline.isEmpty()) {
      for (Map<String, Object> document : documents) {
        catalogs.add(Catalog.fromJson(document));
      }
      for (Map<String, Object> document : inline) {
        catalogs.add(Catalog.fromJson(document, null, version));
      }
      if (catalogs.stream().noneMatch(catalog -> catalog.id().equals("basic"))) {
        catalogs.add(basicAs("basic"));
      }
      return catalogs;
    }
    String firstNamed = named.isEmpty() ? "test-catalog" : named.iterator().next();
    if (fields.get("catalog") instanceof Map<?, ?> single) {
      if (single.get("catalogSchema") instanceof Map<?, ?> nested) {
        return List.of(
            Catalog.fromJson(asObject(nested, "catalog.catalogSchema"), firstNamed, version));
      }
      if (single.containsKey("components") || single.containsKey("catalogId")) {
        return List.of(Catalog.fromJson(asObject(single, "catalog"), firstNamed, version));
      }
    }
    return List.of(basicAs(expectsCatalogError ? "test-catalog" : firstNamed));
  }

  /** Tells if the top-level payload of a case is a list of {@code createSurface} messages. */
  private static boolean onlyCreatesSurfaces(Map<String, Object> fields) {
    return payload(fields) instanceof List<?> messages
        && messages.stream()
            .allMatch(
                message -> message instanceof Map<?, ?> map && map.containsKey("createSurface"));
  }

  /**
   * Makes the basic catalog with a different ID. Its functions have their code. Thus the node tree
   * of a surface can resolve the calls.
   */
  private static Catalog<ComponentApi, FunctionApi> basicAs(String id) {
    return Catalog.fromJson(BasicCatalog.api().toJson(), id, null)
        .<FunctionApi>withFunctions(
            BasicCatalog.withFunctions(Locale.US, uri -> {}).functions().values());
  }

  /**
   * Resolves the node tree of a surface and gets its nodes by component ID. A node that a template
   * made has the key {@code <id>_<index>}.
   */
  private static Map<String, ComponentNode<ComponentApi>> resolvedNodes(
      SurfaceModel<ComponentApi> surface) {
    Map<String, ComponentNode<ComponentApi>> nodes = new LinkedHashMap<>();
    // The handler reads the tree and then disposes it. The last properties stay available.
    NodeResolver<ComponentApi> resolver = new NodeResolver<>(surface);
    resolver.rootNode().get().ifPresent(root -> collectNodes(root, nodes));
    resolver.dispose();
    return nodes;
  }

  @SuppressWarnings("unchecked")
  private static void collectNodes(Object value, Map<String, ComponentNode<ComponentApi>> nodes) {
    switch (value) {
      case ComponentNode<?> found -> {
        ComponentNode<ComponentApi> node = (ComponentNode<ComponentApi>) found;
        String key =
            node.index().isPresent()
                ? node.componentId() + "_" + node.index().getAsInt()
                : node.componentId();
        if (nodes.putIfAbsent(key, node) == null) {
          collectNodes(node.props().get().asMap(), nodes);
        }
      }
      case Map<?, ?> map -> map.values().forEach(member -> collectNodes(member, nodes));
      case List<?> list -> list.forEach(item -> collectNodes(item, nodes));
      case null, default -> {}
    }
  }

  /** Writes a resolved property in the form that a case uses. */
  private static Object plain(Object value) {
    return switch (value) {
      case ResolvedBinding binding -> plain(binding.value());
      case ComponentNode<?> node -> node.componentId();
      case ValidationResult result -> result.toJson();
      case Map<?, ?> map -> {
        Map<Object, Object> json = new LinkedHashMap<>();
        map.forEach((key, member) -> json.put(key, plain(member)));
        yield json;
      }
      case List<?> list -> list.stream().map(ProcessMessagesHandler::plain).toList();
      case null, default -> value;
    };
  }

  /**
   * Tells if a resolved value is the expected value. A case can write a number as the text that a
   * view shows.
   */
  private static boolean matches(Object expected, Object actual) {
    return JsonValues.jsonEquals(expected, actual)
        || (expected instanceof String text
            && actual instanceof Number
            && Coercion.toText(actual).equals(text));
  }

  /** Examines the results of the checks of the components that a surface expectation names. */
  private static void checkValidation(
      Map<String, Object> expect,
      Supplier<Map<String, ComponentNode<ComponentApi>>> nodes,
      String where) {
    for (String field : VALIDATION_FIELDS) {
      if (expect.get(field) == null) {
        continue;
      }
      asObject(expect.get(field), field + " in " + where)
          .forEach((id, expected) -> {
            ComponentNode<ComponentApi> node = nodes.get().get(id);
            if (node == null) {
              throw new AssertionError(where + ": no node '" + id + "'");
            }
            NodeProps<ComponentApi> props = node.props().get();
            List<Object> failed =
                props.validationResults().stream().map(result -> (Object) result.toJson()).toList();
            String at = where + ", " + field + " of '" + id + "'";
            switch (field) {
              case "isValid" -> assertJsonEquals(expected, props.isValid(), at);
              case "validationErrors" -> assertJsonEquals(expected, props.validationErrors(), at);
              case "validationResults" -> assertJsonEquals(expected, failed, at);
              default -> {
                // The first failed result. A case names the members that it examines.
                if (failed.isEmpty()) {
                  throw new AssertionError(at + ": no check failed");
                }
                Map<String, Object> first = asObject(failed.getFirst(), at);
                asObject(expected, at)
                    .forEach((key, value) -> assertJsonEquals(value, first.get(key), at));
              }
            }
          });
    }
  }

  /** Gets the catalog IDs that the {@code createSurface} messages of a case name, in all steps. */
  private static Set<String> surfaceCatalogIds(ConformanceCase testCase) {
    Set<String> ids = new LinkedHashSet<>();
    collectCatalogIds(payload(testCase.fields()), ids);
    if (testCase.fields().get("steps") instanceof List<?> steps) {
      for (Object step : steps) {
        if (step instanceof Map<?, ?> map) {
          Object messages = map.get("messages");
          collectCatalogIds(messages != null ? messages : map.get("payload"), ids);
        }
      }
    }
    return ids;
  }

  private static void collectCatalogIds(Object payload, Set<String> ids) {
    if (payload instanceof List<?> list) {
      list.forEach(item -> collectCatalogIds(item, ids));
    } else if (payload instanceof Map<?, ?> map) {
      if (map.get("messages") instanceof List<?> wrapped) {
        collectCatalogIds(wrapped, ids);
      }
      if (map.get("createSurface") instanceof Map<?, ?> body
          && body.get("catalogId") instanceof String id) {
        ids.add(id);
      }
    }
  }

  /** Gets the IDs of the catalogs that the steps of a case add with {@code addCatalogPaths}. */
  private static Set<String> addedCatalogIds(ConformanceCase testCase) {
    Set<String> ids = new LinkedHashSet<>();
    if (testCase.fields().get("steps") instanceof List<?> steps) {
      for (Object step : steps) {
        if (step instanceof Map<?, ?> map && map.get("addCatalogPaths") instanceof List<?> paths) {
          for (Object path : paths) {
            Object id = CatalogCases.jsonFile(asString(path, "addCatalogPaths")).get("catalogId");
            if (id instanceof String text) {
              ids.add(text);
            }
          }
        }
      }
    }
    return ids;
  }

  private static void checkSurfaces(
      MessageProcessor<ComponentApi> processor, Object raw, String where) {
    Map<String, Object> expect = asObject(raw, "expect in " + where);
    KnownKeys.assertKnownKeys(expect, EXPECT_KEYS, "expect in " + where);
    if (expect.get("surfaces") == null) {
      return;
    }
    Map<String, Object> surfaces = asObject(expect.get("surfaces"), "expect.surfaces in " + where);
    for (Map.Entry<String, Object> entry : surfaces.entrySet()) {
      String surfaceId = entry.getKey();
      String surfaceWhere = where + ", surface '" + surfaceId + "'";
      Map<String, Object> expectations = asObject(entry.getValue(), surfaceWhere);
      KnownKeys.assertKnownKeys(expectations, SURFACE_KEYS, surfaceWhere);
      Optional<SurfaceModel<ComponentApi>> surface = processor.model().getSurface(surfaceId);
      if (Boolean.FALSE.equals(expectations.get("exists"))) {
        if (surface.isPresent()) {
          throw new AssertionError(where + ": surface '" + surfaceId + "' must not exist");
        }
        continue;
      }
      checkSurface(
          surface.orElseThrow(
              () -> new AssertionError(where + ": surface '" + surfaceId + "' must exist")),
          expectations,
          surfaceWhere);
    }
  }

  private static void checkSurface(
      SurfaceModel<ComponentApi> surface, Map<String, Object> expect, String where) {
    // The handler resolves the tree only for a case that needs it, and one time.
    List<Map<String, ComponentNode<ComponentApi>>> resolved = new ArrayList<>();
    Supplier<Map<String, ComponentNode<ComponentApi>>> nodes =
        () -> {
          if (resolved.isEmpty()) {
            resolved.add(resolvedNodes(surface));
          }
          return resolved.getFirst();
        };
    checkValidation(expect, nodes, where);
    if (expect.containsKey("sendDataModel")) {
      assertJsonEquals(
          expect.get("sendDataModel"), surface.sendDataModel(), where + " sendDataModel");
    }
    if (expect.containsKey("metadata")) {
      assertJsonEquals(
          expect.get("metadata"), metadataJson(surface.metadata()), where + " metadata");
    }
    if (expect.containsKey("dataModel")) {
      // The handler compares the full root. Thus it finds a key that the SDK did not delete.
      assertJsonEquals(expect.get("dataModel"), surface.dataModel().get("/"), where + " dataModel");
    }
    Object components = expect.get("components");
    if (components == null) {
      return;
    }
    Map<String, ComponentModel> actual = surface.componentsModel().all();
    // A list names each component of the surface. A map names only the components to examine.
    Map<String, Map<String, Object>> expected = new LinkedHashMap<>();
    if (components instanceof List<?> listed) {
      for (Object item : listed) {
        Map<String, Object> component = asObject(item, "component in " + where);
        expected.put(asString(component.get("id"), "component id in " + where), component);
      }
      if (!expected.keySet().equals(actual.keySet())) {
        throw new AssertionError(
            where + ": expected components " + expected.keySet() + " but was " + actual.keySet());
      }
    } else {
      asObject(components, "components in " + where)
          .forEach((id, component) -> expected.put(id, asObject(component, where)));
    }
    expected.forEach((id, properties) -> {
      ComponentModel component = actual.get(id);
      if (component == null) {
        throw new AssertionError(where + ": no component '" + id + "'");
      }
      properties.forEach((key, value) -> {
        Object found =
            switch (key) {
              case "id" -> component.id();
              case "component" -> component.type();
              case "@metadata" -> metadataJson(component.metadata());
              default -> component.properties().get(key);
            };
        String at = where + ", component '" + id + "', '" + key + "'";
        if (IDENTITY_KEYS.contains(key)) {
          assertJsonEquals(value, found, at);
          return;
        }
        if (JsonValues.jsonEquals(value, found)) {
          return;
        }
        ComponentNode<ComponentApi> node = nodes.get().get(id);
        Object plain = node == null ? null : plain(node.props().get().get(key));
        if (!matches(value, plain)) {
          throw new AssertionError(
              at + ": expected <" + value + "> but was <" + found + ">, resolved <" + plain + ">");
        }
      });
    });
  }

  /** Writes a metadata container as JSON, or null if there is no container. */
  private static Object metadataJson(Optional<A2uiMetadata> metadata) {
    if (metadata.isEmpty()) {
      return null;
    }
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("extensions", metadata.get().extensions().orElse(null));
    return json;
  }
}
