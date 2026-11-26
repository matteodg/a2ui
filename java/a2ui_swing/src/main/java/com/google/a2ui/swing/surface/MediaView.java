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

import java.net.URI;
import java.util.Optional;
import javax.swing.JComponent;

/**
 * A view that plays video or audio. A {@link MediaPlayer} makes it. Use a view only on the event
 * dispatch thread.
 */
public interface MediaView {
  /**
   * Gets the Swing component of the view, with the controls of the player.
   *
   * @return the component. It is the same object for the life of the view.
   */
  JComponent component();

  /**
   * Sets the media to play. The view does not start to play until the user starts it.
   *
   * @param url the URL of the media, or an empty value to show no media. The component examines
   *     each URL with {@link MediaUrls} before it calls the view. The URL is relative if the agent
   *     sent a relative path.
   */
  void setSource(Optional<URI> url);

  /**
   * Sets the image that a video view shows before the video plays. The default implementation does
   * nothing.
   *
   * @param url the URL of the image, or an empty value to show none. See {@link
   *     #setSource(Optional)}.
   */
  default void setPoster(Optional<URI> url) {}

  /** Stops the player and releases its resources. The default implementation does nothing. */
  default void dispose() {}
}
