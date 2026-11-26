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

import static com.google.a2ui.swing.testing.TestSurface.component;
import static com.google.a2ui.swing.testing.TestSurface.object;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.validation.ValidationConfig;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.catalog.basic.SwingBasicCatalog;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.Headless;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import org.junit.jupiter.api.Test;

/** The tests of an {@link A2uiSurface} that is the view of a scroll pane. */
class A2uiSurfaceScrollTest {
  private static final String LONG_TEXT =
      "This paragraph is long. It has many words, and thus it needs many lines in a narrow"
          + " viewport. "
          + "A surface in a scroll pane breaks its text at the width of the viewport. ".repeat(12);

  private JScrollPane scrollPane;
  private A2uiSurface surface;

  private static A2uiSurface surfaceWith(String text) {
    MessageProcessor<SwingComponentImplementation> processor =
        new MessageProcessor<>(List.of(SwingBasicCatalog.create()), ValidationConfig.RELAXED);
    processor.processMessages(
        Map.of(
            "version",
            "1.0",
            "createSurface",
            object(
                "surfaceId",
                "s",
                "catalogId",
                BasicCatalog.ID,
                "components",
                List.of(
                    component("root", "Column", "children", List.of("text")),
                    component("text", "Text", "text", text)))));
    return new A2uiSurface(processor.model().getSurface("s").orElseThrow());
  }

  /** Makes a surface with one column that has a text, in a scroll pane with no window. */
  private void mount(String text, int width, int height) {
    Edt.run(() -> {
      surface = surfaceWith(text);
      scrollPane = new JScrollPane(surface);
      JPanel holder = new JPanel(null);
      holder.add(scrollPane);
      scrollPane.addNotify();
    });
    resize(width, height);
  }

  /**
   * Sets the size of the scroll pane and does its layout. Then it does the layouts that the surface
   * requested, as the repaint manager of a window does.
   */
  private void resize(int width, int height) {
    Edt.run(() -> {
      scrollPane.setSize(width, height);
      scrollPane.validate();
    });
    for (int pass = 0; pass < 4; pass++) {
      Edt.run(scrollPane::validate);
    }
  }

  @Test
  void aLongTextBreaksAtTheWidthOfTheViewportAndScrollsVertically() {
    mount(LONG_TEXT, 300, 200);

    Edt.run(() -> {
      Dimension viewport = scrollPane.getViewport().getExtentSize();
      assertTrue(scrollPane.getVerticalScrollBar().isVisible());
      assertFalse(scrollPane.getHorizontalScrollBar().isVisible());
      assertEquals(viewport.width, surface.getWidth());
      assertTrue(surface.getHeight() > viewport.height, "height " + surface.getHeight());
      assertEquals(surface.preferredHeightFor(viewport.width), surface.getHeight());
      assertTrue(surface.getScrollableTracksViewportWidth());
      assertFalse(surface.getScrollableTracksViewportHeight());
    });
  }

  @Test
  void aWiderViewportMakesTheContentShorterAndRemovesTheScrollBar() {
    mount(LONG_TEXT, 300, 200);
    int narrow = surface.getHeight();

    resize(900, 200);

    Edt.run(() -> {
      Dimension viewport = scrollPane.getViewport().getExtentSize();
      assertEquals(viewport.width, surface.getWidth());
      assertTrue(surface.preferredHeightFor(viewport.width) < narrow);
      assertFalse(scrollPane.getVerticalScrollBar().isVisible());
      assertEquals(surface.preferredHeightFor(viewport.width), surface.getHeight());
    });
  }

  @Test
  void aNarrowerViewportGetsTheScrollBarThatItNeeds() {
    mount(LONG_TEXT, 2000, 200);
    assertFalse(scrollPane.getVerticalScrollBar().isVisible());

    resize(300, 200);

    Edt.run(() -> {
      assertTrue(scrollPane.getVerticalScrollBar().isVisible());
      assertEquals(scrollPane.getViewport().getExtentSize().width, surface.getWidth());
      assertEquals(surface.preferredHeightFor(surface.getWidth()), surface.getHeight());
    });
  }

  @Test
  void aShortSurfaceKeepsTheHeightOfItsContentAndHasNoScrollBar() {
    mount("Hello", 300, 200);

    Edt.run(() -> {
      Dimension viewport = scrollPane.getViewport().getExtentSize();
      assertFalse(scrollPane.getVerticalScrollBar().isVisible());
      assertFalse(scrollPane.getHorizontalScrollBar().isVisible());
      assertEquals(viewport.width, surface.getWidth());
      assertEquals(surface.preferredHeightFor(viewport.width), surface.getHeight());
      assertTrue(surface.getHeight() < viewport.height, "height " + surface.getHeight());
      assertFalse(surface.getScrollableTracksViewportHeight());
    });
  }

  @Test
  void theLayoutOfTheScrollPaneBecomesStable() {
    mount(LONG_TEXT, 300, 200);
    Dimension size = surface.getSize();
    Edt.flush();

    Edt.run(() -> {
      assertTrue(scrollPane.isValid());
      scrollPane.validate();
      assertEquals(size, surface.getSize());
    });
  }

  @Test
  void aSurfaceThatIsNotInAViewportHasThePreferredSizeOfItsContent() {
    Edt.run(() -> {
      A2uiSurface plain = Headless.mount(surfaceWith(LONG_TEXT), 300, 200);

      assertEquals(plain.getPreferredScrollableViewportSize(), plain.getPreferredSize());
      assertTrue(plain.getPreferredSize().width > 300, "width " + plain.getPreferredSize().width);
      assertTrue(plain.preferredHeightFor(300) > plain.getPreferredSize().height);
    });
  }

  @Test
  void theScrollBarsMoveByOneStepAndByOnePage() {
    mount(LONG_TEXT, 300, 200);
    Rectangle visible = new Rectangle(0, 0, 280, 200);

    Edt.run(() -> {
      assertEquals(16, surface.getScrollableUnitIncrement(visible, SwingConstants.VERTICAL, 1));
      assertEquals(184, surface.getScrollableBlockIncrement(visible, SwingConstants.VERTICAL, 1));
      assertEquals(264, surface.getScrollableBlockIncrement(visible, SwingConstants.HORIZONTAL, 1));
    });
  }
}
