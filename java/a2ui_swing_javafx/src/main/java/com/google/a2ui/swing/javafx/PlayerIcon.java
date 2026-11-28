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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * The marks of the buttons of the player. An icon paints its mark in the text color of its button,
 * or in the color of disabled text. Thus the marks follow the look and feel.
 */
enum PlayerIcon implements Icon {
  /** A triangle that points to the right. */
  PLAY,
  /** Two vertical bars. */
  PAUSE,
  /** A speaker with a sound wave. */
  SOUND,
  /** A speaker with a cross. */
  MUTED;

  /** The width and the height of an icon, in pixels. */
  static final int SIDE = 16;

  @Override
  public int getIconWidth() {
    return SIDE;
  }

  @Override
  public int getIconHeight() {
    return SIDE;
  }

  @Override
  public void paintIcon(Component component, Graphics graphics, int x, int y) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g.translate(x, y);
      g.setColor(colorOf(component));
      g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      switch (this) {
        case PLAY -> {
          Path2D.Double triangle = new Path2D.Double();
          triangle.moveTo(4, 2);
          triangle.lineTo(14, 8);
          triangle.lineTo(4, 14);
          triangle.closePath();
          g.fill(triangle);
        }
        case PAUSE -> {
          g.fill(new Rectangle2D.Double(3, 2, 4, 12));
          g.fill(new Rectangle2D.Double(9, 2, 4, 12));
        }
        case SOUND -> {
          g.fill(speaker());
          g.draw(new Arc2D.Double(6, 4, 6, 8, -60, 120, Arc2D.OPEN));
        }
        case MUTED -> {
          g.fill(speaker());
          g.draw(new Line2D.Double(10.5, 5.5, 15, 10.5));
          g.draw(new Line2D.Double(15, 5.5, 10.5, 10.5));
        }
      }
    } finally {
      g.dispose();
    }
  }

  private static Path2D speaker() {
    Path2D.Double shape = new Path2D.Double();
    shape.moveTo(1, 6);
    shape.lineTo(4, 6);
    shape.lineTo(8, 2.5);
    shape.lineTo(8, 13.5);
    shape.lineTo(4, 10);
    shape.lineTo(1, 10);
    shape.closePath();
    return shape;
  }

  private static Color colorOf(Component component) {
    if (component.isEnabled()) {
      return component.getForeground();
    }
    Color disabled = UIManager.getColor("Button.disabledText");
    if (disabled == null) {
      disabled = UIManager.getColor("Label.disabledForeground");
    }
    return disabled == null ? Color.GRAY : disabled;
  }
}
