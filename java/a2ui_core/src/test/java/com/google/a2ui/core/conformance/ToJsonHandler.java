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

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import java.util.Map;
import java.util.Set;

/**
 * The action {@code to_json}: writes the catalog of this SDK and compares it with a published file.
 */
final class ToJsonHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys("action", "builtinCatalog", "expectFile", "protocolVersion");

  @Override
  public void run(ConformanceCase testCase) {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    Object builtin = fields.get("builtinCatalog");
    if (!"basic".equals(builtin) || CatalogCases.version(testCase) != ProtocolVersion.V1_0) {
      throw new AssertionError(
          location + ": the SDK has no catalog '" + builtin + "' for this version");
    }
    Map<String, Object> expected =
        CatalogCases.jsonFile(asString(fields.get("expectFile"), "expectFile in " + location));
    CatalogCases.assertSameDocument(expected, BasicCatalog.api().toJson(), location);
  }
}
