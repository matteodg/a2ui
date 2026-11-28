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

package com.google.a2ui.core.resolution;

import static com.google.a2ui.core.resolution.NodeFixture.call;
import static com.google.a2ui.core.resolution.NodeFixture.component;
import static com.google.a2ui.core.resolution.NodeFixture.object;
import static com.google.a2ui.core.resolution.NodeFixture.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.exceptions.A2uiRpcError;
import com.google.a2ui.core.exceptions.RpcErrorCode;
import com.google.a2ui.core.state.RendererAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** A node sends a call that its catalog does not implement to the caller of its surface. */
class NodeResolverAgentCallTest {
  private final NodeFixture fixture = new NodeFixture();

  /** The calls that the caller received, in their sequence. */
  private final List<Map<String, Object>> asked = new ArrayList<>();

  /** The future of each call that the caller received, in the same sequence. */
  private final List<CompletableFuture<Object>> futures = new ArrayList<>();

  /** Makes the surface with data, sets the caller and then shows the components. */
  private ComponentNode<ComponentApi> mount(Map<String, Object> data, Object... components) {
    NodeResolver<ComponentApi> resolver =
        fixture.resolve(data, component("root", "Text", "text", "Not mounted"));
    fixture
        .surface()
        .setAgentFunctionCaller(call -> {
          asked.add(call);
          CompletableFuture<Object> future = new CompletableFuture<>();
          futures.add(future);
          return future;
        });
    fixture.update(components);
    return resolver.rootNode().get().orElseThrow();
  }

  private static A2uiRpcError failure(String message) {
    return new A2uiRpcError(message, RpcErrorCode.EXECUTION_ERROR);
  }

  @Test
  void aBindingIsNullUntilTheAgentAnswersAndThenTheNodeResolvesAgain() {
    ComponentNode<ComponentApi> root =
        mount(
            Map.of("id", 7L),
            component("root", "Text", "text", call("fetchTitle", "id", path("/id"))));
    int[] emissions = NodeFixture.countProps(root);
    int before = emissions[0];

    assertNull(root.props().get().value("text"));
    assertEquals("fetchTitle", asked.getFirst().get("@call"));
    assertEquals(Map.of("id", 7L), asked.getFirst().get("args"));
    futures.getFirst().complete("Hello");

    assertEquals("Hello", root.props().get().value("text"));
    assertEquals(before + 1, emissions[0]);
    assertEquals(List.of(), fixture.errors);
  }

  @Test
  void anAnswerOfTheAgentIsTheArgumentOfALocalFunction() {
    ComponentNode<ComponentApi> root =
        mount(
            Map.of(),
            component(
                "root", "Text", "text", call("formatString", "value", call("fetchGreeting"))));

    assertNull(root.props().get().value("text"));
    futures.getFirst().complete("Hello");

    assertEquals("Hello", root.props().get().value("text"));
    assertEquals(1, asked.size());
  }

  @Test
  void aNodeSendsAFailedCallAgainOnlyWhenTheCallChanges() {
    ComponentNode<ComponentApi> root =
        mount(
            Map.of("id", 7L, "label", "Rate"),
            component(
                "root",
                "Text",
                "text",
                call(
                    "outer",
                    "label",
                    path("/label"),
                    "rate",
                    call("fetchRate", "id", path("/id")))));

    futures.getFirst().completeExceptionally(failure("The rate feed is offline."));
    fixture.setData("/label", "Exchange rate");

    assertNull(root.props().get().value("text"));
    assertEquals(1, asked.size());
    assertEquals(1, fixture.errors.size());
    assertEquals("The rate feed is offline.", fixture.errors.getFirst().getMessage());
    assertEquals(Optional.of("EXPRESSION_ERROR"), fixture.errors.getFirst().errorCode());

    fixture.setData("/id", 8L);

    assertEquals(2, asked.size());
    assertEquals(Map.of("id", 8L), asked.getLast().get("args"));
  }

  @Test
  void aCancelledCallReportsNothingAndTheNodeSendsItAgain() {
    ComponentNode<ComponentApi> root =
        mount(
            Map.of("label", "Rate"),
            component(
                "root",
                "Text",
                "text",
                call("outer", "label", path("/label"), "rate", call("fetchRate"))));

    futures.getFirst().cancel(false);
    assertEquals(1, asked.size());
    fixture.setData("/label", "Exchange rate");

    assertEquals(2, asked.size());
    assertNull(root.props().get().value("text"));
    assertEquals(List.of(), fixture.errors);
  }

