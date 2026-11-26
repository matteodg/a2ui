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

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.internal.Json;
import com.google.a2ui.core.schema.v1_0.SchemaDocuments;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Reads the behavior of each property of the components of one catalog from their schemas.
 *
 * <p>The classification uses the types that a schema refers to. It never uses the name or the value
 * of a property:
 *
 * <ul>
 *   <li>A location of the common type {@code Action} is an action.
 *   <li>A location of the common type {@code ComponentId}, {@code Child} or {@code ChildList}
 *       contains children.
 *   <li>A location whose type permits a {@code DataBinding} or a {@code FunctionCall} is dynamic.
 *       It is a boolean if each literal that the type permits is a boolean.
 * </ul>
 *
 * <p>This class is not thread-safe.
 */
final class PropertyBehaviors {
  private static final int MAX_DEPTH = 32;
  private static final String DEFINITIONS_PREFIX = "#/$defs/";
  private static final List<String> UNIONS = List.of("oneOf", "anyOf");
  private static final PropertyBehavior.Structure EMPTY =
      new PropertyBehavior.Structure(Map.of(), PropertyBehavior.STATIC);

  /**
   * The behavior of the accessibility attributes. The common types of the protocol define them, and
   * the validation schema of a catalog does not contain their definition.
   */
  private static final class Common {
    private static final PropertyBehavior ACCESSIBILITY =
        new PropertyBehaviors(SchemaDocuments::commonTypes)
            .of(Map.of("$ref", DEFINITIONS_PREFIX + "AccessibilityAttributes"), 0);
  }

  private final Supplier<Map<String, Object>> source;
  private final Map<String, PropertyBehavior.Structure> components = new HashMap<>();

  /** The document that the references of the schemas point into, or null before the first use. */
  private Map<String, Object> document;

  /**
   * Makes the reader for one catalog.
   *
   * @param catalog the catalog whose component schemas give the behaviors
   */
  PropertyBehaviors(Catalog<?, ?> catalog) {
    this(Objects.requireNonNull(catalog, "catalog")::validationSchema);
  }

  private PropertyBehaviors(Supplier<Map<String, Object>> source) {
    this.source = source;
  }

  /** Gets the behavior of the {@code @accessibility} attributes that each component can have. */
  static PropertyBehavior accessibility() {
    return Common.ACCESSIBILITY;
  }

  /**
   * Gets the behavior of the properties of a component type.
   *
   * @param type the name of the component type
   * @return the behaviors. If the catalog does not define the type, each property is static.
   */
  PropertyBehavior.Structure component(String type) {
    return components.computeIfAbsent(
        type,
        name -> {
          Object schema = resolve("#/components/" + Json.escapePointer(name));
          return of(schema, 0) instanceof PropertyBehavior.Structure structure ? structure : EMPTY;
        });
  }

  private PropertyBehavior of(Object schema, int depth) {
    if (!(schema instanceof Map<?, ?> map) || depth > MAX_DEPTH) {
      return PropertyBehavior.STATIC;
    }
    PropertyBehavior behavior = PropertyBehavior.STATIC;
    if (map.get("$ref") instanceof String ref) {
      String name =
          ref.startsWith(DEFINITIONS_PREFIX) ? ref.substring(DEFINITIONS_PREFIX.length()) : "";
      behavior =
          switch (name) {
            case "DataBinding", "FunctionCall" -> new PropertyBehavior.Dynamic(false);
            case "Action" -> PropertyBehavior.ACTION;
            case "ComponentId", "Child" -> PropertyBehavior.CHILD;
            case "ChildList" -> PropertyBehavior.CHILD_LIST;
            default -> of(resolve(ref), depth + 1);
          };
    }
    Object properties = map.get("properties");
    Object additional = map.get("additionalProperties");
    if (properties instanceof Map<?, ?> || additional instanceof Map<?, ?>) {
      Map<String, PropertyBehavior> members = new LinkedHashMap<>();
      if (properties instanceof Map<?, ?> named) {
        named.forEach((name, member) -> members.put(String.valueOf(name), of(member, depth + 1)));
      }
      behavior =
          merge(behavior, new PropertyBehavior.Structure(members, of(additional, depth + 1)));
    }
    if (map.get("items") != null) {
      behavior = merge(behavior, new PropertyBehavior.Sequence(of(map.get("items"), depth + 1)));
    }
    if (map.get("allOf") instanceof List<?> parts) {
      for (Object part : parts) {
        behavior = merge(behavior, of(part, depth + 1));
      }
    }
    for (String union : UNIONS) {
      if (map.get(union) instanceof List<?> branches) {
        behavior = merge(behavior, ofUnion(branches, depth));
      }
    }
    return behavior;
  }

  /**
   * Gets the behavior of a location that has one of some types. If the location is dynamic and each
   * literal type is {@code boolean}, the resolver reads the value as a boolean.
   */
  private PropertyBehavior ofUnion(List<?> branches, int depth) {
    PropertyBehavior behavior = PropertyBehavior.STATIC;
    int literals = 0;
    int booleans = 0;
    for (Object branch : branches) {
      PropertyBehavior member = of(branch, depth + 1);
      behavior = merge(behavior, member);
      Object type = branch instanceof Map<?, ?> map ? map.get("type") : null;
      if (!(member instanceof PropertyBehavior.Dynamic) && !"null".equals(type)) {
        literals++;
        if ("boolean".equals(type)) {
          booleans++;
        }
      }
    }
    if (behavior instanceof PropertyBehavior.Dynamic && literals > 0 && literals == booleans) {
      return new PropertyBehavior.Dynamic(true);
    }
    return behavior;
  }

  /**
   * Gets the behavior of a location that two schemas describe. A location that can contain a
   * binding is dynamic, and its other types are not important. The method merges two objects or two
   * lists member by member.
   */
  private static PropertyBehavior merge(PropertyBehavior a, PropertyBehavior b) {
    if (a.equals(b)) {
      return a;
    }
    if (a instanceof PropertyBehavior.Structure x && b instanceof PropertyBehavior.Structure y) {
      Map<String, PropertyBehavior> members = new LinkedHashMap<>(x.properties());
      y.properties()
          .forEach((name, member) -> members.merge(name, member, PropertyBehaviors::merge));
      return new PropertyBehavior.Structure(members, merge(x.additional(), y.additional()));
    }
    if (a instanceof PropertyBehavior.Sequence x && b instanceof PropertyBehavior.Sequence y) {
      return new PropertyBehavior.Sequence(merge(x.element(), y.element()));
    }
    return rank(b) > rank(a) ? b : a;
  }

  private static int rank(PropertyBehavior behavior) {
    return switch (behavior) {
      case PropertyBehavior.Static _ -> 0;
      case PropertyBehavior.Structure _ -> 1;
      case PropertyBehavior.Sequence _ -> 1;
      case PropertyBehavior.Child _ -> 2;
      case PropertyBehavior.ChildList _ -> 2;
      case PropertyBehavior.Action _ -> 3;
      case PropertyBehavior.Dynamic dynamic -> dynamic.bool() ? 5 : 4;
    };
  }

  /** Gets the value that a local reference names in the document, or null. */
  private Object resolve(String ref) {
    if (!ref.startsWith("#/")) {
      return null;
    }
    if (document == null) {
      document = source.get();
    }
    Object current = document;
    for (String raw : ref.substring(2).split("/", -1)) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = map.get(Json.unescapePointer(raw));
    }
    return current;
  }
}
