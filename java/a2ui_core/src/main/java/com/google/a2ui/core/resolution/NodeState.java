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

package com.google.a2ui.core.resolution;

/** Tells if a node is resolved. If it is not, tells what the node is a placeholder for. */
public enum NodeState {
  /** The surface has the component, and its properties are resolved. */
  RESOLVED("resolved"),

  /**
   * The surface does not have the component at this time. If the component arrives, a resolved node
   * replaces this node.
   */
  PENDING("pending"),

  /** No catalog of the surface declares the type of the component. */
  UNKNOWN_TYPE("unknown-type"),

  /** The reference repeats a parent of the node, at the same data scope. */
  CYCLIC("cyclic");

  private final String wireValue;

  NodeState(String wireValue) {
    this.wireValue = wireValue;
  }

  /**
   * Gets the text that the specification uses for this state.
   *
   * @return the text of the state, for example {@code unknown-type}
   */
  public String wireValue() {
    return wireValue;
  }
}
