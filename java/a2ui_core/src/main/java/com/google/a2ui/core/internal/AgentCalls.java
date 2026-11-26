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
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.exceptions.A2uiRpcError;
import com.google.a2ui.core.exceptions.RpcErrorCode;
import com.google.a2ui.core.resolution.DataContext;
import java.util.Objects;

/**
 * The rules for a function call of the agent, and the data context that applies them.
 *
 * <p>The agent calls a function of the renderer with a {@code callRendererFunction} message. The
 * agent can call a function that permits agent callers. If the function requires a user gesture,
 * the application must tell that a gesture is present. These rules also apply to each call in the
 * arguments and to each call in a template that the function evaluates.
 *
 * <p>Only the core makes the data context of an agent call. The context class gives its factory to
 * this class, because this package is not exported. Thus code outside the module cannot evaluate a
 * call with the permissions of the agent.
 */
public final class AgentCalls {
  /** Makes the data context of an agent call. The context class implements it. */
  @FunctionalInterface
  public interface ContextFactory {
    /**
     * Makes a context that evaluates each call as a call of the agent.
     *
     * @param context the context that gives the surface, the data path and the list position
     * @param catalog the catalog of the function that the agent calls. A call in a template of that
     *     function runs in this catalog.
     * @param userActivated true if a user gesture is present
     * @return the new context
     */
    DataContext forAgent(DataContext context, Catalog<?, ?> catalog, boolean userActivated);
  }

  /** The factory of the context class. The initialization of that class sets it. */
  private static volatile ContextFactory factory;

  private AgentCalls() {}

  /**
   * Sets the factory. The initialization of {@link DataContext} calls this method one time.
   *
   * @param contextFactory the factory of the context class
   * @throws IllegalStateException if a factory is already set
   */
  public static void register(ContextFactory contextFactory) {
    if (factory != null) {
      throw new IllegalStateException("The factory of the agent call context is already set.");
    }
    factory = Objects.requireNonNull(contextFactory, "contextFactory");
  }

  /**
   * Makes a context that evaluates each call as a call of the agent.
   *
   * @param context the context that gives the surface, the data path and the list position
   * @param catalog the catalog of the function that the agent calls
   * @param userActivated true if a user gesture is present
   * @return the new context
   */
  public static DataContext context(
      DataContext context, Catalog<?, ?> catalog, boolean userActivated) {
    // The caller has a context. Thus the context class is initialized and the factory is set.
    return factory.forAgent(
        Objects.requireNonNull(context, "context"),
        Objects.requireNonNull(catalog, "catalog"),
        userActivated);
  }

  /**
   * Makes sure that the agent can call a function.
   *
   * @param function the function that a call names
   * @param userActivated true if a user gesture is present
   * @throws A2uiRpcError with the code {@link RpcErrorCode#INVALID_FUNCTION_CALL} if only the
   *     renderer can call the function, or if the function requires a user gesture and there is
   *     none
   */
  public static void examine(FunctionApi function, boolean userActivated) {
    String name = function.name();
    if (function.allowedCallers() == AllowedCallers.RENDERER_ONLY) {
      throw refused(
          "Function '" + name + "' cannot be called by agent (allowedCallers is rendererOnly).");
    }
    if (function.requiresUserActivation() && !userActivated) {
      throw refused("Function '" + name + "' requires user activation context to execute.");
    }
  }

  /**
   * Makes the error of a call that the renderer refuses to run for the agent.
   *
   * @param message the cause of the refusal
   * @return the error, with the code {@link RpcErrorCode#INVALID_FUNCTION_CALL}
   */
  public static A2uiRpcError refused(String message) {
    return new A2uiRpcError(message, RpcErrorCode.INVALID_FUNCTION_CALL);
  }
}
