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
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
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
   */
  public SurfaceModel(
      String id,
      ProtocolVersion protocolVersion,
      Catalog<C, ?> defaultCatalog,
      Map<String, ? extends Catalog<C, ?>> availableCatalogs,
      boolean sendDataModel,
      A2uiMetadata metadata) {
    this.id = Objects.requireNonNull(id, "id");
    this.protocolVersion = Objects.requireNonNull(protocolVersion, "protocolVersion");
    this.defaultCatalog = defaultCatalog;
    this.availableCatalogs =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(availableCatalogs, "availableCatalogs")));
    this.sendDataModel = sendDataModel;
    this.metadata = metadata;
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

  /** Removes each component and each listener of the components and of the data. */
  public void dispose() {
    componentsModel.dispose();
    dataModel.dispose();
  }

  @Override
  public String toString() {
    return "SurfaceModel(" + id + ")";
  }
}
