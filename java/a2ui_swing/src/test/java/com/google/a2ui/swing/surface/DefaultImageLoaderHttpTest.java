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

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The tests of the HTTP part of the default image loader. The server of the tests is a socket on
 * the loopback address of this computer. The tests do not use the network.
 */
class DefaultImageLoaderHttpTest {
  private ServerSocket server;
  private Thread thread;

  /** The response for each path: the status line with the headers, and the body. */
  private final Map<String, byte[][]> responses = new ConcurrentHashMap<>();

  /** The paths that the server got, in the sequence of the requests. */
  private final List<String> requests = Collections.synchronizedList(new ArrayList<>());

  @BeforeEach
  void startTheServer() throws IOException {
    server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
    thread =
        Thread.ofVirtual()
            .start(() -> {
              while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                  answer(socket);
                } catch (IOException e) {
                  // The test closed the server, or the loader closed the connection.
                }
              }
            });
  }

  @AfterEach
  void stopTheServer() throws Exception {
    server.close();
    thread.join(5_000);
  }

  private void answer(Socket socket) throws IOException {
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    String request = reader.readLine();
    if (request == null) {
      return;
    }
    for (String line = reader.readLine(); line != null && !line.isEmpty(); ) {
      line = reader.readLine();
    }
    String path = request.split(" ")[1];
    requests.add(path);
    byte[][] response =
        responses.getOrDefault(path, new byte[][] {head("404 Not Found", 0), new byte[0]});
    OutputStream out = socket.getOutputStream();
    out.write(response[0]);
    out.write(response[1]);
    out.flush();
  }

  private static byte[] head(String status, int length, String... headers) {
    StringBuilder head = new StringBuilder("HTTP/1.1 " + status + "\r\n");
    head.append("Content-Length: ").append(length).append("\r\n");
    head.append("Connection: close\r\n");
    for (String header : headers) {
      head.append(header).append("\r\n");
    }
    return head.append("\r\n").toString().getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] png(int width, int height) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
    return bytes.toByteArray();
  }

  private void serve(String path, String status, byte[] body, String... headers) {
    responses.put(path, new byte[][] {head(status, body.length, headers), body});
  }

  private void redirect(String path, String location) {
    serve(path, "302 Found", new byte[0], "Location: " + location);
  }

  private URI url(String path) {
    return URI.create("http://127.0.0.1:" + server.getLocalPort() + path);
  }

  @Test
  void itReadsAnImageFromAServer() throws IOException {
    serve("/a.png", "200 OK", png(5, 4), "Content-Type: image/png");

    Optional<Image> image = new DefaultImageLoader().read(url("/a.png"));

    assertEquals(5, image.orElseThrow().getWidth(null));
    assertEquals(4, image.orElseThrow().getHeight(null));
    assertEquals(List.of("/a.png"), requests);
  }

  @Test
  void itFollowsARedirectionToAnHttpUrl() throws IOException {
    serve("/final.png", "200 OK", png(2, 2));
    redirect("/relative", "final.png");
    redirect("/absolute", url("/relative").toString());

    assertTrue(new DefaultImageLoader().read(url("/absolute")).isPresent());
    assertEquals(List.of("/absolute", "/relative", "/final.png"), requests);
  }

  @Test
  void itDoesNotFollowARedirectionToAUrlThatIsNotSafe() throws IOException {
    Path local = Files.createTempFile("a2ui-image", ".png");
    try {
      Files.write(local, png(2, 2));
      redirect("/file", local.toUri().toString());
      redirect("/data", "data:image/png;base64,AAAA");
      redirect("/other-host", "//example.invalid/a.png");
      redirect("/script", "javascript:alert(1)");
      serve("/no-location", "302 Found", new byte[0]);

      for (String path : List.of("/file", "/data", "/other-host", "/script", "/no-location")) {
        assertEquals(Optional.empty(), new DefaultImageLoader().read(url(path)), path);
      }
      assertEquals(List.of("/file", "/data", "/other-host", "/script", "/no-location"), requests);
    } finally {
      Files.delete(local);
    }
  }

  @Test
  void itStopsAfterTooManyRedirections() {
    redirect("/loop", "/loop");

    assertEquals(Optional.empty(), new DefaultImageLoader().read(url("/loop")));
    assertEquals(DefaultImageLoader.MAX_REDIRECTS + 1, requests.size());
  }

  @Test
  void itAcceptsOnlyTheStatus200() throws IOException {
    serve("/missing.png", "404 Not Found", png(2, 2));
    serve("/error.png", "500 Internal Server Error", png(2, 2));
    serve("/partial.png", "206 Partial Content", png(2, 2));

    for (String path : List.of("/missing.png", "/error.png", "/partial.png", "/unknown.png")) {
      assertEquals(Optional.empty(), new DefaultImageLoader().read(url(path)), path);
    }
  }

  @Test
  void itRefusesDataThatIsLargerThanTheLimit() throws IOException {
    byte[] image = png(64, 64);
    serve("/a.png", "200 OK", image);
    // This response tells no length. Thus the loader finds the limit while it reads.
    responses.put(
        "/no-length.png",
        new byte[][] {
          "HTTP/1.0 200 OK\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8), image
        });

    assertTrue(new DefaultImageLoader(image.length, 10_000).read(url("/a.png")).isPresent());
    assertTrue(
        new DefaultImageLoader(image.length, 10_000).read(url("/no-length.png")).isPresent());
    assertEquals(
        Optional.empty(), new DefaultImageLoader(image.length - 1, 10_000).read(url("/a.png")));
    assertEquals(
        Optional.empty(),
        new DefaultImageLoader(image.length - 1, 10_000).read(url("/no-length.png")));
  }

  @Test
  void itGivesAnEmptyResultForDataThatIsNotAnImageAndForAServerThatIsNotThere() throws Exception {
    serve("/text", "200 OK", "hello".getBytes(StandardCharsets.UTF_8));

    assertEquals(Optional.empty(), new DefaultImageLoader().read(url("/text")));

    URI closed = url("/a.png");
    stopTheServer();
    assertEquals(Optional.empty(), new DefaultImageLoader().read(closed));
  }
}
