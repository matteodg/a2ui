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

package com.google.a2ui.swing.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class A2uiFallbacksTest {
  private static final A2uiPendingInfo PENDING = new A2uiPendingInfo("a", Optional.of("Column"));
  private static final A2uiErrorInfo ERROR =
      new A2uiErrorInfo(
          "b",
          Optional.empty(),
          new A2uiFallbackError(A2uiFallbackError.UNKNOWN_TYPE, Optional.of("Chart")));

  @Test
  void theEmptyFallbacksShowNothing() {
    assertNull(A2uiFallbacks.NONE.pending().apply(PENDING));
    assertNull(A2uiFallbacks.NONE.error().apply(ERROR));
  }

  @Test
  void eachWithMethodReplacesOneFallbackAndKeepsTheOther() {
    JLabel pending = new JLabel();
    JLabel error = new JLabel();

    A2uiFallbacks fallbacks =
        A2uiFallbacks.NONE.withPending(info -> pending).withError(info -> error);

    assertSame(pending, fallbacks.pending().apply(PENDING));
    assertSame(error, fallbacks.error().apply(ERROR));
    assertNull(A2uiFallbacks.NONE.withError(info -> error).pending().apply(PENDING));
  }

  @Test
  void theInfoTypesAreValuesWithNoNullPart() {
    assertEquals(new A2uiPendingInfo("a", Optional.of("Column")), PENDING);
    assertEquals("unknown-type", ERROR.error().type());
    assertEquals("cyclic", A2uiFallbackError.CYCLIC);
    assertThrows(NullPointerException.class, () -> new A2uiPendingInfo(null, Optional.empty()));
    assertThrows(NullPointerException.class, () -> new A2uiPendingInfo("a", null));
    assertThrows(NullPointerException.class, () -> new A2uiErrorInfo("a", Optional.empty(), null));
    assertThrows(NullPointerException.class, () -> new A2uiFallbackError(null, Optional.empty()));
    assertThrows(NullPointerException.class, () -> A2uiFallbacks.NONE.withPending(null));
  }
}
