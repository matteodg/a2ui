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

import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.swing.binding.ValueBinding;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.text.ParseException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.AbstractSpinnerModel;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.DefaultFormatter;
import javax.swing.text.DefaultFormatterFactory;

/**
 * The view of the {@code DateTimeInput} component of the basic catalog: a label, a {@code JSpinner}
 * for a date, a time or a date and a time, and the messages of the checks.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code label}</td><td>The text above the field. It is also the accessible name of the
 *       field, unless the agent gave a label. An empty label uses no space.</td></tr>
 *   <tr><td>{@code enableDate}, {@code enableTime}</td><td>The parts that the user selects. With
 *       the two, the field has a date and a time. With one, the field has only that part. With
 *       none, the field has a date, as in the other adapters of the SDKs.</td></tr>
 *   <tr><td>{@code value}</td><td>The ISO 8601 text of the date or the time. An empty text is no
 *       value. The view writes the new text to the data model each time the user changes the
 *       value. When the data model gives back the text that the field has, the view does not touch
 *       the field.</td></tr>
 *   <tr><td>{@code min}, {@code max}</td><td>The first and the last value that the user can
 *       select, as ISO 8601 text. The arrows stop at these values, and the field refuses a typed
 *       value that is not between them.</td></tr>
 *   <tr><td>{@code checks}</td><td>The messages of the failed checks show below the field, as in
 *       the view of {@code TextField}.</td></tr>
 * </table>
 *
 * <p><b>Control.</b> Plain Swing has no date picker. The field is a {@code JSpinner} with a text
 * field. The text has the fixed form {@code yyyy-mm-dd}, {@code hh:mm} or {@code yyyy-mm-dd hh:mm}
 * with a 24 hour clock, and the empty field shows this form in a weak color. The user can type the
 * value, or put the caret in a part and use the arrows of the spinner or the up and down keys. The
 * arrows of an empty field start at the current date and time of the computer. The user removes the
 * value with the removal of all the text.
 *
 * <p><b>The text that the view writes.</b> The view writes the form of the enabled parts: {@code
 * 2025-06-15} for a date, {@code 09:30} for a time, and {@code 2025-06-15T09:30} for a date and a
 * time. A time has seconds, as in {@code 09:30:00}, if the user typed them or if the value before
 * had them. A date and a time keep the zone of the value: {@code Z}, an offset such as {@code
 * +02:00}, or no zone. The view never changes a value to a different zone. Thus a value with no
 * zone stays a local time, and the view shows the time of the text as it is. The view does not
 * write fractions of a second.
 *
 * <p><b>Values from the data model.</b> The field shows a value of the data model in the form of
 * the enabled parts: the date of a date and a time, or the time of a date and a time, or a date at
 * midnight. The view does not write this form back until the user changes the value. A value that
 * is not ISO 8601 text, or that has no enabled part, shows as it is, and the view does not write
 * it.
 *
 * <p>A {@code value} with no data path is a value that only the field has. The user can change it,
 * and the view writes nothing, until the next update.
 *
 * <p>The Swing component of the view is a {@link LabeledControl}. The accessibility attributes go
 * to the text field of the spinner, which gets the focus.
 */
final class DateTimeInputView implements SwingComponentView {
  /** The parts of a value that the user selects. */
  enum Mode {
    /** A date: {@code yyyy-MM-dd}. */
    DATE("yyyy-mm-dd"),
    /** A time: {@code HH:mm} or {@code HH:mm:ss}. */
    TIME("hh:mm"),
    /** A date and a time: {@code yyyy-MM-ddTHH:mm}, with optional seconds and zone. */
    DATE_TIME("yyyy-mm-dd hh:mm");

    private final String hint;

    Mode(String hint) {
      this.hint = hint;
    }

    /**
     * Gets the form of the text, as the empty field shows it.
     *
     * @return the form, for example {@code yyyy-mm-dd}
     */
    String hint() {
      return hint;
    }

    /** Gets the mode for the two flags of the component. Two false flags give a date. */
    static Mode of(boolean date, boolean time) {
      if (time) {
        return date ? DATE_TIME : TIME;
      }
      return DATE;
    }
  }

