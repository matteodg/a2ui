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

import static com.google.a2ui.swing.testing.TestSurface.component;
import static com.google.a2ui.swing.testing.TestSurface.event;
import static com.google.a2ui.swing.testing.TestSurface.object;
import static com.google.a2ui.swing.testing.TestSurface.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.internal.ViewMarks;
import com.google.a2ui.swing.surface.A2uiAccessibility;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.Snapshots;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.accessibility.AccessibleRole;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** The surface of the tests is 400 pixels wide and 300 pixels high. */
class IconViewTest {
  private static final int MARGIN = BasicStyle.MARGIN;
  private static final String SQUARE = "M2 2H22V22H2Z";

  private final BasicFixture fixture = new BasicFixture();

  private void show(Map<String, Object> data, Object name, Object... properties) {
    Map<String, Object> icon = component("icon", "Icon", "name", name);
    icon.putAll(object(properties));
    fixture.create(
        data,
        component("root", "Column", "children", List.of("icon", "after")),
        icon,
        component("after", "Text", "text", "After"));
  }

  private IconView.Glyph glyph() {
    return (IconView.Glyph) fixture.viewOf("icon").accessibleComponent();
  }

  /** Gets the bounds of the glyph in the coordinates of the surface component. */
  private Rectangle glyphBounds() {
    return SwingUtilities.convertRectangle(glyph().getParent(), glyph().getBounds(), fixture.view);
  }

  /** Counts the pixels of the glyph area that are not the background. */
  private int paintedPixels() {
    BufferedImage image = Snapshots.render(fixture.view);
    Rectangle area = glyphBounds();
    int background = image.getRGB(area.x + area.width + 40, area.y + area.height / 2);
    int painted = 0;
    for (int y = area.y; y < area.y + area.height; y++) {
      for (int x = area.x; x < area.x + area.width; x++) {
        if (image.getRGB(x, y) != background) {
          painted++;
        }
      }
    }
    return painted;
  }

  private Color colorAt(int x, int y) {
    Rectangle area = glyphBounds();
    return new Color(Snapshots.render(fixture.view).getRGB(area.x + x, area.y + y));
  }

  @Test
  void aNameOfTheCatalogShowsItsShapeInASquareWithTheMarginOfALeaf() {
    Edt.run(() -> {
      show(Map.of(), "stop");

      assertTrue(glyph().hasShape());
      assertEquals(new Rectangle(MARGIN, MARGIN, IconView.SIZE, IconView.SIZE), glyphBounds());
      assertEquals(new Rectangle(0, 0, 400, IconView.SIZE + 2 * MARGIN), fixture.bounds("icon"));
      assertEquals(fixture.bounds("icon").height, fixture.bounds("after").y);
      // The shape of the name stop is a square from 6 to 18 in the view box.
      assertEquals(BasicStyle.foregroundOf(glyph()), colorAt(12, 12));
      assertEquals(BasicStyle.foregroundOf(glyph()), colorAt(7, 7));
      assertNotEquals(BasicStyle.foregroundOf(glyph()), colorAt(3, 3));
      assertNotEquals(BasicStyle.foregroundOf(glyph()), colorAt(20, 12));
      assertEquals("stop", ViewMarks.iconName(glyph()).orElse(null));
    });
  }

  @Test
  void eachNameOfTheCatalogPaintsAShape() {
    Edt.run(() -> {
      show(Map.of("name", "add"), path("/name"));

      for (String name : IconShapesTest.catalogNames()) {
        fixture.setData("/name", name);

        assertTrue(glyph().hasShape(), name);
        assertTrue(paintedPixels() > 30, name + " painted " + paintedPixels() + " pixels");
        assertEquals(name, ViewMarks.iconName(glyph()).orElse(null));
      }
    });
  }

  @Test
  void aNameFromTheDataModelFollowsTheData() {
    Edt.run(() -> {
      show(Map.of("name", "play"), path("/name"));
      JComponent before = fixture.componentOf("icon");
      int play = paintedPixels();

      fixture.setData("/name", "pause");

      assertSame(before, fixture.componentOf("icon"));
      assertEquals("pause", ViewMarks.iconName(glyph()).orElse(null));
      assertNotEquals(play, paintedPixels());
    });
  }

