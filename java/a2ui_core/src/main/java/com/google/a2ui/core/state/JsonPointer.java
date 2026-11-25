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

import com.google.a2ui.core.exceptions.A2uiDataError;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/** JSON Pointer (RFC 6901) operations as A2UI uses them. This includes relative paths. */
final class JsonPointer {
  /**
   * The segments that no path can contain. Each A2UI implementation rejects them, thus a payload
   * behaves the same in each language.
   */
  private static final Set<String> FORBIDDEN_SEGMENTS =
      Set.of("__proto__", "constructor", "prototype");

  private JsonPointer() {}

  /**
   * Divides a path into segments and decodes the escape sequences.
   *
   * <p>The method ignores empty segments. Thus {@code ""}, {@code "/"} and {@code "///"} are the
   * root, and {@code "foo"}, {@code "/foo"} and {@code "/foo/"} are the same value.
   *
   * @param path the path to divide
   * @return the decoded segments
   * @throws A2uiDataError if a {@code ~} is not before {@code 0} or {@code 1}, or if a segment is
   *     forbidden
   */
  static List<String> parse(String path) {
    for (int tilde = path.indexOf('~'); tilde >= 0; tilde = path.indexOf('~', tilde + 1)) {
      char next = tilde + 1 < path.length() ? path.charAt(tilde + 1) : '\0';
      if (next != '0' && next != '1') {
        throw new A2uiDataError(
            "Invalid escape sequence in path '" + path + "': '~' must be followed by '0' or '1'.",
            path);
      }
    }

    List<String> segments = new ArrayList<>();
    for (String raw : path.split("/", -1)) {
      if (raw.isEmpty()) {
        continue;
      }
      // Decode "~1" first. Thus "~01" becomes "~1" and not "/".
      String segment = raw.replace("~1", "/").replace("~0", "~");
      if (FORBIDDEN_SEGMENTS.contains(segment)) {
        throw new A2uiDataError(
            "Forbidden path segment '" + segment + "' in path '" + path + "'.", path);
      }
      segments.add(segment);
    }
    return segments;
  }

  /**
   * Joins decoded segments into the canonical absolute pointer.
   *
   * @param segments the decoded segments
   * @return the pointer. The root is {@code "/"}.
   */
  static String build(List<String> segments) {
    if (segments.isEmpty()) {
      return "/";
    }
    StringBuilder pointer = new StringBuilder();
    for (String segment : segments) {
      pointer.append('/').append(segment.replace("~", "~0").replace("/", "~1"));
    }
    return pointer.toString();
  }

  /**
   * Gets the list index that a segment gives.
   *
   * <p>A list index is {@code 0}, or digits that do not start with a zero. If the index is too
   * large for an {@code int}, the result is {@link Integer#MAX_VALUE}.
   *
   * @param segment the segment to read
   * @return the index, or an empty result if the segment is not a list index
   */
  static OptionalInt listIndex(String segment) {
    if (segment.isEmpty()) {
      return OptionalInt.empty();
    }
    for (int i = 0; i < segment.length(); i++) {
      char c = segment.charAt(i);
      if (c < '0' || c > '9') {
        return OptionalInt.empty();
      }
    }
    if (segment.length() > 1 && segment.charAt(0) == '0') {
      return OptionalInt.empty();
    }
    return OptionalInt.of(segment.length() > 9 ? Integer.MAX_VALUE : Integer.parseInt(segment));
  }

  /**
   * Calculates the absolute form of a path.
   *
   * <p>A path that starts with {@code /} is absolute, and the method returns it without changes.
   * The paths {@code ""} and {@code "."} are the base. The method appends all other paths to the
   * base.
   *
   * @param path the path to resolve
   * @param basePath the base path. A null or empty base is the root.
   * @return the absolute path
   */
  static String resolve(String path, String basePath) {
    if (path.startsWith("/")) {
      return path;
    }
    String base;
    if (basePath == null || basePath.isEmpty()) {
      base = "/";
    } else if (basePath.startsWith("/")) {
      base = basePath;
    } else {
      base = "/" + basePath;
    }
    while (base.length() > 1 && base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    if (path.isEmpty() || path.equals(".")) {
      return base;
    }
    return base.equals("/") ? "/" + path : base + "/" + path;
  }
}
