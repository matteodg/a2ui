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

import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The {@link MediaEngine} that plays media with JavaFX.
 *
 * <p>The engine does all JavaFX work on the JavaFX application thread, with {@code
 * Platform.runLater}. It gives each event of a player to the event dispatch thread through a {@link
 * Relay}. No thread waits for the other thread. The one exception is {@link #start()}, which waits
 * one time for the start of the JavaFX toolkit.
 *
 * <p>A view asks for its screen, a {@code JFXPanel}, when the user starts a video for the first
 * time, and not when the surface makes the view.
 */
final class JavaFxEngine implements MediaEngine {
  /** The longest time that {@link #start()} waits for the JavaFX toolkit, in milliseconds. */
  private static final long START_TIMEOUT_MILLIS = 15_000;

  private static final System.Logger LOG = System.getLogger("com.google.a2ui.swing.javafx");

  private JavaFxEngine() {}

  /**
   * Starts the JavaFX toolkit, if it does not run, and makes the engine.
   *
   * <p>The method starts the toolkit on a helper thread and waits until the JavaFX application
   * thread runs a task. The helper thread needs no lock of AWT for that. Then the helper thread
   * makes one {@code JFXPanel} that no window shows. The first {@code JFXPanel} of a process
   * prepares the connection of JavaFX and Swing. On the event dispatch thread, that work starts a
   * second event loop, and code of the surface can then run in the middle of a different call. On
   * the helper thread, it starts no event loop. The method does not wait for that panel.
   *
   * <p>The JavaFX toolkit stays in operation until the application exits: the method sets {@code
   * Platform.setImplicitExit(false)}.
   *
   * @return the engine, or an empty result if JavaFX cannot operate on this computer: there is no
   *     display, the JavaFX libraries are absent, or the toolkit does not start
   */
  static Optional<MediaEngine> start() {
    if (GraphicsEnvironment.isHeadless()) {
      LOG.log(System.Logger.Level.INFO, "JavaFX media is not available: there is no display.");
      return Optional.empty();
    }
    AtomicReference<Throwable> failure = new AtomicReference<>();
    CountDownLatch started = new CountDownLatch(1);
    Thread helper =
        new Thread(
            () -> {
              try {
                CountDownLatch runs = new CountDownLatch(1);
                Platform.setImplicitExit(false);
                try {
                  Platform.startup(runs::countDown);
                } catch (IllegalStateException e) {
                  // The application started the toolkit before.
                  Platform.runLater(runs::countDown);
                }
                runs.await();
              } catch (InterruptedException | RuntimeException | LinkageError e) {
                failure.set(e);
              }
              started.countDown();
              if (failure.get() == null) {
                try {
                  new JFXPanel();
                } catch (RuntimeException | LinkageError e) {
                  LOG.log(System.Logger.Level.WARNING, "JavaFX did not make a panel.", e);
                }
              }
            },
            "a2ui-javafx-start");
    helper.setDaemon(true);
    helper.start();
    boolean inTime;
    try {
      inTime = started.await(START_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
    if (!inTime) {
      LOG.log(System.Logger.Level.WARNING, "JavaFX media is not available: no start in time.");
      return Optional.empty();
    }
    if (failure.get() != null) {
      LOG.log(System.Logger.Level.WARNING, "JavaFX media is not available.", failure.get());
      return Optional.empty();
    }
    return Optional.of(new JavaFxEngine());
  }

  @Override
  public JComponent createScreen() {
    JFXPanel panel = new JFXPanel();
    // The picture has no control of its own. The Swing controls of the view get the focus.
    panel.setFocusable(false);
    return panel;
  }

  @Override
  public Session open(
      URI source, JComponent screen, double volume, boolean muted, Listener listener) {
    FxSession session = new FxSession(screen instanceof JFXPanel panel ? panel : null, listener);
    session.onFx(() -> session.load(source, volume, muted));
    return session;
  }

  /** One JavaFX player. The fields of the player belong to the JavaFX application thread. */
  private static final class FxSession implements Session {
    private final JFXPanel panel;
    private final Relay relay;

    /** The player, or null before the load, after a failure of the load and after the release. */
    private MediaPlayer player;

    private boolean released;

    FxSession(JFXPanel panel, Listener listener) {
      this.panel = panel;
      this.relay = new Relay(SwingUtilities::invokeLater, listener);
    }

    /** Runs a task on the JavaFX application thread, later. A failure is a failure of the media. */
    void onFx(Runnable task) {
      try {
        Platform.runLater(() -> {
          try {
            task.run();
          } catch (RuntimeException | LinkageError e) {
            relay.failed(text(e));
          }
        });
      } catch (IllegalStateException e) {
        // The application stopped the JavaFX toolkit.
        relay.failed(text(e));
      }
    }

    private void onPlayer(Consumer<MediaPlayer> task) {
      onFx(() -> {
        if (player != null) {
          task.accept(player);
        }
      });
    }

    /** Makes the player. Call it on the JavaFX application thread. */
    void load(URI source, double volume, boolean muted) {
      if (released) {
        return;
      }
      Media media = new Media(source.toString());
      MediaPlayer made = new MediaPlayer(media);
      player = made;
      made.setAutoPlay(false);
      made.setVolume(volume);
      made.setMute(muted);
      media.setOnError(() -> relay.failed(text(media.getError())));
      made.setOnError(() -> relay.failed(text(made.getError())));
      made.setOnReady(() -> relay.ready(seconds(media.getDuration())));
      made.setOnPlaying(relay::playing);
      made.setOnPaused(relay::paused);
      made.setOnEndOfMedia(relay::ended);
      made.currentTimeProperty().addListener((property, before, now) -> relay.time(seconds(now)));
      if (panel != null) {
        MediaView view = new MediaView(made);
        view.setPreserveRatio(true);
        Scene scene = new Scene(new StackPane(view), Color.BLACK);
        view.fitWidthProperty().bind(scene.widthProperty());
        view.fitHeightProperty().bind(scene.heightProperty());
        panel.setScene(scene);
      }
    }

    @Override
    public void play() {
      onPlayer(MediaPlayer::play);
    }

    @Override
    public void pause() {
      onPlayer(MediaPlayer::pause);
    }

    @Override
    public void seek(double seconds) {
      onPlayer(made -> made.seek(Duration.seconds(seconds)));
    }

    @Override
    public void setVolume(double volume) {
      onPlayer(made -> made.setVolume(volume));
    }

    @Override
    public void setMuted(boolean muted) {
      onPlayer(made -> made.setMute(muted));
    }

    @Override
    public void dispose() {
      relay.close();
      onFx(() -> {
        released = true;
        MediaPlayer old = player;
        player = null;
        if (panel != null) {
          panel.setScene(null);
        }
        if (old != null) {
          old.stop();
          old.dispose();
        }
      });
    }

    private static double seconds(Duration duration) {
      return duration == null || duration.isUnknown() || duration.isIndefinite()
          ? Double.NaN
          : duration.toSeconds();
    }

    private static String text(Throwable failure) {
      if (failure == null) {
        return "";
      }
      String detail = failure.getMessage();
      if (failure instanceof MediaException media && media.getType() != null) {
        String type = media.getType().name();
        return detail == null || detail.isBlank() ? type : type + ": " + detail;
      }
      return detail == null || detail.isBlank() ? failure.getClass().getSimpleName() : detail;
    }
  }
}