  @Test
  void aNameThatTheCatalogDoesNotHaveShowsTheShapeOfHelp() {
    Edt.run(() -> {
      show(Map.of("name", "help"), path("/name"));
      BufferedImage help = glyphImage();
      assertEquals("help", glyph().getAccessibleContext().getAccessibleName());

      for (String unknown : new String[] {"noSuchIcon", "Help", "arrow_back", ""}) {
        fixture.setData("/name", "check");
        assertFalse(sameImage(help, glyphImage()));

        fixture.setData("/name", unknown);

        assertTrue(glyph().hasShape(), unknown);
        assertTrue(sameImage(help, glyphImage()), unknown);
        assertEquals(new Rectangle(MARGIN, MARGIN, IconView.SIZE, IconView.SIZE), glyphBounds());
        // The property has the name that the agent gave. Assistive tools do not get a name.
        assertEquals(unknown, ViewMarks.iconName(glyph()).orElse(null));
        assertNull(glyph().getAccessibleContext().getAccessibleName(), unknown);
      }
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void aValueThatIsNotATextAndNotPathDataShowsNothingAndKeepsItsSpace() {
    Edt.run(() -> {
      show(Map.of("name", "check"), path("/name"));
      assertTrue(glyph().hasShape());

      for (Object other : new Object[] {5L, true, List.of("check"), Map.of("x", "y")}) {
        fixture.setData("/name", other);
        assertFalse(glyph().hasShape(), String.valueOf(other));
        assertEquals(0, paintedPixels(), String.valueOf(other));
        assertNull(ViewMarks.iconName(glyph()).orElse(null));
      }
      assertEquals(new Rectangle(MARGIN, MARGIN, IconView.SIZE, IconView.SIZE), glyphBounds());
      assertEquals(List.of(), fixture.errors);
    });
  }

  /** Gets the image of the area of the glyph. */
  private BufferedImage glyphImage() {
    Rectangle area = glyphBounds();
    return Snapshots.render(fixture.view).getSubimage(area.x, area.y, area.width, area.height);
  }

  private static boolean sameImage(BufferedImage first, BufferedImage second) {
    if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) {
      return false;
    }
    for (int y = 0; y < first.getHeight(); y++) {
      for (int x = 0; x < first.getWidth(); x++) {
        if (first.getRGB(x, y) != second.getRGB(x, y)) {
          return false;
        }
      }
    }
    return true;
  }

  @Test
  void pathDataOfTheAgentShowsInTheViewBoxOfTheIcon() {
    Edt.run(() -> {
      show(Map.of(), Map.of("svgPath", SQUARE));

      assertTrue(glyph().hasShape());
      assertEquals(BasicStyle.foregroundOf(glyph()), colorAt(12, 12));
      assertEquals(BasicStyle.foregroundOf(glyph()), colorAt(3, 3));
      assertNotEquals(BasicStyle.foregroundOf(glyph()), colorAt(0, 0));
      assertEquals(20 * 20, paintedPixels(), 44);
      assertNull(ViewMarks.iconName(glyph()).orElse(null));
    });
  }

  @Test
  void pathDataFromTheDataModelFollowsTheData() {
    Edt.run(() -> {
      show(Map.of("shape", SQUARE), Map.of("svgPath", path("/shape")));
      assertEquals(20 * 20, paintedPixels(), 44);

      fixture.setData("/shape", "M2 2H12V12H2Z");

      assertEquals(10 * 10, paintedPixels(), 24);
    });
  }

  @Test
  void pathDataThatIsNotCorrectShowsNothingAndIsNotAnError() {
    Edt.run(() -> {
      show(Map.of("shape", "M2 2 L nothing"), Map.of("svgPath", path("/shape")));

      assertFalse(glyph().hasShape());
      assertEquals(0, paintedPixels());

      fixture.setData("/shape", "M0 0" + "l1 0".repeat(SvgPath.MAX_LENGTH));
      assertFalse(glyph().hasShape());

      fixture.setData("/shape", "");
      assertFalse(glyph().hasShape());
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void pathDataThatIsLargerThanTheViewBoxPaintsOnlyInTheIcon() {
    Edt.run(() -> {
      show(Map.of(), Map.of("svgPath", "M-500 -500H500V500H-500Z"));

      Rectangle area = glyphBounds();
      BufferedImage image = Snapshots.render(fixture.view);
      Color color = BasicStyle.foregroundOf(glyph());

      assertEquals(color, new Color(image.getRGB(area.x, area.y)));
      assertEquals(
          color, new Color(image.getRGB(area.x + area.width - 1, area.y + area.height - 1)));
      assertNotEquals(color, new Color(image.getRGB(area.x - 1, area.y + 5)));
      assertNotEquals(color, new Color(image.getRGB(area.x + area.width, area.y + 5)));
      assertNotEquals(color, new Color(image.getRGB(area.x + 5, area.y + area.height)));
      assertNotEquals(color, new Color(image.getRGB(area.x + 5, area.y - 1)));
    });
  }

  @Test
  void theIconHasTheTextColorOfTheComponentAboveIt() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Column", "children", List.of("main", "link", "plain")),
          component(
              "main", "Button", "child", "main_icon", "action", event("a"), "variant", "primary"),
          component("main_icon", "Icon", "name", "stop"),
          component(
              "link",
              "Button",
              "child",
              "link_icon",
              "action",
              event("b"),
              "variant",
              "borderless"),
          component("link_icon", "Icon", "name", "stop"),
          component("plain", "Icon", "name", "stop"));

      for (String[] row :
          new String[][] {{"main_icon", "main"}, {"link_icon", "link"}, {"plain", "root"}}) {
        JComponent icon = fixture.viewOf(row[0]).accessibleComponent();
        Rectangle area =
            SwingUtilities.convertRectangle(icon.getParent(), icon.getBounds(), fixture.view);
        Color painted =
            new Color(
                Snapshots.render(fixture.view)
                    .getRGB(area.x + area.width / 2, area.y + area.height / 2));
        assertEquals(BasicStyle.foregroundOf(icon), painted, row[0]);
      }
      JComponent main = fixture.viewOf("main_icon").accessibleComponent();
      JComponent link = fixture.viewOf("link_icon").accessibleComponent();
      assertEquals(BasicStyle.onAccent(), BasicStyle.foregroundOf(main));
      assertEquals(BasicStyle.link(link), BasicStyle.foregroundOf(link));
      // The icon in a button has no margin of its own.
      assertEquals(IconView.SIZE, fixture.componentOf("main_icon").getWidth());
    });
  }

  @Test
  void theIconIsLargerWithALargeFont() {
    Edt.run(() -> {
      show(Map.of(), "stop");
      fixture.view.setFont(new Font(Font.DIALOG, Font.PLAIN, 20));
      glyph().revalidate();
      fixture.view.validate();

      assertEquals(new Rectangle(MARGIN, MARGIN, 30, 30), glyphBounds());
      assertEquals(BasicStyle.foregroundOf(glyph()), colorAt(15, 15));
      assertEquals(BasicStyle.foregroundOf(glyph()), colorAt(9, 9));
      assertNotEquals(BasicStyle.foregroundOf(glyph()), colorAt(5, 5));
    });
  }

  @Test
  void theIconKeepsItsSizeInARowAndInAStretchedPosition() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Row", "children", List.of("icon", "text")),
          component("icon", "Icon", "name", "info", "weight", 1),
          component("text", "Text", "text", "# A heading\n\nA paragraph\n\nA second paragraph"));

