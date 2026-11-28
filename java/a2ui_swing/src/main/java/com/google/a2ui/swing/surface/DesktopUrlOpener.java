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

import com.google.a2ui.core.basic_catalog.v1_0.UrlOpener;
import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Objects;

/** The default {@link UrlOpener}. It opens a URL in the browser of the system. */
final class DesktopUrlOpener implements UrlOpener {
  @Override
  public void open(URI uri) {
    Objects.requireNonNull(uri, "uri");
    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
      throw new UnsupportedOperationException("This computer cannot open a URL in a browser.");
    }
    try {
      Desktop.getDesktop().browse(uri);
    } catch (IOException e) {
      throw new UncheckedIOException("The browser did not open " + uri + ".", e);
    }
  }
}
