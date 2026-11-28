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

package com.google.a2ui.samples.swing;

import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.surface.A2uiFallbacks;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.A2uiSurface;
import com.google.a2ui.swing.surface.A2uiSwing;
import java.awt.Dimension;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JSplitPane;

/**
 * The content of the gallery window: the list of the examples, the surface and the inspector, in
 * three columns.
 *
 * <p>The selection of an example makes a new {@link GallerySession} and gives it all the messages
 * of the example. The panel needs no window. Thus a test can use it with no display.
 *
 * <p>Use the panel only on the event dispatch thread.
 */
final class GalleryPanel {
  private final A2uiServices services;
  private final A2uiFallbacks fallbacks = Placeholders.fallbacks();
  private final ExampleListPanel list;
  private final SurfaceColumn surfaceColumn;
  private final InspectorPanel inspector = new InspectorPanel();
  private final JSplitPane columns;
  private Example example;
  private GallerySession session;

  /**
   * Makes the panel with no example selected.
   *
   * @param examples the examples of the list
   * @param services the services of the surfaces. The function {@code openUrl} uses their URL
   *     opener.
   */
  GalleryPanel(List<Example> examples, A2uiServices services) {
    A2uiSwing.requireEventDispatchThread();
    this.services = Objects.requireNonNull(services, "services");
    this.list = new ExampleListPanel(examples, this::open);
    this.surfaceColumn = new SurfaceColumn(this::reload, this::restart, this::step);

    // The split pane puts its divider at the preferred width of the surface column.
    surfaceColumn.component().setPreferredSize(new Dimension(600, 600));
    inspector.component().setPreferredSize(new Dimension(400, 600));
    JSplitPane right =
        new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT, surfaceColumn.component(), inspector.component());
    right.setResizeWeight(0.6);
    right.setContinuousLayout(true);
    right.setBorder(null);
    columns = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, list.component(), right);
    columns.setResizeWeight(0);
    columns.setContinuousLayout(true);
    columns.setDividerLocation(290);
  }

  /**
   * Gets the Swing component of the panel.
   *
   * @return the component
   */
  JComponent component() {
    return columns;
  }

  /**
   * Selects an example, as the user does in the list.
   *
   * @param id the ID of the example
   * @return false if the list has no example with this ID
   */
  boolean select(String id) {
    return list.select(id);
  }

  /** Shows a new example and processes all its messages. */
  private void open(Example selected) {
    inspector.clearLog();
    start(selected, true);
  }

  /** Processes all the messages of the selected example again, with a new processor. */
  void reload() {
    if (example != null) {
      start(example, true);
    }
  }

  /** Starts the selected example again with a new processor that got no message. */
  void restart() {
    if (example != null) {
      start(example, false);
    }
  }

  /** Processes the subsequent message of the selected example. */
  void step() {
    if (session != null && session.step()) {
      refresh();
    }
  }

  private void start(Example selected, boolean runAll) {
    close();
    example = selected;
    surfaceColumn.showExample(selected);
    if (selected.problem().isPresent()) {
      surfaceColumn.showProgress(0, 0, List.of(selected.problem().get()));
      surfaceColumn.showSurface(null, "The gallery cannot read this example.");
      inspector.showMessages(selected, 0);
      return;
    }
    session = new GallerySession(selected, services, inspector::appendLog, this::showSurface);
    showSurface(Optional.empty());
    if (runAll) {
      session.runAll();
    }
    refresh();
  }

  /** Removes the surface from the screen and disposes the processor of the session. */
  private void close() {
    inspector.followData(null);
    surfaceColumn.showSurface(null, "");
    if (session != null) {
      session.dispose();
      session = null;
    }
  }

  private void refresh() {
    surfaceColumn.showProgress(session.processed(), example.messages().size(), session.failures());
    inspector.showMessages(example, session.processed());
  }

  private void showSurface(Optional<SurfaceModel<SwingComponentImplementation>> surface) {
    if (surface.isPresent()) {
      surfaceColumn.showSurface(new A2uiSurface(surface.get(), fallbacks, services), "");
      inspector.followData(surface.get());
    } else {
      surfaceColumn.showSurface(
          null, "No surface. Use Step to process the subsequent message of the example.");
      inspector.followData(null);
    }
  }

  /**
   * Gets the example that the panel shows.
   *
   * @return the example, or an empty result
   */
  Optional<Example> example() {
    return Optional.ofNullable(example);
  }

  /**
   * Gets the session of the example that the panel shows.
   *
   * @return the session, or an empty result if no example is selected or the gallery cannot read
   *     the example
   */
  Optional<GallerySession> session() {
    return Optional.ofNullable(session);
  }

  /**
   * Gets the center column.
   *
   * @return the column of the surface
   */
  SurfaceColumn surfaceColumn() {
    return surfaceColumn;
  }

  /**
   * Gets the right column.
   *
   * @return the inspector
   */
  InspectorPanel inspector() {
    return inspector;
  }

  /** Disposes the processor of the session. Call this method when the window closes. */
  void dispose() {
    close();
  }
}
