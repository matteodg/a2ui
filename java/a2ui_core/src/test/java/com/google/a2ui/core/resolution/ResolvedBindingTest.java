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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.exceptions.A2uiDataError;
import com.google.a2ui.core.state.DataModel;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResolvedBindingTest {
  private final DataModel data = new DataModel(Map.of("name", "Ada"));

  @Test
  void aBindingKeepsItsValueAndIsNotWritable() {
    ResolvedBinding binding = new ResolvedBinding("Ada");

    assertEquals("Ada", binding.value());
    assertFalse(binding.isWritable());
    assertEquals(Optional.empty(), binding.asWritable());
    assertEquals("ResolvedBinding(Ada)", binding.toString());
    assertNull(new ResolvedBinding(null).value());
  }

  @Test
  void aWritableBindingWritesAtItsPathAndStaysASnapshot() {
    WritableBinding binding = new WritableBinding("Ada", "/name", data, false);

    binding.set("Grace");

    assertTrue(binding.isWritable());
    assertEquals(Optional.of(binding), binding.asWritable());
    assertEquals("/name", binding.path());
    assertEquals("Grace", data.get("/name"));
    assertEquals("Ada", binding.value());
    assertEquals("WritableBinding(/name = Ada)", binding.toString());
  }

  @Test
  void aWritableBindingOfABooleanPropertyWritesABoolean() {
    WritableBinding binding = new WritableBinding(false, "/accepted", data, true);

    binding.set("TRUE");
    assertEquals(true, data.get("/accepted"));

    binding.set(0L);
    assertEquals(false, data.get("/accepted"));

    binding.set(null);
    assertEquals(false, data.get("/accepted"));
  }

  @Test
  void aWriteThatTheDataModelRefusesFails() {
    WritableBinding binding = new WritableBinding(null, "/name/first", data, false);

    assertThrows(A2uiDataError.class, () -> binding.set("Ada"));
  }

  @Test
  void bindingsAreEqualIfTheirClassTheirValueAndTheirPathAreEqual() {
    assertEquals(new ResolvedBinding("a"), new ResolvedBinding("a"));
    assertEquals(new ResolvedBinding(null), new ResolvedBinding(null));
    assertEquals(new ResolvedBinding("a").hashCode(), new ResolvedBinding("a").hashCode());
    assertNotEquals(new ResolvedBinding("a"), new ResolvedBinding("b"));
    assertEquals(
        new WritableBinding("a", "/x", data, false), new WritableBinding("a", "/x", data, false));
    assertEquals(
        new WritableBinding("a", "/x", data, false).hashCode(),
        new WritableBinding("a", "/x", data, false).hashCode());
    assertNotEquals(
        new WritableBinding("a", "/x", data, false), new WritableBinding("a", "/y", data, false));
    assertNotEquals(new ResolvedBinding("a"), new WritableBinding("a", "/x", data, false));
    assertNotEquals(new WritableBinding("a", "/x", data, false), new ResolvedBinding("a"));
  }
}
