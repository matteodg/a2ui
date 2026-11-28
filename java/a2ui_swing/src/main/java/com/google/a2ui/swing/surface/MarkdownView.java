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

package com.google.a2ui.swing.surface;

import javax.swing.JComponent;

/**
 * A view that shows Markdown text. A {@link MarkdownRenderer} makes it.
 *
 * <p>The protocol permits a small subset of Markdown: emphasis, strong text, headings, lists and
 * inline code. A view shows raw HTML as plain text. It shows an image and a link as their text
 * only. Use a view only on the event dispatch thread.
 *
 * <p>The {@code Text} component sets the font and the foreground color of the component of the
 * view. They are the base style of the text. A view shows its text again when one of the two
 * changes.
 *
 * <p>The component of a view whose text wraps implements {@code HeightForWidth}. Then the layout of
 * a container gives the component the height for its width.
 */
public interface MarkdownView {
  /**
   * Gets the Swing component of the view.
   *
   * @return the component. It is the same object for the life of the view.
   */
  JComponent component();

  /**
   * Sets the text to show. The view takes the font and the color of its component as the base style
   * of the text.
   *
   * @param markdown the Markdown text
   */
  void setMarkdown(String markdown);

  /** Releases the resources of the view. The default implementation does nothing. */
  default void dispose() {}
}
