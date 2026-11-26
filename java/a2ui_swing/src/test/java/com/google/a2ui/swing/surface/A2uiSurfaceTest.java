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

package com.google.a2ui.swing.surface;

import static com.google.a2ui.swing.testing.TestSurface.component;
import static com.google.a2ui.swing.testing.TestSurface.object;
import static com.google.a2ui.swing.testing.TestSurface.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.resolution.NodeState;
import com.google.a2ui.core.validation.ValidationConfig;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.Headless;
import com.google.a2ui.swing.testing.TestCatalog;
import com.google.a2ui.swing.testing.TestSurface;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class A2uiSurfaceTest {
  private final TestSurface surface = new TestSurface();

  /** Makes the surface component and connects it, as a window does. */
  private A2uiSurface mount(A2uiFallbacks fallbacks) {
    A2uiSurface view = new A2uiSurface(surface.model(), fallbacks);
    return Headless.mount(view);
  }

  private static Object[] form() {
    return new Object[] {
      component("root", "Box", "children", List.of("title", "name", "echo")),
      component("title", "Label", "text", "Profile"),
      component("name", "Field", "value", path("/name")),
      component("echo", "Label", "text", path("/name"))
    };
  }

  @Test
  void theComponentWorksOnlyOnTheEventDispatchThread() {
    surface.create(Map.of(), component("root", "Label", "text", "Hello"));

    assertThrows(IllegalStateException.class, () -> new A2uiSurface(surface.model()));
    A2uiSurface[] view = {null};
    Edt.run(() -> view[0] = new A2uiSurface(surface.model()));
    assertThrows(IllegalStateException.class, () -> view[0].addNotify());
    assertThrows(IllegalStateException.class, () -> view[0].removeNotify());
    assertThrows(IllegalStateException.class, () -> view[0].viewTree());
  }

  @Test
  void aComponentThatIsNotInAWindowMakesNoViews() {
    Edt.run(() -> {
      surface.create(Map.of(), component("root", "Label", "text", "Hello"));

      A2uiSurface view = new A2uiSurface(surface.model());

      assertFalse(view.isMounted());
      assertEquals(0, view.getComponentCount());
      assertEquals(List.of(), surface.catalog.created);
      assertEquals(Optional.empty(), view.viewTree());
      assertEquals(0, view.activeSubscriptionCount());
      assertSame(surface.model(), view.surface());
      assertSame(A2uiFallbacks.NONE, view.fallbacks());
      assertSame(A2uiServices.DEFAULT, view.services());
    });
  }

  @Test
  void theRootViewFillsTheComponentAfterTheConnection() {
    Edt.run(() -> {
      surface.create(Map.of(), component("root", "Label", "text", "Hello"));

      A2uiSurface view = mount(A2uiFallbacks.NONE);
      view.setSize(200, 50);
      view.validate();

      assertTrue(view.isMounted());
      JLabel label = (JLabel) view.getComponent(0);
      assertEquals("Hello", label.getText());
      assertEquals(view.getSize(), label.getSize());
      assertEquals(1, view.activeSubscriptionCount());
    });
  }

  @Test
  void aMessageUpdatesTheScreenBeforeTheProcessorReturns() {
    Edt.run(() -> {
      surface.create(Map.of("name", "Ada"), form());
      A2uiSurface view = mount(A2uiFallbacks.NONE);
      JPanel box = (JPanel) view.getComponent(0);

      surface.setData("/name", "Grace");

      assertEquals("Grace", ((JTextField) box.getComponent(1)).getText());
      assertEquals("Grace", ((JLabel) box.getComponent(2)).getText());
    });
  }

  @Test
  void theInputOfTheUserUpdatesTheDataAndTheOtherViews() {
    Edt.run(() -> {
      surface.create(Map.of("name", "Ada"), form());
      A2uiSurface view = mount(A2uiFallbacks.NONE);
      JPanel box = (JPanel) view.getComponent(0);
      JTextField field = (JTextField) box.getComponent(1);

      field.setText("Grace");

      assertEquals("Grace", surface.model().dataModel().get("/name"));
      assertEquals("Grace", ((JLabel) box.getComponent(2)).getText());
      assertSame(field, box.getComponent(1));
    });
  }

  @Test
  void theViewTreeIsEqualToTheNodeTree() {
    Edt.run(() -> {
      surface.create(
          Map.of("name", "Ada"),
          component("root", "Box", "children", List.of("title", "late", "name")),
          component("title", "Label", "text", "Profile"),
          component("name", "Field", "value", path("/name")));
      A2uiSurface view = mount(A2uiFallbacks.NONE);

      MountedNode root = view.viewTree().orElseThrow();

      assertEquals("root", root.node().componentId());
      assertEquals("Box", root.node().type());
      assertSame(view.getComponent(0), root.component().orElseThrow());
      assertSame(surface.catalog.created.get(0), root.view().orElseThrow());
      List<String> ids = new ArrayList<>();
      root.children().forEach(child -> ids.add(child.node().componentId()));
      assertEquals(List.of("title", "late", "name"), ids);
      MountedNode late = root.find("late").orElseThrow();
      assertEquals(NodeState.PENDING, late.node().state());
      assertFalse(late.occupiesLayout());
      assertEquals(Optional.empty(), late.view());
      assertTrue(root.find("name").orElseThrow().occupiesLayout());
      assertEquals(Optional.empty(), root.find("other"));
      assertThrows(UnsupportedOperationException.class, () -> root.children().clear());
    });
  }

  @Test
  void theRootShowsThePendingFallbackUntilTheRootArrives() {
    Edt.run(() -> {
      List<A2uiPendingInfo> infos = new ArrayList<>();
      surface.create(Map.of());
      A2uiSurface view =
          mount(
              A2uiFallbacks.NONE.withPending(info -> {
                infos.add(info);
                return new JLabel("Loading");
              }));

      assertEquals("Loading", ((JLabel) view.getComponent(0)).getText());
      assertEquals(List.of(new A2uiPendingInfo("root", Optional.empty())), infos);
      assertEquals(Optional.empty(), view.viewTree());

      surface.update(component("root", "Label", "text", "Hello"));

      assertEquals(1, view.getComponentCount());
      assertEquals("Hello", ((JLabel) view.getComponent(0)).getText());
    });
  }

  @Test
  void aSurfaceWithNoRootAndNoFallbackShowsNothing() {
    Edt.run(() -> {
      surface.create(Map.of());

      A2uiSurface view = mount(A2uiFallbacks.NONE);

      assertEquals(0, view.getComponentCount());
    });
  }

  @Test
  void theDisconnectionDisposesTheViewsAndStopsToFollowTheSurface() {
    Edt.run(() -> {
      surface.create(Map.of("name", "Ada"), form());
      A2uiSurface view = mount(A2uiFallbacks.NONE);
      JLabel echo = (JLabel) ((JPanel) view.getComponent(0)).getComponent(2);
      assertEquals(4, view.activeSubscriptionCount());

      view.removeNotify();
      surface.setData("/name", "Grace");

      assertFalse(view.isMounted());
      assertEquals(0, view.getComponentCount());
      assertEquals(0, view.activeSubscriptionCount());
      assertEquals(List.of("title", "name", "echo", "root"), surface.catalog.disposed);
      assertEquals("Ada", echo.getText());
      assertEquals(Optional.empty(), view.viewTree());
    });
  }

  @Test
  void aSecondConnectionMakesNewViewsFromTheModel() {
    Edt.run(() -> {
      surface.create(Map.of("name", "Ada"), form());
      A2uiSurface view = mount(A2uiFallbacks.NONE);
      Component before = view.getComponent(0);
      view.removeNotify();
      surface.setData("/name", "Grace");

      view.addNotify();

      assertNotSame(before, view.getComponent(0));
      assertEquals("Grace", ((JLabel) ((JPanel) view.getComponent(0)).getComponent(2)).getText());
      assertEquals(8, surface.catalog.created.size());
      assertEquals(4, view.activeSubscriptionCount());
    });
  }

  @Test
  void theDeletionOfTheSurfaceMakesTheComponentEmpty() {
    Edt.run(() -> {
      surface.create(Map.of("name", "Ada"), form());
      A2uiSurface view = mount(A2uiFallbacks.NONE.withPending(info -> new JLabel("Loading")));

      surface.delete();

      assertFalse(view.isMounted());
      assertEquals(0, view.getComponentCount());
      assertEquals(0, view.activeSubscriptionCount());
      assertEquals(4, surface.catalog.disposed.size());

      view.removeNotify();
      view.addNotify();

      assertFalse(view.isMounted());
      assertEquals(0, view.getComponentCount());
    });
  }

  @Test
  void aSurfaceShowsComponentsOfTwoCatalogs() {
    Edt.run(() -> {
      String badgesId = "https://example.com/catalogs/badges/v1/catalog.json";
      SwingComponentImplementation badge =
          SwingComponentImplementation.of(
              "StatusBadge",
              Map.of(
                  "type",
                  "object",
                  "properties",
                  Map.of(
                      "component",
                      Map.of("const", "StatusBadge"),
                      "badgeText",
                      Map.of("$ref", "common_types.json#/$defs/DynamicString")),
                  "required",
                  List.of("component", "badgeText")),
              context -> {
                JLabel label = new JLabel();
                label.setName("badge");
                return SwingComponentView.of(
                    label,
                    (node, props) -> label.setText(Coercion.toText(props.value("badgeText"))));
              });
      Catalog<SwingComponentImplementation, FunctionApi> badges =
          new Catalog<>(badgesId, List.of(badge), List.of(), null, ProtocolVersion.V1_0);
      MessageProcessor<SwingComponentImplementation> processor =
          new MessageProcessor<>(
              List.of(new TestCatalog().catalog(), badges), ValidationConfig.RELAXED);
      processor.processMessages(
          Map.of(
              "version",
              "1.0",
              "createSurface",
              object(
                  "surfaceId",
                  "s",
                  "catalogId",
                  TestCatalog.ID,
                  "components",
                  List.of(
                      component("root", "Box", "children", List.of("heading", "badge")),
                      component("heading", "Label", "text", "System Status"),
                      component(
                          "badge",
                          "StatusBadge",
                          "@catalogId",
                          badgesId,
                          "badgeText",
                          "Operational")))));

      A2uiSurface view =
          Headless.mount(new A2uiSurface(processor.model().getSurface("s").orElseThrow()));

      JPanel box = (JPanel) view.getComponent(0);
      assertEquals("System Status", ((JLabel) box.getComponent(0)).getText());
      assertEquals("badge", box.getComponent(1).getName());
      assertEquals("Operational", ((JLabel) box.getComponent(1)).getText());
    });
  }
}
