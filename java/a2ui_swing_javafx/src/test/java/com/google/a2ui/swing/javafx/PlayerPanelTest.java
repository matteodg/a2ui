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

package com.google.a2ui.swing.javafx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.javafx.PlayerModel.Phase;
import com.google.a2ui.swing.surface.ImageLoader;
import com.google.a2ui.swing.surface.MediaPlayer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The tests use a {@link FakeEngine}. They need no display and no JavaFX toolkit. */
class PlayerPanelTest {
  private static final URI VIDEO = URI.create("https://example.com/intro.mp4");
  private static final URI POSTER = URI.create("https://example.com/poster.png");
  private static final URI OTHER_POSTER = URI.create("https://example.com/other.png");

  /** An image loader that keeps each request until the test gives the answer. */
  private static final class Loader implements ImageLoader {
    final List<URI> requests = new ArrayList<>();
    final List<URI> cancelled = new ArrayList<>();
    final List<Consumer<Optional<Image>>> results = new ArrayList<>();
    boolean broken;

    @Override
    public Subscription load(URI uri, Consumer<Optional<Image>> onResult) {
      if (broken) {
        throw new IllegalStateException("The loader is broken.");
      }
      requests.add(uri);
      results.add(onResult);
      return () -> cancelled.add(uri);
    }
  }

  private final FakeEngine engine = new FakeEngine();
  private final Loader loader = new Loader();
  private final List<URI> opened = new ArrayList<>();
  private PlayerPanel panel;

  private final LogCapture log = new LogCapture();

  @AfterEach
  void closeTheLog() {
    log.close();
  }

  private PlayerPanel make(MediaPlayer.Kind kind) {
    panel = new PlayerPanel(kind, engine, loader, opened::add, 1);
    return panel;
  }

  private JButton play() {
    return Swing.named(panel, "a2ui.media.play", JButton.class);
  }

  private JSlider seek() {
    return Swing.named(panel, "a2ui.media.seek", JSlider.class);
  }

  private JLabel time() {
    return Swing.named(panel, "a2ui.media.time", JLabel.class);
  }

  private JButton mute() {
    return Swing.named(panel, "a2ui.media.mute", JButton.class);
  }

  private JSlider volume() {
    return Swing.named(panel, "a2ui.media.volume", JSlider.class);
  }

  private JLabel message() {
    return Swing.named(panel, "a2ui.media.message", JLabel.class);
  }

  private JButton open() {
    return Swing.named(panel, "a2ui.media.open", JButton.class);
  }

  private JLabel description() {
    return Swing.named(panel, "a2ui.media.description", JLabel.class);
  }

  private JComponent screen() {
    return Swing.named(panel, "a2ui.media.screen", JComponent.class);
  }

  private static String nameOf(Component component) {
    return component.getAccessibleContext().getAccessibleName();
  }

  private static void click(Component component) {
    component.dispatchEvent(
        new MouseEvent(
            component,
            MouseEvent.MOUSE_CLICKED,
            System.currentTimeMillis(),
            0,
            5,
            5,
            1,
            false,
            MouseEvent.BUTTON1));
  }

  /** Starts the media with the play control and sends the events of a player that plays. */
  private FakeEngine.Session start(double duration) {
    panel.model().setSource(Optional.of(VIDEO));
    play().doClick(0);
    FakeEngine.Session session = engine.last();
    session.events.ready(duration);
    session.events.playing();
    return session;
  }

