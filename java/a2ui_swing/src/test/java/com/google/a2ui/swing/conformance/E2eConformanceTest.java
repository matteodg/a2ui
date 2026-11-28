/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.a2ui.swing.conformance;

import java.util.List;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the renderer part of the scenarios in {@code conformance/e2e/} against a mounted {@code
 * A2uiSurface}.
 *
 * <p>The test makes one dynamic test for each scenario, in a container for each suite, as {@code
 * AdapterConformanceTest} does. A scenario of protocol v0.9.1 is recorded as skipped.
 */
class E2eConformanceTest {
  @TestFactory
  List<DynamicNode> e2eSuites() {
    return AdapterConformanceTest.containers(SuiteRegistry.E2E_SUITES);
  }
}
