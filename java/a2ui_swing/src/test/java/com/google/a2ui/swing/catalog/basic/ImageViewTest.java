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
import static com.google.a2ui.swing.testing.TestSurface.object;
import static com.google.a2ui.swing.testing.TestSurface.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.catalog.basic.ImageView.Variant;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.Snapshots;
import com.google.a2ui.swing.testing.TestImageLoader;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.accessibility.AccessibleRole;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** The surface of the tests is 400 pixels wide and 300 pixels high. */
class ImageViewTest {
  private static final int MARGIN = BasicStyle.MARGIN;
  private static final String A = "https://example.com/a.png";
  private static final String B = "https://example.com/b.png";
  private static final Color RED = new Color(200, 0, 0);

  private final TestImageLoader loader = new TestImageLoader();
  private final BasicFixture fixture =
      new BasicFixture(A2uiServices.DEFAULT.withImageLoader(loader));

  private void show(Map<String, Object> data, Object... properties) {
    fixture.create(
        data,
        component("root", "Column", "children", List.of("image", "after")),
        component("image", "Image", properties),
        component("after", "Text", "text", "After"));
  }

  private void update(Object... properties) {
    fixture.update(component("image", "Image", properties));
  }

  private ImageView.Canvas canvas() {
    return (ImageView.Canvas) fixture.viewOf("image").accessibleComponent();
  }

  /** Completes the request for a URL, and does the layout again. */
  private void complete(String url, BufferedImage image) {
    loader.complete(url, image);
    fixture.view.validate();
  }

  private static BufferedImage red(int width, int height) {
    return TestImageLoader.solid(width, height, RED);
  }

  /** Gets the color of a pixel of the box of the image. */
  private Color colorAt(int x, int y) {
    Rectangle area =
        SwingUtilities.convertRectangle(canvas().getParent(), canvas().getBounds(), fixture.view);
    return new Color(Snapshots.render(fixture.view).getRGB(area.x + x, area.y + y));
  }

  private Color background() {
    return new Color(Snapshots.render(fixture.view).getRGB(399, 299));
  }

  @Test
  void theViewAsksTheLoaderAndShowsAnAreaUntilTheImageArrives() {
    Edt.run(() -> {
      show(Map.of(), "url", A);

      assertEquals(List.of(URI.create(A)), loader.requests);
      assertEquals(ImageSource.State.LOADING, canvas().state());
      // The area of the default variant is 300 pixels wide, with the proportions 3 to 2.
      assertEquals(new Dimension(300, 200), canvas().box());
      assertEquals(new Rectangle(0, 0, 400, 200 + 2 * MARGIN), fixture.bounds("image"));
      assertEquals(fixture.bounds("image").height, fixture.bounds("after").y);
      assertNotEquals(background(), colorAt(150, 100));
      assertEquals(background(), colorAt(310, 100));

      complete(A, red(600, 300));

      assertEquals(ImageSource.State.LOADED, canvas().state());
      // The box now has the proportions of the image.
      assertEquals(new Dimension(300, 150), canvas().box());
      assertEquals(new Rectangle(0, 0, 400, 150 + 2 * MARGIN), fixture.bounds("image"));
      assertEquals(fixture.bounds("image").height, fixture.bounds("after").y);
      assertEquals(RED, colorAt(1, 1));
      assertEquals(RED, colorAt(298, 148));
      assertEquals(background(), colorAt(302, 75));
      assertEquals(URI.create(A), canvas().getClientProperty(SwingBasicCatalog.MEDIA_URL_PROPERTY));
    });
  }

