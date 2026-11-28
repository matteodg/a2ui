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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.a2ui.core.exceptions.A2uiDataError;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonPointerTest {
  @ParameterizedTest
  @ValueSource(strings = {"", "/", "///"})
  void parseReadsTheRootAsNoSegments(String path) {
    assertEquals(List.of(), JsonPointer.parse(path));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a/b", "/a/b", "//a/b/"})
  void parseIgnoresEmptySegments(String path) {
    assertEquals(List.of("a", "b"), JsonPointer.parse(path));
  }

  @Test
  void parseDecodesTheEscapeSequences() {
    assertEquals(List.of("a/b", "c~d", "~1"), JsonPointer.parse("/a~1b/c~0d/~01"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/a~", "/a~2", "/~x/b"})
  void parseRejectsAnIncorrectEscapeSequence(String path) {
    A2uiDataError error = assertThrows(A2uiDataError.class, () -> JsonPointer.parse(path));
    assertEquals(Optional.of(path), error.path());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/__proto__", "/a/constructor/b", "prototype"})
  void parseRejectsAForbiddenSegment(String path) {
    assertThrows(A2uiDataError.class, () -> JsonPointer.parse(path));
  }

  @Test
  void buildEncodesTheSegments() {
    assertEquals("/", JsonPointer.build(List.of()));
    assertEquals("/a~1b/c~0d", JsonPointer.build(List.of("a/b", "c~d")));
  }

  @Test
  void listIndexAcceptsOnlyCanonicalIndexes() {
    assertEquals(OptionalInt.of(0), JsonPointer.listIndex("0"));
    assertEquals(OptionalInt.of(12), JsonPointer.listIndex("12"));
    assertEquals(OptionalInt.empty(), JsonPointer.listIndex("01"));
    assertEquals(OptionalInt.empty(), JsonPointer.listIndex("-1"));
    assertEquals(OptionalInt.empty(), JsonPointer.listIndex("1a"));
    assertEquals(OptionalInt.empty(), JsonPointer.listIndex(""));
    assertEquals(OptionalInt.of(Integer.MAX_VALUE), JsonPointer.listIndex("99999999999"));
  }

  @Test
  void resolveReturnsAnAbsolutePathWithoutChanges() {
    assertEquals("/a/b", JsonPointer.resolve("/a/b", "/users/0"));
  }

  @Test
  void resolveAppendsARelativePathToTheBase() {
    assertEquals("/users/0/name", JsonPointer.resolve("name", "/users/0"));
    assertEquals("/users/0/name", JsonPointer.resolve("name", "users/0/"));
    assertEquals("/name", JsonPointer.resolve("name", null));
    assertEquals("/name", JsonPointer.resolve("name", "/"));
  }

  @Test
  void resolveReturnsTheBaseForAnEmptyPathAndForADot() {
    assertEquals("/users/0", JsonPointer.resolve("", "/users/0"));
    assertEquals("/users/0", JsonPointer.resolve(".", "/users/0"));
    assertEquals("/", JsonPointer.resolve("", null));
  }
}
