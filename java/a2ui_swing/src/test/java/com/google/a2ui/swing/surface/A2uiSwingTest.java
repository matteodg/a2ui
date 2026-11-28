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

import static com.google.a2ui.swing.testing.TestSurface.component;
import static com.google.a2ui.swing.testing.TestSurface.object;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.core.exceptions.A2uiRpcError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.validation.ValidationConfig;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.TestCatalog;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.InvocationEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class A2uiSwingTest {
  private final List<Map<String, Object>> sent = new ArrayList<>();
  private final MessageProcessor<SwingComponentImplementation> processor =
      new MessageProcessor<>(
          List.of(new TestCatalog().catalog()), ValidationConfig.RELAXED, sent::add);

  private static Map<String, Object> createSurface() {
    return Map.of(
        "version",
        "1.0",
        "createSurface",
        object(
            "surfaceId",
            "s",
            "catalogId",
            TestCatalog.ID,
            "components",
            List.of(component("root", "Label", "text", "Hello"))));
  }

  @Test
  void processRunsTheProcessorOnTheEventDispatchThreadFromAnotherThread() {
    boolean[] onEdt = {false};
    processor
        .model()
        .onSurfaceCreated()
        .subscribe(surface -> onEdt[0] = EventQueue.isDispatchThread());

    A2uiSwing.process(processor, createSurface());

    assertTrue(onEdt[0]);
    assertTrue(processor.model().getSurface("s").isPresent());
  }

  @Test
  void processCallsTheProcessorDirectlyOnTheEventDispatchThread() {
    Edt.run(() -> {
      A2uiSwing.process(processor, createSurface());

      assertTrue(processor.model().getSurface("s").isPresent());
    });
  }

  @Test
  void processThrowsTheErrorOfTheProcessorOnTheCallingThread() {
    assertThrows(
        A2uiValidationError.class,
        () -> A2uiSwing.process(processor, Map.of("version", "1.0", "unknownAction", Map.of())));
  }

  @Test
  void processLaterCompletesAfterTheProcessorProcessedTheMessages() throws Exception {
    CompletableFuture<Void> done = A2uiSwing.processLater(processor, createSurface());

    done.get(10, TimeUnit.SECONDS);
    Edt.run(() -> assertTrue(processor.model().getSurface("s").isPresent()));
  }

  @Test
  void processLaterFailsItsFutureWithTheErrorOfTheProcessor() {
    CompletableFuture<Void> done =
        A2uiSwing.processLater(processor, Map.of("version", "1.0", "unknownAction", Map.of()));

    ExecutionException failure =
        assertThrows(ExecutionException.class, () -> done.get(10, TimeUnit.SECONDS));
    assertInstanceOf(A2uiValidationError.class, failure.getCause());
  }

  @Test
  void theSchedulerRunsATaskOnTheEventDispatchThreadAfterTheDelay() throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    boolean[] onEdt = {false};
    long start = System.nanoTime();

    A2uiSwing.edtScheduler()
        .schedule(
            Duration.ofMillis(40),
            () -> {
              onEdt[0] = EventQueue.isDispatchThread();
              done.countDown();
            });

    assertTrue(done.await(10, TimeUnit.SECONDS));
    assertTrue(onEdt[0]);
    assertTrue(System.nanoTime() - start >= Duration.ofMillis(30).toNanos());
  }

  @Test
  void aCancelledTaskOfTheSchedulerDoesNotRun() throws Exception {
    boolean[] ran = {false};

    Subscription task =
        A2uiSwing.edtScheduler().schedule(Duration.ofMillis(30), () -> ran[0] = true);
    task.cancel();
    Thread.sleep(150);
    Edt.flush();

    assertFalse(ran[0]);
  }

  @Test
  void installMakesTheTimeoutOfACallCompleteOnTheEventDispatchThread() throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    boolean[] onEdt = {false};
    Throwable[] failure = {null};

    Edt.run(() -> {
      A2uiSwing.install(processor);
      processor.rpc().setDefaultTimeout(Duration.ofMillis(30));
      processor
          .rpc()
          .callAgentFunction("s", Map.of("@call", "lookUp", "catalogId", TestCatalog.ID))
          .whenComplete((value, error) -> {
            onEdt[0] = EventQueue.isDispatchThread();
            failure[0] = error;
            done.countDown();
          });
    });

    assertTrue(done.await(10, TimeUnit.SECONDS));
    assertTrue(onEdt[0]);
    assertInstanceOf(A2uiRpcError.class, failure[0]);
    assertEquals(1, sent.size());
  }

  @Test
  void theUserActivationIsPresentWhileTheQueueDispatchesAMouseButtonEvent() {
    BooleanSupplier activation = A2uiSwing.userActivation();
    List<Boolean> results = new ArrayList<>();
    JLabel label = new JLabel();
    label.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent event) {
            results.add(activation.getAsBoolean());
          }
        });

    Toolkit.getDefaultToolkit()
        .getSystemEventQueue()
        .postEvent(mouse(label, MouseEvent.MOUSE_PRESSED));
    Edt.flush();

    Edt.run(() -> assertEquals(List.of(true), results));
  }

  private static MouseEvent mouse(JLabel source, int id) {
    return new MouseEvent(source, id, 0, 0, 1, 1, 1, false, MouseEvent.BUTTON1);
  }

  @Test
  void aMouseButtonEventAndAKeyEventAreUserGestures() {
    JLabel label = new JLabel();

    assertTrue(A2uiSwing.isUserGesture(mouse(label, MouseEvent.MOUSE_PRESSED)));
    assertTrue(A2uiSwing.isUserGesture(mouse(label, MouseEvent.MOUSE_RELEASED)));
    assertTrue(A2uiSwing.isUserGesture(mouse(label, MouseEvent.MOUSE_CLICKED)));
    assertTrue(
        A2uiSwing.isUserGesture(
            new KeyEvent(
                label, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED)));
  }

  @Test
  void otherEventsAreNotUserGestures() {
    JLabel label = new JLabel();

    assertFalse(A2uiSwing.isUserGesture(mouse(label, MouseEvent.MOUSE_MOVED)));
    assertFalse(A2uiSwing.isUserGesture(mouse(label, MouseEvent.MOUSE_ENTERED)));
    assertFalse(
        A2uiSwing.isUserGesture(
            new MouseWheelEvent(label, MouseEvent.MOUSE_WHEEL, 0, 0, 1, 1, 0, false, 0, 1, 1)));
    assertFalse(A2uiSwing.isUserGesture(new InvocationEvent(label, () -> {})));
    assertFalse(A2uiSwing.isUserGesture(null));
  }

  @Test
  void theUserActivationIsAbsentInOtherCodeAndOnOtherThreads() {
    BooleanSupplier activation = A2uiSwing.userActivation();

    assertFalse(activation.getAsBoolean());
    Edt.run(() -> assertFalse(activation.getAsBoolean()));
  }

  @Test
  void theThreadCheckAcceptsOnlyTheEventDispatchThread() {
    IllegalStateException failure =
        assertThrows(IllegalStateException.class, A2uiSwing::requireEventDispatchThread);

    assertTrue(failure.getMessage().contains("event dispatch thread"));
    Edt.run(() -> assertDoesNotThrow(A2uiSwing::requireEventDispatchThread));
  }
}
