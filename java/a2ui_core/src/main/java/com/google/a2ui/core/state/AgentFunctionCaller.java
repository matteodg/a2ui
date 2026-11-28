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

package com.google.a2ui.core.state;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sends a function call of a surface to the agent. A surface uses its caller for a function that
 * the renderer cannot run.
 *
 * <p>A message processor that has an outbound listener sets a caller on each surface that it makes.
 * An application that sends its messages to the agent in a different way can set its own caller.
 * See {@link SurfaceModel#setAgentFunctionCaller(AgentFunctionCaller)}.
 */
@FunctionalInterface
public interface AgentFunctionCaller {
  /**
   * Sends a call to the agent.
   *
   * <p>The surface is not thread-safe. Complete the future on the thread that owns the surface. The
   * surface changes its nodes on the thread that completes the future.
   *
   * @param call the call as a {@code callAgentFunction} message writes it: the {@code @call}, the
   *     {@code catalogId} of the catalog in which the call runs, and the {@code args}. Each
   *     argument is a resolved value. The map is read-only.
   * @return the future of the answer. It completes with the JSON value that the agent returned. It
   *     fails if the agent reports an error or does not answer in time. A future that is cancelled,
   *     or that fails with an {@code A2uiRpcError} of the code {@code CANCELLED} or {@code
   *     DISPOSED}, withdraws the call. The surface reports no error for a withdrawn call.
   */
  CompletableFuture<Object> call(Map<String, Object> call);
}