  @Test
  void aCheckPassesWhileItWaitsAndFailsIfTheAgentFailsTheCall() {
    ComponentNode<ComponentApi> root =
        mount(
            Map.of(),
            component(
                "root",
                "TextField",
                "label",
                "Name",
                "value",
                path("/name"),
                "@checks",
                List.of(object("condition", call("isFree", "name", "ada"), "message", "Taken."))));

    assertTrue(root.props().get().isValid());
    futures.getFirst().completeExceptionally(failure("The directory is offline."));

    assertFalse(root.props().get().isValid());
    assertEquals(List.of("Taken."), root.props().get().validationErrors());
    assertEquals(1, fixture.errors.size());
  }

  @Test
  void aTemplatePartDoesNotWaitForTheAgent() {
    ComponentNode<ComponentApi> root =
        mount(
            Map.of(),
            component(
                "root", "Text", "text", call("formatString", "value", "Rate: ${fetchRate()}")));

    assertEquals("Rate: ", root.props().get().value("text"));
    assertEquals(List.of(), asked);
    assertEquals(1, fixture.errors.size());
    assertTrue(fixture.errors.getFirst().getMessage().contains("'fetchRate'"));
    assertEquals(Optional.of("EXPRESSION_ERROR"), fixture.errors.getFirst().errorCode());
  }

  @Test
  void aNodeIgnoresTheAnswerToACallThatItMakesNoMore() {
    ComponentNode<ComponentApi> root =
        mount(Map.of(), component("root", "Text", "text", call("fetchTitle")));
    int[] emissions = NodeFixture.countProps(root);
    int before = emissions[0];

    fixture.update(component("root", "Text", "text", "static"));
    futures.getFirst().complete("late");

    assertEquals("static", root.props().get().value("text"));
    assertEquals(before + 1, emissions[0]);
  }

  @Test
  void anEventResolvesItsContextEntriesInTheirSequence() {
    Map<String, Object> context = object("rate", call("fetchRate"), "stock", call("lookupSku"));
    ComponentNode<ComponentApi> root =
        mount(
            Map.of(),
            component(
                "root",
                "Button",
                "child",
                "label",
                "action",
                Map.of("event", object("name", "buy", "context", context))),
            component("label", "Text", "text", "Buy"));
    List<RendererAction> actions = fixture.actions;

    root.props().get().action("action").orElseThrow().invoke();
    assertEquals(1, asked.size());
    futures.get(0).complete(1.08);
    assertEquals(2, asked.size());
    assertEquals("lookupSku", asked.get(1).get("@call"));
    assertEquals(List.of(), actions);
    futures.get(1).complete(3L);

    assertEquals(1, actions.size());
    assertEquals(object("rate", 1.08, "stock", 3L), actions.getFirst().context());
    assertEquals(List.of(), fixture.errors);
  }

  @Test
  void anEventStopsAtTheFirstCallThatTheAgentFails() {
    Map<String, Object> context = object("rate", call("fetchRate"), "stock", call("lookupSku"));
    ComponentNode<ComponentApi> root =
        mount(
            Map.of(),
            component(
                "root",
                "Button",
                "child",
                "label",
                "action",
                Map.of("event", object("name", "buy", "context", context))),
            component("label", "Text", "text", "Buy"));

    root.props().get().action("action").orElseThrow().invoke();
    futures.getFirst().completeExceptionally(failure("The rate feed is offline."));

    assertEquals(1, asked.size());
    assertEquals(List.of(), fixture.actions);
    assertEquals(1, fixture.errors.size());
    assertEquals("The rate feed is offline.", fixture.errors.getFirst().getMessage());
  }

  @Test
  void anActionSendsEachCallAsARequestOfItsOwn() {
    ComponentNode<ComponentApi> root =
        mount(
            Map.of(),
            component(
                "root",
                "Button",
                "child",
                "label",
                "action",
                Map.of("functionCall", call("submitOrder", "id", "o-1"))),
            component("label", "Text", "text", "Buy"));

    root.props().get().action("action").orElseThrow().invoke();
    root.props().get().action("action").orElseThrow().invoke();
    futures.get(0).complete(true);
    root.props().get().action("action").orElseThrow().invoke();
    futures.get(1).completeExceptionally(failure("The order was rejected."));

    assertEquals(3, asked.size());
    assertEquals(1, fixture.errors.size());
    assertEquals("The order was rejected.", fixture.errors.getFirst().getMessage());
    assertEquals(Optional.of("EXPRESSION_ERROR"), fixture.errors.getFirst().errorCode());
  }
}
