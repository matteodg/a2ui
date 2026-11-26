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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiDataError;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.internal.ErrorFields;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The failure of a function that ran is different from a call that the context refused. The core
 * gives the two a different protocol error code in an action.
 */
class FunctionFailureTest {
  private static final String CATALOG_ID = "urn:test:failures";

  private static final A2uiDataError OWN_ERROR = new A2uiDataError("The value is not a list.");

  private final Catalog<ComponentApi, FunctionImplementation> catalog =
      new Catalog<>(
          CATALOG_ID,
          List.of(),
          List.of(
              new FunctionImplementation(
                  "crash",
                  Map.of("returnType", "void"),
                  (args, context) -> {
                    throw new IllegalStateException("The disk is full.");
                  }),
              new FunctionImplementation(
                  "reject",
                  Map.of("returnType", "void"),
                  (args, context) -> {
                    throw OWN_ERROR;
                  }),
              // Evaluates its argument only. Thus it shows the failure of a nested call.
              new FunctionImplementation(
                  "echo", Map.of("returnType", "any"), (args, context) -> args.get("value"))));

  private final DataContext context =
      new DataContext(
          new SurfaceModel<>(
              "s", ProtocolVersion.V1_0, catalog, Map.of(CATALOG_ID, catalog), false, null));

  private static Map<String, Object> call(String name) {
    return Map.of("@call", name, "args", Map.of());
  }

  private A2uiError failure(Object value) {
    return assertThrows(A2uiError.class, () -> context.resolveDynamicValue(value));
  }

  @Test
  void anExceptionOfTheCodeOfAFunctionBecomesAnExpressionError() {
    A2uiError error = failure(call("crash"));

    assertInstanceOf(A2uiExpressionError.class, error);
    assertEquals("Function 'crash' failed: The disk is full.", error.getMessage());
    assertInstanceOf(IllegalStateException.class, error.getCause());
    assertTrue(ErrorFields.isRaisedByFunction(error));
  }

  @Test
  void anA2uiErrorOfTheCodeOfAFunctionStaysTheSameError() {
    A2uiError error = failure(call("reject"));

    assertSame(OWN_ERROR, error);
    assertTrue(ErrorFields.isRaisedByFunction(error));
  }

  @Test
  void theFailureOfANestedCallIsTheFailureOfAFunction() {
    A2uiError error = failure(Map.of("@call", "echo", "args", Map.of("value", call("crash"))));

    assertEquals("Function 'crash' failed: The disk is full.", error.getMessage());
    assertTrue(ErrorFields.isRaisedByFunction(error));
  }

  @Test
  void aCallThatTheContextRefusesIsNotTheFailureOfAFunction() {
    A2uiError unknown = failure(call("missing"));
    A2uiError outsideTemplate = failure(call("@index"));

    assertInstanceOf(A2uiExpressionError.class, unknown);
    assertFalse(ErrorFields.isRaisedByFunction(unknown));
    assertFalse(ErrorFields.isRaisedByFunction(outsideTemplate));
  }
}
