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
import com.google.a2ui.core.internal.Json;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.validation.ValidationConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The action {@code measure_growth}: the handler measures the time to process a payload at two
 * sizes, n and 4n. The time at 4n divided by the time at n must not be more than {@code max_ratio}.
 *
 * <p>A time depends on the computer and on its load. Thus the usual run of the harness records
 * these cases as skipped, and {@code ScalingConformanceTest} runs them apart from the other tests.
 *
 * <p>The handler measures as {@code conformance/agent/direct_json/scaling.yaml} specifies. It makes
 * the catalogs and decodes the payloads before it starts a timer. Each timed run makes a processor
 * and gives it all the messages in one {@code processMessages} call. The handler does one run of
 * each size that it does not measure. Then it measures the two sizes in turn, {@link #RUNS} times
 * each, and compares the medians. It does not force a garbage collection.
 */
final class MeasureGrowthHandler implements CaseHandler {
  /** The number of measured runs of each size. */
  static final int RUNS = 7;

  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys(
          "action",
          "args",
          "catalogPaths",
          "max_ratio",
          "operation",
          "payload",
          "protocolVersion",
          "sizes");

  private static final Set<String> ARG_KEYS = Set.of("validation_config");

  private static final Set<String> SEGMENT_KEYS = Set.of("repeat", "separator");

  /**
   * Writes the payload of a case at one size. See {@code PayloadTemplate} in {@code
   * conformance/conformance_schema.json}.
   *
   * @param template the segments of the payload: a text, or a text to repeat with a separator
   * @param size the number of repetitions of each repeated segment
   */
  static String writePayload(List<Object> template, int size) {
    StringBuilder payload = new StringBuilder();
    for (Object segment : template) {
      if (segment instanceof String text) {
        payload.append(text);
        continue;
      }
      Map<String, Object> repeated = asObject(segment, "payload segment");
      KnownKeys.assertKnownKeys(repeated, SEGMENT_KEYS, "payload segment");
      String text = asString(repeated.get("repeat"), "repeat");
      String separator =
          repeated.get("separator") == null ? "" : asString(repeated.get("separator"), "separator");
      for (int i = 0; i < size; i++) {
        if (i > 0) {
          payload.append(separator);
        }
        payload.append(text.replace("{i}", Integer.toString(i)));
      }
    }
    return payload.toString();
  }

  @Override
  public void run(ConformanceCase testCase) {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    if (!"process_messages".equals(fields.get("operation"))) {
      throw new AssertionError(location + ": unknown operation '" + fields.get("operation") + "'");
    }
    Map<String, Object> args = asObject(fields.get("args"), "args in " + location);
    KnownKeys.assertKnownKeys(args, ARG_KEYS, "args in " + location);
    ValidationConfig config =
        switch (asString(args.get("validation_config"), "validation_config")) {
          case "relaxed" -> ValidationConfig.RELAXED;
          case "strict" -> ValidationConfig.STRICT;
          case "none" -> null;
          default ->
              throw new AssertionError(
                  location + ": unknown validation_config '" + args.get("validation_config") + "'");
        };
    List<Catalog<ComponentApi, FunctionApi>> catalogs = new ArrayList<>();
    for (Object path : asList(fields.get("catalogPaths"), "catalogPaths in " + location)) {
      Map<String, Object> document = CatalogCases.jsonFile(asString(path, "catalogPaths entry"));
      catalogs.add(
          Catalog.fromJson(
              document, asString(document.get("catalogId"), "catalogId"), ProtocolVersion.V1_0));
    }
    List<Object> sizes = asList(fields.get("sizes"), "sizes in " + location);
    if (sizes.size() != 2) {
      throw new AssertionError(location + ": a case has two sizes");
    }
    List<Object> template = asList(fields.get("payload"), "payload in " + location);
    int smaller = ((Number) sizes.get(0)).intValue();
    int larger = ((Number) sizes.get(1)).intValue();
    Object smallPayload = Json.parse(writePayload(template, smaller));
    Object largePayload = Json.parse(writePayload(template, larger));
    Runnable small = () -> new MessageProcessor<>(catalogs, config).processMessages(smallPayload);
    Runnable large = () -> new MessageProcessor<>(catalogs, config).processMessages(largePayload);

    small.run();
    large.run();
    long[] smallTimes = new long[RUNS];
    long[] largeTimes = new long[RUNS];
    for (int run = 0; run < RUNS; run++) {
      smallTimes[run] = time(small);
      largeTimes[run] = time(large);
    }
    double smallMedian = median(smallTimes);
    double largeMedian = median(largeTimes);
    double ratio = largeMedian / Math.max(smallMedian, 1);
    double maxRatio = ((Number) fields.get("max_ratio")).doubleValue();
    String measured =
        String.format(
            Locale.ROOT,
            "%s: process_messages at %d against %d: median %.0f us against %.0f us, ratio %.1f"
                + " (limit %.1f)",
            location,
            larger,
            smaller,
            largeMedian / 1000,
            smallMedian / 1000,
            ratio,
            maxRatio);
    System.out.println(measured);
    if (ratio > maxRatio) {
      throw new AssertionError(measured);
    }
  }

  private static long time(Runnable body) {
    long start = System.nanoTime();
    body.run();
    return System.nanoTime() - start;
  }

  private static double median(long[] times) {
    long[] sorted = times.clone();
    Arrays.sort(sorted);
    return sorted[sorted.length / 2];
  }
}
