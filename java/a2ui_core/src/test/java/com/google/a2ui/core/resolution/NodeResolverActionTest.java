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
import static com.google.a2ui.core.resolution.NodeFixture.countProps;
import static com.google.a2ui.core.resolution.NodeFixture.object;
import static com.google.a2ui.core.resolution.NodeFixture.path;
import static com.google.a2ui.core.resolution.NodeFixture.template;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.catalog.A2uiMetadata;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.exceptions.A2uiValidationError;
import com.google.a2ui.core.state.RendererAction;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The behavior of the actions that the node resolver makes. */
class NodeResolverActionTest {
  private final NodeFixture fixture = new NodeFixture();

  private static Map<String, Object> open(String url) {
    return Map.of("functionCall", call("openUrl", "url", url));
  }

  private ComponentNode<ComponentApi> button(Map<String, Object> data, Map<String, Object> action) {
    return fixture.root(
        data,
        component("root", "Button", "child", "label", "action", action),
        component("label", "Text", "text", "Go"));
  }

  private static NodeAction action(ComponentNode<ComponentApi> node) {
    return node.props().get().action("action").orElseThrow();
  }

  @Test
  void anEventActionReportsTheContextAndTheUserMessageOfTheTimeOfTheCall() {
    Map<String, Object> event =
        object(
            "name", "save",
            "context", object("who", path("/name"), "fixed", 1L),
            "userMessage", call("formatString", "value", "Saved ${/name}"));
    ComponentNode<ComponentApi> root = button(Map.of("name", "Ada"), Map.of("event", event));
    NodeAction action = action(root);
    int[] emissions = countProps(root);

    fixture.setData("/name", "Grace");

    assertEquals(0, emissions[0]);
    assertSame(action, action(root));
    assertEquals(List.of(), fixture.actions);

    action.invoke();

    assertEquals(1, fixture.actions.size());
    RendererAction sent = fixture.actions.getFirst();
    assertEquals("save", sent.name());
    assertEquals(object("who", "Grace", "fixed", 1L), sent.context());
    assertEquals(Optional.of("Saved Grace"), sent.userMessage());
    assertEquals("root", sent.sourceComponentId());
    assertEquals(Optional.empty(), sent.catalogId());
    assertEquals(Optional.empty(), sent.metadata());
  }

  @Test
  void anActionSendsTheMetadataOfItsOptions() {
    ComponentNode<ComponentApi> root = button(Map.of(), Map.of("event", Map.of("name", "save")));
    A2uiMetadata metadata = new A2uiMetadata(Optional.of(Map.of("com_example_signed", true)));
    A2uiMetadata bad = new A2uiMetadata(Optional.of(Map.of("not an identifier", 1L)));

    action(root).invoke(ActionDispatchOptions.withMetadata(metadata));

    assertEquals(1, fixture.actions.size());
    assertEquals(Optional.of(metadata), fixture.actions.getFirst().metadata());
    assertEquals(Map.of(), fixture.actions.getFirst().context());

    // Metadata that is not correct is an error of the caller. Thus the surface reports nothing.
    assertThrows(
        A2uiValidationError.class,
        () -> action(root).invoke(ActionDispatchOptions.withMetadata(bad)));

    assertEquals(1, fixture.actions.size());
    assertEquals(List.of(), fixture.errors);
  }

  @Test
  void anActionOfATemplateItemResolvesItsContextAtTheScopeOfTheItem() {
    Map<String, Object> event =
        object("name", "pick", "context", object("who", path("name"), "row", call("@index")));
    ComponentNode<ComponentApi> root =
        fixture.root(
            Map.of("people", List.of(Map.of("name", "Ada"), Map.of("name", "Lin"))),
            component("root", "Column", "children", template("pick", "/people")),
            component("pick", "Button", "child", "label", "action", Map.of("event", event)),
            component("label", "Text", "text", path("name")));
    List<ComponentNode<ComponentApi>> buttons = root.props().get().children("children");

    action(buttons.get(1)).invoke();

    assertEquals(object("who", "Lin", "row", 1L), fixture.actions.getFirst().context());
    assertEquals("pick", fixture.actions.getFirst().sourceComponentId());
    assertNotEquals(action(buttons.get(0)), action(buttons.get(1)));
  }

