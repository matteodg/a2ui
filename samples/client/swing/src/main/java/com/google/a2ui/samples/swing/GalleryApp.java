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

import com.google.a2ui.swing.catalog.basic.SwingBasicCatalog;
import com.google.a2ui.swing.surface.A2uiServices;
import java.awt.EventQueue;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The gallery of the Swing adapter. It shows each example of the basic catalog in a window with
 * three columns: the list of the examples, the surface, and an inspector.
 *
 * <p>The program reads the examples from the repository. The first program argument, or the system
 * property {@code gallery.examples}, gives the directory. Without them, the program looks for
 * {@code catalogs/basic/v1/examples} in the current directory and in each directory above it.
 *
 * <p>These system properties are optional:
 *
 * <ul>
 *   <li>{@code gallery.example}: the ID of the example to show first
 *   <li>{@code gallery.laf}: the look and feel: {@code System}, {@code Metal} or {@code Nimbus}
 *   <li>{@code gallery.tab}: the tab of the inspector to show first
 *   <li>{@code gallery.screenshot}: a PNG file. The program shows the example, writes an image of
 *       the window to the file and stops. See {@link Screenshot}.
 *   <li>{@code gallery.size}: only in the screenshot mode, a size such as {@code 900x600}. The
 *       program gives this size to the window after the example shows.
 * </ul>
 */
public final class GalleryApp {
  /** The system property that gives the example to show first. */
  static final String EXAMPLE_PROPERTY = "gallery.example";

  /** The system property that gives the look and feel. */
  static final String LOOK_AND_FEEL_PROPERTY = "gallery.laf";

  /** The system property that gives the tab of the inspector to show first. */
  static final String TAB_PROPERTY = "gallery.tab";

  /** The system property that starts the screenshot mode and gives its file. */
  static final String SCREENSHOT_PROPERTY = "gallery.screenshot";

  /** The system property that gives a new size to the window in the screenshot mode. */
  static final String SIZE_PROPERTY = "gallery.size";

  private GalleryApp() {}

  /**
   * Starts the gallery.
   *
   * @param args the directory of the examples, or nothing
   */
  public static void main(String[] args) {
    List<Example> examples;
    try {
      Optional<String> configured =
          args.length > 0 ? Optional.of(args[0]) : property(ExampleLoader.DIRECTORY_PROPERTY);
      Path directory = ExampleLoader.resolveDirectory(configured, Path.of(""));
      examples = ExampleLoader.load(directory, SwingBasicCatalog.viewNames());
    } catch (IllegalArgumentException | UncheckedIOException e) {
      System.err.println(e.getMessage());
      System.exit(1);
      return;
    }
    Optional<String> screenshot = property(SCREENSHOT_PROPERTY);
    if (screenshot.isPresent()) {
      try {
        Screenshot.run(
            examples,
            new Screenshot.Options(
                Path.of(screenshot.get()),
                property(EXAMPLE_PROPERTY),
                property(LOOK_AND_FEEL_PROPERTY),
                property(TAB_PROPERTY),
                property(SIZE_PROPERTY).map(Screenshot::parseSize)));
      } catch (RuntimeException e) {
        System.err.println("The screenshot failed. " + e.getMessage());
        System.exit(2);
      }
      return;
    }
    EventQueue.invokeLater(() -> open(examples));
  }

  private static void open(List<Example> examples) {
    String lookAndFeel = property(LOOK_AND_FEEL_PROPERTY).orElse(LookAndFeels.SYSTEM);
    try {
      LookAndFeels.apply(lookAndFeel, null);
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      lookAndFeel = "";
    }
    GalleryFrame window = new GalleryFrame(examples, A2uiServices.DEFAULT, lookAndFeel);
    window.frame().setVisible(true);
    property(TAB_PROPERTY).ifPresent(window.gallery().inspector()::selectTab);
    Optional<String> first =
        property(EXAMPLE_PROPERTY).or(() -> examples.stream().findFirst().map(Example::id));
    first.ifPresent(window.gallery()::select);
  }

  private static Optional<String> property(String name) {
    return Optional.ofNullable(System.getProperty(name)).filter(value -> !value.isBlank());
  }
}
