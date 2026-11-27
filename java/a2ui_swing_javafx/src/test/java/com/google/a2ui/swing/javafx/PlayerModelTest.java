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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.javafx.PlayerModel.Phase;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The model needs no Swing thread and no JavaFX toolkit. The tests use a {@link FakeEngine}. */
class PlayerModelTest {
  private static final URI VIDEO = URI.create("https://example.com/intro.mp4");
  private static final URI OTHER = URI.create("https://example.com/other.mp4");

  private final FakeEngine engine = new FakeEngine();
  private int changes;
  private final PlayerModel model = new PlayerModel(engine, null, 0.5, () -> changes++);

  private final LogCapture log = new LogCapture();

  @AfterEach
  void closeTheLog() {
    log.close();
  }

  /** Sets the URL, starts the media and sends the events of a player that plays. */
  private FakeEngine.Session play(double duration) {
    model.setSource(Optional.of(VIDEO));
    model.toggle();
    FakeEngine.Session session = engine.last();
    session.events.ready(duration);
    session.events.playing();
    return session;
  }

  @Test
  void aNewModelHasNoMediaAndItsControlsDoNothing() {
    assertEquals(Phase.EMPTY, model.phase());
    assertFalse(model.canToggle());
    assertFalse(model.canSeek());
    assertFalse(model.showsPicture());
    assertEquals(Optional.empty(), model.external());

    model.toggle();
    model.seek(3);

    assertEquals(Phase.EMPTY, model.phase());
    assertEquals(List.of(), engine.sessions);
    assertEquals(0, changes);
  }

  @Test
  void aUrlDoesNotStartThePlayer() {
    model.setSource(Optional.of(VIDEO));

    assertEquals(Phase.IDLE, model.phase());
    assertTrue(model.canToggle());
    assertEquals(Optional.of(VIDEO), model.external());
    // The model opened no session. Thus the engine read nothing from the URL.
    assertEquals(List.of(), engine.sessions);
    assertEquals(1, changes);
  }

  @Test
  void theUserStartsTheMediaAndTheEventsOfThePlayerGiveTheState() {
    model.setSource(Optional.of(VIDEO));

    model.toggle();
    FakeEngine.Session session = engine.last();

    assertEquals(Phase.LOADING, model.phase());
    assertEquals(VIDEO, session.source);
    assertNull(session.screen);
    assertEquals(0.5, session.startVolume);
    assertFalse(session.startMuted);
    assertEquals(List.of("play"), session.calls);
    assertFalse(model.canSeek());
    assertTrue(Double.isNaN(model.duration()));

    session.events.ready(12.5);
    assertEquals(Phase.LOADING, model.phase());
    assertEquals(12.5, model.duration());
    assertTrue(model.canSeek());
    assertFalse(model.showsPicture());

    session.events.playing();
    assertEquals(Phase.PLAYING, model.phase());
    assertTrue(model.showsPicture());

    session.events.time(3.25);
    assertEquals(3.25, model.position());
  }

  @Test
  void theModelAsksForTheScreenOnlyWhenTheUserStartsTheMedia() {
    JComponent screen = new JPanel();
    int[] requests = {0};
    PlayerModel video =
        new PlayerModel(
            engine,
            () -> {
              requests[0]++;
              return screen;
            },
            1,
            () -> {});

    video.setSource(Optional.of(VIDEO));
    assertEquals(0, requests[0]);

    video.toggle();
    assertEquals(1, requests[0]);
    assertSame(screen, engine.last().screen);
  }

  @Test
  void thePlayControlStopsTheMediaTemporarilyAndContinuesIt() {
    FakeEngine.Session session = play(10);

    model.toggle();
    assertEquals(Phase.PAUSED, model.phase());
    assertEquals(List.of("play", "pause"), session.calls);
    session.events.paused();
    assertEquals(Phase.PAUSED, model.phase());

    model.toggle();
    assertEquals(Phase.PLAYING, model.phase());
    assertEquals(List.of("play", "pause", "play"), session.calls);
    session.events.playing();
    assertEquals(Phase.PLAYING, model.phase());
    assertEquals(1, engine.sessions.size());
  }

  @Test
  void aLateEventOfThePlayerDoesNotChangeWhatTheUserAskedFor() {
    model.setSource(Optional.of(VIDEO));
    model.toggle();
    FakeEngine.Session session = engine.last();
    // The user asks for a pause before the player started.
    model.toggle();

    session.events.playing();
    assertEquals(Phase.PAUSED, model.phase());
    assertFalse(model.showsPicture());

    // The user continues. The pause event of the first request comes late.
    model.toggle();
    assertEquals(Phase.LOADING, model.phase());
    session.events.paused();
    assertEquals(Phase.LOADING, model.phase());
    session.events.playing();
    assertEquals(Phase.PLAYING, model.phase());
  }

