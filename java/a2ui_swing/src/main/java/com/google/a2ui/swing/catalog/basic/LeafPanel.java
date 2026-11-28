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

import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.BorderFactory;

/**
 * The outer panel of a leaf or of a control with an outline. It gives the margin of the leaf margin
 * strategy to its content, as an empty border.
 *
 * <p>The margin, and the text style of a subclass, depend on the components above the panel. Swing
 * does not tell a component that a property of a component above it changed. Thus the panel reads
 * these values each time a layout asks for its size, and each time it paints. This costs a walk to
 * the top of the component tree, which is short.
 */
class LeafPanel extends FlexPanel {
  private static final long serialVersionUID = 1L;

  /** The margin that the border has, or -1 before the first call of {@link #sync()}. */
  private int margin = -1;

  /** Makes a panel that puts its content in a column. */
  LeafPanel() {
    super(FlexLayout.Axis.VERTICAL);
  }

  /**
   * Applies the values that depend on the components above the panel. A subclass adds its own
   * values and calls this method.
   */
  void sync() {
    int wanted = BasicStyle.leafMargin(this);
    if (wanted != margin) {
      margin = wanted;
      setBorder(
          wanted == 0 ? null : BorderFactory.createEmptyBorder(wanted, wanted, wanted, wanted));
    }
  }

  @Override
  public Dimension getPreferredSize() {
    sync();
    return super.getPreferredSize();
  }

  @Override
  public Dimension getMinimumSize() {
    sync();
    return super.getMinimumSize();
  }

  @Override
  public int preferredHeightFor(int width) {
    sync();
    return super.preferredHeightFor(width);
  }

  @Override
  public void doLayout() {
    sync();
    super.doLayout();
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    sync();
    super.paintComponent(graphics);
  }
}
