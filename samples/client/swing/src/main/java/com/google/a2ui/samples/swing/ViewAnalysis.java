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

package com.google.a2ui.samples.swing;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Finds the component types of a list of messages, and those that have no Swing view. */
final class ViewAnalysis {
  /** The messages that have a list of components. */
  private static final List<String> COMPONENT_MESSAGES =
      List.of("createSurface", "updateComponents");

  private ViewAnalysis() {}

  /**
   * Gets the type of each component that the messages have.
   *
   * @param messages the messages from the agent. The method ignores a value that is not a message.
   * @return the component types, in alphabetical sequence
   */
  static SortedSet<String> componentTypes(List<?> messages) {
    SortedSet<String> types = new TreeSet<>();
    for (Object message : messages) {
      if (!(message instanceof Map<?, ?> envelope)) {
        continue;
      }
      for (String key : COMPONENT_MESSAGES) {
        if (envelope.get(key) instanceof Map<?, ?> body
            && body.get("components") instanceof List<?> components) {
          for (Object component : components) {
            if (component instanceof Map<?, ?> definition
                && definition.get("component") instanceof String type) {
              types.add(type);
            }
          }
        }
      }
    }
    return types;
  }

  /**
   * Gets the component types that have no view.
   *
   * @param componentTypes the component types of an example
   * @param viewNames the component types that have a view
   * @return the types that are not in {@code viewNames}, in alphabetical sequence
   */
  static SortedSet<String> absentViews(Collection<String> componentTypes, Set<String> viewNames) {
    SortedSet<String> absent = new TreeSet<>(componentTypes);
    absent.removeAll(viewNames);
    return absent;
  }
}
