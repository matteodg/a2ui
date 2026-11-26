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

package com.google.a2ui.core.basic_catalog.v1_0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.resolution.DataContext;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The behavior of the functions that the shared suite {@code core/functions.yaml} does not examine.
 */
class BasicFunctionsTest {
  private static final Map<String, Object> VALID = Map.of("valid", true);

  /** Makes a context at the root of a surface whose default catalog is the given catalog. */
  private static DataContext context(
      Catalog<ComponentApi, FunctionImplementation> catalog, Map<String, Object> data) {
    SurfaceModel<ComponentApi> surface =
        new SurfaceModel<>(
            "s", ProtocolVersion.V1_0, catalog, Map.of(catalog.id(), catalog), false, null);
    surface.dataModel().set("/", data);
    return new DataContext(surface);
  }

  private static DataContext context() {
    return context(BasicCatalog.withFunctions(), Map.of());
  }

  /**
   * Makes a call.
   *
   * @param name the name of the function
   * @param namesAndValues the name of each argument, and after it the value of the argument
   */
  private static Map<String, Object> call(String name, Object... namesAndValues) {
    Map<String, Object> args = new LinkedHashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      args.put((String) namesAndValues[i], namesAndValues[i + 1]);
    }
    Map<String, Object> call = new LinkedHashMap<>();
    call.put("@call", name);
    call.put("args", args);
    return call;
  }

  private static Object evaluate(String name, Object... namesAndValues) {
    return context().resolveDynamicValue(call(name, namesAndValues));
  }

  private static Map<String, Object> invalid(String message) {
    return Map.of("valid", false, "message", message);
  }

  @Test
  void theCatalogWithFunctionsHasTheDocumentOfTheApi() {
    Catalog<ComponentApi, FunctionImplementation> runnable = BasicCatalog.withFunctions();

    assertEquals(BasicCatalog.api().toJson(), runnable.toJson());
    assertEquals(BasicCatalog.api().components(), runnable.components());
    assertEquals(
        List.copyOf(BasicCatalog.api().functions().keySet()),
        List.copyOf(runnable.functions().keySet()));
  }

  @Test
  void requiredAcceptsEachValueThatIsNotEmpty() {
    assertEquals(VALID, evaluate("required", "value", 0));
    assertEquals(VALID, evaluate("required", "value", false));
    assertEquals(VALID, evaluate("required", "value", " "));
    assertEquals(VALID, evaluate("required", "value", Map.of()));
    assertEquals(invalid("This field is required."), evaluate("required", "value", ""));
    assertEquals(invalid("This field is required."), evaluate("required", "value", null));
    assertEquals(invalid("This field is required."), evaluate("required", "value", List.of()));
    assertEquals(invalid("This field is required."), evaluate("required"));
  }

  @Test
  void lengthTellsWhichLimitTheValueDoesNotObey() {
    assertEquals(VALID, evaluate("length", "value", "abc", "min", 3, "max", 3));
    assertEquals(invalid("Minimum length is 3."), evaluate("length", "value", "ab", "min", 3));
    assertEquals(invalid("Maximum length is 2."), evaluate("length", "value", "abc", "max", 2.0));
    assertEquals(
        invalid("Maximum length is 1."), evaluate("length", "value", List.of(1, 2), "max", 1));
    assertEquals(invalid("Minimum length is 1."), evaluate("length", "value", 12345, "min", 1));
    assertEquals(VALID, evaluate("length", "value", "abc"));
  }

  @Test
  void numericTellsWhichLimitTheValueDoesNotObey() {
    assertEquals(VALID, evaluate("numeric", "value", 10, "min", 10, "max", 10));
    assertEquals(VALID, evaluate("numeric", "value", " 2.5 ", "min", 2));
    assertEquals(invalid("Minimum value is 0."), evaluate("numeric", "value", -1, "min", 0));
    assertEquals(invalid("Maximum value is 10."), evaluate("numeric", "value", 11, "max", 10));
    assertEquals(invalid("Maximum value is 1.5."), evaluate("numeric", "value", "2", "max", 1.5));
    assertEquals(invalid("Value must be a valid number."), evaluate("numeric", "value", "abc"));
    assertEquals(invalid("Value must be a valid number."), evaluate("numeric", "value", null));
    assertEquals(
        invalid("Value must be a valid number."), evaluate("numeric", "value", List.of(1)));
  }

  @Test
  void emailGivesACodeAndASeverityForAValueThatIsNotAnAddress() {
    Map<String, Object> invalid =
        Map.of(
            "valid",
            false,
            "code",
            "INVALID_EMAIL",
            "message",
            "Please enter a valid email address (e.g. user@example.com).",
            "severity",
            "error");

    assertEquals(VALID, evaluate("email", "value", "ada.lovelace+work@example.co.uk"));
    for (Object value : Arrays.asList("ada@example", "@example.com", "ada @example.com", 5, null)) {
      assertEquals(invalid, evaluate("email", "value", value), String.valueOf(value));
    }
    assertEquals(invalid, evaluate("email", "value", "a".repeat(320) + "@example.com"));
  }

  @Test
  void theLogicFunctionsReadTheResultOfAValidationCallByItsValidity() {
    Map<String, Object> present = call("required", "value", "x");
    Map<String, Object> absent = call("required", "value", "");

    assertEquals(true, evaluate("and", "values", List.of(present, present)));
    assertEquals(false, evaluate("and", "values", List.of(present, absent)));
    assertEquals(true, evaluate("or", "values", List.of(absent, present)));
    assertEquals(false, evaluate("or", "values", List.of(absent, absent)));
    assertEquals(true, evaluate("not", "value", absent));
    assertEquals(false, evaluate("not", "value", call("not", "value", absent)));
  }

  @Test
  void theLogicFunctionsCoerceTheOperandsOfADirectCall() {
    Map<String, FunctionImplementation> functions = BasicCatalog.withFunctions().functions();
    List<Object> mixed = List.of("TRUE", 2, Map.of("valid", true));

    assertEquals(true, functions.get("and").execute(Map.of("values", mixed), context()));
    assertEquals(
        false, functions.get("or").execute(Map.of("values", List.of("yes", 0)), context()));
    assertEquals(true, functions.get("not").execute(Map.of(), context()));
  }

  @Test
  void andAndOrRequireTwoValues() {
    for (String name : List.of("and", "or")) {
      assertThrows(A2uiExpressionError.class, () -> evaluate(name, "values", List.of(true)));
      assertThrows(A2uiExpressionError.class, () -> evaluate(name, "values", true));
      A2uiExpressionError error = assertThrows(A2uiExpressionError.class, () -> evaluate(name));
      assertEquals("The '" + name + "' function requires at least 2 values.", error.getMessage());
    }
  }

  @Test
  void aFunctionThatTheSdkDoesNotImplementFailsWhenItRuns() {
    A2uiExpressionError error =
        assertThrows(
            A2uiExpressionError.class, () -> evaluate("regex", "value", "a", "pattern", "a"));

    assertTrue(error.getMessage().contains("'regex'"), error.getMessage());
    assertTrue(error.getMessage().contains("not implemented"), error.getMessage());
  }
}
