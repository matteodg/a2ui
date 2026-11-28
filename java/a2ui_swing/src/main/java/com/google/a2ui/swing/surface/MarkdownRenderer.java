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

/**
 * Makes the views that show Markdown text. The {@code Text} component of the basic catalog uses
 * this service.
 *
 * <p>An application that shows Markdown in the rest of its UI gives its own implementation. Thus
 * the text of a surface looks the same as the other text of the application.
 */
@FunctionalInterface
public interface MarkdownRenderer {
  /**
   * Makes a view. Each component that shows Markdown has its own view.
   *
   * @return the new view, with no text
   */
  MarkdownView createView();
}
