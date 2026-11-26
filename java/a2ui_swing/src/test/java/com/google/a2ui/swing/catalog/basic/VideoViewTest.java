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
import static com.google.a2ui.swing.testing.TestSurface.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.internal.ViewMarks;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.MediaPlayer;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.LogCapture;
import com.google.a2ui.swing.testing.Snapshots;
import com.google.a2ui.swing.testing.TestImageLoader;
import com.google.a2ui.swing.testing.TestMediaPlayer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** The surface of the tests is 400 pixels wide and 300 pixels high. */
class VideoViewTest {
  private static final int MARGIN = BasicStyle.MARGIN;
  private static final String VIDEO = "https://example.com/intro.mp4";
  private static final String OTHER = "https://example.com/other.mp4";
  private static final String POSTER = "https://example.com/poster.png";
  private static final Color RED = new Color(200, 0, 0);

  private final TestImageLoader loader = new TestImageLoader();
  private final TestMediaPlayer player = new TestMediaPlayer();
  private final List<URI> opened = new ArrayList<>();
  private final A2uiServices services =
      A2uiServices.DEFAULT.withImageLoader(loader).withUrlOpener(opened::add);
  private BasicFixture fixture = new BasicFixture(services);

  private void show(Map<String, Object> data, Object... properties) {
    fixture.create(
        data,
        component("root", "Column", "children", List.of("video", "after")),
        component("video", "Video", properties),
        component("after", "Text", "text", "After"));
  }

  private void showWithPlayer(Map<String, Object> data, Object... properties) {
    fixture = new BasicFixture(services.withMediaPlayer(player));
    show(data, properties);
  }

  private VideoView.Poster poster() {
    return (VideoView.Poster) fixture.viewOf("video").accessibleComponent();
  }

  private Color colorAt(int x, int y) {
    JComponent control = fixture.viewOf("video").accessibleComponent();
    Rectangle area =
        SwingUtilities.convertRectangle(control.getParent(), control.getBounds(), fixture.view);
    return new Color(Snapshots.render(fixture.view).getRGB(area.x + x, area.y + y));
  }

  @Test
  void theSubstituteIsAnAreaOf16To9WithAPlayMark() {
    Edt.run(() -> {
      show(Map.of(), "url", VIDEO);
      int width = 400 - 2 * MARGIN;

      assertEquals(new Dimension(width, 216), poster().box());
      assertEquals(new Rectangle(0, 0, 400, 216 + 2 * MARGIN), fixture.bounds("video"));
      assertEquals(fixture.bounds("video").height, fixture.bounds("after").y);
      assertTrue(poster().isEnabled());
      // The play mark is white on a dark circle, in the center of the area.
      assertEquals(Color.WHITE, colorAt(width / 2, 108));
      assertNotEquals(colorAt(10, 10), colorAt(width / 2 - 15, 108));
      assertEquals(List.of(), loader.requests);
      assertEquals(List.of(), opened);
    });
  }

  @Test
  void theSubstituteHasALargestWidth() {
    Edt.run(() -> {
      show(Map.of(), "url", VIDEO);

      fixture.resize(900, 500);

      assertEquals(new Dimension(VideoView.MAX_WIDTH, 270), poster().box());
      assertEquals(270 + 2 * MARGIN, fixture.bounds("video").height);
      assertTrue(poster().contains(470, 260));
      assertFalse(poster().contains(490, 100));

      fixture.resize(176, 500);
      assertEquals(new Dimension(160, 90), poster().box());
    });
  }

  @Test
  void theButtonOpensTheUrlOnlyWhenTheUserOperatesIt() {
    Edt.run(() -> {
      show(Map.of("url", VIDEO), "url", path("/url"));
      assertEquals(List.of(), opened);

      poster().doClick(0);
      assertEquals(List.of(URI.create(VIDEO)), opened);

      fixture.setData("/url", OTHER);
      assertEquals(1, opened.size());

      poster().doClick(0);
      assertEquals(List.of(URI.create(VIDEO), URI.create(OTHER)), opened);
      assertEquals(URI.create(OTHER), ViewMarks.mediaUrl(poster()).orElse(null));
    });
  }

