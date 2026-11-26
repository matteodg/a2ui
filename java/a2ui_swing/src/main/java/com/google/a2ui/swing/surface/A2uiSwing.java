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

package com.google.a2ui.swing.surface;

import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.rpc.RpcScheduler;
import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import javax.swing.Timer;

/**
 * Helpers that keep the core on the event dispatch thread of Swing.
 *
 * <p>The core is not thread-safe, and Swing has one UI thread. Thus a Swing application uses the
 * message processor, the surfaces and the views only on the event dispatch thread. The adapter
 * subscribes Swing code to the signals of the core directly. Thus the screen is up to date when
 * {@code processMessages} returns.
 *
 * <p>Call {@link #install(MessageProcessor)} one time for each processor. Give the messages of a
 * transport that has its own thread to {@link #process(MessageProcessor, Object)} or to {@link
 * #processLater(MessageProcessor, Object)}.
 */
public final class A2uiSwing {
  private A2uiSwing() {}

  /**
   * Prepares a message processor for a Swing application.
   *
   * <p>The method gives the RPC handler of the processor the scheduler of {@link #edtScheduler()}.
   * Thus the timeout of a call to the agent completes on the event dispatch thread. The method also
   * gives the handler the test of {@link #userActivation()}.
   *
   * @param processor the processor
   */
  public static void install(MessageProcessor<?> processor) {
    Objects.requireNonNull(processor, "processor");
    processor.rpc().setScheduler(edtScheduler());
    processor.rpc().setUserActivation(userActivation());
  }

  /**
   * Processes messages on the event dispatch thread and waits for the end. Each thread can call
   * this method. On the event dispatch thread, the method calls the processor directly.
   *
   * <p>Do not call this method from a thread that holds a lock that the event dispatch thread
   * needs. If that is possible, use {@link #processLater(MessageProcessor, Object)}.
   *
   * @param processor the processor
   * @param payload one message, a list of messages, or a {@code {"messages": [...]}} wrapper, as
   *     plain JSON values
   * @throws RuntimeException the exception of {@code processMessages}, for example an {@code
   *     A2uiValidationError} for a message that is not correct
   * @throws IllegalStateException if the thread is interrupted while it waits. The interrupt status
   *     of the thread stays set.
   */
  public static void process(MessageProcessor<?> processor, Object payload) {
    Objects.requireNonNull(processor, "processor");
    if (EventQueue.isDispatchThread()) {
      processor.processMessages(payload);
      return;
    }
    try {
      EventQueue.invokeAndWait(() -> processor.processMessages(payload));
    } catch (InvocationTargetException e) {
      switch (e.getCause()) {
        case RuntimeException failure -> throw failure;
        case Error failure -> throw failure;
        default -> throw new IllegalStateException(e.getCause());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "The thread was interrupted before the event dispatch thread processed the messages.", e);
    }
  }

  /**
   * Processes messages on the event dispatch thread at a later time. Each thread can call this
   * method. The method does not wait.
   *
   * @param processor the processor
   * @param payload one message, a list of messages, or a {@code {"messages": [...]}} wrapper, as
   *     plain JSON values
   * @return a future that completes on the event dispatch thread after the processor processed the
   *     messages. It fails with the exception of {@code processMessages}.
   */
  public static CompletableFuture<Void> processLater(
      MessageProcessor<?> processor, Object payload) {
    Objects.requireNonNull(processor, "processor");
    CompletableFuture<Void> done = new CompletableFuture<>();
    EventQueue.invokeLater(() -> {
      try {
        processor.processMessages(payload);
        done.complete(null);
      } catch (RuntimeException | Error e) {
        done.completeExceptionally(e);
      }
    });
    return done;
  }

  /**
   * Makes a scheduler that runs each task on the event dispatch thread, with a {@code
   * javax.swing.Timer}.
   *
   * @return the scheduler
   */
  public static RpcScheduler edtScheduler() {
    return (delay, task) -> {
      Objects.requireNonNull(task, "task");
      Timer timer = new Timer(millisOf(delay), event -> task.run());
      timer.setRepeats(false);
      timer.start();
      return timer::stop;
    };
  }

  /**
   * Makes the test that tells if a user gesture is present. The test gives true while the event
   * dispatch thread dispatches a mouse button event or a key event. It gives false on each other
   * thread.
   *
   * @return the test, for {@code RpcHandler.setUserActivation}
   */
  public static BooleanSupplier userActivation() {
    return () -> isUserGesture(EventQueue.getCurrentEvent());
  }

  /**
   * Makes sure that the caller is on the event dispatch thread.
   *
   * @throws IllegalStateException if the current thread is a different thread
   */
  public static void requireEventDispatchThread() {
    if (!EventQueue.isDispatchThread()) {
      throw new IllegalStateException(
          "A2UI Swing code must run on the event dispatch thread, but the thread is '"
              + Thread.currentThread().getName()
              + "'. Use A2uiSwing.process or SwingUtilities.invokeLater.");
    }
  }

  /** Tells if an event is a gesture of the user that can start an action. */
  static boolean isUserGesture(AWTEvent event) {
    return switch (event) {
      case KeyEvent _ -> true;
      case MouseEvent mouse ->
          switch (mouse.getID()) {
            case MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED ->
                true;
            default -> false;
          };
      case null, default -> false;
    };
  }

  /** Gets a delay in milliseconds for a Swing timer. A part of a millisecond counts as one. */
  private static int millisOf(Duration delay) {
    Objects.requireNonNull(delay, "delay");
    long millis = delay.plusNanos(999_999).toMillis();
    return (int) Math.max(1, Math.min(Integer.MAX_VALUE, millis));
  }
}
