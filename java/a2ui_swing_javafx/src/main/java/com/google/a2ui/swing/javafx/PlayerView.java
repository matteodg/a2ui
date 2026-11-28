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

import com.google.a2ui.core.basic_catalog.v1_0.UrlOpener;
import com.google.a2ui.swing.surface.ImageLoader;
import com.google.a2ui.swing.surface.MediaPlayer;
import com.google.a2ui.swing.surface.MediaView;
import java.net.URI;
import java.util.Optional;
import javax.swing.JComponent;

/**
 * The {@link MediaView} of this module: a {@link PlayerPanel} for one {@code Video} or one {@code
 * AudioPlayer} component.
 *
 * <p>The view never starts the media. Only the user starts it, with the play control or with a
 * click on the screen. The view plays only an absolute {@code http} or {@code https} URL. For a
 * {@code data} URL and for a relative path, it shows the message of a failure.
 */
final class PlayerView implements MediaView {
  private final PlayerPanel panel;

  /**
   * Makes a view with no media.
   *
   * @param kind the type of the media
   * @param engine the player
   * @param imageLoader loads the poster image of a video
   * @param urlOpener opens the media in an external application, after a failure
   * @param volume the volume at the start, from 0 to 1
   */
  PlayerView(
      MediaPlayer.Kind kind,
      MediaEngine engine,
      ImageLoader imageLoader,
      UrlOpener urlOpener,
      double volume) {
    panel = new PlayerPanel(kind, engine, imageLoader, urlOpener, volume);
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public void setSource(Optional<URI> url) {
    panel.model().setSource(url == null ? Optional.empty() : url);
  }

  @Override
  public void setPoster(Optional<URI> url) {
    panel.setPoster(url == null ? Optional.empty() : url);
  }

  @Override
  public void setDescription(String description) {
    panel.setDescription(description);
  }

  @Override
  public void dispose() {
    panel.dispose();
  }
}
