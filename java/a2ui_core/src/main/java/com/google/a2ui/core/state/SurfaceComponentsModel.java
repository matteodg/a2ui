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

import com.google.a2ui.core.common.EventNotifier;
import com.google.a2ui.core.common.EventSource;
import com.google.a2ui.core.exceptions.A2uiStateError;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The components of one surface, in a flat map by ID.
 *
 * <p>The message processor examines an update before it changes this model. The methods that change
 * the model make no such examination. Thus code that changes a model directly can make a surface
 * that the protocol does not permit.
 *
 * <p>A model is not thread-safe.
 */
public final class SurfaceComponentsModel {
  private final Map<String, ComponentModel> components = new LinkedHashMap<>();
  private final Map<String, ComponentModel> view = Collections.unmodifiableMap(components);
  private final EventNotifier<ComponentModel> created = new EventNotifier<>();
  private final EventNotifier<String> deleted = new EventNotifier<>();

  /** Makes a model that has no components. */
  public SurfaceComponentsModel() {}

  /**
   * Gets the components of the surface.
   *
   * @return the components by ID, in the sequence of their arrival. The map is a read-only view,
   *     and it shows each subsequent change of the model.
   */
  public Map<String, ComponentModel> all() {
    return view;
  }

  /**
   * Gets the component that has an ID.
   *
   * @param componentId the ID of the component
   * @return the component, or an empty result if the surface has no such component
   */
  public Optional<ComponentModel> get(String componentId) {
    return Optional.ofNullable(components.get(componentId));
  }

  /**
   * Gets the source of the events for new components.
   *
   * @return the event source. An event occurs after the model adds a component.
   */
  public EventSource<ComponentModel> onCreated() {
    return created;
  }

  /**
   * Gets the source of the events for removed components.
   *
   * @return the event source. An event occurs after the model removes a component, and it is the ID
   *     of that component.
   */
  public EventSource<String> onDeleted() {
    return deleted;
  }

  /**
   * Adds a component and then notifies the listeners. The method examines only the ID. It examines
   * no schema and no graph.
   *
   * @param component the component to add
   * @throws A2uiStateError if the surface already has a component with the same ID
   */
  public void addComponent(ComponentModel component) {
    Objects.requireNonNull(component, "component");
    if (components.containsKey(component.id())) {
      throw new A2uiStateError("Component '" + component.id() + "' is already on the surface.");
    }
    components.put(component.id(), component);
    created.emit(component);
  }

  /**
   * Removes a component, removes its listeners and then notifies the listeners of this model. If
   * the surface has no such component, nothing occurs.
   *
   * @param componentId the ID of the component to remove
   */
  public void removeComponent(String componentId) {
    ComponentModel removed = components.remove(componentId);
    if (removed == null) {
      return;
    }
    removed.dispose();
    deleted.emit(componentId);
  }

  /** Removes each component and each listener. The model sends no events for these removals. */
  public void dispose() {
    components.values().forEach(ComponentModel::dispose);
    components.clear();
    created.dispose();
    deleted.dispose();
  }
}
