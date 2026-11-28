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

package com.google.a2ui.swing.catalog.basic;

import static com.google.a2ui.swing.testing.TestSurface.call;
import static com.google.a2ui.swing.testing.TestSurface.component;
import static com.google.a2ui.swing.testing.TestSurface.object;
import static com.google.a2ui.swing.testing.TestSurface.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.catalog.basic.TextFieldView.Variant;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.Snapshots;
import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;

class TextFieldViewTest {
  private final BasicFixture fixture = new BasicFixture();

  private static Map<String, Object> check(Object condition, String message) {
    return Map.of("condition", condition, "message", message);
  }

  private static Object field(Object... properties) {
    List<Object> all = new ArrayList<>();
    if (!List.of(properties).contains("label")) {
      all.addAll(List.of("label", "Name"));
    }
    if (!List.of(properties).contains("value")) {
      all.addAll(List.of("value", path("/name")));
    }
    all.addAll(List.of(properties));
    return component("root", "TextField", all.toArray());
  }

  /** Makes a surface whose root is a text field with the label "Name" and the value of /name. */
  private void show(Map<String, Object> data, Object... properties) {
    fixture.create(data, field(properties));
  }

  private JTextComponent control() {
    return (JTextComponent) fixture.viewOf("root").accessibleComponent();
  }

  private JComponent panel() {
    return fixture.componentOf("root");
  }

  private JLabel label() {
    return (JLabel) panel().getComponent(0);
  }

  /** Gets the labels of the messages that the view shows below the control. */
  private List<WrappingLabel> messages() {
    JComponent holder = (JComponent) panel().getComponent(2);
    List<WrappingLabel> labels = new ArrayList<>();
    if (holder.isVisible()) {
      for (Component line : holder.getComponents()) {
        labels.add((WrappingLabel) line);
      }
    }
    return labels;
  }

  private List<String> messageTexts() {
    return messages().stream().map(WrappingLabel::getText).toList();
  }

  /** Types text at the caret, as the keyboard does. */
  private void type(String text) {
    control().replaceSelection(text);
  }

