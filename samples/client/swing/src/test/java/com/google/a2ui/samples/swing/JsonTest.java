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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
  @Test
  void aDocumentBecomesPlainJavaValues() {
    Object value = Json.parse("{\"a\": [1, 2.5, true, null, \"x\"], \"b\": {}}");

    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("a", java.util.Arrays.asList(1, 2.5, true, null, "x"));
    expected.put("b", Map.of());
    assertEquals(expected, value);
  }

  @Test
  void aTextThatIsNotJsonCannotBeRead() {
    assertThrows(IllegalArgumentException.class, () -> Json.parse("{"));
  }

  @Test
  void theJsonTextHasOneElementInEachLine() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("name", "Ada");
    value.put("tags", List.of("a", 2));
    value.put("empty", Map.of());
    value.put("none", List.of());

    assertEquals(
        """
        {
          "name": "Ada",
          "tags": [
            "a",
            2
          ],
          "empty": {},
          "none": []
        }\
        """,
        Json.pretty(value));
    assertEquals("{}", Json.pretty(Map.of()));
    assertEquals("[]", Json.pretty(List.of()));
    assertEquals("null", Json.pretty(null));
  }
}
