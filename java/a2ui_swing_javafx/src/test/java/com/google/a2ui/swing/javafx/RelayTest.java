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

package com.google.a2ui.swing.javafx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class RelayTest {
  /** A listener that writes each event to a list. */
  private static class Recorder implements MediaEngine.Listener {
    final List<String> events = new ArrayList<>();

    @Override
    public void ready(double durationSeconds) {
      events.add("ready " + durationSeconds);
    }

    @Override
    public void playing() {
      events.add("playing");
    }

    @Override
    public void paused() {
      events.add("paused");
    }

    @Override
    public void time(double seconds) {
      events.add("time " + seconds);
    }

    @Override
    public void ended() {
      events.add("ended");
    }

    @Override
    public void failed(String message) {
      events.add("failed " + message);
    }
  }

  private final Recorder recorder = new Recorder();
  private final List<Runnable> tasks = new ArrayList<>();
  private final Relay relay = new Relay(tasks::add, recorder);

  private void runTasks() {
    List<Runnable> now = new ArrayList<>(tasks);
    tasks.clear();
    now.forEach(Runnable::run);
  }

  @Test
  void eachEventIsOneTaskOfTheExecutorAndTheSequenceStays() {
    relay.ready(12.5);
    relay.playing();
    relay.time(1.5);
    relay.paused();
    relay.ended();
    relay.failed("no codec");

    // The relay does not call the target on the thread of the player.
    assertEquals(List.of(), recorder.events);
    assertEquals(6, tasks.size());

    runTasks();

    assertEquals(
        List.of("ready 12.5", "playing", "time 1.5", "paused", "ended", "failed no codec"),
        recorder.events);
  }

  @Test
  void aClosedRelayGivesNoEventAlsoNotAnEventThatWaitsInTheExecutor() {
    relay.playing();
    relay.time(2);

    relay.close();
    relay.ended();

    // The two events before the close are tasks. The event after the close is no task.
    assertEquals(2, tasks.size());
    runTasks();
    assertEquals(List.of(), recorder.events);
  }

  @Test
  void theEventsOfADifferentThreadArriveOnTheEventDispatchThread() throws Exception {
    List<String> threads = new ArrayList<>();
    CountDownLatch done = new CountDownLatch(1);
    Relay toSwing =
        new Relay(
            SwingUtilities::invokeLater,
            new Recorder() {
              @Override
              public void playing() {
                threads.add(SwingUtilities.isEventDispatchThread() ? "edt" : "other");
              }

              @Override
              public void ended() {
                done.countDown();
              }
            });

    Thread player =
        new Thread(() -> {
          toSwing.playing();
          toSwing.ended();
        });
    player.start();
    player.join();

    assertEquals(true, done.await(10, TimeUnit.SECONDS));
    assertEquals(List.of("edt"), threads);
  }
}
