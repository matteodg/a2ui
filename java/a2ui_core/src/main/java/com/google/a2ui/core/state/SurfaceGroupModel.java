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

import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.common.EventNotifier;
import com.google.a2ui.core.common.EventSource;
import com.google.a2ui.core.exceptions.A2uiIntegrityError;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The surfaces that a message processor has at this time.
 *
 * <p>A model is not thread-safe.
 *
 * @param <C> the type of the components of the catalogs
 */
public final class SurfaceGroupModel<C extends ComponentApi> {
  private final Map<String, SurfaceModel<C>> surfaces = new LinkedHashMap<>();
  private final Map<String, SurfaceModel<C>> view = Collections.unmodifiableMap(surfaces);
  private final EventNotifier<SurfaceModel<C>> created = new EventNotifier<>();
  private final EventNotifier<String> deleted = new EventNotifier<>();
  private final EventNotifier<RendererAction> actions = new EventNotifier<>();

  /** Makes a model that has no surfaces. */
  public SurfaceGroupModel() {}

  /**
   * Gets the surfaces.
   *
   * @return the surfaces by ID, in the sequence of their creation. The map is a read-only view, and
   *     it shows each subsequent change of the model.
   */
  public Map<String, SurfaceModel<C>> all() {
    return view;
  }

  /**
   * Gets the surface that has an ID.
   *
   * @param surfaceId the ID of the surface
   * @return the surface, or an empty result if there is no such surface
   */
  public Optional<SurfaceModel<C>> getSurface(String surfaceId) {
    return Optional.ofNullable(surfaces.get(surfaceId));
  }

  /**
   * Gets the source of the events for new surfaces.
   *
   * @return the event source. An event occurs after the model adds a surface.
   */
  public EventSource<SurfaceModel<C>> onSurfaceCreated() {
    return created;
  }

  /**
   * Gets the source of the events for deleted surfaces.
   *
   * @return the event source. An event occurs after the model deletes a surface, and it is the ID
   *     of that surface.
   */
  public EventSource<String> onSurfaceDeleted() {
    return deleted;
  }

  /**
   * Gets the source of the action events of all the surfaces. An event occurs when a surface of
   * this model reports an action for the agent.
   *
   * @return the event source
   */
  public EventSource<RendererAction> onAction() {
    return actions;
  }

  /**
   * Adds a surface and then notifies the listeners.
   *
   * @param surface the surface to add
   * @throws A2uiIntegrityError if a surface with the same ID exists
   */
  public void addSurface(SurfaceModel<C> surface) {
    Objects.requireNonNull(surface, "surface");
    if (surfaces.containsKey(surface.id())) {
      throw new A2uiIntegrityError("Surface already exists: " + surface.id());
    }
    surfaces.put(surface.id(), surface);
    // The dispose operation of the surface removes this listener.
    surface.onAction().subscribe(actions::emit);
    created.emit(surface);
  }

  /**
   * Deletes a surface, removes its content and its listeners, and then notifies the listeners of
   * this model. If there is no such surface, nothing occurs.
   *
   * @param surfaceId the ID of the surface to delete
   */
  public void deleteSurface(String surfaceId) {
    SurfaceModel<C> removed = surfaces.remove(surfaceId);
    if (removed == null) {
      return;
    }
    removed.dispose();
    deleted.emit(surfaceId);
  }

  /** Deletes each surface and removes each listener. The model sends no events for the surfaces. */
  public void dispose() {
    surfaces.values().forEach(SurfaceModel::dispose);
    surfaces.clear();
    created.dispose();
    deleted.dispose();
    actions.dispose();
  }
}