  /**
   * A value of ISO 8601 text.
   *
   * @param date the date, or null for a time
   * @param time the time, or null for a date
   * @param seconds true if the text has seconds
   * @param zone the zone of a date and a time: {@code Z}, an offset as the text has it, or empty
   */
  record IsoValue(LocalDate date, LocalTime time, boolean seconds, String zone) {
    private static final Pattern DATE = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern TIME =
        Pattern.compile("(\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.\\d{1,9})?)?");
    private static final Pattern DATE_TIME =
        Pattern.compile(
            DATE.pattern() + "[Tt ]" + TIME.pattern() + "(?: ?([Zz]|[+-]\\d{2}(?::?\\d{2})?))?");

    /**
     * Reads ISO 8601 text: a date, a time, or a date and a time. The separator of a date and a time
     * can be {@code T} or a space.
     *
     * @param text the text
     * @return the value, or an empty result if the text is not one of these forms or is not a
     *     correct date or time
     */
    static Optional<IsoValue> parse(String text) {
      String value = text.strip();
      try {
        Matcher matcher = DATE.matcher(value);
        if (matcher.matches()) {
          return Optional.of(new IsoValue(date(matcher, 1), null, false, ""));
        }
        matcher = TIME.matcher(value);
        if (matcher.matches()) {
          return Optional.of(new IsoValue(null, time(matcher, 1), matcher.group(3) != null, ""));
        }
        matcher = DATE_TIME.matcher(value);
        if (matcher.matches()) {
          String zone = matcher.group(7) == null ? "" : matcher.group(7);
          if (zone.equalsIgnoreCase("z")) {
            zone = "Z";
          } else if (!zone.isEmpty()) {
            // The offset must be one that the time API accepts, for example not +25:00.
            ZoneOffset.of(zone);
          }
          return Optional.of(
              new IsoValue(date(matcher, 1), time(matcher, 4), matcher.group(6) != null, zone));
        }
      } catch (DateTimeException e) {
        // A day, an hour or an offset that does not exist.
      }
      return Optional.empty();
    }

    private static LocalDate date(Matcher matcher, int first) {
      return LocalDate.of(
          Integer.parseInt(matcher.group(first)),
          Integer.parseInt(matcher.group(first + 1)),
          Integer.parseInt(matcher.group(first + 2)));
    }

    private static LocalTime time(Matcher matcher, int first) {
      String seconds = matcher.group(first + 2);
      return LocalTime.of(
          Integer.parseInt(matcher.group(first)),
          Integer.parseInt(matcher.group(first + 1)),
          seconds == null ? 0 : Integer.parseInt(seconds));
    }

    /**
     * Gets the value in the form of a mode.
     *
     * @param mode the mode
     * @return the value, or an empty result if the value does not have the part that the mode needs
     */
    Optional<IsoValue> in(Mode mode) {
      return switch (mode) {
        case DATE ->
            date == null ? Optional.empty() : Optional.of(new IsoValue(date, null, false, ""));
        case TIME ->
            time == null ? Optional.empty() : Optional.of(new IsoValue(null, time, seconds, ""));
        case DATE_TIME ->
            date == null
                ? Optional.empty()
                : Optional.of(
                    new IsoValue(
                        date,
                        time == null ? LocalTime.MIDNIGHT : time,
                        time != null && seconds,
                        time == null ? "" : zone));
      };
    }

    /**
     * Writes the value as ISO 8601 text.
     *
     * @return the text, for example {@code 2025-06-15T09:30Z}
     */
    String iso() {
      return text("T");
    }

    /**
     * Writes the value as the field shows it: the date and the time have a space between them, and
     * a zone has a space before it.
     *
     * @return the text, for example {@code 2025-06-15 09:30 Z}
     */
    String display() {
      String text = text(" ");
      return zone.isEmpty() ? text : text.substring(0, text.length() - zone.length()) + " " + zone;
    }

