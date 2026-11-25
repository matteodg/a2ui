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

package com.google.a2ui.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.exceptions.A2uiDataError;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataModelTest {
  @Test
  void aNewModelContainsAnEmptyObject() {
    assertEquals(Map.of(), new DataModel().get("/"));
  }

  @Test
  void getReadsNestedValuesAndListItems() {
    DataModel model = new DataModel(Map.of("user", Map.of("tags", List.of("a", "b"))));

    assertEquals("b", model.get("/user/tags/1"));
    assertEquals("b", model.get("user/tags/1"));
    assertNull(model.get("/user/tags/2"));
    assertNull(model.get("/user/name/first"));
  }

  @Test
  void setMakesTheAbsentContainers() {
    DataModel model = new DataModel();

    model.set("/user/tags/1", "b");

    assertEquals(Map.of("user", Map.of("tags", Arrays.asList(null, "b"))), model.get("/"));
  }

  @Test
  void theModelStoresIntegersAsLongAndOtherNumbersAsDouble() {
    DataModel model = new DataModel();

    model.set("/int", 5);
    model.set("/float", 1.5f);
    model.set("/bigInteger", BigInteger.TEN);
    model.set("/bigDecimal", new BigDecimal("2.5"));

    assertEquals(5L, model.get("/int"));
    assertEquals(1.5, model.get("/float"));
    assertEquals(10L, model.get("/bigInteger"));
    assertEquals(2.5, model.get("/bigDecimal"));
  }

  @Test
  void setRejectsAValueThatIsNotAJsonValue() {
    DataModel model = new DataModel();

    assertThrows(A2uiDataError.class, () -> model.set("/a", new Object()));
    assertThrows(A2uiDataError.class, () -> model.set("/a", Double.NaN));
    assertThrows(A2uiDataError.class, () -> model.set("/a", Map.of(1, "x")));
    assertEquals(Map.of(), model.get("/"));
  }

  @Test
  void setMakesACopyOfTheValue() {
    DataModel model = new DataModel();
    Map<String, Object> input = new HashMap<>();
    input.put("name", "Ada");

    model.set("/user", input);
    input.put("name", "Grace");

    assertEquals("Ada", model.get("/user/name"));
  }

  @Test
  void getReturnsAReadOnlyCopy() {
    DataModel model = new DataModel(Map.of("items", List.of("a")));

    @SuppressWarnings("unchecked")
    List<Object> items = (List<Object>) model.get("/items");

    assertThrows(UnsupportedOperationException.class, () -> items.add("b"));
  }

  @Test
  void aNullValueRemovesAMapKeyAndKeepsTheLengthOfAList() {
    DataModel model = new DataModel(Map.of("a", 1, "items", List.of("x", "y")));

    model.set("/a", null);
    model.delete("/items/0");
    model.delete("/absent/path");

    assertFalse(model.has("/a"));
    assertTrue(model.has("/items/0"));
    assertEquals(Map.of("items", Arrays.asList(null, "y")), model.get("/"));
  }

  @Test
  void aNullValueAtTheRootGivesAnEmptyObject() {
    DataModel model = new DataModel(Map.of("a", 1));

    model.set("/", null);

    assertEquals(Map.of(), model.get("/"));
  }

  @Test
  void setRejectsAPathThroughAPrimitiveValue() {
    DataModel model = new DataModel(Map.of("name", "Ada"));

    A2uiDataError error = assertThrows(A2uiDataError.class, () -> model.set("/name/first", "A"));

    assertEquals("/name/first", error.path().orElseThrow());
    assertEquals("Ada", model.get("/name"));
  }

  @Test
  void setRejectsAListSegmentThatIsNotAnIndex() {
    DataModel model = new DataModel(Map.of("items", List.of("x")));

    assertThrows(A2uiDataError.class, () -> model.set("/items/first", "y"));
    assertThrows(A2uiDataError.class, () -> model.set("/items/10001", "y"));
    assertThrows(A2uiDataError.class, () -> model.set("/new/10001", "y"));
    assertFalse(model.has("/new"));
  }

  @Test
  void aSubscriberGetsTheChangesOfItsPathItsParentsAndItsChildren() {
    DataModel model = new DataModel(Map.of("user", Map.of("name", "Ada")));
    List<Object> root = new ArrayList<>();
    List<Object> user = new ArrayList<>();
    List<Object> name = new ArrayList<>();
    List<Object> other = new ArrayList<>();
    model.subscribe("/", root::add);
    model.subscribe("/user", user::add);
    model.subscribe("/user/name", name::add);
    model.subscribe("/other", other::add);

    model.set("/user/name", "Grace");

    assertEquals(List.of(Map.of("user", Map.of("name", "Grace"))), root);
    assertEquals(List.of(Map.of("name", "Grace")), user);
    assertEquals(List.of("Grace"), name);
    assertEquals(List.of(), other);

    model.set("/user", Map.of("name", "Linus"));

    assertEquals(List.of("Grace", "Linus"), name);
  }

  @Test
  void aWriteOfTheSameValueDoesNotNotify() {
    DataModel model = new DataModel(Map.of("user", Map.of("name", "Ada")));
    List<Object> seen = new ArrayList<>();
    model.subscribe("/user", seen::add);

    model.set("/user/name", "Ada");
    model.set("/user", Map.of("name", "Ada"));

    assertEquals(List.of(), seen);
  }

  @Test
  void theSubscriptionGivesTheCurrentValue() {
    DataModel model = new DataModel(Map.of("count", 1));
    DataSubscription subscription = model.subscribe("count", value -> {});

    assertEquals(1L, subscription.value());

    model.set("/count", 2);

    assertEquals(2L, subscription.value());
  }

  @Test
  void aCancelledSubscriptionGetsNoMoreChanges() {
    DataModel model = new DataModel();
    List<Object> seen = new ArrayList<>();
    DataSubscription subscription = model.subscribe("/a", seen::add);

    model.set("/a", 1);
    subscription.cancel();
    subscription.cancel();
    model.set("/a", 2);

    assertEquals(List.of(1L), seen);
    assertEquals(1L, subscription.value());
  }

  @Test
  void aDisposedModelDoesNotNotify() {
    DataModel model = new DataModel();
    List<Object> seen = new ArrayList<>();
    model.subscribe("/a", seen::add);

    model.dispose();
    model.set("/a", 1);

    assertEquals(List.of(), seen);
    assertEquals(1L, model.get("/a"));
  }

  @Test
  void resolvePathAppendsARelativePathToTheBase() {
    assertEquals("/users/0/name", DataModel.resolvePath("name", "/users/0"));
    assertEquals("/name", DataModel.resolvePath("/name", "/users/0"));
    assertEquals("/name", DataModel.resolvePath("name", null));
  }
}
