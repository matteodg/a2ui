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

import com.google.a2ui.core.catalog.AllowedCallers;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.common.Uax31;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

  /** The properties that the envelope of each component declares. */
  private static final Set<String> ENVELOPE =
      Set.copyOf(
          Json.asObject(
                  Json.asObject(SchemaReferences.validationTypes().get("ComponentCommon"))
                      .get("properties"))
              .keySet());

  private final Catalog<?, ?> catalog;
  private final JsonSchemaEngine engine;
  private final CatalogSchemaIndex index;

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
    this.index = new CatalogSchemaIndex(catalog);
  }

  /**
   * Finds the problems of a component of this catalog.
   *
   * @param component the component, as {@link Json#freeze(Object)} makes it
   * @param unqualifiedCallsRunHere true if a nested call that names no catalog runs in this catalog
   * @param allowUnknown true if a component type or a function that the catalog does not define is
   *     not a problem. Such an item is not examined.
   * @return the problems. The list is empty if the component is correct.
   * @throws A2uiCatalogError if the schema of the type has a reference that names nothing
   */
  public List<Problem> componentProblems(
      Map<String, Object> component, boolean unqualifiedCallsRunHere, boolean allowUnknown) {
    List<Problem> errors = new ArrayList<>();
    if (!(component.get("component") instanceof String type)) {
      errors.add(new Problem("/component", Problem.MISSING_FIELD, "'component' must name a type"));
      return errors;
    }
    if (catalog.components().containsKey(type)) {
      List<Problem> found =
          engine.problems("/" + COMPONENTS + "/" + Json.escapePointer(type), component);
      // A problem of the schema of the type or of the envelope makes each of its properties look
      // unused. Only the cause is reported.
      Set<String> declared = index.declaredProperties(type);
      for (Problem problem : found) {
        if (!problem.followsFrom(found, name -> isDeclared(declared, name))) {
          errors.add(problem);
        }
      }
    } else if (!allowUnknown) {
      errors.add(
          new Problem(
              "/component",
              Problem.INVALID_VALUE,
              "Unrecognized component type '" + type + "' in catalog '" + catalog.id() + "'"));
    }
    checkCalls(component, "", unqualifiedCallsRunHere, allowUnknown, errors);
    return errors;
  }

  /**
   * Finds the problems of one function call that runs in this catalog. The method does not examine
   * the calls in the arguments.
   *
   * @param call the call, as {@link Json#freeze(Object)} makes it
   * @param path the JSON Pointer of the call. Each problem has a path below it.
   * @param allowUnknown true if a function that the catalog does not define is not a problem
   * @return the problems. The list is empty if the call is correct.
   * @throws A2uiCatalogError if the schema of the function has a reference that names nothing
   */
  public List<Problem> callProblems(Map<String, Object> call, String path, boolean allowUnknown) {
    List<Problem> errors = new ArrayList<>();
    checkCall(call, path, true, true, allowUnknown, errors);
    return errors;
  }

  /**
   * Finds the problems of each function call that is a node or is below a node.
   *
   * @param node the value to examine, as {@link Json#freeze(Object)} makes it
   * @param unqualifiedCallsRunHere true if a call that names no catalog runs in this catalog
   * @param allowUnknown true if a function that the catalog does not define is not a problem
   * @return the problems. The list is empty if each call is correct.
   * @throws A2uiCatalogError if the schema of a function has a reference that names nothing
   */
  public List<Problem> nestedCallProblems(
      Object node, boolean unqualifiedCallsRunHere, boolean allowUnknown) {
    List<Problem> errors = new ArrayList<>();
    checkCalls(node, "", unqualifiedCallsRunHere, allowUnknown, errors);
    return errors;
  }

  /**
   * Finds the problems of the arguments of a call of a function of this catalog. The method does
   * not examine who makes the call. Thus it is correct for a call of the agent.
   *
   * @param name the name of a function of this catalog
   * @param args the resolved arguments, as {@link Json#freeze(Object)} makes them
   * @return the problems. The list is empty if the arguments agree with the schema of the function.
   * @throws A2uiCatalogError if the schema of the function has a reference that names nothing
   */
  public List<Problem> argumentProblems(String name, Map<String, Object> args) {
    Map<String, Object> call = Map.of("@call", name, "args", args);
    List<Problem> found = engine.problems("/" + CALLS + "/" + Json.escapePointer(name), call);
    // A problem of an argument makes each argument look unused. Only the cause is reported.
    return found.stream().filter(problem -> !problem.followsFrom(found, property -> true)).toList();
  }

  /**
   * Examines each function call that is a node or is below a node.
   *
   * @param path the JSON Pointer of the node
   * @param unqualifiedCallsRunHere true if a call that names no catalog runs in this catalog
   * @param errors the list that receives the problems
   */
  private void checkCalls(
      Object node,
      String path,
      boolean unqualifiedCallsRunHere,
      boolean allowUnknown,
      List<Problem> errors) {
    if (node instanceof Map<?, ?> map) {
      if (map.containsKey("@call")) {
        checkCall(Json.asObject(map), path, unqualifiedCallsRunHere, false, allowUnknown, errors);
      }
      map.forEach((key, value) ->
          checkCalls(
              value,
              path + "/" + Json.escapePointer((String) key),
              unqualifiedCallsRunHere,
              allowUnknown,
              errors));
    } else if (node instanceof List<?> list) {
      for (int index = 0; index < list.size(); index++) {
        checkCalls(
            list.get(index), path + "/" + index, unqualifiedCallsRunHere, allowUnknown, errors);
      }
    }
  }

  /**
   * Examines one function call.
   *
   * @param alwaysRunsHere true if the caller knows that the call runs in this catalog. If it is
   *     false, the {@code catalogId} of the call and {@code unqualifiedCallsRunHere} make the
   *     decision.
   */
  private void checkCall(
      Map<String, Object> call,
      String path,
      boolean unqualifiedCallsRunHere,
      boolean alwaysRunsHere,
      boolean allowUnknown,
      List<Problem> errors) {
    if (!(call.get("@call") instanceof String name)) {
      errors.add(invalid(path, "'@call' must be a string"));
      return;
    }
    Object catalogId = call.get("catalogId");

    if (name.startsWith("@")) {
      // A system function is part of the protocol, not of a catalog.
      if (!name.equals(INDEX_FUNCTION)) {
        errors.add(invalid(path, "unknown system function '" + name + "'"));
      } else if (catalogId != null) {
        errors.add(invalid(path, "system function '" + name + "' must not name a catalogId"));
      } else {
        engine
            .problems(INDEX_FUNCTION_POINTER, call)
            .forEach(problem -> errors.add(problem.under(path)));
      }
      return;
    }

    if (!Uax31.isIdentifier(name)) {
      errors.add(invalid(path, "function name '" + name + "' is not a valid UAX #31 identifier"));
    }
    if (call.get("args") instanceof Map<?, ?> args) {
      for (Object argument : args.keySet()) {
        if (!Uax31.isIdentifier((String) argument)) {
          errors.add(
              invalid(
                  path,
                  "argument name '"
                      + argument
                      + "' of '"
                      + name
                      + "' is not a valid UAX #31 identifier"));
        }
      }
    }
    if (catalogId != null && !(catalogId instanceof String)) {
      errors.add(invalid(path, "'catalogId' must be a string"));
      return;
    }

    boolean runsHere =
        alwaysRunsHere
            || (catalogId == null ? unqualifiedCallsRunHere : catalogId.equals(catalog.id()));
    if (!runsHere) {
      return;
    }
    if (!catalog.functions().containsKey(name)) {
      if (!allowUnknown) {
        errors.add(
            invalid(
                path, "Unrecognized function '" + name + "' in catalog '" + catalog.id() + "'"));
      }
      return;
    }
    if (catalog.functions().get(name).allowedCallers() == AllowedCallers.AGENT_ONLY) {
      // Each call in a component is a call of the renderer: a binding, a check or an action.
      errors.add(
          invalid(path, "Function '" + name + "' is agentOnly, so the renderer cannot call it"));
    }
    // A problem of an argument makes each argument look unused. Only the cause is reported.
    List<Problem> found = engine.problems("/" + CALLS + "/" + Json.escapePointer(name), call);
    for (Problem problem : found) {
      if (!problem.followsFrom(found, property -> true)) {
        errors.add(problem.under(path));
      }
    }
  }

  /** Tells if the schema of a component type or the component envelope declares a property. */
  private static boolean isDeclared(Set<String> declaredByType, String name) {
    return name.equals("component") || declaredByType.contains(name) || ENVELOPE.contains(name);
  }

  /** Makes a problem of a call that is not a problem of a schema. */
  private static Problem invalid(String path, String message) {
    return new Problem(path, Problem.INVALID_VALUE, message);
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
                  reference("#/functions/" + Json.escapePointer(name)),
                  // A function that describes no arguments accepts all arguments. The schema
                  // examines only the arguments that the function describes.
                  Map.of("properties", Map.of("args", Map.of("type", "object")))),
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
