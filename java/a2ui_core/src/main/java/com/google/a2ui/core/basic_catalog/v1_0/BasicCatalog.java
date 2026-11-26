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
import com.google.a2ui.core.catalog.FunctionBody;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.internal.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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

  /**
   * Makes the catalog with the code of its functions, for a renderer. The functions write text for
   * the locale {@link Locale#US}. Thus the text is the same on each computer. A call of {@code
   * openUrl} fails. See {@link #withFunctions(Locale, UrlOpener)}.
   *
   * @return the catalog
   */
  public static Catalog<ComponentApi, FunctionImplementation> withFunctions() {
    return withFunctions(Locale.US);
  }

  /**
   * Makes the catalog with the code of its functions, for a renderer. A call of {@code openUrl}
   * fails. See {@link #withFunctions(Locale, UrlOpener)}.
   *
   * @param locale the locale of the text that {@code formatNumber}, {@code formatCurrency}, {@code
   *     formatDate} and {@code pluralize} write
   * @return the catalog
   */
  public static Catalog<ComponentApi, FunctionImplementation> withFunctions(Locale locale) {
    return implement(new BasicFunctions(Objects.requireNonNull(locale, "locale"), null).bodies());
  }

  /**
   * Makes the catalog with the code of its functions, for a renderer.
   *
   * <p>The components and the document of the result are those of {@link #api()}. A function that
   * this SDK does not implement fails when a call runs it.
   *
   * @param locale the locale of the text that {@code formatNumber}, {@code formatCurrency}, {@code
   *     formatDate} and {@code pluralize} write
   * @param urlOpener the object that opens a URL for {@code openUrl}
   * @return the catalog
   */
  public static Catalog<ComponentApi, FunctionImplementation> withFunctions(
      Locale locale, UrlOpener urlOpener) {
    return implement(
        new BasicFunctions(
                Objects.requireNonNull(locale, "locale"),
                Objects.requireNonNull(urlOpener, "urlOpener"))
            .bodies());
  }

  private static Catalog<ComponentApi, FunctionImplementation> implement(
      Map<String, FunctionBody> bodies) {
    List<FunctionImplementation> functions = new ArrayList<>();
    for (FunctionApi function : api().functions().values()) {
      FunctionBody body = bodies.get(function.name());
      functions.add(
          new FunctionImplementation(
              function, body != null ? body : notImplemented(function.name())));
    }
    return api().withFunctions(functions);
  }

  private static FunctionBody notImplemented(String name) {
    return (args, context) -> {
      throw new A2uiExpressionError(
          "The function '" + name + "' of the basic catalog is not implemented in this SDK.");
    };
  }

  /** Reads the document at the first call of {@link #api()}. */
  private static final class Api {
    static final Catalog<ComponentApi, FunctionApi> CATALOG =
        Catalog.fromJson(Json.objectResource(BasicCatalog.class, "catalog.json"));
  }
}
