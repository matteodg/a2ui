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
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.state.ComponentModel;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.core.validation.ValidationConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The actions {@code process_messages} and {@code validate}: one {@link MessageProcessor} gets the
 * messages of a case. Then the handler examines the surfaces, or the error.
 *
 * <p>A {@code validate} case and a case with {@code strictMode: true} use the strict validation.
 * Each other case uses the relaxed validation. The handler compares the properties of a component
 * as the messages write them, because this SDK does not yet resolve bindings.
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

  private static final Set<String> SURFACE_KEYS =
      Set.of("components", "dataModel", "exists", "metadata", "sendDataModel");

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

  private static Catalog<ComponentApi, FunctionApi> basicAs(String id) {
    return Catalog.fromJson(BasicCatalog.api().toJson(), id, null);
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
        assertJsonEquals(value, found, where + ", component '" + id + "', '" + key + "'");
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
