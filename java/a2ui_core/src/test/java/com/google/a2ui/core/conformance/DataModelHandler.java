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

package com.google.a2ui.core.conformance;

import static com.google.a2ui.core.conformance.JsonValues.asList;
import static com.google.a2ui.core.conformance.JsonValues.asObject;
import static com.google.a2ui.core.conformance.JsonValues.asString;
import static com.google.a2ui.core.conformance.JsonValues.assertJsonEquals;

import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.core.state.DataModel;
import com.google.a2ui.core.state.DataSubscription;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The action {@code data_model}.
 *
 * <p>The handler makes a {@link DataModel} from {@code initial} and subscribes to each {@code
 * watch} path. Then it runs the {@code steps} on that model.
 */
final class DataModelHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      Set.of("name", "description", "features", "action", "catalog", "initial", "steps", "watch");

  private static final Set<String> STEP_KEYS =
      Set.of(
          "expect",
          "expect_absent",
          "expect_error",
          "expect_notified",
          "expect_type",
          "expect_values",
          "op",
          "path",
          "value");

  /** One watched path and the number of notifications of the current step. */
  private static final class Observer {
    private final String path;
    private int notifications;
    private DataSubscription subscription;

    private Observer(String path) {
      this.path = path;
    }
  }

  @Override
  public void run(ConformanceCase testCase) throws Exception {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    DataModel model = new DataModel(fields.get("initial"));

    List<Observer> observers = new ArrayList<>();
    if (fields.get("watch") != null) {
      for (Object path : asList(fields.get("watch"), "watch in " + location)) {
        Observer observer = new Observer(asString(path, "watch entry in " + location));
        observer.subscription = model.subscribe(observer.path, value -> observer.notifications++);
        observers.add(observer);
      }
    }

    // The value that the harness gave to the last set(). A `mutate_input` step changes it.
    Object[] lastSetValue = new Object[1];
    List<Object> steps = asList(fields.get("steps"), "steps in " + location);
    for (int index = 0; index < steps.size(); index++) {
      String where = "step " + index + " of " + location;
      Map<String, Object> step = asObject(steps.get(index), where);
      KnownKeys.assertKnownKeys(step, STEP_KEYS, where);
      observers.forEach(observer -> observer.notifications = 0);

      ExpectedErrors.Block apply = () -> applyStep(model, step, lastSetValue, where);
      Object expectError = ExpectedErrors.expectedError(step);
      if (expectError != null) {
        ExpectedErrors.assertRaises(expectError, where, apply);
        continue;
      }
      apply.run();
      checkNotified(step, observers, where);
      checkValues(step, observers, where);
    }
  }

  private static void applyStep(
      DataModel model, Map<String, Object> step, Object[] lastSetValue, String where) {
    Object op = step.get("op");
    switch (String.valueOf(op)) {
      case "get" -> checkGet(model, step, where);
      case "set" -> {
        lastSetValue[0] = JsonValues.mutableCopy(step.get("value"));
        model.set(asString(step.get("path"), "path in " + where), lastSetValue[0]);
      }
      case "delete" -> model.delete(asString(step.get("path"), "path in " + where));
      case "dispose" -> model.dispose();
      case "mutate_input" ->
          mutateInPlace(
              lastSetValue[0], asString(step.get("path"), "path in " + where), step.get("value"));
      default -> throw new AssertionError("Unknown op '" + op + "' in " + where);
    }
  }

  private static void checkGet(DataModel model, Map<String, Object> step, String where) {
    Object actual = model.get(asString(step.get("path"), "path in " + where));
    if (Boolean.TRUE.equals(step.get("expect_absent")) && actual != null) {
      throw new AssertionError(where + ": expected no value but was <" + actual + ">");
    }
    if (step.containsKey("expect_type")) {
      Object type = step.get("expect_type");
      boolean matches =
          switch (String.valueOf(type)) {
            case "list" -> actual instanceof List<?>;
            case "object" -> actual instanceof Map<?, ?>;
            default -> throw new AssertionError("Unknown expect_type '" + type + "' in " + where);
          };
      if (!matches) {
        throw new AssertionError(where + ": expected a " + type + " but was <" + actual + ">");
      }
    }
    if (step.containsKey("expect")) {
      assertJsonEquals(step.get("expect"), actual, where);
    }
  }

  private static void checkNotified(
      Map<String, Object> step, List<Observer> observers, String where) {
    Object expected = step.get("expect_notified");
    if (expected == null) {
      return;
    }
    List<String> expectedPaths = new ArrayList<>();
    for (Object path : asList(expected, "expect_notified in " + where)) {
      expectedPaths.add(asString(path, "expect_notified"));
    }
    List<String> notified = new ArrayList<>();
    for (Observer observer : observers) {
      notified.addAll(Collections.nCopies(observer.notifications, observer.path));
    }
    List<String> sortedExpected = new ArrayList<>(expectedPaths);
    List<String> sortedNotified = new ArrayList<>(notified);
    Collections.sort(sortedExpected);
    Collections.sort(sortedNotified);
    if (!sortedExpected.equals(sortedNotified)) {
      throw new AssertionError(
          where + ": expected notifications " + expectedPaths + " but got " + notified);
    }
  }

  private static void checkValues(
      Map<String, Object> step, List<Observer> observers, String where) {
    Object expected = step.get("expect_values");
    if (expected == null) {
      return;
    }
    asObject(expected, "expect_values in " + where)
        .forEach((path, value) -> {
          Observer observer =
              observers.stream()
                  .filter(candidate -> candidate.path.equals(path))
                  .findFirst()
                  .orElseThrow(() ->
                      new AssertionError(
                          where + ": expect_values names '" + path + "', which is not watched"));
          assertJsonEquals(value, observer.subscription.value(), where + " at " + path);
        });
  }

  /** Writes a value at a path in an object that the harness owns. */
  @SuppressWarnings("unchecked")
  private static void mutateInPlace(Object target, String path, Object value) {
    List<String> segments = new ArrayList<>();
    for (String segment : path.split("/")) {
      if (!segment.isEmpty()) {
        segments.add(segment);
      }
    }
    if (segments.isEmpty()) {
      return;
    }
    Object current = target;
    for (String segment : segments.subList(0, segments.size() - 1)) {
      if (current instanceof List<?> list) {
        current = list.get(Integer.parseInt(segment));
      } else if (current instanceof Map<?, ?> map) {
        current = map.get(segment);
      } else {
        return;
      }
    }
    String last = segments.get(segments.size() - 1);
    if (current instanceof List<?> list) {
      ((List<Object>) list).set(Integer.parseInt(last), value);
    } else if (current instanceof Map<?, ?> map) {
      ((Map<String, Object>) map).put(last, value);
    }
  }
}
