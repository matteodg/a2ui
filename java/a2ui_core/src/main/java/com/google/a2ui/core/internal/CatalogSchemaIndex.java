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

package com.google.a2ui.core.internal;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Answers questions about the component schemas of a v1.0 catalog. Each answer needs a walk through
 * a schema.
 *
 * <p>An index is not thread-safe.
 */
public final class CatalogSchemaIndex {
  private static final int MAX_DEPTH = 32;
  private static final List<String> COMBINERS = List.of("allOf", "oneOf", "anyOf");
  private static final String DEFINITIONS_PREFIX = "#/$defs/";

  /**
   * A reference from one component to a different component.
   *
   * @param id the ID of the component that the reference names
   * @param field the property that contains the reference, for example {@code children[1]}
   */
  public record ChildReference(String id, String field) {}

  private final Catalog<?, ?> catalog;
  private final Map<String, List<Map<String, Object>>> blocks = new HashMap<>();

  /** The document of the catalog as its author wrote it, or null before the first use. */
  private Map<String, Object> authored;

  /**
   * Makes an index for one catalog.
   *
   * @param catalog the catalog to answer questions about
   */
  public CatalogSchemaIndex(Catalog<?, ?> catalog) {
    this.catalog = Objects.requireNonNull(catalog, "catalog");
  }

  /**
   * Tells if the schema of a component type declares, as its own property, one of the names that
   * the component envelope used before v1.0: {@code catalogId}, {@code metadata}, {@code
   * accessibility} or {@code checks}.
   *
   * <p>A schema can write {@code catalogId} or {@code metadata} adjacent to {@code id} in the same
   * {@code properties} block, with the structure that the envelope gives to it. That is the
   * envelope, and it is not a property of the component.
   *
   * @param type the name of the component type
   * @param key the name to look for
   * @return true if the schema declares the property. The result is false if the catalog does not
   *     define the type.
   */
  public boolean ownsLegacyEnvelopeName(String type, String key) {
    for (Map<String, Object> block : propertyBlocks(type)) {
      if (block.containsKey(key)
          && !(block.containsKey("id") && hasEnvelopeShape(key, block.get(key)))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets the components that a component refers to. A reference is a value at a location that the
   * schema of the type gives the type {@code ComponentId}, {@code Child} or {@code ChildList}.
   *
   * @param type the name of the component type
   * @param component the component, as plain JSON values
   * @return the references in the sequence of the schema. Each ID is there one time. The list is
   *     empty if the catalog does not define the type.
   * @throws A2uiCatalogError if the catalog has no validation schema
   */
  public List<ChildReference> children(String type, Map<String, Object> component) {
    if (!catalog.components().containsKey(type)) {
      return List.of();
    }
    Map<String, Object> document = catalog.validationSchema();
    Map<String, ChildReference> found = new LinkedHashMap<>();
    Object schema = Json.asObject(document.get("components")).get(type);
    collectChildren(document, schema, component, "", true, found, 0);
    return List.copyOf(found.values());
  }

  /** Gets each {@code properties} block of the schema of a type and of the schemas it composes. */
  private List<Map<String, Object>> propertyBlocks(String type) {
    return blocks.computeIfAbsent(
        type,
        name -> {
          List<Map<String, Object>> found = new ArrayList<>();
          ComponentApi component = catalog.components().get(name);
          if (component != null) {
            collectBlocks(component.schema(), found, 0);
          }
          return found;
        });
  }

  private void collectBlocks(Object schema, List<Map<String, Object>> found, int depth) {
    Map<String, Object> map = Json.asObject(schema);
    if (map == null || depth > MAX_DEPTH) {
      return;
    }
    Map<String, Object> properties = Json.asObject(map.get("properties"));
    if (properties != null) {
      found.add(properties);
    }
    for (String combiner : COMBINERS) {
      if (map.get(combiner) instanceof List<?> members) {
        members.forEach(member -> collectBlocks(member, found, depth + 1));
      }
    }
    // A reference to a common type is a mixin of the protocol. The catalog does not declare it.
    if (map.get("$ref") instanceof String ref && ref.startsWith("#/")) {
      collectBlocks(resolve(authored(), ref), found, depth + 1);
    }
  }

  private Map<String, Object> authored() {
    if (authored == null) {
      authored = catalog.toJson();
    }
    return authored;
  }

  private static boolean hasEnvelopeShape(String key, Object schema) {
    Map<String, Object> map = Json.asObject(schema);
    if (map == null) {
      return false;
    }
    return switch (key) {
      case "catalogId" -> "string".equals(map.get("type"));
      case "metadata" ->
          "object".equals(map.get("type"))
              && map.get("properties") instanceof Map<?, ?> properties
              && properties.keySet().equals(Set.of("extensions"));
      default -> false;
    };
  }

  /**
   * Collects the references of a value.
   *
   * @param document the validation schema of the catalog
   * @param schema the schema of the value
   * @param field the property that contains the value, for the result
   * @param isComponent true if the value is the component. Its {@code id} is not a reference.
   */
  private static void collectChildren(
      Map<String, Object> document,
      Object schema,
      Object value,
      String field,
      boolean isComponent,
      Map<String, ChildReference> found,
      int depth) {
    Map<String, Object> map = Json.asObject(schema);
    if (map == null || value == null || depth > MAX_DEPTH) {
      return;
    }
    if (map.get("$ref") instanceof String ref) {
      String name =
          ref.startsWith(DEFINITIONS_PREFIX) ? ref.substring(DEFINITIONS_PREFIX.length()) : "";
      switch (name) {
        case "ComponentId", "Child" -> {
          if (value instanceof String id) {
            found.putIfAbsent(id, new ChildReference(id, field));
          }
        }
        case "ChildList" -> {
          if (value instanceof List<?> ids) {
            for (int index = 0; index < ids.size(); index++) {
              if (ids.get(index) instanceof String id) {
                found.putIfAbsent(id, new ChildReference(id, field + "[" + index + "]"));
              }
            }
          } else if (value instanceof Map<?, ?> template
              && template.get("componentId") instanceof String id) {
            found.putIfAbsent(id, new ChildReference(id, field));
          }
        }
        default ->
            collectChildren(
                document, resolve(document, ref), value, field, false, found, depth + 1);
      }
    }
    for (String combiner : COMBINERS) {
      if (map.get(combiner) instanceof List<?> members) {
        for (Object member : members) {
          collectChildren(document, member, value, field, isComponent, found, depth + 1);
        }
      }
    }
    Map<String, Object> properties = Json.asObject(map.get("properties"));
    if (properties != null && value instanceof Map<?, ?> object) {
      properties.forEach((name, propertySchema) -> {
        // The validation schema gives the type ComponentId to the `id` of the component.
        if (isComponent && name.equals("id")) {
          return;
        }
        String at = field.isEmpty() ? name : field + "." + name;
        collectChildren(document, propertySchema, object.get(name), at, false, found, depth + 1);
      });
    }
    Object items = map.get("items");
    if (items != null && value instanceof List<?> list) {
      for (int index = 0; index < list.size(); index++) {
        collectChildren(
            document, items, list.get(index), field + "[" + index + "]", false, found, depth + 1);
      }
    }
  }

  /** Gets the value that a local reference names in a document, or null if it names nothing. */
  private static Object resolve(Map<String, Object> root, String ref) {
    if (!ref.startsWith("#/")) {
      return null;
    }
    Object current = root;
    for (String raw : ref.substring(2).split("/", -1)) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = map.get(Json.unescapePointer(raw));
      if (current == null) {
        return null;
      }
    }
    return current;
  }
}
