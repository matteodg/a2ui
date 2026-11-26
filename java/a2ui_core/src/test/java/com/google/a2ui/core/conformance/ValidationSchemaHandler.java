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

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import java.util.Map;
import java.util.Set;

/**
 * The action {@code validation_schema}: loads a catalog document and compares its validation schema
 * with a golden file.
 */
final class ValidationSchemaHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys(
          "action", "catalog", "catalogId", "catalogPath", "expectFile", "protocolVersion");

  @Override
  public void run(ConformanceCase testCase) {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    Catalog<ComponentApi, FunctionApi> catalog =
        CatalogCases.load(testCase, CatalogCases.document(testCase));
    Map<String, Object> expected =
        CatalogCases.jsonFile(asString(fields.get("expectFile"), "expectFile in " + location));
    CatalogCases.assertSameDocument(expected, catalog.validationSchema(), location);
  }
}
