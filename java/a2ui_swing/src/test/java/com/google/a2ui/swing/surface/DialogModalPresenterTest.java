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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.testing.Edt;
import java.awt.Dimension;
import java.awt.HeadlessException;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/**
 * The tests run with no display. Thus they examine only the size that the presenter calculates. The
 * dialog itself needs a display.
 */
class DialogModalPresenterTest {
  private static final int PADDING = DialogModalPresenter.PADDING;

  /** Content whose height is its area divided by its width, as text that wraps. */
  private static final class Wrapping extends JPanel implements HeightForWidth {
    private static final long serialVersionUID = 1L;

    private final int area;

    Wrapping(int width, int height) {
      area = width * height;
      setPreferredSize(new Dimension(width, height));
    }

    @Override
    public int preferredHeightFor(int width) {
      return area / Math.max(1, width);
    }
  }

  private static JPanel box(int width, int height) {
    JPanel box = new JPanel();
    box.setPreferredSize(new Dimension(width, height));
    return box;
  }

  @Test
  void theAreaHasTheSizeOfTheContentWithThePadding() {
    Edt.run(() ->
        assertEquals(
            new Dimension(300 + 2 * PADDING, 100 + 2 * PADDING),
            DialogModalPresenter.areaSize(box(300, 100), new Dimension(640, 480))));
  }

  @Test
  void theAreaOfSmallContentHasTheMinimumWidth() {
    Edt.run(() ->
        assertEquals(
            new Dimension(DialogModalPresenter.MINIMUM_WIDTH, 20 + 2 * PADDING),
            DialogModalPresenter.areaSize(box(50, 20), new Dimension(640, 480))));
  }

  @Test
  void contentThatIsTooWideGetsTheWidthOfTheLimitAndTheHeightForThatWidth() {
    Edt.run(() -> {
      // The content is 2000 x 20 with no line breaks. At a width of 400, it is 100 high.
      Dimension area =
          DialogModalPresenter.areaSize(
              new Wrapping(2000, 20), new Dimension(400 + 2 * PADDING, 480));

      assertEquals(new Dimension(400 + 2 * PADDING, 100 + 2 * PADDING), area);
    });
  }

  @Test
  void contentThatIsTooHighGetsTheHeightOfTheLimit() {
    Edt.run(() ->
        assertEquals(
            new Dimension(300 + 2 * PADDING, 200),
            DialogModalPresenter.areaSize(box(300, 900), new Dimension(640, 200))));
  }

  @Test
  void theLimitOfAnOwnerWithNoWindowIsTheDefaultLimit() {
    assertEquals(DialogModalPresenter.DEFAULT_LIMIT, DialogModalPresenter.limitFor(null));
  }

  @Test
  void thePresenterNeedsADisplay() {
    Edt.run(() ->
        assertThrows(
            HeadlessException.class,
            () -> A2uiServices.DEFAULT.modalPresenter().present(box(1, 1), box(1, 1), () -> {})));
  }
}
