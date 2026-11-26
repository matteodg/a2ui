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
import java.util.ArrayList;
import java.util.List;

/**
 * A scheduler for tests. It keeps each task, and a test runs the task. Thus a test of a timeout
 * does not wait, and the task runs on the thread of the test.
 */
public final class ManualScheduler implements RpcScheduler {
  /** One task that a handler scheduled. */
  public static final class Scheduled {
    private final Duration delay;
    private final Runnable task;
    private boolean cancelled;

    private Scheduled(Duration delay, Runnable task) {
      this.delay = delay;
      this.task = task;
    }

    /**
     * Gets the delay that the handler asked for.
     *
     * @return the delay
     */
    public Duration delay() {
      return delay;
    }

    /**
     * Tells if the handler cancelled the task.
     *
     * @return true if the handler cancelled the task
     */
    public boolean isCancelled() {
      return cancelled;
    }

    /** Runs the task, as a real scheduler does it when the delay ends. */
    public void fire() {
      task.run();
    }
  }

  private final List<Scheduled> scheduled = new ArrayList<>();

  /** Makes a scheduler that has no task. */
  public ManualScheduler() {}

  @Override
  public Subscription schedule(Duration delay, Runnable task) {
    Scheduled entry = new Scheduled(delay, task);
    scheduled.add(entry);
    return () -> entry.cancelled = true;
  }

  /**
   * Gets each task that a handler scheduled, in the sequence of the calls.
   *
   * @return the tasks
   */
  public List<Scheduled> scheduled() {
    return scheduled;
  }

  /**
   * Gets the last task that a handler scheduled.
   *
   * @return the task
   */
  public Scheduled last() {
    return scheduled.getLast();
  }
}
