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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.internal.ViewMarks;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.MediaPlayer;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.LogCapture;
import com.google.a2ui.swing.testing.TestMediaPlayer;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.accessibility.AccessibleRole;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** The surface of the tests is 400 pixels wide and 300 pixels high. */
class AudioPlayerViewTest {
  private static final int MARGIN = BasicStyle.MARGIN;
  private static final String AUDIO = "https://example.com/podcast-ep1.mp3";
  private static final String OTHER = "https://example.com/podcast-ep2.mp3";

  private final TestMediaPlayer player = new TestMediaPlayer();
  private final List<URI> opened = new ArrayList<>();
  private final A2uiServices services = A2uiServices.DEFAULT.withUrlOpener(opened::add);
  private BasicFixture fixture = new BasicFixture(services);

  private void show(Map<String, Object> data, Object... properties) {
    fixture.create(
        data,
        component("root", "Column", "children", List.of("audio", "after")),
        component("audio", "AudioPlayer", properties),
        component("after", "Text", "text", "After"));
  }

  private void showWithPlayer(Map<String, Object> data, Object... properties) {
    fixture = new BasicFixture(services.withMediaPlayer(player));
    show(data, properties);
  }

  private JButton button() {
    return (JButton) fixture.viewOf("audio").accessibleComponent();
  }

  private WrappingLabel text() {
    return find(fixture.componentOf("audio"));
  }

