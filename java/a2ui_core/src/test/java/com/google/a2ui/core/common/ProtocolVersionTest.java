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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProtocolVersionTest {
  @Test
  void theWireValueHasThePrefixOnlyBeforeVersion1() {
    assertEquals("v0.8", ProtocolVersion.V0_8.wireValue());
    assertEquals("v0.9", ProtocolVersion.V0_9.wireValue());
    assertEquals("v0.9.1", ProtocolVersion.V0_9_1.wireValue());
    assertEquals("1.0", ProtocolVersion.V1_0.wireValue());
  }

  @Test
  void tryParseReadsEachWireValue() {
    for (ProtocolVersion version : ProtocolVersion.values()) {
      assertEquals(Optional.of(version), ProtocolVersion.tryParse(version.wireValue()));
    }
  }

  @Test
  void tryParseAcceptsAVersionWithOrWithoutThePrefix() {
    assertEquals(Optional.of(ProtocolVersion.V1_0), ProtocolVersion.tryParse("v1.0"));
    assertEquals(Optional.of(ProtocolVersion.V1_0), ProtocolVersion.tryParse("1.0.0"));
    assertEquals(Optional.of(ProtocolVersion.V0_9_1), ProtocolVersion.tryParse("0.9.1"));
    assertEquals(Optional.of(ProtocolVersion.V0_9), ProtocolVersion.tryParse("0.9.0"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "2.0", "0.7", "1.0.1", "latest"})
  void tryParseReturnsAnEmptyResultForTextThatIsNotOneOfTheVersions(String text) {
    assertEquals(Optional.empty(), ProtocolVersion.tryParse(text));
  }

  @Test
  void isAtLeastComparesTheVersions() {
    assertTrue(ProtocolVersion.V1_0.isAtLeast(ProtocolVersion.V0_9_1));
    assertTrue(ProtocolVersion.V0_9.isAtLeast(ProtocolVersion.V0_9));
    assertFalse(ProtocolVersion.V0_9.isAtLeast(ProtocolVersion.V0_9_1));
    assertFalse(ProtocolVersion.V0_8.isAtLeast(ProtocolVersion.V1_0));
  }
}
