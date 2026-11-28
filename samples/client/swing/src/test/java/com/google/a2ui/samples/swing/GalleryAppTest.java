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

package com.google.a2ui.samples.swing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.a2ui.swing.surface.MediaPlayer;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GalleryAppTest {
  /** The media player of the optional module {@code a2ui_swing_javafx}. */
  private static final String JAVAFX_PLAYER = "com.google.a2ui.swing.javafx.JavaFxMediaPlayer";

  private static boolean onClassPath(String className) {
    try {
      Class.forName(className, false, GalleryAppTest.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /**
   * The usual build has no media player on the class path. The Maven profile {@code javafx} adds
   * one. The test is correct in the two builds.
   */
  @Test
  void theGalleryHasAMediaPlayerOnlyIfTheClassPathHasOne() {
    Optional<MediaPlayer> player = GalleryApp.mediaPlayer();

    assertEquals(onClassPath(JAVAFX_PLAYER), player.isPresent());
    player.ifPresent(found -> assertEquals(JAVAFX_PLAYER, found.getClass().getName()));
  }
}
