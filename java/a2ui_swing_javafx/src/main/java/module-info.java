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

/**
 * The optional JavaFX media player of the Swing adapter of the A2UI SDK for Java.
 *
 * <p>This module plays the video and the audio of a surface with JavaFX. An application that does
 * not use this module needs no JavaFX library.
 */
module com.google.a2ui.swing.javafx {
  requires transitive com.google.a2ui.swing;
  requires javafx.graphics;
  requires javafx.media;
  requires javafx.swing;

  exports com.google.a2ui.swing.javafx;

  provides com.google.a2ui.swing.surface.MediaPlayer with
      com.google.a2ui.swing.javafx.JavaFxMediaPlayer;
}
