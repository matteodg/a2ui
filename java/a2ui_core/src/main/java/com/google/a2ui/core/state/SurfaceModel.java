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
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.common.EventNotifier;
import com.google.a2ui.core.common.EventSource;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.internal.AgentRequests;
import com.google.a2ui.core.internal.ErrorFields;
import com.google.a2ui.core.internal.ExtensionKeys;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One UI surface: its catalogs, its data and its components.
 *
 * <p>A surface can use more than one catalog. The default catalog only gives the meaning of a
 * component or of a function call that names no catalog.
 *
 * <p>A model is not thread-safe.
 *
 * @param <C> the type of the components of the catalogs
 */
public final class SurfaceModel<C extends ComponentApi> {
  /** The ID of the root component. */
  public static final String DEFAULT_ROOT_ID = "root";

  private final String id;
  private final ProtocolVersion protocolVersion;
  private final Catalog<C, ?> defaultCatalog;
  private final Map<String, Catalog<C, ?>> availableCatalogs;
  private final boolean sendDataModel;
  private final A2uiMetadata metadata;
  private final DataModel dataModel = new DataModel();
  private final SurfaceComponentsModel componentsModel = new SurfaceComponentsModel();
  private final EventNotifier<RendererAction> actions = new EventNotifier<>();
  private final EventNotifier<A2uiError> errors = new EventNotifier<>();
  private final EventNotifier<SurfaceModel<C>> disposing = new EventNotifier<>();
  private final AgentRequests agentRequests;
  private boolean disposed;

  static {
    // The data context is in a different package. It gets the requests of a surface through
    // this function, which is not part of the API.
    AgentRequests.register(surface -> surface.agentRequests);
  }

