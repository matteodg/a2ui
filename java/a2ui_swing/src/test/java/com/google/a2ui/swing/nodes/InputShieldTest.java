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

import com.google.a2ui.core.state.RendererAction;
import com.google.a2ui.swing.surface.A2uiSurface;
import com.google.a2ui.swing.surface.MountedNode;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.Headless;
import com.google.a2ui.swing.testing.Pointer;
import com.google.a2ui.swing.testing.TestSurface;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The shield goes into its layered pane in the event after {@code install}. Thus each test is a
 * sequence of events of the event dispatch thread.
 */
class InputShieldTest {
  private static final int WIDTH = 300;
  private static final int HEIGHT = 200;

  private JRootPane rootPane;
  private JPanel content;
  private JButton button;
  private InputShield shield;
  private final List<String> clicks = new ArrayList<>();

  /** Runs each block as one event of the event dispatch thread. */
  private static void events(Edt.Block... blocks) {
    for (Edt.Block block : blocks) {
      Edt.run(block);
    }
  }

  /** Makes a root pane that is in no window, with a content pane that has no layout. */
  private static JRootPane rootPane() {
    JPanel holder = new JPanel(null);
    JRootPane pane = new JRootPane();
    holder.add(pane);
    pane.setContentPane(new JPanel(null));
    pane.addNotify();
    pane.setSize(WIDTH, HEIGHT);
    pane.validate();
    return pane;
  }

  /** Makes a root pane with one button, and a shield for the button that is not installed. */
  private void make() {
    rootPane = rootPane();
    content = (JPanel) rootPane.getContentPane();
    button = new JButton("Go");
    button.addActionListener(event -> clicks.add("go"));
    button.setBounds(20, 20, 100, 30);
    content.add(button);
    shield = new InputShield(button);
  }

  @Test
  void aShieldTakesTheMouseEventsOfItsComponentAfterTheSubsequentEvent() {
    events(
        this::make,
        () -> {
          assertSame(button, Pointer.click(button));
          assertEquals(List.of("go"), clicks);

          shield.install();

          // The layered pane does not change in the event that installs the shield.
          assertNull(shield.getParent());
        },
        () -> {
          assertSame(rootPane.getLayeredPane(), shield.getParent());
          assertSame(shield, Pointer.click(button));
          assertSame(shield, Pointer.click(button, 0, 0));
          assertSame(shield, Pointer.click(button, 99, 29));
          assertEquals(List.of("go"), clicks);
          assertFalse(shield.isFocusable());
        });
  }

  @Test
  void aShieldTakesNoEventOutsideItsComponent() {
    JButton other = new JButton("Other");
    events(
        this::make,
        () -> {
          other.addActionListener(event -> clicks.add("other"));
          other.setBounds(20, 60, 100, 30);
          content.add(other);
          shield.install();
        },
        () -> {
          assertSame(other, Pointer.click(other));
          assertEquals(List.of("other"), clicks);
          // A point of the content that has no component gets no shield.
          assertFalse(shield.contains(200, 150));
          assertFalse(shield.contains(19, 25));
          assertTrue(shield.contains(20, 20));
          assertTrue(shield.contains(119, 49));
          assertFalse(shield.contains(120, 49));
        });
  }

  @Test
  void aShieldHasNoSizeAndIsInFrontOfTheLayerOfItsComponent() {
    events(
        this::make,
        () -> shield.install(),
        () -> {
          JLayeredPane pane = rootPane.getLayeredPane();
          assertEquals(0, shield.getWidth());
          assertEquals(0, shield.getHeight());
          assertEquals(JLayeredPane.getLayer(content), JLayeredPane.getLayer(shield));
          assertTrue(pane.getIndexOf(shield) < pane.getIndexOf(content));
        });
  }

  @Test
  void aShieldStopsAtOnceAndLeavesTheLayeredPaneInALaterEvent() {
    events(
        this::make,
        () -> shield.install(),
        () -> {
          int listeners = button.getHierarchyListeners().length;

          shield.uninstall();

          assertSame(button, Pointer.click(button));
          assertEquals(List.of("go"), clicks);
          assertEquals(listeners - 1, button.getHierarchyListeners().length);
        },
        () -> {
          assertNull(shield.getParent());
          assertEquals(1, rootPane.getLayeredPane().getComponentCount());
        });
  }

  @Test
  void aShieldFollowsItsComponentToADifferentRootPane() {
    JRootPane[] second = {null};
    events(
        this::make,
        () -> shield.install(),
        () -> {
          second[0] = rootPane();
          second[0].getContentPane().add(button);
        },
        () -> {
          assertSame(second[0].getLayeredPane(), shield.getParent());
          assertEquals(1, rootPane.getLayeredPane().getComponentCount());
          assertSame(shield, Pointer.click(button));

          second[0].getContentPane().remove(button);

          // The shield has no effect for a component that left its pane.
          assertFalse(shield.contains(25, 25));
        },
        () -> {
          assertNull(shield.getParent());
          assertEquals(List.of(), clicks);
        });
  }

