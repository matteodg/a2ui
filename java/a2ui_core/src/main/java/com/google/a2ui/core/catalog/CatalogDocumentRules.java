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

import com.google.a2ui.core.common.Uax31;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.Json;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The rules that protocol v1.0 adds for a catalog document. The load of a document runs them. */
final class CatalogDocumentRules {
  /**
   * The properties that the protocol adds to each v1.0 component. A catalog does not give their
   * names.
   */
  private static final Set<String> COMPONENT_ENVELOPE_KEYS =
      Set.of("@catalogId", "@accessibility", "@metadata", "@checks");

  private static final List<String> SCHEMA_KEYWORDS = List.of("oneOf", "anyOf", "not");

  private static final int MAX_REF_DEPTH = 64;

  private CatalogDocumentRules() {}

  /**
   * Examines a v1.0 catalog document.
   *
   * @param document the document, as {@code Json.freeze} makes it
   * @param catalogId the ID of the catalog, for the error messages
   * @throws A2uiCatalogError for one of these conditions:
   *     <ul>
   *       <li>A {@code $ref} goes out of the document and does not name {@code common_types.json}.
   *       <li>A local {@code $ref} names nothing.
   *       <li>A name is not a UAX #31 identifier.
   *       <li>A function name starts with {@code @}. The protocol keeps these names.
   *       <li>A function requires user activation and lets the agent call it.
   *     </ul>
   *
   * @throws A2uiValidationError if a key of the {@code metadata.extensions} of a component is not a
   *     UAX #31 identifier
   */
  static void check(Map<String, Object> document, String catalogId) {
    checkRefs(document, document, "", catalogId);
    Map<String, Object> components = Json.asObject(document.get("components"));
    if (components != null) {
      for (Map.Entry<String, Object> entry : components.entrySet()) {
        String name = entry.getKey();
        checkIdentifier(name, "component identifier", catalogId, name);
        for (String property : propertyNames(entry.getValue(), document, 0)) {
          if (COMPONENT_ENVELOPE_KEYS.contains(property)) {
            continue;
          }
          checkIdentifier(
              property, "property identifier in component '" + name + "'", catalogId, property);
        }
        checkMetadata(name, entry.getValue());
      }
    }
    Map<String, Object> functions = Json.asObject(document.get("functions"));
    if (functions != null) {
      for (Map.Entry<String, Object> entry : functions.entrySet()) {
        String name = entry.getKey();
        if (name.startsWith("@")) {
          throw new A2uiCatalogError(
              "Catalog '"
                  + catalogId
                  + "' declares function '"
                  + name
                  + "': names starting with '@' are reserved for the protocol's system functions.");
        }
        checkIdentifier(name, "function identifier", catalogId, name);
        for (String argument : argumentNames(entry.getValue(), document)) {
          // An argument can have one `@` at the start, as the envelope keys of the protocol have.
          checkIdentifier(
              argument.startsWith("@") ? argument.substring(1) : argument,
              "argument identifier in function '" + name + "'",
              catalogId,
              argument);
        }
        checkUserActivation(name, entry.getValue(), catalogId);
      }
    }
  }

