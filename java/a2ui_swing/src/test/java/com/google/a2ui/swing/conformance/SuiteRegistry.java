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

package com.google.a2ui.swing.conformance;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.swing.catalog.basic.SwingBasicCatalog;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The list of the suites of the {@code adapter} corpus and the support that this harness gives to
 * each suite.
 */
final class SuiteRegistry {
  /** Runs one case. Throws if the adapter does not behave as the case expects. */
  @FunctionalInterface
  interface CaseHandler {
    void run(ConformanceCase testCase) throws Exception;
  }

  /** The support that this harness gives to a suite. */
  sealed interface SuiteSupport permits Supported, Unsupported {}

  /**
   * The harness knows the suite. It runs a case if the adapter has a view for each component of the
   * basic catalog that the messages of the case use. It records each other case as skipped, with
   * the names of the components that have no view.
   *
   * <p>A case whose action is not in {@code actions} fails. A case that needs no view that is
   * absent, and whose action has no handler, also fails. Thus a new case cannot stay hidden as a
   * skipped case.
   *
   * @param actions the actions of the suite
   * @param handlers the handler for each action that this harness runs
   */
  record Supported(Set<String> actions, Map<String, CaseHandler> handlers)
      implements SuiteSupport {}

  /**
   * The harness records each case of the suite as skipped.
   *
   * @param reason the reason for the skip
   */
  record Unsupported(String reason) implements SuiteSupport {}

  /** The protocol versions that the adapter shows. */
  static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of("1.0");

  /**
   * The components of the basic catalog that have a Swing view: 11 of the 18 components in this
   * version. The commit that adds the view of a component adds its name to the catalog.
   */
  static final Set<String> BASIC_VIEWS = SwingBasicCatalog.viewNames();

  /** The start of the reason to skip a case that uses a basic component that has no view. */
  static final String NEEDS_BASIC_VIEWS = "needs the Swing views of the basic catalog";

  private static final String V1_ONLY = "the Swing adapter shows protocol v1.0 only";

  /**
   * Each suite of the {@code adapter} corpus of {@code conformance/manifest_corpora.json}.
   *
   * <p>{@code SuiteRegistryTest} fails if the corpus and this map are different. Thus a new suite
   * cannot stay hidden.
   */
  static final Map<String, SuiteSupport> ADAPTER_SUITES = adapterSuites();

  private SuiteRegistry() {}

  private static Map<String, SuiteSupport> adapterSuites() {
    Map<String, SuiteSupport> suites = new LinkedHashMap<>();
    suites.put(
        "core/adapter_conformance.yaml",
        new Supported(
            Set.of("render_surface"), Map.of("render_surface", new RenderSurfaceHandler())));
    suites.put(
        "core/accessibility.yaml",
        new Supported(
            Set.of("accessibility_check"),
            Map.of("accessibility_check", new AccessibilityCheckHandler())));
    suites.put(
        "adapter/markdown_subset.yaml",
        new Supported(
            Set.of("render_markdown"), Map.of("render_markdown", new RenderMarkdownHandler())));
    suites.put("adapter/adapter_conformance_v0_9.yaml", new Unsupported(V1_ONLY));
    return Collections.unmodifiableMap(suites);
  }

  /**
   * Gets the reason to skip a case.
   *
   * @return the reason, or null if the case must run
   * @throws AssertionError if the suite does not have the action of the case
   */
  static String skipReason(ConformanceCase testCase, SuiteSupport support) {
    if (support instanceof Unsupported unsupported) {
      return unsupported.reason();
    }
    Supported supported = (Supported) support;
    if (!supported.actions().contains(testCase.action())) {
      throw new AssertionError(
          "Unknown action '" + testCase.action() + "' in " + testCase.location());
    }
    String version = testCase.protocolVersion();
    if (version != null && !SUPPORTED_PROTOCOL_VERSIONS.contains(version)) {
      return "Protocol version " + version + " is not supported; " + V1_ONLY;
    }
    Set<String> absent = new TreeSet<>(basicComponentsOf(testCase));
    absent.removeAll(BASIC_VIEWS);
    return absent.isEmpty() ? null : NEEDS_BASIC_VIEWS + ": " + String.join(", ", absent);
  }

  /**
   * Gets the handler for the action of a case that must run.
   *
   * @throws AssertionError if the harness has no handler for the action
   */
  static CaseHandler handlerFor(ConformanceCase testCase, Supported support) {
    CaseHandler handler = support.handlers().get(testCase.action());
    if (handler == null) {
      throw new AssertionError(
          "The harness has no handler for the action '"
              + testCase.action()
              + "', but the adapter has each view that "
              + testCase.location()
              + " needs.");
    }
    return handler;
  }

  /**
   * Gets the components of the basic catalog that the messages of a case use.
   *
   * @return the names of the component types, in alphabetical sequence
   */
  static Set<String> basicComponentsOf(ConformanceCase testCase) {
    Set<String> types = new TreeSet<>();
    collectMessages(testCase.fields(), types);
    if (testCase.fields().get("steps") instanceof List<?> steps) {
      steps.forEach(step -> collectMessages(step, types));
    }
    types.retainAll(BasicCatalog.api().components().keySet());
    return types;
  }

  /** Adds the type of each component of the messages of a case or of a step. */
  private static void collectMessages(Object holder, Set<String> types) {
    if (!(holder instanceof Map<?, ?> map) || !(map.get("messages") instanceof List<?> messages)) {
      return;
    }
    for (Object message : messages) {
      if (!(message instanceof Map<?, ?> envelope)) {
        continue;
      }
      for (String key : List.of("createSurface", "updateComponents")) {
        if (envelope.get(key) instanceof Map<?, ?> body
            && body.get("components") instanceof List<?> components) {
          for (Object component : components) {
            if (component instanceof Map<?, ?> fields
                && fields.get("component") instanceof String type) {
              types.add(type);
            }
          }
        }
      }
    }
  }
}
