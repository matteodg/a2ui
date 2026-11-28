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

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the timing cases of {@code conformance/core/message_processor_scaling.yaml}, one at a time.
 *
 * <p>A time depends on the computer and on its load. Thus the usual build does not run this class.
 * The build runs it only with the Maven profile {@code performance}: {@code ./mvnw -Pperformance
 * verify}. That build runs no other test.
 *
 * <p>The class records each case in the manifest. The usual build records the same cases as
 * skipped. Thus give the two builds different manifest directories.
 */
@Tag("performance")
class ScalingConformanceTest {
  private static final String V1_0 = "1.0";

  @TestFactory
  List<DynamicNode> timingCases() {
    MeasureGrowthHandler handler = new MeasureGrowthHandler();
    List<DynamicNode> tests = new ArrayList<>();
    for (ConformanceCase testCase : ConformanceLoader.loadSuite(SuiteRegistry.SCALING_SUITE)) {
      tests.add(dynamicTest(testCase.name(), () -> run(testCase, handler)));
    }
    return tests;
  }

  private static void run(ConformanceCase testCase, MeasureGrowthHandler handler) {
    if (!SuiteRegistry.MEASURE_GROWTH.equals(testCase.action())) {
      ConformanceManifest.failed(testCase);
      throw new AssertionError(
          "Unknown action '" + testCase.action() + "' in " + testCase.location());
    }
    if (!V1_0.equals(testCase.protocolVersion())) {
      String reason =
          "Protocol version "
              + testCase.protocolVersion()
              + " is not supported; java-core implements protocol v1.0 only";
      ConformanceManifest.skipped(testCase, reason);
      Assumptions.abort(reason);
    }
    try {
      handler.run(testCase);
    } catch (RuntimeException | AssertionError e) {
      ConformanceManifest.failed(testCase);
      throw e;
    }
    ConformanceManifest.passed(testCase);
  }
}
