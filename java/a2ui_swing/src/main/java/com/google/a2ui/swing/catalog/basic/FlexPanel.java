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

import com.google.a2ui.swing.catalog.HeightForWidth;
import java.awt.LayoutManager;
import javax.swing.JPanel;

/**
 * A panel with a {@link FlexLayout}. The views of the containers of the basic catalog use it, and a
 * custom container can also use it.
 *
 * <p>The panel is different from a {@code JPanel} in three points:
 *
 * <ul>
 *   <li>It implements {@link HeightForWidth}. Thus text that wraps below the panel gets the correct
 *       height.
 *   <li>It is not opaque. Thus the background of the component above it shows.
 *   <li>It has no font and no foreground color of its own. Thus {@code getFont()} and {@code
 *       getForeground()} give the values of the component above it, as AWT specifies. A component
 *       above the panel can thus give a text style to all the content below it, with no code in the
 *       views. This is how a button gives a contrast color to its text.
 * </ul>
 */
public class FlexPanel extends JPanel implements HeightForWidth {
  private static final long serialVersionUID = 1L;

  /**
   * Makes a panel whose layout has the default values of the basic catalog.
   *
   * @param axis the direction of the main axis
   */
  public FlexPanel(FlexLayout.Axis axis) {
    this(new FlexLayout(axis));
  }

  /**
   * Makes a panel with a layout.
   *
   * @param layout the layout of the panel
   */
  // The constructor sets only properties of the Swing component. A subclass can override them.
  @SuppressWarnings("this-escape")
  public FlexPanel(FlexLayout layout) {
    super(layout);
    setOpaque(false);
    inheritTextStyle();
  }

  /**
   * Gets the layout of the panel.
   *
   * @return the layout
   * @throws IllegalStateException if code replaced the layout with a layout of a different class
   */
  public FlexLayout flexLayout() {
    if (getLayout() instanceof FlexLayout layout) {
      return layout;
    }
    throw new IllegalStateException("The layout of a FlexPanel must be a FlexLayout.");
  }

  /**
   * Sets the layout of the panel.
   *
   * @param layout the layout. It must be a {@link FlexLayout}.
   * @throws IllegalArgumentException if the layout is of a different class
   */
  @Override
  public void setLayout(LayoutManager layout) {
    if (!(layout instanceof FlexLayout)) {
      throw new IllegalArgumentException("The layout of a FlexPanel must be a FlexLayout.");
    }
    super.setLayout(layout);
  }

  @Override
  public int preferredHeightFor(int width) {
    if (isPreferredSizeSet()) {
      return getPreferredSize().height;
    }
    return flexLayout().preferredHeight(this, width);
  }

  /** Applies the look and feel, and then removes the font and the color that it set. */
  @Override
  public void updateUI() {
    super.updateUI();
    inheritTextStyle();
  }

  private void inheritTextStyle() {
    setFont(null);
    setForeground(null);
  }
}
