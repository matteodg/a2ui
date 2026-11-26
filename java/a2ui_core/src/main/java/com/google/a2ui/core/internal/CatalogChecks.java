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

package com.google.a2ui.core.internal;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.common.Uax31;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Finds the problems of one component or of one function call against one v1.0 catalog.
 *
 * <p>The public validator of a catalog and the message processor use this class. The validator
 * throws an error for the problems of one item. The processor collects the problems of all the
 * components of a message.
 *
 * <p>An instance is not thread-safe.
 */
public final class CatalogChecks {
  private static final String INDEX_FUNCTION = "@index";
  private static final String INDEX_FUNCTION_POINTER = "/$defs/IndexSystemFunction";

  /**
   * The member of the engine document that has the schema of a full component of each type: the
   * schema of the type and the envelope that each component has ({@code id}, {@code @catalogId},
   * {@code @accessibility}, {@code @metadata}). No other property is permitted. The v1.0 message
   * schema makes the same composition.
   */
  private static final String COMPONENTS = "x-a2ui-components";

  /** The member of the engine document that has the schema of a full call of each function. */
  private static final String CALLS = "x-a2ui-calls";

  private final Catalog<?, ?> catalog;
  private final JsonSchemaEngine engine;

  /**
   * Makes the checks for one catalog.
   *
   * @param catalog the catalog to examine against
   * @throws A2uiCatalogError if the catalog is not a v1.0 catalog, or if it has no validation
   *     schema. See {@link Catalog#validationSchema()}.
   */
  public CatalogChecks(Catalog<?, ?> catalog) {
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.engine = new JsonSchemaEngine(engineDocument(catalog));
  }

  /**
   * Finds the problems of a component of a type that the catalog defines.
   *
   * @param component the component, as {@link Json#freeze(Object)} makes it
   * @param type the type of the component. The catalog must define it.
   * @param unqualifiedCallsRunHere true if a nested call that names no catalog runs in this catalog
   * @return one message for each problem. The list is empty if the component is correct.
   * @throws A2uiCatalogError if the schema of the type has a reference that names nothing
   */
  public List<String> componentErrors(
      Map<String, Object> component, String type, boolean unqualifiedCallsRunHere) {
    List<String> errors =
        new ArrayList<>(
            engine.errors("/" + COMPONENTS + "/" + Json.escapePointer(type), component));
    checkCalls(component, "", unqualifiedCallsRunHere, errors);
    return errors;
  }

  /**
   * Finds the problems of each function call that is a node or is below a node.
   *
   * @param node the value to examine, as {@link Json#freeze(Object)} makes it
   * @param unqualifiedCallsRunHere true if a call that names no catalog runs in this catalog
   * @return one message for each problem. The list is empty if each call is correct.
   * @throws A2uiCatalogError if the schema of a function has a reference that names nothing
   */
  public List<String> nestedCallErrors(Object node, boolean unqualifiedCallsRunHere) {
    List<String> errors = new ArrayList<>();
    checkCalls(node, "", unqualifiedCallsRunHere, errors);
    return errors;
  }

  /**
   * Examines each function call that is a node or is below a node.
   *
   * @param path the JSON Pointer of the node, for the messages
   * @param unqualifiedCallsRunHere true if a call that names no catalog runs in this catalog
   * @param errors the list that receives the problems
   */
  private void checkCalls(
      Object node, String path, boolean unqualifiedCallsRunHere, List<String> errors) {
    if (node instanceof Map<?, ?> map) {
      if (map.containsKey("@call")) {
        checkCall(Json.asObject(map), path, unqualifiedCallsRunHere, errors);
      }
      map.forEach(
          (key, value) -> checkCalls(value, path + "/" + key, unqualifiedCallsRunHere, errors));
    } else if (node instanceof List<?> list) {
      for (int index = 0; index < list.size(); index++) {
        checkCalls(list.get(index), path + "/" + index, unqualifiedCallsRunHere, errors);
      }
    }
  }