  @Test
  void aShieldCoversOnlyThePartOfItsComponentThatAScrollPaneShows() {
    JScrollPane[] scroll = {null};
    events(
        this::make,
        () -> {
          JPanel tall = new JPanel(null);
          tall.setPreferredSize(new Dimension(200, 600));
          content.remove(button);
          button.setBounds(0, 80, 100, 40);
          tall.add(button);
          scroll[0] = new JScrollPane(tall);
          scroll[0].setBorder(null);
          scroll[0].setBounds(0, 0, 250, 100);
          content.add(scroll[0]);
          rootPane.validate();
          shield.install();
        },
        () -> {
          // The viewport shows the rows 0 to 99 of the panel. The button starts at the row 80.
          assertTrue(shield.contains(10, 85));
          assertTrue(shield.contains(10, 99));
          assertFalse(shield.contains(10, 100));
          assertFalse(shield.contains(10, 79));

          scroll[0].getViewport().setViewPosition(new Point(0, 60));

          // The shield has no bounds to change. It is correct after the scroll at once.
          assertTrue(shield.contains(10, 20));
          assertTrue(shield.contains(10, 59));
          assertFalse(shield.contains(10, 60));
          assertFalse(shield.contains(10, 19));

          scroll[0].getViewport().setViewPosition(new Point(0, 300));

          assertFalse(shield.contains(10, 20));
          assertFalse(shield.contains(10, 85));
        });
  }

  @Test
  void aShieldCoversNothingWhileAParentOfItsComponentIsNotVisible() {
    events(
        this::make,
        () -> shield.install(),
        () -> {
          assertTrue(shield.contains(25, 25));

          content.setVisible(false);
          assertFalse(shield.contains(25, 25));

          content.setVisible(true);
          button.setVisible(false);
          assertFalse(shield.contains(25, 25));

          button.setVisible(true);
          assertTrue(shield.contains(25, 25));
        });
  }

  @Test
  void aTurnOfTheWheelGoesToTheListenerAboveAllTheCoveredComponents() {
    List<String> turns = new ArrayList<>();
    JPanel[] panels = new JPanel[2];
    InputShield[] outerShield = {null};
    events(
        this::make,
        () -> {
          // The button is in a panel that listens for the wheel. That panel is in an outer panel
          // that also listens.
          JPanel outer = new JPanel(null);
          JPanel inner = new JPanel(null);
          outer.setBounds(0, 0, WIDTH, HEIGHT);
          inner.setBounds(0, 0, WIDTH, HEIGHT);
          outer.addMouseWheelListener(event -> turns.add("outer"));
          inner.addMouseWheelListener(event -> turns.add("inner"));
          content.remove(button);
          inner.add(button);
          outer.add(inner);
          content.add(outer);
          panels[0] = outer;
          panels[1] = inner;
          shield.install();
        },
        () -> {
          assertSame(shield, Pointer.wheel(button, 1));
          assertEquals(List.of("inner"), turns);

          // The inner panel is also covered. Thus the turn goes above it.
          outerShield[0] = new InputShield(panels[1]);
          outerShield[0].install();
        },
        () -> {
          turns.clear();
          Component target = Pointer.wheel(button, 1);

          assertTrue(target == shield || target == outerShield[0]);
          assertEquals(List.of("outer"), turns);
        });
  }

  @Test
  void aShieldOfAComponentThatIsInNoRootPaneDoesNothing() {
    events(
        () -> {
          button = new JButton("Go");
          JPanel panel = new JPanel(null);
          panel.add(button);
          shield = new InputShield(button);
          shield.install();
        },
        () -> {
          assertNull(shield.getParent());
          assertFalse(shield.contains(1, 1));
          shield.uninstall();
        },
        () -> assertNull(shield.getParent()));
  }

  @Test
  void theViewsOfACustomCatalogBelowAHiddenComponentGetNoEventOfTheUser() {
    TestSurface surface = new TestSurface();
    A2uiSurface[] view = {null};
    events(
        () -> {
          surface.create(
              Map.of("secret", true, "name", ""),
              component(
                  "root",
                  "Box",
                  "children",
                  List.of("save", "name"),
                  "@accessibility",
                  Map.of("hidden", path("/secret"))),
              component("save", "Button", "label", "Save", "action", event("save")),
              component("name", "Field", "value", path("/name")));
          view[0] = new A2uiSurface(surface.model());
          Headless.mountInRootPane(view[0], WIDTH, HEIGHT);
        },
        () -> {
          JButton save = (JButton) controlOf(view[0], "save");
          JTextField name = (JTextField) controlOf(view[0], "name");

          assertFalse(save.isFocusable());
          assertTrue(Pointer.click(save) instanceof InputShield);
          assertFalse(Pointer.clickAndType(save, KeyEvent.VK_SPACE, ' '));
          assertTrue(Pointer.click(name) instanceof InputShield);
          assertFalse(Pointer.clickAndType(name, KeyEvent.VK_A, 'a'));
          assertEquals(List.of(), surface.actions);
          assertEquals("", surface.model().dataModel().get("/name"));

          surface.setData("/secret", false);

          assertSame(save, Pointer.click(save));
          assertTrue(Pointer.clickAndType(name, KeyEvent.VK_A, 'a'));
          assertEquals(
              List.of("save"), surface.actions.stream().map(RendererAction::name).toList());
          assertEquals("a", surface.model().dataModel().get("/name"));
          assertTrue(save.isFocusable());
        },
        () -> {
          JRootPane pane = SwingUtilities.getRootPane(view[0]);
          assertEquals(1, pane.getLayeredPane().getComponentCount());
        });
  }

  private static JComponent controlOf(A2uiSurface view, String componentId) {
    MountedNode node = view.viewTree().orElseThrow().find(componentId).orElseThrow();
    return node.view().orElseThrow().accessibleComponent();
  }
}
