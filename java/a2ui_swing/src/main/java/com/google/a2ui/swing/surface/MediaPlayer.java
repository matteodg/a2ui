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

import java.util.Optional;

/**
 * Makes the views that play video and audio. The {@code Video} and {@code AudioPlayer} components
 * of the basic catalog use this service.
 *
 * <p>Swing cannot play media. Thus the default service, {@link #NONE}, makes no view, and the
 * components show a substitute. An application that has a player, for example JavaFX or vlcj, gives
 * its own implementation.
 */
@FunctionalInterface
public interface MediaPlayer {
  /** The type of the media that a view plays. */
  enum Kind {
    /** A video, with or without sound. */
    VIDEO,
    /** Sound only. */
    AUDIO
  }

  /** The service that makes no view. */
  MediaPlayer NONE = kind -> Optional.empty();

  /**
   * Makes a view. Each component that plays media has its own view.
   *
   * @param kind the type of the media
   * @return the new view, or an empty result if this service cannot play that type
   */
  Optional<MediaView> createView(Kind kind);
}
