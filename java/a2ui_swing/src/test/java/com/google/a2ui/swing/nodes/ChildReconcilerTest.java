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
import static com.google.a2ui.swing.testing.TestSurface.path;
import static com.google.a2ui.swing.testing.TestSurface.template;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeResolver;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.surface.A2uiFallbacks;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.TestCatalog.BoxView;
import com.google.a2ui.swing.testing.TestSurface;
import java.awt.Component;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class ChildReconcilerTest {
  private final TestSurface surface = new TestSurface();

  /** The changes of the Swing container of the root, for example {@code add B}. */
  private final List<String> changes = new ArrayList<>();

  private A2uiFallbacks fallbacks = A2uiFallbacks.NONE;
  private SurfaceScope scope;
  private NodeResolver<SwingComponentImplementation> resolver;
  private NodeHost rootHost;

  /** Makes the surface, shows its root and starts to record the changes of the root container. */
  private JPanel mount(Map<String, Object> data, Object... components) {
    surface.create(data, components);
    scope = new SurfaceScope(surface.model(), fallbacks, A2uiServices.DEFAULT);
    resolver = new NodeResolver<>(surface.model());
    rootHost = new NodeHost(scope, host -> {});
    resolver.rootNode().subscribe(root -> root.ifPresent(rootHost::bind));
    rootHost.bind(root());
    JPanel panel = (JPanel) rootHost.component().orElseThrow();
    panel.addContainerListener(
        new ContainerListener() {
          @Override
          public void componentAdded(ContainerEvent event) {
            changes.add("add " + textOf(event.getChild()));
          }

          @Override
          public void componentRemoved(ContainerEvent event) {
            changes.add("remove " + textOf(event.getChild()));
          }
        });
    return panel;
  }

  private ComponentNode<SwingComponentImplementation> root() {
    return resolver.rootNode().get().orElseThrow();
  }

  private BoxView box() {
    return (BoxView) rootHost.view().orElseThrow();
  }

  private static String textOf(Component component) {
    return switch (component) {
      case JLabel label -> label.getText();
      case JTextField field -> field.getText();
      case JButton button -> button.getText();
      default -> component.getClass().getSimpleName();
    };
  }

  private static List<String> textsOf(JPanel panel) {
    List<String> texts = new ArrayList<>();
    for (Component component : panel.getComponents()) {
      texts.add(textOf(component));
    }
    return texts;
  }

  private static Object[] abc(Object... children) {
    return new Object[] {
      component("root", "Box", "children", List.of(children)),
      component("a", "Label", "text", "A"),
      component("b", "Label", "text", "B"),
      component("c", "Label", "text", "C")
    };
  }

  @Test
  void theContainerHasTheChildrenInTheSequenceOfTheList() {
    Edt.run(() -> {
      JPanel panel = mount(Map.of(), abc("a", "b", "c"));

      assertEquals(List.of("A", "B", "C"), textsOf(panel));
      assertEquals(List.of(panel.getComponents()), box().children.components());
      assertSame(panel, box().children.container());
      assertEquals(3, rootHost.childHosts().size());
      assertEquals(4, scope.activeSubscriptions());
    });
  }

  @Test
  void aChildThatStaysKeepsItsComponentWhenAChildArrivesBeforeIt() {
    Edt.run(() -> {
      JPanel panel = mount(Map.of(), abc("a", "c"));
      Component a = panel.getComponent(0);
      Component c = panel.getComponent(1);

      surface.update(component("root", "Box", "children", List.of("a", "b", "c")));

      assertEquals(List.of("A", "B", "C"), textsOf(panel));
      assertSame(a, panel.getComponent(0));
      assertSame(c, panel.getComponent(2));
      assertEquals(List.of("add B"), changes);
    });
  }

  @Test
  void aChildThatLeavesTheListIsRemovedAndItsViewIsDisposed() {
    Edt.run(() -> {
      JPanel panel = mount(Map.of(), abc("a", "b", "c"));
      Component c = panel.getComponent(2);

      surface.update(component("root", "Box", "children", List.of("a", "c")));

      assertEquals(List.of("A", "C"), textsOf(panel));
      assertSame(c, panel.getComponent(1));
      assertEquals(List.of("remove B"), changes);
      assertEquals(List.of("b"), surface.catalog.disposed);
      assertEquals(3, scope.activeSubscriptions());
    });
  }

  @Test
  void aChangedSequenceMovesTheComponentsAndDoesNotRemoveThem() {
    Edt.run(() -> {
      JPanel panel = mount(Map.of(), abc("a", "b", "c"));
      List<Component> before = List.of(panel.getComponents());

      surface.update(component("root", "Box", "children", List.of("c", "a", "b")));

      assertEquals(List.of("C", "A", "B"), textsOf(panel));
      assertSame(before.get(2), panel.getComponent(0));
      assertSame(before.get(0), panel.getComponent(1));
      assertSame(before.get(1), panel.getComponent(2));
      assertEquals(List.of(), changes);
      assertEquals(List.of(), surface.catalog.disposed);
      assertEquals(4, surface.catalog.created.size());
    });
  }

  @Test
  void anUpdateThatKeepsTheListDoesNotChangeTheContainer() {
    Edt.run(() -> {
      JPanel panel = mount(Map.of(), abc("a", "b", "c"));
      List<Component> before = List.of(panel.getComponents());
      // A component that has a peer can be valid.
      panel.addNotify();
      panel.setSize(100, 100);
      panel.validate();
      assertTrue(panel.isValid());

      box().children.sync(root().props().get().children("children"));

      assertEquals(before, List.of(panel.getComponents()));
      assertEquals(List.of(), changes);
      assertTrue(panel.isValid());
    });
  }

  @Test
  void aChildThatDidNotArriveGetsNoSlotAndThenArrivesInItsPosition() {
    Edt.run(() -> {
      JPanel panel =
          mount(
              Map.of(),
              component("root", "Box", "children", List.of("a", "late", "c")),
              component("a", "Label", "text", "A"),
              component("c", "Label", "text", "C"));
      Component a = panel.getComponent(0);
      Component c = panel.getComponent(1);

      assertEquals(List.of("A", "C"), textsOf(panel));
      assertEquals(3, rootHost.childHosts().size());
      assertEquals(Optional.empty(), rootHost.childHosts().get(1).component());

      surface.update(component("late", "Label", "text", "Late"));

      assertEquals(List.of("A", "Late", "C"), textsOf(panel));
      assertSame(a, panel.getComponent(0));
      assertSame(c, panel.getComponent(2));
      assertSame(panel, rootHost.component().orElseThrow());
      assertEquals(List.of("add Late"), changes);
    });
  }

  @Test
  void aChildOfAnUnknownTypeGetsNoSlot() {
    Edt.run(() -> {
      JPanel panel =
          mount(
              Map.of(),
              component("root", "Box", "children", List.of("a", "odd")),
              component("a", "Label", "text", "A"),
              component("odd", "Chart"));

      assertEquals(List.of("A"), textsOf(panel));
      assertEquals(1, surface.errors.size());
    });
  }

  @Test
  void theFallbackOfAChildIsAUsualChildUntilTheComponentArrives() {
    Edt.run(() -> {
      fallbacks = A2uiFallbacks.NONE.withPending(info -> new JLabel("wait " + info.componentId()));
      JPanel panel =
          mount(
              Map.of(),
              component("root", "Box", "children", List.of("a", "late")),
              component("a", "Label", "text", "A"));
      Component a = panel.getComponent(0);

      assertEquals(List.of("A", "wait late"), textsOf(panel));

      surface.update(component("late", "Label", "text", "Late"));

      assertEquals(List.of("A", "Late"), textsOf(panel));
      assertSame(a, panel.getComponent(0));
      assertEquals(List.of("remove wait late", "add Late"), changes);
    });
  }

  @Test
  void aChildWhoseTypeChangesGetsANewComponentInTheSamePosition() {
    Edt.run(() -> {
      JPanel panel = mount(Map.of(), abc("a", "b", "c"));
      Component a = panel.getComponent(0);
      Component b = panel.getComponent(1);

      surface.update(component("b", "Field", "value", "B2"));

      assertEquals(List.of("A", "B2", "C"), textsOf(panel));
      assertSame(a, panel.getComponent(0));
      assertNotSame(b, panel.getComponent(1));
      assertEquals(List.of("b"), surface.catalog.disposed);
    });
  }

  @Test
  void aListTemplateKeepsTheViewsOfTheItemsThatStay() {
    Edt.run(() -> {
      JPanel panel =
          mount(
              Map.of("items", List.of(Map.of("name", "Alpha"), Map.of("name", "Beta"))),
              component("root", "Box", "children", template("item", "/items")),
              component("item", "Label", "text", path("name")));
      Component first = panel.getComponent(0);
      Component second = panel.getComponent(1);
      assertEquals(List.of("Alpha", "Beta"), textsOf(panel));

      surface.setData(
          "/items",
          List.of(Map.of("name", "Alpha"), Map.of("name", "Gamma"), Map.of("name", "Beta")));

      assertEquals(List.of("Alpha", "Gamma", "Beta"), textsOf(panel));
      assertSame(first, panel.getComponent(0));
      assertSame(second, panel.getComponent(1));
      assertEquals(List.of("add Beta"), changes);
      changes.clear();

      surface.setData("/items", List.of(Map.of("name", "Beta")));

      assertEquals(List.of("Beta"), textsOf(panel));
      assertSame(first, panel.getComponent(0));
      assertEquals(2, changes.size());
      assertEquals(2, scope.activeSubscriptions());
    });
  }

  @Test
  void theWeightOfAChildIsItsLayoutConstraintAndFollowsTheChild() {
    Edt.run(() -> {
      JPanel panel =
          mount(
              Map.of(),
              component("root", "Box", "children", List.of("a", "b")),
              component("a", "Label", "text", "A", "weight", 1),
              component("b", "Label", "text", "B"));
      Component a = panel.getComponent(0);
      Component b = panel.getComponent(1);
      assertEquals(1.0, box().layout.weightOf(a));
      assertEquals(0.0, box().layout.weightOf(b));
      panel.addNotify();
      panel.setSize(100, 100);
      panel.validate();
      assertTrue(panel.isValid());

      surface.update(component("a", "Label", "text", "A", "weight", 3));

      assertSame(a, panel.getComponent(0));
      assertEquals(3.0, box().layout.weightOf(a));
      assertEquals(List.of(), changes);
      assertFalse(panel.isValid());

      surface.update(component("a", "Label", "text", "A"));

      assertEquals(0.0, box().layout.weightOf(a));
    });
  }

  @Test
  void aButtonShowsItsOneChildInsideItself() {
    Edt.run(() -> {
      surface.create(
          Map.of(),
          component("root", "Button", "child", "text"),
          component("text", "Label", "text", "Save"));
      scope = new SurfaceScope(surface.model(), fallbacks, A2uiServices.DEFAULT);
      resolver = new NodeResolver<>(surface.model());
      rootHost = new NodeHost(scope, host -> {});
      rootHost.bind(root());

      JButton button = (JButton) rootHost.component().orElseThrow();
      assertEquals(1, button.getComponentCount());
      assertEquals("Save", ((JLabel) button.getComponent(0)).getText());
      assertEquals(
          Optional.of(button.getComponent(0)),
          rootHost.childHosts().get(0).component().map(Component.class::cast));
    });
  }

  @Test
  void theDisposalOfAParentDisposesTheViewsOfItsChildren() {
    Edt.run(() -> {
      JPanel panel =
          mount(
              Map.of(),
              component("root", "Box", "children", List.of("inner", "c")),
              component("inner", "Box", "children", List.of("a", "b")),
              component("a", "Label", "text", "A"),
              component("b", "Label", "text", "B"),
              component("c", "Label", "text", "C"));
      BoxView rootView = box();

      rootHost.dispose();

      assertEquals(List.of("a", "b", "inner", "c", "root"), surface.catalog.disposed);
      assertEquals(0, scope.activeSubscriptions());
      assertEquals(0, panel.getComponentCount());
      assertEquals(List.of(), rootHost.childHosts());
      assertThrows(IllegalStateException.class, () -> rootView.children.sync(List.of()));
    });
  }
}
