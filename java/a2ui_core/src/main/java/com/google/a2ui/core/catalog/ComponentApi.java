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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The definition of a component: its name and its JSON Schema. It has no code that shows the
 * component.
 *
 * <p>A framework adapter extends this class and adds a view.
 */
public class ComponentApi {
  private final String name;
  private final Map<String, Object> schema;
  private final A2uiMetadata metadata;

  /**
   * Makes a definition and makes a read-only copy of the schema.
   *
   * @param name the name of the component as messages write it, for example {@code Button}
   * @param schema the definition of the component as a catalog document writes it
   * @throws A2uiCatalogError if the schema is not JSON, or if its {@code metadata} is not an object
   */
  public ComponentApi(String name, Map<String, ?> schema) {
    this.name = Objects.requireNonNull(name, "name");
    this.schema = frozenDefinition("component", name, schema);
    Object rawMetadata = this.schema.get("metadata");
    if (rawMetadata == null) {
      this.metadata = null;
    } else if (rawMetadata instanceof Map<?, ?> map) {
      this.metadata = new A2uiMetadata(Optional.ofNullable(Json.asObject(map.get("extensions"))));
    } else {
      throw new A2uiCatalogError(
          "Component '" + name + "' has a 'metadata' that is not an object.");
    }
  }

  /**
   * Gets the name of the component as messages write it.
   *
   * @return the name
   */
  public final String name() {
    return name;
  }

  /**
   * Gets the definition of the component as a catalog document writes it.
   *
   * @return the definition. It is read-only.
   */
  public final Map<String, Object> schema() {
    return schema;
  }

  /**
   * Gets the {@code metadata} of the definition.
   *
   * @return the metadata, or an empty result if the definition declares none
   */
  public final Optional<A2uiMetadata> metadata() {
    return Optional.ofNullable(metadata);
  }

  /**
   * Gets the names of the components that can be the parent of this component.
   *
   * @return the names, or an empty result if each component can be the parent
   */
  public final Optional<List<String>> allowedParents() {
    return names("allowedParents");
  }

  /**
   * Gets the names of the components that this component can contain.
   *
   * @return the names, or an empty result if this component can contain each component
   */
  public final Optional<List<String>> allowedChildren() {
    return names("allowedChildren");
  }

  @Override
  public String toString() {
    return "ComponentApi(" + name + ")";
  }

  /**
   * Makes a read-only copy of the definition of a catalog entry.
   *
   * @param kind the type of the entry for the error message: {@code component} or {@code function}
   * @param name the name of the entry
   * @param schema the definition to copy
   * @throws A2uiCatalogError if the definition is not JSON
   */
  static Map<String, Object> frozenDefinition(String kind, String name, Map<String, ?> schema) {
    Objects.requireNonNull(schema, "schema");
    try {
      return Json.freezeObject(schema);
    } catch (IllegalArgumentException e) {
      throw new A2uiCatalogError(
          "The definition of " + kind + " '" + name + "' is not JSON: " + e.getMessage(), e);
    }
  }

  private Optional<List<String>> names(String key) {
    if (!(schema.get(key) instanceof List<?> list)) {
      return Optional.empty();
    }
    return Optional.of(
        list.stream().filter(String.class::isInstance).map(String.class::cast).toList());
  }
}
