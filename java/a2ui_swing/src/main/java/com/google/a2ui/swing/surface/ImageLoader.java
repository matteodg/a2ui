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

import com.google.a2ui.core.common.Subscription;
import java.awt.Image;
import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Loads the images of a surface. The {@code Image} component of the basic catalog uses this
 * service.
 *
 * <p>An application gives its own implementation to add a cache, to read more image formats or to
 * send credentials. The component examines each URL with {@link MediaUrls} before it calls the
 * loader. See the rules for media URLs in the implementation guide of the basic catalog. The loader
 * is responsible for the limits of what it reads, for example the size of an image.
 */
@FunctionalInterface
public interface ImageLoader {
  /**
   * Starts to load an image. The method does not wait for the image.
   *
   * @param uri the URL of the image. It is relative if the agent sent a relative path. A loader
   *     that has no base location gives an empty result for it.
   * @param onResult receives the image, or an empty result if the loader cannot load the image. The
   *     loader calls it one time, on the event dispatch thread, after this method returns.
   * @return the registration of the request. After the caller cancels it, the loader does not call
   *     {@code onResult}.
   */
  Subscription load(URI uri, Consumer<Optional<Image>> onResult);
}
