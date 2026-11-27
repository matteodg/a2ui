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
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.ImageLoader;
import com.google.a2ui.swing.surface.MediaPlayer;
import com.google.a2ui.swing.surface.MediaView;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A {@link MediaPlayer} that plays the {@code Video} and {@code AudioPlayer} components of a
 * surface with JavaFX. An application gives it to the services of its surfaces:
 *
 * <pre>{@code
 * A2uiServices services = A2uiServices.DEFAULT.withMediaPlayer(new JavaFxMediaPlayer());
 * }</pre>
 *
 * <p>Give the same services to {@code SwingBasicCatalog.create} and to each {@code A2uiSurface}.
 *
 * <p>Each view has Swing controls: a play and pause button, a slider for the position, the time, a
 * mute button and a slider for the volume. A video view also has a screen with the proportions 16
 * to 9. The screen shows the poster image until the media plays.
 *
 * <p>The player obeys these rules:
 *
 * <ul>
 *   <li>It never starts a media by itself. It reads nothing from the URL of the media before the
 *       user operates the play control.
 *   <li>It plays only an absolute {@code http} or {@code https} URL. For a {@code data} URL, for a
 *       relative path and for a media that JavaFX cannot play, the view shows a message. If the URL
 *       is an {@code http} or {@code https} URL, the view also shows a button that opens the URL
 *       with the URL opener. A failure of the media never fails the surface.
 *   <li>It starts the JavaFX toolkit when the first view is necessary, and it sets {@code
 *       Platform.setImplicitExit(false)}. The first call of {@link #createView} waits for that
 *       start one time.
 *   <li>If JavaFX cannot operate, {@link #createView} gives an empty result. The components then
 *       show their usual substitute. This occurs when the computer has no display, when the JavaFX
 *       libraries for the platform are absent, and when the toolkit does not start.
 *   <li>A view releases its JavaFX player when the component goes away, when the URL changes, and
 *       when the view leaves its window or is hidden.
 * </ul>
 *
 * <p>Use the player only on the event dispatch thread, as each service of the adapter. The module
 * also declares the player as a provider of the service {@code MediaPlayer} for {@code
 * java.util.ServiceLoader}.
 */
public final class JavaFxMediaPlayer implements MediaPlayer {
  private static final System.Logger LOG = System.getLogger("com.google.a2ui.swing.javafx");

  private final ImageLoader imageLoader;
  private final UrlOpener urlOpener;
  private final double volume;
  private final Supplier<Optional<MediaEngine>> starter;

  /** The engine, an empty value if JavaFX cannot operate, or null before the first view. */
  private Optional<MediaEngine> engine;

  /**
   * Makes a player with the default options: the image loader and the URL opener of {@link
   * A2uiServices#DEFAULT}, and the full volume.
   */
  public JavaFxMediaPlayer() {
    this(new Builder());
  }

  private JavaFxMediaPlayer(Builder builder) {
    this(builder, () -> JavaFxEngine.start());
  }

  /**
   * Makes a player with a given start of the engine. The tests use it.
   *
   * @param builder the options
   * @param starter starts the engine. The player calls it one time, when the first view is
   *     necessary. An empty result tells that the engine cannot operate.
   */
  JavaFxMediaPlayer(Builder builder, Supplier<Optional<MediaEngine>> starter) {
    this.imageLoader = builder.imageLoader;
    this.urlOpener = builder.urlOpener;
    this.volume = builder.volume;
    this.starter = Objects.requireNonNull(starter, "starter");
  }

  /**
   * Starts to make a player with options.
   *
   * @return a new builder with the default options
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Makes a view with JavaFX.
   *
   * @param kind the type of the media
   * @return the new view, or an empty result if JavaFX cannot operate on this computer
   */
  @Override
  public Optional<MediaView> createView(Kind kind) {
    Objects.requireNonNull(kind, "kind");
    if (engine == null) {
      engine = start();
    }
    if (engine.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(new PlayerView(kind, engine.get(), imageLoader, urlOpener, volume));
    } catch (RuntimeException | LinkageError e) {
      LOG.log(System.Logger.Level.WARNING, "JavaFX did not make a media view.", e);
      return Optional.empty();
    }
  }

  private Optional<MediaEngine> start() {
    try {
      Optional<MediaEngine> started = starter.get();
      return started == null ? Optional.empty() : started;
    } catch (RuntimeException | LinkageError e) {
      // The JavaFX classes are absent, or their native libraries do not load.
      LOG.log(System.Logger.Level.WARNING, "JavaFX media is not available.", e);
      return Optional.empty();
    }
  }

  /** The options of a {@link JavaFxMediaPlayer}. */
  public static final class Builder {
    private ImageLoader imageLoader = A2uiServices.DEFAULT.imageLoader();
    private UrlOpener urlOpener = A2uiServices.DEFAULT.urlOpener();
    private double volume = 1;

    private Builder() {}

    /**
     * Sets the service that loads the poster image of a video. Give the image loader of the
     * services of the surface. The default is the image loader of {@link A2uiServices#DEFAULT}.
     *
     * @param imageLoader the loader
     * @return this builder
     */
    public Builder imageLoader(ImageLoader imageLoader) {
      this.imageLoader = Objects.requireNonNull(imageLoader, "imageLoader");
      return this;
    }

    /**
     * Sets the service that opens a media in an external application. A view offers this to the
     * user when JavaFX cannot play the media. The default is the URL opener of {@link
     * A2uiServices#DEFAULT}.
     *
     * @param urlOpener the opener
     * @return this builder
     */
    public Builder urlOpener(UrlOpener urlOpener) {
      this.urlOpener = Objects.requireNonNull(urlOpener, "urlOpener");
      return this;
    }

    /**
     * Sets the volume that each new view has. The user can change the volume of a view. The default
     * is 1.
     *
     * @param volume the volume, from 0 (no sound) to 1 (full volume)
     * @return this builder
     * @throws IllegalArgumentException if the volume is not in that range
     */
    public Builder volume(double volume) {
      if (!(volume >= 0 && volume <= 1)) {
        throw new IllegalArgumentException("The volume must be from 0 to 1.");
      }
      this.volume = volume;
      return this;
    }

    /**
     * Makes the player.
     *
     * @return a new player with the options of this builder
     */
    public JavaFxMediaPlayer build() {
      return new JavaFxMediaPlayer(this);
    }
  }
}
