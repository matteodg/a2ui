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

package com.google.a2ui.swing.nodes;

import static com.google.a2ui.swing.testing.TestSurface.component;
import static com.google.a2ui.swing.testing.TestSurface.event;
import static com.google.a2ui.swing.testing.TestSurface.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.core.resolution.NodeResolver;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.internal.ViewMarks;
import com.google.a2ui.swing.surface.A2uiAccessibility;
import com.google.a2ui.swing.surface.A2uiSurface;
import com.google.a2ui.swing.surface.MountedNode;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.Headless;
import com.google.a2ui.swing.testing.TestSurface;
import java.awt.Component;
import java.awt.FocusTraversalPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.LayoutFocusTraversalPolicy;
import org.junit.jupiter.api.Test;

class AccessibilityTest {
  private final TestSurface surface = new TestSurface();
  private A2uiSurface view;

  private void mount(Map<String, Object> data, Object... components) {
    surface.create(data, components);
    view = Headless.mount(new A2uiSurface(surface.model()));
  }

  private JComponent componentOf(String componentId) {
    MountedNode node = view.viewTree().orElseThrow().find(componentId).orElseThrow();
    return node.view().orElseThrow().accessibleComponent();
  }

  private AccessibleContext contextOf(String componentId) {
    return componentOf(componentId).getAccessibleContext();
  }

  private static Map<String, Object> saveButton(Object... namesAndValues) {
    Map<String, Object> button =
        component("save", "Button", "child", "save_text", "action", event("save"));
    button.putAll(TestSurface.object(namesAndValues));
    return button;
  }

  private static Object[] page(Map<String, Object> button) {
    return new Object[] {
      component("root", "Box", "children", List.of("save", "status")),
      button,
      component("save_text", "Label", "text", path("/caption")),
      component("status", "Label", "text", path("/status"))
    };
  }

  private static Map<String, Object> data() {
    return Map.of("caption", "Save", "status", "Ready", "hint", "Stores the form");
  }

  @Test
  void theLabelAndTheDescriptionAreTheAccessibleNameAndDescription() {
    Edt.run(() -> {
      mount(
          data(),
          page(
              saveButton(
                  "@accessibility",
                  Map.of("label", "Save the form", "description", path("/hint")))));

      assertEquals("Save the form", contextOf("save").getAccessibleName());
      assertEquals("Stores the form", contextOf("save").getAccessibleDescription());
      assertEquals(AccessibleRole.PUSH_BUTTON, contextOf("save").getAccessibleRole());
      assertEquals(
          new A2uiAccessibility(
              Optional.of("Save the form"),
              Optional.of("Stores the form"),
              false,
              Optional.empty()),
          A2uiAccessibility.of(componentOf("save")));
    });
  }

  @Test
  void aComponentWithNoAttributesKeepsTheNameThatSwingGivesIt() {
    Edt.run(() -> {
      mount(data(), page(saveButton()));

      assertEquals("Ready", contextOf("status").getAccessibleName());
      assertEquals(A2uiAccessibility.NONE, A2uiAccessibility.of(componentOf("status")));
      assertNull(componentOf("status").getClientProperty(A2uiAccessibility.CLIENT_PROPERTY));
    });
  }

  @Test
  void aButtonInfersItsNameFromTheTextOfItsChildAndFollowsIt() {
    Edt.run(() -> {
      mount(data(), page(saveButton()));

      assertEquals("Save", contextOf("save").getAccessibleName());

      surface.setData("/caption", "Save all");

      assertEquals("Save all", contextOf("save").getAccessibleName());
    });
  }

  @Test
  void aButtonInfersItsNameWhenItsChildArrivesLater() {
    Edt.run(() -> {
      mount(Map.of(), component("root", "Button", "child", "late", "action", event("go")));

      surface.update(component("late", "Label", "text", "Go"));

      assertEquals("Go", contextOf("root").getAccessibleName());
    });
  }

  @Test
  void anExplicitLabelIsBeforeTheInferredNameAndTheInferredNameComesBack() {
    Edt.run(() -> {
      mount(data(), page(saveButton("@accessibility", Map.of("label", "Store"))));

      assertEquals("Store", contextOf("save").getAccessibleName());

      surface.update(saveButton());

      assertEquals("Save", contextOf("save").getAccessibleName());
      assertEquals(A2uiAccessibility.NONE, A2uiAccessibility.of(componentOf("save")));
    });
  }