  private static WrappingLabel find(Component component) {
    if (component instanceof WrappingLabel label) {
      return label;
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        WrappingLabel found = find(child);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private Rectangle inSurface(Component component) {
    return SwingUtilities.convertRectangle(
        component.getParent(), component.getBounds(), fixture.view);
  }

  @Test
  void theSubstituteIsAButtonAndTheDescription() {
    Edt.run(() -> {
      show(Map.of(), "url", AUDIO, "description", "Episode 1");

      Rectangle button = inSurface(button());
      Rectangle text = inSurface(text());
      assertTrue(button().isEnabled());
      assertTrue(button().getIcon() != null && button().getText().isEmpty());
      assertEquals("Episode 1", text().getText());
      assertEquals(MARGIN, button.x);
      // The description is at the right of the button and has the other width of the surface.
      assertTrue(text.x > button.x + button.width, button + " " + text);
      assertEquals(400 - MARGIN, text.x + text.width);
      assertEquals(button.getCenterY(), text.getCenterY(), 1.0);
      assertEquals(button.height + 2 * MARGIN, fixture.bounds("audio").height);
      assertEquals(fixture.bounds("audio").height, fixture.bounds("after").y);
      assertEquals(List.of(), opened);
    });
  }

  @Test
  void aLongDescriptionBreaksAtTheWidthOfTheContainer() {
    Edt.run(() -> {
      show(Map.of(), "url", AUDIO, "description", "A long description of the episode. ".repeat(8));

      int height = fixture.bounds("audio").height;
      assertTrue(text().getHeight() > button().getHeight(), "the text has more than one line");
      assertEquals(text().getHeight() + 2 * MARGIN, height);
      assertEquals(height, fixture.bounds("after").y);
      assertEquals(height, ((HeightForWidth) fixture.componentOf("audio")).preferredHeightFor(400));

      fixture.resize(240, 300);
      assertTrue(fixture.bounds("audio").height > height);
      assertEquals(240 - MARGIN, inSurface(text()).x + text().getWidth());
    });
  }

  @Test
  void theDescriptionFollowsTheDataAndAnAbsentDescriptionShowsNoText() {
    Edt.run(() -> {
      show(Map.of("title", "Episode 1"), "url", AUDIO, "description", path("/title"));

      fixture.setData("/title", "Episode 2");
      assertEquals("Episode 2", text().getText());
      assertTrue(text().isVisible());

      fixture.setData("/title", "");
      assertFalse(text().isVisible());

      fixture.update(component("audio", "AudioPlayer", "url", AUDIO));
      assertFalse(text().isVisible());
      assertEquals(button().getHeight() + 2 * MARGIN, fixture.bounds("audio").height);
    });
  }

  @Test
  void theButtonOpensTheUrlOnlyWhenTheUserOperatesIt() {
    Edt.run(() -> {
      show(Map.of("url", AUDIO), "url", path("/url"));
      assertEquals(List.of(), opened);

      button().doClick(0);
      assertEquals(List.of(URI.create(AUDIO)), opened);

      fixture.setData("/url", OTHER);
      assertEquals(1, opened.size());
      assertEquals(URI.create(OTHER), ViewMarks.mediaUrl(button()).orElse(null));

      button().doClick(0);
      assertEquals(List.of(URI.create(AUDIO), URI.create(OTHER)), opened);
    });
  }

  @Test
  void aUrlThatAnExternalApplicationCannotGetDisablesTheButton() {
    Edt.run(() -> {
      show(Map.of("url", "file:///C:/a.mp3"), "url", path("/url"), "description", "Episode 1");

      for (Object value :
          new Object[] {
            "file:///C:/a.mp3",
            "javascript:alert(1)",
            "//example.com/a.mp3",
            "http:example.com/a.mp3",
            "audio/a.mp3",
            "data:audio/mpeg;base64,AAAA",
            "",
            7L
          }) {
        fixture.setData("/url", value);

        assertFalse(button().isEnabled(), String.valueOf(value));
        assertNull(button().getToolTipText());
        button().doClick(0);
      }
      assertEquals(List.of(), opened);
      assertEquals(List.of(), fixture.errors);
      assertEquals("Episode 1", text().getText());

      fixture.setData("/url", "file:///C:/a.mp3");
      assertNull(ViewMarks.mediaUrl(button()).orElse(null));
      fixture.setData("/url", "audio/a.mp3");
      assertEquals(URI.create("audio/a.mp3"), ViewMarks.mediaUrl(button()).orElse(null));

      fixture.setData("/url", AUDIO);
      assertTrue(button().isEnabled());
      assertEquals(AudioPlayerView.OPEN, button().getToolTipText());
    });
  }

  @Test
  void theAccessibleNameTellsWhatTheButtonDoesAndHasTheDescription() {
    Edt.run(() -> {
      show(Map.of("title", "Episode 1"), "url", AUDIO, "description", path("/title"));

      assertEquals(AccessibleRole.PUSH_BUTTON, button().getAccessibleContext().getAccessibleRole());
      assertEquals(
          AudioPlayerView.OPEN + ": Episode 1",
          button().getAccessibleContext().getAccessibleName());

      fixture.setData("/title", "");
      assertEquals(AudioPlayerView.OPEN, button().getAccessibleContext().getAccessibleName());

      fixture.update(
          component(
              "audio", "AudioPlayer", "url", AUDIO, "@accessibility", Map.of("label", "Listen")));
      assertEquals("Listen", button().getAccessibleContext().getAccessibleName());
    });
  }

  @Test
  void theDisposalRemovesTheListenerOfTheButton() {
    Edt.run(() -> {
      show(Map.of(), "url", AUDIO);
      JButton button = button();

      fixture.update(component("root", "Column", "children", List.of("after")));

      button.doClick(0);
      assertEquals(List.of(), opened);
      assertNull(button.getToolTipText());
    });
  }

  @Test
  void theViewOfTheMediaPlayerServiceGetsTheUrl() {
    Edt.run(() -> {
      showWithPlayer(
          Map.of("url", AUDIO, "title", "Episode 1"),
          "url",
          path("/url"),
          "description",
          path("/title"));
      TestMediaPlayer.View view = player.views.get(0);

      assertEquals(1, player.views.size());
      assertEquals(MediaPlayer.Kind.AUDIO, view.kind);
      assertSame(view.label, fixture.viewOf("audio").accessibleComponent());
      assertEquals(List.of(Optional.of(URI.create(AUDIO))), view.sources);
      assertEquals(List.of(), view.posters);
      assertEquals(400 - 2 * MARGIN, view.label.getWidth());
      assertEquals(40 + 2 * MARGIN, fixture.bounds("audio").height);
      assertEquals(URI.create(AUDIO), ViewMarks.mediaUrl(view.label).orElse(null));
      // The description is the accessible name of the component of the media view.
      assertEquals("Episode 1", view.label.getAccessibleContext().getAccessibleName());

      fixture.setData("/url", "ftp://example.com/a.mp3");
      assertEquals(Optional.empty(), view.sources.getLast());
      assertNull(ViewMarks.mediaUrl(view.label).orElse(null));
      assertEquals(List.of(), opened);

      fixture.update(component("root", "Column", "children", List.of("after")));
      assertEquals(1, view.disposals);
    });
  }

  @Test
  void theViewOfTheMediaPlayerServiceGetsTheDescriptionWhenItChanges() {
    Edt.run(() -> {
      showWithPlayer(
          Map.of("url", AUDIO, "title", " Episode 1 "),
          "url",
          path("/url"),
          "description",
          path("/title"));
      TestMediaPlayer.View view = player.views.get(0);

      assertEquals(List.of("Episode 1"), view.descriptions);

      // A change of a different property gives no second call.
      fixture.setData("/url", OTHER);
      assertEquals(List.of("Episode 1"), view.descriptions);

      fixture.setData("/title", "Episode 2");
      assertEquals(List.of("Episode 1", "Episode 2"), view.descriptions);

      fixture.update(component("audio", "AudioPlayer", "url", path("/url")));
      assertEquals(List.of("Episode 1", "Episode 2", ""), view.descriptions);
    });
  }

  @Test
  void aMediaViewThatFailsDoesNotFailTheSurface() {
    Edt.run(() -> {
      showWithPlayer(Map.of("url", AUDIO), "url", path("/url"));
      TestMediaPlayer.View view = player.views.get(0);
      view.broken = true;

      try (LogCapture log = new LogCapture()) {
        fixture.setData("/url", OTHER);
        fixture.update(component("root", "Column", "children", List.of("after")));

        assertEquals(2, log.messages.size());
      }
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void aMediaPlayerServiceThatFailsGivesTheSubstitute() {
    Edt.run(() -> {
      player.broken = true;

      try (LogCapture log = new LogCapture()) {
        showWithPlayer(Map.of(), "url", AUDIO);

        assertEquals(1, log.messages.size());
      }
      button().doClick(0);
      assertEquals(List.of(URI.create(AUDIO)), opened);
    });
  }

  @Test
  void aChangeOfTheLookAndFeelKeepsTheSubstitute() {
    Edt.run(() -> {
      show(Map.of(), "url", AUDIO, "description", "Episode 1");
      JButton before = button();

      SwingUtilities.updateComponentTreeUI(fixture.view);
      fixture.view.validate();

      assertSame(before, button());
      assertTrue(button().getIcon() != null);
      assertEquals("Episode 1", text().getText());
      button().doClick(0);
      assertEquals(List.of(URI.create(AUDIO)), opened);
    });
  }
}
