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
import java.util.concurrent.Executor;

/**
 * Runs a task after a delay. An {@link RpcHandler} uses a scheduler for the timeout of each call to
 * the agent.
 *
 * <p>The task of a timeout completes the future of its call. Thus the code that the application
 * attached to that future runs on the thread that runs the task. The core is not thread-safe. Make
 * sure that the scheduler runs each task on the thread that owns the message processor.
 *
 * <ul>
 *   <li>{@link #onExecutor(Executor)} is correct for each application that can give work to its
 *       thread. A Swing application gives {@code SwingUtilities::invokeLater}.
 *   <li>A UI adapter can supply a scheduler that uses the timer of its toolkit, for example {@code
 *       javax.swing.Timer}.
 *   <li>{@link #timerThread()} is the scheduler of a new handler. It runs each task on a thread of
 *       the SDK.
 * </ul>
 */
@FunctionalInterface
public interface RpcScheduler {
  /**
   * Runs a task one time after a delay.
   *
   * @param delay the time to wait. It is more than zero.
   * @param task the task to run
   * @return the registration of the task. If the caller cancels it before the task starts, the task
   *     does not run. A task that started continues.
   */
  Subscription schedule(Duration delay, Runnable task);

  /**
   * Gets the scheduler that runs each task on the timer thread of the SDK. This is the scheduler of
   * a new {@link RpcHandler}.
   *
   * <p>The timer thread is one daemon thread with the name {@code a2ui-rpc-timeouts}. All the
   * handlers of the JVM share it. The SDK starts the thread at the first timeout that it schedules.
   *
   * <p>With this scheduler, a call that has a timeout completes its future on the timer thread. A
   * handler accepts that. But code that the application attached to the future also runs on the
   * timer thread, and that code must not use the core or the UI. If the application has such code,
   * use {@link #onExecutor(Executor)}.
   *
   * @return the scheduler
   */
  static RpcScheduler timerThread() {
    return RpcTimerThread::schedule;
  }

  /**
   * Makes a scheduler that measures the delay on the timer thread of the SDK and then gives the
   * task to an executor. Thus the task runs on the thread of the executor.
   *
   * <p>Give an executor that runs its work on the thread that owns the message processor. For a
   * Swing application, that executor is {@code SwingUtilities::invokeLater}. Then each timeout
   * completes its future on the event dispatch thread.
   *
   * @param executor the executor that runs each task. It must not refuse a task.
   * @return the scheduler
   */
  static RpcScheduler onExecutor(Executor executor) {
    Objects.requireNonNull(executor, "executor");
    return (delay, task) -> RpcTimerThread.schedule(delay, () -> executor.execute(task));
  }
}
