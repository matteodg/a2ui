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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The side of an {@link EventSource} that sends the events.
 *
 * <p>This class is not thread-safe. Only the thread that owns the model can use a notifier.
 *
 * @param <T> the type of the events
 */
public final class EventNotifier<T> implements EventSource<T> {
  private final Set<Registration<T>> registrations = new LinkedHashSet<>();
  private boolean disposed;

  /** Makes a notifier that has no listeners. */
  public EventNotifier() {}

  /**
   * Gets the number of registered listeners.
   *
   * @return the number of listeners
   */
  public int listenerCount() {
    return registrations.size();
  }

  @Override
  public Subscription subscribe(EventListener<? super T> listener) {
    Objects.requireNonNull(listener, "listener");
    if (disposed) {
      return () -> {};
    }
    Registration<T> registration = new Registration<>(listener);
    registrations.add(registration);
    return () -> registrations.remove(registration);
  }

  /**
   * Sends an event to each listener, in the sequence of registration.
   *
   * <p>The notifier uses the list of listeners from the start of the call. It ignores a listener
   * that a different listener removed during the same call. If a listener throws an exception, the
   * notifier continues with the remaining listeners. Then it throws the first exception again.
   *
   * @param event the event to send
   */
  public void emit(T event) {
    if (disposed || registrations.isEmpty()) {
      return;
    }
    RuntimeException failure = null;
    List<Registration<T>> snapshot = new ArrayList<>(registrations);
    for (Registration<T> registration : snapshot) {
      if (!registrations.contains(registration)) {
        continue;
      }
      try {
        registration.listener.onEvent(event);
      } catch (RuntimeException e) {
        if (failure == null) {
          failure = e;
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  /** Removes each listener. Subsequent events and subscriptions have no effect. */
  public void dispose() {
    disposed = true;
    registrations.clear();
  }

  /** Gives each subscription its own identity. Thus one listener can register two times. */
  private static final class Registration<T> {
    private final EventListener<? super T> listener;

    private Registration(EventListener<? super T> listener) {
      this.listener = listener;
    }
  }
}
