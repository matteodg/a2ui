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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.swing.testing.Edt;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class DefaultImageLoaderTest {
  private final DefaultImageLoader loader = new DefaultImageLoader();

  /** Makes the {@code data} URL of a PNG image of 3 x 2 pixels. */
  private static URI pngDataUrl() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB), "png", bytes);
    return URI.create(
        "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray()));
  }

  @Test
  void itLoadsADataUrlAndGivesTheImageOnTheEventDispatchThread() throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    List<Optional<Image>> results = new ArrayList<>();
    boolean[] onEdt = {false};

    loader.load(
        pngDataUrl(),
        image -> {
          onEdt[0] = EventQueue.isDispatchThread();
          results.add(image);
          done.countDown();
        });

    assertTrue(done.await(10, TimeUnit.SECONDS));
    assertTrue(onEdt[0]);
    assertEquals(1, results.size());
    assertEquals(3, results.get(0).orElseThrow().getWidth(null));
    assertEquals(2, results.get(0).orElseThrow().getHeight(null));
  }

  @Test
  void itGivesAnEmptyResultForDataThatIsNotAnImage() throws Exception {
    assertEquals(Optional.empty(), loader.read(URI.create("data:text/plain,hello")));
    assertEquals(Optional.empty(), loader.read(URI.create("data:image/png;base64")));
    assertEquals(Optional.empty(), loader.read(URI.create("data:image/png;base64,!!!")));
  }

  @Test
  void itGivesAnEmptyResultForASchemeThatItDoesNotRead() {
    assertEquals(Optional.empty(), loader.read(URI.create("file:///etc/passwd")));
    assertEquals(Optional.empty(), loader.read(URI.create("ftp://example.com/a.png")));
    assertEquals(Optional.empty(), loader.read(URI.create("relative/a.png")));
  }

  @Test
  void itReadsADataUrlWithPercentEncodedBytes() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "bmp", bytes);
    StringBuilder encoded = new StringBuilder("data:image/bmp,");
    for (byte b : bytes.toByteArray()) {
      encoded.append(String.format("%%%02X", b & 0xFF));
    }

    Optional<Image> image = loader.read(URI.create(encoded.toString()));

    assertEquals(1, image.orElseThrow().getWidth(null));
  }

  private static URI pngDataUrl(int width, int height) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
    return URI.create(
        "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray()));
  }

  @Test
  void itRefusesAnImageWithMorePixelsThanTheLimit() throws Exception {
    DefaultImageLoader small = new DefaultImageLoader(DefaultImageLoader.MAX_BYTES, 100);

    assertTrue(small.read(pngDataUrl(10, 10)).isPresent());
    assertEquals(Optional.empty(), small.read(pngDataUrl(101, 1)));
    assertEquals(Optional.empty(), small.read(pngDataUrl(11, 10)));
    // The file of a large image with one color is small. The limit is for the pixels.
    assertEquals(Optional.empty(), small.read(pngDataUrl(3000, 3000)));
  }

  @Test
  void itRefusesADataUrlWithMoreBytesThanTheLimit() throws Exception {
    URI url = pngDataUrl(40, 40);
    int bytes =
        Base64.getDecoder()
            .decode(url.toString().substring(url.toString().indexOf(',') + 1))
            .length;

    assertTrue(new DefaultImageLoader(bytes, 10_000).read(url).isPresent());
    assertEquals(Optional.empty(), new DefaultImageLoader(bytes - 1, 10_000).read(url));
    assertEquals(Optional.empty(), new DefaultImageLoader(10, 10_000).read(url));
  }

  @Test
  void aCancelledRequestGivesNoResult() throws Exception {
    List<Optional<Image>> results = new ArrayList<>();
    URI url = pngDataUrl();

    // The event dispatch thread is busy until the cancellation. Thus the result cannot arrive
    // before it.
    Edt.run(() -> {
      Subscription request = loader.load(url, results::add);
      request.cancel();
    });
    Thread.sleep(200);
    Edt.flush();

    assertEquals(List.of(), results);
  }
}
