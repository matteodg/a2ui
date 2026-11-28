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

import java.nio.file.Files;
import java.nio.file.Path;

/** Finds the shared conformance suites and the repository fixtures. */
final class ConformancePaths {
  private static final Path REPO_ROOT = findRepoRoot();

  private ConformancePaths() {}

  /** Gets the root directory of the repository. */
  static Path repoRoot() {
    return REPO_ROOT;
  }

  /** Gets the directory {@code conformance/}. */
  static Path conformanceRoot() {
    return REPO_ROOT.resolve("conformance");
  }

  /**
   * Gets a path that is relative to the root of the repository. Most core suites write paths in
   * this form.
   */
  static Path repoPath(String relative) {
    return REPO_ROOT.resolve(relative);
  }

  /** Gets a path that is relative to {@code conformance/}, such as a suite or a node fixture. */
  static Path conformancePath(String relative) {
    return conformanceRoot().resolve(relative);
  }

  /**
   * Finds the nearest parent directory that contains {@code conformance/conformance_schema.json}.
   */
  private static Path findRepoRoot() {
    Path start = Path.of("").toAbsolutePath();
    for (Path dir = start; dir != null; dir = dir.getParent()) {
      if (Files.isRegularFile(dir.resolve("conformance/conformance_schema.json"))) {
        return dir;
      }
    }
    throw new IllegalStateException(
        "Could not find conformance/conformance_schema.json above " + start);
  }
}
