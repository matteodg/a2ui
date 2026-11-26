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

import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;

/**
 * The view of the {@code Icon} component of the basic catalog: a shape that the view paints in the
 * text color.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code name}</td><td>A name of the catalog, for example {@code arrowBack}, shows the
 *       shape of the module for that name. An object with the key {@code svgPath} shows the SVG
 *       path data of the agent, in a view box of 24 by 24 units and with the non-zero fill rule.
 *       A text that is not a name of the catalog shows the shape of the name {@code help}. Thus
 *       the user sees that an icon is there. Path data that is not correct, and a value that is
 *       not a text and not path data, show no shape. The icon keeps its space then.</td></tr>
 * </table>
 *
 * <p>The icon is a square of {@value #SIZE} pixels, or of 1.5 times the size of the font if that is
 * larger. Thus an icon is as large as a line of text, also with a large font. The color is the text
 * color of the component above the icon. Thus an icon in a primary button has the color of the text
 * of the button.
 *
 * <p>Path data from the agent is not trusted. The view accepts at most {@value SvgPath#MAX_LENGTH}
 * characters, it paints only in the square of the icon, and it does not throw from a paint.
 *
 * <p>The Swing component of the view is a panel that gives the margin of a leaf. The accessibility
 * attributes go to the component that paints the shape. Its accessible role is an icon. Its
 * accessible name is the name of the icon as words, for example {@code arrow back}, unless the
 * agent gave a label. An icon from path data has no name. An icon whose name the catalog does not
 * have also has no name: the view does not tell the word {@code help} for it. A button whose
 * content is only icons takes its name from the names of the icons. The component has the name that
 * the agent gave as the client property {@link SwingBasicCatalog#ICON_NAME_PROPERTY}.
 */
final class IconView implements SwingComponentView {
  /** The least width and height of an icon, in pixels. */
  static final int SIZE = 24;

  /** The size of an icon as a multiple of the size of the font. */
  private static final float FONT_SCALE = 1.5f;

  private static final String SVG_PATH = "svgPath";

  /** The name of the shape that the view shows for a name that the catalog does not have. */
  static final String UNKNOWN_NAME_SHAPE = "help";

  private final LeafPanel panel = new LeafPanel();
  private final Glyph glyph = new Glyph();

  /** The value of the property {@code name} that the glyph shows, or null before the first one. */
  private Object shown;

  private boolean first = true;

  /**
   * Makes the view.
   *
   * @param context the context of the view
   */
  IconView(ViewContext context) {
    // The icon keeps its size at the start of each axis of the panel.
    panel.flexLayout().setAlign(FlexLayout.Align.START);
    panel.add(glyph);
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public JComponent accessibleComponent() {
    return glyph;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    Object name = props.value("name");
    if (!first && Objects.equals(name, shown)) {
      return;
    }
    first = false;
    shown = name;
    Shape shape = null;
    String sent = null;
    String known = null;
    if (name instanceof String text) {
      sent = text;
      shape = IconShapes.named(text).orElse(null);
      if (shape != null) {
        known = text;
      } else {
        shape = IconShapes.named(UNKNOWN_NAME_SHAPE).orElse(null);
      }
    } else if (name instanceof Map<?, ?> map && map.get(SVG_PATH) instanceof String data) {
      shape = SvgPath.parse(data).orElse(null);
    }
    glyph.show(shape, sent, known);
  }

  /** Gets the name of a named icon as words, as the name of the icon for assistive tools. */
  @Override
  public Optional<String> inferredAccessibleName() {
    return Optional.ofNullable(glyph.spokenName());
  }

  /**
   * Writes a name of the catalog as words: {@code arrowBack} gives {@code arrow back}.
   *
   * @param name the name of an icon
   * @return the words in lower case
   */
  static String words(String name) {
    StringBuilder words = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isUpperCase(c) && i > 0) {
        words.append(' ');
      }
      words.append(Character.toLowerCase(c));
    }
    return words.toString();
  }

  /** The component that paints the shape. */
  static final class Glyph extends JComponent {
    private static final long serialVersionUID = 1L;

    /** The shape in the view box, or null if the icon shows nothing. */
    private transient Shape shape;

    /** The name of the catalog that the shape has, or null if the shape is not of a name. */
    private String name;

    Glyph() {
      // The component sets no color and no font. Thus it has those of the component above it.
      setOpaque(false);
      setFocusable(false);
    }

    /**
     * Sets the shape.
     *
     * @param nextShape the shape, or null if the icon shows nothing
     * @param sentName the name that the agent gave, or null if the agent gave no name
     * @param knownName the name of the catalog that the shape has, or null. The name is null for a
     *     name that the catalog does not have, although the icon shows a shape then.
     */
    void show(Shape nextShape, String sentName, String knownName) {
      shape = nextShape;
      name = knownName;
      putClientProperty(SwingBasicCatalog.ICON_NAME_PROPERTY, sentName);
      repaint();
    }

    /** Tells if the icon shows a shape. */
    boolean hasShape() {
      return shape != null;
    }

    /** Gets the name of a named icon as words, or null. */
    String spokenName() {
      return name == null ? null : words(name);
    }

    private int side() {
      return Math.max(SIZE, Math.round(BasicStyle.fontOf(this).getSize2D() * FONT_SCALE));
    }

    @Override
    public Dimension getPreferredSize() {
      if (isPreferredSizeSet()) {
        return super.getPreferredSize();
      }
      int side = side();
      return new Dimension(side, side);
    }

    @Override
    public Dimension getMinimumSize() {
      return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
      return isMaximumSizeSet() ? super.getMaximumSize() : getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Shape painted = shape;
      if (painted == null) {
        return;
      }
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        // The shape is in the center of the component, in the largest square that fits.
        double side = Math.min(getWidth(), getHeight());
        double x = (getWidth() - side) / 2;
        double y = (getHeight() - side) / 2;
        g.clip(new Rectangle2D.Double(x, y, side, side));
        g.translate(x, y);
        g.scale(side / IconShapes.VIEW_BOX, side / IconShapes.VIEW_BOX);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(BasicStyle.foregroundOf(this));
        g.fill(painted);
      } catch (RuntimeException e) {
        // A shape from the agent that Java2D cannot paint shows nothing.
        shape = null;
      } finally {
        g.dispose();
      }
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleGlyph();
      }
      return accessibleContext;
    }

    /** The accessible context of the icon. */
    private final class AccessibleGlyph extends AccessibleJComponent {
      private static final long serialVersionUID = 1L;

      @Override
      public AccessibleRole getAccessibleRole() {
        return AccessibleRole.ICON;
      }
    }
  }
}
