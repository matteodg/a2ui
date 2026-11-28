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
import static com.google.a2ui.core.conformance.JsonValues.assertJsonEquals;

import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.conformance.SuiteRegistry.CaseHandler;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.validation.ValidationConfig;
import java.util.Map;
import java.util.Set;

/**
 * The action {@code get_renderer_data_model}: processes the messages of a case, and then compares
 * the data that the processor gives for the agent with the expected value. The expected value
 * {@code null} is the empty result.
 */
final class RendererDataModelHandler implements CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys(
          "action", "args", "catalogPaths", "expect", "messages", "protocolVersion", "steps");

  private static final Set<String> STEP_KEYS = Set.of("description", "messages");

  private static final Set<String> ARGUMENT_KEYS = Set.of("version");

  @Override
  public void run(ConformanceCase testCase) {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    Map<String, Object> args = asObject(fields.get("args"), "args in " + location);
    KnownKeys.assertKnownKeys(args, ARGUMENT_KEYS, "args in " + location);
    ProtocolVersion version = RendererCapabilitiesHandler.version(args, location);

    MessageProcessor<ComponentApi> processor =
        new MessageProcessor<>(
            ProcessMessagesHandler.catalogsFor(testCase), ValidationConfig.RELAXED);
    if (fields.get("messages") != null) {
      processor.processMessages(fields.get("messages"));
    }
    if (fields.get("steps") != null) {
      for (Object raw : asList(fields.get("steps"), "steps in " + location)) {
        Map<String, Object> step = asObject(raw, "step in " + location);
        KnownKeys.assertKnownKeys(step, STEP_KEYS, "step in " + location);
        processor.processMessages(step.get("messages"));
      }
    }
    assertJsonEquals(
        fields.get("expect"), processor.getRendererDataModel(version).orElse(null), location);
  }
}
