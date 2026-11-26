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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    for (String name : IconShapes.names()) {
      Rectangle2D bounds = IconShapes.named(name).orElseThrow().getBounds2D();

      assertTrue(
          new Rectangle2D.Double(1, 1, IconShapes.VIEW_BOX - 2, IconShapes.VIEW_BOX - 2)
              .contains(bounds),
          name + " has the bounds " + bounds);
      assertTrue(
          Math.max(bounds.getWidth(), bounds.getHeight()) >= 12,
          name + " has the bounds " + bounds);
      // The center of the shape is near the center of the box.
      assertEquals(12, bounds.getCenterX(), 2.6, name);
      assertEquals(12, bounds.getCenterY(), 2.6, name);
    }
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
