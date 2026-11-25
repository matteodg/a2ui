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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Writes one JSON Lines record for each completed case.
 *
 * <p>The manifest lets CI make sure that the harness did not drop a case. The class writes the
 * records only when the environment variable {@code A2UI_CONFORMANCE_MANIFEST_DIR} is set.
 */
final class ConformanceManifest {
  /** The identifier that this harness records and that CI gives to the comparison script. */
  static final String HARNESS = "java-core";

  private static final Path FILE = manifestFile();

  private ConformanceManifest() {}

  static void passed(ConformanceCase testCase) {
    record(testCase, "passed", null);
  }

  static void failed(ConformanceCase testCase) {
    record(testCase, "failed", null);
  }

  static void skipped(ConformanceCase testCase, String reason) {
    record(testCase, "skipped", reason);
  }

  /** Converts a map of strings to one line of JSON. */
  static String toJson(Map<String, String> fields) {
    StringJoiner joiner = new StringJoiner(",", "{", "}");
    fields.forEach((key, value) -> joiner.add(quote(key) + ":" + quote(value)));
    return joiner.toString();
  }

  private static synchronized void record(ConformanceCase testCase, String status, String reason) {
    if (FILE == null) {
      return;
    }
    Map<String, String> line = new LinkedHashMap<>();
    line.put("harness", HARNESS);
    line.put("suite", testCase.suite());
    line.put("case", testCase.name());
    line.put("status", status);
    if (reason != null) {
      line.put("reason", reason);
    }
    try {
      Files.writeString(
          FILE,
          toJson(line) + "\n",
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path manifestFile() {
    String dir = System.getenv("A2UI_CONFORMANCE_MANIFEST_DIR");
    if (dir == null || dir.isBlank()) {
      return null;
    }
    try {
      Path path = Files.createDirectories(Path.of(dir));
      return path.resolve(HARNESS + "-" + ProcessHandle.current().pid() + ".jsonl");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String quote(String text) {
    StringBuilder out = new StringBuilder(text.length() + 2).append('"');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }
}