    private String text(String separator) {
      StringBuilder text = new StringBuilder();
      if (date != null) {
        text.append(date);
      }
      if (time != null) {
        if (date != null) {
          text.append(separator);
        }
        text.append(String.format("%02d:%02d", time.getHour(), time.getMinute()));
        if (seconds) {
          text.append(String.format(":%02d", time.getSecond()));
        }
        text.append(zone);
      }
      return text.toString();
    }

    /** Gets the local date and time of the value, for a comparison of two values of a mode. */
    LocalDateTime key() {
      return (date == null ? LocalDate.EPOCH : date)
          .atTime(time == null ? LocalTime.MIDNIGHT : time);
    }

    /** Adds an amount of a unit. The unit must be a part of the value. */
    IsoValue plus(long amount, ChronoUnit unit) {
      if (date != null && time != null) {
        LocalDateTime moved = key().plus(amount, unit);
        return new IsoValue(moved.toLocalDate(), moved.toLocalTime(), seconds, zone);
      }
      if (date != null) {
        return new IsoValue(date.plus(amount, unit), null, false, zone);
      }
      return new IsoValue(null, time.plus(amount, unit), seconds, zone);
    }
  }

  /**
   * The model of the spinner. Its value is the text of the data model: ISO 8601 text, an empty
   * text, or text that is not correct. The arrows add or remove one unit of the part at the caret.
   */
  static final class IsoModel extends AbstractSpinnerModel {
    private static final long serialVersionUID = 1L;

    private final transient Clock clock;
    private String value = "";
    private Mode mode = Mode.DATE;
    private transient IsoValue min;
    private transient IsoValue max;
    private ChronoUnit unit = ChronoUnit.DAYS;

    /**
     * Makes a model.
     *
     * @param clock the clock that gives the start of the arrows of an empty field
     */
    IsoModel(Clock clock) {
      this.clock = Objects.requireNonNull(clock, "clock");
    }

    Mode mode() {
      return mode;
    }

    /**
     * Sets the mode and the limits.
     *
     * @return true if a value changed
     */
    boolean configure(Mode next, IsoValue low, IsoValue high) {
      if (next == mode && Objects.equals(low, min) && Objects.equals(high, max)) {
        return false;
      }
      if (next != mode) {
        unit = next == Mode.TIME ? ChronoUnit.MINUTES : ChronoUnit.DAYS;
      }
      mode = next;
      // Limits in the wrong sequence have no effect.
      boolean usable = low == null || high == null || !low.key().isAfter(high.key());
      min = usable ? low : null;
      max = usable ? high : null;
      return true;
    }

    /** Gets the unit that the arrows add. */
    ChronoUnit unit() {
      return unit;
    }

    /** Sets the unit that the arrows add, for example the part at the caret. */
    void setUnit(ChronoUnit next) {
      unit = Objects.requireNonNull(next, "next");
    }

    /** Tells if a value of the mode is between the limits. */
    boolean allows(IsoValue candidate) {
      return (min == null || !candidate.key().isBefore(min.key()))
          && (max == null || !candidate.key().isAfter(max.key()));
    }

    /** Gets the value of the mode in the model, or an empty result. */
    Optional<IsoValue> current() {
      return IsoValue.parse(value).flatMap(parsed -> parsed.in(mode));
    }

    @Override
    public Object getValue() {
      return value;
    }

    /**
     * Sets the text of the model.
     *
     * @param next a string
     * @throws IllegalArgumentException if the value is not a string
     */
    @Override
    public void setValue(Object next) {
      if (!(next instanceof String text)) {
        throw new IllegalArgumentException("The value must be a string: " + next);
      }
      if (!text.equals(value)) {
        value = text;
        fireStateChanged();
      }
    }

    @Override
    public Object getNextValue() {
      return step(1);
    }

    @Override
    public Object getPreviousValue() {
      return step(-1);
    }

