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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Stream;

/**
 * Reads the example files of a directory. The gallery reads the examples of the basic catalog from
 * the repository, and it has no copy of them.
 *
 * <p>A file can have one of these structures:
 *
 * <ul>
 *   <li>an object with a list {@code messages}, and optionally a {@code name} and a {@code
 *       description}. Each example of the basic catalog has this structure.
 *   <li>a list of messages
 *   <li>one message
 * </ul>
 */
final class ExampleLoader {
  /** The directory of the examples of the basic catalog, relative to the root of the repository. */
  static final Path REPOSITORY_DIRECTORY = Path.of("catalogs", "basic", "v1", "examples");

  private ExampleLoader() {}

  /**
   * Finds the directory of the examples.
   *
   * @param configured the directory that the user gave, or an empty result
   * @param start the directory where the search starts when the user gave no directory. The method
   *     looks for {@link #REPOSITORY_DIRECTORY} in this directory and in each directory above it.
   * @return the directory of the examples
   * @throws IllegalArgumentException if the directory does not exist
   */
  static Path resolveDirectory(Optional<String> configured, Path start) {
    if (configured.isPresent()) {
      Path directory = Path.of(configured.get()).toAbsolutePath().normalize();
      if (!Files.isDirectory(directory)) {
        throw new IllegalArgumentException("The examples directory does not exist: " + directory);
      }
      return directory;
    }
    for (Path directory = start.toAbsolutePath().normalize();
        directory != null;
        directory = directory.getParent()) {
      Path candidate = directory.resolve(REPOSITORY_DIRECTORY);
      if (Files.isDirectory(candidate)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException(
        "Cannot find the directory "
            + REPOSITORY_DIRECTORY
            + " in "
            + start
            + " or in a directory above it. Give the directory as the program argument.");
  }

  /**
   * Reads each JSON file of a directory.
   *
   * @param directory the directory of the examples
   * @param viewNames the component types that have a Swing view
   * @return the examples. The examples that can show completely are first. Then the sequence is
   *     alphabetical. A file that the gallery cannot read is also in the list, with its problem.
   * @throws UncheckedIOException if the method cannot read the directory
   */
  static List<Example> load(Path directory, Set<String> viewNames) {
    List<Example> examples = new ArrayList<>();
    try (Stream<Path> files = Files.list(directory)) {
      files
          .filter(file -> file.getFileName().toString().endsWith(".json"))
          .filter(Files::isRegularFile)
          .forEach(file -> examples.add(read(file, viewNames)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    examples.sort(Comparator.comparingInt(Example::rank).thenComparing(Example::id));
    return examples;
  }

  /**
   * Reads one example file. This method does not throw an exception for a file that is not correct.
   *
   * @param file the JSON file
   * @param viewNames the component types that have a Swing view
   * @return the example, or an example that has a problem
   */
  static Example read(Path file, Set<String> viewNames) {
    String fileName = file.getFileName().toString();
    String id =
        fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
    Object document;
    try {
      document = Json.parse(Files.readString(file));
    } catch (IOException e) {
      return Example.unreadable(id, "Cannot read the file: " + e.getMessage());
    } catch (IllegalArgumentException e) {
      return Example.unreadable(id, "The file is not JSON: " + e.getMessage());
    }
    String name = id;
    String description = "";
    Object messages = document;
    if (document instanceof Map<?, ?> object) {
      if (object.containsKey("messages")) {
        messages = object.get("messages");
        name = object.get("name") instanceof String text && !text.isBlank() ? text : id;
        description = object.get("description") instanceof String text ? text : "";
      } else if (object.containsKey("version")) {
        messages = List.of(object);
      } else {
        return Example.unreadable(id, "The file has no list 'messages'.");
      }
    }
    if (!(messages instanceof List<?> list)) {
      return Example.unreadable(id, "The value of 'messages' is not a list.");
    }
    if (list.isEmpty()) {
      return Example.unreadable(id, "The file has no messages.");
    }
    SortedSet<String> types = ViewAnalysis.componentTypes(list);
    return new Example(
        id,
        name,
        description,
        new ArrayList<>(list),
        types,
        ViewAnalysis.absentViews(types, viewNames),
        Optional.empty());
  }
}