  @Test
  void aFailedLoadKeepsTheAreaAndShowsAMark() {
    Edt.run(() -> {
      show(Map.of(), "url", A);
      Color area = colorAt(150, 100);

      loader.fail(A);
      fixture.view.validate();

      assertEquals(ImageSource.State.FAILED, canvas().state());
      assertEquals(new Dimension(300, 200), canvas().box());
      assertEquals(area, colorAt(20, 20));
      // The mark is in the center of the area.
      boolean mark = false;
      for (int x = 140; x < 160 && !mark; x++) {
        for (int y = 90; y < 110 && !mark; y++) {
          mark = !colorAt(x, y).equals(area);
        }
      }
      assertTrue(mark, "the area shows a mark");
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void aUrlFromTheDataModelFollowsTheData() {
    Edt.run(() -> {
      show(Map.of("url", A), "url", path("/url"));
      ImageView.Canvas before = canvas();
      complete(A, red(100, 100));

      fixture.setData("/url", B);

      assertSame(before, canvas());
      assertEquals(List.of(URI.create(A), URI.create(B)), loader.requests);
      assertEquals(ImageSource.State.LOADING, canvas().state());
      assertEquals(URI.create(B), canvas().getClientProperty(SwingBasicCatalog.MEDIA_URL_PROPERTY));

      complete(B, TestImageLoader.solid(50, 100, Color.BLUE));

      assertEquals(new Dimension(50, 100), canvas().box());
      assertEquals(Color.BLUE, colorAt(25, 50));
    });
  }

  @Test
  void aNewUrlCancelsTheRequestAndALateImageDoesNotShow() {
    Edt.run(() -> {
      show(Map.of("url", A), "url", path("/url"));

      fixture.setData("/url", B);

      assertEquals(List.of(URI.create(A)), loader.cancelled);

      loader.completeCancelled(A, red(100, 100));
      fixture.view.validate();
      assertEquals(ImageSource.State.LOADING, canvas().state());
      assertEquals(new Dimension(300, 200), canvas().box());

      complete(B, TestImageLoader.solid(80, 40, Color.BLUE));
      assertEquals(new Dimension(80, 40), canvas().box());
      assertEquals(Color.BLUE, colorAt(40, 20));
    });
  }

  @Test
  void anUpdateWithTheSameUrlStartsNoNewRequest() {
    Edt.run(() -> {
      show(Map.of(), "url", A, "description", "First");
      complete(A, red(100, 100));

      update("url", A, "description", "Second", "fit", "cover");

      assertEquals(1, loader.requests.size());
      assertEquals(ImageSource.State.LOADED, canvas().state());
      assertEquals("Second", canvas().getToolTipText());
    });
  }

  @Test
  void aUrlThatIsNotSafeShowsTheAreaAndIsNotAnError() {
    Edt.run(() -> {
      show(Map.of("url", "file:///C:/Windows/win.ini"), "url", path("/url"));

      for (Object value :
          new Object[] {
            "file:///C:/Windows/win.ini",
            "javascript:alert(1)",
            "//example.com/a.png",
            "https:example.com/a.png",
            "",
            5L
          }) {
        fixture.setData("/url", value);

        assertEquals(ImageSource.State.NONE, canvas().state(), String.valueOf(value));
        assertNull(canvas().getClientProperty(SwingBasicCatalog.MEDIA_URL_PROPERTY));
        assertEquals(new Dimension(300, 200), canvas().box());
      }
      assertEquals(List.of(), loader.requests);
      assertEquals(List.of(), fixture.errors);

      fixture.setData("/url", " " + A + " ");
      assertEquals(List.of(URI.create(A)), loader.requests);
    });
  }

  @Test
  void aDataUrlAndARelativePathGoToTheLoader() {
    Edt.run(() -> {
      String data = TestImageLoader.dataUrl(red(4, 4));
      show(Map.of("url", data), "url", path("/url"));

      fixture.setData("/url", "images/logo.png");

      assertEquals(List.of(URI.create(data), URI.create("images/logo.png")), loader.requests);
    });
  }

  @Test
  void theDefaultLoaderShowsADataUrl() throws Exception {
    BasicFixture real = new BasicFixture();
    String data = TestImageLoader.dataUrl(red(60, 30));
    ImageView.Canvas[] shown = {null};
    Edt.run(() -> {
      real.create(
          Map.of(),
          component("root", "Column", "children", List.of("image")),
          component("image", "Image", "url", data));
      shown[0] = (ImageView.Canvas) real.viewOf("image").accessibleComponent();
    });

    // The default loader reads the image on a different thread.
    for (int i = 0; i < 200 && shown[0].state() == ImageSource.State.LOADING; i++) {
      Thread.sleep(25);
      Edt.flush();
    }

    Edt.run(() -> {
      real.view.validate();
      assertEquals(ImageSource.State.LOADED, shown[0].state());
      assertEquals(new Dimension(60, 30), shown[0].box());
    });
  }

  @Test
  void theDisposalCancelsTheRequestAndALateImageHasNoEffect() {
    Edt.run(() -> {
      show(Map.of(), "url", A, "description", "A photo");
      ImageView.Canvas canvas = canvas();

      fixture.update(component("root", "Column", "children", List.of("after")));

      assertEquals(List.of(URI.create(A)), loader.cancelled);
      assertNull(canvas.getToolTipText());

      loader.completeCancelled(A, red(100, 100));
      assertEquals(ImageSource.State.NONE, canvas.state());
    });
  }

  @Test
  void theUnmountOfTheSurfaceCancelsTheRequest() {
    Edt.run(() -> {
      show(Map.of(), "url", A);

      fixture.view.removeNotify();

      assertEquals(List.of(URI.create(A)), loader.cancelled);
      assertEquals(0, loader.pending());
    });
  }

  @Test
  void theDescriptionIsTheToolTipAndTheAccessibleName() {
    Edt.run(() -> {
      show(Map.of("text", "A red square"), "url", A, "description", path("/text"));

      assertEquals(AccessibleRole.ICON, canvas().getAccessibleContext().getAccessibleRole());
      assertEquals("A red square", canvas().getAccessibleContext().getAccessibleName());
      assertEquals("A red square", canvas().getToolTipText());

      fixture.setData("/text", "A logo");
      assertEquals("A logo", canvas().getAccessibleContext().getAccessibleName());
      assertEquals("A logo", canvas().getToolTipText());

      fixture.setData("/text", "");
      assertNull(canvas().getAccessibleContext().getAccessibleName());
      assertNull(canvas().getToolTipText());

      update("url", A);
      assertNull(canvas().getToolTipText());
    });
  }

  @Test
  void anExplicitLabelIsBeforeTheDescription() {
    Edt.run(() -> {
      show(
          Map.of(),
          "url",
          A,
          "description",
          "A red square",
          "@accessibility",
          Map.of("label", "The logo of the shop"));

      assertEquals("The logo of the shop", canvas().getAccessibleContext().getAccessibleName());
      assertEquals("A red square", canvas().getToolTipText());
    });
  }

  @Test
  void theToolTipShowsOnlyOnTheBoxOfTheImage() {
    Edt.run(() -> {
      show(Map.of(), "url", A, "variant", "avatar", "description", "A person");

      assertEquals(400 - 2 * MARGIN, canvas().getWidth());
      assertTrue(canvas().contains(20, 20));
      assertFalse(canvas().contains(60, 20));
      assertFalse(canvas().contains(20, 41));
    });
  }

  @Test
  void theIconAndTheAvatarHaveAFixedSize() {
    Edt.run(() -> {
      show(Map.of(), "url", A, "variant", "icon");
      assertEquals(new Dimension(24, 24), canvas().box());
      assertEquals(24 + 2 * MARGIN, fixture.bounds("image").height);

      complete(A, red(600, 300));
      assertEquals(new Dimension(24, 24), canvas().box());
      assertEquals(RED, colorAt(1, 1));
      assertEquals(RED, colorAt(22, 22));
      assertEquals(background(), colorAt(26, 12));

      update("url", A, "variant", "avatar");
      assertEquals(new Dimension(40, 40), canvas().box());
      assertEquals(40 + 2 * MARGIN, fixture.bounds("image").height);
      // The avatar is a circle.
      assertEquals(RED, colorAt(20, 20));
      assertEquals(RED, colorAt(20, 2));
      assertEquals(background(), colorAt(1, 1));
      assertEquals(background(), colorAt(38, 38));
      assertEquals(1, loader.requests.size());
    });
  }

  @Test
  void theBoxOfEachVariantWithNoImage() {
    assertEquals(new Dimension(24, 24), ImageView.box(Variant.ICON, 500, null));
    assertEquals(new Dimension(40, 40), ImageView.box(Variant.AVATAR, 10, null));
    assertEquals(new Dimension(100, 100), ImageView.box(Variant.SMALL_FEATURE, 500, null));
    assertEquals(new Dimension(60, 60), ImageView.box(Variant.SMALL_FEATURE, 60, null));
    assertEquals(new Dimension(300, 200), ImageView.box(Variant.MEDIUM_FEATURE, 500, null));
    assertEquals(new Dimension(150, 100), ImageView.box(Variant.MEDIUM_FEATURE, 150, null));
    assertEquals(new Dimension(640, 360), ImageView.box(Variant.LARGE_FEATURE, 640, null));
    assertEquals(new Dimension(1000, 400), ImageView.box(Variant.LARGE_FEATURE, 1000, null));
    assertEquals(new Dimension(500, 200), ImageView.box(Variant.HEADER, 500, null));
    assertEquals(new Dimension(1, 200), ImageView.box(Variant.HEADER, 0, null));
  }

  @Test
  void theBoxOfEachVariantHasTheProportionsOfTheImage() {
    Picture wide = Picture.of(red(800, 400)).orElseThrow();
    Picture tall = Picture.of(red(200, 1000)).orElseThrow();
    Picture small = Picture.of(red(40, 20)).orElseThrow();

    assertEquals(new Dimension(100, 50), ImageView.box(Variant.SMALL_FEATURE, 500, wide));
    assertEquals(new Dimension(20, 100), ImageView.box(Variant.SMALL_FEATURE, 500, tall));
    assertEquals(new Dimension(60, 30), ImageView.box(Variant.SMALL_FEATURE, 60, wide));
    assertEquals(new Dimension(300, 150), ImageView.box(Variant.MEDIUM_FEATURE, 500, wide));
    assertEquals(new Dimension(200, 100), ImageView.box(Variant.MEDIUM_FEATURE, 200, wide));
    assertEquals(new Dimension(80, 400), ImageView.box(Variant.MEDIUM_FEATURE, 500, tall));
    assertEquals(new Dimension(500, 250), ImageView.box(Variant.LARGE_FEATURE, 500, wide));
    assertEquals(new Dimension(800, 400), ImageView.box(Variant.LARGE_FEATURE, 2000, wide));
    assertEquals(new Dimension(80, 400), ImageView.box(Variant.LARGE_FEATURE, 2000, tall));
    // A box is never larger than its image.
    for (Variant variant :
        List.of(Variant.SMALL_FEATURE, Variant.MEDIUM_FEATURE, Variant.LARGE_FEATURE)) {
      assertEquals(new Dimension(40, 20), ImageView.box(variant, 500, small), variant.name());
    }
    // The fixed boxes do not follow the image.
    assertEquals(new Dimension(24, 24), ImageView.box(Variant.ICON, 500, wide));
    assertEquals(new Dimension(40, 40), ImageView.box(Variant.AVATAR, 500, wide));
    assertEquals(new Dimension(500, 200), ImageView.box(Variant.HEADER, 500, tall));
  }

  @Test
  void anUnknownVariantIsTheDefault() {
    assertEquals(Variant.MEDIUM_FEATURE, Variant.fromWire(null));
    assertEquals(Variant.MEDIUM_FEATURE, Variant.fromWire("poster"));
    assertEquals(Variant.MEDIUM_FEATURE, Variant.fromWire("mediumFeature"));
    assertEquals(Variant.ICON, Variant.fromWire("icon"));
    assertEquals(Variant.AVATAR, Variant.fromWire("avatar"));
    assertEquals(Variant.SMALL_FEATURE, Variant.fromWire("smallFeature"));
    assertEquals(Variant.LARGE_FEATURE, Variant.fromWire("largeFeature"));
    assertEquals(Variant.HEADER, Variant.fromWire("header"));
  }

  @Test
  void theHeaderHasTheWidthOfItsContainerAndCoversItsBox() {
    Edt.run(() -> {
      show(Map.of(), "url", A, "variant", "header");
      int width = 400 - 2 * MARGIN;

      assertEquals(new Dimension(width, 200), canvas().box());

      // The image is a square with a blue band at its top. The header shows the center.
      BufferedImage image = red(400, 400);
      for (int y = 0; y < 50; y++) {
        for (int x = 0; x < 400; x++) {
          image.setRGB(x, y, Color.BLUE.getRGB());
        }
      }
      complete(A, image);

      assertEquals(new Dimension(width, 200), canvas().box());
      assertEquals(RED, colorAt(5, 5));
      assertEquals(RED, colorAt(width - 5, 195));

      update("url", A, "variant", "header", "fit", "fill");
      assertEquals(Color.BLUE, colorAt(5, 5));
      assertEquals(RED, colorAt(width - 5, 195));

      update("url", A, "variant", "header", "fit", "contain");
      // The image is a square of 200 pixels in the center of the box.
      assertEquals(background(), colorAt(5, 5));
      assertEquals(Color.BLUE, colorAt(width / 2, 5));
      assertEquals(RED, colorAt(width / 2, 195));
    });
  }

  @Test
  void aLargeImageHasTheWidthOfItsContainerAndALimitForItsHeight() {
    Edt.run(() -> {
      show(Map.of(), "url", A, "variant", "largeFeature");
      int width = 400 - 2 * MARGIN;

      assertEquals(new Dimension(width, 216), canvas().box());

      complete(A, red(1600, 800));
      assertEquals(new Dimension(width, width / 2), canvas().box());
      assertEquals(width / 2 + 2 * MARGIN, fixture.bounds("image").height);

      fixture.resize(700, 600);
      assertEquals(new Dimension(684, 342), canvas().box());

      fixture.resize(1200, 600);
      assertEquals(new Dimension(800, 400), canvas().box());
    });
  }

  @Test
  void theImageBecomesNarrowWithItsContainerAndKeepsItsProportions() {
    Edt.run(() -> {
      show(Map.of(), "url", A);
      complete(A, red(600, 300));

      fixture.resize(216, 300);

      assertEquals(new Dimension(200, 100), canvas().box());
      assertEquals(100 + 2 * MARGIN, fixture.bounds("image").height);
      assertEquals(fixture.bounds("image").height, fixture.bounds("after").y);
    });
  }

  @Test
  void theImageInARowKeepsItsSizeAndTheTextGetsTheOtherSpace() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Row", "children", List.of("image", "text")),
          component("image", "Image", "url", A, "variant", "smallFeature"),
          object(
              "id", "text", "component", "Text", "text", "A long text. ".repeat(12), "weight", 1));
      complete(A, red(200, 200));

      assertEquals(100 + 2 * MARGIN, fixture.bounds("image").width);
      assertEquals(100 + 2 * MARGIN, fixture.bounds("text").x);
      assertEquals(400, fixture.bounds("text").x + fixture.bounds("text").width);
      assertTrue(fixture.bounds("image").height >= 100 + 2 * MARGIN);
    });
  }

  @Test
  void aChangeOfTheLookAndFeelKeepsTheImage() {
    Edt.run(() -> {
      show(Map.of(), "url", A, "description", "A photo");
      complete(A, red(100, 100));

      SwingUtilities.updateComponentTreeUI(fixture.view);
      fixture.view.validate();

      assertEquals(ImageSource.State.LOADED, canvas().state());
      assertEquals(RED, colorAt(50, 50));
      assertEquals(1, loader.requests.size());
    });
  }
}
