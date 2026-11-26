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

package com.google.a2ui.swing.catalog.basic;

import static com.google.a2ui.swing.testing.TestSurface.component;
import static com.google.a2ui.swing.testing.TestSurface.event;
import static com.google.a2ui.swing.testing.TestSurface.object;
import static com.google.a2ui.swing.testing.TestSurface.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.ModalPresenter;
import com.google.a2ui.swing.surface.MountedNode;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.LogCapture;
import com.google.a2ui.swing.testing.TestModalPresenter;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;

/**
 * The tests use a presenter that needs no window. The surface is 400 pixels wide and 300 pixels
 * high.
 */
class ModalViewTest {
  private final TestModalPresenter presenter = new TestModalPresenter();
  private final BasicFixture fixture =
      new BasicFixture(A2uiServices.DEFAULT.withModalPresenter(presenter));

  private static Object text(String id, Object text) {
    return component(id, "Text", "text", text);
  }

  /** Makes a surface whose root is a modal with a button as its trigger. */
  private void showWithButton(Object... buttonProperties) {
    List<Object> button = new ArrayList<>(List.of("child", "label", "action", event("opened")));
    button.addAll(List.of(buttonProperties));
    fixture.create(
        Map.of("body", "The details", "ready", true),
        component("root", "Modal", "trigger", "open", "content", "body"),
        component("open", "Button", button.toArray()),
        text("label", "Open"),
        text("body", path("/body")));
  }

  /** Makes a surface whose root is a modal with a text as its trigger. */
  private void showWithText() {
    fixture.create(
        Map.of("body", "The details"),
        component("root", "Modal", "trigger", "open", "content", "body"),
        text("open", "More information"),
        text("body", path("/body")));
  }

  private JComponent trigger() {
    return fixture.componentOf("root");
  }

  private JButton button() {
    return (JButton) fixture.viewOf("open").accessibleComponent();
  }

  /** Gets the IDs of the children that the view of the modal mounts. */
  private List<String> mounted() {
    List<String> ids = new ArrayList<>();
    for (MountedNode child : fixture.node("root").children()) {
      ids.add(child.node().componentId());
    }
    return ids;
  }

  private TestModalPresenter.Presentation shown() {
    assertEquals(1, presenter.open().size(), "one modal is open");
    return presenter.open().get(0);
  }

  /** Clicks a component with the left mouse button, as AWT does for the user. */
  private static void click(Component target) {
    target.dispatchEvent(
        new MouseEvent(
            target, MouseEvent.MOUSE_CLICKED, 0L, 0, 3, 3, 1, false, MouseEvent.BUTTON1));
  }

