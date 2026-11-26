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

package com.google.a2ui.core.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.common.Subscription;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** The schedulers of the SDK and the options of a call. These tests use the real timer thread. */
class RpcSchedulerTest {
  private static final Duration SHORT = Duration.ofMillis(1);

  @Test
  void theTimerThreadSchedulerRunsATaskOnTheDaemonThreadOfTheSdk() throws Exception {
    CompletableFuture<Thread> ran = new CompletableFuture<>();

    RpcScheduler.timerThread().schedule(SHORT, () -> ran.complete(Thread.currentThread()));

    Thread thread = ran.get(5, TimeUnit.SECONDS);
    assertEquals("a2ui-rpc-timeouts", thread.getName());
    assertTrue(thread.isDaemon());
  }

  @Test
  void theExecutorSchedulerGivesTheTaskToTheExecutor() throws Exception {
    CompletableFuture<Runnable> given = new CompletableFuture<>();
    AtomicBoolean ran = new AtomicBoolean();

    RpcScheduler.onExecutor(given::complete).schedule(SHORT, () -> ran.set(true));

    Runnable task = given.get(5, TimeUnit.SECONDS);
    // The timer thread gave the task to the executor and did not run it.
    assertFalse(ran.get());
    task.run();
    assertTrue(ran.get());
    assertThrows(NullPointerException.class, () -> RpcScheduler.onExecutor(null));
  }

  @Test
  void aCancelledTaskDoesNotRun() throws Exception {
    AtomicBoolean ran = new AtomicBoolean();
    CountDownLatch later = new CountDownLatch(1);

    Subscription first =
        RpcScheduler.timerThread().schedule(Duration.ofMillis(50), () -> ran.set(true));
    first.cancel();
    first.cancel();
    // The timer has one thread. Thus a task with a longer delay runs after the time of the first.
    RpcScheduler.timerThread().schedule(Duration.ofMillis(100), later::countDown);

    assertTrue(later.await(5, TimeUnit.SECONDS));
    assertFalse(ran.get());
  }

  @Test
  void theOptionsOfACallGiveATimeoutOrAnId() {
    assertEquals(Optional.empty(), CallOptions.DEFAULT.timeout());
    assertEquals(Optional.empty(), CallOptions.DEFAULT.functionCallId());
    assertEquals(
        new CallOptions(Optional.of(Duration.ofSeconds(2)), Optional.empty()),
        CallOptions.withTimeout(Duration.ofSeconds(2)));
    assertEquals(
        new CallOptions(Optional.empty(), Optional.of("call-1")),
        CallOptions.withFunctionCallId("call-1"));
    assertThrows(NullPointerException.class, () -> new CallOptions(null, Optional.empty()));
    assertThrows(NullPointerException.class, () -> CallOptions.withTimeout(null));
  }
}
