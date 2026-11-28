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

import com.google.a2ui.core.basic_catalog.v1_0.UrlOpener;
import com.google.a2ui.swing.catalog.ViewContext;
import com.google.a2ui.swing.surface.MediaPlayer;
import com.google.a2ui.swing.surface.MediaUrls;
import com.google.a2ui.swing.surface.MediaView;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.swing.Icon;

/**
 * The work that the views of {@code Video} and of {@code AudioPlayer} share: the view of the {@code
 * mediaPlayer} service, and the substitute that opens the media in an external application.
 */
final class MediaSupport {
  private static final System.Logger LOG = System.getLogger("com.google.a2ui.swing");

  private MediaSupport() {}

  /**
   * Asks the {@code mediaPlayer} service for a view.
   *
   * @param context the context of the component view
   * @param kind the type of the media
   * @return the view, or an empty result if the service makes none or fails
   */
  static Optional<MediaView> createView(ViewContext context, MediaPlayer.Kind kind) {
    try {
      Optional<MediaView> view = context.services().mediaPlayer().createView(kind);
      if (view == null || view.isEmpty()) {
        return Optional.empty();
      }
      Objects.requireNonNull(view.get().component(), "the media view has no component");
      return view;
    } catch (RuntimeException e) {
      LOG.log(System.Logger.Level.ERROR, "The media player service did not make a view.", e);
      return Optional.empty();
    }
  }

  /**
   * Runs a call of a media view of the application. A failure of the view does not fail the
   * surface.
   *
   * @param call the call
   */
  static void guarded(Runnable call) {
    try {
      call.run();
    } catch (RuntimeException e) {
      LOG.log(System.Logger.Level.ERROR, "The media view of the application failed.", e);
    }
  }

  /**
   * Gets the URL that the substitute opens in an external application.
   *
   * @param value the resolved value of the URL property
   * @return the URL, or an empty result if the value is not a safe URL or is not an absolute {@code
   *     http} or {@code https} URL. A relative path and a {@code data} URL do not go to an external
   *     application.
   */
  static Optional<URI> external(Object value) {
    return MediaUrls.safe(value)
        .filter(uri -> {
          String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
          return scheme.equals("http") || scheme.equals("https");
        });
  }

  /**
   * Opens a URL with the {@code urlOpener} service. Call this method only in the listener of a
   * gesture of the user. A failure of the service does not fail the surface.
   *
   * @param opener the service
   * @param uri the URL
   */
  static void open(UrlOpener opener, URI uri) {
    try {
      opener.open(uri);
    } catch (RuntimeException e) {
      LOG.log(System.Logger.Level.ERROR, "The URL opener did not open " + uri + ".", e);
    }
  }

  /** An icon that paints a shape of {@link IconShapes} in the text color of its component. */
  static final class ShapeIcon implements Icon {
    private final Shape shape;
    private final int side;

    /**
     * Makes an icon.
     *
     * @param name the name of the shape in the catalog
     * @param side the width and the height of the icon, in pixels
     */
    ShapeIcon(String name, int side) {
      this.shape = IconShapes.named(name).orElseThrow();
      this.side = side;
    }

    @Override
    public int getIconWidth() {
      return side;
    }

    @Override
    public int getIconHeight() {
      return side;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(x, y);
        g.scale((double) side / IconShapes.VIEW_BOX, (double) side / IconShapes.VIEW_BOX);
        Color color =
            component.isEnabled()
                ? BasicStyle.foregroundOf(component)
                : BasicStyle.disabled(component);
        g.setColor(color);
        g.fill(shape);
      } finally {
        g.dispose();
      }
    }
  }
}
