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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExampleLoaderTest {
  private static final Set<String> VIEWS = Set.of("Text", "Column");

  private static final String TEXT_SURFACE =
      """
      {"version": "1.0", "createSurface": {"surfaceId": "s", "catalogId": "c", "components": [
        {"id": "root", "component": "Column", "children": ["title"]},
        {"id": "title", "component": "Text", "text": "Hello"}]}}
      """;

  private static final String CARD_UPDATE =
      """
      {"version": "1.0", "updateComponents": {"surfaceId": "s", "components": [
        {"id": "root", "component": "Card", "child": "title"}]}}
      """;

  @TempDir Path directory;

  private Path write(String name, String text) throws IOException {
    return Files.writeString(directory.resolve(name), text);
  }

  @Test
  void anObjectWithMessagesGivesItsNameItsDescriptionAndItsMessages() throws IOException {
    Path file =
        write(
            "hello.json",
            "{\"name\": \"Hello\", \"description\": \"A text.\", \"messages\": ["
                + TEXT_SURFACE
                + "]}");

    Example example = ExampleLoader.read(file, VIEWS);

    assertEquals("hello", example.id());
    assertEquals("Hello", example.name());
    assertEquals("A text.", example.description());
    assertEquals(1, example.messages().size());
    assertTrue(example.messages().get(0) instanceof Map<?, ?> map && map.containsKey("version"));
    assertEquals(Set.of("Column", "Text"), example.componentTypes());
    assertEquals(Set.of(), example.absentViews());
    assertEquals(Optional.empty(), example.problem());
    assertTrue(example.complete());
  }

  @Test
  void aListOfMessagesIsAnExampleWithTheNameOfItsFile() throws IOException {
    Path file = write("two.json", "[" + TEXT_SURFACE + "," + CARD_UPDATE + "]");

    Example example = ExampleLoader.read(file, VIEWS);

    assertEquals("two", example.name());
    assertEquals("", example.description());
    assertEquals(2, example.messages().size());
    assertEquals(Set.of("Card"), example.absentViews());
    assertFalse(example.complete());
  }

  @Test
  void oneMessageIsAnExampleWithOneMessage() throws IOException {
    Example example = ExampleLoader.read(write("one.json", TEXT_SURFACE), VIEWS);

    assertEquals(1, example.messages().size());
    assertTrue(example.complete());
  }

  @Test
  void aFileThatIsNotCorrectGivesAnExampleWithAProblem() throws IOException {
    assertTrue(problemOf(write("a.json", "{not json")).startsWith("The file is not JSON: "));
    assertEquals("The file has no list 'messages'.", problemOf(write("b.json", "{\"name\": 1}")));
    assertEquals(
        "The value of 'messages' is not a list.", problemOf(write("c.json", "{\"messages\": 1}")));
    assertEquals("The value of 'messages' is not a list.", problemOf(write("d.json", "3")));
    assertEquals("The file has no messages.", problemOf(write("e.json", "{\"messages\": []}")));
    assertTrue(problemOf(directory.resolve("absent.json")).startsWith("Cannot read the file: "));
  }

  private static String problemOf(Path file) {
    Example example = ExampleLoader.read(file, VIEWS);
    assertFalse(example.complete());
    assertEquals(List.of(), example.messages());
    return example.problem().orElseThrow();
  }

  @Test
  void theCompleteExamplesAreFirstAndTheFilesWithAProblemAreLast() throws IOException {
    write("b-card.json", "[" + CARD_UPDATE + "]");
    write("a-broken.json", "{");
    write("d-text.json", "[" + TEXT_SURFACE + "]");
    write("c-text.json", "[" + TEXT_SURFACE + "]");
    write("notes.txt", "This file is not an example.");
    Files.createDirectory(directory.resolve("folder.json"));

    List<Example> examples = ExampleLoader.load(directory, VIEWS);

    assertEquals(
        List.of("c-text", "d-text", "b-card", "a-broken"),
        examples.stream().map(Example::id).toList());
  }

  @Test
  void aDirectoryThatDoesNotExistCannotLoad() {
    assertThrows(
        UncheckedIOException.class, () -> ExampleLoader.load(directory.resolve("absent"), VIEWS));
  }

  @Test
  void theConfiguredDirectoryIsTheDirectoryOfTheExamples() {
    assertEquals(
        directory.toAbsolutePath().normalize(),
        ExampleLoader.resolveDirectory(Optional.of(directory.toString()), Path.of("unused")));
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ExampleLoader.resolveDirectory(
                    Optional.of(directory.resolve("absent").toString()), directory));
    assertTrue(error.getMessage().startsWith("The examples directory does not exist: "));
  }

  @Test
  void theSearchFindsTheExamplesOfTheRepositoryAboveTheStartDirectory() throws IOException {
    Path examples = directory.resolve(ExampleLoader.REPOSITORY_DIRECTORY);
    Files.createDirectories(examples);
    Path start = Files.createDirectories(directory.resolve("samples/client/swing"));

    assertEquals(
        examples.toAbsolutePath().normalize(),
        ExampleLoader.resolveDirectory(Optional.empty(), start));
  }

  @Test
  void theSearchFailsWithAMessageWhenNoDirectoryHasTheExamples() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> ExampleLoader.resolveDirectory(Optional.empty(), directory));

    assertTrue(error.getMessage().startsWith("Cannot find the directory "), error.getMessage());
  }
}
