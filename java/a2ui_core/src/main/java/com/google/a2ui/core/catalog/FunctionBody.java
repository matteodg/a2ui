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

package com.google.a2ui.core.catalog;

import com.google.a2ui.core.resolution.DataContext;
import java.util.Map;

/** The code of a catalog function. */
@FunctionalInterface
public interface FunctionBody {
  /**
   * Runs the function.
   *
   * @param args the arguments of the call by name. Each argument is a resolved, plain JSON value.
   * @param context the scope of the call: the surface, the position in its data, and the catalog of
   *     the function
   * @return the result as a plain JSON value, or null for a function that returns nothing
   */
  Object execute(Map<String, Object> args, DataContext context);
}
