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

import static com.google.a2ui.swing.conformance.JsonValues.asList;
import static com.google.a2ui.swing.conformance.JsonValues.asObject;
import static com.google.a2ui.swing.conformance.JsonValues.asString;
import static com.google.a2ui.swing.conformance.JsonValues.assertJsonEquals;

import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.swing.conformance.RenderSurfaceHandler.TextSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the renderer part of the action {@code e2e_scenario} of the suites in {@code
 * conformance/e2e/}.
 *
 * <p>A scenario is a conversation between an agent and a renderer, in turns. The part of the agent
 * (the text of the model in {@code modelChunks}, the list {@code formats}) belongs to the harness
 * of an agent SDK. This handler does not read it. For each turn, the handler does these steps:
 *
 * <ol>
 *   <li>It gives the A2UI messages of {@code expectWire} to the message processor. The text parts
 *       are for the conversation, not for the renderer. A message that the processor rejects is not
 *       a failure of the turn: the processor sends an {@code error} message to the agent, and
 *       {@code expectOutbound} has it.
 *   <li>It compares the mounted {@code A2uiSurface} of each surface with {@code expectViewTree}. A
 *       null value means that the surface must not exist.
 *   <li>It does each operation of {@code uiOps} through the Swing controls, as {@code
 *       RenderSurfaceHandler} does for the steps of the adapter suite.
 *   <li>It compares the data models, the errors that the surfaces reported, and the messages for
 *       the agent of the full turn.
 * </ol>
 *
 * <p>Each message for the agent must be valid for the schema of the protocol, also in a turn that
 * has no {@code expectOutbound}.
 */
final class E2eScenarioHandler implements SuiteRegistry.CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys("action", "protocolVersion", "catalogPaths", "formats", "source", "turns");

  private static final Set<String> SOURCE_KEYS = Set.of("kind", "from", "model", "recordedAt");

  private static final Set<String> TURN_KEYS =
      Set.of(
          "userText",
          "modelChunks",
          "expectWire",
          "expectViewTree",
          "expectErrors",
          "uiOps",
          "expectDataModel",
          "expectOutbound");

  private static final Set<String> WIRE_PART_KEYS = Set.of("text", "a2ui");

  private static final Set<String> UI_OP_KEYS =
      Set.of("op", "surfaceId", "targetNode", "inputValue", "userActivation");

  /** The operations of a turn. A turn cannot process messages or unmount a surface. */
  private static final Set<String> UI_OPS =
      Set.of("user_input", "user_gesture", "programmatic_invoke");

  private final RenderSurfaceHandler steps = new RenderSurfaceHandler();

  @Override
  public void run(ConformanceCase testCase) {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    assertJsonEquals(
        List.of(AdapterSession.BASIC_CATALOG_PATH),
        fields.get("catalogPaths"),
        "catalogPaths in " + location);
    if (fields.containsKey("source")) {
      KnownKeys.assertKnownKeys(
          asObject(fields.get("source"), "source in " + location),
          SOURCE_KEYS,
          "source in " + location);
    }
    List<Object> turns = asList(fields.get("turns"), "turns in " + location);
    if (turns.isEmpty()) {
      throw new AssertionError(location + ": the scenario has no turns.");
    }
    // A scenario gives no validation configuration. The other renderer harnesses use the default
    // of their processor for it, and this harness does the same.
    AdapterSession session = new AdapterSession(location, null, null);
    try {
      for (int i = 0; i < turns.size(); i++) {
        String where = "turn " + i + " of " + location;
        Map<String, Object> turn = asObject(turns.get(i), where);
        KnownKeys.assertKnownKeys(turn, TURN_KEYS, where);
        runTurn(session, turn, where);
      }
    } finally {
      session.unmountAll();
    }
    if (!session.modals.open().isEmpty()) {
      throw new AssertionError(location + ": a modal is open after the unmount of the surfaces.");
    }
  }

  private void runTurn(AdapterSession session, Map<String, Object> turn, String where) {
    // The records of the session are for the full turn: the messages and each operation.
    session.startStep();
    List<Object> wire = asList(turn.get("expectWire"), "expectWire of " + where);
    for (int i = 0; i < wire.size(); i++) {
      String at = "expectWire " + i + " of " + where;
      Map<String, Object> part = asObject(wire.get(i), at);
      KnownKeys.assertKnownKeys(part, WIRE_PART_KEYS, at);
      if (part.containsKey("a2ui")) {
        try {
          session.process(asList(part.get("a2ui"), at));
        } catch (A2uiError rejected) {
          // The processor sent an error message to the agent. The turn expects it below.
          session.settle();
        }
      }
    }

    if (turn.containsKey("expectViewTree")) {
      String at = "expectViewTree of " + where;
      asObject(turn.get("expectViewTree"), at)
          .forEach((surfaceId, tree) -> {
            if (tree == null) {
              if (session.processor.model().getSurface(surfaceId).isPresent()) {
                throw new AssertionError(at + ": the surface '" + surfaceId + "' exists.");
              }
              return;
            }
            session.select(surfaceId, at);
            steps.assertViewNode(
                session,
                asObject(tree, surfaceId + " in " + at),
                session.tree(),
                TextSource.MARKDOWN,
                surfaceId + " in " + at);
          });
    }

    if (turn.containsKey("uiOps")) {
      List<Object> ops = asList(turn.get("uiOps"), "uiOps of " + where);
      for (int i = 0; i < ops.size(); i++) {
        String at = "uiOp " + i + " of " + where;
        Map<String, Object> op = asObject(ops.get(i), at);
        KnownKeys.assertKnownKeys(op, UI_OP_KEYS, at);
        String name = asString(op.get("op"), "op of " + at);
        if (!UI_OPS.contains(name)) {
          throw new AssertionError("Unknown op '" + name + "' in " + at);
        }
        session.select(asString(op.get("surfaceId"), "surfaceId of " + at), at);
        Map<String, Object> step = new LinkedHashMap<>(op);
        step.remove("surfaceId");
        steps.operate(session, name, step, at);
        session.settle();
      }
    }

    if (turn.containsKey("expectDataModel")) {
      String at = "expectDataModel of " + where;
      asObject(turn.get("expectDataModel"), at)
          .forEach((surfaceId, data) ->
              assertJsonEquals(
                  data,
                  session
                      .processor
                      .model()
                      .getSurface(surfaceId)
                      .orElseThrow(() ->
                          new AssertionError(at + ": there is no surface '" + surfaceId + "'."))
                      .dataModel()
                      .get("/"),
                  surfaceId + " in " + at));
    }
    if (turn.containsKey("expectErrors")) {
      String at = "expectErrors of " + where;
      RenderSurfaceHandler.assertErrors(session, asList(turn.get("expectErrors"), at), at);
    }
    for (int i = 0; i < session.outbound.size(); i++) {
      OutboundSchema.assertValid(session.outbound.get(i), "message " + i + " of " + where);
    }
    if (turn.containsKey("expectOutbound")) {
      String at = "expectOutbound of " + where;
      RenderSurfaceHandler.assertOutbound(
          asList(turn.get("expectOutbound"), at), session.outbound, at);
    }
  }
}
