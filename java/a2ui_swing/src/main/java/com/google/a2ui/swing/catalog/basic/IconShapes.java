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

package com.google.a2ui.swing.catalog.basic;

import java.awt.geom.Path2D;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * The shapes of the named icons of the basic catalog.
 *
 * <p>The shapes are in the resource {@code icons.properties} of this package: one line for each
 * name of the {@code Icon} component, with SVG path data for a view box of {@value #VIEW_BOX} by
 * {@value #VIEW_BOX} units. The path data is from the Material Icons of Google, which have the
 * Apache License 2.0. The header of the resource gives the source. The module reads the resource
 * one time, when a surface shows the first named icon.
 */
final class IconShapes {
  /** The width and the height of the view box of each shape. */
  static final int VIEW_BOX = 24;

  private static final String RESOURCE = "icons.properties";

  private IconShapes() {}

  /** Keeps the shapes. The JVM makes it when code uses it for the first time. */
  private static final class Holder {
    static final Map<String, Path2D> SHAPES = load();

    private static Map<String, Path2D> load() {
      Properties lines = new Properties();
      try (InputStream in = IconShapes.class.getResourceAsStream(RESOURCE)) {
        if (in == null) {
          throw new IllegalStateException("The resource " + RESOURCE + " is absent.");
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
          lines.load(reader);
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      Map<String, Path2D> shapes = new TreeMap<>();
      for (String name : lines.stringPropertyNames()) {
        Path2D shape =
            SvgPath.parse(lines.getProperty(name))
                .orElseThrow(
                    () -> new IllegalStateException("The shape of '" + name + "' is not correct."));
        shapes.put(name, shape);
      }
      return Collections.unmodifiableMap(shapes);
    }
  }

  /**
   * Gets the shape of a named icon.
   *
   * @param name the name of the icon, as the catalog writes it, for example {@code arrowBack}
   * @return a copy of the shape, or an empty result if the module has no shape for the name
   */
  static Optional<Path2D> named(String name) {
    Path2D shape = name == null ? null : Holder.SHAPES.get(name);
    return shape == null ? Optional.empty() : Optional.of((Path2D) shape.clone());
  }

  /**
   * Gets the names of the icons that have a shape.
   *
   * @return the names, in alphabetical sequence. The set is read-only.
   */
  static Set<String> names() {
    return Holder.SHAPES.keySet();
  }
}