  private Rectangle boundsOf(Component component) {
    return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), panel());
  }

  @Test
  void theViewShowsALabelAboveATextFieldWithTheValue() {
    Edt.run(() -> {
      show(Map.of("name", "Ada"));

      assertEquals(Variant.SHORT_TEXT, Variant.fromWire(null));
      assertInstanceOf(JTextField.class, control());
      assertFalse(control() instanceof JPasswordField);
      assertEquals("Ada", control().getText());
      assertTrue(control().isEditable());
      assertEquals("Name", label().getText());
      assertTrue(label().isVisible());
      assertSame(control(), label().getLabelFor());
      // The label is above the control, and the two fill the width in the margin.
      assertEquals(new Insets(8, 8, 8, 8), panel().getInsets());
      assertEquals(8, boundsOf(label()).y);
      assertEquals(boundsOf(label()).y + boundsOf(label()).height + 3, boundsOf(control()).y);
      assertEquals(8, boundsOf(control()).x);
      assertEquals(BasicFixture.WIDTH - 16, boundsOf(control()).width);
      assertEquals(control().getPreferredSize().height, boundsOf(control()).height);
      assertEquals(List.of(), messages());
      assertEquals(
          8 + label().getPreferredSize().height + 3 + control().getPreferredSize().height + 8,
          panel().getPreferredSize().height);
    });
  }

  @Test
  void anEmptyLabelUsesNoSpace() {
    Edt.run(() -> {
      show(Map.of("name", "Ada"), "label", "");

      assertFalse(label().isVisible());
      assertEquals(8, boundsOf(control()).y);
      assertEquals(control().getPreferredSize().height + 16, panel().getPreferredSize().height);
    });
  }

  @Test
  void anEditOfTheUserGoesToTheDataModelAndToTheOtherViews() {
    Edt.run(() -> {
      fixture.create(
          Map.of("name", "Ada"),
          component("root", "Column", "children", List.of("input", "preview")),
          component("input", "TextField", "label", "Name", "value", path("/name")),
          component("preview", "Text", "text", path("/name")));
      JTextComponent input = (JTextComponent) fixture.viewOf("input").accessibleComponent();

      input.setCaretPosition(3);
      input.replaceSelection(" Lovelace");

      assertEquals("Ada Lovelace", fixture.data("/name"));
      assertEquals("Ada Lovelace", VisibleText.of(fixture.componentOf("preview")));
      assertEquals(List.of(), fixture.errors);

      input.setText("Grace");

      assertEquals("Grace", fixture.data("/name"));
      assertEquals("Grace", VisibleText.of(fixture.componentOf("preview")));
    });
  }

  @Test
  void theValueThatComesBackFromTheDataModelDoesNotMoveTheCaret() {
    Edt.run(() -> {
      show(Map.of("name", "Hello world", "other", 1L));
      JTextComponent control = control();
      List<String> changes = new ArrayList<>();
      control
          .getDocument()
          .addDocumentListener(
              new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                  changes.add("insert");
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                  changes.add("remove");
                }

                @Override
                public void changedUpdate(DocumentEvent event) {}
              });

      control.setCaretPosition(5);
      type(",");

      // The data model gave the same text back. Thus the view did not set the text again.
      assertEquals("Hello, world", fixture.data("/name"));
      assertEquals(List.of("insert"), changes);
      assertEquals(6, control.getCaretPosition());

      control.select(7, 12);
      fixture.setData("/other", 2L);
      fixture.update(field("placeholder", "Your name"));

      assertSame(control, control());
      assertEquals(List.of("insert"), changes);
      assertEquals(7, control.getSelectionStart());
      assertEquals(12, control.getSelectionEnd());
    });
  }

  @Test
  void aNewValueOfTheDataModelShowsInTheSameControl() {
    Edt.run(() -> {
      show(Map.of("name", "Ada"));
      JTextComponent control = control();
      JComponent panel = panel();

      fixture.setData("/name", "Grace");

      assertSame(control, control());
      assertSame(panel, panel());
      assertEquals("Grace", control.getText());

      fixture.setData("/name", 42L);
      assertEquals("42", control.getText());

      fixture.setData("/name", null);
      assertEquals("", control.getText());
    });
  }

  @Test
  void aValueWithNoBindingChangesOnlyTheControl() {
    Edt.run(() -> {
      show(Map.of(), "value", "fixed");

      type("!");

      assertEquals("fixed!", control().getText());
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void thePlaceholderShowsWhileTheControlIsEmpty() {
    Edt.run(() -> {
      show(Map.of("name", ""), "placeholder", "Your name");
      JTextComponent control = control();
      control.setBackground(Color.WHITE);
      control.setForeground(Color.BLACK);

      assertEquals("Your name", control.getClientProperty(TextFieldView.PLACEHOLDER_PROPERTY));
      BufferedImage withPlaceholder = Snapshots.render(control);

      fixture.update(field());

      assertNull(control.getClientProperty(TextFieldView.PLACEHOLDER_PROPERTY));
      BufferedImage empty = Snapshots.render(control);
      assertTrue(differentPixels(withPlaceholder, empty) > 20, "the placeholder paints text");
      // The placeholder has a color between the text color and the background color.
      assertTrue(hasColor(withPlaceholder, 0x808080));
      assertFalse(hasColor(empty, 0x808080));

      fixture.update(field("placeholder", "Your name"));
      type("A");

      assertEquals(0, differentPixels(Snapshots.render(control), withText(control, "A")));
      assertEquals("A", fixture.data("/name"));
    });
  }

  /** Paints the control with a text and with no placeholder. */
  private static BufferedImage withText(JTextComponent control, String text) {
    Object placeholder = control.getClientProperty(TextFieldView.PLACEHOLDER_PROPERTY);
    control.putClientProperty(TextFieldView.PLACEHOLDER_PROPERTY, null);
    assertEquals(text, control.getText());
    BufferedImage image = Snapshots.render(control);
    control.putClientProperty(TextFieldView.PLACEHOLDER_PROPERTY, placeholder);
    return image;
  }

  private static int differentPixels(BufferedImage first, BufferedImage second) {
    int count = 0;
    for (int x = 0; x < first.getWidth(); x++) {
      for (int y = 0; y < first.getHeight(); y++) {
        if (first.getRGB(x, y) != second.getRGB(x, y)) {
          count++;
        }
      }
    }
    return count;
  }

  private static boolean hasColor(BufferedImage image, int rgb) {
    for (int x = 0; x < image.getWidth(); x++) {
      for (int y = 0; y < image.getHeight(); y++) {
        if ((image.getRGB(x, y) & 0xffffff) == rgb) {
          return true;
        }
      }
    }
    return false;
  }

  @Test
  void theLongTextVariantIsATextAreaOfThreeLinesInAScrollPane() {
    Edt.run(() -> {
      show(Map.of("name", "line one\nline two"), "variant", "longText");

      JTextArea area = assertInstanceOf(JTextArea.class, control());
      assertEquals("line one\nline two", area.getText());
      assertEquals(3, area.getRows());
      assertTrue(area.getLineWrap());
      assertTrue(area.getWrapStyleWord());
      assertEquals(new JTextField().getFont(), area.getFont());
      JScrollPane scroll = assertInstanceOf(JScrollPane.class, panel().getComponent(1));
      assertSame(area, scroll.getViewport().getView());
      assertEquals(BasicFixture.WIDTH - 16, scroll.getWidth());
      assertTrue(scroll.getHeight() >= 3 * area.getFontMetrics(area.getFont()).getHeight());
      assertSame(area, label().getLabelFor());

      area.setCaretPosition(area.getDocument().getLength());
      type("\nline three");

      assertEquals("line one\nline two\nline three", fixture.data("/name"));
    });
  }

  @Test
  void theObscuredVariantIsAPasswordFieldThatWritesTheText() {
    Edt.run(() -> {
      show(Map.of("name", ""), "variant", "obscured", "placeholder", "Password");

      JPasswordField secret = assertInstanceOf(JPasswordField.class, control());
      assertNotEquals((char) 0, secret.getEchoChar());
      assertEquals("Password", secret.getClientProperty(TextFieldView.PLACEHOLDER_PROPERTY));

      type("s3cret");

      assertEquals("s3cret", fixture.data("/name"));
      assertEquals("s3cret", new String(secret.getPassword()));
    });
  }

  @Test
  void theNumberVariantAcceptsOnlyTheTextOfADecimalNumber() {
    Edt.run(() -> {
      show(Map.of("name", ""), "variant", "number");
      JTextComponent control = control();

      assertInstanceOf(JTextField.class, control);
      for (String key : List.of("-", "1", "a", "2", ".", ".", "5", " ", "e", "-", "0")) {
        control.setCaretPosition(control.getDocument().getLength());
        type(key);
      }

      assertEquals("-12.50", control.getText());
      assertEquals("-12.50", fixture.data("/name"));

      // The filter refuses an edit as a whole.
      control.selectAll();
      type("1,234");
      assertEquals("-12.50", control.getText());

      control.selectAll();
      type("1234");
      assertEquals("1234", fixture.data("/name"));

      control.setCaretPosition(0);
      type("-");
      control.setCaretPosition(0);
      type("-");
      assertEquals("-1234", control.getText());

      control.selectAll();
      type("");
      assertEquals("", fixture.data("/name"));
    });
  }

  @Test
  void theNumberVariantShowsAValueOfTheDataModelAsItIs() {
    Edt.run(() -> {
      show(Map.of("name", "n/a"), "variant", "number");
      JTextComponent control = control();

      assertEquals("n/a", control.getText());

      fixture.setData("/name", 7.5);
      assertEquals("7.5", control.getText());

      fixture.setData("/name", "about 3");
      assertEquals("about 3", control.getText());

      // The user can remove text that is not a number, and cannot add more of it.
      control.select(0, 6);
      type("");
      assertEquals("3", fixture.data("/name"));
      control.setCaretPosition(1);
      type("x");
      assertEquals("3", control.getText());
    });
  }

  @Test
  void aChangeOfTheVariantReplacesTheControlAndKeepsThePanel() {
    Edt.run(() -> {
      show(Map.of("name", "Ada"), "placeholder", "Your name");
      JComponent panel = panel();
      JTextComponent before = control();
      int listenersBefore = ((AbstractDocument) before.getDocument()).getDocumentListeners().length;

      fixture.update(field("variant", "longText", "placeholder", "Your name"));

      JTextComponent after = control();
      assertSame(panel, panel());
      assertNotSame(before, after);
      assertInstanceOf(JTextArea.class, after);
      assertEquals("Ada", after.getText());
      assertEquals("Your name", after.getClientProperty(TextFieldView.PLACEHOLDER_PROPERTY));
      assertEquals(3, panel.getComponentCount());
      assertFalse(SwingUtilities.isDescendingFrom(before, panel));
      assertEquals("Name", after.getAccessibleContext().getAccessibleName());

      after.setCaretPosition(3);
      type("!");
      assertEquals("Ada!", fixture.data("/name"));

      // The control before does not write to the data model.
      assertEquals(
          listenersBefore - 1,
          ((AbstractDocument) before.getDocument()).getDocumentListeners().length);
      before.setText("stale");
      assertEquals("Ada!", fixture.data("/name"));
    });
  }

  @Test
  void theMessageOfAFailedCheckShowsBelowTheControlInTheErrorColor() {
    Edt.run(() -> {
      show(
          Map.of("name", "not-an-email"),
          "checks",
          List.of(check(call("email", "value", path("/name")), "Enter a valid email address.")));
      Color background = fixture.view.getBackground();

      assertEquals(List.of("Enter a valid email address."), messageTexts());
      WrappingLabel message = messages().getFirst();
      assertEquals(BasicStyle.error(panel()), message.getForeground());
      assertTrue(BasicStyle.contrast(message.getForeground(), background) >= 4.5);
      assertTrue(message.getForeground().getRed() > 2 * message.getForeground().getGreen());
      assertEquals(boundsOf(control()).y + boundsOf(control()).height + 3, boundsOf(message).y);
      assertEquals(8, boundsOf(message).x);
      assertEquals("Enter a valid email address.", control().getToolTipText());
      assertEquals("error", control().getClientProperty("JComponent.outline"));
      // The user can still edit the control.
      assertTrue(control().isEnabled());
      assertTrue(control().isEditable());
      assertTrue(message.getFont().getSize2D() < fixture.view.getFont().getSize2D());

      control().setText("ada@example.com");

      assertEquals(List.of(), messages());
      assertNull(control().getToolTipText());
      assertNull(control().getClientProperty("JComponent.outline"));
      assertEquals(
          8 + label().getPreferredSize().height + 3 + control().getPreferredSize().height + 8,
          panel().getPreferredSize().height);
    });
  }

  @Test
  void aWarningAndAnInformationAreHintsThatDoNotStopTheUser() {
    Edt.run(() -> {
      Map<String, Object> warning =
          Map.of("valid", false, "message", "The name is short.", "severity", "warning");
      Map<String, Object> info =
          Map.of("valid", false, "message", "Optional: add a title.", "severity", "info");
      show(
          Map.of("name", "al", "warning", warning, "info", info),
          "checks",
          List.of(
              check(path("/warning"), "unused"),
              check(path("/info"), "unused"),
              check(call("required", "value", path("/name")), "Enter a name.")));

      assertEquals(List.of("The name is short.", "Optional: add a title."), messageTexts());
      assertEquals(BasicStyle.warning(panel()), messages().get(0).getForeground());
      assertEquals(BasicStyle.muted(panel()), messages().get(1).getForeground());
      assertNull(control().getToolTipText());
      assertNull(control().getClientProperty("JComponent.outline"));
      assertEquals(
          boundsOf(messages().get(0)).y + boundsOf(messages().get(0)).height,
          boundsOf(messages().get(1)).y);

      control().setText("");

      assertEquals(
          List.of("The name is short.", "Optional: add a title.", "Enter a name."), messageTexts());
      assertEquals(BasicStyle.error(panel()), messages().get(2).getForeground());
      assertEquals("Enter a name.", control().getToolTipText());
    });
  }

  @Test
  void aLongMessageBreaksAtTheWidthOfTheViewAndTheHeightFollows() {
    Edt.run(() -> {
      show(
          Map.of("name", ""),
          "checks",
          List.of(
              check(
                  call("required", "value", path("/name")),
                  "This message has many words, and it is longer than the width of a small"
                      + " surface. Thus it must be in more than one line.")));
      WrappingLabel message = messages().getFirst();
      int oneLine = message.getPreferredSize().height;
      FlexPanel panel = (FlexPanel) panel();

      assertTrue(message.getPreferredSize().width > 300);

      fixture.resize(200, 300);

      assertEquals(184, message.getWidth());
      assertTrue(message.getHeight() >= 3 * oneLine, "the height is " + message.getHeight());
      assertEquals(panel.preferredHeightFor(200), boundsOf(message).y + message.getHeight() + 8);
      assertTrue(panel.preferredHeightFor(200) > panel.preferredHeightFor(2000));
    });
  }

  @Test
  void theAccessibleNameIsTheLabelUnlessTheAgentGaveOne() {
    Edt.run(() -> {
      show(Map.of("name", ""), "label", "Your Email");

      assertEquals("Your Email", control().getAccessibleContext().getAccessibleName());

      fixture.update(
          field(
              "label",
              "Your Email",
              "@accessibility",
              object("label", "Email address", "description", "Enter a valid email address")));

      assertEquals("Email address", control().getAccessibleContext().getAccessibleName());
      assertEquals(
          "Enter a valid email address",
          control().getAccessibleContext().getAccessibleDescription());
    });
  }

  @Test
  void theDisposalRemovesTheListenerOfTheControl() {
    Edt.run(() -> {
      show(
          Map.of("name", ""),
          "checks",
          List.of(check(call("required", "value", path("/name")), "Enter a name.")));
      JTextComponent control = control();
      AbstractDocument document = (AbstractDocument) control.getDocument();
      int listeners = document.getDocumentListeners().length;

      fixture.view.removeNotify();

      assertEquals(listeners - 1, document.getDocumentListeners().length);
      assertNull(control.getToolTipText());
      control.setText("after");
      assertEquals("", fixture.data("/name"));
      assertEquals(0, fixture.view.activeSubscriptionCount());
    });
  }

  @Test
  void aRemovalInTheNumberVariantGoesToTheDataModel() {
    Edt.run(() -> {
      show(Map.of("name", "x"), "variant", "number");

      control().getDocument().remove(0, 1);

      assertEquals("", fixture.data("/name"));
    });
  }
}
