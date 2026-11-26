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

import java.awt.Component;
import java.awt.Container;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 * Reads the text that a Swing component and the components below it show to the user. A container
 * view uses it to infer its accessible name, for example a button from the text of its child.
 *
 * <p>The text has no markup: the text of a component that shows HTML is the text of its document.
 * The value of a field that the user can edit is not a part of the text.
 */
final class VisibleText {
  private VisibleText() {}

  /**
   * Reads the visible text of a component.
   *
   * @param component the component
   * @return the text, with one space for each run of white space. It is empty if the component
   *     shows no text.
   */
  static String of(Component component) {
    StringBuilder text = new StringBuilder();
    collect(component, text);
    return text.toString().replaceAll("\\s+", " ").strip();
  }

  private static void collect(Component component, StringBuilder text) {
    if (!component.isVisible()) {
      return;
    }
    switch (component) {
      case JTextComponent field -> {
        if (!field.isEditable()) {
          Document document = field.getDocument();
          try {
            text.append(document.getText(0, document.getLength())).append(' ');
          } catch (BadLocationException e) {
            throw new IllegalStateException(e);
          }
        }
        return;
      }
      case JLabel label -> text.append(withoutTags(label.getText())).append(' ');
      case AbstractButton button -> text.append(withoutTags(button.getText())).append(' ');
      default -> {}
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        collect(child, text);
      }
    }
  }

  /** Removes the tags of the text of a label that shows HTML. */
  private static String withoutTags(String text) {
    if (text == null) {
      return "";
    }
    return text.regionMatches(true, 0, "<html>", 0, 6) ? text.replaceAll("<[^>]*>", " ") : text;
  }
}
