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

import com.google.a2ui.core.exceptions.A2uiDataError;
import com.google.a2ui.core.state.DataModel;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * The scope in which the core evaluates a data binding or a function call: a surface and a position
 * in the data of the surface.
 *
 * <p>A context cannot change. {@link #nested(String)} makes a new context.
 */
public final class DataContext {
  /** The value of {@link #index} outside a list template. */
  private static final int NO_INDEX = -1;

  private final SurfaceModel<?> surface;
  private final String path;
  private final int index;

  /**
   * Makes a context at the root of the data of a surface, outside a list template.
   *
   * @param surface the surface whose data and catalogs the context uses
   */
  public DataContext(SurfaceModel<?> surface) {
    this(surface, "/");
  }

  /**
   * Makes a context at a path of the data of a surface, outside a list template.
   *
   * @param surface the surface whose data and catalogs the context uses
   * @param path the data path from which the context reads relative paths. A path that does not
   *     start with {@code /} starts at the root.
   */
  public DataContext(SurfaceModel<?> surface, String path) {
    this(
        Objects.requireNonNull(surface, "surface"),
        DataModel.resolvePath(Objects.requireNonNull(path, "path"), null),
        NO_INDEX);
  }

  private DataContext(SurfaceModel<?> surface, String path, int index) {
    this.surface = surface;
    this.path = path;
    this.index = index;
  }

  /**
   * Gets the surface whose data and catalogs the context uses.
   *
   * @return the surface
   */
  public SurfaceModel<?> surface() {
    return surface;
  }

  /**
   * Gets the data path from which the context reads relative paths.
   *
   * @return the absolute path. It is {@code /} at the top of the data. In a list template, it is
   *     the path of one item of the list.
   */
  public String path() {
    return path;
  }

  /**
   * Gets the position of the current item in a list template.
   *
   * @return the position, where the first item is 0. The result is empty outside a list template.
   */
  public OptionalInt index() {
    return index == NO_INDEX ? OptionalInt.empty() : OptionalInt.of(index);
  }

  /**
   * Calculates the absolute form of a path.
   *
   * @param path the path to resolve. A path that does not start with {@code /} starts at the path
   *     of this context.
   * @return the absolute path
   */
  public String resolvePath(String path) {
    return DataModel.resolvePath(path, this.path);
  }

  /**
   * Makes a context for a position below this context. The new context has the list position of
   * this context.
   *
   * @param relativePath the path of the position. A path that does not start with {@code /} starts
   *     at the path of this context.
   * @return the new context
   */
  public DataContext nested(String relativePath) {
    return new DataContext(surface, resolvePath(relativePath), index);
  }

  /**
   * Makes a context for one item of a list that a template shows.
   *
   * @param relativePath the path of the item. A path that does not start with {@code /} starts at
   *     the path of this context.
   * @param index the position of the item in the list, where the first item is 0
   * @return the new context
   * @throws IllegalArgumentException if the position is negative
   */
  public DataContext nested(String relativePath, int index) {
    if (index < 0) {
      throw new IllegalArgumentException("The position of a list item must not be negative.");
    }
    return new DataContext(surface, resolvePath(relativePath), index);
  }

  /**
   * Writes a value in the data of the surface.
   *
   * @param path the path to write. A path that does not start with {@code /} starts at the path of
   *     this context.
   * @param value the JSON value to write, or null to remove the value
   * @throws A2uiDataError if the data model cannot write the value
   */
  public void set(String path, Object value) {
    surface.dataModel().set(resolvePath(path), value);
  }

  @Override
  public String toString() {
    return "DataContext(" + surface.id() + ", " + path + ")";
  }
}