  @Test
  void aLabelThatReadsDataFollowsTheData() {
    Edt.run(() -> {
      mount(data(), page(saveButton("@accessibility", Map.of("label", path("/hint")))));

      surface.setData("/hint", "Stores all pages");

      assertEquals("Stores all pages", contextOf("save").getAccessibleName());
    });
  }

  @Test
  void aTextFieldInfersItsNameFromItsLabel() {
    Edt.run(() -> {
      mount(Map.of(), component("root", "Field", "label", "Name", "value", "Ada"));

      assertEquals("Name", contextOf("root").getAccessibleName());
    });
  }

  @Test
  void aHiddenComponentHasAnEmptyNameAndTheNameComesBack() {
    Edt.run(() -> {
      mount(
          Map.of("caption", "Save", "status", "Ready", "secret", true),
          component("root", "Box", "children", List.of("status")),
          component(
              "status",
              "Label",
              "text",
              path("/status"),
              "@accessibility",
              Map.of("hidden", path("/secret"), "label", "Status")));

      assertTrue(A2uiAccessibility.of(componentOf("status")).hidden());
      assertEquals("", contextOf("status").getAccessibleName());
      assertEquals("", contextOf("status").getAccessibleDescription());

      surface.setData("/secret", false);

      assertEquals("Status", contextOf("status").getAccessibleName());
      assertNull(contextOf("status").getAccessibleDescription());
      assertEquals(
          new A2uiAccessibility(Optional.of("Status"), Optional.empty(), false, Optional.empty()),
          A2uiAccessibility.of(componentOf("status")));
    });
  }

  /** Makes a box with a button and a live label in an inner box. The outer box can be hidden. */
  private void mountNested(boolean hidden) {
    mount(
        Map.of("caption", "Save", "status", "Ready", "secret", hidden),
        component(
            "root",
            "Box",
            "children",
            List.of("inner"),
            "@accessibility",
            Map.of("hidden", path("/secret"))),
        component("inner", "Box", "children", List.of("save", "status")),
        saveButton("@accessibility", Map.of("description", "Stores the form")),
        component("save_text", "Label", "text", path("/caption")),
        component(
            "status",
            "Label",
            "text",
            path("/status"),
            "@accessibility",
            Map.of("label", "Status", "live", "polite")));
  }

  @Test
  void aHiddenComponentHidesAllTheComponentsBelowIt() {
    Edt.run(() -> {
      mountNested(true);

      for (String id : List.of("root", "inner", "save", "save_text", "status")) {
        assertTrue(A2uiAccessibility.isHidden(componentOf(id)), id);
        assertEquals("", contextOf(id).getAccessibleName(), id);
        assertEquals("", contextOf(id).getAccessibleDescription(), id);
      }
      // The record of a component has only its own attributes.
      assertTrue(A2uiAccessibility.of(componentOf("root")).hidden());
      assertFalse(A2uiAccessibility.of(componentOf("status")).hidden());
      assertEquals(Optional.of("Status"), A2uiAccessibility.of(componentOf("status")).label());
    });
  }

  @Test
  void theComponentsBelowGetTheirNamesBackWhenTheComponentIsNotHiddenAgain() {
    Edt.run(() -> {
      mountNested(true);

      surface.setData("/secret", false);

      for (String id : List.of("root", "inner", "save", "save_text", "status")) {
        assertFalse(A2uiAccessibility.isHidden(componentOf(id)), id);
      }
      assertEquals("Save", contextOf("save").getAccessibleName());
      assertEquals("Stores the form", contextOf("save").getAccessibleDescription());
      assertEquals("Status", contextOf("status").getAccessibleName());
      assertEquals("Save", contextOf("save_text").getAccessibleName());

      surface.setData("/secret", true);

      assertTrue(A2uiAccessibility.isHidden(componentOf("status")));
      assertEquals("", contextOf("save").getAccessibleName());
      assertEquals("", contextOf("status").getAccessibleName());
    });
  }

  private JComponent rootOf(String componentId) {
    MountedNode node = view.viewTree().orElseThrow().find(componentId).orElseThrow();
    return node.view().orElseThrow().component();
  }

