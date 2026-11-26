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

import com.google.a2ui.core.schema.v1_0.SchemaDocuments;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The {@code $ref} values of the v1.0 schemas.
 *
 * <p>The specification writes a reference to a common type as {@code
 * common_types.json#/$defs/DynamicString}. It writes a reference to the catalog of a surface as
 * {@code catalog.json#/$defs/anyFunction}. The SDK validates against one document in memory. Thus
 * it changes these references to local references, for example {@code #/$defs/DynamicString}.
 */
public final class SchemaReferences {
  private static final String DEFINITIONS_PREFIX = "#/$defs/";
  private static final String COMMON_TYPES_FILE = "common_types.json";
  private static final String CATALOG_FILE = "catalog.json";

  private SchemaReferences() {}

  /**
   * Gets the common types of protocol v1.0 with each reference made local.
   *
   * @return the definitions by name. The map is read-only.
   */
  public static Map<String, Object> commonTypes() {
    return CommonTypes.DEFINITIONS;
  }

  /**
   * Makes a deep copy of a schema in which each reference to {@code common_types.json} or to {@code
   * catalog.json} is local. Other references stay as they are.
   *
   * @param schema the schema to copy, as plain JSON values
   * @param onCommonType receives the name of each common type that a reference to {@code
   *     common_types.json} names
   * @return the copy. Its maps and its lists can change.
   */
  public static Object localize(Object schema, Consumer<String> onCommonType) {
    return switch (schema) {
      case Map<?, ?> map -> {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) ->
            copy.put(
                (String) key,
                "$ref".equals(key) && value instanceof String ref
                    ? localReference(ref, onCommonType)
                    : localize(value, onCommonType)));
        yield copy;
      }
      case List<?> list -> {
        List<Object> copy = new ArrayList<>(list.size());
        list.forEach(item -> copy.add(localize(item, onCommonType)));
        yield copy;
      }
      case null, default -> schema;
    };
  }

  /**
   * Gets the name of the definition that a local reference names.
   *
   * @param ref the value of a {@code $ref}, or a different value
   * @return the name, or null if the value is not a reference into the {@code $defs} of its
   *     document. For {@code #/$defs/Action/properties/event}, the name is {@code Action}.
   */
  public static String definitionName(Object ref) {
    if (!(ref instanceof String text) || !text.startsWith(DEFINITIONS_PREFIX)) {
      return null;
    }
    String path = text.substring(DEFINITIONS_PREFIX.length());
    int end = path.indexOf('/');
    return Json.unescapePointer(end < 0 ? path : path.substring(0, end));
  }

  /**
   * Collects the name of each definition that the local references of a schema name.
   *
   * @param schema the schema to examine
   * @param names the collection that receives the names
   */
  public static void collectDefinitionNames(Object schema, Collection<String> names) {
    if (schema instanceof Map<?, ?> map) {
      String name = definitionName(map.get("$ref"));
      if (name != null) {
        names.add(name);
      }
      map.values().forEach(value -> collectDefinitionNames(value, names));
    } else if (schema instanceof List<?> list) {
      list.forEach(item -> collectDefinitionNames(item, names));
    }
  }

  private static String localReference(String ref, Consumer<String> onCommonType) {
    int hash = ref.indexOf('#');
    if (hash <= 0) {
      return ref;
    }
    String document = ref.substring(0, hash);
    String file = document.substring(document.lastIndexOf('/') + 1);
    String local = ref.substring(hash);
    if (file.equals(COMMON_TYPES_FILE)) {
      String name = definitionName(local);
      if (name != null) {
        onCommonType.accept(name);
      }
      return local;
    }
    return file.equals(CATALOG_FILE) ? local : ref;
  }

  /** Reads the common types when the JVM initializes the class. That is at the first use. */
  private static final class CommonTypes {
    static final Map<String, Object> DEFINITIONS = load();

    private static Map<String, Object> load() {
      Object definitions = SchemaDocuments.commonTypes().get("$defs");
      // In common_types.json, a reference to a common type is local already.
      return Json.asObject(Json.freeze(localize(definitions, name -> {})));
    }
  }
}
