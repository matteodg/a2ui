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

import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.common.EventNotifier;
import com.google.a2ui.core.common.EventSource;
import com.google.a2ui.core.internal.Json;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One component of a surface, as the agent last described it: its type, its catalog and its raw
 * properties. No binding is resolved.
 *
 * <p>A model is not thread-safe.
 */
public final class ComponentModel {
  private final String id;
  private final String type;
  private final Catalog<?, ?> catalog;
  private final EventNotifier<ComponentModel> updated = new EventNotifier<>();
  private Definition definition;

  /**
   * Makes a component and makes read-only copies of its values.
   *
   * @param id the ID of the component. It is unique in its surface.
   * @param type the name of the component type, for example {@code Button}
   * @param catalog the catalog that defines the component
   * @param properties the properties of the component, without the envelope ({@code id}, {@code
   *     component} and the {@code @} keys)
   * @param metadata the {@code @metadata} of the component, or null if it has none
   * @param accessibility the {@code @accessibility} attributes as the message writes them, or null
   *     if the component has none
   * @param checks the {@code @checks} rules as the message writes them, or null if the component
   *     has none
   * @throws IllegalArgumentException if a value is not JSON
   */
  public ComponentModel(
      String id,
      String type,
      Catalog<?, ?> catalog,
      Map<String, ?> properties,
      A2uiMetadata metadata,
      Map<String, ?> accessibility,
      List<?> checks) {
    this.id = Objects.requireNonNull(id, "id");
    this.type = Objects.requireNonNull(type, "type");
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.definition = Definition.of(properties, metadata, accessibility, checks);
  }

  /**
   * Gets the ID of the component.
   *
   * @return the ID. It is unique in its surface.
   */
  public String id() {
    return id;
  }

  /**
   * Gets the name of the component type.
   *
   * @return the name, for example {@code Button}
   */
  public String type() {
    return type;
  }

  /**
   * Gets the catalog that defines the component. It is the catalog that the {@code @catalogId} of
   * the component names. If the component names none, it is the default catalog of the surface.
   *
   * @return the catalog
   */
  public Catalog<?, ?> catalog() {
    return catalog;
  }

  /**
   * Gets the properties of the component, without the envelope ({@code id}, {@code component} and
   * the {@code @} keys).
   *
   * @return the properties as plain JSON values. The map is read-only.
   */
  public Map<String, Object> properties() {
    return definition.properties();
  }

  /**
   * Gets the {@code @metadata} of the component.
   *
   * @return the metadata, or an empty result if the component has none
   */
  public Optional<A2uiMetadata> metadata() {
    return Optional.ofNullable(definition.metadata());
  }

  /**
   * Gets the {@code @accessibility} attributes of the component, as the message writes them.
   *
   * @return the attributes, or an empty result if the component has none. The map is read-only.
   */
  public Optional<Map<String, Object>> accessibility() {
    return Optional.ofNullable(definition.accessibility());
  }

  /**
   * Gets the {@code @checks} rules of the component, as the message writes them.
   *
   * @return the rules, or an empty result if the component has none. The list is read-only.
   */
  public Optional<List<Object>> checks() {
    return Optional.ofNullable(definition.checks());
  }

  /**
   * Gets the source of the update events. An event occurs after an entry of {@code
   * updateComponents} changes this component. The component stays the same object.
   *
   * @return the event source. The event is this component.
   */
  public EventSource<ComponentModel> onUpdated() {
    return updated;
  }

  /**
   * Replaces all the values of the component, as an entry of {@code updateComponents} does, and
   * then notifies the listeners. If the new values are equal to the current values, nothing occurs.
   *
   * @param properties the new properties, without the envelope
   * @param metadata the new {@code @metadata}, or null if the component has none
   * @param accessibility the new {@code @accessibility} attributes, or null
   * @param checks the new {@code @checks} rules, or null
   * @throws IllegalArgumentException if a value is not JSON
   */
  public void replaceDefinition(
      Map<String, ?> properties,
      A2uiMetadata metadata,
      Map<String, ?> accessibility,
      List<?> checks) {
    Definition replacement = Definition.of(properties, metadata, accessibility, checks);
    if (definition.equals(replacement)) {
      return;
    }
    definition = replacement;
    updated.emit(this);
  }

  /** Removes each listener. Use this method when the component leaves its surface. */
  public void dispose() {
    updated.dispose();
  }

  @Override
  public String toString() {
    return "ComponentModel(" + id + ": " + type + ")";
  }

  /** The values that an entry of {@code updateComponents} gives to a component. */
  private record Definition(
      Map<String, Object> properties,
      A2uiMetadata metadata,
      Map<String, Object> accessibility,
      List<Object> checks) {
    @SuppressWarnings("unchecked")
    static Definition of(
        Map<String, ?> properties,
        A2uiMetadata metadata,
        Map<String, ?> accessibility,
        List<?> checks) {
      Objects.requireNonNull(properties, "properties");
      return new Definition(
          Json.freezeObject(properties),
          metadata,
          accessibility == null ? null : Json.freezeObject(accessibility),
          checks == null ? null : (List<Object>) Json.freeze(checks));
    }
  }
}
