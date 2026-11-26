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

import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/**
 * The view of the default {@link MarkdownRenderer} of this version. It shows the text as the agent
 * wrote it, with the Markdown marks, in a text area that looks like a label. It breaks long lines
 * between words.
 */
final class PlainTextMarkdownView implements MarkdownView {
  private final JTextArea area = new JTextArea();

  PlainTextMarkdownView() {
    area.setEditable(false);
    area.setFocusable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setOpaque(false);
    area.setBorder(null);
    // The text looks like the text of a label in each look and feel.
    area.setFont(UIManager.getFont("Label.font"));
    area.setForeground(UIManager.getColor("Label.foreground"));
  }

  @Override
  public JComponent component() {
    return area;
  }

  @Override
  public void setMarkdown(String markdown) {
    Objects.requireNonNull(markdown, "markdown");
    if (!markdown.equals(area.getText())) {
      area.setText(markdown);
    }
  }
}
