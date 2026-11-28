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
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.exceptions.A2uiRpcError;
import com.google.a2ui.core.exceptions.RpcErrorCode;
import com.google.a2ui.core.expressions.Expression;
import com.google.a2ui.core.internal.AgentCalls;
import com.google.a2ui.core.internal.AgentRequests;
import com.google.a2ui.core.internal.AgentRequests.Outcome;
import com.google.a2ui.core.internal.ErrorFields;
import com.google.a2ui.core.internal.Json;
import com.google.a2ui.core.internal.Problem;
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
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The scope in which the core evaluates a data binding or a function call: a surface, a position in
 * the data of the surface, and the permissions of the caller.
 *
 * <p>Each call that such a context evaluates is a call of the renderer: a property binding, a check
 * or an action. Thus the context refuses a function that only the agent can call.
 *
 * <p>The core also makes a context for a {@code callRendererFunction} message. Each call that this
 * context evaluates is a call of the agent. This is also the rule for a call in an argument and for
 * a call in a template. This context permits a function that only the agent can call. It refuses a
 * function that only the renderer can call, and a function that requires a user gesture when there
 * is none. Such a refusal is an {@link A2uiRpcError} with the code {@link
 * RpcErrorCode#INVALID_FUNCTION_CALL}. An application cannot make this context.
 *
 * <p>A function that the catalog of a call does not implement is a function of the agent, if the
 * surface has an agent function caller. The bindings, the checks and the actions of a component
 * wait for the answer of the agent. A context that an application makes cannot wait. Such a context
 * gives the answer that the surface keeps for an identical call. If the surface keeps none, the
 * call fails. The context sends nothing to the agent.
 *
 * <p>A context cannot change. {@link #nested(String)} and {@link #withUserActivation()} make a new
 * context.
 */
public final class DataContext {
  /** The work of a context for a call that only the agent can run. */
  private enum Routing {
    /** Reads the answer that the surface keeps, and fails if there is none. Sends nothing. */
    ANSWERED_ONLY,
    /** Fails as if the surface had no agent function caller. */
    NEVER,
    /** Sends the call, or shares an identical call that waits. The node resolves again later. */
    BINDING,
    /** Sends the call as a request of its own each time, and continues when the agent answers. */
    ACTION,
  }

  private static final String EXPRESSION_ERROR = "EXPRESSION_ERROR";

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

  /** True if each call that this context evaluates is a call of the agent. */
  private final boolean agentCaller;

  /**
   * The catalog of the function that runs in this context, or null if no function runs in it. A
   * call that a template of that function writes runs in this catalog.
   */
  private final Catalog<?, ?> callCatalog;

  /**
   * The set that gets the absolute path of each read through this context, or null if the context
   * records nothing. Each context that this context makes uses the same set.
   */
  private final Set<String> reads;

  private final Routing routing;

  /** The memory of the node of a binding, or null if the routing is not {@link Routing#BINDING}. */
  private final AgentMemo memo;

  /**
   * Receives a failure that does not stop the evaluation, or null to report such a failure to the
   * surface.
   */
  private final Consumer<A2uiError> report;

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
        null,
        null,
        false,
        Routing.ANSWERED_ONLY,
        null,
        null);
  }

  private DataContext(
      SurfaceModel<?> surface,
      String path,
      int index,
      boolean userActivated,
      Catalog<?, ?> callCatalog,
      Set<String> reads,
      boolean agentCaller,
      Routing routing,
      AgentMemo memo,
      Consumer<A2uiError> report) {
    this.surface = surface;
    this.path = path;
    this.index = index;
    this.userActivated = userActivated;
    this.callCatalog = callCatalog;
    this.reads = reads;
    this.agentCaller = agentCaller;
    this.routing = routing;
    this.memo = memo;
    this.report = report;
  }

  /** Makes a copy of this context with a different position or a different user gesture. */
  private DataContext copy(String newPath, int newIndex, boolean newUserActivated) {
    return new DataContext(
        surface,
        newPath,
        newIndex,
        newUserActivated,
        callCatalog,
        reads,
        agentCaller,
        routing,
        memo,
        report);
  }

  static {
    // The RPC layer is in a different package. It makes the context of an agent call through
    // this function, which is not part of the API. A call of the agent does not go back to the
    // agent.
    AgentCalls.register((context, catalog, userActivated) ->
        new DataContext(
            context.surface,
            context.path,
            context.index,
            userActivated,
            catalog,
            context.reads,
            true,
            Routing.NEVER,
            null,
            context.report));
  }

  /**
   * Makes a context at the scope of a resolved node. The context records each path that code reads
   * through it and through each context that it makes.
   *
   * @param surface the surface whose data and catalogs the context uses
   * @param path the absolute data path of the scope
   * @param index the position of the list item of the scope, or an empty value outside a list
   *     template
   * @param reads the set that gets the absolute path of each read, or null to record nothing
   * @param memo the memory of the node. A call to the agent in this context is the call of a
   *     binding.
   * @param report receives a failure that does not stop the evaluation
   * @return the new context
   */
  static DataContext tracking(
      SurfaceModel<?> surface,
      String path,
      OptionalInt index,
      Set<String> reads,
      AgentMemo memo,
      Consumer<A2uiError> report) {
    return new DataContext(
        surface,
        path,
        index.orElse(NO_INDEX),
        false,
        null,
        reads,
        false,
        Routing.BINDING,
        Objects.requireNonNull(memo, "memo"),
        report);
  }

  /**
   * Makes a context for an action at the scope of a node. Each call to the agent in this context is
   * a request of its own.
   *
   * @param surface the surface whose data and catalogs the context uses
   * @param path the absolute data path of the scope
   * @param index the position of the list item of the scope, or an empty value outside a list
   *     template
   * @param userActivated true if the action is the result of a user gesture
   * @return the new context
   */
  static DataContext forAction(
      SurfaceModel<?> surface, String path, OptionalInt index, boolean userActivated) {
    return new DataContext(
        surface,
        path,
        index.orElse(NO_INDEX),
        userActivated,
        null,
        null,
        false,
        Routing.ACTION,
        null,
        null);
  }

  /**
   * Reads a value from the data of the surface.
   *
   * <p>A function must read data through its context and not directly from the data model. The core
   * records each path that a binding reads through a context. Then the core evaluates the binding
   * again when the data at one of these paths changes.
   *
   * @param path the path to read. A path that does not start with {@code /} starts at the path of
   *     this context.
   * @return the value, or null if the path has no value. Maps and lists are read-only.
   * @throws A2uiDataError if the path is not correct
   */
  public Object get(String path) {
    String absolute = resolvePath(path);
    Object value = surface.dataModel().get(absolute);
    // The context records only a path that the data model accepts.
    if (reads != null) {
      reads.add(absolute);
    }
    return value;
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
    return copy(resolvePath(relativePath), index, userActivated);
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
    return copy(resolvePath(relativePath), index, userActivated);
  }

  /**
   * Makes a context for work that is the result of a user gesture, for example a click.
   *
   * <p>Only such a context can call a function that requires user activation.
   *
   * @return the new context
   */
  public DataContext withUserActivation() {
    return copy(path, index, true);
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
   *       such an object, the key {@code @@name} becomes {@code @name}. A different key that starts
   *       with {@code @} is not correct.
   *   <li>Each other value stays as it is.
   * </ul>
   *
   * <p>This method does not send a call to the agent. If the catalog does not implement a function
   * and the surface has an agent function caller, the result is the answer that the surface keeps
   * for an identical call. If the surface keeps none, the call fails.
   *
   * <p>Before a function runs, the context reads each argument as a boolean if the schema of the
   * function gives it the type {@code DynamicBoolean}. See {@link Coercion#toBoolean(Object)}. The
   * context refuses a call that omits an argument that the schema of the function requires. Then
   * the function does not run.
   *
   * @param value the value to resolve, or null
   * @return the resolved value. Maps and lists are read-only.
   * @throws A2uiExpressionError if a call names a function that the catalog does not have, that has
   *     no code, that only the agent can call, or that requires a user gesture when there is none,
   *     if the code of a function throws an exception that is not an {@code A2uiError}, or if an
   *     object that is not a binding and not a call has a key that starts with one {@code @}
   * @throws A2uiRpcError if this context evaluates the calls of the agent, and a call names a
   *     function that only the renderer can call, or that requires a user gesture when there is
   *     none
   * @throws A2uiCatalogError if the surface cannot use the catalog that a call names, or if a call
   *     names no catalog and the surface has no default catalog
   * @throws A2uiDataError if the path of a binding is not correct
   */
  public Object resolveDynamicValue(Object value) {
    Object resolved = resolvePending(value);
    if (resolved instanceof Later) {
      // Only the context of a node or of an action can wait, and the core does not give such a
      // context to other code.
      throw new IllegalStateException("This context cannot wait for the agent.");
    }
    return resolved;
  }

  /**
   * Resolves a dynamic value as {@link #resolveDynamicValue(Object)} does. But the result is a
   * {@link Later} if the value waits for the agent.
   */
  Object resolvePending(Object value) {
    return switch (value) {
      case Map<?, ?> object -> {
        if (object.get("@path") instanceof String binding) {
          yield get(binding);
        }
        if (object.get("@call") instanceof String name) {
          yield call(name, object.get("catalogId"), null, false, () -> resolveArguments(object));
        }
        List<String> names = new ArrayList<>(object.size());
        List<Object> members = new ArrayList<>(object.size());
        object.forEach((key, item) -> {
          String name = String.valueOf(key);
          // A literal object writes `@@name` for a key that starts with `@`.
          boolean escaped = name.startsWith("@@");
          if (name.startsWith("@") && !escaped) {
            throw unknownReservedKey(name);
          }
          names.add(escaped ? name.substring(1) : name);
          members.add(resolvePending(item));
        });
        yield gather(members, values -> Collections.unmodifiableMap(zip(names, values)));
      }
      case List<?> list -> {
        List<Object> items = new ArrayList<>(list.size());
        list.forEach(item -> items.add(resolvePending(item)));
        yield gather(items, values -> Collections.unmodifiableList(new ArrayList<>(values)));
      }
      case null, default -> value;
    };
  }

  private static Map<String, Object> zip(List<String> names, List<Object> values) {
    Map<String, Object> object = new LinkedHashMap<>();
    for (int position = 0; position < names.size(); position++) {
      object.put(names.get(position), values.get(position));
    }
    return object;
  }

  /**
   * Makes a result from parts when no part waits for the agent. If no part waits, the result is
   * there immediately. If a part waits, the result is a {@link Later}.
   *
   * <p>The parts are siblings. Thus each call of the parts goes to the agent before the agent
   * answers one of them.
   *
   * @param parts the resolved parts. A part that waits is a {@link Later}.
   * @param build makes the result from the values of the parts
   */
  private static Object gather(List<Object> parts, Function<List<Object>, Object> build) {
    List<Later> waiting = new ArrayList<>();
    for (Object part : parts) {
      if (part == Later.PENDING) {
        // The resolver resolves the binding again from the start when the agent answers.
        return Later.PENDING;
      }
      if (part instanceof Later later) {
        waiting.add(later);
      }
    }
    if (waiting.isEmpty()) {
      return build.apply(parts);
    }
    Later result = new Later();
    int[] remaining = {waiting.size()};
    for (Later part : waiting) {
      part.then(end -> {
        // The first failure ends the result. The other parts change nothing later.
        if (result.isSettled()) {
          return;
        }
        switch (end.state()) {
          case FAILED -> result.fail(end.error());
          case CANCELLED -> result.cancel();
          default -> {
            if (--remaining[0] == 0) {
              List<Object> values = new ArrayList<>(parts.size());
              parts.forEach(item -> values.add(item instanceof Later later ? later.value() : item));
              try {
                result.follow(build.apply(values));
              } catch (A2uiError error) {
                result.fail(error);
              }
            }
          }
        }
      });
    }
    return result;
  }

  /**
   * Evaluates one part of a parsed <code>${...}</code> template.
   *
   * <p>A template cannot name a catalog for a function call. Thus the call runs in the catalog of
   * the function that evaluates the template. This is also the rule for a call in the arguments of
   * such a call. If no function runs in this context, the call runs in the default catalog of the
   * surface.
   *
   * <p>A template part does not wait for the agent. If the surface has an agent function caller and
   * the catalog does not implement a function of the part, the context reports an error to the
   * surface and the value of the call is null. Thus the part shows nothing. The context sends
   * nothing to the agent.
   *
   * @param expression the part to evaluate
   * @return the value of the part
   * @throws A2uiExpressionError if a call names a function that the catalog does not have, that has
   *     no code, that only the agent can call, or that requires a user gesture when there is none,
   *     or if the code of a function throws an exception that is not an {@code A2uiError}
   * @throws A2uiRpcError if this context evaluates the calls of the agent, and a call names a
   *     function that only the renderer can call, or that requires a user gesture when there is
   *     none
   * @throws A2uiCatalogError if no function runs in this context and the surface has no default
   *     catalog
   * @throws A2uiDataError if a data path is not correct
   */
  public Object evaluate(Expression expression) {
    return switch (Objects.requireNonNull(expression, "expression")) {
      case Expression.Literal literal -> literal.value();
      case Expression.Path binding -> get(binding.path());
      case Expression.Call call ->
          call(
              call.name(),
              null,
              callCatalog,
              true,
              () -> {
                Map<String, Object> arguments = new LinkedHashMap<>();
                call.args().forEach((name, argument) -> arguments.put(name, evaluate(argument)));
                return arguments;
              });
    };
  }

  /**
   * Evaluates the parts of a parsed <code>${...}</code> template, in their sequence. See {@link
   * #evaluate(Expression)}.
   *
   * <p>A function that evaluates a template uses this method. If this context evaluates the calls
   * of the agent, the method first examines each function call of each part. If the agent cannot
   * call one of these functions, the method throws the error before a part runs. Thus a part that
   * fails for a different cause cannot hide a call that the agent must not make.
   *
   * @param parts the parts to evaluate, as {@code ExpressionParser.parseTemplate} gives them
   * @return the value of each part, in the sequence of the parts. The list is read-only.
   * @throws A2uiExpressionError if a call names a function that the catalog does not have, that has
   *     no code, that only the agent can call, or that requires a user gesture when there is none,
   *     or if the code of a function throws an exception that is not an {@code A2uiError}
   * @throws A2uiRpcError if this context evaluates the calls of the agent, and a call names a
   *     function that only the renderer can call, or that requires a user gesture when there is
   *     none
   * @throws A2uiCatalogError if no function runs in this context and the surface has no default
   *     catalog
   * @throws A2uiDataError if a data path is not correct
   */
  public List<Object> evaluateTemplate(List<? extends Expression> parts) {
    Objects.requireNonNull(parts, "parts");
    if (agentCaller) {
      parts.forEach(this::examineForAgent);
    }
    List<Object> values = new ArrayList<>(parts.size());
    for (Expression part : parts) {
      values.add(evaluate(part));
    }
    return Collections.unmodifiableList(values);
  }

  /**
   * Examines each function call of a template part against the caller boundary of the agent. The
   * method runs no function. It does not examine a call whose function it cannot find, because the
   * evaluation reports that problem.
   */
  private void examineForAgent(Expression expression) {
    if (!(expression instanceof Expression.Call call)) {
      return;
    }
    if (!call.name().startsWith("@")) {
      Catalog<?, ?> catalog =
          callCatalog != null ? callCatalog : surface.defaultCatalog().orElse(null);
      FunctionApi function = catalog == null ? null : catalog.functions().get(call.name());
      if (function != null) {
        AgentCalls.examine(function, userActivated);
      }
    }
    call.args().values().forEach(this::examineForAgent);
  }

  /** Makes the error of a key of a literal object that starts with one {@code @}. */
  private static A2uiExpressionError unknownReservedKey(String name) {
    return new A2uiExpressionError(
        "The key '"
            + name
            + "' is not a binding that this renderer knows. Write '@"
            + name
            + "' for a literal key.");
  }

  private Map<String, Object> resolveArguments(Map<?, ?> call) {
    Map<String, Object> arguments = new LinkedHashMap<>();
    if (call.get("args") instanceof Map<?, ?> written) {
      written.forEach(
          (name, argument) -> arguments.put(String.valueOf(name), resolvePending(argument)));
    }
    return arguments;
  }

  /**
   * Runs a function, or gives the call to the agent.
   *
   * @param name the name of the function
   * @param catalogId the {@code catalogId} that the call names, or null if it names none
   * @param inherited the catalog for a call that names none, or null to use the default catalog of
   *     the surface
   * @param template true if a template part makes the call. Such a call cannot wait for the agent.
   * @param arguments resolves the arguments. The context calls it only if the function can run. An
   *     argument that waits for the agent is a {@link Later}.
   * @return the result, or a {@link Later} if the result waits for the agent
   */
  private Object call(
      String name,
      Object catalogId,
      Catalog<?, ?> inherited,
      boolean template,
      Supplier<Map<String, Object>> arguments) {
    if (name.startsWith("@")) {
      // A system function is part of the protocol, not of a catalog.
      if (catalogId != null) {
        throw new A2uiExpressionError("System function '" + name + "' must not name a catalogId.");
      }
      // The context resolves no argument of a call that cannot run.
      examineSystemCall(name);
      return afterArguments(arguments, resolved -> systemCall(name, resolved));
    }

    String subject = "Function call '" + name + "'";
    Catalog<?, ?> catalog;
    if (catalogId instanceof String id) {
      // A function can name its own catalog. The agent can call a function of a catalog that the
      // processor got after the creation of the surface. Thus the surface possibly does not have
      // that catalog.
      catalog =
          callCatalog != null && callCatalog.id().equals(id)
              ? callCatalog
              : surface.resolveCatalog(id, subject);
    } else {
      catalog = inherited != null ? inherited : surface.resolveCatalog(null, subject);
    }
    FunctionApi function = catalog.functions().get(name);
    FunctionImplementation local =
        function instanceof FunctionImplementation implementation ? implementation : null;
    // A function that this renderer cannot run is a function of the agent, if the surface can
    // send a call to the agent.
    boolean routes =
        local == null && routing != Routing.NEVER && AgentRequests.of(surface).caller() != null;
    if (function == null && !routes) {
      throw new A2uiExpressionError(
          "Unknown function '" + name + "' in catalog '" + catalog.id() + "'.");
    }
    if (function != null) {
      if (agentCaller) {
        // The caller boundary of the agent. It also examines the user gesture.
        AgentCalls.examine(function, userActivated);
      } else if (function.allowedCallers() == AllowedCallers.AGENT_ONLY) {
        throw new A2uiExpressionError(
            "Function '" + name + "' is agentOnly and cannot be called by renderer code.");
      }
      if (function.requiresUserActivation() && !userActivated) {
        throw new A2uiExpressionError(
            "Function '"
                + name
                + "' requires user activation, and this call is not the result of a user gesture.");
      }
    }
    if (local == null) {
      if (!routes) {
        throw new A2uiExpressionError(
            "Function '"
                + name
                + "' of catalog '"
                + catalog.id()
                + "' has no implementation in this renderer.");
      }
      if (template) {
        // A template part cannot wait. The part shows nothing, and the other parts continue.
        reportContinued(
            new A2uiExpressionError(
                "Function '"
                    + name
                    + "' of catalog '"
                    + catalog.id()
                    + "' is not implemented by this renderer. A template part cannot wait for the"
                    + " agent. Thus the renderer did not send the call."));
        return null;
      }
      return afterArguments(arguments, resolved -> send(name, catalog, function, resolved));
    }
    return afterArguments(arguments, resolved -> run(local, catalog, resolved));
  }

  /**
   * Resolves the arguments of a call and then does the call. If an argument waits for the agent,
   * the call waits for that argument.
   */
  private static Object afterArguments(
      Supplier<Map<String, Object>> arguments, Function<Map<String, Object>, Object> call) {
    Map<String, Object> written = arguments.get();
    List<String> names = List.copyOf(written.keySet());
    return gather(new ArrayList<>(written.values()), values -> call.apply(zip(names, values)));
  }

  /** Reports a failure that does not stop the evaluation. */
  private void reportContinued(A2uiError error) {
    if (report != null) {
      report.accept(error);
    } else {
      surface.dispatchError(ErrorFields.errorCode(error, EXPRESSION_ERROR));
    }
  }

  /** Runs a function that the catalog implements. */
  private Object run(
      FunctionImplementation function, Catalog<?, ?> catalog, Map<String, Object> arguments) {
    Map<String, Object> resolved = coerceArguments(function, arguments);
    requireArguments(function, resolved);
    // A function cannot wait for the agent.
    DataContext scope =
        new DataContext(
            surface,
            path,
            index,
            userActivated,
            catalog,
            reads,
            agentCaller,
            routing == Routing.NEVER ? Routing.NEVER : Routing.ANSWERED_ONLY,
            null,
            report);
    try {
      return function.execute(resolved, scope);
    } catch (A2uiError error) {
      // The function ran and failed. This is not a call that the context refused.
      throw ErrorFields.raisedByFunction(error);
    } catch (RuntimeException error) {
      // Each exception of the code of a function becomes one type of failure for the caller.
      throw ErrorFields.raisedByFunction(
          new A2uiExpressionError(
              "Function '" + function.name() + "' failed: " + error.getMessage(), error));
    }
  }

  /**
   * Gives a call that this renderer cannot run to the agent, as the routing of this context tells.
   *
   * @param declared the declaration of the function in the catalog, or null if the catalog does not
   *     declare the function
   * @return the answer, or a {@link Later} if the answer is not there
   */
  private Object send(
      String name, Catalog<?, ?> catalog, FunctionApi declared, Map<String, Object> resolved) {
    AgentRequests requests = AgentRequests.of(surface);
    Map<String, Object> arguments = resolved;
    if (declared != null) {
      // The renderer examines a declared function as it examines a local function. Thus the
      // agent gets the arguments that an implementation here gets, with the defaults.
      arguments =
          withDefaults(declared.argumentSchema().orElse(null), coerceArguments(declared, resolved));
    }
    Map<String, Object> call = new LinkedHashMap<>();
    call.put("@call", name);
    call.put("catalogId", catalog.id());
    call.put("args", arguments);
    Map<String, Object> frozen;
    try {
      frozen = Json.freezeObject(call);
    } catch (IllegalArgumentException error) {
      throw notJson(name, error);
    }
    if (declared != null) {
      List<Problem> problems;
      try {
        Map<String, Object> examined = Json.asObject(frozen.get("args"));
        problems =
            requests
                .checksFor(catalog)
                .map(checks -> checks.argumentProblems(name, examined))
                .orElse(List.of());
      } catch (A2uiCatalogError error) {
        throw new A2uiExpressionError(
            "Validation failed for function '" + name + "': " + error.getMessage(), error);
      }
      if (!problems.isEmpty()) {
        throw new A2uiExpressionError(
            "Validation failed for function '"
                + name
                + "': "
                + problems.stream().map(Problem::toString).collect(Collectors.joining("; ")));
      }
    }
    String key = AgentRequests.keyOf(frozen);
    if (key == null) {
      throw notJson(name, null);
    }
    return switch (routing) {
      case BINDING -> memo.valueOf(requests, key, frozen);
      case ACTION -> {
        Later result = new Later();
        requests.fresh(
            frozen,
            outcome -> {
              switch (outcome) {
                case Outcome.Value answer -> result.succeed(answer.value());
                case Outcome.Failed failed -> result.fail(failed.error());
                case Outcome.Cancelled _ -> result.cancel();
              }
            });
        yield result;
      }
      case ANSWERED_ONLY, NEVER -> {
        Outcome.Value answer = requests.answer(key);
        if (answer == null) {
          throw new A2uiExpressionError(
              "Function '"
                  + name
                  + "' is not implemented by this renderer, and this evaluation cannot wait for its"
                  + " result. Thus the renderer did not send the call to the agent.");
        }
        yield answer.value();
      }
    };
  }

  private static A2uiExpressionError notJson(String name, Throwable cause) {
    return new A2uiExpressionError(
        "The arguments of function '"
            + name
            + "' are not JSON. Thus the renderer did not send the call to the agent.",
        cause);
  }

  /**
   * Adds the defaults of a schema to an object. The method adds the default of each property that
   * the object omits. It also does this in each object that is a property.
   */
  private static Map<String, Object> withDefaults(Map<?, ?> schema, Map<String, Object> value) {
    if (schema == null || !(schema.get("properties") instanceof Map<?, ?> properties)) {
      return value;
    }
    Map<String, Object> filled = new LinkedHashMap<>(value);
    properties.forEach((key, rules) -> {
      if (!(key instanceof String name) || !(rules instanceof Map<?, ?> property)) {
        return;
      }
      if (!filled.containsKey(name)) {
        if (property.containsKey("default")) {
          filled.put(name, property.get("default"));
        }
      } else if (filled.get(name) instanceof Map<?, ?> present) {
        filled.put(name, withDefaults(property, Json.asObject(present)));
      }
    });
    return filled;
  }

  /** Makes sure that a function that the protocol defines can run in this context. */
  private void examineSystemCall(String name) {
    if (!name.equals(INDEX_FUNCTION)) {
      throw new A2uiExpressionError("Unknown system function '" + name + "'.");
    }
    if (index == NO_INDEX) {
      throw new A2uiExpressionError(
          "The @index function can only be evaluated inside a collection template iteration"
              + " scope.");
    }
  }

  /** Runs a function that the protocol defines. Such a function is in no catalog. */
  private Object systemCall(String name, Map<String, Object> arguments) {
    examineSystemCall(name);
    Object written = arguments.get("offset");
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
   * Makes sure that a call gives each argument that the schema of the function requires. An
   * argument that has the value null is given.
   *
   * @throws A2uiExpressionError if the call omits a required argument
   */
  private static void requireArguments(FunctionApi function, Map<String, Object> arguments) {
    Object required = function.argumentSchema().map(schema -> schema.get("required")).orElse(null);
    if (!(required instanceof List<?> names)) {
      return;
    }
    List<String> missing = new ArrayList<>();
    for (Object name : names) {
      if (!arguments.containsKey(name)) {
        missing.add("'" + name + "'");
      }
    }
    if (!missing.isEmpty()) {
      throw new A2uiExpressionError(
          "Validation failed for function '"
              + function.name()
              + "': missing "
              + String.join(", ", missing)
              + ".");
    }
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
