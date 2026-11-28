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

package com.google.a2ui.swing.catalog.basic;

import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.swing.surface.ImageLoader;
import com.google.a2ui.swing.surface.MediaUrls;
import java.awt.EventQueue;
import java.awt.Image;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads the image of one media URL property with the {@code imageLoader} service, and keeps the
 * result. The views of {@code Image} and of {@code Video} use it.
 *
 * <p>The object examines each value with {@link MediaUrls}. It asks the loader only for a URL that
 * is safe. It cancels the request when the value changes and when the view is disposed. It ignores
 * a result that arrives after that. Use an object only on the event dispatch thread.
 */
final class ImageSource {
  private static final System.Logger LOG = System.getLogger("com.google.a2ui.swing");

  /** The state of the image of the current value. */
  enum State {
    /** The value is absent or is not a safe URL. There is no image and no request. */
    NONE,
    /** The loader did not give a result yet. */
    LOADING,
    /** The image is there. */
    LOADED,
    /** The loader gave no image. */
    FAILED
  }

  private final ImageLoader loader;
  private final Runnable changed;

  /** The value of the property that this object got last, or null before the first one. */
  private Object value;

  private boolean first = true;
  private URI url;
  private State state = State.NONE;
  private Picture picture;
  private Subscription request;

  /** The number of the current request. A result of a request with an older number is late. */
  private int generation;

  private boolean disposed;

  /**
   * Makes the object.
   *
   * @param loader the image loader of the surface
   * @param changed runs after each change of the state that comes from the loader
   */
  ImageSource(ImageLoader loader, Runnable changed) {
    this.loader = Objects.requireNonNull(loader, "loader");
    this.changed = Objects.requireNonNull(changed, "changed");
  }

  /**
   * Gets the state of the image.
   *
   * @return the state
   */
  State state() {
    return state;
  }

  /**
   * Gets the loaded image.
   *
   * @return the image, or an empty result if the state is not {@link State#LOADED}
   */
  Optional<Picture> picture() {
    return Optional.ofNullable(picture);
  }

  /**
   * Gets the URL of the current value.
   *
   * @return the URL, or an empty result if the value is absent or is not a safe URL
   */
  Optional<URI> url() {
    return Optional.ofNullable(url);
  }

  /**
   * Shows the resolved value of the URL property. A value that is equal to the value before it has
   * no effect.
   *
   * @param next the value of the property
   * @return true if the value is different from the value before it
   */
  boolean show(Object next) {
    if (disposed || (!first && Objects.equals(next, value))) {
      return false;
    }
    first = false;
    value = next;
    cancel();
    picture = null;
    url = MediaUrls.safe(next).orElse(null);
    if (url == null) {
      state = State.NONE;
      return true;
    }
    state = State.LOADING;
    int id = generation;
    try {
      Subscription started = loader.load(url, result -> deliver(id, result));
      if (id == generation && state == State.LOADING) {
        request = started;
      }
    } catch (RuntimeException e) {
      LOG.log(System.Logger.Level.ERROR, "The image loader failed for " + shortUrl() + ".", e);
      if (id == generation) {
        state = State.FAILED;
      }
    }
    return true;
  }

  /** Gets the result of a request. The loader calls it on the event dispatch thread. */
  private void deliver(int id, Optional<Image> result) {
    if (!EventQueue.isDispatchThread()) {
      // A loader of the application did not obey the contract. The result goes to the correct
      // thread.
      EventQueue.invokeLater(() -> deliver(id, result));
      return;
    }
    if (disposed || id != generation || state != State.LOADING) {
      return;
    }
    request = null;
    Optional<Picture> loaded = Optional.empty();
    try {
      loaded = result == null ? Optional.empty() : result.flatMap(Picture::of);
    } catch (RuntimeException e) {
      LOG.log(System.Logger.Level.ERROR, "The image of " + shortUrl() + " is not correct.", e);
    }
    picture = loaded.orElse(null);
    state = picture == null ? State.FAILED : State.LOADED;
    changed.run();
  }

  /** Cancels the request and releases the image. A second call has no effect. */
  void dispose() {
    if (disposed) {
      return;
    }
    disposed = true;
    cancel();
    picture = null;
    state = State.NONE;
  }

  private void cancel() {
    generation++;
    Subscription active = request;
    request = null;
    if (active != null) {
      try {
        active.cancel();
      } catch (RuntimeException e) {
        LOG.log(System.Logger.Level.ERROR, "The image loader failed in a cancellation.", e);
      }
    }
  }

  /** Gets the start of the URL for a log message. A data URL can be very long. */
  private String shortUrl() {
    String text = String.valueOf(url);
    return text.length() <= 80 ? text : text.substring(0, 80) + "...";
  }
}