  /**
   * Makes a surface that has no data and no components.
   *
   * @param id the ID of the surface
   * @param protocolVersion the protocol version of the message that made the surface
   * @param defaultCatalog the catalog that {@code createSurface.catalogId} named, or null if the
   *     message named none
   * @param availableCatalogs each catalog that the surface can use, by ID. The surface makes a copy
   *     of the map.
   * @param sendDataModel true if the renderer sends the data model of this surface to the agent
   * @param metadata the {@code createSurface.metadata}, or null if the message had none
   * @throws A2uiCatalogError if a catalog is not for the protocol version of the surface. All the
   *     catalogs of one surface are for one protocol version.
   */
  public SurfaceModel(
      String id,
      ProtocolVersion protocolVersion,
      Catalog<C, ?> defaultCatalog,
      Map<String, ? extends Catalog<C, ?>> availableCatalogs,
      boolean sendDataModel,
      A2uiMetadata metadata) {
    this.id = Objects.requireNonNull(id, "id");
    this.agentRequests = new AgentRequests(id);
    this.protocolVersion = Objects.requireNonNull(protocolVersion, "protocolVersion");
    this.defaultCatalog = defaultCatalog;
    this.availableCatalogs =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(availableCatalogs, "availableCatalogs")));
    this.sendDataModel = sendDataModel;
    this.metadata = metadata;
    if (defaultCatalog != null) {
      requireSurfaceVersion(defaultCatalog);
    }
    this.availableCatalogs.values().forEach(this::requireSurfaceVersion);
  }

  /** Makes sure that a catalog is for the protocol version of this surface. */
  private void requireSurfaceVersion(Catalog<C, ?> catalog) {
    ProtocolVersion version = catalog.protocolVersion().orElse(null);
    if (version != protocolVersion) {
      throw new A2uiCatalogError(
          "Protocol version mismatch: cannot mix "
              + (version == null
                  ? "unversioned catalog '" + catalog.id() + "'"
                  : "catalog '" + catalog.id() + "' (" + version.wireValue() + ")")
              + " with surface version "
              + protocolVersion.wireValue()
              + ".");
    }
  }

  /**
   * Gets the ID of the surface.
   *
   * @return the ID
   */
  public String id() {
    return id;
  }

  /**
   * Gets the protocol version of the message that made the surface.
   *
   * @return the version
   */
  public ProtocolVersion protocolVersion() {
    return protocolVersion;
  }

  /**
   * Gets the catalog that {@code createSurface.catalogId} named.
   *
   * @return the catalog, or an empty result if the message named none
   */
  public Optional<Catalog<C, ?>> defaultCatalog() {
    return Optional.ofNullable(defaultCatalog);
  }

  /**
   * Gets each catalog that the surface can use.
   *
   * @return the catalogs by ID. The map is read-only.
   */
  public Map<String, Catalog<C, ?>> availableCatalogs() {
    return availableCatalogs;
  }

  /**
   * Tells if the renderer sends the data model of this surface to the agent.
   *
   * @return true if the renderer sends the data model
   */
  public boolean sendDataModel() {
    return sendDataModel;
  }

  /**
   * Gets the {@code createSurface.metadata}.
   *
   * @return the metadata, or an empty result if the message had none
   */
  public Optional<A2uiMetadata> metadata() {
    return Optional.ofNullable(metadata);
  }

  /**
   * Gets the application data of the surface.
   *
   * @return the data model
   */
  public DataModel dataModel() {
    return dataModel;
  }

  /**
   * Gets the components of the surface.
   *
   * @return the model of the components
   */
  public SurfaceComponentsModel componentsModel() {
    return componentsModel;
  }

  /**
   * Gets the ID of the component at the root of the tree.
   *
   * @return the ID
   */
  public String rootId() {
    return DEFAULT_ROOT_ID;
  }

  /**
   * Gets the catalog in which an item runs. See {@link #resolveCatalog(String, String)}.
   *
   * @param catalogId the ID that the item names, or null if it names none
   * @return the catalog
   * @throws A2uiCatalogError if the surface cannot use the named catalog, or if the item names no
   *     catalog and the surface has no default catalog
   */
  public Catalog<C, ?> resolveCatalog(String catalogId) {
    return resolveCatalog(catalogId, "Item");
  }

  /**
   * Gets the catalog in which an item runs. It is the catalog that the item names. If the item
   * names none, it is the default catalog of the surface.
   *
   * @param catalogId the ID that the item names, or null if it names none
   * @param subject the item, for the start of the error message. An example is {@code Function call
   *     'upper'}.
   * @return the catalog
   * @throws A2uiCatalogError if the surface cannot use the named catalog, or if the item names no
   *     catalog and the surface has no default catalog
   */
  public Catalog<C, ?> resolveCatalog(String catalogId, String subject) {
    if (catalogId != null) {
      Catalog<C, ?> named = availableCatalogs.get(catalogId);
      if (named == null) {
        throw new A2uiCatalogError(
            "Catalog not found: " + catalogId + " (" + subject + " on surface '" + id + "').");
      }
      return named;
    }
    if (defaultCatalog == null) {
      throw new A2uiCatalogError(
          subject + " names no catalogId and surface '" + id + "' has no default catalogId.");
    }
    return defaultCatalog;
  }

  /**
   * Gets the catalog of a component. It is the catalog that the component names. If the component
   * names none, it is the default catalog of the surface.
   *
   * @param catalogId the ID that the component names, or null if it names none
   * @param componentId the ID of the component, for the error message
   * @return the catalog
   * @throws A2uiCatalogError if the surface cannot use the named catalog, or if the component names
   *     no catalog and the surface has no default catalog
   */
  public Catalog<C, ?> resolveComponentCatalog(String catalogId, String componentId) {
    String subject = "Component '" + componentId + "'";
    if (catalogId != null && !availableCatalogs.containsKey(catalogId)) {
      throw new A2uiCatalogError(
          subject + ": Catalog '" + catalogId + "' is not supported by surface '" + id + "'.");
    }
    return resolveCatalog(catalogId, subject);
  }

  /**
   * Gets the caller that sends a function call of this surface to the agent.
   *
   * @return the caller, or an empty result if the surface cannot send a call to the agent
   */
  public Optional<AgentFunctionCaller> agentFunctionCaller() {
    return Optional.ofNullable(agentRequests.caller());
  }

  /**
   * Sets the caller that sends a function call of this surface to the agent.
   *
   * <p>With a caller, the surface sends each function call that its catalog does not implement to
   * the agent. This applies to the bindings, the checks and the actions of the components. The
   * value of such a call is null until the agent answers. Then the node resolver resolves the node
   * of the binding again. Without a caller, such a call fails.
   *
   * <p>The surface shares one request between the identical calls that bindings make while the
   * request waits. It keeps the last 256 answers, and a binding that makes an identical call uses
   * the answer. A call of an action is always a new request.
   *
   * <p>A different caller removes the shared requests and the answers that the surface keeps. A
   * disposed surface ignores this method.
   *
   * @param caller the caller, or null if the surface cannot send a call to the agent
   */
  public void setAgentFunctionCaller(AgentFunctionCaller caller) {
    agentRequests.caller(caller);
  }

  /**
   * Tells if the surface is disposed.
   *
   * @return true after {@link #dispose()}
   */
  public boolean isDisposed() {
    return disposed;
  }

  /**
   * Gets the source of the action events. An event occurs when a component of this surface reports
   * an action for the agent.
   *
   * @return the event source
   */
  public EventSource<RendererAction> onAction() {
    return actions;
  }

  /**
   * Gets the source of the error events. An event occurs when a problem occurs on this surface
   * after the processor accepted its messages. Examples are a binding that the core cannot
   * evaluate, a component of an unknown type and a cycle of references.
   *
   * <p>The agent must know about each such problem. The other parts of the surface continue to
   * operate.
   *
   * @return the event source
   */
  public EventSource<A2uiError> onError() {
    return errors;
  }

  /**
   * Gets the source of the event that occurs one time, when {@link #dispose()} starts. At that time
   * the surface has its components and its data.
   *
   * @return the event source. The event is this surface.
   */
  public EventSource<SurfaceModel<C>> onDisposing() {
    return disposing;
  }

  /**
   * Reports an event action to the listeners of {@link #onAction()}, with no {@code
   * action.metadata}. See {@link #dispatchAction(Map, String, A2uiMetadata)}.
   *
   * @param payload the action as a component declares it, with resolved bindings
   * @param sourceComponentId the ID of the component that the user operated
   * @throws A2uiValidationError if the event has no name
   * @throws IllegalArgumentException if the context is not JSON
   */
  public void dispatchAction(Map<String, ?> payload, String sourceComponentId) {
    dispatchAction(payload, sourceComponentId, null);
  }

  /**
   * Reports an event action to the listeners of {@link #onAction()}.
   *
   * <p>The payload is an action as a component declares it: <code>
   * {"event": {"name": ..., "context": ..., "userMessage": ...}}</code>. The caller resolves its
   * bindings before the call. A {@code functionCall} action is work of the renderer. Thus the
   * surface reports nothing for a payload that has no {@code event}.
   *
   * <p>This method does not examine the {@code userMessage}. The action has a user message only if
   * the {@code userMessage} of the event is a text that is not empty.
   *
   * @param payload the action as a component declares it, with resolved bindings
   * @param sourceComponentId the ID of the component that the user operated
   * @param metadata the {@code action.metadata} to send with the action, or null
   * @throws A2uiValidationError if the event has no name, or if a key of the extensions of the
   *     metadata is not a UAX #31 identifier
   * @throws IllegalArgumentException if the context is not JSON
   */
  public void dispatchAction(
      Map<String, ?> payload, String sourceComponentId, A2uiMetadata metadata) {
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(sourceComponentId, "sourceComponentId");
    if (!(payload.get("event") instanceof Map<?, ?> event)) {
      return;
    }
    if (!(event.get("name") instanceof String name)) {
      throw new A2uiValidationError("An event action needs a name.");
    }
    ExtensionKeys.check(metadata);
    Optional<Map<String, Object>> extensions =
        metadata == null ? Optional.empty() : metadata.extensions();
    Map<String, Object> context = new LinkedHashMap<>();
    if (event.get("context") instanceof Map<?, ?> written) {
      written.forEach((key, value) -> context.put(String.valueOf(key), value));
    }
    String defaultId = defaultCatalog == null ? null : defaultCatalog.id();
    Optional<String> sourceCatalog =
        componentsModel
            .get(sourceComponentId)
            .map(component -> component.catalog().id())
            .filter(catalogId -> !catalogId.equals(defaultId));
    actions.emit(
        new RendererAction(
            name,
            id,
            sourceComponentId,
            Instant.now(),
            context,
            event.get("userMessage") instanceof String text && !text.isEmpty()
                ? Optional.of(text)
                : Optional.empty(),
            sourceCatalog,
            extensions.map(map -> metadata)));
  }

  /**
   * Reports an error to the listeners of {@link #onError()}. If the error names no surface, this
   * method writes the ID of this surface on it.
   *
   * <p>The view of a component uses this method for a failure of its own that the agent must know
   * about. The view can give a path and details to a constructor of {@code A2uiExpressionError} or
   * of {@code A2uiValidationError}.
   *
   * @param error the error to report
   */
  public void dispatchError(A2uiError error) {
    Objects.requireNonNull(error, "error");
    if (error.surfaceId().isEmpty()) {
      ErrorFields.surfaceId(error, id);
    }
    errors.emit(error);
  }

  /**
   * Notifies the listeners of {@link #onDisposing()}. Then removes each component and each listener
   * of the surface. The surface also removes its agent function caller. Thus a subsequent call
   * sends nothing to the agent.
   */
  public void dispose() {
    disposed = true;
    agentRequests.dispose();
    disposing.emit(this);
    disposing.dispose();
    componentsModel.dispose();
    dataModel.dispose();
    actions.dispose();
    errors.dispose();
  }

  @Override
  public String toString() {
    return "SurfaceModel(" + id + ")";
  }
}
