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

import com.google.a2ui.core.common.Subscription;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** The timer thread of the SDK: one daemon thread that all the RPC handlers of the JVM share. */
final class RpcTimerThread {
  /** The name of the timer thread. */
  static final String NAME = "a2ui-rpc-timeouts";

  private RpcTimerThread() {}

  /**
   * Runs a task one time on the timer thread, after a delay.
   *
   * @return the registration of the task. If the caller cancels it before the task starts, the task
   *     does not run.
   */
  static Subscription schedule(Duration delay, Runnable task) {
    Objects.requireNonNull(delay, "delay");
    Objects.requireNonNull(task, "task");
    ScheduledFuture<?> scheduled =
        Holder.TIMER.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
    return () -> scheduled.cancel(false);
  }

  /** Makes the timer when the JVM initializes the class. That is at the first timeout. */
  private static final class Holder {
    static final ScheduledThreadPoolExecutor TIMER = timer();

    private static ScheduledThreadPoolExecutor timer() {
      ScheduledThreadPoolExecutor timer =
          new ScheduledThreadPoolExecutor(
              1,
              work -> {
                Thread thread = new Thread(work, NAME);
                thread.setDaemon(true);
                return thread;
              });
      // A call that gets its response cancels its timeout. The timer must not keep that task.
      timer.setRemoveOnCancelPolicy(true);
      return timer;
    }
  }
}
