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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.A2uiSurface;
import java.awt.Component;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JButton;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The tests of the behavior of the three columns, with no display. */
class GalleryPanelTest {
  private final List<URI> opened = new ArrayList<>();
  private final A2uiServices services = Headless.services().withUrlOpener(opened::add);
  private GalleryPanel gallery;

  private void open(List<Example> examples) {
    Headless.onEdt(() -> {
      gallery = new GalleryPanel(examples, services);
      Headless.mount(gallery.component(), 1280, 800);
    });
  }

  private void select(String id) {
    Headless.onEdt(() -> assertTrue(gallery.select(id), id));
    Headless.settle(gallery.component());
  }

  private A2uiSurface surface() {
    return gallery.surfaceColumn().surface().orElseThrow();
  }

  private <T extends Component> List<T> find(Class<T> type) {
    return Headless.find(surface(), type::isInstance).stream().map(type::cast).toList();
  }

  @Test
  void noExampleIsSelectedAtTheStart() {
    open(Headless.examples());

    Headless.onEdt(() -> {
      assertTrue(gallery.example().isEmpty());
      assertTrue(gallery.session().isEmpty());
      assertTrue(gallery.surfaceColumn().surface().isEmpty());
      assertEquals("No surface.", gallery.inspector().dataText());
      assertFalse(gallery.select("no-such-example"));
    });
  }

  @Test
  void anEditOfAFieldUpdatesTheDataModelTab() {
    open(Headless.examples());
    select("scaffold-simple-login-form");

    Headless.onEdt(() -> {
      assertFalse(gallery.inspector().dataText().contains("ada"));

      find(JTextField.class).get(0).setText("ada");

      assertTrue(
          gallery.inspector().dataText().contains("\"username\""), gallery.inspector().dataText());
      assertTrue(gallery.inspector().dataText().contains("\"ada\""));
    });
  }

  @Test
  void aClickOfAButtonAddsTheActionToTheLogAndClearRemovesIt() {
    open(Headless.examples());
    select("scaffold-interactive-button");

    Headless.onEdt(() -> {
      assertEquals(0, gallery.inspector().logEntries());

      find(JButton.class).get(0).doClick(0);

      assertEquals(1, gallery.inspector().logEntries());
      assertTrue(gallery.inspector().logText().contains("\"action\""));
      assertTrue(gallery.inspector().logText().contains("button_clicked"));

      gallery.inspector().clearLog();

      assertEquals(0, gallery.inspector().logEntries());
      assertEquals("", gallery.inspector().logText());
    });
  }

  @Test
  void reloadMakesANewProcessorAndANewSurface() {
    open(Headless.examples());
    select("scaffold-simple-login-form");

    Headless.onEdt(() -> {
      MessageProcessor<?> before = gallery.session().orElseThrow().processor();
      A2uiSurface surfaceBefore = surface();
      find(JTextField.class).get(0).setText("ada");

      gallery.reload();

      assertNotSame(before, gallery.session().orElseThrow().processor());
      assertNotSame(surfaceBefore, surface());
      assertFalse(surfaceBefore.isMounted(), "the surface before is not mounted");
      assertTrue(surface().isMounted());
      assertEquals("", find(JTextField.class).get(0).getText());
      assertFalse(gallery.inspector().dataText().contains("ada"));
      assertEquals("2 of 2 messages", gallery.surfaceColumn().progressText());
    });
  }

  @Test
  void restartProcessesNoMessageAndStepProcessesOneMessage() {
    open(Headless.examples());
    select("scaffold-incremental");

    Headless.onEdt(() -> {
      int total = gallery.example().orElseThrow().messages().size();
      assertEquals(total + " of " + total + " messages", gallery.surfaceColumn().progressText());

      gallery.restart();

      assertEquals("0 of " + total + " messages", gallery.surfaceColumn().progressText());
      assertTrue(gallery.surfaceColumn().surface().isEmpty());
      assertTrue(gallery.inspector().messagesText().contains("// Message 1 of 6: not processed"));

      gallery.step();

      assertEquals("1 of " + total + " messages", gallery.surfaceColumn().progressText());
      assertTrue(surface().isMounted(), "the first message makes the surface");
      assertTrue(gallery.inspector().messagesText().contains("// Message 1 of 6: processed"));
      assertTrue(gallery.inspector().messagesText().contains("// Message 2 of 6: not processed"));

      for (int index = 1; index < total; index++) {
        gallery.step();
      }
      gallery.step();

      assertEquals(total + " of " + total + " messages", gallery.surfaceColumn().progressText());
      assertEquals(List.of(), gallery.session().orElseThrow().failures());
    });
  }

