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

import java.util.Map;

/**
 * The operation that the resolver does on the value at one location of a component. The schema of
 * the component gives the behavior of each location.
 */
sealed interface PropertyBehavior {
  /** The behavior of a value that the resolver does not change. */
  PropertyBehavior STATIC = new Static();

  /** The behavior of an action. */
  PropertyBehavior ACTION = new Action();

  /** The behavior of the ID of one child component. */
  PropertyBehavior CHILD = new Child();

  /** The behavior of a list of child IDs or of a list template. */
  PropertyBehavior CHILD_LIST = new ChildList();

  /** The resolver does not change the value. */
  record Static() implements PropertyBehavior {}

  /**
   * The value is a literal, a data binding or a function call.
   *
   * @param bool true if the resolver reads the value as a boolean
   */
  record Dynamic(boolean bool) implements PropertyBehavior {}

  /** The value is an action that the view invokes. */
  record Action() implements PropertyBehavior {}

  /** The value is the ID of one child component. */
  record Child() implements PropertyBehavior {}

  /** The value is a list of child IDs, or a template that a data list repeats. */
  record ChildList() implements PropertyBehavior {}

  /**
   * The value is an object. Each member has a behavior.
   *
   * @param properties the behavior of each member that the schema names
   * @param additional the behavior of each other member
   */
  record Structure(Map<String, PropertyBehavior> properties, PropertyBehavior additional)
      implements PropertyBehavior {
    /** Gets the behavior of a member. */
    PropertyBehavior member(String name) {
      return properties.getOrDefault(name, additional);
    }
  }

  /**
   * The value is a list. Each item has the same behavior.
   *
   * @param element the behavior of each item
   */
  record Sequence(PropertyBehavior element) implements PropertyBehavior {}
}