  private void checkCall(
      Map<String, Object> call, String path, boolean unqualifiedCallsRunHere, List<String> errors) {
    String prefix = path.isEmpty() ? "" : path + ": ";
    if (!(call.get("@call") instanceof String name)) {
      errors.add(prefix + "'@call' must be a string");
      return;
    }
    Object catalogId = call.get("catalogId");

    if (name.startsWith("@")) {
      // A system function is part of the protocol, not of a catalog.
      if (!name.equals(INDEX_FUNCTION)) {
        errors.add(prefix + "unknown system function '" + name + "'");
      } else if (catalogId != null) {
        errors.add(prefix + "system function '" + name + "' must not name a catalogId");
      } else {
        engine.errors(INDEX_FUNCTION_POINTER, call).forEach(error -> errors.add(prefix + error));
      }
      return;
    }

    if (!Uax31.isIdentifier(name)) {
      errors.add(prefix + "function name '" + name + "' is not a UAX #31 identifier");
    }
    if (call.get("args") instanceof Map<?, ?> args) {
      for (Object argument : args.keySet()) {
        if (!Uax31.isIdentifier((String) argument)) {
          errors.add(
              prefix
                  + "argument name '"
                  + argument
                  + "' of '"
                  + name
                  + "' is not a UAX #31 identifier");
        }
      }
    }
    if (catalogId != null && !(catalogId instanceof String)) {
      errors.add(prefix + "'catalogId' must be a string");
      return;
    }

    boolean runsHere = catalogId == null ? unqualifiedCallsRunHere : catalogId.equals(catalog.id());
    if (!runsHere) {
      return;
    }
    if (!catalog.functions().containsKey(name)) {
      errors.add(prefix + "unknown function '" + name + "' in catalog '" + catalog.id() + "'");
      return;
    }
    engine
        .errors("/" + CALLS + "/" + Json.escapePointer(name), call)
        .forEach(error -> errors.add(prefix + error));
  }

  /**
   * Makes the document that the engine validates against: the validation schema of the catalog, the
   * common types of the envelopes, and one schema for each full component and each full call.
   */
  private static Map<String, Object> engineDocument(Catalog<?, ?> catalog) {
    Map<String, Object> document = new LinkedHashMap<>(catalog.validationSchema());
    // The validation schema has only the common types that the catalog uses. The envelopes also
    // need ComponentCommon, FunctionCommon and IndexSystemFunction. The catalog cannot define one
    // of these names, thus the two sets of definitions agree.
    Map<String, Object> definitions = new LinkedHashMap<>(SchemaReferences.validationTypes());
    definitions.putAll(Json.asObject(document.get("$defs")));
    // The schema examines the envelope of a nested call. `checkCall` examines the function and the
    // arguments, because they are in the catalog in which the call runs.
    definitions.put("FunctionCall", SchemaReferences.validationTypes().get("FunctionCall"));
    document.put("$defs", definitions);

    Map<String, Object> components = new LinkedHashMap<>();
    Map<String, Object> flatComponents = Json.asObject(document.get("components"));
    for (String name : catalog.components().keySet()) {
      // The envelope closes the component. A component that closes itself does not know the
      // properties of the envelope, for example `@catalogId`. Thus only the envelope closes it.
      Map<String, Object> open = new LinkedHashMap<>(Json.asObject(flatComponents.get(name)));
      open.remove("unevaluatedProperties", false);
      components.put(
          name,
          Map.of(
              "allOf",
              List.of(reference("#/$defs/ComponentCommon"), open),
              "unevaluatedProperties",
              false));
    }
    document.put(COMPONENTS, components);

    Map<String, Object> calls = new LinkedHashMap<>();
    for (String name : catalog.functions().keySet()) {
      calls.put(
          name,
          Map.of(
              "allOf",
              List.of(
                  reference("#/$defs/FunctionCommon"),
                  reference("#/functions/" + Json.escapePointer(name))),
              "unevaluatedProperties",
              false));
    }
    document.put(CALLS, calls);
    return document;
  }

  private static Map<String, Object> reference(String target) {
    return Map.of("$ref", target);
  }
}
