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

package com.google.a2ui.core.basic_catalog.v1_0;

import java.net.URI;

/**
 * Opens a URL for the function {@code openUrl}, for example in the browser of the system.
 *
 * <p>The application supplies the implementation, because the core has no UI code.
 */
@FunctionalInterface
public interface UrlOpener {
  /**
   * Opens a URL.
   *
   * @param uri an absolute URL whose scheme is {@code http} or {@code https}
   */
  void open(URI uri);
}
