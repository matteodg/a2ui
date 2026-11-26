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

package com.google.a2ui.samples.swing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The tests of the options of the screenshot mode. The mode itself needs a display. */
class ScreenshotTest {
  @Test
  void aSizeHasAWidthAndAHeight() {
    assertEquals(new Dimension(900, 600), Screenshot.parseSize("900x600"));
    assertEquals(new Dimension(1280, 800), Screenshot.parseSize(" 1280 X 800 "));
  }

  @Test
  void aTextThatIsNotASizeGivesAMessage() {
    for (String text : List.of("", "900", "900x", "axb", "0x600", "900x-1", "1x2x3")) {
      IllegalArgumentException error =
          assertThrows(IllegalArgumentException.class, () -> Screenshot.parseSize(text), text);
      assertTrue(error.getMessage().contains("900x600"), error.getMessage());
    }
  }
}
