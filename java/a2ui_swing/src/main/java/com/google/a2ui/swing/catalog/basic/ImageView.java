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

import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.Optional;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;

/**
 * The view of the {@code Image} component of the basic catalog: a component that paints the image
 * that the {@code imageLoader} service loads.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code url}</td><td>The URL of the image. The view examines it with {@code MediaUrls}.
 *       It asks the loader only for a URL that is safe. The loader works on a different thread.
 *       When the URL changes, the view cancels the request and starts a new one.</td></tr>
 *   <tr><td>{@code description}</td><td>The accessible name and the tool tip of the image, unless
 *       the agent gave a label.</td></tr>
 *   <tr><td>{@code variant}</td><td>The size of the box of the image. See the list below. The
 *       default is {@code mediumFeature}.</td></tr>
 *   <tr><td>{@code fit}</td><td>How the image fills its box, as CSS {@code object-fit}: {@code
 *       contain}, {@code cover}, {@code fill}, {@code none} or {@code scaleDown}. The default is
 *       {@code fill}, and {@code cover} for the variant {@code header}.</td></tr>
 * </table>
 *
 * <p>The box of each variant is this:
 *
 * <ul>
 *   <li>{@code icon}: a square of {@value #ICON_SIDE} pixels.
 *   <li>{@code avatar}: a circle of {@value #AVATAR_SIDE} pixels.
 *   <li>{@code smallFeature}: the proportions of the image, at most {@value #SMALL_SIDE} pixels
 *       wide and high.
 *   <li>{@code mediumFeature}: the proportions of the image, at most {@value #MEDIUM_WIDTH} pixels
 *       wide and {@value #MAX_HEIGHT} pixels high.
 *   <li>{@code largeFeature}: the proportions of the image, at most as wide as the container and
 *       {@value #MAX_HEIGHT} pixels high.
 *   <li>{@code header}: the width of the container and a height of {@value #HEADER_HEIGHT} pixels.
 * </ul>
 *
 * <p>A box with the proportions of the image is never larger than the image, and never wider than
 * the container. Thus the image is not distorted with the default {@code fit}, as an {@code img}
 * element with an automatic height is not. The value of {@code fit} changes the result in the boxes
 * that have a fixed size: {@code icon}, {@code avatar} and {@code header}.
 *
 * <p>While the image loads, the view shows an area with a neutral color that has the size of the
 * variant. When the loader gives no image, the area also shows a small mark. A URL that is absent
 * or not safe shows the area with no mark. That is not an error of the surface. The box gets the
 * proportions of the image when the image arrives.
 *
 * <p>The Swing component of the view is a panel that gives the margin of a leaf. The accessibility
 * attributes go to the component that paints the image. Its accessible role is an icon. It has the
 * URL that the view accepted as the client property {@link SwingBasicCatalog#MEDIA_URL_PROPERTY}.
 */
final class ImageView implements SwingComponentView {
  /** The width and the height of the variant {@code icon}, in pixels. */
  static final int ICON_SIDE = 24;

  /** The width and the height of the variant {@code avatar}, in pixels. */
  static final int AVATAR_SIDE = 40;

  /** The largest width and height of the variant {@code smallFeature}, in pixels. */
  static final int SMALL_SIDE = 100;

  /** The largest width of the variant {@code mediumFeature}, in pixels. */
  static final int MEDIUM_WIDTH = 300;

  /** The largest height of the variants that have the proportions of the image, in pixels. */
  static final int MAX_HEIGHT = 400;

  /** The height of the variant {@code header}, in pixels. */
  static final int HEADER_HEIGHT = 200;

  /** The preferred width of a variant that fills the width of its container, in pixels. */
  private static final int NATURAL_WIDTH = 600;

  /** The values of the property {@code variant}. */
  enum Variant {
    /** A very small square. */
    ICON,
    /** A small circle. */
    AVATAR,
    /** A small image. */
    SMALL_FEATURE,
    /** An image of medium size. */
    MEDIUM_FEATURE,
    /** A large image. */
    LARGE_FEATURE,
    /** A band with the width of the container. */
    HEADER;

