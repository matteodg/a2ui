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

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * The default {@link ModalPresenter}. It shows the content in a modal dialog above the window of
 * the owner. It needs a display.
 */
final class DialogModalPresenter implements ModalPresenter {
  @Override
  public Handle present(JComponent owner, JComponent content, Runnable onClosed) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(onClosed, "onClosed");
    Window window = SwingUtilities.getWindowAncestor(owner);
    JDialog dialog = new JDialog(window, Dialog.ModalityType.DOCUMENT_MODAL);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    dialog.getContentPane().add(content, BorderLayout.CENTER);
    dialog.pack();
    dialog.setLocationRelativeTo(window);
    // The element tells if the dialog is closed, or if the handle starts to close it.
    boolean[] closed = {false};
    dialog.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent event) {
            dialog.getContentPane().remove(content);
            if (!closed[0]) {
              closed[0] = true;
              onClosed.run();
            }
          }
        });
    // A modal dialog keeps the caller in setVisible until the dialog closes. Thus the dialog opens
    // after this method returns.
    EventQueue.invokeLater(() -> {
      if (!closed[0]) {
        dialog.setVisible(true);
      }
    });
    return () -> {
      if (!closed[0]) {
        closed[0] = true;
        dialog.dispose();
      }
    };
  }
}
