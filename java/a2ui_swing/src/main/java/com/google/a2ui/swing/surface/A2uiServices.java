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

import com.google.a2ui.core.basic_catalog.v1_0.UrlOpener;
import java.util.Objects;

/**
 * The services that the components of a surface use for the functions that plain Swing does not
 * have. An application replaces a service to use its own implementation.
 *
 * <p>The services of {@link #DEFAULT} are these:
 *
 * <ul>
 *   <li>{@code markdown}: shows the text as the agent wrote it, with the Markdown marks. The
 *       version of this module that adds the {@code Text} component replaces it with a view that
 *       shows the Markdown subset of the protocol.
 *   <li>{@code imageLoader}: reads {@code http}, {@code https} and {@code data} URLs with {@code
 *       ImageIO}, on a thread that is not the event dispatch thread. It reads the formats that
 *       {@code ImageIO} reads, for example PNG, JPEG, GIF and BMP. It has no cache.
 *   <li>{@code mediaPlayer}: {@link MediaPlayer#NONE}, because Swing cannot play media.
 *   <li>{@code modalPresenter}: shows a modal {@code JDialog} above the window of the surface. It
 *       needs a display.
 *   <li>{@code urlOpener}: opens the URL in the browser of the system with {@code
 *       java.awt.Desktop}. It throws an {@code UnsupportedOperationException} if the computer
 *       cannot open a browser.
 * </ul>
 *
 * @param markdown makes the views that show Markdown text
 * @param imageLoader loads the images
 * @param mediaPlayer makes the views that play video and audio
 * @param modalPresenter shows content above the surface
 * @param urlOpener opens a URL, for the function {@code openUrl} and for media that Swing cannot
 *     play
 */
public record A2uiServices(
    MarkdownRenderer markdown,
    ImageLoader imageLoader,
    MediaPlayer mediaPlayer,
    ModalPresenter modalPresenter,
    UrlOpener urlOpener) {
  /** The default services. See the class description. */
  public static final A2uiServices DEFAULT =
      new A2uiServices(
          PlainTextMarkdownView::new,
          new DefaultImageLoader(),
          MediaPlayer.NONE,
          new DialogModalPresenter(),
          new DesktopUrlOpener());

  /** Makes the services. */
  public A2uiServices {
    Objects.requireNonNull(markdown, "markdown");
    Objects.requireNonNull(imageLoader, "imageLoader");
    Objects.requireNonNull(mediaPlayer, "mediaPlayer");
    Objects.requireNonNull(modalPresenter, "modalPresenter");
    Objects.requireNonNull(urlOpener, "urlOpener");
  }

  /**
   * Makes a copy of these services with a different Markdown renderer.
   *
   * @param markdown the new renderer
   * @return the new services
   */
  public A2uiServices withMarkdown(MarkdownRenderer markdown) {
    return new A2uiServices(markdown, imageLoader, mediaPlayer, modalPresenter, urlOpener);
  }

  /**
   * Makes a copy of these services with a different image loader.
   *
   * @param imageLoader the new loader
   * @return the new services
   */
  public A2uiServices withImageLoader(ImageLoader imageLoader) {
    return new A2uiServices(markdown, imageLoader, mediaPlayer, modalPresenter, urlOpener);
  }

  /**
   * Makes a copy of these services with a different media player.
   *
   * @param mediaPlayer the new player
   * @return the new services
   */
  public A2uiServices withMediaPlayer(MediaPlayer mediaPlayer) {
    return new A2uiServices(markdown, imageLoader, mediaPlayer, modalPresenter, urlOpener);
  }

  /**
   * Makes a copy of these services with a different modal presenter.
   *
   * @param modalPresenter the new presenter
   * @return the new services
   */
  public A2uiServices withModalPresenter(ModalPresenter modalPresenter) {
    return new A2uiServices(markdown, imageLoader, mediaPlayer, modalPresenter, urlOpener);
  }

  /**
   * Makes a copy of these services with a different URL opener.
   *
   * @param urlOpener the new opener
   * @return the new services
   */
  public A2uiServices withUrlOpener(UrlOpener urlOpener) {
    return new A2uiServices(markdown, imageLoader, mediaPlayer, modalPresenter, urlOpener);
  }
}
