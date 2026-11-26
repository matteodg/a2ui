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

package com.google.a2ui.swing.testing;

import com.google.a2ui.swing.surface.A2uiSurface;
import javax.swing.JPanel;

/**
 * Shows a surface component with no window, for tests that run with no display. Use it on the event
 * dispatch thread.
 */
public final class Headless {
  private Headless() {}

  /**
   * Connects a surface component as a window does, and thus makes its views.
   *
   * <p>The component gets a parent that is not in a window. Thus Swing knows that the component is
   * not on the screen, and code that asks for a screen position gets the usual exception.
   *
   * @param surface the surface component
   * @return the surface component
   */
  public static A2uiSurface mount(A2uiSurface surface) {
    JPanel holder = new JPanel(null);
    holder.add(surface);
    surface.addNotify();
    return surface;
  }

  /**
   * Connects a surface component as a window does, and does its layout at a size.
   *
   * @param surface the surface component
   * @param width the width in pixels
   * @param height the height in pixels
   * @return the surface component
   */
  public static A2uiSurface mount(A2uiSurface surface, int width, int height) {
    mount(surface);
    surface.setSize(width, height);
    surface.validate();
    return surface;
  }
}
