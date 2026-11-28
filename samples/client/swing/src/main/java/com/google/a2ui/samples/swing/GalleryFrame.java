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

package com.google.a2ui.samples.swing;

import com.google.a2ui.swing.surface.A2uiServices;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.WindowConstants;

/**
 * The window of the gallery. It has the three columns of a {@link GalleryPanel} and a menu that
 * changes the look and feel.
 *
 * <p>Use the window only on the event dispatch thread.
 */
final class GalleryFrame {
  private final JFrame frame = new JFrame("A2UI Swing Gallery");
  private final GalleryPanel gallery;
  private final ButtonGroup lookAndFeelItems = new ButtonGroup();

  /**
   * Makes the window. It is not visible.
   *
   * @param examples the examples of the list
   * @param services the services of the surfaces
   * @param lookAndFeel the look and feel that is active, one of {@link LookAndFeels#NAMES}
   */
  GalleryFrame(List<Example> examples, A2uiServices services, String lookAndFeel) {
    gallery = new GalleryPanel(examples, services);
    frame.setContentPane(gallery.component());
    frame.setJMenuBar(menuBar(lookAndFeel));
    // The processor of the session stops with the window. Then the program can end.
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    frame.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent event) {
            gallery.dispose();
          }
        });
    frame.setSize(1280, 800);
    frame.setLocationByPlatform(true);
  }

  private JMenuBar menuBar(String active) {
    JMenu view = new JMenu("View");
    view.setMnemonic('V');
    JMenu lookAndFeel = new JMenu("Look and feel");
    for (String name : LookAndFeels.NAMES) {
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(name, name.equalsIgnoreCase(active));
      item.setActionCommand(name);
      item.addActionListener(event -> setLookAndFeel(name));
      lookAndFeelItems.add(item);
      lookAndFeel.add(item);
    }
    view.add(lookAndFeel);
    JMenuBar bar = new JMenuBar();
    bar.add(view);
    return bar;
  }

  /**
   * Changes the look and feel of the window, as the menu does.
   *
   * @param name one of {@link LookAndFeels#NAMES}
   */
  void setLookAndFeel(String name) {
    try {
      LookAndFeels.apply(name, frame);
    } catch (IllegalArgumentException e) {
      JOptionPane.showMessageDialog(
          frame, e.getMessage(), frame.getTitle(), JOptionPane.ERROR_MESSAGE);
      return;
    }
    lookAndFeelItems
        .getElements()
        .asIterator()
        .forEachRemaining(item -> item.setSelected(item.getActionCommand().equalsIgnoreCase(name)));
  }

  /**
   * Gets the Swing window.
   *
   * @return the window
   */
  JFrame frame() {
    return frame;
  }

  /**
   * Gets the content of the window.
   *
   * @return the panel with the three columns
   */
  GalleryPanel gallery() {
    return gallery;
  }
}
