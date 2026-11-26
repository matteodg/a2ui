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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/**
 * The safety rule for the media URLs of a surface: the {@code url} of the {@code Image}, {@code
 * Video} and {@code AudioPlayer} components, and the {@code posterUrl} of {@code Video}.
 *
 * <p>These URLs come from the agent. The implementation guide of the basic catalog specifies one
 * rule for each renderer. Thus a surface loads the same media on each platform, and a URL cannot
 * read a local resource. The rule is this:
 *
 * <ol>
 *   <li>White space and control characters at the two ends are not a part of the URL. An empty URL
 *       is not safe.
 *   <li>A URL is safe only if its scheme is {@code http}, {@code https} or {@code data}, or if it
 *       has no scheme. A URL with no scheme is a relative path.
 *   <li>An {@code http} or {@code https} URL must start with the scheme, {@code ://} and a host.
 *       Thus {@code https:host} is not safe.
 *   <li>A relative path must not start with two slashes, because {@code //host} is a different
 *       host. A backslash counts as a slash.
 *   <li>The rule ignores each tab, line feed and carriage return when it reads the scheme and the
 *       first slashes, as a browser does. Thus {@code ht<tab>tps://host} and {@code /<tab>/host}
 *       are not safe.
 * </ol>
 *
 * <p>The views of the basic catalog show nothing for a URL that is not safe. That is not an error
 * of the surface. A custom component that loads media from a URL of the agent uses this class too.
 */
public final class MediaUrls {
  private static final String SLASHES = "/\\";

  private MediaUrls() {}

  /**
   * Examines the value of a media URL property.
   *
   * <p>The method reads only the start of the text to make the decision. Then it makes the {@code
   * URI}. It removes each tab, line feed and carriage return, and it writes each character that a
   * {@code URI} cannot contain, for example a space, as a percent escape.
   *
   * @param value the resolved value of the property
   * @return the URL, or an empty result if the value is not a text or is not safe. The URL of a
   *     relative path is relative. An application that has a base location resolves it. The default
   *     services load nothing for it.
   */
  public static Optional<URI> safe(Object value) {
    if (!(value instanceof String text)) {
      return Optional.empty();
    }
    int start = 0;
    int end = text.length();
    while (start < end && isPadding(text.charAt(start))) {
      start++;
    }
    while (end > start && isPadding(text.charAt(end - 1))) {
      end--;
    }
    if (start == end || startsWithTwoSlashes(text, start, end)) {
      return Optional.empty();
    }
    String scheme = schemeOf(text, start, end);
    if (scheme != null) {
      switch (scheme) {
        case "http", "https" -> {
          if (!hasHostAfterScheme(text, start, end, scheme)) {
            return Optional.empty();
          }
        }
        case "data" -> {}
        default -> {
          return Optional.empty();
        }
      }
    }
    return parse(withoutLineCharacters(text.substring(start, end)), scheme);
  }

  /** Tells if a character at an end of the text is white space or a control character. */
  private static boolean isPadding(char c) {
    return c <= ' '
        || (c >= 0x7f && c <= 0xa0)
        || c == 0xfeff
        || Character.isWhitespace(c)
        || Character.isSpaceChar(c);
  }

  /** Tells if a browser drops the character from a URL before it reads the URL. */
  private static boolean isLineCharacter(char c) {
    return c == '\t' || c == '\n' || c == '\r';
  }

  private static boolean startsWithTwoSlashes(String text, int start, int end) {
    int slashes = 0;
    for (int i = start; i < end && slashes < 2; i++) {
      char c = text.charAt(i);
      if (isLineCharacter(c)) {
        continue;
      }
      if (SLASHES.indexOf(c) < 0) {
        return false;
      }
      slashes++;
    }
    return slashes == 2;
  }

  /**
   * Reads the scheme as a browser does: without the tabs, the line feeds and the carriage returns.
   *
   * @return the scheme in lower case, or null if the text has no scheme
   */
  private static String schemeOf(String text, int start, int end) {
    StringBuilder scheme = new StringBuilder();
    for (int i = start; i < end; i++) {
      char c = text.charAt(i);
      if (isLineCharacter(c)) {
        continue;
      }
      if (c == ':') {
        return scheme.isEmpty() ? null : scheme.toString().toLowerCase(Locale.ROOT);
      }
      boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
      boolean other = (c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.';
      if (!letter && (scheme.isEmpty() || !other)) {
        return null;
      }
      scheme.append(c);
    }
    return null;
  }

  /** Tells if the text itself starts with the scheme, {@code ://} and one character of a host. */
  private static boolean hasHostAfterScheme(String text, int start, int end, String scheme) {
    String prefix = scheme + "://";
    int host = start + prefix.length();
    return host < end
        && text.regionMatches(true, start, prefix, 0, prefix.length())
        && "/\\?#:@".indexOf(text.charAt(host)) < 0
        && !isLineCharacter(text.charAt(host));
  }

  private static String withoutLineCharacters(String text) {
    if (text.indexOf('\t') < 0 && text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
      return text;
    }
    StringBuilder kept = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      if (!isLineCharacter(text.charAt(i))) {
        kept.append(text.charAt(i));
      }
    }
    return kept.toString();
  }

  /** Makes the URI, and makes sure that the URI has the scheme that the rule accepted. */
  private static Optional<URI> parse(String text, String scheme) {
    URI uri;
    try {
      uri = new URI(text);
    } catch (URISyntaxException first) {
      try {
        uri = new URI(escaped(text));
      } catch (URISyntaxException second) {
        return Optional.empty();
      }
    }
    String parsed = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
    if (scheme == null ? parsed != null : !scheme.equals(parsed)) {
      return Optional.empty();
    }
    if (scheme == null && uri.getRawAuthority() != null) {
      return Optional.empty();
    }
    if (scheme != null && !scheme.equals("data")) {
      String authority = uri.getRawAuthority();
      if (authority == null || authority.isEmpty()) {
        return Optional.empty();
      }
    }
    return Optional.of(uri);
  }

  /** Writes each character that a URI cannot contain as a percent escape. */
  private static String escaped(String text) {
    StringBuilder escaped = new StringBuilder(text.length() + 16);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c <= ' ' || c == 0x7f || "\"<>\\^`{|}".indexOf(c) >= 0) {
        escaped
            .append('%')
            .append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
            .append(Character.toUpperCase(Character.forDigit(c & 15, 16)));
      } else {
        escaped.append(c);
      }
    }
    return escaped.toString();
  }
}
