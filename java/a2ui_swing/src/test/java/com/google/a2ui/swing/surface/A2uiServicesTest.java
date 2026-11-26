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

package com.google.a2ui.swing.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.UrlOpener;
import com.google.a2ui.swing.testing.Edt;
import java.util.Optional;
import javax.swing.JEditorPane;
import org.junit.jupiter.api.Test;

class A2uiServicesTest {
  @Test
  void theDefaultServicesHaveEachService() {
    A2uiServices services = A2uiServices.DEFAULT;

    assertSame(MediaPlayer.NONE, services.mediaPlayer());
    assertEquals(Optional.empty(), services.mediaPlayer().createView(MediaPlayer.Kind.VIDEO));
    assertEquals(Optional.empty(), services.mediaPlayer().createView(MediaPlayer.Kind.AUDIO));
    assertTrue(services.modalPresenter() instanceof DialogModalPresenter);
    assertTrue(services.urlOpener() instanceof DesktopUrlOpener);
    assertTrue(services.imageLoader() instanceof DefaultImageLoader);
  }

  @Test
  void eachWithMethodReplacesOneServiceAndKeepsTheOthers() {
    MarkdownRenderer markdown = HtmlMarkdownView::new;
    ImageLoader loader = (uri, onResult) -> () -> {};
    MediaPlayer player = kind -> Optional.empty();
    ModalPresenter presenter = (owner, content, onClosed) -> () -> {};
    UrlOpener opener = uri -> {};

    A2uiServices services =
        A2uiServices.DEFAULT
            .withMarkdown(markdown)
            .withImageLoader(loader)
            .withMediaPlayer(player)
            .withModalPresenter(presenter)
            .withUrlOpener(opener);

    assertEquals(new A2uiServices(markdown, loader, player, presenter, opener), services);
    assertSame(
        A2uiServices.DEFAULT.imageLoader(),
        A2uiServices.DEFAULT.withMarkdown(markdown).imageLoader());
  }

  @Test
  void aServiceCannotBeNull() {
    assertThrows(NullPointerException.class, () -> A2uiServices.DEFAULT.withUrlOpener(null));
  }

  @Test
  void theDefaultMarkdownServiceMakesAViewOfTheMarkdownSubsetForEachCall() {
    Edt.run(() -> {
      MarkdownView view = A2uiServices.DEFAULT.markdown().createView();
      MarkdownView second = A2uiServices.DEFAULT.markdown().createView();

      view.setMarkdown("This is **bold** text.");

      JEditorPane pane = (JEditorPane) view.component();
      String shown = pane.getDocument().getText(0, pane.getDocument().getLength());
      assertEquals("This is bold text.", shown.strip());
      assertFalse(pane.isEditable());
      assertNotSame(view.component(), second.component());
      assertSame(view.component(), view.component());
    });
  }
}
