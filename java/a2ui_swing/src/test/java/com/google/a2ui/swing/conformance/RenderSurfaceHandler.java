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

import static com.google.a2ui.swing.conformance.JsonValues.asList;
import static com.google.a2ui.swing.conformance.JsonValues.asObject;
import static com.google.a2ui.swing.conformance.JsonValues.asString;
import static com.google.a2ui.swing.conformance.JsonValues.assertJsonEquals;

import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.resolution.ActionDispatchOptions;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.core.resolution.ValidationResult;
import com.google.a2ui.core.resolution.ValidationSeverity;
import com.google.a2ui.core.state.RendererAction;
import com.google.a2ui.core.validation.ValidationConfig;
import com.google.a2ui.swing.surface.MountedNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.accessibility.AccessibleAction;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.text.JTextComponent;

/**
 * Runs the action {@code render_surface} of {@code core/adapter_conformance.yaml}.
 *
 * <p>The handler mounts an {@code A2uiSurface} with no window and runs the steps of the case. It
 * reads each expectation from the mounted Swing components where a component shows the value: the
 * text of a text component, the value of a control, the messages of the checks, the state of a
 * button. A step operates the Swing control: it sets the text of a text field, it clicks a button,
 * a check box or the options of a choice picker, it sets the accessible value of a slider, and it
 * selects a tab of a tabbed pane. The value of an option is its action command, the value of a
 * slider is its accessible value, and the value of a date field is the value of its formatted text
 * field.
 *
 * <p>An input step for a component type that has no view in this version fails. The registry skips
 * the cases that need such a component.
 */
