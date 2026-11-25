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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The list of the core suites and the support that this harness gives to each suite. */
final class SuiteRegistry {
  /** Runs one case. Throws if the SDK does not behave as the case expects. */
  @FunctionalInterface
  interface CaseHandler {
    void run(ConformanceCase testCase) throws Exception;
  }

  /** The support that this harness gives to a suite. */
  sealed interface SuiteSupport permits Supported, Unsupported {}

  /**
   * The harness runs the suite. It selects the handler from the {@code action} of each case.
   *
   * @param actions the handler for each action
   */
  record Supported(Map<String, CaseHandler> actions) implements SuiteSupport {}

  /**
   * The harness records each case of the suite as skipped.
   *
   * @param reason the reason for the skip
   */
  record Unsupported(String reason) implements SuiteSupport {}

  /** The protocol versions that this SDK implements. */
  static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of("1.0");

  /** The cases that the harness skips, by name. Each case has its reason. */
  static final Map<String, String> SKIP_TEST_NAMES = Map.of();

  private static final String NOT_YET = "Not implemented in java-core yet";
  private static final String V1_ONLY = "java-core implements protocol v1.0 only";
  private static final String ADAPTER = "Framework adapter suite; java-core has no UI";

  /**
   * Each suite in {@code conformance/core/}.
   *
   * <p>{@code SuiteRegistryTest} fails if a file on the disk is not in this map, or if an entry of
   * this map has no file. Thus a new suite cannot stay hidden.
   */
  static final Map<String, SuiteSupport> CORE_SUITES = coreSuites();

  private SuiteRegistry() {}

  /**
   * Gets the reason to skip a case.
   *
   * @return the reason, or null if the case must run
   */
  static String skipReason(ConformanceCase testCase, SuiteSupport support) {
    String byName = SKIP_TEST_NAMES.get(testCase.name());
    if (byName != null) {
      return byName;
    }
    if (support instanceof Unsupported unsupported) {
      return unsupported.reason();
    }
    String version = testCase.protocolVersion();
    if (version != null && !SUPPORTED_PROTOCOL_VERSIONS.contains(version)) {
      return "Protocol version " + version + " is not supported; " + V1_ONLY;
    }
    return null;
  }

  /**
   * Gets the handler for the action of a case.
   *
   * @throws AssertionError if the suite has no handler for the action
   */
  static CaseHandler handlerFor(ConformanceCase testCase, Supported support) {
    CaseHandler handler = support.actions().get(testCase.action());
    if (handler == null) {
      throw new AssertionError(
          "Unknown action '" + testCase.action() + "' in " + testCase.location());
    }
    return handler;
  }

  private static Map<String, SuiteSupport> coreSuites() {
    Map<String, SuiteSupport> suites = new LinkedHashMap<>();
    suites.put("core/accessibility.yaml", new Unsupported(ADAPTER));
    suites.put("core/actions.yaml", new Unsupported(NOT_YET));
    suites.put("core/adapter_conformance.yaml", new Unsupported(ADAPTER));
    suites.put("core/add_catalog.yaml", new Unsupported(NOT_YET));
    suites.put("core/agent_function_routing.yaml", new Unsupported(NOT_YET));
    suites.put("core/agent_to_renderer.yaml", new Unsupported(NOT_YET));
    suites.put("core/catalog.yaml", new Unsupported(NOT_YET));
    suites.put("core/common_types.yaml", new Unsupported(NOT_YET));
    suites.put("core/composition_constraints.yaml", new Unsupported(NOT_YET));
    suites.put("core/data_context.yaml", new Unsupported(NOT_YET));
    suites.put("core/data_deletion.yaml", new Unsupported(NOT_YET));
    suites.put("core/data_model.yaml", new Unsupported(NOT_YET));
    suites.put("core/data_model_pointers.yaml", new Unsupported(NOT_YET));
    suites.put("core/error_responses.yaml", new Unsupported(NOT_YET));
    suites.put("core/expressions.yaml", new Unsupported(NOT_YET));
    suites.put("core/functions.yaml", new Unsupported(NOT_YET));
    suites.put("core/index_function.yaml", new Unsupported(NOT_YET));
    suites.put("core/message_processor_scaling.yaml", new Unsupported(NOT_YET));
    suites.put("core/message_processor_v0_8.yaml", new Unsupported(V1_ONLY));
    suites.put("core/message_processor_v0_9.yaml", new Unsupported(V1_ONLY));
    suites.put("core/message_processor_v1_0.yaml", new Unsupported(NOT_YET));
    suites.put("core/multi_catalog.yaml", new Unsupported(NOT_YET));
    suites.put("core/node_resolution.yaml", new Unsupported(NOT_YET));
    suites.put("core/reserved_keys.yaml", new Unsupported(NOT_YET));
    suites.put("core/rpc_functions.yaml", new Unsupported(NOT_YET));
    suites.put("core/validation_result.yaml", new Unsupported(NOT_YET));
    suites.put("core/validator_v0_8.yaml", new Unsupported(V1_ONLY));
    suites.put("core/validator_v0_9.yaml", new Unsupported(V1_ONLY));
    suites.put("core/validator_v1_0.yaml", new Unsupported(NOT_YET));
    return Collections.unmodifiableMap(suites);
  }
}