  /** Finds the first text component in a component. */
  private static JTextComponent textIn(Component component) {
    if (component instanceof JTextComponent text) {
      return text;
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        JTextComponent found = textIn(child);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  @Test
  void onlyTheTriggerShowsAndTheContentHasNoViewWhileTheModalIsClosed() {
    Edt.run(() -> {
      showWithButton();

      assertEquals(List.of("open"), mounted());
      assertEquals(List.of(), presenter.presentations());
      assertEquals("Open", VisibleText.of(fixture.view));
      // The panel of the trigger adds no space around the trigger.
      assertEquals(new Rectangle(0, 0, 400, 300), fixture.bounds("root"));
      assertEquals(new Insets(0, 0, 0, 0), trigger().getInsets());
      assertEquals(new Rectangle(0, 0, 400, 300), fixture.bounds("open"));
      assertFalse(trigger().isOpaque());
    });
  }

  @Test
  void aButtonInTheTriggerDoesItsActionAndThenOpensTheModal() {
    Edt.run(() -> {
      List<Integer> actionsAtTheOpening = new ArrayList<>();
      BasicFixture[] recorded = {null};
      ModalPresenter recorder =
          (owner, content, onClosed) -> {
            actionsAtTheOpening.add(recorded[0].actions.size());
            return presenter.present(owner, content, onClosed);
          };
      recorded[0] = new BasicFixture(A2uiServices.DEFAULT.withModalPresenter(recorder));
      recorded[0].create(
          Map.of(),
          component("root", "Modal", "trigger", "open", "content", "body"),
          component("open", "Button", "child", "label", "action", event("opened")),
          text("label", "Open"),
          text("body", "The details"));

      ((JButton) recorded[0].viewOf("open").accessibleComponent()).doClick(0);

      assertEquals(1, recorded[0].actions.size());
      assertEquals("opened", recorded[0].actions.get(0).name());
      assertEquals("open", recorded[0].actions.get(0).sourceComponentId());
      // The presenter got the content after the action of the button.
      assertEquals(List.of(1), actionsAtTheOpening);
      assertEquals(1, presenter.open().size());
    });
  }

  @Test
  void theOpenModalMountsTheContentAfterTheTriggerAndGivesItToThePresenter() {
    Edt.run(() -> {
      showWithButton();

      button().doClick(0);

      assertEquals(List.of("open", "body"), mounted());
      assertSame(trigger(), shown().owner());
      assertSame(fixture.componentOf("body").getParent(), shown().content());
      assertEquals("The details", VisibleText.of(shown().content()));
      // The surface shows only the trigger.
      assertEquals("Open", VisibleText.of(fixture.view));
      assertEquals(1, trigger().getComponentCount());
    });
  }

  @Test
  void aSecondOperationOfTheTriggerDoesNotOpenASecondModal() {
    Edt.run(() -> {
      showWithButton();

      button().doClick(0);
      JComponent body = fixture.componentOf("body");
      button().doClick(0);

      assertEquals(1, presenter.presentations().size());
      assertSame(body, fixture.componentOf("body"));
      // The button does its action each time.
      assertEquals(2, fixture.actions.size());
    });
  }

  @Test
  void aButtonThatTheUserCannotOperateDoesNotOpenTheModal() {
    Edt.run(() -> {
      showWithButton(
          "checks", List.of(Map.of("condition", path("/never"), "message", "Not possible.")));

      assertFalse(button().isEnabled());
      button().doClick(0);
      click(button());

      assertEquals(List.of(), presenter.presentations());
      assertEquals(List.of("open"), mounted());
    });
  }

  @Test
  void theContentFollowsTheDataAndTheMessagesWhileTheModalIsOpen() {
    Edt.run(() -> {
      showWithButton();
      button().doClick(0);
      JComponent content = shown().content();

      fixture.setData("/body", "New details");

      assertEquals("New details", VisibleText.of(content));

      fixture.update(
          component("root", "Modal", "trigger", "open", "content", "other"),
          text("other", "Other content"));

      assertEquals(List.of("open", "other"), mounted());
      assertSame(content, shown().content());
      assertEquals("Other content", VisibleText.of(content));
      assertEquals(1, presenter.presentations().size());
    });
  }

  @Test
  void theViewOfTheContentIsDisposedWhenTheUserClosesTheModal() {
    Edt.run(() -> {
      showWithButton();
      int closed = fixture.view.activeSubscriptionCount();
      button().doClick(0);
      JComponent body = fixture.componentOf("body");
      JComponent content = shown().content();

      assertTrue(fixture.view.activeSubscriptionCount() > closed);

      shown().userCloses();

      assertEquals(List.of("open"), mounted());
      assertEquals(0, content.getComponentCount());
      assertNull(body.getParent());
      assertEquals(closed, fixture.view.activeSubscriptionCount());
      assertEquals(List.of(), presenter.open());

      // A new opening makes a new view of the content.
      button().doClick(0);

      assertEquals(List.of("open", "body"), mounted());
      assertNotSame(body, fixture.componentOf("body"));
      assertSame(content, shown().content());
      assertEquals(2, presenter.presentations().size());
    });
  }

  @Test
  void aClickOnATextInTheTriggerOpensTheModal() {
    Edt.run(() -> {
      showWithText();

      click(textIn(fixture.componentOf("open")));

      assertEquals(List.of("open", "body"), mounted());
      assertEquals("The details", VisibleText.of(shown().content()));
    });
  }

  @Test
  void aClickOnTheMarginOfTheTriggerOpensTheModalAndADifferentMouseButtonDoesNot() {
    Edt.run(() -> {
      showWithText();
      Component leaf = fixture.componentOf("open");

      leaf.dispatchEvent(
          new MouseEvent(
              leaf, MouseEvent.MOUSE_CLICKED, 0L, 0, 3, 3, 1, false, MouseEvent.BUTTON3));

      assertEquals(List.of(), presenter.presentations());

      click(leaf);

      assertEquals(1, presenter.open().size());
    });
  }

  @Test
  void aTriggerWithNoButtonGetsTheFocusAndOpensTheModalWithTheKeyboard() {
    Edt.run(() -> {
      showWithText();
      JComponent trigger = trigger();

      assertTrue(trigger.isFocusable());
      assertEquals(
          ModalView.OPEN_ACTION,
          trigger
              .getInputMap(JComponent.WHEN_FOCUSED)
              .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0)));
      assertEquals(
          ModalView.OPEN_ACTION,
          trigger
              .getInputMap(JComponent.WHEN_FOCUSED)
              .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0)));

      trigger
          .getActionMap()
          .get(ModalView.OPEN_ACTION)
          .actionPerformed(new ActionEvent(trigger, ActionEvent.ACTION_PERFORMED, ""));

      assertEquals(1, presenter.open().size());
    });
  }

  @Test
  void aTriggerWithAButtonDoesNotGetTheFocusBecauseTheButtonGetsIt() {
    Edt.run(() -> {
      showWithButton();
      JComponent trigger = trigger();

      assertFalse(trigger.isFocusable());
      assertTrue(button().isFocusable());

      trigger
          .getActionMap()
          .get(ModalView.OPEN_ACTION)
          .actionPerformed(new ActionEvent(trigger, ActionEvent.ACTION_PERFORMED, ""));

      assertEquals(List.of(), presenter.presentations());
    });
  }

  @Test
  void aClickOnATriggerWhoseCheckFailsDoesNotOpenTheModal() {
    Edt.run(() -> {
      fixture.create(
          Map.of("name", ""),
          component("root", "Modal", "trigger", "field", "content", "body"),
          component(
              "field",
              "TextField",
              "label",
              "Name",
              "value",
              path("/name"),
              "checks",
              List.of(
                  Map.of(
                      "condition",
                      Map.of("@call", "required", "args", Map.of("value", path("/name"))),
                      "message",
                      "Enter a name."))),
          text("body", "The details"));
      Component leaf = fixture.componentOf("field");

      click(leaf);

      assertEquals(List.of(), presenter.presentations());

      fixture.setData("/name", "Ada");
      click(leaf);

      assertEquals(1, presenter.open().size());
    });
  }

  @Test
  void aModalInTheTriggerOfAModalOpensOnlyItself() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Modal", "trigger", "row", "content", "outer_body"),
          component("row", "Row", "children", List.of("beside", "inner")),
          text("beside", "Beside"),
          component("inner", "Modal", "trigger", "inner_text", "content", "inner_body"),
          text("inner_text", "Inner"),
          text("outer_body", "Outer content"),
          text("inner_body", "Inner content"));

      click(textIn(fixture.componentOf("inner_text")));

      assertEquals("Inner content", VisibleText.of(shown().content()));
      assertSame(fixture.componentOf("inner"), shown().owner());
      shown().userCloses();

      click(textIn(fixture.componentOf("beside")));

      assertEquals("Outer content", VisibleText.of(shown().content()));
      assertSame(fixture.componentOf("root"), shown().owner());
    });
  }

  @Test
  void aButtonInTheContentOfAModalDoesNotOpenTheModalAgain() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component("root", "Modal", "trigger", "open", "content", "confirm"),
          component("open", "Button", "child", "label", "action", event("opened")),
          text("label", "Open"),
          component("confirm", "Button", "child", "confirm_label", "action", event("confirmed")),
          text("confirm_label", "Confirm"));
      button().doClick(0);

      ((JButton) fixture.viewOf("confirm").accessibleComponent()).doClick(0);

      assertEquals(1, presenter.presentations().size());
      assertEquals("confirmed", fixture.actions.get(1).name());
    });
  }

  @Test
  void anUpdateKeepsThePanelOfTheTriggerAndShowsTheNewTrigger() {
    Edt.run(() -> {
      showWithButton();
      JComponent trigger = trigger();

      fixture.update(
          component("root", "Modal", "trigger", "link", "content", "body"),
          text("link", "Details"));

      assertSame(trigger, trigger());
      assertEquals(List.of("link"), mounted());
      assertEquals("Details", VisibleText.of(trigger));
      assertTrue(trigger.isFocusable());
    });
  }

  @Test
  void aTriggerWithNoButtonIsAButtonForAssistiveTools() {
    Edt.run(() -> {
      showWithText();
      AccessibleContext context = trigger().getAccessibleContext();
      AccessibleAction action = context.getAccessibleAction();

      assertSame(trigger(), fixture.viewOf("root").accessibleComponent());
      assertEquals(AccessibleRole.PUSH_BUTTON, context.getAccessibleRole());
      assertEquals("More information", context.getAccessibleName());
      assertEquals(1, action.getAccessibleActionCount());

      assertTrue(action.doAccessibleAction(0));

      assertEquals(1, presenter.open().size());
      assertFalse(action.doAccessibleAction(1));
    });
  }

  @Test
  void aTriggerWithAButtonIsAPanelAndTheButtonHasTheName() {
    Edt.run(() -> {
      showWithButton();
      AccessibleContext context = trigger().getAccessibleContext();

      assertEquals(AccessibleRole.PANEL, context.getAccessibleRole());
      assertNull(context.getAccessibleAction());
      assertNull(context.getAccessibleName());
      assertEquals("Open", button().getAccessibleContext().getAccessibleName());
    });
  }

  @Test
  void theLabelOfTheAgentIsTheAccessibleNameOfTheTrigger() {
    Edt.run(() -> {
      fixture.create(
          Map.of(),
          component(
              "root",
              "Modal",
              "trigger",
              "open",
              "content",
              "body",
              "@accessibility",
              object("label", "Show the details", "description", "Opens a dialog")),
          text("open", "More information"),
          text("body", "The details"));
      AccessibleContext context = trigger().getAccessibleContext();

      assertEquals("Show the details", context.getAccessibleName());
      assertEquals("Opens a dialog", context.getAccessibleDescription());
    });
  }

  @Test
  void aPresenterThatFailsLeavesTheModalClosedAndTheSurfaceInOperation() {
    Edt.run(() -> {
      BasicFixture failing =
          new BasicFixture(
              A2uiServices.DEFAULT.withModalPresenter((owner, content, onClosed) -> {
                throw new IllegalStateException("no display");
              }));
      failing.create(
          Map.of(),
          component("root", "Modal", "trigger", "open", "content", "body"),
          component("open", "Button", "child", "label", "action", event("opened")),
          text("label", "Open"),
          text("body", "The details"));
      int closed = failing.view.activeSubscriptionCount();

      try (LogCapture log = new LogCapture()) {
        ((JButton) failing.viewOf("open").accessibleComponent()).doClick(0);

        assertEquals(List.of("The modal presenter did not show the modal."), log.messages);
      }
      assertEquals(1, failing.node("root").children().size());
      assertEquals(closed, failing.view.activeSubscriptionCount());
      assertEquals(1, failing.actions.size());
    });
  }

  @Test
  void theDisposalClosesTheOpenModalAndRemovesTheListeners() {
    Edt.run(() -> {
      showWithText();
      JComponent trigger = trigger();
      JTextComponent text = textIn(fixture.componentOf("open"));
      int textListeners = text.getMouseListeners().length;
      click(text);
      TestModalPresenter.Presentation presentation = shown();
      JComponent content = presentation.content();

      fixture.view.removeNotify();

      assertFalse(presentation.isOpen());
      assertTrue(presentation.closedByHandle());
      assertEquals(0, content.getComponentCount());
      assertEquals(0, trigger.getComponentCount());
      assertEquals(0, fixture.view.activeSubscriptionCount());
      assertEquals(textListeners - 1, text.getMouseListeners().length);
      assertEquals(0, trigger.getMouseListeners().length);
      assertEquals(0, trigger.getContainerListeners().length);
      assertEquals(0, trigger.getFocusListeners().length);
      assertNull(trigger.getClientProperty(ModalView.OPENER_PROPERTY));
      // An operation after the disposal opens nothing.
      click(trigger);
      assertEquals(1, presenter.presentations().size());
    });
  }

  @Test
  void theDisposalOfAClosedModalRemovesTheListenersOfTheComponentsOfTheTrigger() {
    Edt.run(() -> {
      showWithButton();
      JComponent trigger = trigger();
      JButton button = button();
      Component leaf = fixture.componentOf("open");

      assertEquals(1, leaf.getMouseListeners().length);

      fixture.view.removeNotify();

      assertEquals(0, leaf.getMouseListeners().length);
      assertEquals(0, trigger.getMouseListeners().length);
      button.doClick(0);
      assertEquals(List.of(), presenter.presentations());
      assertEquals(0, fixture.view.activeSubscriptionCount());
    });
  }
}
