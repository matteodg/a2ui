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

import com.google.a2ui.core.catalog.ComponentApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The resolved properties of a node, by the property names of the schema of the component. Also the
 * resolved members of an object in such a property.
 *
 * <p>Each property has one of these values:
 *
 * <ul>
 *   <li>a {@link ResolvedBinding} for a property that a message can bind to data,
 *   <li>a {@link NodeAction} for an action,
 *   <li>a {@link ComponentNode}, or a list of them, for a reference to children,
 *   <li>the value as the agent wrote it in all other conditions. The resolver resolves an object
 *       and a list member by member.
 * </ul>
 *
 * <p>A component that has checks also has the properties {@link #IS_VALID}, {@link
 * #VALIDATION_ERRORS} and {@link #VALIDATION_RESULTS}. The resolved accessibility attributes of a
 * component are in the property {@link #ACCESSIBILITY}.
 *
 * <p>The properties are a snapshot and they cannot change. When a value changes, the node publishes
 * a new object. Two objects are equal if their maps are equal.
 *
 * @param <C> the type of the components of the catalogs
 */
public final class NodeProps<C extends ComponentApi> {
  /** The property that contains the resolved accessibility attributes of a component. */
  public static final String ACCESSIBILITY = "@accessibility";

  /** The property that tells if each check of the severity {@code error} passes. */
  public static final String IS_VALID = "isValid";

  /** The property that contains the messages of the failed checks of the severity {@code error}. */
  public static final String VALIDATION_ERRORS = "validationErrors";

  /** The property that contains each failed {@link ValidationResult}. */
  public static final String VALIDATION_RESULTS = "validationResults";

  private final Map<String, Object> values;

  /**
   * Makes the properties.
   *
   * @param values the resolved values by name. The object keeps the map and does not copy it.
   */
  NodeProps(Map<String, Object> values) {
    this.values = Collections.unmodifiableMap(values);
  }

  /**
   * Gets all the properties.
   *
   * @return the resolved values by name, in the sequence of the message. The map is read-only.
   */
  public Map<String, Object> asMap() {
    return values;
  }

  /**
   * Tells if the component has a property.
   *
   * @param name the name of the property
   * @return true if the properties contain the name
   */
  public boolean has(String name) {
    return values.containsKey(name);
  }

  /**
   * Gets the resolved value of a property in the form that the class description gives.
   *
   * @param name the name of the property
   * @return the value, or null if the component does not have the property
   */
  public Object get(String name) {
    return values.get(name);
  }

  /**
   * Gets a dynamic property.
   *
   * @param name the name of the property
   * @return the binding, or an empty result if the property is absent or is not dynamic
   */
  public Optional<ResolvedBinding> binding(String name) {
    return values.get(name) instanceof ResolvedBinding binding
        ? Optional.of(binding)
        : Optional.empty();
  }

  /**
   * Gets a dynamic property that a control can write.
   *
   * @param name the name of the property
   * @return the binding, or an empty result if the property is absent or if the message did not
   *     bind it to a data path
   */
  public Optional<WritableBinding> writableBinding(String name) {
    return binding(name).flatMap(ResolvedBinding::asWritable);
  }

  /**
   * Gets the plain value of a property. For a dynamic property, this is the value of its binding.
   *
   * @param name the name of the property
   * @return the value, or null if the property is absent or has no value
   */
  public Object value(String name) {
    Object value = values.get(name);
    return value instanceof ResolvedBinding binding ? binding.value() : value;
  }

  /**
   * Gets an action property.
   *
   * @param name the name of the property
   * @return the action, or an empty result if the property is absent or is not an action
   */
  public Optional<NodeAction> action(String name) {
    return values.get(name) instanceof NodeAction action ? Optional.of(action) : Optional.empty();
  }

  /**
   * Gets the node of a property that refers to one child.
   *
   * @param name the name of the property
   * @return the child, or an empty result if the property is absent or is not one child
   */
  public Optional<ComponentNode<C>> child(String name) {
    return node(values.get(name));
  }

  /**
   * Gets the nodes of a property that refers to a list of children or to a list template.
   *
   * @param name the name of the property
   * @return the children in their sequence. The list is read-only. It is empty if the property is
   *     absent or contains no children.
   */
  public List<ComponentNode<C>> children(String name) {
    if (!(values.get(name) instanceof List<?> list)) {
      return List.of();
    }
    List<ComponentNode<C>> nodes = new ArrayList<>(list.size());
    for (Object item : list) {
      node(item).ifPresent(nodes::add);
    }
    return Collections.unmodifiableList(nodes);
  }

  /**
   * Gets the resolved members of a property that is an object.
   *
   * @param name the name of the property
   * @return the members, or an empty result if the property is absent or is not an object
   */
  public Optional<NodeProps<C>> object(String name) {
    return members(values.get(name));
  }

  /**
   * Gets the resolved members of each object in a property that is a list of objects. An example is
   * the {@code tabs} of a {@code Tabs} component.
   *
   * @param name the name of the property
   * @return the members of each object, in the sequence of the list. The list is read-only. It does
   *     not contain the items that are not objects.
   */
  public List<NodeProps<C>> objects(String name) {
    if (!(values.get(name) instanceof List<?> list)) {
      return List.of();
    }
    List<NodeProps<C>> items = new ArrayList<>(list.size());
    for (Object item : list) {
      members(item).ifPresent(items::add);
    }
    return Collections.unmodifiableList(items);
  }

  /**
   * Gets the resolved accessibility attributes of the component.
   *
   * @return the attributes. The result has no properties if the component has no attributes.
   */
  public NodeProps<C> accessibility() {
    return object(ACCESSIBILITY).orElseGet(() -> new NodeProps<>(Map.of()));
  }

  /**
   * Tells if each check of the severity {@code error} passes.
   *
   * @return false if a check of that severity failed. The result is true for a component that has
   *     no checks.
   */
  public boolean isValid() {
    return !Boolean.FALSE.equals(values.get(IS_VALID));
  }

  /**
   * Gets the messages of the failed checks of the severity {@code error}.
   *
   * @return the messages in the sequence of the rules. The list is read-only.
   */
  public List<String> validationErrors() {
    return typedList(values.get(VALIDATION_ERRORS), String.class);
  }

  /**
   * Gets the result of each failed check, of each severity.
   *
   * @return the results in the sequence of the rules. The list is read-only.
   */
  public List<ValidationResult> validationResults() {
    return typedList(values.get(VALIDATION_RESULTS), ValidationResult.class);
  }

  @SuppressWarnings("unchecked")
  private Optional<ComponentNode<C>> node(Object value) {
    // A resolver puts only its own nodes in its properties. Thus they have the component type C.
    return value instanceof ComponentNode<?> node
        ? Optional.of((ComponentNode<C>) node)
        : Optional.empty();
  }

  @SuppressWarnings("unchecked")
  private Optional<NodeProps<C>> members(Object value) {
    // The resolver makes each object of the properties as a map with string keys.
    return value instanceof Map<?, ?> map
        ? Optional.of(new NodeProps<>((Map<String, Object>) map))
        : Optional.empty();
  }

  private static <T> List<T> typedList(Object value, Class<T> type) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<T> items = new ArrayList<>(list.size());
    for (Object item : list) {
      if (type.isInstance(item)) {
        items.add(type.cast(item));
      }
    }
    return Collections.unmodifiableList(items);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof NodeProps<?> props && props.values.equals(values);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(values);
  }

  @Override
  public String toString() {
    return "NodeProps(" + values + ")";
  }
}
