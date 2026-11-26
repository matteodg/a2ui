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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ViewAnalysisTest {
  private static Map<String, Object> component(String id, String type) {
    return Map.of("id", id, "component", type);
  }

  @Test
  void theTypesComeFromTheSurfaceMessageAndFromTheComponentUpdates() {
    List<Object> messages =
        List.of(
            Map.of(
                "version",
                "1.0",
                "createSurface",
                Map.of(
                    "surfaceId",
                    "s",
                    "components",
                    List.of(component("root", "Column"), component("a", "Text")))),
            Map.of(
                "version",
                "1.0",
                "updateComponents",
                Map.of(
                    "surfaceId",
                    "s",
                    "components",
                    List.of(component("b", "Card"), component("c", "Text")))),
            Map.of("version", "1.0", "updateDataModel", Map.of("surfaceId", "s", "value", "Icon")));

    assertEquals(
        List.of("Card", "Column", "Text"), List.copyOf(ViewAnalysis.componentTypes(messages)));
  }

  @Test
  void aSurfaceMessageWithNoComponentsHasNoTypes() {
    List<Object> messages =
        List.of(Map.of("version", "1.0", "createSurface", Map.of("surfaceId", "s")));

    assertEquals(Set.of(), ViewAnalysis.componentTypes(messages));
  }

  @Test
  void aValueThatIsNotAMessageOrNotAComponentHasNoTypes() {
    List<Object> components = new ArrayList<>();
    components.add("text");
    components.add(null);
    components.add(Map.of("id", "a"));
    components.add(Map.of("id", "b", "component", 7));
    List<Object> messages = new ArrayList<>();
    messages.add("text");
    messages.add(null);
    messages.add(Map.of("updateComponents", "text"));
    messages.add(Map.of("updateComponents", Map.of("components", "text")));
    messages.add(Map.of("updateComponents", Map.of("components", components)));

    assertEquals(Set.of(), ViewAnalysis.componentTypes(messages));
  }

  @Test
  void theAbsentViewsAreTheTypesThatHaveNoView() {
    assertEquals(
        List.of("Card", "Icon"),
        List.copyOf(
            ViewAnalysis.absentViews(
                List.of("Text", "Icon", "Card", "Column"), Set.of("Text", "Column", "Row"))));
    assertEquals(Set.of(), ViewAnalysis.absentViews(List.of("Text"), Set.of("Text")));
  }
}
