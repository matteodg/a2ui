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
import com.google.a2ui.core.internal.Json;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The signature of a catalog function: its name, its return type, its permitted callers and the
 * JSON Schema of a call. It has no code.
 *
 * <p>The implementation of a function extends this class.
 */
public class FunctionApi {
  private final String name;
  private final Map<String, Object> schema;
  private final FunctionReturnType returnType;
  private final AllowedCallers allowedCallers;
  private final boolean requiresUserActivation;

  /**
   * Makes a signature and makes a read-only copy of the schema.
   *
   * @param name the name of the function as a call writes it, for example {@code formatString}
   * @param schema the definition of the function as a catalog document writes it
   * @throws A2uiCatalogError if the schema is not JSON, or if its {@code allowedCallers} or its
   *     {@code requiresUserActivation} has a value that is not correct
   */
  public FunctionApi(String name, Map<String, ?> schema) {
    this.name = Objects.requireNonNull(name, "name");
    this.schema = ComponentApi.frozenDefinition("function", name, schema);
    this.returnType = readReturnType(this.schema);
    this.allowedCallers = readAllowedCallers(name, this.schema.get("allowedCallers"));
    this.requiresUserActivation =
        switch (this.schema.get("requiresUserActivation")) {
          case null -> false;
          case Boolean flag -> flag;
          default ->
              throw new A2uiCatalogError(
                  "Function '" + name + "' has a requiresUserActivation that is not a boolean.");
        };
  }

  /**
   * Gets the name of the function as a call writes it.
   *
   * @return the name
   */
  public final String name() {
    return name;
  }

  /**
   * Gets the definition of the function as a catalog document writes it.
   *
   * @return the definition. It is read-only.
   */
  public final Map<String, Object> schema() {
    return schema;
  }

  /**
   * Gets the {@code description} of the definition.
   *
   * @return the description, or an empty result if the definition has none
   */
  public final Optional<String> description() {
    return schema.get("description") instanceof String text ? Optional.of(text) : Optional.empty();
  }

  /**
   * Gets the type of the value that the function returns.
   *
   * <p>The type is the first of these values that the definition has:
   *
   * <ol>
   *   <li>The constant that the call schema gives for {@code returnType}. Catalogs before v1.0
   *       write the type in this form.
   *   <li>The {@code returnType} of the definition.
   *   <li>{@link FunctionReturnType#ANY}.
   * </ol>
   *
   * @return the return type
   */
  public final FunctionReturnType returnType() {
    return returnType;
  }

  /**
   * Gets the parties that can call the function.
   *
   * @return the permitted callers. The result is {@link AllowedCallers#RENDERER_ONLY} if the
   *     definition does not give them.
   */
  public final AllowedCallers allowedCallers() {
    return allowedCallers;
  }

  /**
   * Tells if the function runs only as the result of a user gesture.
   *
   * @return true if the function requires a user gesture
   */
  public final boolean requiresUserActivation() {
    return requiresUserActivation;
  }

  /**
   * Gets the schema of the arguments of the function.
   *
   * <p>The schema is the property {@code args} of the call schema. If the call schema has no such
   * property, the schema is the {@code parameters} of the definition.
   *
   * @return the schema, or an empty result if the definition declares no arguments
   */
  public final Optional<Map<String, Object>> argumentSchema() {
    Map<String, Object> properties = Json.asObject(schema.get("properties"));
    Object args = properties == null ? null : properties.get("args");
    return Optional.ofNullable(Json.asObject(args != null ? args : schema.get("parameters")));
  }

  @Override
  public String toString() {
    return "FunctionApi(" + name + ")";
  }

  private static FunctionReturnType readReturnType(Map<String, Object> schema) {
    Map<String, Object> properties = Json.asObject(schema.get("properties"));
    Map<String, Object> property =
        properties == null ? null : Json.asObject(properties.get("returnType"));
    if (property != null && property.get("const") instanceof String constant) {
      return FunctionReturnType.fromWire(constant);
    }
    if (schema.get("returnType") instanceof String declared) {
      return FunctionReturnType.fromWire(declared);
    }
    return FunctionReturnType.ANY;
  }

  private static AllowedCallers readAllowedCallers(String name, Object raw) {
    return switch (raw) {
      case null -> AllowedCallers.RENDERER_ONLY;
      case String text ->
          AllowedCallers.fromWire(text)
              .orElseThrow(() ->
                  new A2uiCatalogError(
                      "Function '" + name + "' has an unknown allowedCallers '" + text + "'."));
      default ->
          throw new A2uiCatalogError(
              "Function '" + name + "' has an allowedCallers that is not a string.");
    };
  }
}
