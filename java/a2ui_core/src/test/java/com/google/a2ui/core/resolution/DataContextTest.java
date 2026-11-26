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

package com.google.a2ui.core.resolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiDataError;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class DataContextTest {
  /** Makes a surface whose default catalog is the given catalog and that has the given data. */
  private static SurfaceModel<ComponentApi> surface(
      Catalog<ComponentApi, ?> catalog, Map<String, Object> data) {
    SurfaceModel<ComponentApi> surface =
        new SurfaceModel<>(
            "s", ProtocolVersion.V1_0, catalog, Map.of(catalog.id(), catalog), false, null);
    surface.dataModel().set("/", data);
    return surface;
  }

  private static SurfaceModel<ComponentApi> surface(Map<String, Object> data) {
    return surface(BasicCatalog.api(), data);
  }

  @Test
  void aNewContextIsAtTheRootAndOutsideAListTemplate() {
    SurfaceModel<ComponentApi> surface = surface(Map.of());

    DataContext context = new DataContext(surface);

    assertSame(surface, context.surface());
    assertEquals("/", context.path());
    assertEquals(OptionalInt.empty(), context.index());
    assertEquals("DataContext(s, /)", context.toString());
  }

  @Test
  void aContextPathThatIsNotAbsoluteStartsAtTheRoot() {
    SurfaceModel<ComponentApi> surface = surface(Map.of());

    assertEquals("/user", new DataContext(surface, "user").path());
    assertEquals("/user", new DataContext(surface, "/user").path());
    assertEquals("/", new DataContext(surface, "").path());
  }

  @Test
  void resolvePathReadsARelativePathFromTheContextPath() {
    DataContext context = new DataContext(surface(Map.of()), "/user");

    assertEquals("/user/name", context.resolvePath("name"));
    assertEquals("/title", context.resolvePath("/title"));
    assertEquals("/user", context.resolvePath(""));
  }

  @Test
  void nestedKeepsTheListPositionUnlessTheCallGivesOne() {
    DataContext root = new DataContext(surface(Map.of()));

    DataContext item = root.nested("items/1", 1);
    DataContext field = item.nested("address");

    assertEquals("/items/1", item.path());
    assertEquals(OptionalInt.of(1), item.index());
    assertEquals("/items/1/address", field.path());
    assertEquals(OptionalInt.of(1), field.index());
    assertEquals(OptionalInt.of(0), field.nested("lines/0", 0).index());
    assertEquals(OptionalInt.empty(), root.nested("items").index());
    assertThrows(IllegalArgumentException.class, () -> root.nested("items/0", -1));
  }

  @Test
  void setWritesRelativeToTheContextPath() {
    SurfaceModel<ComponentApi> surface = surface(Map.of("user", Map.of("name", "Ada")));
    DataContext context = new DataContext(surface, "/user");

    context.set("name", "Grace");
    context.set("/title", "Dr");

    assertEquals("Grace", surface.dataModel().get("/user/name"));
    assertEquals("Dr", surface.dataModel().get("/title"));
    assertThrows(A2uiDataError.class, () -> context.set("name/first", "G"));
  }
}
