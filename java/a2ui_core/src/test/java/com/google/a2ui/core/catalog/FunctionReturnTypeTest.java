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

package com.google.a2ui.core.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FunctionReturnTypeTest {
  @Test
  void fromWireReadsEachWireValue() {
    for (FunctionReturnType type : FunctionReturnType.values()) {
      assertEquals(type, FunctionReturnType.fromWire(type.wireValue()));
    }
    assertEquals("validationResult", FunctionReturnType.VALIDATION_RESULT.wireValue());
  }

  @Test
  void fromWireReturnsAnyForATextThatItDoesNotKnow() {
    assertEquals(FunctionReturnType.ANY, FunctionReturnType.fromWire("future"));
    assertEquals(FunctionReturnType.ANY, FunctionReturnType.fromWire("String"));
    assertEquals(FunctionReturnType.ANY, FunctionReturnType.fromWire(null));
  }
}