  /** Makes sure that the Tab key goes past, or goes into, the root component of a view. */
  private void assertTabGoesPast(String componentId, boolean past) {
    JComponent root = rootOf(componentId);
    assertEquals(past, root.isFocusTraversalPolicyProvider(), componentId);
    if (past) {
      FocusTraversalPolicy policy = root.getFocusTraversalPolicy();
      JComponent control = componentOf(componentId);
      assertNull(policy.getFirstComponent(root), componentId);
      assertNull(policy.getLastComponent(root), componentId);
      assertNull(policy.getDefaultComponent(root), componentId);
      assertNull(policy.getComponentAfter(root, control), componentId);
      assertNull(policy.getComponentBefore(root, control), componentId);
    } else {
      assertFalse(root.isFocusTraversalPolicySet(), componentId);
    }
  }

  @Test
  void theKeyboardFocusDoesNotGoIntoAHiddenComponentOrBelowIt() {
    Edt.run(() -> {
      mountNested(true);

      assertFalse(componentOf("save").isFocusable());
      for (String id : List.of("root", "inner", "save", "save_text", "status")) {
        assertTabGoesPast(id, true);
        assertFalse(rootOf(id).isFocusable(), id);
      }
    });
  }

  @Test
  void theKeyboardFocusGoesIntoAComponentThatIsNotHiddenAgain() {
    Edt.run(() -> {
      mountNested(true);

      surface.setData("/secret", false);

      assertTrue(componentOf("save").isFocusable());
      for (String id : List.of("root", "inner", "save", "save_text", "status")) {
        assertTabGoesPast(id, false);
      }

      surface.setData("/secret", true);

      assertFalse(componentOf("save").isFocusable());
      assertTabGoesPast("inner", true);
    });
  }

  @Test
  void aComponentThatIsNotHiddenKeepsItsFocusValues() {
    Edt.run(() -> {
      mountNested(false);

      assertTrue(componentOf("save").isFocusable());
      for (String id : List.of("root", "inner", "save", "save_text", "status")) {
        assertTabGoesPast(id, false);
      }
    });
  }

  @Test
  void aHiddenViewGetsItsOwnFocusPolicyBack() {
    Edt.run(() -> {
      JPanel panel = new JPanel();
      JTextField field = new JTextField();
      panel.add(field);
      FocusTraversalPolicy own = new LayoutFocusTraversalPolicy();
      panel.setFocusTraversalPolicy(own);
      panel.setFocusTraversalPolicyProvider(true);
      field.setFocusable(false);
      HiddenFocus focus = new HiddenFocus();

      focus.apply(panel, field, true);

      assertTrue(focus.active());
      assertTrue(panel.isFocusTraversalPolicyProvider());
      assertNull(panel.getFocusTraversalPolicy().getFirstComponent(panel));

      focus.apply(panel, field, false);

      assertFalse(focus.active());
      assertTrue(panel.isFocusTraversalPolicyProvider());
      assertSame(own, panel.getFocusTraversalPolicy());
      // The field was not focusable before. Thus it is not focusable after.
      assertFalse(field.isFocusable());
    });
  }

  /** Gives access to the test of Swing that tells if a component is a stop of the Tab key. */
  private static final class TabStops extends LayoutFocusTraversalPolicy {
    private static final long serialVersionUID = 1L;

    boolean isStop(Component component) {
      return accept(component);
    }
  }

  @Test
  void aPanelIsNotAStopOfTheTabKeyAfterItsViewShowsAgain() {
    Edt.run(() -> {
      JPanel panel = new JPanel();
      JButton button = new JButton("Save");
      JPanel window = new JPanel();
      panel.add(button);
      window.add(panel);
      window.setSize(300, 100);
      window.addNotify();
      TabStops stops = new TabStops();
      assertFalse(stops.isStop(panel));
      assertTrue(stops.isStop(button));
      HiddenFocus focus = new HiddenFocus();

      focus.apply(panel, panel, true);
      focus.apply(panel, panel, false);

      // The Tab key stops at the button, not at the panel around the button.
      assertFalse(stops.isStop(panel));
      assertTrue(stops.isStop(button));
      window.removeNotify();
    });
  }

  @Test
  void aComponentThatItsCodeMadeFocusableIsAStopAgainAfterItsViewShowsAgain() {
    Edt.run(() -> {
      JPanel canvas = new JPanel();
      canvas.setFocusable(true);
      JPanel window = new JPanel();
      window.add(canvas);
      window.setSize(300, 100);
      window.addNotify();
      TabStops stops = new TabStops();
      assertTrue(stops.isStop(canvas));
      HiddenFocus focus = new HiddenFocus();

      focus.apply(canvas, canvas, true);
      assertFalse(stops.isStop(canvas));
      focus.apply(canvas, canvas, false);

      assertTrue(stops.isStop(canvas));
      window.removeNotify();
    });
  }

