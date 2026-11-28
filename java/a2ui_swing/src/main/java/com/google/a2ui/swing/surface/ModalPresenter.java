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

import javax.swing.JComponent;

/**
 * Shows content above the surface until the user closes it. The {@code Modal} component of the
 * basic catalog uses this service.
 *
 * <p>The default service shows a modal {@code JDialog} above the window of the surface. An
 * application gives its own implementation to show the content in a different way, for example in a
 * glass pane. A test gives an implementation that needs no window.
 */
@FunctionalInterface
public interface ModalPresenter {
  /** The content that a presenter shows at this time. */
  @FunctionalInterface
  interface Handle {
    /**
     * Stops to show the content. A second call has no effect. The presenter does not run the {@code
     * onClosed} of the content for this call.
     */
    void close();
  }

  /**
   * Shows content. The method does not wait until the user closes the content.
   *
   * @param owner the component that opens the content. The presenter shows the content above the
   *     window of this component.
   * @param content the content to show
   * @param onClosed the work to do when the user closes the content. The presenter runs it one
   *     time, on the event dispatch thread.
   * @return the handle that stops to show the content
   */
  Handle present(JComponent owner, JComponent content, Runnable onClosed);
}
