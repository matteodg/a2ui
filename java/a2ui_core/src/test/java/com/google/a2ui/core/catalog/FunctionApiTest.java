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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.exceptions.A2uiCatalogError;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FunctionApiTest {
  @Test
  void aDefinitionWithNoMembersHasTheDefaultValues() {
    FunctionApi function = new FunctionApi("f", Map.of());

    assertEquals("f", function.name());
    assertEquals(Optional.empty(), function.description());
    assertEquals(FunctionReturnType.ANY, function.returnType());
    assertEquals(AllowedCallers.RENDERER_ONLY, function.allowedCallers());
    assertFalse(function.requiresUserActivation());
    assertEquals(Optional.empty(), function.argumentSchema());
    assertEquals("FunctionApi(f)", function.toString());
  }

  @Test
  void theSignatureComesFromTheMembersOfTheDefinition() {
    Map<String, Object> args = Map.of("properties", Map.of("url", Map.of("type", "string")));
    FunctionApi function =
        new FunctionApi(
            "openUrl",
            Map.of(
                "description", "Opens a URL.",
                "returnType", "void",
                "allowedCallers", "rendererOrAgent",
                "requiresUserActivation", true,
                "properties", Map.of("args", args)));

    assertEquals(Optional.of("Opens a URL."), function.description());
    assertEquals(FunctionReturnType.VOID, function.returnType());
    assertEquals(AllowedCallers.RENDERER_OR_AGENT, function.allowedCallers());
    assertTrue(function.requiresUserActivation());
    assertEquals(Optional.of(args), function.argumentSchema());
  }

  @Test
  void theReturnTypeConstantOfTheCallSchemaIsFirst() {
    Map<String, Object> schema =
        Map.of(
            "returnType", "string", "properties", Map.of("returnType", Map.of("const", "number")));

    assertEquals(FunctionReturnType.NUMBER, new FunctionApi("f", schema).returnType());
  }

  @Test
  void anUnknownReturnTypeIsAny() {
    assertEquals(
        FunctionReturnType.ANY, new FunctionApi("f", Map.of("returnType", "future")).returnType());
  }

  @Test
  void theParametersAreTheArgumentSchemaIfTheCallSchemaHasNoArgs() {
    Map<String, Object> parameters = Map.of("value", Map.of("type", "string"));

    FunctionApi function = new FunctionApi("f", Map.of("parameters", parameters));

    assertEquals(Optional.of(parameters), function.argumentSchema());
  }

  @Test
  void callerMembersThatAreNotCorrectAreRejected() {
    assertThrows(
        A2uiCatalogError.class, () -> new FunctionApi("f", Map.of("allowedCallers", "everyone")));
    assertThrows(A2uiCatalogError.class, () -> new FunctionApi("f", Map.of("allowedCallers", 1)));
    assertThrows(
        A2uiCatalogError.class,
        () -> new FunctionApi("f", Map.of("requiresUserActivation", "yes")));
  }

  @Test
  void aSchemaThatIsNotJsonIsRejected() {
    A2uiCatalogError error =
        assertThrows(
            A2uiCatalogError.class, () -> new FunctionApi("f", Map.of("default", new Object())));

    assertTrue(
        error.getMessage().startsWith("The definition of function 'f' is not JSON: "),
        error.getMessage());
  }
}
