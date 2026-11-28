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

import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.internal.Json;
import com.google.a2ui.core.internal.SchemaReferences;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Makes the self-contained schema that {@link Catalog#validationSchema()} returns.
 *
 * <p>The schema has the structure that the golden files of the conformance suite {@code
 * core/catalog.yaml} give for a v1.0 catalog:
 *
 * <ul>
 *   <li>Each reference is local.
 *   <li>Each component is one object schema. The builder merges the {@code allOf} mixins of the
 *       component into its {@code properties} and its {@code required}, and it adds the {@code id}.
 *   <li>{@code $defs} has the definitions of the catalog, the two unions, and each common type that
 *       the schema uses.
 * </ul>
 */
final class ValidationSchemaBuilder {
  private static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";
  private static final String DEFS = "$defs";
  private static final String REF = "$ref";
  private static final String ANY_COMPONENT = "anyComponent";
  private static final String ANY_FUNCTION = "anyFunction";
  private static final String THEME = "theme";

  /**
   * The common types in which each v1.0 message puts the entries of a catalog. A catalog cannot
   * give a different definition for these names or for a common type that they use, because the
   * validation of a component or of a call always uses them.
   */
  private static final List<String> ENVELOPE_TYPES =
      List.of("ComponentCommon", "FunctionCommon", "FunctionCall", "IndexSystemFunction");

  /** The keywords of a component schema that the builder merges or writes again. */
  private static final Set<String> MERGED_KEYWORDS =
      Set.of(
          "type",
          "description",
          "allOf",
          "properties",
          "required",
          "unevaluatedProperties",
          "additionalProperties",
          "allowedParents",
          "allowedChildren");

  /**
   * The properties that a component does not get from a common type, because the builder or the
   * message schema supplies them.
   */
  private static final Set<String> MIXIN_ENVELOPE_KEYS = Set.of("id", "component", "@catalogId");

  private static final Set<String> COMPONENT_ENVELOPE_KEYS = Set.of("id", "component");

  private ValidationSchemaBuilder() {}

  /**
   * Makes the validation schema of a catalog.
   *
   * @param catalogId the ID of the catalog
   * @param version the protocol version of the catalog, or null if it declares none
   * @param document the catalog document, as {@link Catalog#toJson()} writes it
   * @param componentUnion the union of the components of the catalog
   * @param functionUnion the union of the functions of the catalog
   * @return the schema. It is read-only.
   * @throws A2uiCatalogError if the catalog is not a v1.0 catalog, if it refers to a common type
   *     that does not exist, or if its {@code $defs} has the name of a common type that the schema
   *     uses
   */
  static Map<String, Object> build(
      String catalogId,
      ProtocolVersion version,
      Map<String, Object> document,
      Map<String, Object> componentUnion,
      Map<String, Object> functionUnion) {
    if (version != ProtocolVersion.V1_0) {
      throw new A2uiCatalogError(
          "Catalog '"
              + catalogId
              + "' has no validation schema: this SDK validates v1.0 catalogs only, and the catalog"
              + " is for "
              + (version == null ? "an unstated version" : version.wireValue())
              + ".");
    }
    Set<String> namedCommonTypes = new LinkedHashSet<>();
    Map<String, Object> local =
        Json.asObject(SchemaReferences.localize(document, namedCommonTypes::add));
    Map<String, Object> definitions = new LinkedHashMap<>();
    if (Json.asObject(local.get(DEFS)) != null) {
      definitions.putAll(Json.asObject(local.get(DEFS)));
    }
    // The schema always has the unions of the entries of the catalog. An open union that the
    // document wrote cannot validate a message.
    definitions.remove(ANY_COMPONENT);
    definitions.remove(ANY_FUNCTION);
    checkCommonTypes(catalogId, namedCommonTypes, definitions.keySet());
    Map<String, Object> theme = Json.asObject(local.get(THEME));
    if (theme != null) {
      definitions.put(THEME, theme);
    }
    openTheme(Json.asObject(definitions.get(THEME)));

    Map<String, Object> components = new LinkedHashMap<>();
    Map<String, Object> rawComponents = Json.asObject(local.get("components"));
    Set<String> localMixins = new LinkedHashSet<>();
    if (rawComponents != null) {
      rawComponents.forEach((name, schema) ->
          components.put(name, flatten(name, Json.asObject(schema), definitions, localMixins)));
    }
    Map<String, Object> functions = new LinkedHashMap<>();
    if (Json.asObject(local.get("functions")) != null) {
      functions.putAll(Json.asObject(local.get("functions")));
    }
    functions.values().forEach(function -> closeArguments(Json.asObject(function)));
    removeMergedMixins(localMixins, definitions, List.of(components, functions));
    definitions.put(ANY_COMPONENT, SchemaReferences.localize(componentUnion, name -> {}));
    definitions.put(ANY_FUNCTION, SchemaReferences.localize(functionUnion, name -> {}));

    Map<String, Object> commonTypes = SchemaReferences.commonTypes();
    addDescriptions(components, commonTypes);
    addDescriptions(functions, commonTypes);
    addDescriptions(definitions, commonTypes);

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("$schema", DIALECT);
    schema.put("catalogId", catalogId);
    schema.put("protocolVersion", version.wireValue());
    if (local.get("instructions") instanceof String instructions && !instructions.isEmpty()) {
      schema.put("instructions", instructions);
    }
    if (!components.isEmpty()) {
      schema.put("components", components);
    }
    if (!functions.isEmpty()) {
      schema.put("functions", functions);
    }
    schema.put(DEFS, definitions);
    addCommonTypes(schema, definitions, commonTypes);
    return Json.freezeObject(schema);
  }

  /**
   * Makes sure that the catalog does not give its own definition for a common type that the schema
   * uses. After the references are local, the two definitions have the same reference.
   *
   * @param named the common types that the references of the document name
   * @param authored the names of the definitions of the document
   */
  private static void checkCommonTypes(String catalogId, Set<String> named, Set<String> authored) {
    Map<String, Object> commonTypes = SchemaReferences.commonTypes();
    for (String name : named) {
      if (!commonTypes.containsKey(name)) {
        throw new A2uiCatalogError(
            "Catalog '" + catalogId + "' references an unknown common type '" + name + "'.");
      }
    }
    Deque<String> needed = new ArrayDeque<>(ENVELOPE_TYPES);
    needed.addAll(named);
    Set<String> examined = new HashSet<>();
    while (!needed.isEmpty()) {
      String name = needed.removeFirst();
      // A common type also refers to the unions of the catalog. They are not common types.
      if (!commonTypes.containsKey(name) || !examined.add(name)) {
        continue;
      }
      if (authored.contains(name)) {
        throw new A2uiCatalogError(
            "Catalog '"
                + catalogId
                + "' defines $defs '"
                + name
                + "', which is also the name of a common type it uses.");
      }
      SchemaReferences.collectDefinitionNames(commonTypes.get(name), needed);
    }
  }

  /**
   * Writes in an object theme that it accepts more properties, if the theme does not say. Thus a
   * renderer can add its own theme properties.
   */
  private static void openTheme(Map<String, Object> theme) {
    if (theme != null
        && ("object".equals(theme.get("type")) || theme.containsKey("properties"))
        && !theme.containsKey("additionalProperties")
        && !theme.containsKey("unevaluatedProperties")
        && !theme.containsKey(REF)) {
      theme.put("additionalProperties", true);
    }
  }

  /**
   * The parts of a component schema that have properties.
   *
   * @param mixins the common types that the component mixes in, for example {@code Checkable}
   * @param local the definitions of the catalog that the component mixes in
   * @param commonInline the inline parts that do not declare the property {@code component}
   * @param specificInline the inline parts that declare the property {@code component}
   * @param unmerged the parts of an {@code allOf} that the builder cannot merge. They stay in an
   *     {@code allOf}.
   */
  private record Parts(
      List<Map<String, Object>> mixins,
      List<Map<String, Object>> local,
      List<Map<String, Object>> commonInline,
      List<Map<String, Object>> specificInline,
      List<Object> unmerged) {
    Parts(List<Object> unmerged) {
      this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), unmerged);
    }
  }

  /** Makes one object schema from a component schema and its {@code allOf} mixins. */
  private static Map<String, Object> flatten(
      String name,
      Map<String, Object> component,
      Map<String, Object> definitions,
      Set<String> localMixins) {
    Parts parts = collect(component, definitions, new HashSet<>(), localMixins, new ArrayList<>());
    String description = null;
    String componentConst = name;
    Map<String, Object> merged = new LinkedHashMap<>();
    Set<String> required = new TreeSet<>();
    // A later group replaces a property of an earlier group. Thus a property that the component
    // declares itself replaces the property of a mixin.
    List<List<Map<String, Object>>> groups =
        List.of(parts.mixins(), parts.local(), parts.commonInline(), parts.specificInline());
    for (List<Map<String, Object>> group : groups) {
      boolean isMixin = group == parts.mixins();
      Set<String> skipped = isMixin ? MIXIN_ENVELOPE_KEYS : COMPONENT_ENVELOPE_KEYS;
      for (Map<String, Object> part : group) {
        if (part.get("description") instanceof String text && !text.isEmpty()) {
          description = text;
        }
        Map<String, Object> properties = Json.asObject(part.get("properties"));
        if (properties != null) {
          Map<String, Object> type = Json.asObject(properties.get("component"));
          if (type != null && type.get("const") instanceof String value) {
            componentConst = value;
          }
          properties.forEach((property, schema) -> {
            if (!skipped.contains(property) && !isCatalogIdOfTheEnvelope(property, schema)) {
              merged.put(property, schema);
            }
          });
        }
        if (part.get("required") instanceof List<?> names) {
          for (Object item : names) {
            if (item instanceof String property && !skipped.contains(property)) {
              required.add(property);
            }
          }
        }
      }
    }

    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("id", newReference("ComponentId"));
    // The properties of the component come first, and the properties of the common types come
    // last.
    for (List<Map<String, Object>> group :
        List.of(parts.specificInline(), parts.local(), parts.commonInline(), parts.mixins())) {
      for (Map<String, Object> part : group) {
        Map<String, Object> declared = Json.asObject(part.get("properties"));
        if (declared != null) {
          declared.keySet().stream()
              .filter(merged::containsKey)
              .forEach(property -> properties.putIfAbsent(property, merged.get(property)));
        }
      }
    }
    Map<String, Object> type = new LinkedHashMap<>();
    type.put("const", componentConst);
    properties.put("component", type);

    Map<String, Object> flat = new LinkedHashMap<>();
    flat.put("type", "object");
    component.forEach((keyword, value) -> {
      if (!MERGED_KEYWORDS.contains(keyword)) {
        flat.put(keyword, value);
      }
    });
    if (description != null) {
      flat.put("description", description);
    }
    flat.put("properties", properties);
    List<Object> requiredNames = new ArrayList<>();
    requiredNames.add("id");
    requiredNames.addAll(required);
    requiredNames.add("component");
    flat.put("required", requiredNames);
    if (!parts.unmerged().isEmpty()) {
      flat.put("allOf", parts.unmerged());
    }
    Object closed =
        component.containsKey("unevaluatedProperties")
            ? component.get("unevaluatedProperties")
            : component.get("additionalProperties");
    if (closed != null) {
      flat.put("unevaluatedProperties", closed);
    }
    for (String keyword : List.of("allowedParents", "allowedChildren")) {
      if (component.get(keyword) instanceof List<?> names && !names.isEmpty()) {
        flat.put(keyword, names);
      }
    }
    return flat;
  }

  /**
   * Tells if a property is the {@code @catalogId} of the component envelope, which a component can
   * write inline. The message schema supplies this property.
   */
  private static boolean isCatalogIdOfTheEnvelope(String property, Object schema) {
    return property.equals("@catalogId")
        && schema instanceof Map<?, ?> map
        && "string".equals(map.get("type"));
  }

  /**
   * Collects the parts of a schema and of the schemas in its {@code allOf}.
   *
   * @param visited the references to definitions of the catalog that the collection followed
   * @param localMixins receives the name of each definition of the catalog that an {@code allOf}
   *     refers to
   */
  private static Parts collect(
      Map<String, Object> schema,
      Map<String, Object> definitions,
      Set<String> visited,
      Set<String> localMixins,
      List<Object> unmerged) {
    Parts parts = new Parts(unmerged);
    if (schema.get("allOf") instanceof List<?> allOf) {
      for (Object item : allOf) {
        Map<String, Object> sub = Json.asObject(item);
        if (sub == null) {
          continue;
        }
        if (!(sub.get(REF) instanceof String ref)) {
          Parts nested = collect(sub, definitions, visited, localMixins, unmerged);
          parts.mixins().addAll(nested.mixins());
          parts.local().addAll(nested.local());
          parts.commonInline().addAll(nested.commonInline());
          parts.specificInline().addAll(nested.specificInline());
          Map<String, Object> other = new LinkedHashMap<>(sub);
          other.keySet().removeAll(MERGED_KEYWORDS);
          if (!other.isEmpty()) {
            unmerged.add(other);
          }
          continue;
        }
        String name = SchemaReferences.definitionName(ref);
        if (name != null && definitions.containsKey(name)) {
          localMixins.add(name);
          Map<String, Object> target = Json.asObject(definitions.get(name));
          if (visited.add(ref) && target != null) {
            Parts nested = collect(target, definitions, visited, localMixins, unmerged);
            parts.mixins().addAll(nested.mixins());
            parts.local().addAll(nested.local());
            parts.local().addAll(nested.commonInline());
            parts.local().addAll(nested.specificInline());
          }
          continue;
        }
        Map<String, Object> mixin =
            name == null ? null : Json.asObject(SchemaReferences.commonTypes().get(name));
        if (mixin != null && mixin.get("properties") instanceof Map<?, ?>) {
          Map<String, Object> part = new LinkedHashMap<>();
          part.put("properties", SchemaReferences.localize(mixin.get("properties"), type -> {}));
          if (mixin.get("required") instanceof List<?> names) {
            part.put("required", names);
          }
          parts.mixins().add(part);
        } else {
          unmerged.add(sub);
        }
      }
    }
    if (schema.containsKey("properties")
        || schema.containsKey("required")
        || schema.containsKey("description")) {
      Map<String, Object> properties = Json.asObject(schema.get("properties"));
      if (properties != null && properties.containsKey("component")) {
        parts.specificInline().add(schema);
      } else {
        parts.commonInline().add(schema);
      }
    }
    return parts;
  }

  /**
   * Removes each definition of the catalog that was only a mixin of a component. After the merge,
   * no reference names such a definition.
   */
  private static void removeMergedMixins(
      Set<String> localMixins, Map<String, Object> definitions, Object roots) {
    if (localMixins.isEmpty()) {
      return;
    }
    Deque<String> pending = new ArrayDeque<>();
    SchemaReferences.collectDefinitionNames(roots, pending);
    SchemaReferences.collectDefinitionNames(definitions.get(THEME), pending);
    Set<String> reachable = new HashSet<>();
    while (!pending.isEmpty()) {
      String name = pending.removeFirst();
      if (definitions.containsKey(name) && reachable.add(name)) {
        SchemaReferences.collectDefinitionNames(definitions.get(name), pending);
      }
    }
    for (String name : localMixins) {
      if (!reachable.contains(name) && !name.equals(THEME)) {
        definitions.remove(name);
      }
    }
  }

  /**
   * Gives the description of a common type to each reference to it that has no description. The
   * golden files have these descriptions.
   */
  private static void addDescriptions(Object node, Map<String, Object> commonTypes) {
    if (node instanceof List<?> list) {
      list.forEach(item -> addDescriptions(item, commonTypes));
      return;
    }
    Map<String, Object> schema = Json.asObject(node);
    if (schema == null) {
      return;
    }
    String name = SchemaReferences.definitionName(schema.get(REF));
    Map<String, Object> type = name == null ? null : Json.asObject(commonTypes.get(name));
    if (type != null
        && !schema.containsKey("description")
        && type.get("description") instanceof String description) {
      schema.put("description", description);
    }
    schema.values().forEach(value -> addDescriptions(value, commonTypes));
  }

  /**
   * Copies into the definitions each common type that the schema uses, and each common type that
   * these types use.
   */
  private static void addCommonTypes(
      Map<String, Object> schema,
      Map<String, Object> definitions,
      Map<String, Object> commonTypes) {
    Deque<String> needed = new ArrayDeque<>();
    Set<String> names = new TreeSet<>();
    SchemaReferences.collectDefinitionNames(schema, names);
    needed.addAll(names);
    while (!needed.isEmpty()) {
      String name = needed.removeFirst();
      if (definitions.containsKey(name) || !commonTypes.containsKey(name)) {
        continue;
      }
      definitions.put(name, commonTypes.get(name));
      SchemaReferences.collectDefinitionNames(commonTypes.get(name), needed);
    }
  }

  /**
   * Writes in the {@code args} of a function call that they accept only the listed arguments, if
   * the object does not say. The keyword is always {@code unevaluatedProperties}.
   */
  private static void closeArguments(Map<String, Object> function) {
    Map<String, Object> properties =
        function == null ? null : Json.asObject(function.get("properties"));
    if (properties == null || !properties.containsKey("@call")) {
      return;
    }
    Map<String, Object> args = Json.asObject(properties.get("args"));
    if (args != null && "object".equals(args.get("type")) && !args.containsKey(REF)) {
      Object additional = args.remove("additionalProperties");
      args.putIfAbsent("unevaluatedProperties", additional != null ? additional : false);
    }
  }

  private static Map<String, Object> newReference(String commonType) {
    Map<String, Object> reference = new LinkedHashMap<>();
    reference.put(REF, "#/$defs/" + Json.escapePointer(commonType));
    return reference;
  }
}