  @Test
  void theEndOfTheMediaLetsTheUserPlayItAgainFromTheStart() {
    FakeEngine.Session session = play(10);
    session.events.time(9.8);

    session.events.ended();
    assertEquals(Phase.ENDED, model.phase());
    assertEquals(10, model.position());
    // A time event after the end does not move the position.
    session.events.time(9.9);
    assertEquals(10, model.position());

    model.toggle();
    assertEquals(Phase.PLAYING, model.phase());
    assertEquals(0, model.position());
    assertEquals(List.of("play", "seek 0.0", "play"), session.calls);
    assertEquals(1, engine.sessions.size());
  }

  @Test
  void aSeekKeepsThePositionInTheLengthOfTheMedia() {
    FakeEngine.Session session = play(10);

    model.seek(4);
    model.seek(99);
    model.seek(-3);
    model.seek(Double.NaN);

    assertEquals(List.of("play", "seek 4.0", "seek 10.0", "seek 0.0"), session.calls);
    assertEquals(0, model.position());
    assertEquals(Phase.PLAYING, model.phase());
  }

  @Test
  void aSeekHasNoEffectBeforeTheModelKnowsTheLength() {
    model.setSource(Optional.of(VIDEO));
    model.seek(4);
    model.toggle();
    model.seek(4);
    // A player that does not know the length gives no number.
    engine.last().events.ready(Double.NaN);
    model.seek(4);
    engine.last().events.ready(Double.POSITIVE_INFINITY);
    model.seek(4);

    assertEquals(List.of("play"), engine.last().calls);
    assertFalse(model.canSeek());
    // The position still follows the player.
    engine.last().events.time(7);
    assertEquals(7, model.position());
  }

  @Test
  void aSeekAfterTheEndPlaysFromTheNewPosition() {
    FakeEngine.Session session = play(10);
    session.events.ended();

    model.seek(2);

    assertEquals(Phase.PLAYING, model.phase());
    assertEquals(2, model.position());
    assertEquals(List.of("play", "seek 2.0", "play"), session.calls);
  }

  @Test
  void theVolumeAndTheMuteStateGoToTheSessionAndToTheNextSession() {
    model.setVolume(0.25);
    model.setMuted(true);
    assertEquals(0.25, model.volume());
    assertTrue(model.muted());

    FakeEngine.Session session = play(10);
    assertEquals(0.25, session.startVolume);
    assertTrue(session.startMuted);

    model.setVolume(7);
    model.setVolume(1);
    model.setVolume(Double.NaN);
    model.setMuted(false);
    model.setMuted(false);

    assertEquals(List.of("play", "volume 1.0", "muted false"), session.calls);
    assertEquals(1, model.volume());
    assertFalse(model.muted());
  }

  @Test
  void aDifferentUrlReleasesThePlayerAndDoesNotStartTheNewMedia() {
    FakeEngine.Session session = play(10);
    session.events.time(4);

    model.setSource(Optional.of(VIDEO));
    assertFalse(session.disposed());
    assertEquals(Phase.PLAYING, model.phase());

    model.setSource(Optional.of(OTHER));

    assertTrue(session.disposed());
    assertEquals(Phase.IDLE, model.phase());
    assertEquals(0, model.position());
    assertTrue(Double.isNaN(model.duration()));
    assertFalse(model.showsPicture());
    assertEquals(1, engine.sessions.size());

    model.setSource(Optional.empty());
    assertEquals(Phase.EMPTY, model.phase());
    assertFalse(model.canToggle());
  }

  @Test
  void theModelIgnoresTheEventsOfAPlayerThatItReleased() {
    FakeEngine.Session old = play(10);
    model.setSource(Optional.of(OTHER));
    int before = changes;

    old.events.ready(5);
    old.events.playing();
    old.events.time(3);
    old.events.paused();
    old.events.ended();
    old.events.failed("late");

    assertEquals(Phase.IDLE, model.phase());
    assertEquals(0, model.position());
    assertEquals("", model.message());
    assertEquals(before, changes);
  }

  @Test
  void aViewThatLeavesItsWindowReleasesThePlayerAndKeepsTheUrl() {
    FakeEngine.Session session = play(10);

    model.suspend();

    assertTrue(session.disposed());
    assertEquals(Phase.IDLE, model.phase());
    assertTrue(model.canToggle());

    // A second call has no session to release.
    int before = changes;
    model.suspend();
    assertEquals(before, changes);

    model.toggle();
    assertEquals(2, engine.sessions.size());
    assertEquals(VIDEO, engine.last().source);
  }

