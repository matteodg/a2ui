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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests of the real JavaFX engine. They start the JavaFX toolkit and play a WAV file that the test
 * writes. They use no network, and the file has no sound.
 *
 * <p>The tests have the tag {@code javafx}. The usual build does not run them, because they need a
 * display and the native media libraries of JavaFX. Run them with the Maven profile {@code
 * javafx-it}. If the toolkit cannot start, JUnit records the tests as skipped.
 */
@Tag("javafx")
class JavaFxEngineTest {
  /** The length of the test file, in seconds. */
  private static final double LENGTH = 3;

  private static final long TIMEOUT_MILLIS = 20_000;

  /**
   * The directory of the media files of the tests, relative to the directory of the module. The
   * player keeps a file open for a short time after its release. Thus the tests do not delete the
   * files. The goal {@code clean} of Maven removes them.
   */
  private static final Path DIRECTORY = Path.of("target", "javafx-media");

  private static MediaEngine engine;

  /** A listener that keeps each event. The engine calls it on the event dispatch thread. */
  private static final class Recorder implements MediaEngine.Listener {
    final List<String> events = new CopyOnWriteArrayList<>();
    final List<String> wrongThreads = new CopyOnWriteArrayList<>();
    volatile double duration = Double.NaN;
    volatile double time;
    volatile String failure;

    private void record(String event) {
      if (!SwingUtilities.isEventDispatchThread()) {
        wrongThreads.add(event + " on " + Thread.currentThread().getName());
      }
      events.add(event);
    }

    @Override
    public void ready(double durationSeconds) {
      duration = durationSeconds;
      record("ready");
    }

    @Override
    public void playing() {
      record("playing");
    }

    @Override
    public void paused() {
      record("paused");
    }

    @Override
    public void time(double seconds) {
      time = seconds;
      record("time");
    }

    @Override
    public void ended() {
      record("ended");
    }

    @Override
    public void failed(String message) {
      failure = message;
      record("failed");
    }

    int count(String event) {
      return (int) events.stream().filter(event::equals).count();
    }
  }

  @BeforeAll
  static void startTheToolkit() {
    Optional<?>[] started = {Optional.empty()};
    Swing.onEdt(() -> started[0] = JavaFxEngine.start());
    assumeTrue(started[0].isPresent(), "The JavaFX toolkit cannot start on this computer.");
    engine = (MediaEngine) started[0].get();
    try {
      Files.createDirectories(DIRECTORY);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Writes a WAV file with PCM samples of 16 bits, one channel, 8000 samples for each second. */
  private static Path silence(String name, double seconds) throws IOException {
    int rate = 8000;
    int dataLength = (int) (seconds * rate) * 2;
    ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
    header.putInt(36 + dataLength);
    header.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
    header.putInt(16);
    header.putShort((short) 1);
    header.putShort((short) 1);
    header.putInt(rate);
    header.putInt(rate * 2);
    header.putShort((short) 2);
    header.putShort((short) 16);
    header.put("data".getBytes(StandardCharsets.US_ASCII));
    header.putInt(dataLength);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    bytes.write(header.array());
    bytes.write(new byte[dataLength]);
    return Files.write(DIRECTORY.resolve(name), bytes.toByteArray());
  }

  private static void await(String what, BooleanSupplier condition) throws InterruptedException {
    long end = System.currentTimeMillis() + TIMEOUT_MILLIS;
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() > end) {
        fail("The player did not give this in time: " + what);
      }
      Thread.sleep(20);
    }
  }

  private static MediaEngine.Session open(Path file, JComponent screen, Recorder recorder) {
    MediaEngine.Session[] session = {null};
    // The volume is 0 and the sound is off. Thus the computer makes no sound.
    Swing.onEdt(() -> session[0] = engine.open(file.toUri(), screen, 0, true, recorder));
    return session[0];
  }

  @Test
  void theEnginePlaysAWavFileAndGivesItsEventsOnTheEventDispatchThread() throws Exception {
    Recorder recorder = new Recorder();
    MediaEngine.Session session = open(silence("silence.wav", LENGTH), null, recorder);
    try {
      await("ready", () -> recorder.count("ready") == 1);
      assertEquals(LENGTH, recorder.duration, 0.1);
      // The engine does not start the media by itself.
      Thread.sleep(400);
      assertEquals(0, recorder.count("playing"));
      assertEquals(0, recorder.count("time"));

      Swing.onEdt(session::play);
      await("playing", () -> recorder.count("playing") == 1);
      await("a time after the start", () -> recorder.time > 0.3);

      Swing.onEdt(session::pause);
      await("paused", () -> recorder.count("paused") == 1);
      Thread.sleep(200);
      double held = recorder.time;
      Thread.sleep(500);
      assertEquals(held, recorder.time, 0.05);

      Swing.onEdt(() -> session.seek(2));
      await("the time of the seek", () -> recorder.time >= 1.9);

      Swing.onEdt(session::play);
      await("ended", () -> recorder.count("ended") == 1);
      assertEquals(LENGTH, recorder.time, 0.2);

      // After the end, a seek and a play request play the media again.
      Swing.onEdt(() -> {
        session.seek(0);
        session.play();
      });
      await("a time after the second start", () -> recorder.time > 0.2 && recorder.time < 2);
      await("a later time", () -> recorder.time > 0.6);
    } finally {
      Swing.onEdt(session::dispose);
    }
    // No event comes after the release.
    int events = recorder.events.size();
    Thread.sleep(600);
    assertEquals(events, recorder.events.size());
    assertEquals(List.of(), recorder.wrongThreads);
    assertEquals(0, recorder.count("failed"));
  }

  @Test
  void theScreenIsAJavaFxPanelThatDoesNotTakeTheFocus() throws Exception {
    JComponent[] screen = {null};
    Swing.onEdt(() -> screen[0] = engine.createScreen());
    assertEquals("javafx.embed.swing.JFXPanel", screen[0].getClass().getName());
    assertFalse(screen[0].isFocusable());

    // A session with a screen gets its scene and loads the media.
    Recorder recorder = new Recorder();
    MediaEngine.Session session = open(silence("screen.wav", LENGTH), screen[0], recorder);
    try {
      await("ready", () -> recorder.count("ready") == 1);
    } finally {
      Swing.onEdt(session::dispose);
    }
    assertEquals(0, recorder.count("failed"));
  }

  @Test
  void aFileThatIsNoMediaAndAFileThatIsAbsentGiveAFailure() throws Exception {
    Path text = Files.writeString(DIRECTORY.resolve("text.mp3"), "This file is no media.");
    Recorder first = new Recorder();
    MediaEngine.Session bad = open(text, null, first);
    Swing.onEdt(bad::play);
    Recorder second = new Recorder();
    MediaEngine.Session absent = open(DIRECTORY.resolve("absent.wav"), null, second);
    Swing.onEdt(absent::play);
    try {
      await("the failure of the file that is no media", () -> first.count("failed") >= 1);
      await("the failure of the file that is absent", () -> second.count("failed") >= 1);
    } finally {
      Swing.onEdt(bad::dispose);
      Swing.onEdt(absent::dispose);
    }
    assertFalse(first.failure.isBlank(), first.failure);
    assertFalse(second.failure.isBlank(), second.failure);
    assertEquals(0, first.count("playing"));
    assertEquals(0, second.count("playing"));
    assertTrue(first.wrongThreads.isEmpty() && second.wrongThreads.isEmpty());
  }
}