  private static void checkRefs(
      Object node, Map<String, Object> document, String pointer, String id) {
    if (node instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = (String) entry.getKey();
        String path = pointer + "/" + Json.escapePointer(key);
        if (key.equals("$ref") && entry.getValue() instanceof String ref) {
          checkRef(ref, document, path, id);
        }
        checkRefs(entry.getValue(), document, path, id);
      }
    } else if (node instanceof List<?> list) {
      for (int index = 0; index < list.size(); index++) {
        checkRefs(list.get(index), document, pointer + "/" + index, id);
      }
    }
  }

  private static void checkRef(String ref, Map<String, Object> document, String path, String id) {
    if (ref.startsWith("#")) {
      if (ref.length() > 1 && resolveLocal(ref, document) == null) {
        throw new A2uiCatalogError(
            "Catalog '" + id + "' has a $ref '" + ref + "' at " + path + " that names nothing.");
      }
      return;
    }
    int fragment = ref.indexOf('#');
    String location = fragment < 0 ? ref : ref.substring(0, fragment);
    String file = location.substring(location.lastIndexOf('/') + 1);
    boolean supported = location.equals("catalog.json") || file.equals("common_types.json");
    if (!supported) {
      throw new A2uiCatalogError(
          "Unsupported external $ref '"
              + ref
              + "' at "
              + path
              + " in catalog '"
              + id
              + "': a v1.0 catalog may only reference itself (#/...) or common_types.json.");
    }
  }

  /**
   * Gets the value that a local reference names in a document.
   *
   * @return the value, or null if the reference names nothing
   */
  private static Object resolveLocal(String ref, Map<String, Object> document) {
    if (!ref.startsWith("#/")) {
      return null;
    }
    Object current = document;
    for (String raw : ref.substring(2).split("/", -1)) {
      String segment = Json.unescapePointer(raw);
      if (current instanceof Map<?, ?> map) {
        current = map.get(segment);
      } else if (current instanceof List<?> list) {
        int index = listIndex(segment);
        current = index >= 0 && index < list.size() ? list.get(index) : null;
      } else {
        current = null;
      }
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  /** Gets the list index that a segment gives, or -1 if the segment is not a list index. */
  private static int listIndex(String segment) {
    if (segment.isEmpty() || segment.length() > 9) {
      return -1;
    }
    for (int i = 0; i < segment.length(); i++) {
      char c = segment.charAt(i);
      if (c < '0' || c > '9') {
        return -1;
      }
    }
    return Integer.parseInt(segment);
  }

  /**
   * Gets the property names that an object schema declares.
   *
   * <p>The result includes the names of the {@code allOf} members and of the local definitions that
   * the schema references. Validation merges all of them.
   */
  private static Set<String> propertyNames(Object schema, Map<String, Object> document, int depth) {
    Set<String> names = new LinkedHashSet<>();
    if (!(schema instanceof Map<?, ?> map) || depth > MAX_REF_DEPTH) {
      return names;
    }
    if (map.get("properties") instanceof Map<?, ?> properties) {
      for (Object key : properties.keySet()) {
        names.add((String) key);
      }
    }
    if (map.get("allOf") instanceof List<?> members) {
      for (Object member : members) {
        names.addAll(propertyNames(member, document, depth + 1));
      }
    }
    if (map.get("$ref") instanceof String ref && ref.startsWith("#/")) {
      names.addAll(propertyNames(resolveLocal(ref, document), document, depth + 1));
    }
    return names;
  }

  /**
   * Gets the argument names that a function definition declares.
   *
   * <p>The names are the properties of the {@code args} schema. If the definition has no such
   * schema, the names are the keys of a {@code parameters} map from a name to a schema.
   */
  private static Set<String> argumentNames(Object definition, Map<String, Object> document) {
    Map<String, Object> function = Json.asObject(definition);
    if (function == null) {
      return Set.of();
    }
    Map<String, Object> properties = Json.asObject(function.get("properties"));
    Object declared = properties == null ? null : properties.get("args");
    Map<String, Object> args =
        Json.asObject(declared != null ? declared : function.get("parameters"));
    if (args == null) {
      return Set.of();
    }
    if (args.containsKey("properties") || args.containsKey("allOf") || args.containsKey("$ref")) {
      return propertyNames(args, document, 0);
    }
    boolean isSchema =
        args.get("type") instanceof String || SCHEMA_KEYWORDS.stream().anyMatch(args::containsKey);
    Set<String> names = new LinkedHashSet<>();
    for (Map.Entry<String, Object> entry : args.entrySet()) {
      if (entry.getKey().startsWith("$")) {
        continue;
      }
      if (!(entry.getValue() instanceof Map<?, ?>) && !(entry.getValue() instanceof Boolean)) {
        return Set.of();
      }
      names.add(entry.getKey());
    }
    return isSchema ? Set.of() : names;
  }

  private static void checkIdentifier(String name, String kind, String catalogId, String shown) {
    if (!Uax31.isIdentifier(name)) {
      throw new A2uiCatalogError(
          "Invalid UAX #31 " + kind + " '" + shown + "' in catalog '" + catalogId + "'.");
    }
  }

  private static void checkMetadata(String component, Object definition) {
    Map<String, Object> schema = Json.asObject(definition);
    Map<String, Object> metadata = schema == null ? null : Json.asObject(schema.get("metadata"));
    Map<String, Object> extensions =
        metadata == null ? null : Json.asObject(metadata.get("extensions"));
    if (extensions == null) {
      return;
    }
    for (String key : extensions.keySet()) {
      if (!Uax31.isIdentifier(key)) {
        throw new A2uiValidationError(
            "Component '"
                + component
                + "' has a metadata.extensions key '"
                + key
                + "' that is not a UAX #31 identifier.");
      }
    }
  }

  private static void checkUserActivation(String name, Object definition, String catalogId) {
    Map<String, Object> function = Json.asObject(definition);
    if (function == null || !Boolean.TRUE.equals(function.get("requiresUserActivation"))) {
      return;
    }
    Object declared = function.get("allowedCallers");
    Object callers = declared != null ? declared : AllowedCallers.RENDERER_ONLY.wireValue();
    if (!callers.equals(AllowedCallers.RENDERER_ONLY.wireValue())) {
      throw new A2uiCatalogError(
          "Function '"
              + name
              + "' in catalog '"
              + catalogId
              + "' sets requiresUserActivation, which requires allowedCallers to be rendererOnly,"
              + " but it is '"
              + callers
              + "'.");
    }
  }
}
