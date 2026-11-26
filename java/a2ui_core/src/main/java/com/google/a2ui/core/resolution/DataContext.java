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

package com.google.a2ui.core.resolution;

import com.google.a2ui.core.catalog.AllowedCallers;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiDataError;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.expressions.Expression;
import com.google.a2ui.core.internal.Json;
import com.google.a2ui.core.state.DataModel;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * The scope in which the core evaluates a data binding or a function call: a surface, a position in
 * the data of the surface, and the permissions of the caller.
 *
 * <p>Each call that a context evaluates is a call of the renderer: a property binding, a check or
 * an action. Thus a context refuses a function that only the agent can call.
 *
 * <p>A context cannot change. {@link #nested(String)} and {@link #withUserActivation()} make a new
 * context.
 */
public final class DataContext {
  /** The value of {@link #index} outside a list template. */
  private static final int NO_INDEX = -1;

  /** The function of the protocol that gives the position of the current list item. */
  private static final String INDEX_FUNCTION = "@index";

  /** The name of the common type whose values the context reads as booleans. */
  private static final String DYNAMIC_BOOLEAN = "DynamicBoolean";

  private final SurfaceModel<?> surface;
  private final String path;
  private final int index;
  private final boolean userActivated;

  /**
   * The catalog of the function that runs in this context, or null if no function runs in it. A
   * call that a template of that function writes runs in this catalog.
   */
  private final Catalog<?, ?> callCatalog;

  /**
   * Makes a context at the root of the data of a surface, outside a list template.
   *
   * @param surface the surface whose data and catalogs the context uses
   */
  public DataContext(SurfaceModel<?> surface) {
    this(surface, "/");
  }

  /**
   * Makes a context at a path of the data of a surface, outside a list template.
   *
   * @param surface the surface whose data and catalogs the context uses
   * @param path the data path from which the context reads relative paths. A path that does not
   *     start with {@code /} starts at the root.
   */
  public DataContext(SurfaceModel<?> surface, String path) {
    this(
        Objects.requireNonNull(surface, "surface"),
        DataModel.resolvePath(Objects.requireNonNull(path, "path"), null),
        NO_INDEX,
        false,
        null);
  }

  private DataContext(
      SurfaceModel<?> surface,
      String path,
      int index,
      boolean userActivated,
      Catalog<?, ?> callCatalog) {
    this.surface = surface;
    this.path = path;
    this.index = index;
    this.userActivated = userActivated;
    this.callCatalog = callCatalog;
  }

  /**
   * Gets the surface whose data and catalogs the context uses.
   *
   * @return the surface
   */
  public SurfaceModel<?> surface() {
    return surface;
  }

  /**
   * Gets the data path from which the context reads relative paths.
   *
   * @return the absolute path. It is {@code /} at the top of the data. In a list template, it is
   *     the path of one item of the list.
   */
  public String path() {
    return path;
  }

  /**
   * Gets the position of the current item in a list template.
   *
   * @return the position, where the first item is 0. The result is empty outside a list template.
   */
  public OptionalInt index() {
    return index == NO_INDEX ? OptionalInt.empty() : OptionalInt.of(index);
  }

  /**
   * Calculates the absolute form of a path.
   *
   * @param path the path to resolve. A path that does not start with {@code /} starts at the path
   *     of this context.
   * @return the absolute path
   */
  public String resolvePath(String path) {
    return DataModel.resolvePath(path, this.path);
  }

  /**
   * Makes a context for a position below this context. The new context has the list position of
   * this context.
   *
   * @param relativePath the path of the position. A path that does not start with {@code /} starts
   *     at the path of this context.
   * @return the new context
   */
  public DataContext nested(String relativePath) {
    return new DataContext(surface, resolvePath(relativePath), index, userActivated, callCatalog);
  }

  /**
   * Makes a context for one item of a list that a template shows.
   *
   * @param relativePath the path of the item. A path that does not start with {@code /} starts at
   *     the path of this context.
   * @param index the position of the item in the list, where the first item is 0
   * @return the new context
   * @throws IllegalArgumentException if the position is negative
   */
  public DataContext nested(String relativePath, int index) {
    if (index < 0) {
      throw new IllegalArgumentException("The position of a list item must not be negative.");
    }
    return new DataContext(surface, resolvePath(relativePath), index, userActivated, callCatalog);
  }

  /**
   * Makes a context for work that is the result of a user gesture, for example a click.
   *
   * <p>Only such a context can call a function that requires user activation.
   *
   * @return the new context
   */
  public DataContext withUserActivation() {
    return new DataContext(surface, path, index, true, callCatalog);
  }

  /**
   * Writes a value in the data of the surface.
   *
   * @param path the path to write. A path that does not start with {@code /} starts at the path of
   *     this context.
   * @param value the JSON value to write, or null to remove the value
   * @throws A2uiDataError if the data model cannot write the value
   */
  public void set(String path, Object value) {
    surface.dataModel().set(resolvePath(path), value);
  }

  /**
   * Resolves a dynamic value as a message writes it.
   *
   * <ul>
   *   <li>A binding <code>{"@path": ...}</code> becomes the data at the path.
   *   <li>A call <code>{"@call": ...}</code> becomes the result of the function. The function runs
   *       in the catalog that the call names. If the call names none, the function runs in the
   *       default catalog of the surface.
   *   <li>The context resolves the bindings and the calls in each other object and in each list. In
   *       such an object, the key {@code @@name} becomes {@code @name}.
   *   <li>Each other value stays as it is.
   * </ul>
   *
   * <p>Before a function runs, the context reads each argument as a boolean if the schema of the
   * function gives it the type {@code DynamicBoolean}. See {@link Coercion#toBoolean(Object)}.
   *
   * @param value the value to resolve, or null
   * @return the resolved value. Maps and lists are read-only.
   * @throws A2uiExpressionError if a call names a function that the catalog does not have, that has
   *     no code, that only the agent can call, or that requires a user gesture when there is none,
   *     or if a function fails
   * @throws A2uiCatalogError if the surface cannot use the catalog that a call names, or if a call
   *     names no catalog and the surface has no default catalog
   * @throws A2uiDataError if the path of a binding is not correct
   */
  public Object resolveDynamicValue(Object value) {
    return switch (value) {
      case Map<?, ?> object -> {
        if (object.get("@path") instanceof String binding) {
          yield surface.dataModel().get(resolvePath(binding));
        }
        if (object.get("@call") instanceof String name) {
          yield call(name, object.get("catalogId"), null, () -> resolveArguments(object));
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        object.forEach((key, item) -> {
          String name = String.valueOf(key);
          // A literal object writes `@@name` for a key that starts with `@`.
          resolved.put(name.startsWith("@@") ? name.substring(1) : name, resolveDynamicValue(item));
        });
        yield Collections.unmodifiableMap(resolved);
      }
      case List<?> list -> {
        List<Object> resolved = new ArrayList<>(list.size());
        list.forEach(item -> resolved.add(resolveDynamicValue(item)));
        yield Collections.unmodifiableList(resolved);
      }
      case null, default -> value;
    };
  }

  /**
   * Evaluates one part of a parsed <code>${...}</code> template.
   *
   * <p>A template cannot name a catalog for a function call. Thus the call runs in the catalog of
   * the function that evaluates the template. This is also the rule for a call in the arguments of
   * such a call. If no function runs in this context, the call runs in the default catalog of the
   * surface.
   *
   * @param expression the part to evaluate
   * @return the value of the part
   * @throws A2uiExpressionError if a call names a function that the catalog does not have, that has
   *     no code, that only the agent can call, or that requires a user gesture when there is none,
   *     or if a function fails
   * @throws A2uiCatalogError if no function runs in this context and the surface has no default
   *     catalog
   * @throws A2uiDataError if a data path is not correct
   */
  public Object evaluate(Expression expression) {
    return switch (Objects.requireNonNull(expression, "expression")) {
      case Expression.Literal literal -> literal.value();
      case Expression.Path binding -> surface.dataModel().get(resolvePath(binding.path()));
      case Expression.Call call ->
          call(
              call.name(),
              null,
              callCatalog,
              () -> {
                Map<String, Object> arguments = new LinkedHashMap<>();
                call.args().forEach((name, argument) -> arguments.put(name, evaluate(argument)));
                return arguments;
              });
    };
  }

  private Map<String, Object> resolveArguments(Map<?, ?> call) {
    Map<String, Object> arguments = new LinkedHashMap<>();
    if (call.get("args") instanceof Map<?, ?> written) {
      written.forEach(
          (name, argument) -> arguments.put(String.valueOf(name), resolveDynamicValue(argument)));
    }
    return arguments;
  }

  /**
   * Runs a function.
   *
   * @param name the name of the function
   * @param catalogId the {@code catalogId} that the call names, or null if it names none
   * @param inherited the catalog for a call that names none, or null to use the default catalog of
   *     the surface
   * @param arguments resolves the arguments. The context calls it only if the function can run.
   */
  private Object call(
      String name,
      Object catalogId,
      Catalog<?, ?> inherited,
      Supplier<Map<String, Object>> arguments) {
    if (name.startsWith("@")) {
      // A system function is part of the protocol, not of a catalog.
      if (catalogId != null) {
        throw new A2uiExpressionError("System function '" + name + "' must not name a catalogId.");
      }
      return systemCall(name, arguments);
    }

    String subject = "Function call '" + name + "'";
    Catalog<?, ?> catalog;
    if (catalogId instanceof String id) {
      catalog = surface.resolveCatalog(id, subject);
    } else {
      catalog = inherited != null ? inherited : surface.resolveCatalog(null, subject);
    }
    FunctionApi function = catalog.functions().get(name);
    if (function == null) {
      throw new A2uiExpressionError(
          "Unknown function '" + name + "' in catalog '" + catalog.id() + "'.");
    }
    if (function.allowedCallers() == AllowedCallers.AGENT_ONLY) {
      throw new A2uiExpressionError(
          "Function '" + name + "' is agentOnly and cannot be called by renderer code.");
    }
    if (function.requiresUserActivation() && !userActivated) {
      throw new A2uiExpressionError(
          "Function '"
              + name
              + "' requires user activation, and this call is not the result of a user gesture.");
    }
    if (!(function instanceof FunctionImplementation implementation)) {
      throw new A2uiExpressionError(
          "Function '"
              + name
              + "' of catalog '"
              + catalog.id()
              + "' has no implementation in this renderer.");
    }
    return implementation.execute(
        coerceArguments(function, arguments.get()),
        new DataContext(surface, path, index, userActivated, catalog));
  }

  /** Runs a function that the protocol defines. Such a function is in no catalog. */
  private Object systemCall(String name, Supplier<Map<String, Object>> arguments) {
    if (!name.equals(INDEX_FUNCTION)) {
      throw new A2uiExpressionError("Unknown system function '" + name + "'.");
    }
    if (index == NO_INDEX) {
      throw new A2uiExpressionError(
          "The @index function can only be evaluated inside a collection template iteration"
              + " scope.");
    }
    Object written = arguments.get().get("offset");
    if (written == null) {
      return (long) index;
    }
    OptionalDouble offset = Coercion.parseNumber(written);
    if (offset.isEmpty()) {
      throw new A2uiExpressionError("The offset of @index must be a number.");
    }
    return index + (long) offset.getAsDouble();
  }

  /**
   * Reads each argument as a boolean if the schema of the function gives it the type {@code
   * DynamicBoolean}. Each other argument stays as it is.
   */
  private static Map<String, Object> coerceArguments(
      FunctionApi function, Map<String, Object> arguments) {
    Map<String, Object> properties =
        function
            .argumentSchema()
            .map(schema -> Json.asObject(schema.get("properties")))
            .orElse(null);
    if (properties == null) {
      return Collections.unmodifiableMap(arguments);
    }
    Map<String, Object> coerced = new LinkedHashMap<>();
    arguments.forEach((name, value) -> coerced.put(name, coerce(properties.get(name), value)));
    return Collections.unmodifiableMap(coerced);
  }

  private static Object coerce(Object schema, Object value) {
    if (!(schema instanceof Map<?, ?> rules)) {
      return value;
    }
    if (rules.get("$ref") instanceof String reference
        && reference.substring(reference.lastIndexOf('/') + 1).equals(DYNAMIC_BOOLEAN)) {
      return Coercion.toBoolean(value);
    }
    if (rules.get("items") instanceof Map<?, ?> items && value instanceof List<?> list) {
      List<Object> coerced = new ArrayList<>(list.size());
      list.forEach(item -> coerced.add(coerce(items, item)));
      return Collections.unmodifiableList(coerced);
    }
    if (rules.get("properties") instanceof Map<?, ?> properties
        && value instanceof Map<?, ?> object) {
      Map<Object, Object> coerced = new LinkedHashMap<>();
      object.forEach((key, item) -> coerced.put(key, coerce(properties.get(key), item)));
      return Collections.unmodifiableMap(coerced);
    }
    return value;
  }

  @Override
  public String toString() {
    return "DataContext(" + surface.id() + ", " + path + ")";
  }
}