final class RenderSurfaceHandler implements SuiteRegistry.CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys(
          "action", "protocolVersion", "catalogPaths", "catalogs", "strictMode", "steps");

  private static final Set<String> OPS =
      Set.of(
          "process_messages",
          "user_input",
          "user_gesture",
          "programmatic_invoke",
          "unmount_surface");

  private static final Set<String> STEP_KEYS =
      Set.of(
          "op",
          "messages",
          "targetNode",
          "inputValue",
          "userActivation",
          "expectViewTree",
          "expectSameViewIdentity",
          "expectNewViewIdentity",
          "expectDisposedNodes",
          "expectSurfaceRemoved",
          "expectDataModel",
          "expectActions",
          "expectErrors",
          "expectAccessibilityTree",
          "expectThemeAbsent",
          "expectActiveSubscriptions",
          "expectOutbound");

  /** The keys of a view node that each view has. Each other key is a property of the view. */
  private static final Set<String> VIEW_NODE_KEYS =
      Set.of(
          "id",
          "type",
          "state",
          "occupiesLayout",
          "text",
          "value",
          "url",
          "name",
          "axis",
          "badgeText",
          "isValid",
          "validationErrors",
          "validationResults",
          "hints",
          "children");

  private static final Set<String> ACTION_KEYS =
      Set.of("name", "surfaceId", "sourceComponentId", "context");

  private static final Set<String> ERROR_KEYS = Set.of("code", "surfaceId", "message");

  @Override
  public void run(ConformanceCase testCase) {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);
    if (fields.containsKey("catalogPaths")) {
      assertJsonEquals(
          List.of(AdapterSession.BASIC_CATALOG_PATH),
          fields.get("catalogPaths"),
          "catalogPaths in " + location);
    }
    // A case that does not ask for the strict checks changes its components in steps. Then a
    // component can be an orphan for a time. A case that has no value shows what a surface
    // has, also a cycle. The processor accepts a cycle only with no configuration, and then the
    // node resolver reports it.
    ValidationConfig config =
        switch (fields.get("strictMode")) {
          case null -> null;
          case Boolean strict -> strict ? ValidationConfig.STRICT : ValidationConfig.RELAXED;
          default -> throw new AssertionError("strictMode in " + location + " is not a boolean.");
        };
    AdapterSession session = new AdapterSession(location, fields.get("catalogs"), config);
    List<Object> steps = asList(fields.get("steps"), "steps in " + location);
    for (int i = 0; i < steps.size(); i++) {
      String where = "step " + i + " of " + location;
      Map<String, Object> step = asObject(steps.get(i), where);
      KnownKeys.assertKnownKeys(step, STEP_KEYS, where);
      runStep(session, step, where);
    }
    if (session.view != null && session.view.isMounted()) {
      session.view.removeNotify();
    }
    // The view of a modal closes its content when the surface goes away.
    if (!session.modals.open().isEmpty()) {
      throw new AssertionError(location + ": a modal is open after the unmount of the surface.");
    }
  }

  private void runStep(AdapterSession session, Map<String, Object> step, String where) {
    String op = asString(step.get("op"), "op of " + where);
    if (!OPS.contains(op)) {
      throw new AssertionError("Unknown op '" + op + "' in " + where);
    }
    // The identities and the nodes of the tree before the step, for the expectations after it.
    Map<String, JComponent> before = new LinkedHashMap<>();
    for (String key : List.of("expectSameViewIdentity", "expectNewViewIdentity")) {
      if (step.containsKey(key)) {
        for (Object path : asList(step.get(key), key + " of " + where)) {
          before.put((String) path, componentAt(session, (String) path, where));
        }
      }
    }
    Map<String, MountedNode> leaving = new LinkedHashMap<>();
    if (step.containsKey("expectDisposedNodes")) {
      for (Object id : asList(step.get("expectDisposedNodes"), "expectDisposedNodes of " + where)) {
        leaving.put((String) id, session.find((String) id));
      }
    }

    session.startStep();
    switch (op) {
      case "process_messages" -> session.process(step.get("messages"));
      case "user_input" -> userInput(session, step, where);
      case "user_gesture" -> {
        if (step.containsKey("userActivation")) {
          assertJsonEquals(true, step.get("userActivation"), "userActivation of " + where);
        }
        JComponent control = session.control(session.find(target(step, where)));
        // A control that is not a button has an accessible action if the user can operate it, as
        // the trigger of a modal that has no button.
        AccessibleAction accessible =
            control instanceof AbstractButton
                ? null
                : control.getAccessibleContext().getAccessibleAction();
        if (!(control instanceof AbstractButton) && accessible == null) {
          throw new AssertionError(where + ": the harness cannot operate " + control.getClass());
        }
        session.gesture = true;
        try {
          if (control instanceof AbstractButton button) {
            button.doClick(0);
          } else if (!accessible.doAccessibleAction(0)) {
            throw new AssertionError(where + ": the control did not do its action.");
          }
        } finally {
          session.gesture = false;
        }
      }
      case "programmatic_invoke" -> {
        assertJsonEquals(false, step.get("userActivation"), "userActivation of " + where);
        ComponentNode<?> node = session.find(target(step, where)).node();
        node.props()
            .get()
            .action("action")
            .orElseThrow(() -> new AssertionError(where + ": the component has no action."))
            .invoke(ActionDispatchOptions.WITHOUT_USER_ACTIVATION);
      }
      case "unmount_surface" -> {
        if (session.view.activeSubscriptionCount() <= 0) {
          throw new AssertionError(where + ": the mounted surface has no subscriptions.");
        }
        session.view.removeNotify();
      }
      default -> throw new AssertionError("Unknown op '" + op + "' in " + where);
    }
    session.settle();
    expect(session, step, before, leaving, where);
  }

  private static String target(Map<String, Object> step, String where) {
    return asString(step.get("targetNode"), "targetNode of " + where);
  }

  /** Writes a value through the Swing control of a component, as the user does. */
  private void userInput(AdapterSession session, Map<String, Object> step, String where) {
    MountedNode node = session.find(target(step, where));
    Object value = step.get("inputValue");
    if ((node.node().type().equals("TextField") || node.node().type().equals("DateTimeInput"))
        && session.control(node) instanceof JTextComponent field) {
      // The user types the text. The field of a DateTimeInput accepts ISO 8601 text.
      session.gesture = true;
      try {
        field.setText(asString(value, "inputValue of " + where));
      } finally {
        session.gesture = false;
      }
      return;
    }
    if (node.node().type().equals("CheckBox") && session.control(node) instanceof JCheckBox box) {
      if (!(value instanceof Boolean checked)) {
        throw new AssertionError(where + ": '" + value + "' is not the state of a check box.");
      }
      // The user clicks the check box if it does not show the state.
      if (box.isSelected() != checked) {
        click(session, box);
      }
      return;
    }
    if (node.node().type().equals("ChoicePicker")) {
      // The input value is the list of the values that the user selects. The user first selects
      // each wanted option, and then clears each other selected option.
      List<Object> wanted = asList(value, "inputValue of " + where);
      List<AbstractButton> options = AdapterSession.optionsOf(session.control(node));
      for (AbstractButton option : options) {
        if (!option.isSelected() && wanted.contains(option.getActionCommand())) {
          click(session, option);
        }
      }
      for (AbstractButton option : options) {
        if (option.isSelected() && !wanted.contains(option.getActionCommand())) {
          click(session, option);
        }
      }
      return;
    }
    if (node.node().type().equals("Slider") && session.control(node) instanceof JSlider slider) {
      // The accessible value of the slider is the number of the component. An assistive tool
      // moves the slider with it, as the user does with the mouse or with the keys.
      if (!(value instanceof Number number)) {
        throw new AssertionError(where + ": '" + value + "' is not a number.");
      }
      session.gesture = true;
      try {
        if (!slider.getAccessibleContext().getAccessibleValue().setCurrentAccessibleValue(number)) {
          throw new AssertionError(where + ": the slider did not accept " + number);
        }
      } finally {
        session.gesture = false;
      }
      return;
    }
    if (node.node().type().equals("Tabs") && session.control(node) instanceof JTabbedPane tabs) {
      // The input value of a Tabs component is the index of the tab that the user selects.
      if (!(value instanceof Number index)
          || index.intValue() < 0
          || index.intValue() >= tabs.getTabCount()) {
        throw new AssertionError(where + ": '" + value + "' is not the index of a tab.");
      }
      session.gesture = true;
      try {
        tabs.setSelectedIndex(index.intValue());
      } finally {
        session.gesture = false;
      }
      return;
    }
    throw new AssertionError(
        where + ": the harness has no input for the component type '" + node.node().type() + "'.");
  }

  /** Clicks a button as the user does. */
  private static void click(AdapterSession session, AbstractButton button) {
    session.gesture = true;
    try {
      button.doClick(0);
    } finally {
      session.gesture = false;
    }
  }

  private void expect(
      AdapterSession session,
      Map<String, Object> step,
      Map<String, JComponent> before,
      Map<String, MountedNode> leaving,
      String where) {
    if (step.containsKey("expectViewTree")) {
      assertViewNode(
          session,
          asObject(step.get("expectViewTree"), "expectViewTree of " + where),
          session.tree(),
          "expectViewTree of " + where);
    }
    if (step.containsKey("expectSameViewIdentity")) {
      for (Object path : asList(step.get("expectSameViewIdentity"), where)) {
        if (before.get(path) != componentAt(session, (String) path, where)) {
          throw new AssertionError(where + ": the view at '" + path + "' is a new object.");
        }
      }
    }
    if (step.containsKey("expectNewViewIdentity")) {
      for (Object path : asList(step.get("expectNewViewIdentity"), where)) {
        if (before.get(path) == componentAt(session, (String) path, where)) {
          throw new AssertionError(where + ": the view at '" + path + "' is the same object.");
        }
      }
    }
    leaving.forEach((id, old) -> {
      if (!old.node().isDisposed()) {
        throw new AssertionError(where + ": the node of '" + id + "' is not disposed.");
      }
      if (old.component().isPresent() && old.component().get().getParent() != null) {
        throw new AssertionError(where + ": the view of '" + id + "' is in the surface.");
      }
      if (session.tree().find(id).isPresent()) {
        throw new AssertionError(where + ": the surface shows '" + id + "'.");
      }
    });
    if (step.containsKey("expectSurfaceRemoved")) {
      assertJsonEquals(true, step.get("expectSurfaceRemoved"), "expectSurfaceRemoved of " + where);
      if (session.processor.model().getSurface(session.surface.id()).isPresent()
          || session.view.isMounted()
          || session.view.getComponentCount() != 0) {
        throw new AssertionError(where + ": the surface is not removed.");
      }
    }
    if (step.containsKey("expectDataModel")) {
      assertJsonEquals(
          step.get("expectDataModel"),
          session.surface.dataModel().get("/"),
          "expectDataModel of " + where);
    }
    if (step.containsKey("expectActions")) {
      assertActions(session, asList(step.get("expectActions"), where), where);
    }
    if (step.containsKey("expectErrors")) {
      assertErrors(session, asList(step.get("expectErrors"), where), where);
    }
    if (step.containsKey("expectAccessibilityTree")) {
      String at = "expectAccessibilityTree of " + where;
      session.assertAccessibility(asObject(step.get("expectAccessibilityTree"), at), at);
    }
    if (step.containsKey("expectThemeAbsent")) {
      // Protocol v1.0 has no theme, and the surface model of this SDK has no theme property. The
      // views take each color from the look and feel. Thus there is nothing to read.
      assertJsonEquals(true, step.get("expectThemeAbsent"), "expectThemeAbsent of " + where);
    }
    if (step.containsKey("expectActiveSubscriptions")) {
      assertJsonEquals(
          step.get("expectActiveSubscriptions"),
          session.view.activeSubscriptionCount(),
          "expectActiveSubscriptions of " + where);
    }
    if (step.containsKey("expectOutbound")) {
      List<Object> expected = asList(step.get("expectOutbound"), where);
      if (expected.size() != session.outbound.size()) {
        throw new AssertionError(
            where + ": expected " + expected.size() + " messages, but got " + session.outbound);
      }
      for (int i = 0; i < expected.size(); i++) {
        OutboundSchema.assertValid(session.outbound.get(i), "message " + i + " of " + where);
        assertMatches(expected.get(i), session.outbound.get(i), "message " + i + " of " + where);
      }
    }
  }

  /** Gets the Swing component at a path such as {@code root/children/0}. */
  private static JComponent componentAt(AdapterSession session, String path, String where) {
    String[] parts = path.split("/");
    MountedNode node = session.tree();
    if (!parts[0].equals("root") || parts.length % 2 == 0) {
      throw new AssertionError(where + ": the path '" + path + "' is not correct.");
    }
    for (int i = 1; i < parts.length; i += 2) {
      if (!parts[i].equals("children")) {
        throw new AssertionError(where + ": the path '" + path + "' is not correct.");
      }
      node = node.children().get(Integer.parseInt(parts[i + 1]));
    }
    return node.component().orElse(null);
  }

  /** Compares one position of the mounted tree, and the positions below it. */
  private void assertViewNode(
      AdapterSession session, Map<String, Object> expected, MountedNode actual, String where) {
    KnownKeys.assertKnownKeys(expected, VIEW_NODE_KEYS, where);
    ComponentNode<?> node = actual.node();
    String at = "'" + expected.get("id") + "' in " + where;
    assertJsonEquals(expected.get("id"), node.componentId(), "id of " + at);
    if (expected.containsKey("type")) {
      assertJsonEquals(expected.get("type"), node.type(), "type of " + at);
    }
    if (expected.containsKey("state")) {
      assertJsonEquals(expected.get("state"), node.state().wireValue(), "state of " + at);
    }
    if (expected.containsKey("occupiesLayout")) {
      assertJsonEquals(
          expected.get("occupiesLayout"), actual.occupiesLayout(), "occupiesLayout of " + at);
    }
    if (expected.containsKey("text")) {
      String shown = AdapterSession.visibleText(session.control(actual));
      assertJsonEquals(expected.get("text"), shown, "text of " + at);
    }
    if (expected.containsKey("value")) {
      assertJsonEquals(expected.get("value"), shownValue(session, actual, at), "value of " + at);
    }
    for (String property : List.of("url", "name", "axis", "badgeText")) {
      if (property.equals("axis")
          && expected.containsKey(property)
          && session.control(actual) instanceof JSeparator line) {
        // A divider shows its axis as the orientation of its line.
        assertJsonEquals(
            expected.get(property),
            line.getOrientation() == SwingConstants.VERTICAL ? "vertical" : "horizontal",
            "axis of " + at);
        continue;
      }
      if (expected.containsKey(property)
          && AdapterSession.basicProperty(session.control(actual), property).isPresent()) {
        // A view of the basic catalog tells what it shows with a client property.
        assertJsonEquals(
            expected.get(property),
            AdapterSession.basicProperty(session.control(actual), property).get(),
            property + " of " + at);
        if (property.equals("url")
            && node.type().equals("Image")
            && !session.images.requests.contains(
                URI.create(String.valueOf(expected.get(property))))) {
          throw new AssertionError("The view did not ask the image loader for 'url' of " + at);
        }
        continue;
      }
      if (expected.containsKey(property)) {
        NodeProps<?> props =
            AdapterSession.customProps(actual)
                .orElseThrow(() ->
                    new AssertionError("The harness cannot read '" + property + "' of " + at));
        assertJsonEquals(expected.get(property), props.value(property), property + " of " + at);
        if (!AdapterSession.visibleText(actual.component().orElseThrow())
            .contains(String.valueOf(expected.get(property)))) {
          throw new AssertionError("The view does not show '" + property + "' of " + at);
        }
      }
    }
    assertChecks(session, expected, actual, at);
    if (expected.containsKey("children")) {
      List<Object> children = asList(expected.get("children"), "children of " + at);
      if (children.size() != actual.children().size()) {
        throw new AssertionError(
            "children of "
                + at
                + ": expected "
                + children.size()
                + " but the view shows "
                + actual.children().size());
      }
      for (int i = 0; i < children.size(); i++) {
        assertViewNode(
            session, asObject(children.get(i), "child of " + at), actual.children().get(i), where);
      }
    }
  }

  /** Reads the value that the control of a component shows. */
  private static Object shownValue(AdapterSession session, MountedNode actual, String at) {
    JComponent control = session.control(actual);
    if (control instanceof JFormattedTextField field) {
      // The value of a formatted field is the value of the component, for example ISO 8601 text.
      return field.getValue();
    }
    if (control instanceof JTextComponent field) {
      return AdapterSession.documentText(field);
    }
    if (control instanceof JCheckBox box) {
      return box.isSelected();
    }
    if (control instanceof JSlider slider) {
      return slider.getAccessibleContext().getAccessibleValue().getCurrentAccessibleValue();
    }
    if (actual.node().type().equals("ChoicePicker")) {
      // The value of a picker is the list of the values of the selected options.
      List<Object> values = new ArrayList<>();
      for (AbstractButton option : AdapterSession.optionsOf(control)) {
        if (option.isSelected()) {
          values.add(option.getActionCommand());
        }
      }
      return values;
    }
    throw new AssertionError("The harness cannot read the value of " + at);
  }

  /** Compares the state of the checks, in the node and in what the view shows. */
  private void assertChecks(
      AdapterSession session, Map<String, Object> expected, MountedNode actual, String at) {
    if (!expected.containsKey("isValid")
        && !expected.containsKey("validationErrors")
        && !expected.containsKey("validationResults")
        && !expected.containsKey("hints")) {
      return;
    }
    NodeProps<?> props = actual.node().props().get();
    JComponent control = session.control(actual);
    List<String> visible = AdapterSession.labelsBelow(actual.component().orElseThrow());
    if (expected.containsKey("isValid")) {
      assertJsonEquals(expected.get("isValid"), props.isValid(), "isValid of " + at);
      // A button that is not valid is disabled. Each other control that is not valid has its
      // error messages as its tool tip, and the user can still operate it.
      boolean shownValid =
          control instanceof JButton ? control.isEnabled() : control.getToolTipText() == null;
      assertJsonEquals(expected.get("isValid"), shownValid, "the shown validity of " + at);
    }
    if (expected.containsKey("validationErrors")) {
      List<Object> errors = asList(expected.get("validationErrors"), "validationErrors of " + at);
      assertJsonEquals(errors, props.validationErrors(), "validationErrors of " + at);
      assertVisible(errors, visible, at);
    }
    if (expected.containsKey("validationResults")) {
      List<Object> results = new ArrayList<>();
      List<Object> messages = new ArrayList<>();
      for (ValidationResult result : props.validationResults()) {
        results.add(result.toJson());
        result.message().ifPresent(messages::add);
      }
      List<Object> wanted = asList(expected.get("validationResults"), "validationResults of " + at);
      for (Object item : wanted) {
        KnownKeys.assertKnownKeys(
            asObject(item, at), Set.of("valid", "code", "message", "severity"), at);
      }
      assertJsonEquals(wanted, results, "validationResults of " + at);
      assertVisible(messages, visible, at);
    }
    if (expected.containsKey("hints")) {
      List<Object> hints = new ArrayList<>();
      for (ValidationResult result : props.validationResults()) {
        if (result.severity() != ValidationSeverity.ERROR) {
          result.message().ifPresent(hints::add);
        }
      }
      assertJsonEquals(expected.get("hints"), hints, "hints of " + at);
      assertVisible(hints, visible, at);
      if (!control.isEnabled()) {
        throw new AssertionError("A hint must not stop the user: " + at);
      }
    }
  }

  private static void assertVisible(List<Object> messages, List<String> visible, String at) {
    for (Object message : messages) {
      if (!visible.contains(message)) {
        throw new AssertionError(
            "The view of " + at + " does not show the message '" + message + "': " + visible);
      }
    }
  }

  private static void assertActions(AdapterSession session, List<Object> expected, String where) {
    if (expected.size() != session.actions.size()) {
      throw new AssertionError(
          where + ": expected " + expected.size() + " actions, but got " + session.actions);
    }
    for (int i = 0; i < expected.size(); i++) {
      Map<String, Object> wanted = asObject(expected.get(i), "action of " + where);
      KnownKeys.assertKnownKeys(wanted, ACTION_KEYS, "action of " + where);
      RendererAction action = session.actions.get(i);
      Map<String, Object> actual = new LinkedHashMap<>();
      actual.put("name", action.name());
      actual.put("surfaceId", action.surfaceId());
      actual.put("sourceComponentId", action.sourceComponentId());
      actual.put("context", action.context());
      wanted.forEach(
          (key, value) -> assertJsonEquals(value, actual.get(key), key + " of action " + where));
    }
  }

  private static void assertErrors(AdapterSession session, List<Object> expected, String where) {
    if (expected.size() != session.errors.size()) {
      throw new AssertionError(
          where + ": expected " + expected.size() + " errors, but got " + session.errors);
    }
    for (int i = 0; i < expected.size(); i++) {
      Map<String, Object> wanted = asObject(expected.get(i), "error of " + where);
      KnownKeys.assertKnownKeys(wanted, ERROR_KEYS, "error of " + where);
      A2uiError error = session.errors.get(i);
      if (wanted.containsKey("code")) {
        assertJsonEquals(wanted.get("code"), error.errorCode().orElse(null), "code of " + where);
      }
      if (wanted.containsKey("surfaceId")) {
        assertJsonEquals(
            wanted.get("surfaceId"), error.surfaceId().orElse(null), "surfaceId of " + where);
      }
      if (wanted.containsKey("message")
          && !Pattern.compile(asString(wanted.get("message"), where))
              .matcher(String.valueOf(error.getMessage()))
              .find()) {
        throw new AssertionError(
            where + ": the message '" + error.getMessage() + "' does not match " + wanted);
      }
    }
  }

  /**
   * Compares a message with its expectation. The expectation {@code {$match: any}} accepts each
   * value that is not null.
   */
  private static void assertMatches(Object expected, Object actual, String where) {
    if (expected instanceof Map<?, ?> map) {
      if (map.size() == 1 && "any".equals(map.get("$match"))) {
        if (actual == null) {
          throw new AssertionError(where + ": expected a value, but there is none.");
        }
        return;
      }
      Map<String, Object> actualMap = asObject(actual, where);
      assertJsonEquals(
          new TreeSet<>(map.keySet()), new TreeSet<>(actualMap.keySet()), "keys of " + where);
      map.forEach((key, value) -> assertMatches(value, actualMap.get(key), where + "/" + key));
      return;
    }
    if (expected instanceof List<?> list) {
      List<Object> actualList = asList(actual, where);
      assertJsonEquals(list.size(), actualList.size(), "size of " + where);
      for (int i = 0; i < list.size(); i++) {
        assertMatches(list.get(i), actualList.get(i), where + "/" + i);
      }
      return;
    }
    assertJsonEquals(expected, actual, where);
  }
}
