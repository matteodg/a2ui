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

import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.exceptions.A2uiDataError;
import com.google.a2ui.core.state.DataModel;
import java.util.Objects;

/**
 * The binding of a property that the message bound to a data path. A control uses it to write the
 * input of the user into the data.
 *
 * <p>Two writable bindings are equal if they have equal values and equal paths.
 */
public final class WritableBinding extends ResolvedBinding {
  private final String path;
  private final DataModel dataModel;
  private final boolean bool;

  /**
   * Makes a binding.
   *
   * @param value the resolved value, or null
   * @param path the absolute data path of the property
   * @param dataModel the data model that gets the writes
   * @param bool true if the property is a boolean. Then {@link #set(Object)} writes a boolean.
   */
  WritableBinding(Object value, String path, DataModel dataModel, boolean bool) {
    super(value);
    this.path = Objects.requireNonNull(path, "path");
    this.dataModel = Objects.requireNonNull(dataModel, "dataModel");
    this.bool = bool;
  }

  /**
   * Gets the data path of the property.
   *
   * @return the absolute path
   */
  public String path() {
    return path;
  }

  /**
   * Writes a value at the data path of the property. Then each node that reads the path publishes
   * new properties.
   *
   * <p>If the property is a boolean, the binding writes the boolean form of the value. See {@link
   * Coercion#toBoolean(Object)}.
   *
   * @param value the JSON value to write, or null to remove the value
   * @throws A2uiDataError if the data model cannot write the value
   */
  public void set(Object value) {
    dataModel.set(path, bool ? Coercion.toBoolean(value) : value);
  }

  @Override
  public boolean equals(Object other) {
    return super.equals(other) && ((WritableBinding) other).path.equals(path);
  }

  @Override
  public int hashCode() {
    return 31 * path.hashCode() + super.hashCode();
  }

  @Override
  public String toString() {
    return "WritableBinding(" + path + " = " + value() + ")";
  }
}
