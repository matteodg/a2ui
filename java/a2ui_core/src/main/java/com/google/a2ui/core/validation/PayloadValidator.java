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

package com.google.a2ui.core.validation;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.common.Uax31;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Examines one component or one function call against one v1.0 catalog.
 *
 * <p>A surface can use more than one catalog, and this validator knows only its own catalog. The
 * validator fully examines a nested function call that runs in its catalog. Such a call names this
 * catalog, or it names no catalog and its component also names none. For each other call, the
 * validator examines only what needs no catalog: the structure of the call and the syntax of its
 * names.
 *
 * <p>A validator is not thread-safe.
 */
public final class PayloadValidator {
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
   * Makes a validator for one catalog.
   *
   * @param catalog the catalog to examine against
   * @throws A2uiCatalogError if the catalog is not a v1.0 catalog, or if it has no validation
   *     schema. See {@link Catalog#validationSchema()}.
   */
  public PayloadValidator(Catalog<?, ?> catalog) {
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.engine = new JsonSchemaEngine(engineDocument(catalog));
  }

  /**
   * Examines a component object, as the message {@code updateComponents} contains it.
   *
   * @param component the component as plain JSON values
   * @throws A2uiValidationError if the component names a type that the catalog does not define, if
   *     it does not agree with the schema of its type, or if it contains a function call that is
   *     not correct. The message lists each problem.
   * @throws A2uiCatalogError if the schema of the type has a reference that names nothing
   */
  public void validateComponent(Map<String, ?> component) {
    Map<String, Object> value = frozen(component);
    String label = "Component '" + (value.get("id") == null ? "?" : value.get("id")) + "'";
    if (!(value.get("component") instanceof String type)) {
      throw new A2uiValidationError(label + " has no string 'component' naming its type.");
    }
    if (!catalog.components().containsKey(type)) {
      throw new A2uiValidationError(
          label
              + " has an unknown component type '"
              + type
              + "' for catalog '"
              + catalog.id()
              + "'.");
    }

    List<String> errors =
        new ArrayList<>(engine.errors("/" + COMPONENTS + "/" + Json.escapePointer(type), value));
    // A component that names its catalog is possibly not in the default catalog of the surface.
    // A call that names no catalog runs in that default catalog. Thus this catalog cannot examine
    // such a call.
    checkCalls(value, "", !value.containsKey("@catalogId"), errors);
    if (!errors.isEmpty()) {
      throw new A2uiValidationError(
          label + " (" + type + ") is invalid: " + String.join("; ", errors));
    }
  }

  /**
   * Examines a call of a function of this catalog, and the calls in its arguments.
   *
   * @param name the name of the function
   * @param args the arguments of the call as plain JSON values
   * @throws A2uiValidationError if the catalog does not define the function, or if the arguments do
   *     not agree with its schema. The message lists each problem.
   * @throws A2uiCatalogError if the schema of the function has a reference that names nothing
   */
  public void validateFunction(String name, Map<String, ?> args) {
    Map<String, Object> call = new LinkedHashMap<>();
    call.put("@call", Objects.requireNonNull(name, "name"));
    call.put("args", Objects.requireNonNull(args, "args"));
    List<String> errors = new ArrayList<>();
    checkCalls(frozen(call), "", true, errors);
    if (!errors.isEmpty()) {
      throw new A2uiValidationError(
          "Call to '" + name + "' is invalid: " + String.join("; ", errors));
    }
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

  private static Map<String, Object> frozen(Map<String, ?> value) {
    Objects.requireNonNull(value, "value");
    try {
      return Json.freezeObject(value);
    } catch (IllegalArgumentException e) {
      throw new A2uiValidationError("The value to validate is not JSON: " + e.getMessage(), e);
    }
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
    Map<String, Object> definitions = new LinkedHashMap<>(CommonTypeValidator.definitions());
    definitions.putAll(Json.asObject(document.get("$defs")));
    // The schema examines the envelope of a nested call. `checkCall` examines the function and the
    // arguments, because they are in the catalog in which the call runs.
    definitions.put("FunctionCall", CommonTypeValidator.definitions().get("FunctionCall"));
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
