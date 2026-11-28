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

import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.common.EventListener;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Supplier;

/** Gives the test code of other packages the hooks of the node resolver that are not public API. */
public final class ResolverHooks {
  private ResolverHooks() {}

  /**
   * Sets the listener that receives each node of a resolver immediately after its creation. Thus a
   * test can count the notifications of a node from its creation.
   *
   * @param <C> the type of the components of the catalogs
   * @param resolver the resolver to observe
   * @param listener the listener to set
   */
  public static <C extends ComponentApi> void onNodeCreated(
      NodeResolver<C> resolver, EventListener<ComponentNode<C>> listener) {
    resolver.onNodeCreated(listener);
  }

  /**
   * Does an action as the action of a node does it. See {@link ActionRunner}.
   *
   * @param surface the surface of the component
   * @param scope the absolute data path at which the action resolves its values
   * @param index the position of the list item of the scope, or an empty value outside a list
   *     template
   * @param userActivated true if the action is the result of a user gesture
   * @param payload the action as a component declares it
   * @param sourceComponentId the ID of the component that the user operated
   * @param metadata the {@code action.metadata} to send with an event, or null
   * @return a supplier of the result of a function call action at the time of the question. The
   *     result is null for an event, for an action that failed, and while the call waits for the
   *     agent.
   */
  public static Supplier<Object> runAction(
      SurfaceModel<?> surface,
      String scope,
      OptionalInt index,
      boolean userActivated,
      Map<String, ?> payload,
      String sourceComponentId,
      A2uiMetadata metadata) {
    Object result =
        ActionRunner.run(
            surface, scope, index, userActivated, payload, sourceComponentId, metadata);
    return () -> result instanceof Later later ? later.value() : result;
  }
}