  @Test
  void aFunctionActionRunsWithAUserGestureAndReportsNoAction() {
    ComponentNode<ComponentApi> root = button(Map.of(), open("https://example.com/a"));

    assertEquals(List.of(), fixture.opened);

    action(root).invoke();

    assertEquals(List.of(URI.create("https://example.com/a")), fixture.opened);
    assertEquals(List.of(), fixture.actions);
    assertEquals(List.of(), fixture.errors);
  }

  @Test
  void aFunctionActionWithNoUserGestureDoesNotRunAFunctionThatRequiresOne() {
    ComponentNode<ComponentApi> root = button(Map.of(), open("https://example.com/a"));

    action(root).invoke(ActionDispatchOptions.WITHOUT_USER_ACTIVATION);

    assertEquals(List.of(), fixture.opened);
    assertEquals(List.of(), fixture.actions);
    assertEquals(1, fixture.errors.size());
    A2uiError error = fixture.errors.getFirst();
    assertInstanceOf(A2uiExpressionError.class, error);
    assertTrue(error.getMessage().contains("openUrl"), error.getMessage());
    assertEquals(Optional.of("EXPRESSION_ERROR"), error.errorCode());
    assertEquals(Optional.of("s"), error.surfaceId());

    // The same action runs the function when the options tell that the user operated the view.
    action(root).invoke(ActionDispatchOptions.WITHOUT_USER_ACTIVATION.withUserActivation(true));

    assertEquals(List.of(URI.create("https://example.com/a")), fixture.opened);
    assertEquals(1, fixture.errors.size());
  }

  @Test
  void anEventActionWithNoUserGestureReportsItsActionAndKeepsItsMetadata() {
    ComponentNode<ComponentApi> root = button(Map.of(), Map.of("event", Map.of("name", "save")));
    A2uiMetadata metadata = new A2uiMetadata(Optional.of(Map.of("com_example_signed", true)));

    action(root).invoke(ActionDispatchOptions.withMetadata(metadata).withUserActivation(false));

    assertEquals(1, fixture.actions.size());
    assertEquals(Optional.of(metadata), fixture.actions.getFirst().metadata());
    assertEquals(List.of(), fixture.errors);
  }

  @Test
  void theOptionsOfACallAreTheResultOfAUserGestureUnlessTheyTellTheOpposite() {
    A2uiMetadata metadata = new A2uiMetadata(Optional.of(Map.of("com_example_signed", true)));

    assertTrue(ActionDispatchOptions.NONE.userActivation());
    assertTrue(ActionDispatchOptions.withMetadata(metadata).userActivation());
    assertTrue(new ActionDispatchOptions(Optional.of(metadata)).userActivation());
    assertEquals(Optional.empty(), ActionDispatchOptions.WITHOUT_USER_ACTIVATION.metadata());
    assertEquals(
        new ActionDispatchOptions(Optional.empty(), false),
        ActionDispatchOptions.WITHOUT_USER_ACTIVATION);
  }

  @Test
  void aFunctionActionThatFailsReportsItsFailure() {
    ComponentNode<ComponentApi> root = button(Map.of(), open("https://example.com/a"));
    int[] emissions = countProps(root);

    fixture.update(
        component("root", "Button", "child", "label", "action", open("javascript:alert(1)")));
    action(root).invoke();

    assertEquals(1, emissions[0]);
    assertEquals(List.of(), fixture.opened);
    assertEquals(1, fixture.errors.size());
    assertEquals(Optional.of("s"), fixture.errors.getFirst().surfaceId());
    assertEquals(Optional.of("EXECUTION_ERROR"), fixture.errors.getFirst().errorCode());
  }

