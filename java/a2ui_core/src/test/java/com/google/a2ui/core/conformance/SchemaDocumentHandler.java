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

import static com.google.a2ui.core.conformance.JsonValues.asString;
import static com.google.a2ui.core.conformance.JsonValues.jsonEquals;

import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The actions {@code common_types_schema} and {@code agent_to_renderer_schema}: the schema that the
 * SDK uses is the document that the specification publishes.
 *
 * <p>This SDK has no wire models from which it can generate a schema. The build copies the
 * documents of the specification into the SDK, and the SDK validates against them. Thus these cases
 * make sure that the copy in the SDK is the published document.
 */
final class SchemaDocumentHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys("action", "expectFile", "protocolVersion");

  private final Supplier<Map<String, Object>> document;

  /**
   * Makes a handler for one schema document.
   *
   * @param document gets the document from the SDK
   */
  SchemaDocumentHandler(Supplier<Map<String, Object>> document) {
    this.document = document;
  }

  @Override
  public void run(ConformanceCase testCase) {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    String expectFile = asString(fields.get("expectFile"), "expectFile in " + location);
    if (!jsonEquals(CatalogCases.jsonFile(expectFile), document.get())) {
      throw new AssertionError(location + ": the schema in the SDK is not " + expectFile);
    }
  }
}
