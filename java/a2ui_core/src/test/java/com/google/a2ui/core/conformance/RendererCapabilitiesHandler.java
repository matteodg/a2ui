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

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.core.processing.MessageProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The action {@code get_renderer_capabilities}: makes a processor with the catalogs of a case and
 * compares its capabilities with the full expected structure.
 */
final class RendererCapabilitiesHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys("action", "args", "catalogs", "expect", "protocolVersion");

  private static final Set<String> ARGUMENT_KEYS = Set.of("includeInlineCatalogs", "version");

  @Override
  public void run(ConformanceCase testCase) {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    Map<String, Object> args = asObject(fields.get("args"), "args in " + location);
    KnownKeys.assertKnownKeys(args, ARGUMENT_KEYS, "args in " + location);
    ProtocolVersion version = version(args, location);

    // An entry that has only an ID and a version is a catalog with no components.
    List<Catalog<ComponentApi, FunctionApi>> catalogs = new ArrayList<>();
    for (Object entry : asList(fields.get("catalogs"), "catalogs in " + location)) {
      catalogs.add(
          Catalog.fromJson(asObject(entry, "catalogs entry in " + location), null, version));
    }
    Map<String, Object> actual =
        new MessageProcessor<>(catalogs)
            .getRendererCapabilities(
                List.of(version), Boolean.TRUE.equals(args.get("includeInlineCatalogs")));
    CatalogCases.assertSameDocument(fields.get("expect"), actual, location);
  }

  /** Gets the protocol version that the arguments of a case give. */
  static ProtocolVersion version(Map<String, Object> args, String location) {
    String text = asString(args.get("version"), "args.version in " + location);
    return ProtocolVersion.tryParse(text)
        .orElseThrow(() -> new AssertionError("Unknown version '" + text + "' in " + location));
  }
}
