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

/**
 * The Swing adapter of the A2UI SDK for Java.
 *
 * <p>This module shows the surfaces of the A2UI core with Swing components. It has no other
 * dependency than the core and the JDK.
 */
module com.google.a2ui.swing {
  requires transitive com.google.a2ui.core;
  requires transitive java.desktop;

  exports com.google.a2ui.swing.binding;
  exports com.google.a2ui.swing.catalog;
  exports com.google.a2ui.swing.catalog.basic;
  exports com.google.a2ui.swing.surface;
}
