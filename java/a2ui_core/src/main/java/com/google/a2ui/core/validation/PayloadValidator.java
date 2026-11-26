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

package com.google.a2ui.core.validation;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.CatalogChecks;
import com.google.a2ui.core.internal.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Examines one component or one function call against one v1.0 catalog.
 *
 * <p>A surface can use more than one catalog, and this validator knows only its own catalog. The
 * validator fully examines a nested function call that runs in its catalog. Such a call names this
 * catalog, or it names no catalog and its component also names none. For each other call, the
 * validator examines only what needs no catalog: the structure of the call and the syntax of its
 * names.
 *
 * <p>A validator is not thread-safe.
 */
public final class PayloadValidator {
  private final CatalogChecks checks;

  /**
   * Makes a validator for one catalog.
   *
   * @param catalog the catalog to examine against
   * @throws A2uiCatalogError if the catalog is not a v1.0 catalog, or if it has no validation
   *     schema. See {@link Catalog#validationSchema()}.
   */
  public PayloadValidator(Catalog<?, ?> catalog) {
    this.checks = new CatalogChecks(Objects.requireNonNull(catalog, "catalog"));
  }

  /**
   * Examines a component object, as the message {@code updateComponents} contains it.
   *
   * @param component the component as plain JSON values
   * @throws A2uiValidationError if the component names a type that the catalog does not define, if
   *     it does not agree with the schema of its type, or if it contains a function call that is
   *     not correct. The message lists each problem.
   * @throws A2uiCatalogError if the schema of the type has a reference that names nothing
   */
  public void validateComponent(Map<String, ?> component) {
    Map<String, Object> value = frozen(component);
    // A component that names its catalog is possibly not in the default catalog of the surface.
    // A call that names no catalog runs in that default catalog. Thus this catalog cannot examine
    // such a call.
    List<String> errors = checks.componentErrors(value, !value.containsKey("@catalogId"), false);
    if (!errors.isEmpty()) {
      throw new A2uiValidationError(
          "Component '"
              + (value.get("id") == null ? "?" : value.get("id"))
              + "' ("
              + (value.get("component") == null ? "?" : value.get("component"))
              + ") is invalid: "
              + String.join("; ", errors));
    }
  }

  /**
   * Examines a call of a function of this catalog, and the calls in its arguments.
   *
   * @param name the name of the function
   * @param args the arguments of the call as plain JSON values
   * @throws A2uiValidationError if the catalog does not define the function, or if the arguments do
   *     not agree with its schema. The message lists each problem.
   * @throws A2uiCatalogError if the schema of the function has a reference that names nothing
   */
  public void validateFunction(String name, Map<String, ?> args) {
    Map<String, Object> call = new LinkedHashMap<>();
    call.put("@call", Objects.requireNonNull(name, "name"));
    call.put("args", Objects.requireNonNull(args, "args"));
    List<String> errors = checks.nestedCallErrors(frozen(call), true, false);
    if (!errors.isEmpty()) {
      throw new A2uiValidationError(
          "Call to '" + name + "' is invalid: " + String.join("; ", errors));
    }
  }

  private static Map<String, Object> frozen(Map<String, ?> value) {
    Objects.requireNonNull(value, "value");
    try {
      return Json.freezeObject(value);
    } catch (IllegalArgumentException e) {
      throw new A2uiValidationError("The value to validate is not JSON: " + e.getMessage(), e);
    }
  }
}