  @Test
  void aUrlThatAnExternalApplicationCannotGetDisablesTheButton() {
    Edt.run(() -> {
      show(Map.of("url", "file:///C:/video.mp4"), "url", path("/url"));
      int width = 400 - 2 * MARGIN;

      for (Object value :
          new Object[] {
            "file:///C:/video.mp4",
            "javascript:alert(1)",
            "//example.com/a.mp4",
            "https:example.com/a.mp4",
            "videos/a.mp4",
            "data:video/mp4;base64,AAAA",
            "",
            7L
          }) {
        fixture.setData("/url", value);

        assertFalse(poster().isEnabled(), String.valueOf(value));
        assertNull(poster().getToolTipText());
        poster().doClick(0);
        // The area has no play mark.
        assertEquals(colorAt(10, 10), colorAt(width / 2, 108), String.valueOf(value));
      }
      assertEquals(List.of(), opened);
      assertEquals(List.of(), fixture.errors);
      // A relative path and a data URL are safe. Only the external application does not get them.
      assertEquals(
          URI.create("data:video/mp4;base64,AAAA"), urlPropertyOf("data:video/mp4;base64,AAAA"));
      assertEquals(URI.create("videos/a.mp4"), urlPropertyOf("videos/a.mp4"));
      assertNull(urlPropertyOf("file:///C:/video.mp4"));

      fixture.setData("/url", VIDEO);
      assertTrue(poster().isEnabled());
      assertEquals(VideoView.OPEN, poster().getToolTipText());
    });
  }

  private Object urlPropertyOf(Object value) {
    fixture.setData("/url", value);
    return ViewMarks.mediaUrl(poster()).orElse(null);
  }

  @Test
  void thePosterImageComesFromTheImageLoaderAndCoversTheArea() {
    Edt.run(() -> {
      show(Map.of("poster", POSTER), "url", VIDEO, "posterUrl", path("/poster"));
      int width = 400 - 2 * MARGIN;
      Color area = colorAt(10, 10);

      assertEquals(List.of(URI.create(POSTER)), loader.requests);
      assertEquals(ImageSource.State.LOADING, poster().posterState());

      loader.complete(POSTER, TestImageLoader.solid(100, 400, RED));

      assertEquals(ImageSource.State.LOADED, poster().posterState());
      assertEquals(new Dimension(width, 216), poster().box());
      assertEquals(RED, colorAt(2, 2));
      assertEquals(RED, colorAt(width - 3, 213));
      assertEquals(Color.WHITE, colorAt(width / 2, 108));

      fixture.setData("/poster", "https://example.com/fail.png");
      assertEquals(List.of(), loader.cancelled);
      loader.fail("https://example.com/fail.png");
      assertEquals(ImageSource.State.FAILED, poster().posterState());
      assertEquals(area, colorAt(10, 10));

      fixture.setData("/poster", "file:///C:/poster.png");
      assertEquals(ImageSource.State.NONE, poster().posterState());
      assertEquals(2, loader.requests.size());
    });
  }

  @Test
  void theDisposalCancelsTheRequestOfThePoster() {
    Edt.run(() -> {
      show(Map.of(), "url", VIDEO, "posterUrl", POSTER);
      VideoView.Poster poster = poster();

      fixture.update(component("root", "Column", "children", List.of("after")));

      assertEquals(List.of(URI.create(POSTER)), loader.cancelled);
      loader.completeCancelled(POSTER, TestImageLoader.solid(10, 10, RED));
      assertEquals(ImageSource.State.NONE, poster.posterState());
      // The button of a disposed view opens nothing.
      poster.doClick(0);
      assertEquals(List.of(), opened);
    });
  }

