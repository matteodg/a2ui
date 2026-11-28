/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.a2ui.samples.swing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.surface.A2uiErrorInfo;
import com.google.a2ui.swing.surface.A2uiFallbackError;
import com.google.a2ui.swing.surface.A2uiPendingInfo;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class PlaceholdersTest {
  private static JComponent noView() {
    JComponent[] made = {null};
    Headless.onEdt(() ->
        made[0] =
            Placeholders.fallbacks()
                .error()
                .apply(
                    new A2uiErrorInfo(
                        "player",
                        Optional.of("Row"),
                        new A2uiFallbackError(A2uiFallbackError.NO_VIEW, Optional.of("Video")))));
    return made[0];
  }

  @Test
  void aPlaceholderTellsTheTypeAndTheIdOfTheComponent() {
    JLabel label = (JLabel) noView();

    assertEquals("No Swing view: Video 'player'", label.getText());
    assertEquals(label.getText(), label.getToolTipText());
    assertEquals(Placeholders.COMPONENT_NAME, label.getName());
  }

  @Test
  void aPlaceholderForAComponentThatDidNotArriveTellsItsId() {
    JComponent[] made = {null};
    Headless.onEdt(() ->
        made[0] =
            Placeholders.fallbacks()
                .pending()
                .apply(new A2uiPendingInfo("late", Optional.empty())));

    assertEquals("Waiting for the component 'late'", ((JLabel) made[0]).getText());
  }

  @Test
  void aPlaceholderCanBeNarrowerThanItsText() {
    JComponent label = noView();

    assertTrue(label.getPreferredSize().width > Placeholders.MINIMUM_WIDTH);
    assertEquals(Placeholders.MINIMUM_WIDTH, label.getMinimumSize().width);
    assertEquals(label.getPreferredSize().height, label.getMinimumSize().height);
  }
}
