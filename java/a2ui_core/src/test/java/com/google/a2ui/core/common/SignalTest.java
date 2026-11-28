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

package com.google.a2ui.core.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SignalTest {
  @Test
  void getReturnsTheInitialValueAndThenTheNewValue() {
    Signal<String> signal = new Signal<>("a");
    assertEquals("a", signal.get());

    signal.set("b");

    assertEquals("b", signal.get());
  }

  @Test
  void subscribeDoesNotSendTheCurrentValue() {
    Signal<String> signal = new Signal<>("a");
    List<String> seen = new ArrayList<>();

    signal.subscribe(seen::add);

    assertEquals(List.of(), seen);
  }

  @Test
  void setNotifiesTheListenersWhenTheValueChanges() {
    Signal<String> signal = new Signal<>("a");
    List<String> seen = new ArrayList<>();
    signal.subscribe(seen::add);

    signal.set("b");
    signal.set("b");
    signal.set("c");

    assertEquals(List.of("b", "c"), seen);
  }

  @Test
  void aSignalAcceptsNullValues() {
    Signal<String> signal = new Signal<>(null);
    List<String> seen = new ArrayList<>();
    signal.subscribe(seen::add);

    signal.set(null);
    signal.set("a");
    signal.set(null);

    assertNull(signal.get());
    List<String> expected = new ArrayList<>();
    expected.add("a");
    expected.add(null);
    assertEquals(expected, seen);
  }

  @Test
  void aBatchSendsOneNotificationForEachChangedSignal() {
    Signal<Integer> first = new Signal<>(1);
    Signal<Integer> second = new Signal<>(10);
    List<String> seen = new ArrayList<>();
    first.subscribe(value -> seen.add("first:" + value + ",second:" + second.get()));
    second.subscribe(value -> seen.add("second:" + value));

    Reactive.batch(() -> {
      first.set(2);
      first.set(3);
      second.set(20);
      assertEquals(List.of(), seen);
    });

    assertEquals(List.of("first:3,second:20", "second:20"), seen);
  }

  @Test
  void aBatchDoesNotNotifyWhenTheValueReturnsToTheInitialValue() {
    Signal<Integer> signal = new Signal<>(1);
    List<Integer> seen = new ArrayList<>();
    signal.subscribe(seen::add);

    Reactive.batch(() -> {
      signal.set(2);
      signal.set(1);
    });

    assertEquals(List.of(), seen);
  }

  @Test
  void aBatchKeepsANullInitialValue() {
    Signal<String> signal = new Signal<>(null);
    List<String> seen = new ArrayList<>();
    signal.subscribe(seen::add);

    Reactive.batch(() -> {
      signal.set("a");
      signal.set(null);
    });

    assertEquals(List.of(), seen);
  }

  @Test
  void anInnerBatchNotifiesWhenTheOutermostBatchEnds() {
    Signal<Integer> signal = new Signal<>(1);
    List<Integer> seen = new ArrayList<>();
    signal.subscribe(seen::add);

    Reactive.batch(() -> {
      Reactive.batch(() -> signal.set(2));
      assertEquals(List.of(), seen);
    });

    assertEquals(List.of(2), seen);
  }

  @Test
  void aBatchNotifiesWhenTheBlockThrows() {
    Signal<Integer> signal = new Signal<>(1);
    List<Integer> seen = new ArrayList<>();
    signal.subscribe(seen::add);

    assertThrows(
        IllegalStateException.class,
        () ->
            Reactive.batch(() -> {
              signal.set(2);
              throw new IllegalStateException("failure");
            }));

    assertEquals(List.of(2), seen);
  }

  @Test
  void aListenerCanWriteASignalDuringTheFlush() {
    Signal<Integer> source = new Signal<>(1);
    Signal<Integer> derived = new Signal<>(10);
    List<Integer> seen = new ArrayList<>();
    source.subscribe(value -> derived.set(value * 10));
    derived.subscribe(seen::add);

    Reactive.batch(() -> source.set(2));

    assertEquals(List.of(20), seen);
  }

  @Test
  void aDisposedSignalDoesNotNotify() {
    Signal<Integer> signal = new Signal<>(1);
    List<Integer> seen = new ArrayList<>();
    signal.subscribe(seen::add);

    signal.dispose();
    signal.set(2);

    assertEquals(List.of(), seen);
    assertEquals(2, signal.get());
  }
}
