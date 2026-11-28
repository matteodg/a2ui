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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComponent;

/**
 * The state of one media view, and the rules that change it. The controls show this state and call
 * its operations. The model calls the {@link MediaEngine}.
 *
 * <p>The model has no Swing code and no JavaFX code. Use it only on the event dispatch thread.
 *
 * <p>The rules are these:
 *
 * <ul>
 *   <li>The model opens a session of the engine only in {@link #toggle()}, which is the operation
 *       of the user. A new URL never starts the player. Thus the model reads nothing from the
 *       network before the user asks for it.
 *   <li>The model plays only an absolute {@code http} or {@code https} URL. For each other URL it
 *       goes to {@link Phase#FAILED} and does not call the engine.
 *   <li>A new URL, {@link #suspend()} and {@link #dispose()} release the session.
 *   <li>The model ignores each event of a session that it released.
 *   <li>A failure of the engine goes to {@link Phase#FAILED}. No method of the model throws.
 * </ul>
 */
final class PlayerModel {
  /** The state of the view. */
  enum Phase {
    /** The view has no URL. */
    EMPTY,
    /** The view has a URL and no session. The user did not start the media. */
    IDLE,
    /** The user started the media, and the player does not play it yet. */
    LOADING,
    /** The media plays. */
    PLAYING,
    /** The user stopped the media temporarily. */
    PAUSED,
    /** The media played to its end. */
    ENDED,
    /** The player cannot play the media. See {@link PlayerModel#message()}. */
    FAILED
  }

  /** The message for a URL that JavaFX cannot play. */
  static final String UNSUPPORTED_URL = "The player can play only an http or https URL.";

  /** The message for a failure that has no description. */
  static final String UNKNOWN_FAILURE = "The player gave no description of the failure.";

  private static final System.Logger LOG = System.getLogger("com.google.a2ui.swing.javafx");

  private final MediaEngine engine;
  private final Supplier<JComponent> screen;
  private final Runnable onChange;

  private URI source;
  private Phase phase = Phase.EMPTY;
  private String message = "";
  private double position;
  private double duration = Double.NaN;
  private double volume;
  private boolean muted;

  /** The session of the engine, or null. */
  private MediaEngine.Session session;

  /** The listener of the current session, or null. An event of a different listener is old. */
  private Events events;

  /** True if the last request of the user to the session was to play. */
  private boolean wantsToPlay;

  /** True after the current session started to play for the first time. */
  private boolean started;

  private boolean disposed;

