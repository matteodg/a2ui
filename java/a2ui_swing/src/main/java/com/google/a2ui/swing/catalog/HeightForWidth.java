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

package com.google.a2ui.swing.catalog;

/**
 * A Swing component whose preferred height depends on its width. Text that wraps is the usual
 * example: a smaller width gives more lines.
 *
 * <p>{@code getPreferredSize()} of a Swing component has no width as input. Thus it gives the size
 * of the content with no line breaks. A layout that knows the width of a component asks this
 * interface for the height, in the same layout pass. {@code FlexLayout} does this. A container that
 * has such a component also implements the interface and passes the question to its content, as
 * {@code FlexPanel} does. Then text wraps correctly below each number of containers.
 *
 * <p>A component that does not implement the interface has its usual preferred height at each
 * width.
 */
public interface HeightForWidth {
  /**
   * Gets the preferred height of the component at a width.
   *
   * @param width the width that a layout gives to the component, in pixels, with the insets of the
   *     component
   * @return the height in pixels, with the insets of the component
   */
  int preferredHeightFor(int width);
}
