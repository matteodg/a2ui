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
import static com.google.a2ui.swing.testing.TestSurface.event;
import static com.google.a2ui.swing.testing.TestSurface.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.Headless;
import com.google.a2ui.swing.testing.Snapshots;
import com.google.a2ui.swing.testing.TestSurface;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Shows a surface of test components with no window and writes it as an image. The image is in
 * {@code target/snapshots/}. A person looks at it to find layout problems.
 */
class SurfaceSnapshotTest {
  private static final int WIDTH = 420;
  private static final int HEIGHT = 220;

  private final TestSurface surface = new TestSurface();

  private Object[] profile() {
    return new Object[] {
      component("root", "Box", "gap", 12, "children", List.of("title", "form", "words", "footer")),
      component(
          "title",
          "Label",
          "text",
          TestSurface.call("formatString", "value", "Profile of ${/name}")),
      component(
          "form",
          "Box",
          "direction",
          "horizontal",
          "align",
          "center",
          "gap",
          8,
          "children",
          List.of("caption", "name", "save")),
      component("caption", "Label", "text", "Name"),
      component("name", "Field", "value", path("/name"), "weight", 1),
      component("save", "Button", "label", "Save", "action", event("save")),
      component(
          "words",
          "Box",
          "direction",
          "horizontal",
          "justify",
          "spaceBetween",
          "children",
          List.of("one", "late", "two", "three")),
      component("one", "Label", "text", "start"),
      component("two", "Label", "text", "center"),
      component("three", "Label", "text", "end"),
      component(
          "footer",
          "Box",
          "direction",
          "horizontal",
          "justify",
          "end",
          "weight",
          1,
          "align",
          "end",
          "children",
          List.of("status")),
      component("status", "Label", "text", "Saved")
    };
  }

  private static Rectangle boundsIn(A2uiSurface view, String componentId) {
    JComponent component =
        view.viewTree().orElseThrow().find(componentId).orElseThrow().component().orElseThrow();
    return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), view);
  }

  @Test
  void aSurfaceOfLabelsAndFlexContainersPaintsWithNoWindow() {
    Edt.run(() -> {
      surface.create(Map.of("name", "Ada Lovelace"), profile());
      A2uiSurface view = Headless.mount(new A2uiSurface(surface.model()), WIDTH, HEIGHT);

      Path file = Snapshots.write(view, "foundations-profile");

      BufferedImage image = ImageIO.read(file.toFile());
      assertTrue(Files.size(file) > 0);
      assertEquals(WIDTH, image.getWidth());
      assertEquals(HEIGHT, image.getHeight());
      Set<Integer> colors = new HashSet<>();
      for (int x = 0; x < WIDTH; x += 2) {
        for (int y = 0; y < HEIGHT; y += 2) {
          colors.add(image.getRGB(x, y));
        }
      }
      assertTrue(colors.size() > 2, "The image must contain painted text and controls.");
    });
  }

  @Test
  void theLayoutOfTheMountedSurfaceFollowsTheFlexProperties() {
    Edt.run(() -> {
      surface.create(Map.of("name", "Ada Lovelace"), profile());
      A2uiSurface view = Headless.mount(new A2uiSurface(surface.model()), WIDTH, HEIGHT);

      Rectangle title = boundsIn(view, "title");
      Rectangle form = boundsIn(view, "form");
      Rectangle caption = boundsIn(view, "caption");
      Rectangle name = boundsIn(view, "name");
      Rectangle save = boundsIn(view, "save");
      Rectangle words = boundsIn(view, "words");
      Rectangle footer = boundsIn(view, "footer");
      Rectangle status = boundsIn(view, "status");

      // The column fills the surface, and each row fills the width.
      assertEquals(new Rectangle(0, 0, WIDTH, title.height), title);
      assertEquals(0, form.x);
      assertEquals(WIDTH, form.width);
      assertEquals(title.height + 12, form.y);
      assertEquals(form.y + form.height + 12, words.y);
      // The field has the weight. Thus it fills the space between the caption and the button.
      assertEquals(0, caption.x);
      assertEquals(caption.x + caption.width + 8, name.x);
      assertEquals(name.x + name.width + 8, save.x);
      assertEquals(WIDTH, save.x + save.width);
      assertTrue(name.width > 200, "the width of the field is " + name.width);
      // The row puts each component in the center of the cross axis.
      assertEquals(form.y + (form.height - caption.height) / 2, caption.y);
      // The child that did not arrive has no slot. Thus the three words are at the two edges and
      // in the center.
      Rectangle one = boundsIn(view, "one");
      Rectangle two = boundsIn(view, "two");
      Rectangle three = boundsIn(view, "three");
      assertEquals(0, one.x);
      assertEquals(WIDTH, three.x + three.width);
      assertEquals(two.x - (one.x + one.width), three.x - (two.x + two.width), 1);
      // The footer has the weight of the column. Thus it fills the height that stays free, and
      // its text is in the corner.
      assertEquals(HEIGHT, footer.y + footer.height);
      assertEquals(words.y + words.height + 12, footer.y);
      assertEquals(WIDTH, status.x + status.width);
      assertEquals(HEIGHT, status.y + status.height);
      assertNotEquals(0, status.width);
      assertEquals(
          "Profile of Ada Lovelace",
          ((JLabel) view.viewTree().orElseThrow().find("title").orElseThrow().component().get())
              .getText());
    });
  }
}