    /** Gets the text one unit after or before the value, or null if the value cannot move. */
    private Object step(int direction) {
      Optional<IsoValue> now = current();
      IsoValue next;
      if (now.isEmpty()) {
        LocalDateTime start = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        next =
            new IsoValue(start.toLocalDate(), start.toLocalTime(), false, "")
                .in(mode)
                .orElseThrow();
      } else {
        ChronoUnit used = unit;
        if (mode == Mode.DATE && used.isTimeBased()) {
          used = ChronoUnit.DAYS;
        } else if (mode == Mode.TIME && used.isDateBased()) {
          used = ChronoUnit.MINUTES;
        }
        next = now.get().plus(direction, used);
      }
      if (min != null && next.key().isBefore(min.key())) {
        next = withShape(min, now);
      }
      if (max != null && next.key().isAfter(max.key())) {
        next = withShape(max, now);
      }
      String text = next.iso();
      return text.equals(value) ? null : text;
    }

    /** Gives a limit the seconds and the zone of the value. */
    private static IsoValue withShape(IsoValue limit, Optional<IsoValue> now) {
      return now.map(
              value -> new IsoValue(limit.date(), limit.time(), value.seconds(), value.zone()))
          .orElse(limit);
    }
  }

  /** Reads and writes the text of the field. */
  private static final class IsoFormatter extends DefaultFormatter {
    private static final long serialVersionUID = 1L;

    private final IsoModel model;

    IsoFormatter(IsoModel model) {
      this.model = model;
      setCommitsOnValidEdit(true);
      setOverwriteMode(false);
    }

    @Override
    public String valueToString(Object value) {
      String text = value == null ? "" : value.toString();
      return IsoValue.parse(text)
          .flatMap(parsed -> parsed.in(model.mode()))
          .map(IsoValue::display)
          .orElse(text);
    }

    @Override
    public Object stringToValue(String text) throws ParseException {
      JFormattedTextField field = getFormattedTextField();
      Object current = field == null ? model.getValue() : field.getValue();
      // The text of the value that the field has is that value. Thus a field that the user did not
      // edit does not write the form of the field to the data model.
      if (text.equals(valueToString(current))) {
        return current;
      }
      if (text.isBlank()) {
        return "";
      }
      Optional<IsoValue> typed = IsoValue.parse(text);
      if (typed.isEmpty() || !typed.equals(typed.get().in(model.mode()))) {
        throw new ParseException("The text is not a value of the field: " + text, 0);
      }
      IsoValue value = typed.get();
      if (model.mode() != Mode.DATE && !value.seconds()) {
        // The value keeps the seconds that the value before had.
        Optional<IsoValue> before = model.current();
        if (before.isPresent() && before.get().seconds() && value.time().getSecond() == 0) {
          value = new IsoValue(value.date(), value.time(), true, value.zone());
        }
      }
      if (!model.allows(value)) {
        throw new ParseException("The value is not between the limits: " + text, 0);
      }
      return value.iso();
    }
  }

  /** The spinner of the view. Its text has the font of a text field. */
  private static final class Spinner extends JSpinner {
    private static final long serialVersionUID = 1L;

    Spinner(IsoModel model) {
      super(model);
    }

    /**
     * Applies the look and feel, and then the font of a text field. Some look and feels give a
     * spinner a bold font. The spinner gives its font to the text field of its editor.
     */
    @Override
    public void updateUI() {
      super.updateUI();
      Font font = UIManager.getFont("TextField.font");
      if (font != null) {
        setFont(new FontUIResource(font));
      }
    }
  }

  /** The text field of the spinner. It paints the form of the text while it is empty. */
  private static final class Field extends JFormattedTextField {
    private static final long serialVersionUID = 1L;

    /** Keeps the text at the leading edge, also when a look and feel puts it at the end. */
    @Override
    public void setHorizontalAlignment(int alignment) {
      super.setHorizontalAlignment(SwingConstants.LEADING);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      TextFieldView.paintPlaceholder(this, graphics);
    }
  }

  /** The editor of the spinner. It has a {@link Field} with the {@link IsoFormatter}. */
  private static final class Editor extends JSpinner.DefaultEditor {
    private static final long serialVersionUID = 1L;

