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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class IconShapesTest {
  /** Reads the names of the icons from the schema of the {@code Icon} component. */
  static List<String> catalogNames() {
    Map<?, ?> properties =
        (Map<?, ?>) BasicCatalog.api().components().get("Icon").schema().get("properties");
    Map<?, ?> name = (Map<?, ?>) properties.get("name");
    Map<?, ?> named = (Map<?, ?>) ((List<?>) name.get("oneOf")).get(0);
    return ((List<?>) named.get("enum")).stream().map(String.class::cast).toList();
  }

  @Test
  void eachNameOfTheCatalogHasAShapeAndNoOtherNameHasOne() {
    assertTrue(catalogNames().size() > 50, catalogNames().toString());
    assertEquals(new TreeSet<>(catalogNames()), IconShapes.names());
  }

  @Test
  void eachShapeIsInTheViewBoxAndUsesMostOfIt() {
    Rectangle2D box = new Rectangle2D.Double(0, 0, IconShapes.VIEW_BOX, IconShapes.VIEW_BOX);
    for (String name : catalogNames()) {
      Path2D shape = IconShapes.named(name).orElseThrow(() -> new AssertionError(name));
      Rectangle2D bounds = shape.getBounds2D();

      assertFalse(new Area(shape).isEmpty(), name + " has no area");
      assertTrue(box.contains(bounds), name + " has the bounds " + bounds);
      assertTrue(
          Math.max(bounds.getWidth(), bounds.getHeight()) >= 12,
          name + " has the bounds " + bounds);
    }
  }

  @Test
  void eachLineOfTheResourceIsPathDataThatTheReaderAccepts() throws IOException {
    Properties lines = new Properties();
    try (InputStream in = IconShapes.class.getResourceAsStream("icons.properties")) {
      lines.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    assertEquals(new TreeSet<>(catalogNames()), lines.stringPropertyNames());
    for (String name : catalogNames()) {
      String data = lines.getProperty(name);
      assertTrue(data.startsWith("M"), name);
      assertTrue(SvgPath.parse(data).isPresent(), name + " is not correct: " + data);
    }
  }

  @Test
  void theHolesOfTheShapesShow() {
    // Each point is in a hole of a Material shape: the head of the person, a part of the question
    // mark, the dot of the letter, the center of the wheel and the ring of the eye.
    Map<String, Point2D> holes =
        Map.of(
            "accountCircle", new Point2D.Double(12, 9.5),
            "help", new Point2D.Double(12, 18),
            "info", new Point2D.Double(12, 8),
            "settings", new Point2D.Double(12, 12),
            "visibility", new Point2D.Double(12, 8),
            "lock", new Point2D.Double(12, 4.5),
            "error", new Point2D.Double(12, 16),
            "search", new Point2D.Double(9.5, 9.5));
    holes.forEach((name, hole) -> {
      Path2D shape = IconShapes.named(name).orElseThrow();
      assertEquals(Path2D.WIND_NON_ZERO, shape.getWindingRule(), name);
      assertFalse(shape.contains(hole), name + " has no hole at " + hole);
    });
    // The points next to the holes are in the shapes.
    assertTrue(IconShapes.named("accountCircle").orElseThrow().contains(12, 3));
    assertTrue(IconShapes.named("visibility").orElseThrow().contains(12, 12));
    assertTrue(IconShapes.named("settings").orElseThrow().contains(12, 7.5));
  }

  @Test
  void aNameThatTheCatalogDoesNotHaveHasNoShape() {
    assertEquals(Optional.empty(), IconShapes.named("noSuchIcon"));
    assertEquals(Optional.empty(), IconShapes.named("Add"));
    assertEquals(Optional.empty(), IconShapes.named(""));
    assertEquals(Optional.empty(), IconShapes.named(null));
  }

  @Test
  void eachCallGivesACopyOfTheShape() {
    Path2D first = IconShapes.named("add").orElseThrow();
    Path2D second = IconShapes.named("add").orElseThrow();

    assertNotSame(first, second);
    assertEquals(first.getBounds2D(), second.getBounds2D());
    assertTrue(first.contains(12, 12));
  }
}
