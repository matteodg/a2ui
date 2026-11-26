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

import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.resolution.DataContext;
import java.util.Map;
import java.util.Objects;

/** A catalog function that a renderer can run: a {@link FunctionApi} and its code. */
public class FunctionImplementation extends FunctionApi {
  private final FunctionBody body;

  /**
   * Makes a function from a definition and its code.
   *
   * @param name the name of the function as a call writes it
   * @param schema the definition of the function as a catalog document writes it
   * @param body the code of the function
   * @throws A2uiCatalogError if the schema is not JSON, or if its {@code allowedCallers} or its
   *     {@code requiresUserActivation} has a value that is not correct
   */
  public FunctionImplementation(String name, Map<String, ?> schema, FunctionBody body) {
    super(name, schema);
    this.body = Objects.requireNonNull(body, "body");
  }

  /**
   * Makes a function from a signature and its code.
   *
   * @param api the signature of the function
   * @param body the code of the function
   */
  public FunctionImplementation(FunctionApi api, FunctionBody body) {
    this(api.name(), api.schema(), body);
  }

  /**
   * Runs the function.
   *
   * <p>This method runs the code only. The {@link DataContext} that evaluates a call examines the
   * permitted callers and the user gesture before it calls this method.
   *
   * @param args the arguments of the call by name. Each argument is a resolved, plain JSON value.
   * @param context the scope of the call
   * @return the result as a plain JSON value, or null for a function that returns nothing
   */
  public final Object execute(Map<String, Object> args, DataContext context) {
    return body.execute(
        Objects.requireNonNull(args, "args"), Objects.requireNonNull(context, "context"));
  }

  @Override
  public String toString() {
    return "FunctionImplementation(" + name() + ")";
  }
}