  /**
   * Makes a model with no URL.
   *
   * @param engine the player
   * @param screen gives the component that shows the picture of a video, or null for audio. The
   *     model asks for the component each time it opens a session.
   * @param volume the volume at the start, from 0 to 1
   * @param onChange runs after each change of the state
   */
  PlayerModel(MediaEngine engine, Supplier<JComponent> screen, double volume, Runnable onChange) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.screen = screen;
    this.volume = clamp(volume, 0, 1);
    this.onChange = Objects.requireNonNull(onChange, "onChange");
  }

  /**
   * Tells if the player of this module can play a URL.
   *
   * @param uri a URL that passed the check of {@code MediaUrls}
   * @return true for an absolute {@code http} or {@code https} URL with a host. JavaFX cannot play
   *     a {@code data} URL, and a relative path has no base location here.
   */
  static boolean playable(URI uri) {
    if (uri == null || uri.getScheme() == null) {
      return false;
    }
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    String authority = uri.getRawAuthority();
    return (scheme.equals("http") || scheme.equals("https"))
        && authority != null
        && !authority.isEmpty();
  }

  /** Gets the state. */
  Phase phase() {
    return phase;
  }

  /** Gets the description of the failure. It is empty unless the state is {@link Phase#FAILED}. */
  String message() {
    return message;
  }

  /** Gets the position from the start of the media, in seconds. */
  double position() {
    return position;
  }

  /** Gets the length of the media in seconds, or {@code NaN} if the model does not know it. */
  double duration() {
    return duration;
  }

  /** Gets the volume, from 0 to 1. */
  double volume() {
    return volume;
  }

  /** Tells if the sound is off. */
  boolean muted() {
    return muted;
  }

  /** Tells if {@link #toggle()} has an effect in the current state. */
  boolean canToggle() {
    return phase != Phase.EMPTY && playable(source);
  }

  /** Tells if {@link #seek(double)} has an effect in the current state. */
  boolean canSeek() {
    return session != null && !Double.isNaN(duration);
  }

  /** Tells if the screen has a picture of the media to show. */
  boolean showsPicture() {
    return session != null && started;
  }

  /**
   * Gets the URL that an external application can open as a substitute.
   *
   * @return the URL of the media if it is an absolute {@code http} or {@code https} URL
   */
  Optional<URI> external() {
    return playable(source) ? Optional.of(source) : Optional.empty();
  }

  /**
   * Sets the URL of the media. The same URL as before changes nothing. A different URL releases the
   * session. The model does not start the new media.
   *
   * @param url the URL, or an empty value for no media
   */
  void setSource(Optional<URI> url) {
    URI next = url.orElse(null);
    if (disposed || Objects.equals(next, source)) {
      return;
    }
    close();
    source = next;
    message = "";
    if (next == null) {
      phase = Phase.EMPTY;
    } else if (playable(next)) {
      phase = Phase.IDLE;
    } else {
      phase = Phase.FAILED;
      message = UNSUPPORTED_URL;
    }
    onChange.run();
  }

  /**
   * Does the operation of the play control: starts the media, stops it temporarily, continues it,
   * or plays it again from the start. After a failure, the operation tries again.
   */
  void toggle() {
    if (disposed || !canToggle()) {
      return;
    }
    switch (phase) {
      case IDLE, FAILED -> open();
      case LOADING, PLAYING -> {
        wantsToPlay = false;
        phase = Phase.PAUSED;
        call(MediaEngine.Session::pause);
      }
      case PAUSED -> {
        wantsToPlay = true;
        phase = started ? Phase.PLAYING : Phase.LOADING;
        call(MediaEngine.Session::play);
      }
      case ENDED -> {
        wantsToPlay = true;
        phase = Phase.PLAYING;
        position = 0;
        call(player -> {
          player.seek(0);
          player.play();
        });
      }
      case EMPTY -> {}
    }
    onChange.run();
  }

  /**
   * Goes to a position. The operation has no effect before the model knows the length of the media.
   *
   * @param seconds the position from the start of the media, in seconds
   */
  void seek(double seconds) {
    if (disposed || !canSeek() || Double.isNaN(seconds)) {
      return;
    }
    double target = clamp(seconds, 0, duration);
    position = target;
    boolean again = phase == Phase.ENDED;
    if (again) {
      wantsToPlay = true;
      phase = Phase.PLAYING;
    }
    call(player -> {
      player.seek(target);
      if (again) {
        player.play();
      }
    });
    onChange.run();
  }

  /**
   * Sets the volume.
   *
   * @param value the volume, from 0 to 1
   */
  void setVolume(double value) {
    double next = Double.isNaN(value) ? volume : clamp(value, 0, 1);
    if (disposed || next == volume) {
      return;
    }
    volume = next;
    call(player -> player.setVolume(next));
    onChange.run();
  }

  /**
   * Stops or permits the sound.
   *
   * @param value true to play with no sound
   */
  void setMuted(boolean value) {
    if (disposed || value == muted) {
      return;
    }
    muted = value;
    call(player -> player.setMuted(value));
    onChange.run();
  }

  /**
   * Releases the session and keeps the URL. The view calls it when it leaves its window. The user
   * can start the media again.
   */
  void suspend() {
    if (disposed || session == null) {
      return;
    }
    close();
    phase = Phase.IDLE;
    onChange.run();
  }

  /** Releases the session. The model then ignores each call and runs no change function. */
  void dispose() {
    if (disposed) {
      return;
    }
    close();
    disposed = true;
    source = null;
    message = "";
    phase = Phase.EMPTY;
  }

  /** Opens a session for the URL and asks it to play. */
  private void open() {
    close();
    message = "";
    Events listener = new Events();
    try {
      events = listener;
      JComponent picture = screen == null ? null : screen.get();
      session =
          Objects.requireNonNull(
              engine.open(source, picture, volume, muted, listener), "the engine gave no session");
      wantsToPlay = true;
      phase = Phase.LOADING;
      session.play();
    } catch (RuntimeException | LinkageError e) {
      LOG.log(System.Logger.Level.WARNING, "The media engine did not open " + source + ".", e);
      fail(e.getMessage());
    }
  }

  /** Runs a call of the session. A failure of the session is a failure of the media. */
  private void call(Consumer<MediaEngine.Session> call) {
    if (session == null) {
      return;
    }
    try {
      call.accept(session);
    } catch (RuntimeException | LinkageError e) {
      LOG.log(System.Logger.Level.WARNING, "The media session failed for " + source + ".", e);
      fail(e.getMessage());
    }
  }

  private void fail(String text) {
    close();
    phase = Phase.FAILED;
    message = text == null || text.isBlank() ? UNKNOWN_FAILURE : text.strip();
  }

  /** Releases the session, if there is one, and removes what the model knows about its media. */
  private void close() {
    MediaEngine.Session old = session;
    session = null;
    events = null;
    wantsToPlay = false;
    started = false;
    position = 0;
    duration = Double.NaN;
    if (old != null) {
      try {
        old.dispose();
      } catch (RuntimeException | LinkageError e) {
        LOG.log(System.Logger.Level.WARNING, "The media session did not release its player.", e);
      }
    }
  }

  private static double clamp(double value, double low, double high) {
    return Math.max(low, Math.min(high, value));
  }

  /** The listener of one session. The model ignores it after it releases the session. */
  private final class Events implements MediaEngine.Listener {
    private boolean old() {
      return events != this;
    }

    @Override
    public void ready(double durationSeconds) {
      if (old()) {
        return;
      }
      boolean known = durationSeconds > 0 && !Double.isInfinite(durationSeconds);
      duration = known ? durationSeconds : Double.NaN;
      onChange.run();
    }

    @Override
    public void playing() {
      // An event that comes after the user asked for a pause is late. The pause event follows it.
      if (old() || !wantsToPlay) {
        return;
      }
      started = true;
      phase = Phase.PLAYING;
      onChange.run();
    }

    @Override
    public void paused() {
      if (old() || wantsToPlay) {
        return;
      }
      phase = Phase.PAUSED;
      onChange.run();
    }

    @Override
    public void time(double seconds) {
      if (old() || Double.isNaN(seconds) || phase == Phase.ENDED) {
        return;
      }
      position = Double.isNaN(duration) ? Math.max(0, seconds) : clamp(seconds, 0, duration);
      onChange.run();
    }

    @Override
    public void ended() {
      if (old()) {
        return;
      }
      wantsToPlay = false;
      phase = Phase.ENDED;
      if (!Double.isNaN(duration)) {
        position = duration;
      }
      onChange.run();
    }

    @Override
    public void failed(String text) {
      if (old()) {
        return;
      }
      fail(text);
      onChange.run();
    }
  }
}
