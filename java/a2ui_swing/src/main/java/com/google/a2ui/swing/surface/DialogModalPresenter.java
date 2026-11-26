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

package com.google.a2ui.swing.surface;

import com.google.a2ui.swing.catalog.HeightForWidth;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * The default {@link ModalPresenter}. It shows the content in a modal dialog above the window of
 * the owner, in the center of that window. It needs a display.
 *
 * <p>The user closes the dialog with the close control of the window or with the Escape key. The
 * dialog blocks only the window of the owner.
 *
 * <p>The dialog has the size of the content. It is not larger than the window of the owner less a
 * distance to each side. Text in the content breaks at the width of the dialog. If the content is
 * higher than the dialog, the dialog scrolls it.
 */
final class DialogModalPresenter implements ModalPresenter {
  /** The space between the edge of the dialog and the content, in pixels. */
  static final int PADDING = 8;

  /** The least width of the content area of the dialog, in pixels. */
  static final int MINIMUM_WIDTH = 240;

  /** The least height of the limit of the content area of the dialog, in pixels. */
  static final int MINIMUM_HEIGHT = 160;

  /** The distance between each side of the window of the owner and the dialog, in pixels. */
  static final int DISTANCE = 48;

  /** The limit of the content area of a dialog whose owner has no window. */
  static final Dimension DEFAULT_LIMIT = new Dimension(640, 480);

  private static final int SCROLL_UNIT = 16;

  @Override
  public Handle present(JComponent owner, JComponent content, Runnable onClosed) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(onClosed, "onClosed");
    Window window = SwingUtilities.getWindowAncestor(owner);
    JDialog dialog = new JDialog(window, Dialog.ModalityType.DOCUMENT_MODAL);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    Holder holder = new Holder(content);
    JScrollPane scroll =
        new JScrollPane(
            holder,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.setViewportBorder(BorderFactory.createEmptyBorder());
    dialog.getContentPane().add(scroll, BorderLayout.CENTER);
    // The first call connects the content to the screen of the dialog. Text has its correct size
    // only then, because a screen can have a scale.
    dialog.pack();
    scroll.setPreferredSize(areaSize(content, limitFor(window)));
    dialog
        .getRootPane()
        .registerKeyboardAction(
            event -> dialog.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    dialog.pack();
    dialog.setLocationRelativeTo(window);
    // The element tells if the dialog is closed, or if the handle starts to close it.
    boolean[] closed = {false};
    dialog.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent event) {
            holder.remove(content);
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

  /**
   * Gets the largest size of the content area of a dialog above a window.
   *
   * @param window the window of the owner, or null
   * @return the size of the window less {@link #DISTANCE} on each side, and not less than the
   *     minimum values
   */
  static Dimension limitFor(Window window) {
    if (window == null || window.getWidth() <= 0 || window.getHeight() <= 0) {
      return new Dimension(DEFAULT_LIMIT);
    }
    return new Dimension(
        Math.max(MINIMUM_WIDTH, window.getWidth() - 2 * DISTANCE),
        Math.max(MINIMUM_HEIGHT, window.getHeight() - 2 * DISTANCE));
  }

  /**
   * Gets the size of the content area of the dialog for content.
   *
   * @param content the content
   * @param limit the largest size
   * @return the width of the content with no line breaks and with the padding, between {@link
   *     #MINIMUM_WIDTH} and the limit, and the height that the content needs at that width, not
   *     more than the limit
   */
  static Dimension areaSize(JComponent content, Dimension limit) {
    Dimension natural = content.getPreferredSize();
    int width =
        Math.max(
            Math.min(MINIMUM_WIDTH, limit.width),
            Math.min(natural.width + 2 * PADDING, limit.width));
    int height = heightAt(content, width - 2 * PADDING) + 2 * PADDING;
    return new Dimension(width, Math.min(height, limit.height));
  }

  private static int heightAt(JComponent content, int width) {
    return content instanceof HeightForWidth wrapping
        ? wrapping.preferredHeightFor(Math.max(0, width))
        : content.getPreferredSize().height;
  }

  /**
   * The panel in the scroll pane of the dialog. It gives the padding to the content, and it has the
   * width of the dialog. Thus text in the content breaks at that width.
   */
  private static final class Holder extends JPanel implements Scrollable {
    private static final long serialVersionUID = 1L;

    private final JComponent content;

    Holder(JComponent content) {
      super(new BorderLayout());
      this.content = content;
      setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
      add(content, BorderLayout.CENTER);
    }

    private int heightFor(int width) {
      return heightAt(content, width - 2 * PADDING) + 2 * PADDING;
    }

    /**
     * Gets the preferred size: the size of the content at the width that the scroll pane has for
     * it. The result comes from the size of the scroll pane, and not from the size of the viewport.
     * The layout of a scroll pane asks for it before it gives a size to the viewport.
     */
    @Override
    public Dimension getPreferredSize() {
      if (isPreferredSizeSet()
          || !(getParent() instanceof JViewport viewport)
          || !(viewport.getParent() instanceof JScrollPane pane)
          || pane.getWidth() <= 0) {
        return super.getPreferredSize();
      }
      Insets insets = pane.getInsets();
      int fullWidth = Math.max(0, pane.getWidth() - insets.left - insets.right);
      int fullHeight = Math.max(0, pane.getHeight() - insets.top - insets.bottom);
      int height = heightFor(fullWidth);
      if (height <= fullHeight) {
        return new Dimension(fullWidth, height);
      }
      int narrow = Math.max(0, fullWidth - pane.getVerticalScrollBar().getPreferredSize().width);
      return new Dimension(narrow, heightFor(narrow));
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return super.getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
      return SCROLL_UNIT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      int visible = orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
      return Math.max(SCROLL_UNIT, visible - SCROLL_UNIT);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return getParent() instanceof JViewport viewport
          && viewport.getParent() instanceof JScrollPane pane
          && heightFor(pane.getWidth()) <= pane.getHeight();
    }
  }
}