  @Test
  void theDisposalReleasesThePlayerAndStopsTheModel() {
    FakeEngine.Session session = play(10);
    int before = changes;

    model.dispose();
    model.dispose();

    assertEquals(List.of("play", "dispose"), session.calls);
    assertEquals(Phase.EMPTY, model.phase());

    model.setSource(Optional.of(OTHER));
    model.toggle();
    model.seek(1);
    model.setVolume(0.1);
    model.setMuted(true);
    model.suspend();
    session.events.failed("late");

    assertEquals(Phase.EMPTY, model.phase());
    assertEquals(1, engine.sessions.size());
    assertEquals(before, changes);
    assertEquals(List.of(), log.messages);
  }

  @Test
  void aFailureOfThePlayerGivesTheFailureStateAndTheUserCanTryAgain() {
    FakeEngine.Session session = play(10);

    session.events.failed("  MEDIA_UNSUPPORTED: no codec  ");

    assertEquals(Phase.FAILED, model.phase());
    assertEquals("MEDIA_UNSUPPORTED: no codec", model.message());
    assertTrue(session.disposed());
    assertEquals(Optional.of(VIDEO), model.external());
    assertTrue(model.canToggle());

    model.toggle();

    assertEquals(Phase.LOADING, model.phase());
    assertEquals("", model.message());
    assertEquals(2, engine.sessions.size());

    engine.last().events.failed(" ");
    assertEquals(PlayerModel.UNKNOWN_FAILURE, model.message());
    engine.sessions.clear();
    model.toggle();
    engine.last().events.failed(null);
    assertEquals(PlayerModel.UNKNOWN_FAILURE, model.message());
  }

  @Test
  void anEngineThatThrowsGivesTheFailureState() {
    engine.brokenOpen = true;
    model.setSource(Optional.of(VIDEO));

    model.toggle();

    assertEquals(Phase.FAILED, model.phase());
    assertEquals("The engine opens nothing.", model.message());
    assertEquals(1, log.messages.size());

    engine.brokenOpen = false;
    engine.brokenSessions = true;
    model.toggle();

    assertEquals(Phase.FAILED, model.phase());
    assertEquals("The session is broken.", model.message());
    // The model released the session, and the failure of the release did not go to the caller.
    assertEquals(List.of("play", "dispose"), engine.last().calls);
    assertEquals(3, log.messages.size());
  }

  @Test
  void aSessionThatThrowsInALaterCallGivesTheFailureState() {
    FakeEngine.Session session = play(10);
    session.broken = true;

    model.seek(3);

    assertEquals(Phase.FAILED, model.phase());
    assertEquals("The session is broken.", model.message());
    assertFalse(model.canSeek());
  }

  @Test
  void aScreenThatTheEngineCannotMakeGivesTheFailureState() {
    PlayerModel video =
        new PlayerModel(
            engine,
            () -> {
              throw new IllegalStateException("no screen");
            },
            1,
            () -> {});
    video.setSource(Optional.of(VIDEO));

    video.toggle();

    assertEquals(Phase.FAILED, video.phase());
    assertEquals("no screen", video.message());
    assertEquals(1, log.messages.size());
    assertEquals(List.of(), engine.sessions);
  }

  @Test
  void theModelPlaysOnlyAnAbsoluteHttpOrHttpsUrl() {
    assertTrue(PlayerModel.playable(URI.create("http://example.com/a.mp3")));
    assertTrue(PlayerModel.playable(URI.create("HTTPS://example.com/a.mp4")));
    assertTrue(PlayerModel.playable(URI.create("http://127.0.0.1:8080/a.wav")));
    assertFalse(PlayerModel.playable(null));
    assertFalse(PlayerModel.playable(URI.create("data:audio/wav;base64,AAAA")));
    assertFalse(PlayerModel.playable(URI.create("media/a.mp3")));
    assertFalse(PlayerModel.playable(URI.create("/media/a.mp3")));
    assertFalse(PlayerModel.playable(URI.create("file:///C:/a.mp3")));
    assertFalse(PlayerModel.playable(URI.create("jar:file:/a.jar!/a.mp3")));
    assertFalse(PlayerModel.playable(URI.create("ftp://example.com/a.mp3")));
    assertFalse(PlayerModel.playable(URI.create("https:example.com/a.mp3")));
    assertFalse(PlayerModel.playable(URI.create("https:///a.mp3")));
  }

  @Test
  void aUrlThatThePlayerCannotPlayGivesTheFailureStateWithNoCallOfTheEngine() {
    for (String url : List.of("data:audio/wav;base64,AAAA", "media/a.mp3", "file:///C:/a.mp3")) {
      model.setSource(Optional.of(URI.create(url)));

      assertEquals(Phase.FAILED, model.phase(), url);
      assertEquals(PlayerModel.UNSUPPORTED_URL, model.message());
      assertFalse(model.canToggle());
      assertEquals(Optional.empty(), model.external());

      model.toggle();
      assertEquals(Phase.FAILED, model.phase());
    }
    assertEquals(List.of(), engine.sessions);

    model.setSource(Optional.of(VIDEO));
    assertEquals(Phase.IDLE, model.phase());
    assertEquals("", model.message());
  }
}
