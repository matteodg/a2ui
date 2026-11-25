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

import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.google.a2ui.core.conformance.SuiteRegistry.SuiteSupport;
import com.google.a2ui.core.conformance.SuiteRegistry.Supported;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

/** Runs each case of each suite in {@code conformance/core/} and records it in the manifest. */
class ConformanceSuitesTest {
  @TestFactory
  List<DynamicNode> coreSuites() {
    List<DynamicNode> containers = new ArrayList<>();
    SuiteRegistry.CORE_SUITES.forEach((suite, support) -> {
      List<DynamicNode> tests = new ArrayList<>();
      for (ConformanceCase testCase : ConformanceLoader.loadSuite(suite)) {
        tests.add(dynamicTest(testCase.name(), () -> run(testCase, support)));
      }
      containers.add(dynamicContainer(suite, tests));
    });
    return containers;
  }

  private static void run(ConformanceCase testCase, SuiteSupport support) throws Exception {
    String reason = SuiteRegistry.skipReason(testCase, support);
    if (reason != null) {
      ConformanceManifest.skipped(testCase, reason);
      Assumptions.abort(reason);
    }
    try {
      SuiteRegistry.handlerFor(testCase, (Supported) support).run(testCase);
    } catch (Exception | AssertionError e) {
      ConformanceManifest.failed(testCase);
      throw e;
    }
    ConformanceManifest.passed(testCase);
  }
}
