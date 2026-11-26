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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * One example of the gallery: the messages of one file and what the gallery knows about them.
 *
 * @param id the name of the file, without the extension
 * @param name the name that the file gives to the example, or the ID
 * @param description the description that the file gives, or an empty text
 * @param messages the messages from the agent, in the sequence of the file
 * @param componentTypes the type of each component that the messages have
 * @param absentViews the component types that have no Swing view. The surface shows a placeholder
 *     for each component of such a type.
 * @param problem the reason why the gallery cannot read the file, or an empty result
 */
record Example(
    String id,
    String name,
    String description,
    List<Object> messages,
    SortedSet<String> componentTypes,
    SortedSet<String> absentViews,
    Optional<String> problem) {

  /** Makes the example. */
  Example {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(description, "description");
    messages = Collections.unmodifiableList(messages);
    componentTypes = Collections.unmodifiableSortedSet(new TreeSet<>(componentTypes));
    absentViews = Collections.unmodifiableSortedSet(new TreeSet<>(absentViews));
    Objects.requireNonNull(problem, "problem");
  }

  /** Makes an example for a file that the gallery cannot read. */
  static Example unreadable(String id, String problem) {
    return new Example(
        id, id, "", List.of(), new TreeSet<>(), new TreeSet<>(), Optional.of(problem));
  }

  /**
   * Tells if the gallery can show the example completely.
   *
   * @return true if the gallery read the file and each component type has a Swing view
   */
  boolean complete() {
    return problem.isEmpty() && absentViews.isEmpty();
  }

  /**
   * Gets the position of the example in the list: the complete examples are first, then the
   * examples with placeholders, then the files that the gallery cannot read.
   */
  int rank() {
    if (problem.isPresent()) {
      return 2;
    }
    return absentViews.isEmpty() ? 0 : 1;
  }
}
