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
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.Json;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A set of component definitions and function definitions that an agent and a renderer agree on.
 * The {@link #id()} identifies the catalog. A catalog cannot change.
 *
 * <p>A catalog keeps each definition as its document writes it. Thus {@link #fromJson(Map)} and
 * then {@link #toJson()} return the same document.
 *
 * @param <C> the type of the components. It is {@link ComponentApi} for a catalog that only
 *     describes its components. A framework adapter uses its own type for a catalog that can show
 *     them.
 * @param <F> the type of the functions. It is {@link FunctionApi} for a catalog that only describes
 *     its functions. A catalog that can run them uses the type of the implementations.
 */
public final class Catalog<C extends ComponentApi, F extends FunctionApi> {
  /**
   * The top-level members that describe one catalog. A derived catalog does not get these members.
   */
  private static final List<String> SOURCE_ONLY_KEYS =
      List.of("$id", "title", "description", "instructions");

  private static final Set<String> ENTRY_KEYS =
      Set.of("catalogId", "components", "functions", "$defs");

  private final String id;
  private final ProtocolVersion protocolVersion;
  private final Map<String, C> components;
  private final Map<String, F> functions;
  private final DocumentLayout layout;

  /** The result of {@link #validationSchema()}, or null before the first call. */
  private Map<String, Object> validationSchema;

  /**
   * Makes a catalog that has components only, and that declares no protocol version.
   *
   * @param id the ID of the catalog. It is usually a URI.
   * @param components the components, in the sequence of their declaration
   * @throws A2uiCatalogError if the ID is empty, or if two components have the same name
   */
  public Catalog(String id, Collection<? extends C> components) {
    this(id, components, List.of());
  }

  /**
   * Makes a catalog that declares no protocol version.
   *
   * @param id the ID of the catalog. It is usually a URI.
   * @param components the components, in the sequence of their declaration
   * @param functions the functions, in the sequence of their declaration
   * @throws A2uiCatalogError if the ID is empty, or if two entries of one type have the same name
   */
  public Catalog(String id, Collection<? extends C> components, Collection<? extends F> functions) {
    this(id, components, functions, null, null);
  }

  /**
   * Makes a catalog.
   *
   * @param id the ID of the catalog. It is usually a URI.
   * @param components the components, in the sequence of their declaration
   * @param functions the functions, in the sequence of their declaration
   * @param themeSchema the schema of the theme that the catalog accepts, or null if there is none
   * @param protocolVersion the protocol version that the catalog is for, or null if the catalog
   *     declares none
   * @throws A2uiCatalogError if the ID is empty, if two entries of one type have the same name, or
   *     if the theme schema is not JSON
   */
  public Catalog(
      String id,
      Collection<? extends C> components,
      Collection<? extends F> functions,
      Map<String, ?> themeSchema,
      ProtocolVersion protocolVersion) {
    this(
        id,
        protocolVersion,
        components,
        functions,
        DocumentLayout.forNewCatalog(id, themeSchema, protocolVersion));
  }

  private Catalog(
      String id,
      ProtocolVersion protocolVersion,
      Collection<? extends C> components,
      Collection<? extends F> functions,
      DocumentLayout layout) {
    this.id = Objects.requireNonNull(id, "id");
    if (id.isEmpty()) {
      throw new A2uiCatalogError("A catalog id must not be empty.");
    }
    this.protocolVersion = protocolVersion;
    this.components = index("component", components, ComponentApi::name);
    this.functions = index("function", functions, FunctionApi::name);
    this.layout = layout;
  }

  /**
   * Reads a catalog document that declares its ID. See {@link #fromJson(Map, String,
   * ProtocolVersion)}.
   *
   * @param document the catalog document as plain JSON values
   * @return the catalog
   * @throws A2uiCatalogError if the document is not correct
   * @throws A2uiValidationError if a key of the {@code metadata.extensions} of a v1.0 component is
   *     not a UAX #31 identifier
   */
  public static Catalog<ComponentApi, FunctionApi> fromJson(Map<String, ?> document) {
    return fromJson(document, null, null);
  }

  /**
   * Reads a catalog document.
   *
   * <p>A document contains signatures only. Thus the result describes its components and its
   * functions, and it cannot show them or run them.
   *
   * <p>{@code $defs.anyComponent} or {@code $defs.anyFunction} can be a union that lists only some
   * of the entries of the document. Then the catalog defines only the listed entries.
   *
   * <p>The method also applies the rules that v1.0 adds to a v1.0 document:
   *
   * <ul>
   *   <li>Each {@code $ref} stays in the document or names {@code common_types.json}.
   *   <li>The names of the components, properties, functions and arguments are UAX #31 identifiers.
   *   <li>No function name starts with {@code @}.
   *   <li>Only the renderer can call a function that requires user activation.
   * </ul>
   *
   * <p>The method does not download a document.
   *
   * @param document the catalog document as plain JSON values
   * @param catalogId the ID of the catalog. It replaces the ID that the document declares. A null
   *     value keeps the ID of the document, which must then declare one.
   * @param protocolVersion the version to use if the document declares none, or null
   * @return the catalog
   * @throws A2uiCatalogError if the document is not JSON, has a member of an incorrect type, has no
   *     ID, names a protocol version that this SDK does not know, or does not obey a v1.0 rule
   * @throws A2uiValidationError if a key of the {@code metadata.extensions} of a v1.0 component is
   *     not a UAX #31 identifier
   */
  public static Catalog<ComponentApi, FunctionApi> fromJson(
      Map<String, ?> document, String catalogId, ProtocolVersion protocolVersion) {
    Objects.requireNonNull(document, "document");
    Map<String, Object> json;
    try {
      json = Json.freezeObject(document);
    } catch (IllegalArgumentException e) {
      throw new A2uiCatalogError("The catalog document is not JSON: " + e.getMessage(), e);
    }

    Object declaredId = catalogId != null ? catalogId : json.get("catalogId");
    if (!(declaredId instanceof String id) || id.isEmpty()) {
      throw new A2uiCatalogError(
          "A catalog document has no 'catalogId' that is a non-empty string, and the call gives"
              + " none: catalog_id must be provided or exist in catalog schema.");
    }
    ProtocolVersion version =
        switch (json.get("protocolVersion")) {
          case null -> protocolVersion;
          case String declared ->
              ProtocolVersion.tryParse(declared)
                  .orElseThrow(() ->
                      new A2uiCatalogError(
                          "Catalog '"
                              + id
                              + "' declares protocol version '"
                              + declared
                              + "', which this SDK does not know."));
          default ->
              throw new A2uiCatalogError(
                  "The 'protocolVersion' of catalog '" + id + "' must be a string.");
        };

    Map<String, Object> rawComponents = section(json, "components", id, true);
    Map<String, Object> rawFunctions = section(json, "functions", id, true);
    Map<String, Object> rawDefinitions = section(json, "$defs", id, false);
    if (version != null && version.isAtLeast(ProtocolVersion.V1_0)) {
      CatalogDocumentRules.check(json, id);
    }

    Map<String, Object> definitions = rawDefinitions == null ? Map.of() : rawDefinitions;
    List<ComponentApi> components =
        entries(rawComponents, definitions.get("anyComponent"), "#/components/", ComponentApi::new);
    List<FunctionApi> functions =
        entries(rawFunctions, definitions.get("anyFunction"), "#/functions/", FunctionApi::new);

    // The catalog writes a union from its entries only if the union is equal to the one that this
    // class calculates. It keeps each different definition of the name as the document writes it.
    // An example is an open `anyComponent`.
    boolean computesComponentUnion =
        componentUnion(names(components, ComponentApi::name))
            .equals(definitions.get("anyComponent"));
    boolean computesFunctionUnion =
        functionUnion(names(functions, FunctionApi::name)).equals(definitions.get("anyFunction"));

    Map<String, Object> template = new LinkedHashMap<>(json);
    template.keySet().removeAll(ENTRY_KEYS);
    Map<String, Object> keptDefinitions = new LinkedHashMap<>(definitions);
    if (computesComponentUnion) {
      keptDefinitions.remove("anyComponent");
    }
    if (computesFunctionUnion) {
      keptDefinitions.remove("anyFunction");
    }
    return new Catalog<>(
        id,
        version,
        components,
        functions,
        new DocumentLayout(
            Collections.unmodifiableMap(template),
            Collections.unmodifiableMap(keptDefinitions),
            json.containsKey("catalogId"),
            rawComponents != null,
            rawFunctions != null,
            rawDefinitions != null,
            computesComponentUnion,
            computesFunctionUnion));
  }

  /**
   * Gets the ID of the catalog.
   *
   * @return the ID. It is usually a URI.
   */
  public String id() {
    return id;
  }

  /**
   * Gets the protocol version that the catalog is for.
   *
   * @return the version, or an empty result if the catalog declares none. Such a catalog is from
   *     before v1.0.
   */
  public Optional<ProtocolVersion> protocolVersion() {
    return Optional.ofNullable(protocolVersion);
  }

  /**
   * Gets the components of the catalog.
   *
   * @return the components by name, in the sequence of their declaration. The map is read-only.
   */
  public Map<String, C> components() {
    return components;
  }

  /**
   * Gets the functions of the catalog.
   *
   * @return the functions by name, in the sequence of their declaration. The map is read-only.
   */
  public Map<String, F> functions() {
    return functions;
  }

  /**
   * Gets the schema of the theme that the catalog accepts.
   *
   * @return the schema, or an empty result if the catalog declares none
   */
  public Optional<Map<String, Object>> themeSchema() {
    Map<String, Object> theme = Json.asObject(layout.template().get("theme"));
    return Optional.ofNullable(
        theme != null ? theme : Json.asObject(layout.definitions().get("theme")));
  }

  /**
   * Gets the instructions that tell an agent how to use this catalog.
   *
   * @return the instructions, or an empty result if the catalog has none
   */
  public Optional<String> instructions() {
    return layout.template().get("instructions") instanceof String text
        ? Optional.of(text)
        : Optional.empty();
  }

  /**
   * Writes the catalog as a catalog document.
   *
   * <p>A catalog from {@link #fromJson(Map)} writes the document that it read. The references to
   * other documents stay as the document wrote them.
   *
   * @return the document as plain JSON values. It is read-only.
   */
  public Map<String, Object> toJson() {
    Map<String, Object> document = new LinkedHashMap<>(layout.template());
    if (layout.writesCatalogId()) {
      document.put("catalogId", id);
    }
    if (layout.writesComponents() || !components.isEmpty()) {
      Map<String, Object> schemas = new LinkedHashMap<>();
      components.forEach((name, component) -> schemas.put(name, component.schema()));
      document.put("components", Collections.unmodifiableMap(schemas));
    }
    if (layout.writesFunctions() || !functions.isEmpty()) {
      Map<String, Object> schemas = new LinkedHashMap<>();
      functions.forEach((name, function) -> schemas.put(name, function.schema()));
      document.put("functions", Collections.unmodifiableMap(schemas));
    }
    Map<String, Object> definitions = new LinkedHashMap<>(layout.definitions());
    if (layout.computesComponentUnion()) {
      definitions.put("anyComponent", componentUnion(components.keySet()));
    }
    if (layout.computesFunctionUnion()) {
      definitions.put("anyFunction", functionUnion(functions.keySet()));
    }
    if (layout.writesDefinitions() || !definitions.isEmpty()) {
      document.put("$defs", Collections.unmodifiableMap(definitions));
    }
    return Collections.unmodifiableMap(document);
  }

  /**
   * Gets the catalog as one self-contained JSON Schema document. A validator can use this schema
   * and no other document.
   *
   * <p>The schema is not the document that {@link #toJson()} writes:
   *
   * <ul>
   *   <li>Each {@code $ref} is local.
   *   <li>Each component is one object schema. Its {@code properties} and its {@code required} have
   *       the content of the {@code allOf} mixins of the component, and the {@code id} that each
   *       component has.
   *   <li>{@code $defs} has the definitions of the catalog, the unions {@code anyComponent} and
   *       {@code anyFunction}, and each common type that the schema uses.
   *   <li>The theme of the catalog is {@code $defs.theme}.
   *   <li>The {@code args} of a function accept only the arguments that they list, unless the
   *       function says differently.
   * </ul>
   *
   * <p>The catalog makes the schema at the first call. Each subsequent call returns the same
   * object.
   *
   * @return the schema as plain JSON values. It is read-only.
   * @throws A2uiCatalogError if the catalog is not a v1.0 catalog, if it refers to a common type
   *     that does not exist, or if its {@code $defs} has the name of a common type that the schema
   *     uses
   */
  public Map<String, Object> validationSchema() {
    if (validationSchema == null) {
      validationSchema =
          ValidationSchemaBuilder.build(
              id,
              protocolVersion,
              toJson(),
              componentUnion(components.keySet()),
              functionUnion(functions.keySet()));
    }
    return validationSchema;
  }

  /**
   * Makes a catalog with a new ID that has all the entries of this catalog. See {@link
   * #derive(String, Collection, Collection, Collection, Collection, String)}.
   *
   * @param catalogId the ID of the derived catalog
   * @return the derived catalog
   * @throws A2uiCatalogError if the ID is empty
   */
  public Catalog<C, F> derive(String catalogId) {
    return derive(catalogId, null, List.of(), null, List.of(), null);
  }

  /**
   * Makes a catalog with a new ID that has some components and all the functions of this catalog.
   * See {@link #derive(String, Collection, Collection, Collection, Collection, String)}.
   *
   * @param catalogId the ID of the derived catalog
   * @param components the names of the components to keep, or null to keep all of them
   * @return the derived catalog
   * @throws A2uiCatalogError if the ID is empty, or if a name is not a component of this catalog
   */
  public Catalog<C, F> derive(String catalogId, Collection<String> components) {
    return derive(catalogId, components, List.of(), null, List.of(), null);
  }

  /**
   * Makes a catalog with a new ID from entries of this catalog and from new entries.
   *
   * <p>The derived catalog keeps the protocol version, the theme and the shared definitions of this
   * catalog. It does not keep the {@code $id}, the title, the description and the instructions,
   * because they describe this catalog.
   *
   * @param catalogId the ID of the derived catalog
   * @param components the names of the components to keep, or null to keep all of them
   * @param addComponents the components to add. A component with the name of a kept component
   *     replaces it.
   * @param functions the names of the functions to keep, or null to keep all of them
   * @param addFunctions the functions to add. A function with the name of a kept function replaces
   *     it.
   * @param instructions the instructions that tell an agent how to use the derived catalog, or null
   *     if there are none
   * @return the derived catalog
   * @throws A2uiCatalogError if the ID is empty, or if a name is not an entry of this catalog
   */
  public Catalog<C, F> derive(
      String catalogId,
      Collection<String> components,
      Collection<? extends C> addComponents,
      Collection<String> functions,
      Collection<? extends F> addFunctions,
      String instructions) {
    Objects.requireNonNull(catalogId, "catalogId");
    Map<String, Object> template = new LinkedHashMap<>(layout.template());
    template.keySet().removeAll(SOURCE_ONLY_KEYS);
    if (instructions != null) {
      template.put("instructions", instructions);
    }
    return new Catalog<>(
        catalogId,
        protocolVersion,
        pick(
            "component", catalogId, this.components, components, addComponents, ComponentApi::name),
        pick("function", catalogId, this.functions, functions, addFunctions, FunctionApi::name),
        layout.forDerivedCatalog(Collections.unmodifiableMap(template)));
  }

  @Override
  public String toString() {
    return "Catalog(" + id + ")";
  }

  private <E> Collection<E> pick(
      String kind,
      String catalogId,
      Map<String, E> own,
      Collection<String> names,
      Collection<? extends E> added,
      Function<? super E, String> nameOf) {
    Map<String, E> picked = new LinkedHashMap<>();
    for (String name : names == null ? own.keySet() : names) {
      E entry = own.get(name);
      if (entry == null) {
        throw new A2uiCatalogError(
            "Catalog '"
                + id
                + "' has no "
                + kind
                + " '"
                + name
                + "' to derive catalog '"
                + catalogId
                + "' from.");
      }
      picked.put(name, entry);
    }
    for (E entry : added) {
      picked.put(nameOf.apply(entry), entry);
    }
    return picked.values();
  }

  /**
   * Gets the object at a top-level key of a document.
   *
   * @param entriesAreObjects true if each value of the object must also be an object
   * @return the object, or null if the document does not have the key
   */
  private static Map<String, Object> section(
      Map<String, Object> json, String key, String catalogId, boolean entriesAreObjects) {
    Object raw = json.get(key);
    if (raw == null) {
      return null;
    }
    Map<String, Object> section = Json.asObject(raw);
    if (section == null) {
      throw new A2uiCatalogError(
          "The '" + key + "' of catalog '" + catalogId + "' must be an object.");
    }
    if (entriesAreObjects) {
      section.forEach((name, definition) -> {
        if (!(definition instanceof Map<?, ?>)) {
          throw new A2uiCatalogError(
              "The definition of '"
                  + name
                  + "' in the '"
                  + key
                  + "' of catalog '"
                  + catalogId
                  + "' must be an object.");
        }
      });
    }
    return section;
  }

  /**
   * Makes the entries of one section of a document.
   *
   * @param section the definitions by name, or null if the document does not have the section
   * @param union the definition of the union of the section, or null
   * @param prefix the start of the references that the union contains
   */
  private static <E> List<E> entries(
      Map<String, Object> section,
      Object union,
      String prefix,
      BiFunction<String, Map<String, Object>, E> create) {
    List<E> entries = new ArrayList<>();
    if (section == null) {
      return entries;
    }
    Set<String> listed = unionMembers(union, prefix);
    section.forEach((name, definition) -> {
      if (listed == null || listed.contains(name)) {
        entries.add(create.apply(name, Json.asObject(definition)));
      }
    });
    return entries;
  }

  /**
   * Gets the entry names that a {@code oneOf} union of references lists.
   *
   * @return the names, or null if the value is not such a union. Then the value does not limit the
   *     entries of the catalog.
   */
  private static Set<String> unionMembers(Object union, String prefix) {
    Map<String, Object> schema = Json.asObject(union);
    if (schema == null || !(schema.get("oneOf") instanceof List<?> members)) {
      return null;
    }
    Set<String> names = new LinkedHashSet<>();
    for (Object member : members) {
      Map<String, Object> reference = Json.asObject(member);
      if (reference != null
          && reference.get("$ref") instanceof String ref
          && ref.startsWith(prefix)) {
        names.add(Json.unescapePointer(ref.substring(prefix.length())));
      }
    }
    return names;
  }

  private static Map<String, Object> componentUnion(Collection<String> names) {
    if (names.isEmpty()) {
      return Map.of("not", Map.of());
    }
    Map<String, Object> union = new LinkedHashMap<>();
    union.put("oneOf", references("#/components/", names));
    union.put("discriminator", Map.of("propertyName", "component"));
    return Collections.unmodifiableMap(union);
  }

  private static Map<String, Object> functionUnion(Collection<String> names) {
    if (names.isEmpty()) {
      return Map.of("not", Map.of());
    }
    return Map.of("oneOf", references("#/functions/", names));
  }

  private static List<Object> references(String prefix, Collection<String> names) {
    List<Object> references = new ArrayList<>(names.size());
    for (String name : names) {
      references.add(Map.of("$ref", prefix + Json.escapePointer(name)));
    }
    return Collections.unmodifiableList(references);
  }

  private static <E> List<String> names(List<E> entries, Function<? super E, String> nameOf) {
    return entries.stream().map(nameOf).toList();
  }

  private static <E> Map<String, E> index(
      String kind, Collection<? extends E> entries, Function<? super E, String> nameOf) {
    Objects.requireNonNull(entries, kind + "s");
    Map<String, E> byName = new LinkedHashMap<>();
    for (E entry : entries) {
      String name = nameOf.apply(entry);
      if (byName.put(name, entry) != null) {
        throw new A2uiCatalogError(
            "A catalog cannot define the " + kind + " '" + name + "' twice.");
      }
    }
    return Collections.unmodifiableMap(byName);
  }

  /**
   * The parts of a catalog document that are not the entries of the catalog.
   *
   * @param template the top-level members of the document. It does not contain {@code catalogId},
   *     {@code components}, {@code functions} and {@code $defs}.
   * @param definitions the {@code $defs} of the document, without the unions that the catalog
   *     calculates
   * @param writesCatalogId true if the document has the member {@code catalogId}
   * @param writesComponents true if the document has the member {@code components} also when the
   *     catalog has no components
   * @param writesFunctions true if the document has the member {@code functions} also when the
   *     catalog has no functions
   * @param writesDefinitions true if the document has the member {@code $defs} also when there are
   *     no definitions
   * @param computesComponentUnion true if {@code $defs.anyComponent} is the union of the components
   *     of the catalog. The catalog then writes the union from its components.
   * @param computesFunctionUnion true if {@code $defs.anyFunction} is the union of the functions of
   *     the catalog. The catalog then writes the union from its functions.
   */
  private record DocumentLayout(
      Map<String, Object> template,
      Map<String, Object> definitions,
      boolean writesCatalogId,
      boolean writesComponents,
      boolean writesFunctions,
      boolean writesDefinitions,
      boolean computesComponentUnion,
      boolean computesFunctionUnion) {
    static DocumentLayout forNewCatalog(
        String id, Map<String, ?> themeSchema, ProtocolVersion protocolVersion) {
      Map<String, Object> template = new LinkedHashMap<>();
      if (protocolVersion != null) {
        template.put("protocolVersion", protocolVersion.wireValue());
      }
      if (themeSchema != null) {
        try {
          template.put("theme", Json.freezeObject(themeSchema));
        } catch (IllegalArgumentException e) {
          throw new A2uiCatalogError(
              "The theme schema of catalog '" + id + "' is not JSON: " + e.getMessage(), e);
        }
      }
      // A v1.0 document gives the names of its entries in the two unions. The validation of a
      // message uses these unions.
      boolean isV1 = protocolVersion != null && protocolVersion.isAtLeast(ProtocolVersion.V1_0);
      return new DocumentLayout(
          Collections.unmodifiableMap(template), Map.of(), true, true, false, false, isV1, isV1);
    }

    DocumentLayout forDerivedCatalog(Map<String, Object> newTemplate) {
      return new DocumentLayout(
          newTemplate,
          definitions,
          true,
          writesComponents,
          writesFunctions,
          writesDefinitions,
          computesComponentUnion,
          computesFunctionUnion);
    }
  }
}