  @Test
  void aFunctionThatThrowsInAnActionIsAnExecutionError() {
    Map<String, Object> event =
        object("name", "save", "context", object("first", Map.of("@call", "crash"), "second", 1L));

    ComponentNode<ComponentApi> root =
        button(Map.of(), Map.of("functionCall", Map.of("@call", "crash")));

    action(root).invoke();
    fixture.update(component("root", "Button", "child", "label", "action", Map.of("event", event)));
    action(root).invoke();

    assertEquals(List.of(), fixture.actions);
    assertEquals(2, fixture.errors.size());
    for (A2uiError error : fixture.errors) {
      assertInstanceOf(A2uiExpressionError.class, error);
      assertEquals("Function 'crash' failed: The disk is full.", error.getMessage());
      assertEquals(Optional.of("EXECUTION_ERROR"), error.errorCode());
      assertEquals(Optional.of("s"), error.surfaceId());
    }
  }

  @Test
  void aCallThatTheContextRefusesInAnActionIsAnExpressionError() {
    // The function @index has no value outside a list template. Thus no function runs.
    action(button(Map.of(), Map.of("functionCall", call("@index")))).invoke();

    assertEquals(1, fixture.errors.size());
    assertEquals(Optional.of("EXPRESSION_ERROR"), fixture.errors.getFirst().errorCode());
  }

  @Test
  void aContextEntryThatCannotBeResolvedStopsTheEvent() {
    Map<String, Object> event =
        object("name", "save", "context", object("row", call("@index"), "fixed", 1L));

    action(button(Map.of(), Map.of("event", event))).invoke();

    assertEquals(List.of(), fixture.actions);
    assertEquals(1, fixture.errors.size());
    A2uiError error = fixture.errors.getFirst();
    assertTrue(
        error.getMessage().startsWith("Action event context entry 'row' is invalid: "),
        error.getMessage());
    assertEquals(Optional.of("EXPRESSION_ERROR"), error.errorCode());
  }

  @Test
  void aUserMessageThatIsNotTextIsAnInvalidAction() {
    Map<String, Object> event = object("name", "save", "userMessage", path("/note"));
    ComponentNode<ComponentApi> root = button(Map.of("note", "Saved"), Map.of("event", event));

    action(root).invoke();
    fixture.setData("/note", null);
    action(root).invoke();

    // A user message with no value is absent, and the surface reports the action.
    assertEquals(
        List.of(Optional.of("Saved"), Optional.empty()),
        fixture.actions.stream().map(RendererAction::userMessage).toList());
    assertEquals(List.of(), fixture.errors);

    fixture.setData("/note", 7L);
    action(root).invoke();

    assertEquals(2, fixture.actions.size());
    assertEquals(1, fixture.errors.size());
    assertInstanceOf(A2uiValidationError.class, fixture.errors.getFirst());
    assertEquals(Optional.of("INVALID_ACTION"), fixture.errors.getFirst().errorCode());
  }

  @Test
  void anEventWithNoNameAndAnActionWithNoContentAreInvalidActions() {
    ComponentNode<ComponentApi> root = button(Map.of(), Map.of("event", Map.of("name", "save")));

    // The message processor rejects these actions. Thus the test writes the component directly.
    fixture.put("root", "Button", "child", "label", "action", Map.of("event", Map.of()));
    action(root).invoke();
    fixture.put("root", "Button", "child", "label", "action", Map.of());
    action(root).invoke();

    assertEquals(List.of(), fixture.actions);
    assertEquals(
        List.of(Optional.of("INVALID_ACTION"), Optional.of("INVALID_ACTION")),
        fixture.errors.stream().map(A2uiError::errorCode).toList());
  }

  @Test
  void anActionOfADisposedNodeDoesNothing() {
    NodeResolver<ComponentApi> resolver =
        fixture.resolve(
            Map.of(),
            component("root", "Button", "child", "label", "action", open("https://example.com/a")),
            component("label", "Text", "text", "Go"));
    NodeAction action = action(resolver.rootNode().get().orElseThrow());

    resolver.dispose();
    action.invoke();

    assertEquals(List.of(), fixture.opened);
  }
}
