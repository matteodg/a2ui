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

package com.google.a2ui.core.state;

import com.google.a2ui.core.common.EventListener;
import com.google.a2ui.core.common.Reactive;
import com.google.a2ui.core.common.Signal;
import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.core.exceptions.A2uiDataError;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * The observable store of the application data of one surface.
 *
 * <p>The values are JSON values in plain JVM types: {@code Map<String, Object>}, {@code
 * List<Object>}, {@code String}, {@code Long}, {@code Double}, {@code Boolean} and {@code null}.
 *
 * <p>The paths are JSON Pointers (RFC 6901). The model reads a path with no leading slash from the
 * root, and it ignores empty segments. Thus {@code /a/b}, {@code a/b} and {@code //a/b/} are the
 * same value.
 *
 * <p>The model owns its data. {@link #set(String, Object)} makes a copy of the given value, and
 * {@link #get(String)} returns a read-only copy. Thus a caller cannot change the state of the model
 * through an object that it holds.
 *
 * <p>This class is not thread-safe. Use a model from one thread only.
 */
public final class DataModel {
  /**
   * The largest list index that a write can use. This limit prevents a path such as {@code
   * /items/999999999} from allocating a list of that length.
   */
  public static final int MAX_LIST_INDEX = 10_000;

  private Object root;
  private final Map<String, PathWatch> watches = new LinkedHashMap<>();

  /** Makes a model that contains an empty object. */
  public DataModel() {
    this(null);
  }

  /**
   * Makes a model that contains a copy of the given data.
   *
   * @param initialData the first content of the model. A null value gives an empty object.
   * @throws A2uiDataError if the data is not a JSON value
   */
  public DataModel(Object initialData) {
    Object owned = own(initialData);
    this.root = owned == null ? new LinkedHashMap<String, Object>() : owned;
  }

  /**
   * Calculates the absolute form of a path.
   *
   * <p>A path that starts with {@code /} is absolute, and the method returns it without changes.
   * The paths {@code ""} and {@code "."} are the base. The method appends all other paths to the
   * base.
   *
   * @param path the path to resolve
   * @param basePath the base path. A null or empty base is the root.
   * @return the absolute path
   */
  public static String resolvePath(String path, String basePath) {
    return JsonPointer.resolve(Objects.requireNonNull(path, "path"), basePath);
  }

  /**
   * Gets the value at a path.
   *
   * @param path the path to read
   * @return the value, or null if the path has no value. Maps and lists are read-only copies.
   * @throws A2uiDataError if the path is not correct or has a forbidden segment
   */
  public Object get(String path) {
    return snapshot(read(JsonPointer.parse(path)));
  }

  /**
   * Tells if a path is in the data. The result is true also when the value at the path is null.
   *
   * @param path the path to examine
   * @return true if the path is in the data
   * @throws A2uiDataError if the path is not correct or has a forbidden segment
   */
  public boolean has(String path) {
    return exists(JsonPointer.parse(path));
  }

  /**
   * Writes a copy of a value at a path.
   *
   * <p>The model notifies the subscribers of that path, of its parent paths and of its child paths
   * if their value changed.
   *
   * <p>The model makes the containers that are absent on the path. A container is a list if the
   * subsequent segment is a list index. If not, it is a map. A write after the end of a list fills
   * the list with null values.
   *
   * <p>A null value has these effects:
   *
   * <ul>
   *   <li>It removes a map key.
   *   <li>It sets a list item to null. The length of the list stays the same.
   *   <li>It sets the root to an empty map.
   *   <li>It does nothing if the path has no value.
   * </ul>
   *
   * <p>If the write fails, the model does not change.
   *
   * @param path the path to write
   * @param value the value to write, or null to remove the value
   * @throws A2uiDataError if the path is not correct, has a forbidden segment, goes through a
   *     string, number or boolean, or uses a list with a segment that is not an index of {@link
   *     #MAX_LIST_INDEX} or less. Also if the value is not a JSON value.
   */
  public void set(String path, Object value) {
    List<String> segments = JsonPointer.parse(path);
    if (!segments.isEmpty() && value == null && !exists(segments)) {
      return;
    }
    Object owned = own(value);
    if (!segments.isEmpty()) {
      checkWritable(path, segments);
    }

    Reactive.batch(() -> {
      if (segments.isEmpty()) {
        root = owned == null ? new LinkedHashMap<String, Object>() : owned;
      } else {
        write(segments, owned);
      }
      notifyRelated(JsonPointer.build(segments));
    });
  }

  /**
   * Removes the value at a path. This is the same as {@code set(path, null)}.
   *
   * @param path the path to remove
   * @throws A2uiDataError if the path is not correct or has a forbidden segment
   */
  public void delete(String path) {
    set(path, null);
  }

  /**
   * Registers a listener for the changes of the value at a path.
   *
   * <p>The model calls the listener with the new value. A write to a parent path or to a child path
   * can also change the value. The model does not call the listener at the time of the
   * subscription. Call {@link DataSubscription#value()} to read the current value.
   *
   * @param path the path to observe
   * @param listener the listener to register
   * @return the subscription
   * @throws A2uiDataError if the path is not correct or has a forbidden segment
   */
  public DataSubscription subscribe(String path, EventListener<Object> listener) {
    Objects.requireNonNull(listener, "listener");
    List<String> segments = JsonPointer.parse(path);
    String key = JsonPointer.build(segments);
    PathWatch watch =
        watches.computeIfAbsent(key, k -> new PathWatch(segments, snapshot(read(segments))));
    watch.subscribers++;
    return new WatchSubscription(key, watch, watch.signal.subscribe(listener));
  }

  /** Removes each subscriber. Code can continue to read and write the data. */
  public void dispose() {
    for (PathWatch watch : watches.values()) {
      watch.signal.dispose();
    }
    watches.clear();
  }

  private Object read(List<String> segments) {
    Object current = root;
    for (String segment : segments) {
      if (current instanceof Map<?, ?> map) {
        current = map.get(segment);
      } else if (current instanceof List<?> list) {
        OptionalInt index = JsonPointer.listIndex(segment);
        current =
            index.isPresent() && index.getAsInt() < list.size() ? list.get(index.getAsInt()) : null;
      } else {
        return null;
      }
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  private boolean exists(List<String> segments) {
    Object current = root;
    for (String segment : segments) {
      if (current instanceof Map<?, ?> map) {
        if (!map.containsKey(segment)) {
          return false;
        }
        current = map.get(segment);
      } else if (current instanceof List<?> list) {
        OptionalInt index = JsonPointer.listIndex(segment);
        if (index.isEmpty() || index.getAsInt() >= list.size()) {
          return false;
        }
        current = list.get(index.getAsInt());
      } else {
        return false;
      }
    }
    return true;
  }

  /**
   * Throws an error for a write that {@link #write} cannot complete. Thus {@link #write} never
   * stops in the middle of a change.
   */
  private void checkWritable(String path, List<String> segments) {
    Object current = root;
    // This flag is true after the path goes out of the current data. Each subsequent container is
    // new. It is a list only if its segment is a list index.
    boolean beyondExisting = false;
    for (int position = 0; position < segments.size(); position++) {
      String segment = segments.get(position);
      OptionalInt index = JsonPointer.listIndex(segment);
      if (beyondExisting) {
        if (index.isPresent()) {
          checkIndexBound(path, index.getAsInt());
        }
        continue;
      }
      Object next;
      if (current instanceof Map<?, ?> map) {
        next = map.get(segment);
      } else if (current instanceof List<?> list) {
        if (index.isEmpty()) {
          throw new A2uiDataError(
              "Cannot use non-numeric segment '" + segment + "' on a list in path '" + path + "'.",
              path);
        }
        checkIndexBound(path, index.getAsInt());
        next = index.getAsInt() < list.size() ? list.get(index.getAsInt()) : null;
      } else {
        throw new A2uiDataError(
            "Cannot set path '"
                + path
                + "': '"
                + segment
                + "' would be a property of a primitive value.",
            path);
      }
      if (position < segments.size() - 1) {
        if (next == null) {
          beyondExisting = true;
        } else {
          current = next;
        }
      }
    }
  }

  private static void checkIndexBound(String path, int index) {
    if (index > MAX_LIST_INDEX) {
      throw new A2uiDataError(
          "List index out of bounds in path '"
              + path
              + "': "
              + index
              + " (max "
              + MAX_LIST_INDEX
              + ").",
          path);
    }
  }

  private void write(List<String> segments, Object value) {
    Object current = root;
    int lastPosition = segments.size() - 1;
    for (int position = 0; position < lastPosition; position++) {
      String segment = segments.get(position);
      boolean nextIsIndex = JsonPointer.listIndex(segments.get(position + 1)).isPresent();
      if (current instanceof Map<?, ?>) {
        Map<String, Object> map = asMap(current);
        Object child = map.get(segment);
        if (child == null) {
          child = newContainer(nextIsIndex);
          map.put(segment, child);
        }
        current = child;
      } else if (current instanceof List<?>) {
        List<Object> list = asList(current);
        int index = JsonPointer.listIndex(segment).orElseThrow();
        padTo(list, index);
        Object child = list.get(index);
        if (child == null) {
          child = newContainer(nextIsIndex);
          list.set(index, child);
        }
        current = child;
      } else {
        throw new IllegalStateException("checkWritable accepted a path through a primitive");
      }
    }

    String last = segments.get(lastPosition);
    if (current instanceof Map<?, ?>) {
      Map<String, Object> map = asMap(current);
      if (value == null) {
        map.remove(last);
      } else {
        map.put(last, value);
      }
    } else if (current instanceof List<?>) {
      List<Object> list = asList(current);
      int index = JsonPointer.listIndex(last).orElseThrow();
      if (value != null) {
        padTo(list, index);
        list.set(index, value);
      } else if (index < list.size()) {
        list.set(index, null);
      }
    } else {
      throw new IllegalStateException("checkWritable accepted a path through a primitive");
    }
  }

  private void notifyRelated(String changedPath) {
    String changedPrefix = descendantPrefix(changedPath);
    for (Map.Entry<String, PathWatch> entry : new ArrayList<>(watches.entrySet())) {
      String watchedPath = entry.getKey();
      PathWatch watch = entry.getValue();
      boolean related =
          watchedPath.equals(changedPath)
              || watchedPath.startsWith(changedPrefix)
              || changedPath.startsWith(descendantPrefix(watchedPath));
      // The signal holds a copy. Thus a container that changed in place is not equal to the copy,
      // and a value that did not change does not cause a notification.
      if (related) {
        watch.signal.set(snapshot(read(watch.segments)));
      }
    }
  }

  private static String descendantPrefix(String path) {
    return path.equals("/") ? "/" : path + "/";
  }

  private static Object newContainer(boolean list) {
    return list ? new ArrayList<Object>() : new LinkedHashMap<String, Object>();
  }

  private static void padTo(List<Object> list, int index) {
    while (list.size() <= index) {
      list.add(null);
    }
  }

  // The model stores only the containers that own() makes. Thus these casts are safe.
  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> asList(Object value) {
    return (List<Object>) value;
  }

  /** Makes a deep copy of a value that the model can change. */
  private static Object own(Object value) {
    return switch (value) {
      case null -> null;
      case String s -> s;
      case Boolean b -> b;
      case Long l -> l;
      case Integer i -> i.longValue();
      case Short s -> s.longValue();
      case Byte b -> b.longValue();
      case Double d -> finite(d);
      case Float f -> finite(f.doubleValue());
      case BigInteger big ->
          big.bitLength() <= 63 ? (Object) big.longValue() : finite(big.doubleValue());
      case BigDecimal big -> finite(big.doubleValue());
      case Map<?, ?> map -> {
        Map<String, Object> copy = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          if (!(entry.getKey() instanceof String key)) {
            throw new A2uiDataError(
                "A data model object key must be a string, but got: " + entry.getKey());
          }
          copy.put(key, own(entry.getValue()));
        }
        yield copy;
      }
      case Collection<?> collection -> {
        List<Object> copy = new ArrayList<>(collection.size());
        for (Object item : collection) {
          copy.add(own(item));
        }
        yield copy;
      }
      default ->
          throw new A2uiDataError(
              "A data model value must be a JSON value, but got "
                  + value.getClass().getName()
                  + ".");
    };
  }

  private static Double finite(double value) {
    if (!Double.isFinite(value)) {
      throw new A2uiDataError("A data model number must be finite, but got " + value + ".");
    }
    return value;
  }

  /** Makes a deep, read-only copy of a value that the model stores. */
  private static Object snapshot(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>(map.size());
      map.forEach((key, item) -> copy.put((String) key, snapshot(item)));
      return Collections.unmodifiableMap(copy);
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>(list.size());
      list.forEach(item -> copy.add(snapshot(item)));
      return Collections.unmodifiableList(copy);
    }
    return value;
  }

  /** The signal and the number of subscribers of one observed path. */
  private static final class PathWatch {
    private final List<String> segments;
    private final Signal<Object> signal;
    private int subscribers;

    private PathWatch(List<String> segments, Object initialValue) {
      this.segments = segments;
      this.signal = new Signal<>(initialValue);
    }
  }

  private final class WatchSubscription implements DataSubscription {
    private final String key;
    private final PathWatch watch;
    private Subscription inner;

    private WatchSubscription(String key, PathWatch watch, Subscription inner) {
      this.key = key;
      this.watch = watch;
      this.inner = inner;
    }

    @Override
    public Object value() {
      return watch.signal.get();
    }

    @Override
    public void cancel() {
      if (inner == null) {
        return;
      }
      inner.cancel();
      inner = null;
      watch.subscribers--;
      if (watch.subscribers == 0 && watches.get(key) == watch) {
        watches.remove(key);
      }
    }
  }
}
