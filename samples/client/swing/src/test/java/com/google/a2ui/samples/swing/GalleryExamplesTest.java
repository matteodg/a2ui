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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.catalog.basic.SwingBasicCatalog;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.A2uiSurface;
import com.google.a2ui.swing.surface.MountedNode;
import java.awt.Component;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Shows each example of the basic catalog in the gallery, with no display. The test makes sure that
 * the processor accepts the messages and that the surface shows.
 */
class GalleryExamplesTest {
  private static final int WIDTH = 1280;
  private static final int HEIGHT = 800;

  private final List<URI> opened = new ArrayList<>();
  private final A2uiServices services = Headless.services().withUrlOpener(opened::add);

  private static List<Component> placeholders(Component root) {
    return Headless.find(
        root, component -> Placeholders.COMPONENT_NAME.equals(component.getName()));
  }

  /** Tells if the tree that a surface shows has a component of one of some types. */
  private static boolean shows(MountedNode node, Collection<String> types) {
    return types.contains(node.node().type())
        || node.children().stream().anyMatch(child -> shows(child, types));
  }

  @Test
  void theRepositoryHasTheExamplesAndTheCompleteExamplesAreFirst() {
    List<Example> examples = Headless.examples();

    assertTrue(examples.size() >= 43, "examples: " + examples.size());
    assertEquals(Optional.empty(), examples.stream().flatMap(e -> e.problem().stream()).findAny());
    List<Boolean> complete = examples.stream().map(Example::complete).toList();
    assertTrue(complete.get(0), "the first example is complete");
    assertEquals(
        complete.stream().sorted((first, second) -> Boolean.compare(second, first)).toList(),
        complete);
    assertTrue(examples.stream().anyMatch(example -> example.id().equals("login-form")));
  }

  /**
   * The README of the sample tells how many components have a view and how many examples show
   * completely. This test fails when a change of the views or of the examples makes these numbers
   * different.
   */
  @Test
  void theReadmeHasTheNumberOfTheViewsAndOfTheCompleteExamples() throws IOException {
    List<Example> examples = Headless.examples();
    long complete = examples.stream().filter(Example::complete).count();
    String readme = Files.readString(Path.of("README.md")).replaceAll("\\s+", " ");

    assertTrue(
        readme.contains(
            "views for "
                + SwingBasicCatalog.viewNames().size()
                + " of the "
                + SwingBasicCatalog.components().size()
                + " components of the basic catalog"),
        "the number of the views");
    assertTrue(
        readme.contains(
            "Thus " + complete + " of the " + examples.size() + " examples show completely"),
        "the number of the complete examples");
    if (complete < examples.size()) {
      assertTrue(
          readme.contains("The other " + (examples.size() - complete) + " examples show a"),
          "the number of the other examples");
    }
    // The README has the names of the views only while some components have none.
    if (SwingBasicCatalog.viewNames().size() < SwingBasicCatalog.components().size()) {
      for (String view : SwingBasicCatalog.viewNames()) {
        assertTrue(readme.contains("`" + view + "`"), view);
      }
    }
  }

  @TestFactory
  Stream<DynamicTest> theGalleryShowsEachExample() {
    List<Example> examples = Headless.examples();
    GalleryPanel[] gallery = {null};
    Headless.onEdt(() -> {
      gallery[0] = new GalleryPanel(examples, services);
      Headless.mount(gallery[0].component(), WIDTH, HEIGHT);
    });
    return examples.stream()
        .map(example -> DynamicTest.dynamicTest(example.id(), () -> show(gallery[0], example)));
  }

  private void show(GalleryPanel gallery, Example example) {
    Headless.onEdt(() -> assertTrue(gallery.select(example.id())));
    Headless.settle(gallery.component());

    Headless.onEdt(() -> {
      GallerySession session = gallery.session().orElseThrow();
      assertEquals(example.messages().size(), session.processed());
      assertEquals(List.of(), session.failures(), example.id());
      assertEquals("", gallery.surfaceColumn().failureText());
      A2uiSurface surface = gallery.surfaceColumn().surface().orElseThrow();
      assertTrue(surface.isMounted(), "the surface is mounted");
      assertTrue(surface.viewTree().isPresent(), "the surface has a root view");
      assertEquals(
          gallery.surfaceColumn().surfacePane().getViewport().getExtentSize().width,
          surface.getWidth(),
          "the surface has the width of the viewport");
      assertTrue(surface.getHeight() > 20, "height " + surface.getHeight());
      List<Component> placeholders = placeholders(surface);
      if (example.complete()) {
        assertEquals(0, placeholders.size(), "a complete example has no placeholder");
        assertTrue(gallery.surfaceColumn().absentText().startsWith("Each component"));
      } else {
        // The content of a modal that is not open is not in the tree of the surface.
        if (shows(surface.viewTree().orElseThrow(), example.absentViews())) {
          assertFalse(placeholders.isEmpty(), "an absent view shows as a placeholder");
        }
        for (String type : example.absentViews()) {
          assertTrue(gallery.surfaceColumn().absentText().contains(type), type);
        }
      }
      assertTrue(gallery.inspector().messagesText().contains("\"createSurface\""));
      assertFalse(gallery.inspector().dataText().equals("No surface."));
      assertEquals(List.of(), opened, "no example opens a URL by itself");
    });
  }
}
