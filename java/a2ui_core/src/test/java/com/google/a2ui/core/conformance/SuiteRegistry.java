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

import com.google.a2ui.core.schema.v1_0.SchemaDocuments;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
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
   * <p>An action that is in no map fails its case.
   *
   * @param actions the handler for each action that this harness runs
   * @param unsupported the cases that this harness does not run, each with its reason. A key is an
   *     action ({@code validate}), or an action and the path of a key ({@code
   *     from_json.expect.selfContained}). The second form applies only to the cases of the action
   *     that set that key. The harness records the cases as skipped. If two keys apply to one case,
   *     the first key in the sequence of the map gives the reason. Thus the reason is the same in
   *     each run. {@link SuiteRegistry#reasons(String...)} makes such a map.
   * @param anyVersion the actions that run for each protocol version label. The behavior that they
   *     examine is the same in each version, and the SDK has one implementation.
   */
  record Supported(
      Map<String, CaseHandler> actions,
      SequencedMap<String, String> unsupported,
      Set<String> anyVersion)
      implements SuiteSupport {
    /** Makes the support for a suite where each action runs and the version label is important. */
    Supported(Map<String, CaseHandler> actions) {
      this(actions, reasons(), Set.of());
    }
  }

  /**
   * The harness records each case of the suite as skipped.
   *
   * @param reason the reason for the skip
   */
  record Unsupported(String reason) implements SuiteSupport {}

  /** The protocol versions that this SDK implements. */
  static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of("1.0");

  private static final String NEEDS_RESOLUTION =
      "Expects resolved property values, and java-core does not resolve nodes yet";

  private static final String V1_ONLY = "java-core implements protocol v1.0 only";

  /** The cases that the harness skips, by name. Each case has its reason. */
  static final Map<String, String> SKIP_TEST_NAMES =
      Map.of(
          "test_escaped_doubled_at_unescaping",
          NEEDS_RESOLUTION,
          "test_escaped_doubled_at_metadata_unescaping",
          NEEDS_RESOLUTION,
          "test_v10_get_renderer_data_model_filters_by_surface_protocol_version",
          "Creates a v0.9 surface; " + V1_ONLY);

  private static final String NOT_YET = "Not implemented in java-core yet";
  private static final String ADAPTER = "Framework adapter suite; java-core has no UI";

  private static final String PROCESS = "process_messages";
  private static final String VALIDATE = "validate";

  /**
   * Each suite in {@code conformance/core/}.
   *
   * <p>{@code SuiteRegistryTest} fails if a file on the disk is not in this map, or if an entry of
   * this map has no file. Thus a new suite cannot stay hidden.
   */
  static final Map<String, SuiteSupport> CORE_SUITES = coreSuites();

  private SuiteRegistry() {}

  /**
   * Makes a map for {@link Supported#unsupported()} that keeps the sequence of its entries.
   *
   * @param keysAndReasons each key, and after it the reason to skip the cases that the key applies
   *     to
   * @return the map. It is read-only.
   */
  static SequencedMap<String, String> reasons(String... keysAndReasons) {
    if (keysAndReasons.length % 2 != 0) {
      throw new IllegalArgumentException("Each key must have a reason.");
    }
    SequencedMap<String, String> reasons = new LinkedHashMap<>();
    for (int i = 0; i < keysAndReasons.length; i += 2) {
      if (reasons.put(keysAndReasons[i], keysAndReasons[i + 1]) != null) {
        throw new IllegalArgumentException("The key " + keysAndReasons[i] + " is there twice.");
      }
    }
    return Collections.unmodifiableSequencedMap(reasons);
  }

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
    Supported supported = (Supported) support;
    String action = testCase.action();
    for (Map.Entry<String, String> entry : supported.unsupported().entrySet()) {
      if (matchesUnsupported(testCase, entry.getKey())) {
        return entry.getValue();
      }
    }
    String version = testCase.protocolVersion();
    boolean versionMatters = action == null || !supported.anyVersion().contains(action);
    if (versionMatters && version != null && !SUPPORTED_PROTOCOL_VERSIONS.contains(version)) {
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

  /**
   * Tells if a key of {@code unsupported} applies to a case.
   *
   * <p>The key applies if its action is the action of the case and the case sets the key path that
   * follows the action. A key with no path applies to each case of the action. If the path goes
   * through a list, for example {@code steps}, the key applies if one item of the list sets the
   * remaining path.
   *
   * @param target the key, for example {@code from_json.expect.selfContained}
   */
  static boolean matchesUnsupported(ConformanceCase testCase, String target) {
    List<String> parts = List.of(target.split("[.]"));
    return parts.getFirst().equals(testCase.action())
        && hasPath(testCase.fields(), parts.subList(1, parts.size()));
  }

  private static boolean hasPath(Object node, List<String> keys) {
    if (keys.isEmpty()) {
      return true;
    }
    return switch (node) {
      case List<?> list -> list.stream().anyMatch(item -> hasPath(item, keys));
      case Map<?, ?> map ->
          map.containsKey(keys.getFirst())
              && hasPath(map.get(keys.getFirst()), keys.subList(1, keys.size()));
      case null, default -> false;
    };
  }

  /**
   * Makes the support for a suite whose cases give messages to a message processor.
   *
   * @param actions the actions of the suite that the harness runs: {@code process_messages}, {@code
   *     validate}, or the two
   */
  private static Supported processorSuite(List<String> actions) {
    return processorSuite(actions, Map.of());
  }

  /**
   * Makes the support for a suite whose cases give messages to a message processor or ask a
   * processor a question.
   *
   * @param actions the actions of the suite that give messages: {@code process_messages}, {@code
   *     validate}, or the two
   * @param otherActions the handler for each other action of the suite
   */
  private static Supported processorSuite(
      List<String> actions, Map<String, CaseHandler> otherActions) {
    Map<String, CaseHandler> handlers = new LinkedHashMap<>();
    for (String action : actions) {
      handlers.put(action, new ProcessMessagesHandler(action.equals(VALIDATE)));
    }
    handlers.putAll(otherActions);
    return new Supported(handlers);
  }

  private static Map<String, SuiteSupport> coreSuites() {
    Map<String, SuiteSupport> suites = new LinkedHashMap<>();
    suites.put("core/accessibility.yaml", new Unsupported(ADAPTER));
    suites.put("core/actions.yaml", new Unsupported(NOT_YET));
    suites.put("core/adapter_conformance.yaml", new Unsupported(ADAPTER));
    suites.put("core/add_catalog.yaml", processorSuite(List.of(PROCESS)));
    suites.put("core/agent_function_routing.yaml", new Unsupported(NOT_YET));
    suites.put(
        "core/agent_to_renderer.yaml",
        new Supported(
            Map.of(
                "agent_to_renderer_schema",
                new SchemaDocumentHandler(SchemaDocuments::agentToRenderer))));
    suites.put(
        "core/catalog.yaml",
        new Supported(
            Map.of(
                "from_json", new FromJsonHandler(),
                "to_json", new ToJsonHandler(),
                "round_trip", new RoundTripHandler(),
                "validation_schema", new ValidationSchemaHandler(),
                "derive", new DeriveHandler())));
    suites.put(
        "core/common_types.yaml",
        new Supported(
            Map.of(
                "common_types_schema", new SchemaDocumentHandler(SchemaDocuments::commonTypes),
                "validate_common_type", new ValidateCommonTypeHandler())));
    suites.put("core/composition_constraints.yaml", processorSuite(List.of(PROCESS, VALIDATE)));
    suites.put(
        "core/data_context.yaml",
        new Supported(
            Map.of("resolve_path", new ResolvePathHandler()), reasons(), Set.of("resolve_path")));
    suites.put("core/data_deletion.yaml", processorSuite(List.of(VALIDATE)));
    suites.put(
        "core/data_model.yaml",
        new Supported(
            Map.of("data_model", new DataModelHandler()), reasons(), Set.of("data_model")));
    suites.put("core/data_model_pointers.yaml", processorSuite(List.of(VALIDATE)));
    suites.put("core/error_responses.yaml", processorSuite(List.of(VALIDATE)));
    suites.put(
        "core/expressions.yaml",
        new Supported(
            Map.of("parse_expression_template", new ParseExpressionTemplateHandler()),
            reasons("validate", NOT_YET),
            Set.of("parse_expression_template")));
    suites.put("core/functions.yaml", new Unsupported(NOT_YET));
    suites.put("core/index_function.yaml", new Unsupported(NOT_YET));
    suites.put("core/message_processor_scaling.yaml", new Unsupported(NOT_YET));
    suites.put("core/message_processor_v0_8.yaml", new Unsupported(V1_ONLY));
    suites.put("core/message_processor_v0_9.yaml", new Unsupported(V1_ONLY));
    suites.put(
        "core/message_processor_v1_0.yaml",
        processorSuite(
            List.of(PROCESS, VALIDATE),
            Map.of(
                "get_renderer_capabilities", new RendererCapabilitiesHandler(),
                "get_renderer_data_model", new RendererDataModelHandler())));
    suites.put("core/multi_catalog.yaml", new Unsupported(NOT_YET));
    suites.put("core/node_resolution.yaml", new Unsupported(NOT_YET));
    suites.put("core/reserved_keys.yaml", processorSuite(List.of(PROCESS, VALIDATE)));
    suites.put("core/rpc_functions.yaml", new Unsupported(NOT_YET));
    suites.put("core/validation_result.yaml", new Unsupported(NOT_YET));
    suites.put("core/validator_v0_8.yaml", new Unsupported(V1_ONLY));
    suites.put("core/validator_v0_9.yaml", new Unsupported(V1_ONLY));
    suites.put("core/validator_v1_0.yaml", processorSuite(List.of(VALIDATE)));
    return Collections.unmodifiableMap(suites);
  }
}
