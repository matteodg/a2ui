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

import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.common.EventListener;

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
}