  @Test
  void aControlIsFocusableAgainAfterItsViewShowsAgain() {
    Edt.run(() -> {
      JPanel panel = new JPanel();
      JButton button = new JButton("Save");
      panel.add(button);
      HiddenFocus focus = new HiddenFocus();

      focus.apply(panel, button, true);
      assertFalse(button.isFocusable());
      focus.apply(panel, button, false);

      assertTrue(button.isFocusable());
    });
  }

  @Test
  void aComponentThatArrivesBelowAHiddenComponentIsHidden() {
    Edt.run(() -> {
      mountNested(true);

      surface.update(
          component("inner", "Box", "children", List.of("save", "status", "late")),
          component("late", "Label", "text", "Late"));
      surface.setData("/caption", "Store");

      assertTrue(A2uiAccessibility.isHidden(componentOf("late")));
      assertEquals("", contextOf("late").getAccessibleName());
      // An update of a component in the hidden part does not give a name back.
      assertEquals("", contextOf("save").getAccessibleName());
      assertEquals("", contextOf("save_text").getAccessibleName());
    });
  }

  @Test
  void aLiveComponentInAHiddenPartSendsNoChangeEvent() {
    Edt.run(() -> {
      mountNested(true);
      List<String> events = new ArrayList<>();
      contextOf("status")
          .addPropertyChangeListener(event -> {
            if (AccessibleContext.ACCESSIBLE_VISIBLE_DATA_PROPERTY.equals(
                event.getPropertyName())) {
              events.add(event.getPropertyName());
            }
          });

      surface.setData("/status", "Saved");
      assertEquals(List.of(), events);

      surface.setData("/secret", false);
      surface.setData("/status", "Ready");
      assertEquals(List.of(AccessibleContext.ACCESSIBLE_VISIBLE_DATA_PROPERTY), events);
    });
  }

  @Test
  void aComponentWithNoHiddenComponentAboveItIsNotMarked() {
    Edt.run(() -> {
      mountNested(false);

      assertFalse(A2uiAccessibility.isHidden(componentOf("status")));
      assertFalse(ViewMarks.isHidden(componentOf("status")));
    });
  }

  @Test
  void aLiveComponentKeepsItsValueAndTellsItsListenersAboutAnUpdate() {
    Edt.run(() -> {
      mount(
          data(),
          component("root", "Box", "children", List.of("status")),
          component(
              "status",
              "Label",
              "text",
              path("/status"),
              "@accessibility",
              Map.of("live", "polite")));
      List<String> events = new ArrayList<>();
      contextOf("status")
          .addPropertyChangeListener(event -> {
            if (Boolean.TRUE.equals(event.getNewValue())) {
              events.add(event.getPropertyName());
            }
          });

      assertEquals(Optional.of("polite"), A2uiAccessibility.of(componentOf("status")).live());
      assertEquals(List.of(), events);

      surface.setData("/status", "Saved");

      assertEquals(List.of(AccessibleContext.ACCESSIBLE_VISIBLE_DATA_PROPERTY), events);
    });
  }

  @Test
  void theBareAccessibilityKeyOfProtocolVersion1GivesTheSameAttributes() {
    Edt.run(() -> {
      mount(data(), page(saveButton("accessibility", Map.of("label", "Close"))));

      assertEquals("Close", contextOf("save").getAccessibleName());
    });
  }

  @Test
  void theAttributesGoToTheAccessibleComponentOfTheView() {
    Edt.run(() -> {
      surface.create(
          Map.of(),
          component("root", "Label", "text", "x", "@accessibility", Map.of("label", "Name")));
      ComponentNode<?> node = new NodeResolver<>(surface.model()).rootNode().get().orElseThrow();
      JPanel panel = new JPanel();
      JTextField control = new JTextField();
      panel.add(control);
      SwingComponentView composite =
          new SwingComponentView() {
            @Override
            public JComponent component() {
              return panel;
            }

            @Override
            public JComponent accessibleComponent() {
              return control;
            }

            @Override
            public void update(ComponentNode<?> updated, NodeProps<?> props) {}
          };

      new Accessibility().apply(composite, node.props().get(), true, false);

      assertEquals("Name", control.getAccessibleContext().getAccessibleName());
      assertNull(panel.getAccessibleContext().getAccessibleName());
    });
  }
}
