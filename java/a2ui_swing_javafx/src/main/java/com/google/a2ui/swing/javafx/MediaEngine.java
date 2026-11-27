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
import javax.swing.JComponent;

/**
 * The player that is behind the controls of a media view. The JavaFX implementation is {@link
 * JavaFxEngine}. The tests use an implementation that plays nothing.
 *
 * <p>The controls call an engine only on the event dispatch thread. No method of an engine waits
 * for the player. An engine calls a {@link Listener} only on the event dispatch thread, in a later
 * event.
 */
interface MediaEngine {
  /**
   * Makes the Swing component that shows the picture of a video. A view makes one screen, when the
   * user starts a video for the first time, and uses it for each video that it plays.
   *
   * @return the new component
   */
  JComponent createScreen();

  /**
   * Starts to load a media. The session does not play until {@link Session#play()}.
   *
   * @param source the URL of the media
   * @param screen the screen that shows the picture, or null to play the sound only
   * @param volume the volume, from 0 to 1
   * @param muted true to play with no sound
   * @param listener gets the events of the session
   * @return the session
   */
  Session open(URI source, JComponent screen, double volume, boolean muted, Listener listener);

  /** One media that an engine loaded. */
  interface Session {
    /** Starts to play, or continues after a pause. */
    void play();

    /** Stops temporarily. The position stays. */
    void pause();

    /**
     * Goes to a position.
     *
     * @param seconds the position from the start of the media, in seconds
     */
    void seek(double seconds);

    /**
     * Sets the volume.
     *
     * @param volume the volume, from 0 to 1
     */
    void setVolume(double volume);

    /**
     * Stops or permits the sound.
     *
     * @param muted true to play with no sound
     */
    void setMuted(boolean muted);

    /** Stops the player and releases its resources. The listener gets no event after this call. */
    void dispose();
  }

  /** The events of a session. */
  interface Listener {
    /**
     * The player read the start of the media.
     *
     * @param durationSeconds the length of the media in seconds, or {@code NaN} if the player does
     *     not know it
     */
    void ready(double durationSeconds);

    /** The media plays. */
    void playing();

    /** The media stopped temporarily. */
    void paused();

    /**
     * The position changed.
     *
     * @param seconds the position from the start of the media, in seconds
     */
    void time(double seconds);

    /** The media played to its end. */
    void ended();

    /**
     * The player cannot play the media.
     *
     * @param message a description of the failure, in a form that a user can read
     */
    void failed(String message);
  }
}
