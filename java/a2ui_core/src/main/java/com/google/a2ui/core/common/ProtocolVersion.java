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

import java.util.Optional;

/**
 * A version of the A2UI protocol, as catalogs and messages name it.
 *
 * <p>This SDK processes only v1.0 messages. The enum also lists the earlier versions. Thus the SDK
 * can read and write a catalog document of an earlier version, and it can tell that the document is
 * not a v1.0 catalog.
 */
public enum ProtocolVersion {
  /** Protocol v0.8. */
  V0_8("v0.8", new SemVer(0, 8, 0)),

  /** Protocol v0.9. */
  V0_9("v0.9", new SemVer(0, 9, 0)),

  /** Protocol v0.9.1. */
  V0_9_1("v0.9.1", new SemVer(0, 9, 1)),

  /** Protocol v1.0. */
  V1_0("1.0", new SemVer(1, 0, 0));

  private final String wireValue;
  private final SemVer semVer;

  ProtocolVersion(String wireValue, SemVer semVer) {
    this.wireValue = wireValue;
    this.semVer = semVer;
  }

  /**
   * Gets the text that messages and catalog documents use for this version.
   *
   * <p>The versions before 1.0 have the prefix {@code v}. Version 1.0 does not have it.
   *
   * @return the text of the version
   */
  public String wireValue() {
    return wireValue;
  }

  /**
   * Tells if this version is the same as a different version or is later.
   *
   * @param other the version to compare with
   * @return true if this version is not earlier than {@code other}
   */
  public boolean isAtLeast(ProtocolVersion other) {
    return semVer.compareTo(other.semVer) >= 0;
  }

  /**
   * Reads a version from text.
   *
   * <p>The prefix {@code v} and a last {@code .0} are optional. Thus {@code 1.0}, {@code v1.0} and
   * {@code 1.0.0} are {@link #V1_0}.
   *
   * @param text the text to read, or null
   * @return the version, or an empty result if the text is not one of the versions
   */
  public static Optional<ProtocolVersion> tryParse(String text) {
    Optional<SemVer> parsed = SemVer.tryParse(text);
    if (parsed.isEmpty()) {
      return Optional.empty();
    }
    for (ProtocolVersion version : values()) {
      if (version.semVer.equals(parsed.get())) {
        return Optional.of(version);
      }
    }
    return Optional.empty();
  }
}
