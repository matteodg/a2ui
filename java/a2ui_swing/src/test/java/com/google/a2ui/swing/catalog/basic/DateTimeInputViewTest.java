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

import static com.google.a2ui.swing.testing.TestSurface.component;
import static com.google.a2ui.swing.testing.TestSurface.object;
import static com.google.a2ui.swing.testing.TestSurface.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.swing.catalog.basic.DateTimeInputView.IsoModel;
import com.google.a2ui.swing.catalog.basic.DateTimeInputView.IsoValue;
import com.google.a2ui.swing.catalog.basic.DateTimeInputView.Mode;
import com.google.a2ui.swing.testing.Edt;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import org.junit.jupiter.api.Test;

class DateTimeInputViewTest {
  private final BasicFixture fixture = new BasicFixture();

  /** The writes to the path /when. */
  private final List<Object> writes = new ArrayList<>();

  /** A clock at 2025-03-04 05:06:07 in the zone UTC. */
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2025-03-04T05:06:07Z"), ZoneOffset.UTC);

  private static Object input(Object... properties) {
    List<Object> all = new ArrayList<>();
    if (!List.of(properties).contains("label")) {
      all.addAll(List.of("label", "When"));
    }
    if (!List.of(properties).contains("value")) {
      all.addAll(List.of("value", path("/when")));
    }
    all.addAll(List.of(properties));
    return component("root", "DateTimeInput", all.toArray());
  }

  /** Makes a surface whose root is a date input with the label "When" and the value of /when. */
  private void show(Map<String, Object> data, Object... properties) {
    fixture.create(data, input(properties));
    fixture.model().dataModel().subscribe("/when", writes::add);
  }

  private JFormattedTextField field() {
    return (JFormattedTextField) fixture.viewOf("root").accessibleComponent();
  }

  private JSpinner spinner() {
    return (JSpinner) panel().getComponent(1);
  }

  private JComponent panel() {
    return fixture.componentOf("root");
  }

  private IsoModel model() {
    return (IsoModel) spinner().getModel();
  }

