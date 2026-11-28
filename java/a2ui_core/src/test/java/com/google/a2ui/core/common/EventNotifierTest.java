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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventNotifierTest {
  @Test
  void emitCallsEachListenerInTheSequenceOfRegistration() {
    EventNotifier<String> notifier = new EventNotifier<>();
    List<String> calls = new ArrayList<>();
    notifier.subscribe(event -> calls.add("first:" + event));
    notifier.subscribe(event -> calls.add("second:" + event));

    notifier.emit("a");

    assertEquals(List.of("first:a", "second:a"), calls);
  }

  @Test
  void aCancelledSubscriptionGetsNoMoreEvents() {
    EventNotifier<String> notifier = new EventNotifier<>();
    List<String> calls = new ArrayList<>();
    Subscription subscription = notifier.subscribe(calls::add);

    notifier.emit("a");
    subscription.cancel();
    subscription.cancel();
    notifier.emit("b");

    assertEquals(List.of("a"), calls);
    assertEquals(0, notifier.listenerCount());
  }

  @Test
  void oneListenerCanRegisterTwoTimes() {
    EventNotifier<String> notifier = new EventNotifier<>();
    List<String> calls = new ArrayList<>();
    EventListener<String> listener = calls::add;
    Subscription first = notifier.subscribe(listener);
    notifier.subscribe(listener);

    notifier.emit("a");
    first.cancel();
    notifier.emit("b");

    assertEquals(List.of("a", "a", "b"), calls);
  }

  @Test
  void aListenerAddedDuringEmitDoesNotGetThatEvent() {
    EventNotifier<String> notifier = new EventNotifier<>();
    List<String> calls = new ArrayList<>();
    notifier.subscribe(event -> notifier.subscribe(calls::add));

    notifier.emit("a");

    assertEquals(List.of(), calls);
    assertEquals(2, notifier.listenerCount());
  }

  @Test
  void aListenerRemovedDuringEmitDoesNotGetThatEvent() {
    EventNotifier<String> notifier = new EventNotifier<>();
    List<String> calls = new ArrayList<>();
    Subscription[] second = new Subscription[1];
    notifier.subscribe(event -> second[0].cancel());
    second[0] = notifier.subscribe(calls::add);

    notifier.emit("a");

    assertEquals(List.of(), calls);
  }

  @Test
  void emitContinuesAfterAFailureAndThrowsTheFirstException() {
    EventNotifier<String> notifier = new EventNotifier<>();
    List<String> calls = new ArrayList<>();
    IllegalStateException first = new IllegalStateException("first");
    notifier.subscribe(event -> {
      throw first;
    });
    notifier.subscribe(event -> {
      throw new IllegalStateException("second");
    });
    notifier.subscribe(calls::add);

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> notifier.emit("a"));

    assertSame(first, thrown);
    assertEquals(List.of("a"), calls);
  }

  @Test
  void aDisposedNotifierIgnoresEventsAndSubscriptions() {
    EventNotifier<String> notifier = new EventNotifier<>();
    List<String> calls = new ArrayList<>();
    notifier.subscribe(calls::add);

    notifier.dispose();
    notifier.subscribe(calls::add);
    notifier.emit("a");

    assertEquals(List.of(), calls);
    assertEquals(0, notifier.listenerCount());
  }
}
