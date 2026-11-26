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

package com.google.a2ui.core.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.resolution.DataContext;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FunctionImplementationTest {
  private static final Map<String, Object> SCHEMA =
      Map.of("returnType", "string", "requiresUserActivation", true);

  private static DataContext context() {
    return new DataContext(
        new SurfaceModel<ComponentApi>("s", ProtocolVersion.V1_0, null, Map.of(), false, null));
  }

  @Test
  void anImplementationHasTheSignatureOfItsDefinition() {
    FunctionImplementation function =
        new FunctionImplementation("shout", SCHEMA, (args, context) -> null);

    assertEquals("shout", function.name());
    assertEquals(SCHEMA, function.schema());
    assertEquals(FunctionReturnType.STRING, function.returnType());
    assertTrue(function.requiresUserActivation());
    assertEquals("FunctionImplementation(shout)", function.toString());
  }

  @Test
  void anImplementationCanCopyTheSignatureOfAFunctionApi() {
    FunctionApi api = new FunctionApi("shout", SCHEMA);

    FunctionImplementation function = new FunctionImplementation(api, (args, context) -> null);

    assertEquals("shout", function.name());
    assertEquals(api.schema(), function.schema());
  }

  @Test
  void executeGivesTheArgumentsAndTheContextToTheBody() {
    List<Object> received = new ArrayList<>();
    FunctionImplementation function =
        new FunctionImplementation(
            "shout",
            SCHEMA,
            (args, context) -> {
              received.add(args);
              received.add(context);
              return "HI";
            });
    DataContext context = context();

    Object result = function.execute(Map.of("value", "hi"), context);

    assertEquals("HI", result);
    assertEquals(List.of(Map.of("value", "hi"), context), received);
  }

  @Test
  void executeReturnsNullForAFunctionThatReturnsNothing() {
    FunctionImplementation function =
        new FunctionImplementation("log", Map.of("returnType", "void"), (args, context) -> null);

    assertNull(function.execute(Map.of(), context()));
  }

  @Test
  void aNullBodyAndNullArgumentsAreRejected() {
    FunctionImplementation function =
        new FunctionImplementation("shout", SCHEMA, (args, context) -> null);

    assertThrows(NullPointerException.class, () -> new FunctionImplementation("f", SCHEMA, null));
    assertThrows(NullPointerException.class, () -> function.execute(null, context()));
    assertThrows(NullPointerException.class, () -> function.execute(Map.of(), null));
  }
}