  @Test
  void theViewShowsALabelAboveASpinnerWithTheDate() {
    Edt.run(() -> {
      show(Map.of("when", "2025-06-15"), "enableDate", true);

      assertEquals("2025-06-15", field().getText());
      assertEquals("2025-06-15", field().getValue());
      assertEquals("2025-06-15", spinner().getValue());
      assertTrue(field().isEditable());
      JLabel label = (JLabel) panel().getComponent(0);
      assertEquals("When", label.getText());
      assertSame(field(), label.getLabelFor());
      assertEquals("When", field().getAccessibleContext().getAccessibleName());
      assertEquals("yyyy-mm-dd", field().getClientProperty(TextFieldView.PLACEHOLDER_PROPERTY));
      assertEquals(BasicFixture.WIDTH - 16, spinner().getWidth());
      assertEquals(List.of(), writes);
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void theFlagsSelectTheParts() {
    Edt.run(() -> {
      show(Map.of("when", "2025-01-01T08:00:00"), "enableDate", true, "enableTime", true);
      assertEquals("2025-01-01 08:00:00", field().getText());
      assertEquals(
          "yyyy-mm-dd hh:mm", field().getClientProperty(TextFieldView.PLACEHOLDER_PROPERTY));

      fixture.update(input("enableTime", true));
      assertEquals("08:00:00", field().getText());
      assertEquals("hh:mm", field().getClientProperty(TextFieldView.PLACEHOLDER_PROPERTY));

      fixture.update(input("enableDate", true));
      assertEquals("2025-01-01", field().getText());

      // With no flag, the field has a date.
      fixture.update(input());
      assertEquals("2025-01-01", field().getText());
      assertEquals(Mode.DATE, Mode.of(false, false));

      // The field shows the value in the form of the parts, and writes nothing.
      assertEquals(List.of(), writes);
      assertEquals("2025-01-01T08:00:00", fixture.data("/when"));
    });
  }

  @Test
  void theUserTypesADateAndTheViewWritesIsoText() {
    Edt.run(() -> {
      fixture.create(
          Map.of("when", ""),
          component("root", "Column", "children", List.of("input", "preview")),
          component(
              "input",
              "DateTimeInput",
              "label",
              "When",
              "enableDate",
              true,
              "value",
              path("/when")),
          component("preview", "Text", "text", path("/when")));
      JFormattedTextField field =
          (JFormattedTextField) fixture.viewOf("input").accessibleComponent();

      field.replaceSelection("2025-02-2");
      // An incomplete date is not a value.
      assertEquals("", fixture.data("/when"));

      field.replaceSelection("8");

      assertEquals("2025-02-28", fixture.data("/when"));
      assertEquals("2025-02-28", VisibleText.of(fixture.componentOf("preview")));
      assertEquals("2025-02-28", field.getText());
      assertEquals(10, field.getCaretPosition());

      // A date that does not exist is not a value.
      field.setText("2025-02-30");
      assertEquals("2025-02-28", fixture.data("/when"));
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void theUserTypesADateAndATimeWithASpaceOrWithT() {
    Edt.run(() -> {
      show(Map.of("when", ""), "enableDate", true, "enableTime", true);

      field().setText("2025-01-31 09:30");
      assertEquals("2025-01-31T09:30", fixture.data("/when"));

      field().setText("2025-01-31T09:30:15");
      assertEquals("2025-01-31T09:30:15", fixture.data("/when"));

      // A date alone is not a value of a field with a date and a time.
      field().setText("2025-02-01");
      assertEquals("2025-01-31T09:30:15", fixture.data("/when"));
    });
  }

  @Test
  void theViewKeepsTheZoneAndTheSecondsOfTheValue() {
    Edt.run(() -> {
      show(Map.of("when", "2025-01-01T08:00:00Z"), "enableDate", true, "enableTime", true);

      assertEquals("2025-01-01 08:00:00 Z", field().getText());

      model().setUnit(ChronoUnit.HOURS);
      spinner().setValue(spinner().getNextValue());

      // The zone stays, and the time is not changed to the zone of the computer.
      assertEquals("2025-01-01T09:00:00Z", fixture.data("/when"));

      fixture.setData("/when", "2025-01-01T08:00+02:00");
      assertEquals("2025-01-01 08:00 +02:00", field().getText());
      model().setUnit(ChronoUnit.MINUTES);
      spinner().setValue(spinner().getPreviousValue());
      assertEquals("2025-01-01T07:59+02:00", fixture.data("/when"));

      // A typed time with no seconds keeps the seconds of the value before.
      fixture.setData("/when", "2025-01-01T08:00:30");
      field().setText("2025-01-02 10:00");
      assertEquals("2025-01-02T10:00:00", fixture.data("/when"));
    });
  }

  @Test
  void aTimeFieldWritesHoursAndMinutes() {
    Edt.run(() -> {
      show(Map.of("when", ""), "enableTime", true);

      field().setText("7:30");
      assertEquals("", fixture.data("/when"));

      field().setText("07:30");
      assertEquals("07:30", fixture.data("/when"));

      field().setText("24:00");
      assertEquals("07:30", fixture.data("/when"));
    });
  }

  @Test
  void theUserRemovesTheValueWithTheRemovalOfTheText() {
    Edt.run(() -> {
      show(Map.of("when", "2025-06-15"), "enableDate", true);

      field().selectAll();
      field().replaceSelection("");

      assertEquals("", fixture.data("/when"));
      assertEquals("", field().getText());
    });
  }

  @Test
  void theArrowsOfAnEmptyFieldStartAtTheClockAndStopAtTheLimits() {
    Edt.run(() -> {
      IsoModel model = new IsoModel(CLOCK);
      model.configure(Mode.DATE, null, null);

      assertEquals("2025-03-04", model.getNextValue());
      assertEquals("2025-03-04", model.getPreviousValue());

      model.setValue("2025-03-04");
      assertEquals("2025-03-05", model.getNextValue());
      model.setUnit(ChronoUnit.MONTHS);
      assertEquals("2025-04-04", model.getNextValue());
      // A date field ignores a unit of the time.
      model.setUnit(ChronoUnit.HOURS);
      assertEquals("2025-03-03", model.getPreviousValue());

      IsoValue min = IsoValue.parse("2025-03-01").orElseThrow();
      IsoValue max = IsoValue.parse("2025-03-05").orElseThrow();
      model.configure(Mode.DATE, min, max);
      model.setValue("2025-03-05");
      assertNull(model.getNextValue());
      model.setValue("2025-03-01");
      assertNull(model.getPreviousValue());
      model.setValue("2025-01-01");
      assertEquals("2025-03-01", model.getNextValue());

      // Limits in the wrong sequence have no effect.
      model.configure(Mode.DATE, max, min);
      assertEquals("2025-01-02", model.getNextValue());

      model.configure(Mode.TIME, null, null);
      model.setValue("");
      assertEquals("05:06", model.getNextValue());
      assertEquals(ChronoUnit.MINUTES, model.unit());
      model.setValue("23:59");
      assertEquals("00:00", model.getNextValue());

      model.configure(Mode.DATE_TIME, null, null);
      model.setValue("");
      assertEquals("2025-03-04T05:06", model.getNextValue());
    });
  }

  @Test
  void theCaretSelectsThePartOfTheArrows() {
    Optional<IsoValue> withSeconds = IsoValue.parse("2025-01-01T08:00:00");
    assertEquals(ChronoUnit.YEARS, DateTimeInputView.unitAt(Mode.DATE, 0, Optional.empty()));
    assertEquals(ChronoUnit.MONTHS, DateTimeInputView.unitAt(Mode.DATE, 6, Optional.empty()));
    assertEquals(ChronoUnit.DAYS, DateTimeInputView.unitAt(Mode.DATE, 10, Optional.empty()));
    assertEquals(ChronoUnit.HOURS, DateTimeInputView.unitAt(Mode.DATE_TIME, 12, withSeconds));
    assertEquals(ChronoUnit.MINUTES, DateTimeInputView.unitAt(Mode.DATE_TIME, 15, withSeconds));
    assertEquals(ChronoUnit.SECONDS, DateTimeInputView.unitAt(Mode.DATE_TIME, 18, withSeconds));
    assertEquals(ChronoUnit.MINUTES, DateTimeInputView.unitAt(Mode.TIME, 7, Optional.empty()));
    assertEquals(ChronoUnit.HOURS, DateTimeInputView.unitAt(Mode.TIME, 1, Optional.empty()));
  }

  @Test
  void theParserReadsTheFormsOfIso8601() {
    assertEquals(
        new IsoValue(LocalDate.of(2025, 6, 15), null, false, ""),
        IsoValue.parse("2025-06-15").orElseThrow());
    assertEquals(
        new IsoValue(null, LocalTime.of(9, 30), false, ""), IsoValue.parse("09:30").orElseThrow());
    assertEquals(
        new IsoValue(null, LocalTime.of(9, 30, 5), true, ""),
        IsoValue.parse("09:30:05").orElseThrow());
    assertEquals(
        new IsoValue(LocalDate.of(2025, 1, 1), LocalTime.of(8, 0), true, "Z"),
        IsoValue.parse("2025-01-01t08:00:00.000z").orElseThrow());
    assertEquals("+0200", IsoValue.parse("2025-01-01T08:00+0200").orElseThrow().zone());
    assertEquals("-05", IsoValue.parse("2025-01-01T08:00-05").orElseThrow().zone());
    assertEquals(
        "2025-01-01T08:00:00Z", IsoValue.parse("2025-01-01 08:00:00 Z").orElseThrow().iso());
    for (String wrong :
        List.of(
            "",
            "tomorrow",
            "2025-13-01",
            "2025-02-29",
            "25:00",
            "09:30Z",
            "2025-01-01T08:00+25:00",
            "2025-01-01T08",
            "2025/01/01")) {
      assertEquals(Optional.empty(), IsoValue.parse(wrong), wrong);
    }
    IsoValue date = IsoValue.parse("2025-06-15").orElseThrow();
    assertEquals("2025-06-15 00:00", date.in(Mode.DATE_TIME).orElseThrow().display());
    assertEquals(Optional.empty(), date.in(Mode.TIME));
    assertEquals(Optional.empty(), IsoValue.parse("09:30").orElseThrow().in(Mode.DATE));
  }

  @Test
  void aValueThatIsNotIsoTextShowsAsItIsAndIsNotWritten() {
    Edt.run(() -> {
      show(Map.of("when", "next Monday"), "enableDate", true);

      assertEquals("next Monday", field().getText());
      assertEquals("next Monday", field().getValue());

      // A time is not a value of a date field.
      fixture.setData("/when", "09:30");
      assertEquals("09:30", field().getText());

      fixture.setData("/when", 42L);
      assertEquals("42", field().getText());

      fixture.setData("/when", null);
      assertEquals("", field().getText());

      // The commit of the text of the field does not write the value again.
      field().commitEdit();
      assertEquals(3, writes.size());
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void aFieldThatTheUserDidNotEditWritesNothing() {
    Edt.run(() -> {
      show(Map.of("when", "2025-06-15"), "enableDate", true, "enableTime", true);

      assertEquals("2025-06-15 00:00", field().getText());

      field().commitEdit();
      field().setText(field().getText());

      assertEquals(List.of(), writes);
      assertEquals("2025-06-15", fixture.data("/when"));
    });
  }

  @Test
  void aNewValueOfTheDataModelShowsAndWritesNothing() {
    Edt.run(() -> {
      show(Map.of("when", "2025-06-15", "other", 1L), "enableDate", true);
      JFormattedTextField field = field();
      List<Object> changes = new ArrayList<>();
      spinner().addChangeListener(event -> changes.add(spinner().getValue()));

      fixture.setData("/when", "2025-07-01");

      assertEquals("2025-07-01", field.getText());
      assertEquals(List.of("2025-07-01"), changes);
      assertEquals(List.of("2025-07-01"), writes);

      fixture.setData("/other", 2L);
      fixture.update(input("enableDate", true, "label", "Day"));

      assertSame(field, field());
      assertEquals(List.of("2025-07-01"), changes);
      assertEquals(1, writes.size());
    });
  }

  @Test
  void theFieldRefusesATypedValueThatIsNotBetweenTheLimits() {
    Edt.run(() -> {
      show(
          Map.of("when", "2025-06-15", "min", "2025-06-01", "max", "2025-06-30T12:00:00"),
          "enableDate",
          true,
          "min",
          path("/min"),
          "max",
          path("/max"));

      field().setText("2025-07-01");
      assertEquals("2025-06-15", fixture.data("/when"));

      field().setText("2025-06-30");
      assertEquals("2025-06-30", fixture.data("/when"));

      model().setUnit(ChronoUnit.DAYS);
      assertNull(spinner().getNextValue());
    });
  }

  @Test
  void theLimitsCanBeLiteralIsoText() {
    Edt.run(() -> {
      show(
          Map.of("when", "2025-06-15"),
          "enableDate",
          true,
          "min",
          "2025-06-01",
          "max",
          "2025-06-30T12:00:00Z");

      field().setText("2025-05-31");
      assertEquals("2025-06-15", fixture.data("/when"));

      field().setText("2025-06-01");
      assertEquals("2025-06-01", fixture.data("/when"));

      model().setUnit(ChronoUnit.DAYS);
      assertNull(spinner().getPreviousValue());
    });
  }

  @Test
  void aValueWithNoBindingChangesOnlyTheField() {
    Edt.run(() -> {
      show(Map.of("when", ""), "enableDate", true, "value", "2025-06-15");

      field().setText("2025-06-16");

      assertEquals("2025-06-16", spinner().getValue());
      assertEquals("", fixture.data("/when"));
      assertEquals(List.of(), fixture.errors);

      fixture.update(input("enableDate", true, "value", "2025-06-15", "label", "Other"));
      assertEquals("2025-06-15", field().getText());
    });
  }

  @Test
  void theMessagesOfTheChecksShowBelowTheField() {
    Edt.run(() -> {
      show(
          Map.of("when", "", "ok", false),
          "enableDate",
          true,
          "checks",
          List.of(Map.of("condition", path("/ok"), "message", "Select a date.")));

      JComponent lines = (JComponent) panel().getComponent(2);
      assertTrue(lines.isVisible());
      assertEquals("Select a date.", field().getToolTipText());
      assertTrue(field().isEditable());

      fixture.setData("/ok", true);
      assertFalse(lines.isVisible());
      assertNull(field().getToolTipText());
    });
  }

  @Test
  void theAccessibleNameOfTheAgentWins() {
    Edt.run(() -> {
      show(Map.of("when", ""), "@accessibility", object("label", "Date of the visit"));

      assertEquals("Date of the visit", field().getAccessibleContext().getAccessibleName());
    });
  }

  @Test
  void theDisposalRemovesTheListenerOfTheSpinner() {
    Edt.run(() -> {
      show(Map.of("when", ""), "enableDate", true);
      JSpinner spinner = spinner();
      JFormattedTextField field = field();
      int listeners = spinner.getChangeListeners().length;

      fixture.view.removeNotify();

      assertEquals(listeners - 1, spinner.getChangeListeners().length);
      field.setText("2025-06-16");
      assertEquals("", fixture.data("/when"));
      assertEquals(0, fixture.view.activeSubscriptionCount());
    });
  }
}
