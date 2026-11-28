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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MediaUrlsTest {
  /** Each safe value and the URL that the rule gives for it. */
  private static final String[][] SAFE = {
    {"https://example.com/image.png", "https://example.com/image.png"},
    {"http://example.com/video.mp4", "http://example.com/video.mp4"},
    {"  http://example.com/video.mp4  ", "http://example.com/video.mp4"},
    {"\u0000\t https://example.com/a.png\r\n\u00a0", "https://example.com/a.png"},
    {"HTTPS://Example.com/A.png", "HTTPS://Example.com/A.png"},
    {"https://example.com:8443/a.png?size=2#top", "https://example.com:8443/a.png?size=2#top"},
    {"https://user@example.com/a.png", "https://user@example.com/a.png"},
    {"https://[::1]/a.png", "https://[::1]/a.png"},
    {"https://example.com/a\tb.png", "https://example.com/ab.png"},
    {"https://example.com/my image.png", "https://example.com/my%20image.png"},
    {"https://example.com/a|b.png", "https://example.com/a%7Cb.png"},
    {"https://example.com/caf\u00e9.png", "https://example.com/caf\u00e9.png"},
    {"data:image/png;base64,AAAA", "data:image/png;base64,AAAA"},
    {"DATA:image/png;base64,AAAA", "DATA:image/png;base64,AAAA"},
    {"data:image/png;base64,AA\r\nAA", "data:image/png;base64,AAAA"},
    {"data:text/plain,a b", "data:text/plain,a%20b"},
    {"/assets/logo.png", "/assets/logo.png"},
    {"assets/logo.png", "assets/logo.png"},
    {"./logo.png", "./logo.png"},
    {"../logo.png", "../logo.png"},
    {"logo.png?size=2", "logo.png?size=2"},
    {"?size=2", "?size=2"},
    {"/a//b.png", "/a//b.png"},
    {"a\\b.png", "a%5Cb.png"},
    {"images/a:b.png", "images/a:b.png"},
  };

  /** Each value that is not safe. */
  private static final List<Object> NOT_SAFE =
      Arrays.asList(
          null,
          123L,
          true,
          Map.of("url", "https://example.com/a.png"),
          "",
          "   ",
          "\t\r\n",
          "javascript:alert(1)",
          "JavaScript:alert(1)",
          "java\tscript:alert(1)",
          " javascript:alert(1)",
          "vbscript:msgbox(1)",
          "file:///etc/passwd",
          "file:/etc/passwd",
          "FILE:///C:/Windows/win.ini",
          "C:\\Windows\\win.ini",
          "content://media/external/images/1",
          "blob:https://example.com/123",
          "ftp://example.com/a.png",
          "jar:file:/a.jar!/a.png",
          "about:blank",
          "a-very-long-scheme-name+1.0:content",
          "//evil.example.com/a.png",
          "\\\\evil.example.com\\a.png",
          "/\\evil.example.com/a.png",
          "\\/evil.example.com/a.png",
          "/\t/evil.example.com/a.png",
          "/\n/evil.example.com/a.png",
          "\t//evil.example.com/a.png",
          "http:evil.example.com/a.png",
          "https:evil.example.com/a.png",
          "https:/evil.example.com/a.png",
          "https\t:evil.example.com/a.png",
          "ht\ttps://evil.example.com/a.png",
          "https:\t//evil.example.com/a.png",
          "https:/\t/evil.example.com/a.png",
          "https:\\\\evil.example.com/a.png",
          "https://",
          "https:///a.png",
          "https://\\evil.example.com/a.png",
          "https://?a.png",
          "https://#a.png",
          "https://:80/a.png",
          "https://@/a.png",
          "https://example.com/a%zz.png");

  @Test
  void aSafeValueGivesItsUrl() {
    for (String[] row : SAFE) {
      assertEquals(Optional.of(URI.create(row[1])), MediaUrls.safe(row[0]), row[0]);
    }
  }

  @Test
  void aValueThatIsNotSafeGivesNoUrl() {
    for (Object value : NOT_SAFE) {
      assertEquals(Optional.empty(), MediaUrls.safe(value), String.valueOf(value));
    }
  }

  @Test
  void theUrlOfARelativePathIsRelative() {
    assertFalse(MediaUrls.safe("assets/logo.png").orElseThrow().isAbsolute());
    assertTrue(MediaUrls.safe("https://example.com/logo.png").orElseThrow().isAbsolute());
  }

  @Test
  void theRuleReadsOnlyTheStartOfALongDataUrl() {
    String data = "data:image/png;base64," + "A".repeat(4_000_000);

    long start = System.nanoTime();
    URI uri = MediaUrls.safe(data).orElseThrow();
    long millis = (System.nanoTime() - start) / 1_000_000;

    assertEquals("data", uri.getScheme());
    assertTrue(millis < 2_000, "the rule needed " + millis + " ms");
  }
}
