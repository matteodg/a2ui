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

package com.google.a2ui.core.basic_catalog.v1_0;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.internal.Json;

/** The basic catalog for protocol v1.0. It has 18 components and 14 functions. */
public final class BasicCatalog {
  /** The ID of the catalog. */
  public static final String ID = "https://a2ui.org/specification/v1_0/catalogs/basic/catalog.json";

  private BasicCatalog() {}

  /**
   * Gets the signatures of the components and of the functions of the catalog.
   *
   * <p>The SDK reads them from the catalog document that the A2UI repository publishes. The result
   * describes the catalog. It cannot show a component or run a function.
   *
   * @return the catalog. Each call returns the same object.
   */
  public static Catalog<ComponentApi, FunctionApi> api() {
    return Api.CATALOG;
  }

  /** Reads the document at the first call of {@link #api()}. */
  private static final class Api {
    static final Catalog<ComponentApi, FunctionApi> CATALOG =
        Catalog.fromJson(Json.objectResource(BasicCatalog.class, "catalog.json"));
  }
}