  @Test
  void aPanelWithNoMediaHasControlsThatTheUserCannotOperate() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.AUDIO);

      assertFalse(play().isEnabled());
      assertFalse(seek().isEnabled());
      assertEquals("0:00", time().getText());
      assertEquals(PlayerPanel.PLAY, nameOf(play()));
      assertEquals(PlayerPanel.SEEK, nameOf(seek()));
      assertEquals(PlayerPanel.MUTE, nameOf(mute()));
      assertEquals(PlayerPanel.VOLUME, nameOf(volume()));
      assertEquals(100, volume().getValue());
      assertFalse(message().isVisible());
      assertFalse(open().isVisible());
      assertFalse(description().isVisible());
    });
  }

  @Test
  void thePlayControlStartsTheMediaAndShowsTheStateOfThePlayer() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.AUDIO);
      panel.model().setSource(Optional.of(VIDEO));
      assertTrue(play().isEnabled());
      assertSame(PlayerIcon.PLAY, play().getIcon());
      assertEquals(List.of(), engine.sessions);

      play().doClick(0);
      FakeEngine.Session session = engine.last();

      assertEquals(Phase.LOADING, panel.model().phase());
      assertSame(PlayerIcon.PAUSE, play().getIcon());
      assertEquals(PlayerPanel.PAUSE, nameOf(play()));
      assertEquals(PlayerPanel.LOADING, time().getText());
      assertFalse(seek().isEnabled());

      session.events.ready(75);
      session.events.playing();
      session.events.time(12.4);

      assertEquals("0:12 / 1:15", time().getText());
      assertTrue(seek().isEnabled());
      assertEquals(75, seek().getMaximum());
      assertEquals(12, seek().getValue());
      // The panel wrote the position to the slider. That is not a request of the user.
      assertEquals(List.of("play"), session.calls);

      play().doClick(0);
      assertEquals(Phase.PAUSED, panel.model().phase());
      assertSame(PlayerIcon.PLAY, play().getIcon());
      assertEquals(PlayerPanel.PLAY, nameOf(play()));
    });
  }

  @Test
  void theSlidersAndTheSoundControlOperateThePlayer() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.AUDIO);
      FakeEngine.Session session = start(60);

      seek().setValue(30);
      assertEquals(30, panel.model().position());

      // While the user moves the slider, the player keeps its position.
      seek().setValueIsAdjusting(true);
      seek().setValue(45);
      session.events.time(31);
      assertEquals(45, seek().getValue());
      seek().setValueIsAdjusting(false);

      volume().setValue(25);
      mute().doClick(0);
      assertSame(PlayerIcon.MUTED, mute().getIcon());
      assertEquals(PlayerPanel.UNMUTE, nameOf(mute()));
      mute().doClick(0);
      assertSame(PlayerIcon.SOUND, mute().getIcon());

      assertEquals(
          List.of("play", "seek 30.0", "seek 45.0", "volume 0.25", "muted true", "muted false"),
          session.calls);
    });
  }

  @Test
  void theDescriptionShowsAndIsAPartOfTheNamesOfTheControls() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.AUDIO);
      int without = panel.preferredHeightFor(400);

      panel.setDescription("  Episode 1  ");

      assertTrue(description().isVisible());
      assertEquals("Episode 1", description().getText());
      assertEquals("Play: Episode 1", nameOf(play()));
      assertEquals("Seek: Episode 1", nameOf(seek()));
      assertEquals("Mute: Episode 1", nameOf(mute()));
      assertEquals("Volume: Episode 1", nameOf(volume()));
      assertTrue(panel.preferredHeightFor(400) > without);

      panel.setDescription(null);

      assertFalse(description().isVisible());
      assertEquals(PlayerPanel.PLAY, nameOf(play()));
      assertEquals(without, panel.preferredHeightFor(400));
    });
  }

  @Test
  void aFailureShowsAMessageAndAButtonThatOpensTheUrlOnlyWhenTheUserOperatesIt() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.VIDEO);
      Swing.mount(panel, 400, 400);
      int before = panel.preferredHeightFor(400);
      FakeEngine.Session session = start(10);

      session.events.failed("MEDIA_UNSUPPORTED: no codec");

      assertTrue(message().isVisible());
      assertEquals(PlayerPanel.FAILURE + "MEDIA_UNSUPPORTED: no codec", message().getText());
      assertEquals(message().getText(), message().getToolTipText());
      assertTrue(open().isVisible());
      assertTrue(panel.preferredHeightFor(400) > before);
      assertEquals(List.of(), opened);
      // The picture of the player that failed does not show.
      assertFalse(engine.screens.get(0).isVisible());

      open().doClick(0);
      assertEquals(List.of(VIDEO), opened);

      // The play control tries again, and the message goes away.
      play().doClick(0);
      assertFalse(message().isVisible());
      assertFalse(open().isVisible());
      assertEquals(before, panel.preferredHeightFor(400));
      assertEquals(2, engine.sessions.size());
    });
  }

  @Test
  void aUrlThatThePlayerCannotPlayShowsAMessageAndNoButton() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.AUDIO);

      panel.model().setSource(Optional.of(URI.create("data:audio/wav;base64,AAAA")));

      assertTrue(message().isVisible());
      assertEquals(PlayerPanel.FAILURE + PlayerModel.UNSUPPORTED_URL, message().getText());
      assertFalse(open().isVisible());
      assertFalse(play().isEnabled());
      assertEquals(List.of(), engine.sessions);
    });
  }

  @Test
  void aUrlOpenerThatFailsDoesNotFailTheListener() {
    Swing.onEdt(() -> {
      panel =
          new PlayerPanel(
              MediaPlayer.Kind.AUDIO,
              engine,
              loader,
              uri -> {
                throw new UnsupportedOperationException("no browser");
              },
              1);
      start(10).events.failed("no codec");

      open().doClick(0);

      assertEquals(Phase.FAILED, panel.model().phase());
      assertEquals(List.of("The URL opener did not open " + VIDEO + "."), log.messages);
    });
  }

  @Test
  void aVideoPanelHasAScreenOf16To9AndItsHeightDependsOnItsWidth() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.VIDEO);
      Swing.mount(panel, 400, 400);
      Component bar = play().getParent();
      int barHeight = bar.getPreferredSize().height;

      assertTrue(panel instanceof HeightForWidth);
      assertEquals(new Rectangle(0, 0, 400, 225), screen().getBounds());
      assertEquals(new Rectangle(0, 229, 400, barHeight), bar.getBounds());
      assertEquals(225 + 4 + barHeight, panel.preferredHeightFor(400));
      assertEquals(180 + 4 + barHeight, panel.preferredHeightFor(320));
      // The parts are at most 480 pixels wide.
      assertEquals(270 + 4 + barHeight, panel.preferredHeightFor(1000));
      assertEquals(480, panel.getPreferredSize().width);
      assertEquals(270 + 4 + barHeight, panel.getPreferredSize().height);

      panel.setSize(1000, 400);
      panel.validate();
      assertEquals(new Rectangle(0, 0, 480, 270), screen().getBounds());
      assertEquals(480, bar.getWidth());

      // The controls are in one row: play, position, time, sound, volume.
      assertEquals(0, play().getX());
      assertTrue(seek().getX() >= play().getX() + play().getWidth());
      assertTrue(time().getX() >= seek().getX() + seek().getWidth());
      assertTrue(mute().getX() >= time().getX() + time().getWidth());
      assertTrue(volume().getX() >= mute().getX() + mute().getWidth());
      assertEquals(480, volume().getX() + volume().getWidth());
      assertTrue(seek().getWidth() > 100);
    });
  }

  @Test
  void aNarrowBarHasNoVolumeSliderAndKeepsTheOtherControls() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.AUDIO);
      Swing.mount(panel, 240, 100);

      assertFalse(volume().isVisible());
      assertEquals(240, mute().getX() + mute().getWidth());
      assertTrue(seek().getWidth() > 40);

      panel.setSize(400, 100);
      panel.validate();
      panel.validate();
      assertTrue(volume().isVisible());
    });
  }

  @Test
  void anAudioPanelHasNoScreenAndAsksForNone() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.AUDIO);
      Swing.mount(panel, 400, 100);
      Component bar = play().getParent();

      assertThrows(IllegalArgumentException.class, this::screen);
      assertEquals(bar.getPreferredSize().height, panel.preferredHeightFor(400));
      assertEquals(0, bar.getY());

      start(10);
      assertEquals(List.of(), engine.screens);
      assertEquals(null, engine.last().screen);
    });
  }

  @Test
  void theEngineMakesTheScreenOnlyWhenTheUserStartsTheVideo() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.VIDEO);
      Swing.mount(panel, 400, 400);
      panel.model().setSource(Optional.of(VIDEO));
      assertEquals(List.of(), engine.screens);

      play().doClick(0);
      FakeEngine.Session session = engine.last();
      panel.validate();

      assertEquals(1, engine.screens.size());
      JComponent picture = engine.screens.get(0);
      assertSame(picture, session.screen);
      assertSame(screen(), picture.getParent());
      // The poster shows until the player plays.
      assertFalse(picture.isVisible());

      session.events.ready(10);
      session.events.playing();
      panel.validate();
      assertTrue(picture.isVisible());
      assertEquals(new Rectangle(0, 0, 400, 225), picture.getBounds());

      // A second media uses the same screen.
      panel.model().setSource(Optional.of(URI.create("https://example.com/two.mp4")));
      assertFalse(picture.isVisible());
      play().doClick(0);
      assertEquals(1, engine.screens.size());
      assertSame(picture, engine.last().screen);
    });
  }

  @Test
  void aClickOnTheScreenHasTheEffectOfThePlayControl() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.VIDEO);
      Swing.mount(panel, 400, 400);
      // A screen with no media does nothing.
      click(screen());
      assertEquals(List.of(), engine.sessions);

      panel.model().setSource(Optional.of(VIDEO));
      click(screen());
      FakeEngine.Session session = engine.last();
      session.events.ready(10);
      session.events.playing();
      assertEquals(Phase.PLAYING, panel.model().phase());

      // The picture of the engine covers the screen now. A click on it stops the media.
      click(engine.screens.get(0));
      assertEquals(Phase.PAUSED, panel.model().phase());
      assertEquals(List.of("play", "pause"), session.calls);
    });
  }

  @Test
  void theScreenPaintsThePosterAndAPlayMarkUntilTheMediaPlays() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.VIDEO);
      Swing.mount(panel, 400, 400);
      panel.model().setSource(Optional.of(VIDEO));
      panel.setPoster(Optional.of(POSTER));
      BufferedImage red = new BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB);
      for (int x = 0; x < 160; x++) {
        for (int y = 0; y < 90; y++) {
          red.setRGB(x, y, new Color(200, 0, 0).getRGB());
        }
      }

      BufferedImage waiting = Swing.snapshot(panel, "player-video-poster-waits");
      assertEquals(Color.BLACK, new Color(waiting.getRGB(10, 10)));

      loader.results.get(0).accept(Optional.of(red));
      BufferedImage idle = Swing.snapshot(panel, "player-video-idle");

      assertEquals(new Color(200, 0, 0), new Color(idle.getRGB(10, 10)));
      assertEquals(new Color(200, 0, 0), new Color(idle.getRGB(390, 215)));
      // The play mark is white, in the center of the screen.
      assertEquals(Color.WHITE, new Color(idle.getRGB(202, 112)));

      // While the player loads, the screen has no play mark.
      play().doClick(0);
      BufferedImage loading = Swing.snapshot(panel, "player-video-loading");
      assertNotEquals(Color.WHITE, new Color(loading.getRGB(202, 112)));
    });
  }

  @Test
  void thePanelLoadsThePosterOneTimeAndCancelsTheRequestThatItDoesNotNeed() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.VIDEO);

      panel.setPoster(Optional.of(POSTER));
      panel.setPoster(Optional.of(POSTER));
      assertEquals(List.of(POSTER), loader.requests);

      panel.setPoster(Optional.of(OTHER_POSTER));
      assertEquals(List.of(POSTER, OTHER_POSTER), loader.requests);
      assertEquals(List.of(POSTER), loader.cancelled);

      // A request that has its answer needs no cancel.
      loader.results.get(1).accept(Optional.empty());
      panel.setPoster(Optional.empty());
      assertEquals(List.of(POSTER), loader.cancelled);

      panel.setPoster(Optional.of(POSTER));
      panel.dispose();
      assertEquals(List.of(POSTER, POSTER), loader.cancelled);

      // An audio panel has no poster.
      make(MediaPlayer.Kind.AUDIO);
      panel.setPoster(Optional.of(POSTER));
      assertEquals(3, loader.requests.size());
    });
  }

  @Test
  void thePanelLoadsNoPosterFromAUrlThatIsNotSafe() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.VIDEO);

      for (String unsafe :
          List.of("file:///C:/secret.png", "javascript:alert(1)", "//host/a.png")) {
        panel.setPoster(Optional.of(URI.create(unsafe)));
      }

      assertEquals(List.of(), loader.requests);
    });
  }

  @Test
  void anImageLoaderThatFailsDoesNotFailThePanel() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.VIDEO);
      loader.broken = true;

      panel.setPoster(Optional.of(POSTER));

      assertEquals(List.of(), loader.requests);
      assertEquals(1, log.messages.size());
    });
  }

  @Test
  void aPanelThatLeavesItsWindowReleasesThePlayer() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.VIDEO);
      JPanel holder = Swing.mount(panel, 400, 400);
      FakeEngine.Session session = start(10);

      holder.remove(panel);
      panel.removeNotify();

      assertTrue(session.disposed());
      assertEquals(Phase.IDLE, panel.model().phase());
      assertFalse(engine.screens.get(0).isVisible());
      assertTrue(play().isEnabled());
    });
  }

  @Test
  void theDisposalReleasesThePlayer() {
    Swing.onEdt(() -> {
      make(MediaPlayer.Kind.AUDIO);
      FakeEngine.Session session = start(10);

      panel.dispose();

      assertTrue(session.disposed());
      play().doClick(0);
      assertEquals(1, engine.sessions.size());
    });
  }

  @Test
  void aTimeIsMinutesAndSecondsWithHoursIfThereAreSome() {
    assertEquals("0:00", PlayerPanel.clock(0));
    assertEquals("0:09", PlayerPanel.clock(9.99));
    assertEquals("1:05", PlayerPanel.clock(65));
    assertEquals("59:59", PlayerPanel.clock(3599));
    assertEquals("1:02:03", PlayerPanel.clock(3723));
    assertEquals("0:00", PlayerPanel.clock(-4));
    assertEquals("0:00", PlayerPanel.clock(Double.NaN));
  }

  @Test
  void thePanelShowsInEachLookAndFeel() {
    Swing.onEdt(() -> {
      String original = UIManager.getLookAndFeel().getClass().getName();
      try {
        for (String name : List.of("Metal", "Nimbus")) {
          for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if (info.getName().equals(name)) {
              UIManager.setLookAndFeel(info.getClassName());
            }
          }
          make(MediaPlayer.Kind.VIDEO);
          panel.setDescription("A description of the media");
          Swing.mount(panel, 420, panel.preferredHeightFor(420));
          FakeEngine.Session session = start(125);
          session.events.time(42);
          session.events.failed("MEDIA_UNSUPPORTED: no codec");
          panel.setSize(420, panel.preferredHeightFor(420));
          panel.validate();
          panel.validate();

          Swing.snapshot(panel, "player-video-failed-" + name.toLowerCase());
          assertTrue(play().getHeight() > 0, name);
          assertTrue(seek().getHeight() > 0, name);
          assertTrue(open().getHeight() > 0, name);

          make(MediaPlayer.Kind.AUDIO);
          panel.setDescription("A description of the media");
          Swing.mount(panel, 420, panel.preferredHeightFor(420));
          start(125).events.time(42);
          panel.validate();
          Swing.snapshot(panel, "player-audio-playing-" + name.toLowerCase());
          assertEquals("0:42 / 2:05", time().getText());
        }
      } finally {
        UIManager.setLookAndFeel(original);
      }
    });
  }
}