    Editor(JSpinner spinner, IsoModel model) {
      super(spinner);
      JFormattedTextField old = getTextField();
      old.removePropertyChangeListener(this);
      remove(old);
      Field field = new Field();
      field.setName("Spinner.formattedTextField");
      field.setFormatterFactory(new DefaultFormatterFactory(new IsoFormatter(model)));
      field.setValue(spinner.getValue());
      field.setColumns(Mode.DATE_TIME.hint().length() + 3);
      field.setInheritsPopupMenu(true);
      field.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
      field.addPropertyChangeListener(this);
      // The arrow keys operate the spinner, and not the field.
      ActionMap actions = field.getActionMap();
      Action disabled =
          new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
              // The action of the spinner runs.
            }
          };
      disabled.setEnabled(false);
      actions.put("increment", disabled);
      actions.put("decrement", disabled);
      field.addCaretListener(event -> {
        if (field.isFocusOwner()) {
          model.setUnit(unitAt(model.mode(), event.getDot(), model.current()));
        }
      });
      add(field);
    }
  }

  /**
   * Gets the part of the text of the field at a position of the caret.
   *
   * @param mode the mode of the field
   * @param position the position of the caret
   * @param value the value of the field, or an empty result
   * @return the unit of the part
   */
  static ChronoUnit unitAt(Mode mode, int position, Optional<IsoValue> value) {
    int time = position;
    if (mode != Mode.TIME) {
      if (position <= 4) {
        return ChronoUnit.YEARS;
      }
      if (position <= 7) {
        return ChronoUnit.MONTHS;
      }
      if (position <= 10 || mode == Mode.DATE) {
        return ChronoUnit.DAYS;
      }
      time = position - 11;
    }
    if (time <= 2) {
      return ChronoUnit.HOURS;
    }
    if (time <= 5 || value.map(v -> !v.seconds()).orElse(true)) {
      return ChronoUnit.MINUTES;
    }
    return ChronoUnit.SECONDS;
  }

  private final LabeledControl panel = new LabeledControl();
  private final IsoModel model;
  private final JSpinner spinner;
  private final JFormattedTextField field;
  private final ValueBinding<String> value;
  private final ChangeListener listener = event -> changed();

  /**
   * Makes the view.
   *
   * @param context the context of the view
   */
  DateTimeInputView(ViewContext context) {
    this(Clock.systemDefaultZone());
  }

  /**
   * Makes the view with a clock, for the tests.
   *
   * @param clock the clock that gives the start of the arrows of an empty field
   */
  DateTimeInputView(Clock clock) {
    model = new IsoModel(clock);
    spinner = new Spinner(model);
    spinner.setEditor(new Editor(spinner, model));
    field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
    field.putClientProperty(TextFieldView.PLACEHOLDER_PROPERTY, model.mode().hint());
    value = new ValueBinding<>(() -> (String) spinner.getValue(), spinner::setValue);
    panel.setControl(spinner, field);
    spinner.addChangeListener(listener);
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public JComponent accessibleComponent() {
    return field;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    panel.setLabel(Coercion.toText(props.value("label")));
    Mode mode =
        Mode.of(
            Coercion.toBoolean(props.value("enableDate")),
            Coercion.toBoolean(props.value("enableTime")));
    if (model.configure(mode, limit(props.value("min"), mode), limit(props.value("max"), mode))) {
      field.putClientProperty(TextFieldView.PLACEHOLDER_PROPERTY, mode.hint());
      // The field writes its value again in the form of the mode.
      field.setValue(spinner.getValue());
      field.repaint();
    }
    Object text = props.value("value");
    value.update(text == null ? "" : Coercion.toText(text), props.writableBinding("value"));
    panel.showChecks(props.validationResults());
    panel.sync();
    panel.revalidate();
    panel.repaint();
  }

  /** Gets the label, as the name of the field for assistive tools. */
  @Override
  public Optional<String> inferredAccessibleName() {
    return panel.labelText();
  }

  @Override
  public void dispose() {
    spinner.removeChangeListener(listener);
    value.dispose();
    panel.clearToolTip();
  }

  /** Reads a limit of the field in the form of a mode. */
  private static IsoValue limit(Object value, Mode mode) {
    return value instanceof String text
        ? IsoValue.parse(text).flatMap(parsed -> parsed.in(mode)).orElse(null)
        : null;
  }

  /** Writes the value of the spinner after a change of the user. */
  private void changed() {
    value.edited(spinner.getValue());
  }
}
