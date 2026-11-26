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

package com.google.a2ui.core.conformance;

import static com.google.a2ui.core.conformance.JsonValues.asObject;
import static com.google.a2ui.core.conformance.JsonValues.asString;

import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.core.internal.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The action {@code resolve_nodes}: the handler builds the node tree of a fixture and runs the
 * operations of the case. After each operation it examines the tree, the notifications and the
 * effects, as {@code conformance/core/node_resolution.md} specifies.
 *
 * <p>A case whose fixture uses a catalog from before v1.0 does not come to this handler. The
 * version rule of the registry skips it, because {@link ConformanceCase#protocolVersion()} reads
 * the version of the catalog of the fixture. {@code NodeScenariosTest} runs those scenarios in the
 * v1.0 syntax.
 */
final class ResolveNodesHandler implements CaseHandler {
  @Override
  public void run(ConformanceCase testCase) {
    new NodeScenario(testCase.location(), fixture(testCase.fields()), testCase.fields()).run();
  }

  /**
   * Reads the fixture that a case names.
   *
   * @param fields the content of the case
   * @return the content of the fixture
   */
  static Map<String, Object> fixture(Map<String, Object> fields) {
    String path = asString(fields.get("fixture"), "fixture");
    return asObject(ConformanceLoader.parse(read(ConformancePaths.conformancePath(path))), path);
  }

  /**
   * Reads the catalog document of a fixture.
   *
   * @param path the path of the document relative to {@code conformance/}
   */
  static Map<String, Object> catalogDocument(String path) {
    return asObject(Json.parse(read(ConformancePaths.conformancePath(path).normalize())), path);
  }

  /**
   * Gets the protocol version of the catalog of the fixture that a case names.
   *
   * <p>The version is the {@code protocolVersion} of the catalog document. A document from before
   * v1.0 does not have that key. Then the version is the name of the directory of the document, for
   * example {@code 0.9.1} for {@code catalogs/basic/v0_9_1/catalog.json}.
   *
   * @param fields the content of the case
   * @return the version, or null if the document and its directory give none
   */
  static String fixtureVersion(Map<String, Object> fields) {
    String path = asString(fixture(fields).get("catalog"), "catalog of the fixture");
    Object declared = catalogDocument(path).get("protocolVersion");
    if (declared != null) {
      return declared.toString();
    }
    Path directory = Path.of(path).getParent();
    String name = directory == null ? "" : directory.getFileName().toString();
    return name.matches("v\\d+(_\\d+)*") ? name.substring(1).replace('_', '.') : null;
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
