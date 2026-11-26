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

package com.google.a2ui.core.processing;

import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.common.Reactive;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiDataError;
import com.google.a2ui.core.exceptions.A2uiIntegrityError;
import com.google.a2ui.core.exceptions.A2uiRecursionError;
import com.google.a2ui.core.exceptions.A2uiStateError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.CatalogChecks;
import com.google.a2ui.core.internal.CatalogSchemaIndex;
import com.google.a2ui.core.internal.ComponentGraph;
import com.google.a2ui.core.internal.Json;
import com.google.a2ui.core.state.ComponentModel;
import com.google.a2ui.core.state.SurfaceComponentsModel;
import com.google.a2ui.core.state.SurfaceGroupModel;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.core.validation.ValidationConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the v1.0 messages of the agent, examines them and applies them to the surfaces of {@link
 * #model()}.
 *
 * <p>The processor examines a full message before it changes a surface. Thus a message that it
 * rejects changes nothing. In a batch, the messages before the rejected message stay applied.
 *
 * <p>The processor handles {@code createSurface}, {@code updateComponents}, {@code updateDataModel}
 * and {@code deleteSurface}. It does not yet handle {@code callRendererFunction} and {@code
 * agentFunctionResponse}.
 *
 * <p>A processor is not thread-safe. Use a processor and its models from one thread.
 *
 * @param <C> the type of the components of the catalogs
 */
public final class MessageProcessor<C extends ComponentApi> {
  /** The keys of a component that are not its properties. */
  private static final Set<String> ENVELOPE_KEYS =
      Set.of("id", "component", "@catalogId", "@metadata", "@accessibility", "@checks");

  /** The envelope keys that v1.0 accepts without their {@code @}, apart from {@code catalogId}. */
  private static final List<String> LEGACY_ENVELOPE_KEYS =
      List.of("metadata", "accessibility", "checks");

  /** The keys whose text value is a JSON Pointer. */
  private static final List<String> PATH_KEYS = List.of("path", "@path");

  private final SurfaceGroupModel<C> model = new SurfaceGroupModel<>();
  private final Map<String, Catalog<C, ?>> catalogs = new LinkedHashMap<>();
  private final Map<String, Catalog<C, ?>> catalogsView = Collections.unmodifiableMap(catalogs);
  private final ValidationConfig validationConfig;
  private final Map<Catalog<?, ?>, CatalogChecks> checks = new IdentityHashMap<>();
  private final Map<Catalog<?, ?>, CatalogSchemaIndex> indexes = new IdentityHashMap<>();

  /**
   * Makes a processor that uses {@link ValidationConfig#STRICT}.
   *
   * @param catalogs each catalog that this renderer supports. A surface can use the v1.0 catalogs.
   * @throws A2uiCatalogError if two catalogs have the same ID
   */
  public MessageProcessor(Collection<? extends Catalog<C, ?>> catalogs) {
    this(catalogs, ValidationConfig.STRICT);
  }

  /**
   * Makes a processor.
   *
   * @param catalogs each catalog that this renderer supports. A surface can use the v1.0 catalogs.
   * @param validationConfig the problems to accept. A null value stops the catalog checks and the
   *     graph checks, for a renderer that trusts its agent. The processor continues to examine the
   *     message envelope and to resolve each catalog.
   * @throws A2uiCatalogError if two catalogs have the same ID
   */
  public MessageProcessor(
      Collection<? extends Catalog<C, ?>> catalogs, ValidationConfig validationConfig) {
    Objects.requireNonNull(catalogs, "catalogs");
    this.validationConfig = validationConfig;
    for (Catalog<C, ?> catalog : catalogs) {
      if (this.catalogs.put(catalog.id(), catalog) != null) {
        throw new A2uiCatalogError("Two catalogs have the id '" + catalog.id() + "'.");
      }
    }
  }

  /**
   * Gets the surfaces that this processor made and did not delete.
   *
   * @return the model of the surfaces
   */
  public SurfaceGroupModel<C> model() {
    return model;
  }

  /**
   * Gets the catalogs that this processor supports.
   *
   * @return the catalogs by ID. The map is read-only.
   */
  public Map<String, Catalog<C, ?>> catalogs() {
    return catalogsView;
  }

  /**
   * Processes a payload with the validation configuration of the processor. See {@link
   * #processMessages(Object, ValidationConfig)}.
   *
   * @param payload one message, a list of messages, or a {@code {"messages": [...]}} wrapper, as
   *     plain JSON values
   * @throws A2uiValidationError if a message is not correct
   * @throws A2uiCatalogError if a message needs a catalog that the processor or the surface does
   *     not have
   * @throws A2uiDataError if an {@code updateDataModel} message cannot write its path
   */
  public void processMessages(Object payload) {
    processMessages(payload, validationConfig);
  }

  /**
   * Processes a payload.
   *
   * <p>The payload is one message, a list of messages, or a {@code {"messages": [...]}} wrapper. A
   * list can contain lists and wrappers. A null payload and an empty list contain no message. The
   * processor ignores the other keys of a wrapper, but it examines their depth and their paths.
   *
   * @param payload the payload, as plain JSON values
   * @param validationConfig the problems to accept. It replaces the configuration of the processor
   *     for this call only. A null value stops the catalog checks and the graph checks.
   * @throws A2uiValidationError if a message is not an object, has an incorrect envelope, or does
   *     not agree with the schema of a catalog
   * @throws A2uiIntegrityError if a message names a surface that does not exist, makes a surface
   *     that exists, or gives a surface a component graph that is not correct. This error is an
   *     {@code A2uiValidationError}.
   * @throws A2uiRecursionError if a message or the containers around it nest values deeper than 50
   *     levels. This error is an {@code A2uiValidationError}.
   * @throws A2uiCatalogError if a message needs a catalog that the processor or the surface does
   *     not have
   * @throws A2uiDataError if an {@code updateDataModel} message cannot write its path
   * @throws A2uiStateError for a {@code callRendererFunction} or an {@code agentFunctionResponse}
   *     message, which this SDK does not handle yet
   */
  public void processMessages(Object payload, ValidationConfig validationConfig) {
    List<Map<String, Object>> messages = new ArrayList<>();
    unwrap(payload, 0, messages);
    for (Map<String, Object> message : messages) {
      process(message, validationConfig);
    }
  }

  /** Deletes each surface and removes each listener. */
  public void dispose() {
    model.dispose();
  }

  /**
   * Collects the messages of a payload.
   *
   * @param depth the number of containers around the payload. A list adds one level. A wrapper adds
   *     two levels: the wrapper and its list.
   */
  private void unwrap(Object payload, int depth, List<Map<String, Object>> messages) {
    if (depth > ComponentGraph.MAX_DEPTH) {
      throw new A2uiRecursionError(
          "Global recursion limit exceeded: the containers around the messages nest deeper than "
              + ComponentGraph.MAX_DEPTH
              + " levels.");
    }
    switch (payload) {
      case null -> {}
      case Collection<?> list -> list.forEach(item -> unwrap(item, depth + 1, messages));
      case Map<?, ?> map when map.get("messages") instanceof Collection<?> wrapped -> {
        // The other keys of a wrapper contain no message. They obey the same limits as a message.
        Map<Object, Object> siblings = new LinkedHashMap<>(map);
        siblings.remove("messages");
        checkValue("the message wrapper", frozen(siblings), depth);
        unwrap(wrapped, depth + 1, messages);
      }
      case Map<?, ?> map -> messages.add(frozen(map));
      default ->
          throw new A2uiValidationError(
              "An A2UI message must be a JSON object, but got: " + payload);
    }
  }

  private static Map<String, Object> frozen(Map<?, ?> raw) {
    try {
      return Json.asObject(Json.freeze(raw));
    } catch (IllegalArgumentException e) {
      throw new A2uiValidationError("The message is not JSON: " + e.getMessage(), e);
    }
  }

  private void process(Map<String, Object> received, ValidationConfig config) {
    Map<String, Object> message = received;
    List<String> actions =
        message.keySet().stream().filter(MessageSchemas.ACTIONS::containsKey).toList();
    checkValue(
        actions.size() == 1 ? "the " + actions.getFirst() + " message" : "the message", message, 0);

    Object version = message.get("version");
    if (version == null) {
      throw new A2uiValidationError("Missing required version field in message.");
    }
    if (!(version instanceof String text)
        || ProtocolVersion.tryParse(text).orElse(null) != ProtocolVersion.V1_0) {
      throw new A2uiValidationError(
          "Unsupported protocol version '" + version + "': this SDK processes version 1.0.");
    }
    if (actions.size() > 1) {
      throw new A2uiValidationError(
          "Message contains multiple conflicting update actions: " + actions);
    }
    if (actions.isEmpty()) {
      Optional<String> otherVersion =
          message.keySet().stream()
              .filter(MessageSchemas.OTHER_VERSION_ACTIONS::contains)
              .findFirst();
      throw new A2uiValidationError(
          otherVersion
              .map(key ->
                  "The action '"
                      + key
                      + "' is not supported in protocol version 1.0. Its actions are "
                      + MessageSchemas.ACTIONS.keySet()
                      + ".")
              .orElseGet(() ->
                  "Message has no action. Protocol version 1.0 has "
                      + MessageSchemas.ACTIONS.keySet()
                      + "."));
    }
    String action = actions.getFirst();

    Map<String, Object> body = Json.asObject(message.get(action));
    if (body == null) {
      throw new A2uiValidationError("'" + action + "' must be an object.");
    }
    Object surfaceId = body.get("surfaceId");
    if (surfaceId != null && !(surfaceId instanceof String)) {
      throw new A2uiValidationError(
          "surfaceId must be a string in '" + action + "', but got: " + surfaceId);
    }
    // v1.0 removed `theme`. A renderer that accepts unknown elements removes the field. For each
    // other renderer, the schema rejects it.
    if (action.equals("createSurface") && body.containsKey("theme") && acceptsUnknown(config)) {
      Map<String, Object> withoutTheme = new LinkedHashMap<>(body);
      withoutTheme.remove("theme");
      Map<String, Object> changed = new LinkedHashMap<>(message);
      changed.put(action, withoutTheme);
      message = changed;
      body = withoutTheme;
    }
    List<String> problems = MessageSchemas.errors(action, message);
    if (!problems.isEmpty()) {
      throw new A2uiValidationError(
          "Invalid " + action + " message: " + String.join("; ", problems));
    }

    switch (action) {
      case "createSurface" -> createSurface(body, config);
      case "updateComponents" -> updateComponents(body, config);
      case "updateDataModel" -> updateDataModel(body);
      case "deleteSurface" -> model.deleteSurface((String) body.get("surfaceId"));
      default ->
          throw new A2uiStateError("This SDK does not handle '" + action + "' messages yet.");
    }
  }

  private static boolean acceptsUnknown(ValidationConfig config) {
    return config == null || config.allowUnknownElements();
  }

  private void createSurface(Map<String, Object> body, ValidationConfig config) {
    String id = (String) body.get("surfaceId");
    if (model.getSurface(id).isPresent()) {
      throw new A2uiIntegrityError("Surface already exists: " + id);
    }

    Map<String, Catalog<C, ?>> available = new LinkedHashMap<>();
    catalogs.forEach((catalogId, catalog) -> {
      if (catalog.protocolVersion().orElse(null) == ProtocolVersion.V1_0) {
        available.put(catalogId, catalog);
      }
    });
    String catalogId = (String) body.get("catalogId");
    Catalog<C, ?> defaultCatalog = catalogId == null ? null : available.get(catalogId);
    if (catalogId != null && defaultCatalog == null) {
      throw new A2uiCatalogError(
          catalogs.containsKey(catalogId)
              ? "Catalog not found: "
                  + catalogId
                  + " is not a v1.0 catalog, so surface '"
                  + id
                  + "' cannot use it."
              : "Catalog not found: " + catalogId + " (createSurface for surface '" + id + "').");
    }
    SurfaceModel<C> surface =
        new SurfaceModel<>(
            id,
            ProtocolVersion.V1_0,
            defaultCatalog,
            available,
            Boolean.TRUE.equals(body.get("sendDataModel")),
            metadataOf(body.get("metadata")));
    // The data comes before the components, because the components bind to the data. The
    // processor writes each top-level key separately. Thus one notification occurs for each key,
    // and the write does not replace the root.
    Map<String, Object> data = Json.asObject(body.get("dataModel"));
    if (data != null) {
      data.forEach((key, value) -> surface.dataModel().set("/" + Json.escapePointer(key), value));
    }
    List<Planned> planned =
        body.get("components") instanceof List<?> components
            ? plan(surface, components, config)
            : List.of();
    // All the checks are complete. Thus the surface becomes visible fully or not at all.
    model.addSurface(surface);
    apply(surface, planned);
  }

  private void updateComponents(Map<String, Object> body, ValidationConfig config) {
    SurfaceModel<C> surface = existingSurface((String) body.get("surfaceId"));
    List<Planned> planned = plan(surface, (List<?>) body.get("components"), config);
    Reactive.batch(() -> apply(surface, planned));
  }

  private void updateDataModel(Map<String, Object> body) {
    SurfaceModel<C> surface = existingSurface((String) body.get("surfaceId"));
    String path = body.get("path") instanceof String text && !text.isEmpty() ? text : "/";
    // The data model also accepts a relative path. The path of a message is always absolute.
    if (!path.startsWith("/")) {
      throw new A2uiValidationError(
          "Invalid path syntax in the updateDataModel message: '"
              + path
              + "' is not a JSON Pointer that starts with '/'.");
    }
    surface.dataModel().set(path, body.get("value"));
  }

  private SurfaceModel<C> existingSurface(String id) {
    return model
        .getSurface(id)
        .orElseThrow(() -> new A2uiIntegrityError("Surface not found for message: " + id));
  }

  /**
   * One component of an update. The processor examined it, and it is ready for a surface.
   *
   * @param normalized the component with each envelope key in its {@code @} form
   */
  private record Planned(
      String id, String type, Catalog<?, ?> catalog, Map<String, Object> normalized) {}

  /**
   * Examines the components of an update against the surface as it is after the update. The method
   * changes nothing.
   *
   * @return the components to write
   */
  private List<Planned> plan(SurfaceModel<C> surface, List<?> components, ValidationConfig config) {
    Catalog<C, ?> defaultCatalog = surface.defaultCatalog().orElse(null);
    boolean allowUnknown = acceptsUnknown(config);
    List<Planned> planned = new ArrayList<>(components.size());
    Set<String> ids = new HashSet<>();
    List<String> problems = new ArrayList<>();
    for (int position = 0; position < components.size(); position++) {
      Map<String, Object> component = Json.asObject(components.get(position));
      String id = (String) component.get("id");
      if (!ids.add(id)) {
        throw new A2uiIntegrityError(
            "Duplicate component ID '" + id + "' in one update of surface '" + surface.id() + "'.");
      }
      Planned entry = normalize(surface, id, component);
      planned.add(entry);
      if (config != null) {
        // A call that names no catalog runs in the default catalog of the surface. The catalog of
        // the component examines such a call only if it is that default catalog.
        boolean runsUnqualifiedCalls = entry.catalog() == defaultCatalog;
        for (String problem :
            checksFor(entry.catalog())
                .componentErrors(entry.normalized(), runsUnqualifiedCalls, allowUnknown)) {
          problems.add("component '" + id + "': " + problem);
        }
      }
      checkCallCatalogs(
          surface,
          entry,
          entry.normalized(),
          "/components/" + position,
          config,
          allowUnknown,
          problems);
    }
    if (!problems.isEmpty()) {
      throw new A2uiValidationError(
          "Invalid components for surface '" + surface.id() + "': " + String.join("; ", problems));
    }
    if (config != null) {
      ComponentGraph.validate(candidateGraph(surface, planned), surface.rootId(), config);
    }
    return planned;
  }

  /**
   * Resolves the catalog of a component, and gives the {@code @} form to each envelope key that
   * v1.0 accepts without its {@code @}: {@code catalogId}, {@code metadata}, {@code accessibility}
   * and {@code checks}. Such a key is an envelope key only if the schema of the component declares
   * no property of that name.
   */
  private Planned normalize(SurfaceModel<C> surface, String id, Map<String, Object> component) {
    String type = (String) component.get("component");
    Map<String, Object> normalized = new LinkedHashMap<>(component);

    // The default catalog of the surface makes the decision for `catalogId`, because the processor
    // must read this key before it knows the catalog of the component.
    Catalog<C, ?> defaultCatalog = surface.defaultCatalog().orElse(null);
    if (!owns(defaultCatalog, type, "catalogId")) {
      adoptLegacyKey(normalized, id, "catalogId");
    }
    Object catalogId = normalized.get("@catalogId");
    if (normalized.containsKey("@catalogId") && !(catalogId instanceof String)) {
      throw new A2uiValidationError(
          "Component '" + id + "' has a @catalogId that is not a string: " + catalogId);
    }
    Catalog<C, ?> catalog = surface.resolveCatalog((String) catalogId, "Component '" + id + "'");
    for (String key : LEGACY_ENVELOPE_KEYS) {
      if (!owns(catalog, type, key)) {
        adoptLegacyKey(normalized, id, key);
      }
    }
    return new Planned(id, type, catalog, Collections.unmodifiableMap(normalized));
  }

  private boolean owns(Catalog<?, ?> catalog, String type, String key) {
    return catalog != null && indexFor(catalog).ownsLegacyEnvelopeName(type, key);
  }

  /** Moves the value of an envelope key without {@code @} to the key with {@code @}. */
  private static void adoptLegacyKey(Map<String, Object> component, String id, String key) {
    if (!component.containsKey(key)) {
      return;
    }
    if (component.containsKey("@" + key)) {
      throw new A2uiValidationError(
          "Component '"
              + id
              + "' has both '@"
              + key
              + "' and '"
              + key
              + "', which mean the same in the envelope.");
    }
    component.put("@" + key, component.remove(key));
  }

  /**
   * Resolves the catalog of each function call in a node or below it.
   *
   * <p>A call that cannot run is an error, with or without a validation configuration. With a
   * configuration, the method also examines each call that the checks of the component did not
   * examine, because the call runs in a different catalog. It examines such a call against the
   * catalog in which the call runs.
   *
   * @param owner the component that contains the node
   * @param path the JSON Pointer of the node, for the messages
   * @param problems the list that receives the problems
   */
  private void checkCallCatalogs(
      SurfaceModel<C> surface,
      Planned owner,
      Object node,
      String path,
      ValidationConfig config,
      boolean allowUnknown,
      List<String> problems) {
    if (node instanceof List<?> list) {
      for (int index = 0; index < list.size(); index++) {
        checkCallCatalogs(
            surface, owner, list.get(index), path + "/" + index, config, allowUnknown, problems);
      }
      return;
    }
    Map<String, Object> map = Json.asObject(node);
    if (map == null) {
      return;
    }
    if (map.get("@call") instanceof String name && !name.startsWith("@")) {
      Object catalogId = map.get("catalogId");
      if (catalogId != null && !(catalogId instanceof String)) {
        throw new A2uiValidationError(
            path + ": the catalogId of the call to '" + name + "' must be a string");
      }
      Catalog<C, ?> target =
          surface.resolveCatalog((String) catalogId, "Function call '" + name + "'");
      boolean examinedByOwner =
          catalogId == null
              ? owner.catalog() == surface.defaultCatalog().orElse(null)
              : catalogId.equals(owner.catalog().id());
      if (config != null && !examinedByOwner) {
        problems.addAll(checksFor(target).callErrors(map, path, allowUnknown));
      }
    }
    map.forEach((key, value) ->
        checkCallCatalogs(surface, owner, value, path + "/" + key, config, allowUnknown, problems));
  }

  /**
   * Examines the parts of a value that need no catalog: the depth of the values and the syntax of
   * the data paths.
   *
   * @param subject the description of the value that contains the node, for the messages
   * @param depth the depth of the node. A message has the depth 0.
   */
  private static void checkValue(String subject, Object node, int depth) {
    if (depth > ComponentGraph.MAX_DEPTH) {
      throw new A2uiRecursionError(
          "Global recursion limit exceeded: "
              + subject
              + " nests values deeper than "
              + ComponentGraph.MAX_DEPTH
              + " levels.");
    }
    if (node instanceof List<?> list) {
      list.forEach(item -> checkValue(subject, item, depth + 1));
    } else if (node instanceof Map<?, ?> map) {
      // v1.0 binds data with `@path`. A child list template and `updateDataModel` use `path`.
      for (String key : PATH_KEYS) {
        if (map.get(key) instanceof String path) {
          checkPath(subject, path);
        }
      }
      map.values().forEach(value -> checkValue(subject, value, depth + 1));
    }
  }

  /**
   * Examines the escapes of a JSON Pointer.
   *
   * @throws A2uiValidationError if the path has a {@code ~} that is not the start of {@code ~0} or
   *     {@code ~1}
   */
  private static void checkPath(String subject, String path) {
    for (int tilde = path.indexOf('~'); tilde >= 0; tilde = path.indexOf('~', tilde + 1)) {
      char next = tilde + 1 < path.length() ? path.charAt(tilde + 1) : ' ';
      if (next != '0' && next != '1') {
        throw new A2uiValidationError(
            "Invalid path syntax in "
                + subject
                + ": '"
                + path
                + "' has a '~' that is not followed by '0' or '1'.");
      }
    }
  }

  /** Makes the graph that the surface has after the processor writes the planned components. */
  private Map<String, ComponentGraph.Node> candidateGraph(
      SurfaceModel<C> surface, List<Planned> planned) {
    Map<String, ComponentGraph.Node> nodes = new LinkedHashMap<>();
    for (ComponentModel existing : surface.componentsModel().all().values()) {
      nodes.put(
          existing.id(),
          node(existing.id(), existing.type(), existing.catalog(), existing.properties()));
    }
    for (Planned entry : planned) {
      nodes.put(entry.id(), node(entry.id(), entry.type(), entry.catalog(), entry.normalized()));
    }
    return nodes;
  }

  private ComponentGraph.Node node(
      String id, String type, Catalog<?, ?> catalog, Map<String, Object> values) {
    return new ComponentGraph.Node(
        id, type, catalog.components().get(type), indexFor(catalog).children(type, values));
  }

  private void apply(SurfaceModel<C> surface, List<Planned> planned) {
    SurfaceComponentsModel components = surface.componentsModel();
    for (Planned entry : planned) {
      Map<String, Object> normalized = entry.normalized();
      Map<String, Object> properties = new LinkedHashMap<>(normalized);
      properties.keySet().removeAll(ENVELOPE_KEYS);
      A2uiMetadata metadata = metadataOf(normalized.get("@metadata"));
      Map<String, Object> accessibility = Json.asObject(normalized.get("@accessibility"));
      List<?> rules = normalized.get("@checks") instanceof List<?> list ? list : null;

      ComponentModel existing = components.get(entry.id()).orElse(null);
      // A component with a different type or a different catalog is a different component with
      // the same ID. Thus the processor replaces it, and a view can discard its state.
      if (existing != null
          && existing.type().equals(entry.type())
          && existing.catalog() == entry.catalog()) {
        existing.replaceDefinition(properties, metadata, accessibility, rules);
      } else {
        components.removeComponent(entry.id());
        components.addComponent(
            new ComponentModel(
                entry.id(),
                entry.type(),
                entry.catalog(),
                properties,
                metadata,
                accessibility,
                rules));
      }
    }
  }

  private static A2uiMetadata metadataOf(Object raw) {
    Map<String, Object> metadata = Json.asObject(raw);
    return metadata == null
        ? null
        : new A2uiMetadata(Optional.ofNullable(Json.asObject(metadata.get("extensions"))));
  }

  private CatalogChecks checksFor(Catalog<?, ?> catalog) {
    return checks.computeIfAbsent(catalog, CatalogChecks::new);
  }

  private CatalogSchemaIndex indexFor(Catalog<?, ?> catalog) {
    return indexes.computeIfAbsent(catalog, CatalogSchemaIndex::new);
  }
}
