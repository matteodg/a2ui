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

import static com.google.a2ui.core.conformance.JsonValues.asObject;
import static com.google.a2ui.core.conformance.JsonValues.asString;
import static com.google.a2ui.core.conformance.JsonValues.assertJsonEquals;

import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.core.state.DataModel;
import java.util.Map;
import java.util.Set;

/** The action {@code resolve_path}: resolves {@code args.path} against {@code args.contextPath}. */
final class ResolvePathHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      Set.of("name", "description", "features", "action", "args", "catalog", "expect");

  private static final Set<String> ARGS_KEYS = Set.of("contextPath", "path");

  @Override
  public void run(ConformanceCase testCase) {
    String location = testCase.location();
    KnownKeys.assertKnownKeys(testCase.fields(), CASE_KEYS, location);
    Map<String, Object> args = asObject(testCase.fields().get("args"), "args in " + location);
    KnownKeys.assertKnownKeys(args, ARGS_KEYS, "args in " + location);
    String path = asString(args.get("path"), "args.path in " + location);
    Object rawContext = args.get("contextPath");
    String contextPath = rawContext == null ? null : asString(rawContext, "args.contextPath");

    assertJsonEquals(
        testCase.fields().get("expect"), DataModel.resolvePath(path, contextPath), location);
  }
}
