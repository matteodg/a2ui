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

package com.google.a2ui.swing.testing;

import com.google.a2ui.swing.surface.MediaPlayer;
import com.google.a2ui.swing.surface.MediaView;
import java.awt.Dimension;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * A {@link MediaPlayer} for tests. It plays nothing. Each view is a label that shows the URL of its
 * media, and it records the calls that it gets.
 */
public final class TestMediaPlayer implements MediaPlayer {
  /** A view of this player. */
  public static final class View implements MediaView {
    /** The type of the media. */
    public final Kind kind;

    /** The Swing component: a label of 200 x 40 pixels. */
    public final JLabel label = new JLabel();

    /** Each value that {@link #setSource(Optional)} got. */
    public final List<Optional<URI>> sources = new ArrayList<>();

    /** Each value that {@link #setPoster(Optional)} got. */
    public final List<Optional<URI>> posters = new ArrayList<>();

    /** The number of the calls of {@link #dispose()}. */
    public int disposals;

    /** True to make each subsequent call of the view throw. */
    public boolean broken;

    View(Kind kind) {
      this.kind = kind;
      label.setPreferredSize(new Dimension(200, 40));
    }

    @Override
    public JComponent component() {
      return label;
    }

    @Override
    public void setSource(Optional<URI> url) {
      fail();
      sources.add(url);
      label.setText(url.map(URI::toString).orElse(""));
    }

    @Override
    public void setPoster(Optional<URI> url) {
      fail();
      posters.add(url);
    }

    @Override
    public void dispose() {
      disposals++;
      fail();
    }

    private void fail() {
      if (broken) {
        throw new IllegalStateException("The media view is broken.");
      }
    }
  }

  /** Each view that the player made, in the sequence of the creation. */
  public final List<View> views = new ArrayList<>();

  /** True to make {@link #createView(Kind)} throw. */
  public boolean broken;

  @Override
  public Optional<MediaView> createView(Kind kind) {
    if (broken) {
      throw new IllegalStateException("The media player is broken.");
    }
    View view = new View(kind);
    views.add(view);
    return Optional.of(view);
  }
}
