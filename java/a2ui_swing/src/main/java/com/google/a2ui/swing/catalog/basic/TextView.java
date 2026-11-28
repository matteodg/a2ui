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

import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.MarkdownRenderer;
import com.google.a2ui.swing.surface.MarkdownView;
import java.awt.Color;
import java.awt.Font;
import java.util.Objects;
import javax.swing.JComponent;

/**
 * The view of the {@code Text} component of the basic catalog.
 *
 * <p>The view shows the property {@code text} as Markdown, with the view of the {@code markdown}
 * service of the surface. The default service shows the Markdown subset of the protocol in a text
 * component that breaks its lines at its width. It shows HTML as plain text, and a link and an
 * image as their text only. If the service of an application fails, the view uses the default
 * service.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code text}</td><td>The Markdown text. A value that is not a string shows as the text
 *       of {@code Coercion.toText}.</td></tr>
 *   <tr><td>{@code variant}</td><td>{@code body}, the default, is the base text style. {@code
 *       caption} has 0.8 times the font size, and a color between the text color and the
 *       background color.</td></tr>
 * </table>
 *
 * <p>The base text style is the font and the foreground color of the Swing component above the
 * view. Thus the text in a primary button has the contrast color of the button, and an application
 * sets the text style of a full surface with {@code setFont} and {@code setForeground} on its
 * {@code A2uiSurface}. The view has the margin of a leaf. Text in a button has no margin.
 *
 * <p>The Swing component of the view is a panel. The accessibility attributes go to the component
 * of the Markdown view in it.
 */
final class TextView implements SwingComponentView {
  private static final System.Logger LOG = System.getLogger("com.google.a2ui.swing");

  /** The font size of a caption, as a multiple of the base font size. */
  private static final float CAPTION_SCALE = 0.8f;

  private final Panel panel = new Panel();
  private MarkdownView markdown;

  /** True if the Markdown view is from the default service. */
  private boolean usesDefault;

  private String text = "";
  private boolean caption;

  /**
   * Makes the view.
   *
   * @param context the context of the view. It gives the {@code markdown} service.
   */
  TextView(ViewContext context) {
    MarkdownRenderer renderer = context.services().markdown();
    usesDefault = renderer == A2uiServices.DEFAULT.markdown();
    try {
      markdown = Objects.requireNonNull(renderer.createView(), "createView returned null");
      Objects.requireNonNull(markdown.component(), "the Markdown view has no component");
    } catch (RuntimeException e) {
      if (usesDefault) {
        throw e;
      }
      LOG.log(System.Logger.Level.ERROR, "The Markdown service did not make a view.", e);
      markdown = A2uiServices.DEFAULT.markdown().createView();
      usesDefault = true;
    }
    panel.add(markdown.component());
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public JComponent accessibleComponent() {
    return markdown.component();
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    text = Coercion.toText(props.value("text"));
    caption = "caption".equals(props.value("variant"));
    // The component has its style before it gets the text. Thus it writes the text one time.
    panel.sync();
    try {
      markdown.setMarkdown(text);
    } catch (RuntimeException e) {
      if (usesDefault) {
        throw e;
      }
      LOG.log(System.Logger.Level.ERROR, "The Markdown view of the application failed.", e);
      useDefaultView();
    }
    panel.revalidate();
    panel.repaint();
  }

  @Override
  public void dispose() {
    markdown.dispose();
  }

  /** Replaces the Markdown view of the application with the view of the default service. */
  private void useDefaultView() {
    MarkdownView failed = markdown;
    panel.remove(failed.component());
    markdown = A2uiServices.DEFAULT.markdown().createView();
    usesDefault = true;
    panel.add(markdown.component());
    panel.sync();
    markdown.setMarkdown(text);
    try {
      failed.dispose();
    } catch (RuntimeException e) {
      LOG.log(System.Logger.Level.ERROR, "The Markdown view of the application failed.", e);
    }
  }

  /** The panel of the view. It gives the margin and the base text style to the Markdown view. */
  private final class Panel extends LeafPanel {
    private static final long serialVersionUID = 1L;

    @Override
    void sync() {
      super.sync();
      if (markdown == null) {
        return;
      }
      Font font = BasicStyle.fontOf(this);
      Color color = BasicStyle.foregroundOf(this);
      if (caption) {
        font = font.deriveFont(font.getSize2D() * CAPTION_SCALE);
        color = BasicStyle.muted(this);
      }
      JComponent target = markdown.component();
      if (!font.equals(target.getFont())) {
        target.setFont(font);
      }
      if (!color.equals(target.getForeground())) {
        target.setForeground(color);
      }
    }
  }
}