  @Test
  void theAccessibleNameTellsWhatTheButtonDoes() {
    Edt.run(() -> {
      show(Map.of(), "url", VIDEO);

      assertEquals(AccessibleRole.PUSH_BUTTON, poster().getAccessibleContext().getAccessibleRole());
      assertEquals(VideoView.OPEN, poster().getAccessibleContext().getAccessibleName());

      fixture.update(
          component("video", "Video", "url", VIDEO, "@accessibility", Map.of("label", "Trailer")));
      assertEquals("Trailer", poster().getAccessibleContext().getAccessibleName());
    });
  }

  @Test
  void aFailureOfTheUrlOpenerDoesNotFailTheSurface() {
    Edt.run(() -> {
      fixture =
          new BasicFixture(
              services.withUrlOpener(uri -> {
                throw new UnsupportedOperationException("This computer has no browser.");
              }));
      show(Map.of(), "url", VIDEO);

      try (LogCapture log = new LogCapture()) {
        poster().doClick(0);

        assertEquals(1, log.messages.size());
      }
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void theViewOfTheMediaPlayerServiceGetsTheTwoUrls() {
    Edt.run(() -> {
      showWithPlayer(
          Map.of("url", VIDEO, "poster", POSTER),
          "url",
          path("/url"),
          "posterUrl",
          path("/poster"));
      TestMediaPlayer.View view = player.views.get(0);

      assertEquals(1, player.views.size());
      assertEquals(MediaPlayer.Kind.VIDEO, view.kind);
      assertSame(view.label, fixture.viewOf("video").accessibleComponent());
      assertEquals(List.of(Optional.of(URI.create(VIDEO))), view.sources);
      assertEquals(List.of(Optional.of(URI.create(POSTER))), view.posters);
      // The component of the media view has the width of the container.
      assertEquals(400 - 2 * MARGIN, view.label.getWidth());
      assertEquals(40 + 2 * MARGIN, fixture.bounds("video").height);
      assertEquals(URI.create(VIDEO), ViewMarks.mediaUrl(view.label).orElse(null));
      assertEquals(List.of(), loader.requests);

      fixture.setData("/url", "javascript:alert(1)");
      fixture.setData("/poster", "");

      assertEquals(Optional.empty(), view.sources.getLast());
      assertEquals(Optional.empty(), view.posters.getLast());
      assertNull(ViewMarks.mediaUrl(view.label).orElse(null));
      assertEquals(List.of(), opened);

      fixture.update(component("root", "Column", "children", List.of("after")));
      assertEquals(1, view.disposals);
    });
  }

  @Test
  void aMediaViewThatFailsDoesNotFailTheSurface() {
    Edt.run(() -> {
      showWithPlayer(Map.of("url", VIDEO), "url", path("/url"));
      TestMediaPlayer.View view = player.views.get(0);
      view.broken = true;

      try (LogCapture log = new LogCapture()) {
        fixture.setData("/url", OTHER);
        fixture.update(component("root", "Column", "children", List.of("after")));

        assertEquals(3, log.messages.size());
      }
      assertEquals(1, view.disposals);
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void aMediaPlayerServiceThatFailsGivesTheSubstitute() {
    Edt.run(() -> {
      player.broken = true;

      try (LogCapture log = new LogCapture()) {
        showWithPlayer(Map.of(), "url", VIDEO);

        assertEquals(1, log.messages.size());
      }
      assertTrue(poster().isEnabled());
      poster().doClick(0);
      assertEquals(List.of(URI.create(VIDEO)), opened);
    });
  }

  @Test
  void aChangeOfTheLookAndFeelKeepsTheSubstitute() {
    Edt.run(() -> {
      show(Map.of(), "url", VIDEO, "posterUrl", POSTER);
      loader.complete(POSTER, TestImageLoader.solid(160, 90, RED));

      SwingUtilities.updateComponentTreeUI(fixture.view);
      fixture.view.validate();

      assertEquals(RED, colorAt(2, 2));
      assertFalse(poster().isBorderPainted());
      assertFalse(poster().isContentAreaFilled());
      assertEquals(new Dimension(400 - 2 * MARGIN, 216), poster().box());
      assertEquals(1, loader.requests.size());
    });
  }
}
