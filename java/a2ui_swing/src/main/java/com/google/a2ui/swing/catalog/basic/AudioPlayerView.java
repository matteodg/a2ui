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

import com.google.a2ui.core.basic_catalog.v1_0.UrlOpener;
import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import com.google.a2ui.swing.surface.MediaPlayer;
import com.google.a2ui.swing.surface.MediaUrls;
import com.google.a2ui.swing.surface.MediaView;
import java.awt.event.ActionListener;
import java.net.URI;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JComponent;

/**
 * The view of the {@code AudioPlayer} component of the basic catalog.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code url}</td><td>The URL of the audio. The view examines it with {@code
 *       MediaUrls}.</td></tr>
 *   <tr><td>{@code description}</td><td>A title or a summary of the audio. The substitute shows it
 *       next to its button.</td></tr>
 * </table>
 *
 * <p>Swing cannot play audio from a URL. Thus the view asks the {@code mediaPlayer} service for a
 * {@code MediaView}. If the service gives one, the view shows its component with the width of the
 * container, and gives the URL to it. The description is then the accessible name of that
 * component, unless the agent gave a label.
 *
 * <p>The default service gives no view. Then the view shows a substitute: a button with a play
 * mark, and the description as text that breaks at the width of the container. When the user
 * operates the button, the {@code urlOpener} service opens the URL in an external application. The
 * view never opens a URL by itself. The user cannot operate the button if the URL is absent, is not
 * safe, or is not an absolute {@code http} or {@code https} URL. That is not an error of the
 * surface.
 *
 * <p>The Swing component of the view is a panel that gives the margin of a leaf. The accessibility
 * attributes go to the component of the media view, or to the button. The accessible name of the
 * button tells what it does and has the description, unless the agent gave a label. The component
 * has the URL that the view accepted as the client property {@link
 * SwingBasicCatalog#MEDIA_URL_PROPERTY}.
 */
final class AudioPlayerView implements SwingComponentView {
  /** The start of the accessible name, and the tool tip, of the button of the substitute. */
  static final String OPEN = "Open the audio in an external application";

  /** The side of the play mark of the button, in pixels. */
  private static final int MARK_SIDE = 20;

  /** The space between the button and the description, in pixels. */
  private static final int GAP = 8;

  private final LeafPanel panel = new LeafPanel();
  private final MediaView media;
  private final JButton button;
  private final WrappingLabel text;
  private final UrlOpener opener;
  private final ActionListener listener = event -> open();
  private String description = "";

  /** The URL that the button opens, or null. */
  private URI target;

  /**
   * Makes the view.
   *
   * @param context the context of the view. It gives the {@code mediaPlayer} and {@code urlOpener}
   *     services.
   */
  AudioPlayerView(ViewContext context) {
    opener = context.services().urlOpener();
    media = MediaSupport.createView(context, MediaPlayer.Kind.AUDIO).orElse(null);
    if (media != null) {
      button = null;
      text = null;
      panel.add(media.component());
      return;
    }
    button = new JButton(new MediaSupport.ShapeIcon("play", MARK_SIDE));
    button.addActionListener(listener);
    text = new WrappingLabel();
    FlexPanel row =
        new FlexPanel(
            new FlexLayout(
                FlexLayout.Axis.HORIZONTAL,
                FlexLayout.Justify.START,
                FlexLayout.Align.CENTER,
                GAP));
    row.add(button);
    // The text gets the width that the button does not use, and breaks there.
    row.add(text, 1.0);
    panel.add(row);
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public JComponent accessibleComponent() {
    return media != null ? media.component() : button;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    Optional<URI> url = MediaUrls.safe(props.value("url"));
    accessibleComponent().putClientProperty(SwingBasicCatalog.MEDIA_URL_PROPERTY, url.orElse(null));
    Object value = props.value("description");
    description = value == null ? "" : Coercion.toText(value).strip();
    if (media != null) {
      MediaSupport.guarded(() -> media.setSource(url));
    } else {
      target = MediaSupport.external(props.value("url")).orElse(null);
      button.setEnabled(target != null);
      button.setToolTipText(target == null ? null : OPEN);
      text.setText(description);
      text.setVisible(!description.isEmpty());
    }
    panel.revalidate();
    panel.repaint();
  }

  /** Gets the name for assistive tools: what the button does, and the description. */
  @Override
  public Optional<String> inferredAccessibleName() {
    if (media != null) {
      return description.isEmpty() ? Optional.empty() : Optional.of(description);
    }
    return Optional.of(description.isEmpty() ? OPEN : OPEN + ": " + description);
  }

  @Override
  public void dispose() {
    if (media != null) {
      MediaSupport.guarded(media::dispose);
    } else {
      button.removeActionListener(listener);
      button.setToolTipText(null);
      target = null;
    }
  }

  /** Opens the audio in an external application. The listener of the button calls it. */
  private void open() {
    if (target != null) {
      MediaSupport.open(opener, target);
    }
  }
}
