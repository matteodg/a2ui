/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.a2ui.swing.testing;

import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.surface.ModalPresenter;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * A {@link ModalPresenter} that needs no window, for tests that run with no display. It keeps the
 * content of each modal in a panel that is not in a window. Use it on the event dispatch thread.
 */
public final class TestModalPresenter implements ModalPresenter {
  /** One modal that the presenter got. */
  public static final class Presentation {
    private final JComponent owner;
    private final JComponent content;
    private final Runnable onClosed;
    private final JPanel holder = new JPanel(new BorderLayout());
    private boolean open = true;
    private boolean closedByHandle;

    private Presentation(JComponent owner, JComponent content, Runnable onClosed) {
      this.owner = owner;
      this.content = content;
      this.onClosed = onClosed;
      // The content is connected as in a dialog. Thus its layout works.
      JPanel outer = new JPanel(null);
      outer.add(holder);
      holder.add(content, BorderLayout.CENTER);
      holder.addNotify();
    }

    /**
     * Gets the component that opened the modal.
     *
     * @return the owner
     */
    public JComponent owner() {
      return owner;
    }

    /**
     * Gets the content of the modal.
     *
     * @return the content
     */
    public JComponent content() {
      return content;
    }

    /**
     * Tells if the modal shows.
     *
     * @return false after the user or the handle closed the modal
     */
    public boolean isOpen() {
      return open;
    }

    /**
     * Tells if the handle closed the modal.
     *
     * @return true after a call of {@code Handle.close()}
     */
    public boolean closedByHandle() {
      return closedByHandle;
    }

    /** Closes the modal as the user does, and tells the opener. A second call has no effect. */
    public void userCloses() {
      if (open) {
        release();
        onClosed.run();
      }
    }

    /**
     * Gives the content a width and the height that it needs at that width, and does its layout.
     *
     * @param width the width in pixels
     * @return the panel that has the content, with its size
     */
    public JComponent layout(int width) {
      int height =
          content instanceof HeightForWidth wrapping
              ? wrapping.preferredHeightFor(width)
              : content.getPreferredSize().height;
      holder.setSize(width, height);
      holder.validate();
      return holder;
    }

    private void release() {
      open = false;
      holder.removeNotify();
      holder.remove(content);
    }
  }

  private final List<Presentation> presentations = new ArrayList<>();

  @Override
  public Handle present(JComponent owner, JComponent content, Runnable onClosed) {
    Presentation presentation = new Presentation(owner, content, onClosed);
    presentations.add(presentation);
    return () -> {
      if (presentation.open) {
        presentation.closedByHandle = true;
        presentation.release();
      }
    };
  }

  /**
   * Gets each modal that the presenter got, in the sequence of the calls.
   *
   * @return the modals. The list is a read-only copy.
   */
  public List<Presentation> presentations() {
    return List.copyOf(presentations);
  }

  /**
   * Gets the modals that show at this time.
   *
   * @return the open modals. The list is a read-only copy.
   */
  public List<Presentation> open() {
    return presentations.stream().filter(Presentation::isOpen).toList();
  }
}
