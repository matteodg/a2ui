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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeResolver;
import com.google.a2ui.core.resolution.NodeState;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.surface.A2uiErrorInfo;
import com.google.a2ui.swing.surface.A2uiFallbackError;
import com.google.a2ui.swing.surface.A2uiFallbacks;
import com.google.a2ui.swing.surface.A2uiPendingInfo;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.LogCapture;
import com.google.a2ui.swing.testing.TestSurface;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class NodeHostTest {
  private final TestSurface surface = new TestSurface();
  private final List<String> events = new ArrayList<>();
  private final List<Object> fallbackInfos = new ArrayList<>();
  private A2uiFallbacks fallbacks = A2uiFallbacks.NONE;
  private SurfaceScope scope;
  private NodeResolver<SwingComponentImplementation> resolver;

  private final NodeHost.Listener listener =
      new NodeHost.Listener() {
        @Override
        public void componentChanged(NodeHost host) {
          events.add("component");
        }

        @Override
        public void updated(NodeHost host) {
          events.add("updated");
        }
      };

  /** Makes the surface and a host for its root. */
  private NodeHost rootHost(Map<String, Object> data, Object... components) {
    mount(data, components);
    NodeHost host = new NodeHost(scope, Optional.empty(), listener);
    host.bind(root());
    return host;
  }

  /** Makes the surface, its scope and its resolver. */
  private void mount(Map<String, Object> data, Object... components) {
    surface.create(data, components);
    scope = new SurfaceScope(surface.model(), fallbacks, A2uiServices.DEFAULT);
    resolver = new NodeResolver<>(surface.model());
  }

  private ComponentNode<SwingComponentImplementation> root() {
    return resolver.rootNode().get().orElseThrow();
  }

  /** Makes the surface and a host for the child of its root button. */
  private NodeHost childHost(Object... components) {
    mount(Map.of(), components);
    NodeHost host = new NodeHost(scope, Optional.of("Button"), listener);
    host.bind(child());
    return host;
  }

  private ComponentNode<SwingComponentImplementation> child() {
    return root().props().get().child("child").orElseThrow();
  }

  private static JLabel fallbackLabel(String text) {
    JLabel label = new JLabel(text);
    label.setName("fallback");
    return label;
  }

  @Test
  void aResolvedNodeGetsTheViewOfItsImplementation() {
    Edt.run(() -> {
      NodeHost host = rootHost(Map.of(), component("root", "Label", "text", "Hello"));

      JLabel label = (JLabel) host.component().orElseThrow();
      assertEquals("Hello", label.getText());
      assertSame(surface.catalog.created.get(0), host.view().orElseThrow());
      assertSame(root(), host.node());
      assertEquals(List.of("updated", "component"), events);
      assertEquals(1, scope.activeSubscriptions());
    });
  }

  @Test
  void aChangeOfThePropertiesUpdatesTheSameView() {
    Edt.run(() -> {
      NodeHost host =
          rootHost(Map.of("name", "Ada"), component("root", "Label", "text", path("/name")));
      JComponent before = host.component().orElseThrow();
      events.clear();

      surface.setData("/name", "Grace");

      assertSame(before, host.component().orElseThrow());
      assertEquals("Grace", ((JLabel) before).getText());
      assertEquals(List.of("updated"), events);
      assertEquals(1, surface.catalog.created.size());
    });
  }

  @Test
  void aNewNodeWithTheSameImplementationKeepsTheView() {
    Edt.run(() -> {
      NodeHost host = rootHost(Map.of(), component("root", "Label", "text", "One"));
      JComponent before = host.component().orElseThrow();
      events.clear();

      surface.update(component("root", "Label", "text", "Two"));
      host.bind(root());

      assertSame(before, host.component().orElseThrow());
      assertEquals("Two", ((JLabel) before).getText());
      assertFalse(events.contains("component"));
      assertEquals(List.of(), surface.catalog.disposed);
      assertEquals(1, scope.activeSubscriptions());
    });
  }

  @Test
  void aNodeOfADifferentTypeGetsANewViewAndTheOldViewIsDisposed() {
    Edt.run(() -> {
      NodeHost host = rootHost(Map.of(), component("root", "Label", "text", "One"));
      JComponent before = host.component().orElseThrow();
      events.clear();

      surface.update(component("root", "Field", "value", "Two"));
      host.bind(root());

      JComponent after = host.component().orElseThrow();
      assertNotSame(before, after);
      assertEquals("Two", ((JTextField) after).getText());
      assertEquals(List.of("root"), surface.catalog.disposed);
      assertEquals(List.of("updated", "component"), events);
      assertEquals(1, scope.activeSubscriptions());
    });
  }

  @Test
  void aPendingNodeShowsNothingWithoutAFallback() {
    Edt.run(() -> {
      NodeHost host = childHost(component("root", "Button", "child", "late"));

      assertEquals(NodeState.PENDING, host.node().state());
      assertEquals(Optional.empty(), host.component());
      assertEquals(Optional.empty(), host.view());
      assertEquals(List.of(), events);
    });
  }

  @Test
  void aPendingNodeShowsThePendingFallbackAndThenTheComponentThatArrives() {
    Edt.run(() -> {
      fallbacks =
          A2uiFallbacks.NONE.withPending(info -> {
            fallbackInfos.add(info);
            return fallbackLabel("...");
          });
      NodeHost host = childHost(component("root", "Button", "child", "late"));

      assertEquals("fallback", host.component().orElseThrow().getName());
      assertEquals(List.of(new A2uiPendingInfo("late", Optional.of("Button"))), fallbackInfos);
      assertEquals(List.of("component"), events);
      events.clear();

      surface.update(component("late", "Label", "text", "Arrived"));
      host.bind(child());

      assertEquals("Arrived", ((JLabel) host.component().orElseThrow()).getText());
      assertNull(host.component().orElseThrow().getName());
      assertEquals(List.of("updated", "component"), events);
    });
  }

  @Test
  void aNodeOfAnUnknownTypeShowsTheErrorFallback() {
    Edt.run(() -> {
      fallbacks =
          A2uiFallbacks.NONE.withError(info -> {
            fallbackInfos.add(info);
            return fallbackLabel("!");
          });
      NodeHost host =
          childHost(component("root", "Button", "child", "odd"), component("odd", "Chart"));

      assertEquals(NodeState.UNKNOWN_TYPE, host.node().state());
      assertEquals("fallback", host.component().orElseThrow().getName());
      assertEquals(
          List.of(
              new A2uiErrorInfo(
                  "odd",
                  Optional.of("Button"),
                  new A2uiFallbackError(A2uiFallbackError.UNKNOWN_TYPE, Optional.of("Chart")))),
          fallbackInfos);
    });
  }

  @Test
  void aCyclicNodeShowsTheErrorFallback() {
    Edt.run(() -> {
      fallbacks =
          A2uiFallbacks.NONE.withError(info -> {
            fallbackInfos.add(info);
            return fallbackLabel("!");
          });
      mount(Map.of(), component("root", "Button", "child", "middle"));
      surface.put("middle", "Button", "child", "root");
      NodeHost host = new NodeHost(scope, Optional.of("Button"), listener);

      host.bind(child().props().get().child("child").orElseThrow());

      assertEquals(NodeState.CYCLIC, host.node().state());
      assertEquals(
          List.of(
              new A2uiErrorInfo(
                  "root",
                  Optional.of("Button"),
                  new A2uiFallbackError(A2uiFallbackError.CYCLIC, Optional.empty()))),
          fallbackInfos);
    });
  }

  @Test
  void aFallbackThatThrowsShowsNothingAndTheFailureGoesToTheLog() {
    Edt.run(() -> {
      fallbacks =
          A2uiFallbacks.NONE.withPending(info -> {
            throw new IllegalStateException("The fallback failed.");
          });
      try (LogCapture log = new LogCapture()) {
        NodeHost host = childHost(component("root", "Button", "child", "late"));

        assertEquals(Optional.empty(), host.component());
        assertEquals(List.of(), events);
        assertEquals(
            List.of("The pending fallback failed for the component 'late'."), log.messages);
      }
    });
  }

  @Test
  void aNewPlaceholderWithTheSameDescriptionKeepsTheFallbackComponent() {
    Edt.run(() -> {
      fallbacks = A2uiFallbacks.NONE.withPending(info -> fallbackLabel("..."));
      NodeHost host = childHost(component("root", "Button", "child", "late"));
      JComponent before = host.component().orElseThrow();
      events.clear();

      surface.update(component("root", "Button", "child", "late", "label", "New"));
      host.bind(child());

      assertSame(before, host.component().orElseThrow());
      assertEquals(List.of(), events);
    });
  }

  @Test
  void aTextFieldWritesTheInputOfTheUserToTheDataAndKeepsItsComponent() {
    Edt.run(() -> {
      NodeHost host =
          rootHost(Map.of("name", "Ada"), component("root", "Field", "value", path("/name")));
      JTextField field = (JTextField) host.component().orElseThrow();
      assertEquals("Ada", field.getText());

      field.setText("Grace");

      assertEquals("Grace", surface.model().dataModel().get("/name"));
      assertSame(field, host.component().orElseThrow());
      assertEquals("Grace", field.getText());
    });
  }

  @Test
  void aButtonDoesItsActionInTheListenerOfTheGesture() {
    Edt.run(() -> {
      NodeHost host =
          rootHost(
              Map.of("sku", "sku-42"),
              component(
                  "root", "Button", "label", "Buy", "action", event("buy", "sku", path("/sku"))));
      JButton button = (JButton) host.component().orElseThrow();

      button.doClick(0);

      assertEquals(1, surface.actions.size());
      assertEquals("buy", surface.actions.get(0).name());
      assertEquals("root", surface.actions.get(0).sourceComponentId());
      assertEquals(Map.of("sku", "sku-42"), surface.actions.get(0).context());
    });
  }

  @Test
  void aFailedCheckReachesTheViewAsTheValidationState() {
    Edt.run(() -> {
      NodeHost host =
          rootHost(
              Map.of("name", ""),
              component(
                  "root",
                  "Field",
                  "value",
                  path("/name"),
                  "@checks",
                  List.of(
                      Map.of(
                          "condition",
                          TestSurface.call("required", "value", path("/name")),
                          "message",
                          "Enter a name."))));
      JTextField field = (JTextField) host.component().orElseThrow();
      assertEquals("Enter a name.", field.getToolTipText());

      field.setText("Ada");

      assertNull(field.getToolTipText());
      assertTrue(host.node().props().get().isValid());
    });
  }

  @Test
  void theDisposalDisposesTheViewAndCancelsTheSubscription() {
    Edt.run(() -> {
      NodeHost host =
          rootHost(Map.of("name", "Ada"), component("root", "Label", "text", path("/name")));
      JLabel label = (JLabel) host.component().orElseThrow();
      events.clear();

      host.dispose();
      host.dispose();
      surface.setData("/name", "Grace");

      assertEquals(List.of("root"), surface.catalog.disposed);
      assertEquals(Optional.empty(), host.component());
      assertEquals(0, scope.activeSubscriptions());
      assertEquals("Ada", label.getText());
      assertEquals(List.of(), events);
      assertThrows(IllegalStateException.class, () -> host.bind(root()));
    });
  }
}
