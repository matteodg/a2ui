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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.validation.ValidationConfig;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.catalog.basic.SwingBasicCatalog;
import com.google.a2ui.swing.javafx.PlayerModel.Phase;
import com.google.a2ui.swing.surface.A2uiFallbacks;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.A2uiSurface;
import com.google.a2ui.swing.surface.A2uiSwing;
import com.google.a2ui.swing.surface.MediaPlayer;
import com.google.a2ui.swing.surface.MediaView;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JavaFxMediaPlayerTest {
  private static final String VIDEO = "https://example.com/intro.mp4";
  private static final String AUDIO = "https://example.com/episode.mp3";

  private final FakeEngine engine = new FakeEngine();
  private final List<URI> opened = new ArrayList<>();
  private int starts;

  private final LogCapture log = new LogCapture();

  @AfterEach
  void closeTheLog() {
    log.close();
  }

  /** Makes a player whose engine is the test engine. */
  private JavaFxMediaPlayer player() {
    return new JavaFxMediaPlayer(
        JavaFxMediaPlayer.builder().urlOpener(opened::add).volume(0.5),
        () -> {
          starts++;
          return Optional.of(engine);
        });
  }

  private static Map<String, Object> component(String id, String type, Object... namesAndValues) {
    Map<String, Object> component = new LinkedHashMap<>();
    component.put("id", id);
    component.put("component", type);
    for (int i = 0; i < namesAndValues.length; i += 2) {
      component.put((String) namesAndValues[i], namesAndValues[i + 1]);
    }
    return component;
  }

  /** A surface of the basic catalog whose media player is {@link #player()}. */
  private final class Surface {
    final A2uiServices services =
        A2uiServices.DEFAULT.withUrlOpener(opened::add).withMediaPlayer(player());
    final MessageProcessor<SwingComponentImplementation> processor =
        new MessageProcessor<>(
            List.of(SwingBasicCatalog.create(Locale.US, services)), ValidationConfig.RELAXED);
    final A2uiSurface view;

    Surface(Object... components) {
      A2uiSwing.install(processor);
      Map<String, Object> create = new LinkedHashMap<>();
      create.put("surfaceId", "main");
      create.put("catalogId", SwingBasicCatalog.create(Locale.US, services).id());
      create.put("components", List.of(components));
      processor.processMessages(Map.of("version", "1.0", "createSurface", create));
      view =
          new A2uiSurface(
              processor.model().getSurface("main").orElseThrow(), A2uiFallbacks.NONE, services);
      Swing.mount(view, 400, 600);
    }

    void update(Object... components) {
      processor.processMessages(
          Map.of(
              "version",
              "1.0",
              "updateComponents",
              Map.of("surfaceId", "main", "components", List.of(components))));
      view.validate();
    }

    JComponent control(String id) {
      return view.viewTree()
          .orElseThrow()
          .find(id)
          .orElseThrow()
          .view()
          .orElseThrow()
          .accessibleComponent();
    }

    PlayerPanel panel(String id) {
      return assertInstanceOf(PlayerPanel.class, control(id));
    }
  }

  @Test
  void thePlayerMakesAViewForEachTypeOfMediaAndStartsTheEngineOneTime() {
    Swing.onEdt(() -> {
      JavaFxMediaPlayer player = player();
      assertEquals(0, starts);

      MediaView video = player.createView(MediaPlayer.Kind.VIDEO).orElseThrow();
      MediaView audio = player.createView(MediaPlayer.Kind.AUDIO).orElseThrow();

      assertEquals(1, starts);
      assertInstanceOf(PlayerPanel.class, video.component());
      assertSame(video.component(), video.component());
      assertTrue(video.component() != audio.component());
      // The view of a video has a screen. The engine did not make its picture yet.
      Swing.named(video.component(), "a2ui.media.screen", JComponent.class);
      assertEquals(List.of(), engine.screens);
      assertEquals(0.5, ((PlayerPanel) audio.component()).model().volume());
    });
  }

  @Test
  void aViewGetsItsMediaAndDoesNotStartIt() {
    Swing.onEdt(() -> {
      MediaView view = player().createView(MediaPlayer.Kind.VIDEO).orElseThrow();
      PlayerModel model = ((PlayerPanel) view.component()).model();

      view.setSource(Optional.of(URI.create(VIDEO)));
      view.setPoster(Optional.empty());
      view.setDescription("A title");

      assertEquals(Phase.IDLE, model.phase());
      assertEquals(List.of(), engine.sessions);

      model.toggle();
      view.dispose();

      assertTrue(engine.last().disposed());
      assertEquals(Phase.EMPTY, model.phase());
    });
  }

  @Test
  void anEngineThatCannotStartGivesNoViewAndThePlayerAsksOnlyOneTime() {
    Swing.onEdt(() -> {
      JavaFxMediaPlayer player =
          new JavaFxMediaPlayer(
              JavaFxMediaPlayer.builder(),
              () -> {
                starts++;
                return Optional.empty();
              });

      assertEquals(Optional.empty(), player.createView(MediaPlayer.Kind.VIDEO));
      assertEquals(Optional.empty(), player.createView(MediaPlayer.Kind.AUDIO));
      assertEquals(1, starts);
    });
  }

  @Test
  void aStartThatFailsGivesNoView() {
    Swing.onEdt(() -> {
      // This error occurs when the JavaFX libraries are not on the class path.
      JavaFxMediaPlayer absent =
          new JavaFxMediaPlayer(
              JavaFxMediaPlayer.builder(),
              () -> {
                throw new NoClassDefFoundError("javafx/application/Platform");
              });
      // This error occurs when the native library of the platform does not load.
      JavaFxMediaPlayer noNative =
          new JavaFxMediaPlayer(
              JavaFxMediaPlayer.builder(),
              () -> {
                throw new UnsatisfiedLinkError("no glass in java.library.path");
              });
      JavaFxMediaPlayer broken =
          new JavaFxMediaPlayer(
              JavaFxMediaPlayer.builder(),
              () -> {
                throw new UnsupportedOperationException("Unable to open DISPLAY");
              });
      JavaFxMediaPlayer nothing = new JavaFxMediaPlayer(JavaFxMediaPlayer.builder(), () -> null);

      for (JavaFxMediaPlayer player : List.of(absent, noNative, broken, nothing)) {
        assertEquals(Optional.empty(), player.createView(MediaPlayer.Kind.VIDEO));
        assertEquals(Optional.empty(), player.createView(MediaPlayer.Kind.AUDIO));
      }
      // Each player that failed writes one record, at its first view.
      assertEquals(3, log.messages.size());
    });
  }

  @Test
  void theJavaFxEngineDoesNotStartOnAComputerWithNoDisplay() {
    assumeTrue(GraphicsEnvironment.isHeadless(), "The test needs a JVM with no display.");
    Swing.onEdt(() -> {
      assertEquals(Optional.empty(), JavaFxEngine.start());

      JavaFxMediaPlayer player = new JavaFxMediaPlayer();
      assertEquals(Optional.empty(), player.createView(MediaPlayer.Kind.VIDEO));
      assertEquals(Optional.empty(), player.createView(MediaPlayer.Kind.AUDIO));
    });
  }

  @Test
  void theComponentsShowTheirSubstituteWhenJavaFxCannotOperate() {
    assumeTrue(GraphicsEnvironment.isHeadless(), "The test needs a JVM with no display.");
    Swing.onEdt(() -> {
      A2uiServices services = A2uiServices.DEFAULT.withMediaPlayer(new JavaFxMediaPlayer());
      MessageProcessor<SwingComponentImplementation> processor =
          new MessageProcessor<>(
              List.of(SwingBasicCatalog.create(Locale.US, services)), ValidationConfig.RELAXED);
      A2uiSwing.install(processor);
      Map<String, Object> create = new LinkedHashMap<>();
      create.put("surfaceId", "main");
      create.put("catalogId", SwingBasicCatalog.create(Locale.US, services).id());
      create.put(
          "components",
          List.of(
              component("root", "Column", "children", List.of("video", "audio")),
              component("video", "Video", "url", VIDEO),
              component("audio", "AudioPlayer", "url", AUDIO)));
      processor.processMessages(Map.of("version", "1.0", "createSurface", create));
      A2uiSurface view =
          new A2uiSurface(
              processor.model().getSurface("main").orElseThrow(), A2uiFallbacks.NONE, services);
      Swing.mount(view, 400, 600);

      // The substitute of each component is a button that opens the URL.
      for (String id : List.of("video", "audio")) {
        JComponent control =
            view.viewTree()
                .orElseThrow()
                .find(id)
                .orElseThrow()
                .view()
                .orElseThrow()
                .accessibleComponent();
        assertInstanceOf(JButton.class, control, id);
        assertTrue(control.isEnabled());
      }
    });
  }

  @Test
  void theBuilderRefusesValuesThatAreNotCorrect() {
    JavaFxMediaPlayer.Builder builder = JavaFxMediaPlayer.builder();

    assertThrows(NullPointerException.class, () -> builder.imageLoader(null));
    assertThrows(NullPointerException.class, () -> builder.urlOpener(null));
    assertThrows(IllegalArgumentException.class, () -> builder.volume(-0.1));
    assertThrows(IllegalArgumentException.class, () -> builder.volume(1.1));
    assertThrows(IllegalArgumentException.class, () -> builder.volume(Double.NaN));
    assertSame(builder, builder.volume(0).volume(1));
    assertTrue(builder.build() != builder.build());
    Swing.onEdt(() ->
        assertThrows(NullPointerException.class, () -> new JavaFxMediaPlayer().createView(null)));
  }

  @Test
  void theServiceLoaderFindsThePlayer() {
    List<String> providers = new ArrayList<>();
    ServiceLoader.load(MediaPlayer.class).stream()
        .forEach(provider -> providers.add(provider.type().getName()));

    assertEquals(List.of(JavaFxMediaPlayer.class.getName()), providers);
    assertInstanceOf(
        JavaFxMediaPlayer.class, ServiceLoader.load(MediaPlayer.class).findFirst().orElseThrow());
  }

  @Test
  void theVideoComponentShowsThePlayerAndGivesOnlyASafeUrlToIt() {
    Swing.onEdt(() -> {
      Surface surface =
          new Surface(
              component("root", "Column", "children", List.of("video", "after")),
              component("video", "Video", "url", VIDEO, "posterUrl", "https://example.com/p.png"),
              component("after", "Text", "text", "After"));
      PlayerPanel panel = surface.panel("video");
      int margin = (400 - panel.getWidth()) / 2;

      assertEquals(Phase.IDLE, panel.model().phase());
      assertEquals(Optional.of(URI.create(VIDEO)), panel.model().external());
      assertEquals(List.of(), engine.sessions);
      // The screen has the width of the container and the proportions 16 to 9.
      JComponent screen = Swing.named(panel, "a2ui.media.screen", JComponent.class);
      assertEquals(400 - 2 * margin, screen.getWidth());
      assertEquals(Math.round(screen.getWidth() * 9 / 16f), screen.getHeight());
      assertEquals(panel.preferredHeightFor(panel.getWidth()), panel.getHeight());

      Swing.named(panel, "a2ui.media.play", JButton.class).doClick(0);
      FakeEngine.Session session = engine.last();
      assertEquals(URI.create(VIDEO), session.source);
      assertEquals(0.5, session.startVolume);

      // A URL that is not safe never comes to the player.
      for (String unsafe :
          List.of("javascript:alert(1)", "file:///C:/secret.mp4", "//host/a.mp4")) {
        surface.update(component("video", "Video", "url", unsafe));
        assertEquals(Phase.EMPTY, panel.model().phase(), unsafe);
      }
      assertTrue(session.disposed());
      assertEquals(1, engine.sessions.size());

      // A safe URL that JavaFX cannot play gives the failure state, with no call of the engine.
      for (String notPlayable : List.of("data:video/mp4;base64,AAAA", "media/intro.mp4")) {
        surface.update(component("video", "Video", "url", notPlayable));
        assertEquals(Phase.FAILED, panel.model().phase(), notPlayable);
        assertEquals(PlayerModel.UNSUPPORTED_URL, panel.model().message());
        assertFalse(Swing.named(panel, "a2ui.media.open", JButton.class).isVisible());
      }
      assertEquals(1, engine.sessions.size());
      assertEquals(panel.preferredHeightFor(panel.getWidth()), panel.getHeight());
      assertEquals(List.of(), opened);
    });
  }

  @Test
  void theAudioComponentGivesItsDescriptionToThePlayer() {
    Swing.onEdt(() -> {
      Surface surface =
          new Surface(
              component("root", "Column", "children", List.of("audio")),
              component("audio", "AudioPlayer", "url", AUDIO, "description", "Episode 1"));
      PlayerPanel panel = surface.panel("audio");

      assertEquals("Episode 1", panel.getAccessibleContext().getAccessibleName());
      assertEquals(
          "Play: Episode 1",
          Swing.named(panel, "a2ui.media.play", JButton.class)
              .getAccessibleContext()
              .getAccessibleName());
      JLabel description = Swing.named(panel, "a2ui.media.description", JLabel.class);
      assertTrue(description.isVisible());
      assertEquals("Episode 1", description.getText());
      assertEquals(panel.preferredHeightFor(panel.getWidth()), panel.getHeight());
      Swing.snapshot(surface.view, "surface-audio");
    });
  }

  @Test
  void aComponentThatGoesAwayAndASurfaceThatLeavesItsWindowReleaseThePlayer() {
    Swing.onEdt(() -> {
      Surface surface =
          new Surface(
              component("root", "Column", "children", List.of("video", "audio")),
              component("video", "Video", "url", VIDEO),
              component("audio", "AudioPlayer", "url", AUDIO));
      Swing.named(surface.panel("video"), "a2ui.media.play", JButton.class).doClick(0);
      FakeEngine.Session video = engine.last();
      PlayerPanel audioPanel = surface.panel("audio");
      Swing.named(audioPanel, "a2ui.media.play", JButton.class).doClick(0);
      FakeEngine.Session audio = engine.last();

      surface.update(component("root", "Column", "children", List.of("audio")));
      assertTrue(video.disposed());
      assertFalse(audio.disposed());

      surface.view.removeNotify();
      assertTrue(audio.disposed());
      assertEquals(2, engine.sessions.size());
    });
  }
}
