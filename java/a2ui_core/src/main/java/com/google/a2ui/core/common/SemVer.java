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

package com.google.a2ui.core.common;

import com.google.a2ui.core.exceptions.A2uiValidationError;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A protocol version or a catalog version of the form {@code major[.minor[.patch]]}.
 *
 * <p>An absent part is zero. Thus {@code 1}, {@code 1.0} and {@code 1.0.0} are equal.
 *
 * @param major the major version
 * @param minor the minor version
 * @param patch the patch version
 */
public record SemVer(int major, int minor, int patch) implements Comparable<SemVer> {
  private static final Pattern PATTERN =
      Pattern.compile("[vV]?(\\d{1,9})(?:\\.(\\d{1,9}))?(?:\\.(\\d{1,9}))?");

  private static final Comparator<SemVer> ORDER =
      Comparator.comparingInt(SemVer::major)
          .thenComparingInt(SemVer::minor)
          .thenComparingInt(SemVer::patch);

  /**
   * Makes a version and makes sure that no part is negative.
   *
   * @throws A2uiValidationError if a part is negative
   */
  public SemVer {
    if (major < 0 || minor < 0 || patch < 0) {
      throw new A2uiValidationError("Invalid version: '" + major + "." + minor + "." + patch + "'");
    }
  }

  /**
   * Reads a version from text. The text can start with {@code v}.
   *
   * @param text the text to read, or null
   * @return the version, or an empty result if the text is not a version
   */
  public static Optional<SemVer> tryParse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    Matcher matcher = PATTERN.matcher(text.strip());
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(
        new SemVer(part(matcher.group(1)), part(matcher.group(2)), part(matcher.group(3))));
  }

  /**
   * Reads a version from text. The text can start with {@code v}.
   *
   * @param text the text to read, or null
   * @return the version
   * @throws A2uiValidationError if the text is not a version
   */
  public static SemVer parse(String text) {
    return tryParse(text)
        .orElseThrow(() -> new A2uiValidationError("Invalid version: '" + text + "'"));
  }

  @Override
  public int compareTo(SemVer other) {
    return ORDER.compare(this, other);
  }

  @Override
  public String toString() {
    return major + "." + minor + "." + patch;
  }

  private static int part(String digits) {
    return digits == null ? 0 : Integer.parseInt(digits);
  }
}
