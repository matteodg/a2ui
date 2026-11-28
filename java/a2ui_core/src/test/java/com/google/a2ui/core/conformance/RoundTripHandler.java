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

import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import java.util.Map;
import java.util.Set;

/**
 * The action {@code round_trip}: {@code fromJson(c).toJson()} is equal to {@code c}, and a second
 * round does not change the document.
 */
final class RoundTripHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys("action", "catalog", "catalogId", "catalogPath", "protocolVersion");

  @Override
  public void run(ConformanceCase testCase) {
    String location = testCase.location();
    KnownKeys.assertKnownKeys(testCase.fields(), CASE_KEYS, location);
    Map<String, Object> source = CatalogCases.document(testCase);
    Map<String, Object> written = CatalogCases.load(testCase, source).toJson();
    CatalogCases.assertSameDocument(source, written, "toJson in " + location);
    Map<String, Object> again = CatalogCases.load(testCase, written).toJson();
    CatalogCases.assertSameDocument(written, again, "second toJson in " + location);
  }
}
