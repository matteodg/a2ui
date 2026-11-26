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
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import com.google.a2ui.swing.surface.MediaPlayer;
import com.google.a2ui.swing.surface.MediaUrls;
import com.google.a2ui.swing.surface.MediaView;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.net.URI;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;

/**
 * The view of the {@code Video} component of the basic catalog.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code url}</td><td>The URL of the video. The view examines it with {@code
 *       MediaUrls}.</td></tr>
 *   <tr><td>{@code posterUrl}</td><td>The URL of the image that shows before the video plays. The
 *       view examines it with {@code MediaUrls}.</td></tr>
 * </table>
 *
 * <p>Swing cannot play a video. Thus the view asks the {@code mediaPlayer} service for a {@code
 * MediaView}. If the service gives one, the view shows its component with the width of the
 * container, and gives the two URLs to it.
 *
 * <p>The default service gives no view. Then the view shows a substitute: an area with the
 * proportions 16 to 9, at most {@value #MAX_WIDTH} pixels wide, with the poster image and a play
 * mark. The {@code imageLoader} service loads the poster. The area is a button. When the user
 * operates it, the {@code urlOpener} service opens the URL of the video in an external application.
 * The view never opens a URL by itself. The user cannot operate the button, and it shows no play
 * mark, if the URL is absent, is not safe, or is not an absolute {@code http} or {@code https} URL.
 * That is not an error of the surface.
 *
 * <p>The Swing component of the view is a panel that gives the margin of a leaf. The accessibility
 * attributes go to the component of the media view, or to the button. The accessible name of the
 * button tells what it does, unless the agent gave a label. The component has the URL that the view
 * accepted as the client property {@link SwingBasicCatalog#MEDIA_URL_PROPERTY}.
 */
final class VideoView implements SwingComponentView {
  /** The largest width of the substitute, in pixels. */
  static final int MAX_WIDTH = 480;

  /** The accessible name and the tool tip of the button of the substitute. */
  static final String OPEN = "Open the video in an external application";

  private final LeafPanel panel = new LeafPanel();
  private final MediaView media;
  private final Poster poster;
  private final ImageSource image;
  private final UrlOpener opener;
  private final ActionListener listener = event -> open();

  /** The URL that the button opens, or null. */
  private URI target;