      JComponent icon = fixture.viewOf("icon").accessibleComponent();
      assertEquals(IconView.SIZE, icon.getWidth());
      assertEquals(IconView.SIZE, icon.getHeight());
      assertEquals(MARGIN, icon.getX());
      assertEquals(MARGIN, icon.getY());
      // The panel of the view fills the row. The icon does not become larger.
      assertTrue(fixture.bounds("icon").height > IconView.SIZE + 2 * MARGIN);
      assertTrue(fixture.bounds("icon").width > IconView.SIZE + 2 * MARGIN);
      assertTrue(fixture.componentOf("icon") instanceof HeightForWidth);
      assertEquals(
          IconView.SIZE + 2 * MARGIN,
          ((HeightForWidth) fixture.componentOf("icon")).preferredHeightFor(30));
    });
  }

  @Test
  void theAccessibleNameIsTheNameOfTheIconAsWords() {
    Edt.run(() -> {
      show(Map.of("name", "arrowBack"), path("/name"));

      assertEquals(AccessibleRole.ICON, glyph().getAccessibleContext().getAccessibleRole());
      assertEquals("arrow back", glyph().getAccessibleContext().getAccessibleName());

      fixture.setData("/name", "visibilityOff");
      assertEquals("visibility off", glyph().getAccessibleContext().getAccessibleName());

      fixture.setData("/name", "noSuchIcon");
      assertNull(glyph().getAccessibleContext().getAccessibleName());

      fixture.setData("/name", Map.of("svgPath", SQUARE));
      assertNull(glyph().getAccessibleContext().getAccessibleName());
    });
  }

  @Test
  void anExplicitLabelIsBeforeTheNameOfTheIconAndAHiddenIconHasNoName() {
    Edt.run(() -> {
      show(
          Map.of("hidden", false),
          "delete",
          "@accessibility",
          Map.of("label", "Remove the item", "hidden", path("/hidden")));

      assertEquals("Remove the item", glyph().getAccessibleContext().getAccessibleName());

      fixture.setData("/hidden", true);

      assertEquals("", glyph().getAccessibleContext().getAccessibleName());
      assertTrue(A2uiAccessibility.isHidden(glyph()));
    });
  }

  @Test
  void aButtonWithOnlyIconsTakesItsNameFromTheIcons() {
    Edt.run(() -> {
      // A literal name that the catalog does not have is not correct for the validator. Thus the
      // name comes from the data model.
      fixture.create(
          Map.of("other", "noSuchIcon"),
          component("root", "Column", "children", List.of("icons", "mixed", "shape", "other")),
          component("icons", "Button", "child", "icons_row", "action", event("a")),
          component("icons_row", "Row", "children", List.of("first", "second")),
          component("first", "Icon", "name", "skipNext"),
          component("second", "Icon", "name", "volumeUp"),
          component("mixed", "Button", "child", "mixed_row", "action", event("b")),
          component("mixed_row", "Row", "children", List.of("mixed_icon", "mixed_text")),
          component("mixed_icon", "Icon", "name", "send"),
          component("mixed_text", "Text", "text", "Send the form"),
          component("shape", "Button", "child", "shape_icon", "action", event("c")),
          component("shape_icon", "Icon", "name", Map.of("svgPath", SQUARE)),
          component("other", "Button", "child", "other_icon", "action", event("d")),
          component("other_icon", "Icon", "name", path("/other")));

      assertEquals("skip next volume up", name("icons"));
      // Visible text is before the names of the icons.
      assertEquals("Send the form", name("mixed"));
      assertTrue(name("shape") == null || name("shape").isEmpty(), name("shape"));
      // The shape of help for a name that the catalog does not have gives no name.
      assertTrue(name("other") == null || name("other").isEmpty(), name("other"));
    });
  }

  private String name(String buttonId) {
    JButton button = (JButton) fixture.viewOf(buttonId).accessibleComponent();
    return button.getAccessibleContext().getAccessibleName();
  }

  @Test
  void theWordsOfANameAreInLowerCase() {
    assertEquals("add", IconView.words("add"));
    assertEquals("account circle", IconView.words("accountCircle"));
    assertEquals("more vert", IconView.words("moreVert"));
  }

  @Test
  void aChangeOfTheLookAndFeelKeepsTheIcon() {
    Edt.run(() -> {
      show(Map.of(), "home");
      int before = paintedPixels();

      SwingUtilities.updateComponentTreeUI(fixture.view);
      fixture.view.validate();

      assertTrue(glyph().hasShape());
      assertEquals(before, paintedPixels());
      assertEquals(new Rectangle(MARGIN, MARGIN, IconView.SIZE, IconView.SIZE), glyphBounds());
    });
  }
}
