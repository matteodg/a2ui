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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * A {@link MediaEngine} for tests. It plays nothing and needs no JavaFX toolkit. It records each
 * call, and a test sends the events of a player through {@link Session#events}.
 */
final class FakeEngine implements MediaEngine {
  /** A session of this engine. */
  static final class Session implements MediaEngine.Session {
    /** The URL of the media. */
    final URI source;

    /** The screen that the session got, or null. */
    final JComponent screen;

    /** The volume that the session got at the start. */
    final double startVolume;

    /** The mute state that the session got at the start. */
    final boolean startMuted;

    /** The listener of the session. A test calls it to send the events of a player. */
    final Listener events;

    /** Each call that the session got, for example {@code play} or {@code seek 3.0}. */
    final List<String> calls = new ArrayList<>();

    /** True to make each subsequent call of the session throw. */
    boolean broken;

    Session(URI source, JComponent screen, double volume, boolean muted, Listener events) {
      this.source = source;
      this.screen = screen;
      this.startVolume = volume;
      this.startMuted = muted;
      this.events = events;
    }

    private void record(String call) {
      calls.add(call);
      if (broken) {
        throw new IllegalStateException("The session is broken.");
      }
    }

    @Override
    public void play() {
      record("play");
    }

    @Override
    public void pause() {
      record("pause");
    }

    @Override
    public void seek(double seconds) {
      record("seek " + seconds);
    }

    @Override
    public void setVolume(double volume) {
      record("volume " + volume);
    }

    @Override
    public void setMuted(boolean muted) {
      record("muted " + muted);
    }

    @Override
    public void dispose() {
      record("dispose");
    }

    /** Tells if the session got the call {@code dispose}. */
    boolean disposed() {
      return calls.contains("dispose");
    }
  }

  /** Each session that the engine opened, in the sequence of the calls. */
  final List<Session> sessions = new ArrayList<>();

  /** Each screen that the engine made. */
  final List<JComponent> screens = new ArrayList<>();

  /** True to make {@link #open} throw. */
  boolean brokenOpen;

  /** True to make {@link #createScreen()} throw. */
  boolean brokenScreen;

  /** True to make each new session throw in each call. */
  boolean brokenSessions;

  @Override
  public JComponent createScreen() {
    if (brokenScreen) {
      throw new IllegalStateException("The engine makes no screen.");
    }
    JPanel screen = new JPanel();
    screens.add(screen);
    return screen;
  }

  @Override
  public Session open(
      URI source, JComponent screen, double volume, boolean muted, Listener listener) {
    if (brokenOpen) {
      throw new IllegalStateException("The engine opens nothing.");
    }
    Session session = new Session(source, screen, volume, muted, listener);
    session.broken = brokenSessions;
    sessions.add(session);
    return session;
  }

  /** Gets the last session that the engine opened. */
  Session last() {
    return sessions.getLast();
  }
}