  @Test
  void aStepThatWaitsForAComponentShowsAPlaceholder() {
    open(Headless.examples());
    select("scaffold-incremental");

    Headless.onEdt(() -> {
      gallery.restart();
      gallery.step();

      List<Component> placeholders =
          Headless.find(
              surface(), component -> Placeholders.COMPONENT_NAME.equals(component.getName()));
      assertEquals(1, placeholders.size(), "the root did not arrive");
    });
  }

  @Test
  void aMessageThatTheProcessorRejectsShowsItsFailureAndTheOtherMessagesRun(@TempDir Path directory)
      throws IOException {
    Files.writeString(
        directory.resolve("rejected.json"),
        """
        [{"version": "1.0", "createSurface": {"surfaceId": "s",
            "catalogId": "https://a2ui.org/specification/v1_0/catalogs/basic/catalog.json"}},
         {"version": "1.0", "updateComponents": {"surfaceId": "other", "components": [
            {"id": "root", "component": "Text", "text": "Never"}]}},
         {"version": "1.0", "updateComponents": {"surfaceId": "s", "components": [
            {"id": "root", "component": "Text", "text": "Hello"}]}}]
        """);
    Files.writeString(directory.resolve("broken.json"), "{");
    open(ExampleLoader.load(directory, Set.of("Text")));

    select("rejected");
    Headless.onEdt(() -> {
      assertTrue(
          gallery.surfaceColumn().failureText().startsWith("Message 2: "),
          gallery.surfaceColumn().failureText());
      assertEquals("3 of 3 messages", gallery.surfaceColumn().progressText());
      assertTrue(surface().viewTree().isPresent(), "the third message shows");
      assertTrue(gallery.inspector().logText().contains("\"error\""));
    });

    select("broken");
    Headless.onEdt(() -> {
      assertTrue(gallery.surfaceColumn().failureText().startsWith("The file is not JSON: "));
      assertTrue(gallery.surfaceColumn().surface().isEmpty());
      assertTrue(gallery.session().isEmpty());
      assertTrue(gallery.inspector().messagesText().startsWith("The file is not JSON: "));
      gallery.reload();
      gallery.restart();
      gallery.step();
      assertTrue(gallery.session().isEmpty());
    });
  }

  @Test
  void theListMarksAnExampleThatCannotShowCompletely() {
    Example complete = Headless.examples().get(0);
    // Each example of the repository is complete. Thus the test makes an example that is not.
    Example incomplete =
        new Example(
            "gauge-card",
            "Gauge card",
            "",
            List.of(),
            new TreeSet<>(Set.of("Card", "Gauge", "Text")),
            new TreeSet<>(Set.of("Gauge")),
            Optional.empty());

    assertEquals(complete.id(), ExampleListPanel.entryText(complete));
    assertEquals(
        incomplete.id() + "  [" + incomplete.absentViews().size() + " with no view]",
        ExampleListPanel.entryText(incomplete));
    assertTrue(
        ExampleListPanel.entryToolTip(incomplete).contains(incomplete.absentViews().first()));
    Example unreadable = Example.unreadable("x", "The file has no messages.");
    assertEquals("x  [cannot read]", ExampleListPanel.entryText(unreadable));
    assertEquals("The file has no messages.", ExampleListPanel.entryToolTip(unreadable));
  }

  @Test
  void theInspectorSelectsATabByItsName() {
    open(Headless.examples());

    Headless.onEdt(() -> {
      assertTrue(gallery.inspector().selectTab("data model"));
      assertTrue(gallery.inspector().selectTab("DataModel"));
      assertTrue(gallery.inspector().selectTab("log"));
      assertFalse(gallery.inspector().selectTab("other"));
    });
  }
}
