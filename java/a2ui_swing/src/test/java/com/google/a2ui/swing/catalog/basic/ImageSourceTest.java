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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.catalog.basic.ImageSource.State;
import com.google.a2ui.swing.surface.ImageLoader;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.LogCapture;
import com.google.a2ui.swing.testing.TestImageLoader;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ImageSourceTest {
  private static final String A = "https://example.com/a.png";
  private static final String B = "https://example.com/b.png";

  private final TestImageLoader loader = new TestImageLoader();
  private int changes;
  private final ImageSource source = new ImageSource(loader, () -> changes++);

  private static BufferedImage image(int width) {
    return TestImageLoader.pattern(width, 10);
  }

  @Test
  void theImageArrivesAfterTheRequest() {
    Edt.run(() -> {
      assertEquals(State.NONE, source.state());

      assertTrue(source.show(A));

      assertEquals(State.LOADING, source.state());
      assertEquals(Optional.of(URI.create(A)), source.url());
      assertEquals(Optional.empty(), source.picture());
      assertEquals(List.of(URI.create(A)), loader.requests);
      assertEquals(0, changes);

      loader.complete(A, image(30));

      assertEquals(State.LOADED, source.state());
      assertEquals(30, source.picture().orElseThrow().width());
      assertEquals(1, changes);
    });
  }

  @Test
  void aLoaderThatGivesNoImageGivesTheFailedState() {
    Edt.run(() -> {
      source.show(A);
      loader.fail(A);

      assertEquals(State.FAILED, source.state());
      assertEquals(Optional.empty(), source.picture());
      assertEquals(Optional.of(URI.create(A)), source.url());
      assertEquals(1, changes);
    });
  }

  @Test
  void theSameValueStartsNoSecondRequest() {
    Edt.run(() -> {
      assertTrue(source.show(A));
      assertFalse(source.show(A));
      loader.complete(A, image(30));
      assertFalse(source.show(A));

      assertEquals(1, loader.requests.size());
      assertEquals(State.LOADED, source.state());
    });
  }

  @Test
  void aNewValueCancelsTheRequestAndTheLateResultHasNoEffect() {
    Edt.run(() -> {
      source.show(A);
      assertTrue(source.show(B));

      assertEquals(List.of(URI.create(A)), loader.cancelled);
      assertEquals(State.LOADING, source.state());
      assertEquals(Optional.of(URI.create(B)), source.url());

      // This loader does not obey the cancellation.
      loader.completeCancelled(A, image(30));
      assertEquals(State.LOADING, source.state());
      assertEquals(0, changes);

      loader.complete(B, image(50));
      assertEquals(50, source.picture().orElseThrow().width());
      assertEquals(1, changes);
    });
  }

  @Test
  void aNewValueRemovesTheImageOfTheValueBeforeIt() {
    Edt.run(() -> {
      source.show(A);
      loader.complete(A, image(30));

      source.show(B);

      assertEquals(State.LOADING, source.state());
      assertEquals(Optional.empty(), source.picture());
      // The first request has its result. Thus there is nothing to cancel.
      assertEquals(List.of(), loader.cancelled);
    });
  }

  @Test
  void aValueThatIsNotASafeUrlStartsNoRequest() {
    Edt.run(() -> {
      for (Object value :
          new Object[] {
            null, "", "  ", "file:///etc/passwd", "javascript:alert(1)", "//host/a.png", 5L
          }) {
        source.show(value);

        assertEquals(State.NONE, source.state(), String.valueOf(value));
        assertEquals(Optional.empty(), source.url(), String.valueOf(value));
      }
      assertEquals(List.of(), loader.requests);

      source.show(A);
      source.show("ftp://example.com/a.png");

      assertEquals(State.NONE, source.state());
      assertEquals(List.of(URI.create(A)), loader.cancelled);
    });
  }

  @Test
  void theLoaderGetsTheUrlWithNoSpaceAtItsEnds() {
    Edt.run(() -> {
      source.show("  " + A + "\n");

      assertEquals(List.of(URI.create(A)), loader.requests);
    });
  }

  @Test
  void theDisposalCancelsTheRequestAndTheLateResultHasNoEffect() {
    Edt.run(() -> {
      source.show(A);

      source.dispose();
      source.dispose();

      assertEquals(List.of(URI.create(A)), loader.cancelled);
      assertEquals(State.NONE, source.state());

      loader.completeCancelled(A, image(30));
      assertEquals(State.NONE, source.state());
      assertEquals(Optional.empty(), source.picture());
      assertEquals(0, changes);

      assertFalse(source.show(B));
      assertEquals(1, loader.requests.size());
    });
  }

  @Test
  void theDisposalReleasesALoadedImage() {
    Edt.run(() -> {
      source.show(A);
      loader.complete(A, image(30));

      source.dispose();

      assertEquals(Optional.empty(), source.picture());
      assertEquals(List.of(), loader.cancelled);
    });
  }

  @Test
  void aLoaderThatAnswersDuringTheCallGivesTheImage() {
    Edt.run(() -> {
      ImageSource immediate = new ImageSource(TestImageLoader.patterns(), () -> changes++);

      immediate.show(A);

      assertEquals(State.LOADED, immediate.state());
      assertEquals(400, immediate.picture().orElseThrow().width());
      assertEquals(1, changes);

      immediate.show("https://example.com/fail.png");
      assertEquals(State.FAILED, immediate.state());
    });
  }

  @Test
  void aLoaderThatThrowsGivesTheFailedState() {
    Edt.run(() -> {
      ImageLoader broken =
          (uri, onResult) -> {
            throw new IllegalStateException("The loader is broken.");
          };
      ImageSource failing = new ImageSource(broken, () -> changes++);

      try (LogCapture log = new LogCapture()) {
        failing.show(A);

        assertEquals(State.FAILED, failing.state());
        assertEquals(1, log.messages.size());
      }
    });
  }

  @Test
  void aLoaderThatGivesANullResultGivesTheFailedState() {
    Edt.run(() -> {
      ImageLoader empty =
          (uri, onResult) -> {
            onResult.accept(null);
            return () -> {};
          };
      ImageSource failing = new ImageSource(empty, () -> changes++);

      failing.show(A);

      assertEquals(State.FAILED, failing.state());
    });
  }

  @Test
  void aResultFromADifferentThreadGoesToTheEventDispatchThread() throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    boolean[] onEdt = {false};
    ImageSource[] threaded = {null};
    ImageLoader offThread =
        (uri, onResult) -> {
          Thread.ofVirtual().start(() -> onResult.accept(Optional.of(image(20))));
          return () -> {};
        };
    Edt.run(() -> {
      threaded[0] =
          new ImageSource(
              offThread,
              () -> {
                onEdt[0] = java.awt.EventQueue.isDispatchThread();
                done.countDown();
              });
      threaded[0].show(A);
    });

    assertTrue(done.await(10, TimeUnit.SECONDS));
    assertTrue(onEdt[0]);
    Edt.run(() -> assertEquals(State.LOADED, threaded[0].state()));
  }
}