    /** Gets the variant for a value of the property. An unknown value gives the default. */
    static Variant fromWire(Object value) {
      if (!(value instanceof String text)) {
        return MEDIUM_FEATURE;
      }
      return switch (text) {
        case "icon" -> ICON;
        case "avatar" -> AVATAR;
        case "smallFeature" -> SMALL_FEATURE;
        case "largeFeature" -> LARGE_FEATURE;
        case "header" -> HEADER;
        default -> MEDIUM_FEATURE;
      };
    }
  }

  private final LeafPanel panel = new LeafPanel();
  private final ImageSource source;
  private final Canvas canvas;
  private String description = "";

  /**
   * Makes the view.
   *
   * @param context the context of the view. It gives the {@code imageLoader} service.
   */
  ImageView(ViewContext context) {
    source = new ImageSource(context.services().imageLoader(), this::loaded);
    canvas = new Canvas(source);
    panel.add(canvas);
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public JComponent accessibleComponent() {
    return canvas;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    Variant variant = Variant.fromWire(props.value("variant"));
    canvas.variant = variant;
    canvas.fit =
        Picture.Fit.fromWire(props.value("fit"))
            .orElse(variant == Variant.HEADER ? Picture.Fit.COVER : Picture.Fit.FILL);
    source.show(props.value("url"));
    canvas.putClientProperty(SwingBasicCatalog.MEDIA_URL_PROPERTY, source.url().orElse(null));
    Object text = props.value("description");
    description = text == null ? "" : Coercion.toText(text).strip();
    canvas.setToolTipText(description.isEmpty() ? null : description);
    panel.revalidate();
    panel.repaint();
  }

  /** Gets the description, as the name of the image for assistive tools. */
  @Override
  public Optional<String> inferredAccessibleName() {
    return description.isEmpty() ? Optional.empty() : Optional.of(description);
  }

  @Override
  public void dispose() {
    source.dispose();
    canvas.setToolTipText(null);
  }

  /** Shows the result of the loader. The box of the image can have a different size now. */
  private void loaded() {
    canvas.revalidate();
    panel.revalidate();
    panel.repaint();
  }

  /**
   * Calculates the box of an image.
   *
   * @param variant the variant
   * @param available the width that the container gives, in pixels
   * @param picture the loaded image, or null
   * @return the size of the box in pixels. Each side is 1 pixel or more.
   */
  static Dimension box(Variant variant, int available, Picture picture) {
    int width = Math.max(1, available);
    return switch (variant) {
      case ICON -> new Dimension(ICON_SIDE, ICON_SIDE);
      case AVATAR -> new Dimension(AVATAR_SIDE, AVATAR_SIDE);
      case HEADER -> new Dimension(width, HEADER_HEIGHT);
      case SMALL_FEATURE -> {
        int side = Math.min(width, SMALL_SIDE);
        yield picture == null ? new Dimension(side, side) : proportional(picture, side, SMALL_SIDE);
      }
      case MEDIUM_FEATURE -> {
        int limit = Math.min(width, MEDIUM_WIDTH);
        yield picture == null
            ? new Dimension(limit, Math.max(1, Math.round(limit * 2 / 3f)))
            : proportional(picture, limit, MAX_HEIGHT);
      }
      case LARGE_FEATURE ->
          picture == null
              ? new Dimension(width, Math.max(1, Math.min(MAX_HEIGHT, Math.round(width * 9 / 16f))))
              : proportional(picture, width, MAX_HEIGHT);
    };
  }

  /** Gets the size of an image after it becomes small enough for a largest width and height. */
  private static Dimension proportional(Picture picture, int maxWidth, int maxHeight) {
    double scale =
        Math.min(
            1,
            Math.min((double) maxWidth / picture.width(), (double) maxHeight / picture.height()));
    return new Dimension(
        Math.max(1, (int) Math.round(picture.width() * scale)),
        Math.max(1, (int) Math.round(picture.height() * scale)));
  }

  /** The component that paints the image or the area that replaces it. */
  static final class Canvas extends JComponent implements HeightForWidth {
    private static final long serialVersionUID = 1L;

    /** The side of the mark of an image that did not load, in pixels. */
    private static final int MARK_SIDE = 24;

    private final transient ImageSource source;
    private Variant variant = Variant.MEDIUM_FEATURE;
    private Picture.Fit fit = Picture.Fit.FILL;

    Canvas(ImageSource source) {
      this.source = source;
      setOpaque(false);
      setFocusable(false);
    }

    /** Gets the state of the image. */
    ImageSource.State state() {
      return source.state();
    }

    /** Gets the box of the image at the current width of the component. */
    Dimension box() {
      return boxFor(getWidth());
    }

    private Dimension boxFor(int width) {
      return ImageView.box(variant, width, source.picture().orElse(null));
    }

    private int naturalWidth() {
      return switch (variant) {
        case SMALL_FEATURE -> SMALL_SIDE;
        case MEDIUM_FEATURE -> MEDIUM_WIDTH;
        default -> NATURAL_WIDTH;
      };
    }

    @Override
    public Dimension getPreferredSize() {
      return isPreferredSizeSet() ? super.getPreferredSize() : boxFor(naturalWidth());
    }

    @Override
    public Dimension getMinimumSize() {
      if (isMinimumSizeSet()) {
        return super.getMinimumSize();
      }
      // A box with a fixed size does not become smaller. Each other box can become small.
      return variant == Variant.ICON || variant == Variant.AVATAR
          ? boxFor(naturalWidth())
          : new Dimension(ICON_SIDE, ICON_SIDE);
    }

    @Override
    public int preferredHeightFor(int width) {
      return isPreferredSizeSet() ? super.getPreferredSize().height : boxFor(width).height;
    }

    /** Tells if a point is in the box of the image. The tool tip shows only there. */
    @Override
    public boolean contains(int x, int y) {
      Dimension box = box();
      return x >= 0 && y >= 0 && x < box.width && y < Math.min(box.height, getHeight());
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Dimension size = box();
      Rectangle2D box = new Rectangle2D.Double(0, 0, size.width, size.height);
      Shape clip =
          variant == Variant.AVATAR ? new Ellipse2D.Double(0, 0, size.width, size.height) : box;
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Picture picture = source.picture().orElse(null);
        if (picture != null) {
          picture.paint(g, box, fit, clip);
          return;
        }
        g.setColor(
            BasicStyle.mix(BasicStyle.foregroundOf(this), BasicStyle.backgroundOf(this), 0.88));
        g.fill(clip);
        if (source.state() == ImageSource.State.FAILED) {
          paintMark(g, box);
        }
      } catch (RuntimeException e) {
        // A paint never fails the surface.
      } finally {
        g.dispose();
      }
    }

    /** Paints the mark of an image that did not load, in the center of the box. */
    private void paintMark(Graphics2D g, Rectangle2D box) {
      double side = Math.min(MARK_SIDE, Math.min(box.getWidth(), box.getHeight()) * 0.6);
      Shape mark = IconShapes.named("photo").orElse(null);
      if (mark == null || side < 8) {
        return;
      }
      AffineTransform place =
          AffineTransform.getTranslateInstance(
              box.getCenterX() - side / 2, box.getCenterY() - side / 2);
      place.scale(side / IconShapes.VIEW_BOX, side / IconShapes.VIEW_BOX);
      g.setColor(BasicStyle.mix(BasicStyle.foregroundOf(this), BasicStyle.backgroundOf(this), 0.6));
      g.fill(place.createTransformedShape(mark));
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleCanvas();
      }
      return accessibleContext;
    }

    /** The accessible context of the image. */
    private final class AccessibleCanvas extends AccessibleJComponent {
      private static final long serialVersionUID = 1L;

      @Override
      public AccessibleRole getAccessibleRole() {
        return AccessibleRole.ICON;
      }
    }
  }
}
