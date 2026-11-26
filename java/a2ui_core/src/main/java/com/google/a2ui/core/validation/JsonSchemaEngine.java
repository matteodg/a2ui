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

import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.internal.Json;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaException;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Validates values against the schemas in one self-contained JSON Schema 2020-12 document.
 *
 * <p>Each {@code $ref} in the document must be local ({@code #/...}). The engine reads nothing from
 * the disk or from the network.
 *
 * <p>Only this class and {@link UnicodeRegularExpressions} use the JSON Schema library. Thus the
 * library is not visible in the public API.
 *
 * <p>An engine is safe to use from more than one thread, because {@link CommonTypeValidator} has
 * one engine for all its callers.
 */
final class JsonSchemaEngine {
  /** The name that the engine gives to its document. The name is not an address. */
  private static final String DOCUMENT_IRI = "https://a2ui.invalid/schema.json";

  private static final SchemaRegistryConfig CONFIG =
      SchemaRegistryConfig.builder()
          .regularExpressionFactory(new UnicodeRegularExpressions())
          .build();

  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  private final SchemaRegistry registry;
  private final Map<String, Schema> schemas = new ConcurrentHashMap<>();

  /**
   * Makes an engine for one document.
   *
   * @param document the schema document, as plain JSON values that {@code Json.freeze} made
   */
  JsonSchemaEngine(Map<String, Object> document) {
    // The engine uses its own name for the document. Thus the `$id` of the document has no effect.
    Map<String, Object> unnamed = new LinkedHashMap<>(document);
    unnamed.remove("$id");
    Map<String, String> texts = Map.of(DOCUMENT_IRI, Json.write(unnamed));
    registry =
        SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaRegistryConfig(CONFIG).schemas(texts));
  }

  /**
   * Validates a value against one schema of the document.
   *
   * @param pointer the JSON Pointer of the schema in the document, for example {@code
   *     /components/Button}
   * @param value the value to validate, as plain JSON values that {@code Json.freeze} made
   * @return one message for each problem. The list is empty if the value is valid.
   * @throws A2uiCatalogError if the document has no schema at the pointer, or if the schema has a
   *     {@code $ref} that the engine cannot follow in the document
   */
  List<String> errors(String pointer, Object value) {
    List<Error> errors;
    try {
      Schema schema =
          schemas.computeIfAbsent(
              pointer, key -> registry.getSchema(SchemaLocation.of(DOCUMENT_IRI + "#" + key)));
      errors = schema.validate(toNode(value));
    } catch (SchemaException e) {
      throw new A2uiCatalogError(
          "The schema at '" + pointer + "' cannot validate a value: " + e.getMessage(), e);
    }
    List<String> messages = new ArrayList<>();
    for (Error error : errors) {
      String location = error.getInstanceLocation().toString();
      messages.add(
          location.isEmpty() || location.equals("$")
              ? error.getMessage()
              : location + ": " + error.getMessage());
    }
    return messages;
  }

  /** Converts a plain JSON value to the tree that the JSON Schema library reads. */
  private static JsonNode toNode(Object value) {
    return switch (value) {
      case null -> NODES.nullNode();
      case String s -> NODES.stringNode(s);
      case Boolean b -> NODES.booleanNode(b);
      case Long l -> NODES.numberNode(l.longValue());
      case Double d -> NODES.numberNode(d.doubleValue());
      case Map<?, ?> map -> {
        ObjectNode node = NODES.objectNode();
        map.forEach((key, item) -> node.set((String) key, toNode(item)));
        yield node;
      }
      case List<?> list -> {
        ArrayNode node = NODES.arrayNode(list.size());
        list.forEach(item -> node.add(toNode(item)));
        yield node;
      }
      default ->
          throw new IllegalArgumentException(
              "Not a plain JSON value: " + value.getClass().getName());
    };
  }
}