  /**
   * Makes the view.
   *
   * @param context the context of the view. It gives the {@code mediaPlayer}, {@code imageLoader}
   *     and {@code urlOpener} services.
   */
  VideoView(ViewContext context) {
    opener = context.services().urlOpener();
    media = MediaSupport.createView(context, MediaPlayer.Kind.VIDEO).orElse(null);
    if (media != null) {
      poster = null;
      image = null;
      panel.add(media.component());
    } else {
      image = new ImageSource(context.services().imageLoader(), this::loaded);
      poster = new Poster(image);
      poster.addActionListener(listener);
      panel.add(poster);
    }
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public JComponent accessibleComponent() {
    return media != null ? media.component() : poster;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    Optional<URI> url = MediaUrls.safe(props.value("url"));
    accessibleComponent().putClientProperty(SwingBasicCatalog.MEDIA_URL_PROPERTY, url.orElse(null));
    if (media != null) {
      MediaSupport.guarded(() -> media.setSource(url));
      MediaSupport.guarded(() -> media.setPoster(MediaUrls.safe(props.value("posterUrl"))));
    } else {
      target = MediaSupport.external(props.value("url")).orElse(null);
      poster.setEnabled(target != null);
      poster.setToolTipText(target == null ? null : OPEN);
      poster.setCursor(
          target == null
              ? Cursor.getDefaultCursor()
              : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      image.show(props.value("posterUrl"));
    }
    panel.revalidate();
    panel.repaint();
  }

  /** Gets the text that tells what the button does, as its name for assistive tools. */
  @Override
  public Optional<String> inferredAccessibleName() {
    return media != null ? Optional.empty() : Optional.of(OPEN);
  }

  @Override
  public void dispose() {
    if (media != null) {
      MediaSupport.guarded(media::dispose);
    } else {
      poster.removeActionListener(listener);
      poster.setToolTipText(null);
      image.dispose();
      target = null;
    }
  }

  /** Opens the video in an external application. The listener of the button calls it. */
  private void open() {
    if (target != null) {
      MediaSupport.open(opener, target);
    }
  }

  private void loaded() {
    poster.repaint();
  }

  /** The button of the substitute. It paints the poster image and the play mark. */
  static final class Poster extends JButton implements HeightForWidth {
    private static final long serialVersionUID = 1L;

    /** The radius of the circle of the play mark, in pixels. */
    private static final int MARK_RADIUS = 22;

    private final transient ImageSource image;

    Poster(ImageSource image) {
      this.image = image;
      setRolloverEnabled(true);
    }

    /** Applies the look and feel, and then removes the parts that the button paints itself. */
    @Override
    public void updateUI() {
      super.updateUI();
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setBorder(BorderFactory.createEmptyBorder());
    }

    /** Gets the state of the poster image. */
    ImageSource.State posterState() {
      return image.state();
    }

    /** Gets the area of the substitute at the current width of the button. */
    Dimension box() {
      return boxFor(getWidth());
    }

    private static Dimension boxFor(int available) {
      int width = Math.max(1, Math.min(available, MAX_WIDTH));
      return new Dimension(width, Math.max(1, Math.round(width * 9 / 16f)));
    }

    @Override
    public Dimension getPreferredSize() {
      return isPreferredSizeSet() ? super.getPreferredSize() : boxFor(MAX_WIDTH);
    }

    @Override
    public Dimension getMinimumSize() {
      return isMinimumSizeSet() ? super.getMinimumSize() : boxFor(4 * MARK_RADIUS);
    }

    @Override
    public int preferredHeightFor(int width) {
      return isPreferredSizeSet() ? super.getPreferredSize().height : boxFor(width).height;
    }

    /** Tells if a point is in the area. The user operates the button only there. */
    @Override
    public boolean contains(int x, int y) {
      Dimension box = box();
      return x >= 0 && y >= 0 && x < box.width && y < Math.min(box.height, getHeight());
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Dimension size = box();
      Rectangle2D box = new Rectangle2D.Double(0, 0, size.width, size.height);
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(
            BasicStyle.mix(BasicStyle.foregroundOf(this), BasicStyle.backgroundOf(this), 0.25));
        g.fill(box);
        image.picture().ifPresent(picture -> picture.paint(g, box, Picture.Fit.COVER, box));
        if (isEnabled()) {
          paintMark(g, box);
        }
        if (isFocusOwner()) {
          g.setColor(BasicStyle.accent());
          g.setStroke(new BasicStroke(2));
          g.draw(new Rectangle2D.Double(1, 1, size.width - 2, size.height - 2));
        }
      } catch (RuntimeException e) {
        // A paint never fails the surface.
      } finally {
        g.dispose();
      }
    }

    /** Paints a circle with a triangle in the center of the area. */
    private void paintMark(Graphics2D g, Rectangle2D box) {
      double radius = Math.min(MARK_RADIUS, Math.min(box.getWidth(), box.getHeight()) / 3);
      double x = box.getCenterX();
      double y = box.getCenterY();
      // The mark is on an image of each color. Thus it has its own two colors.
      g.setColor(new Color(0, 0, 0, getModel().isRollover() ? 200 : 150));
      g.fill(new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius));
      Path2D.Double triangle = new Path2D.Double();
      triangle.moveTo(x - radius * 0.3, y - radius * 0.45);
      triangle.lineTo(x + radius * 0.5, y);
      triangle.lineTo(x - radius * 0.3, y + radius * 0.45);
      triangle.closePath();
      g.setColor(Color.WHITE);
      g.fill(triangle);
    }
  }
}
