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
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleValue;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeListener;

/**
 * The view of the {@code Slider} component of the basic catalog: a label, a {@code JSlider} of the
 * look and feel with the current value next to it, and the messages of the checks.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code label}</td><td>The text above the slider. It is also the accessible name of the
 *       slider, unless the agent gave a label. An empty label uses no space.</td></tr>
 *   <tr><td>{@code min}, {@code max}</td><td>The range of the slider. The default of {@code min}
 *       is 0. The catalog requires {@code max}. If it is absent, it is 100. A {@code max} that is
 *       less than {@code min} is {@code min}.</td></tr>
 *   <tr><td>{@code steps}</td><td>The number of equal divisions of the range. The slider then
 *       moves only to the values {@code min + k * (max - min) / steps}, and it shows a tick mark
 *       for each value if there are not more than {@value #MAX_TICKS} steps.</td></tr>
 *   <tr><td>{@code value}</td><td>The number that the slider shows. The view writes each move of
 *       the user to the data model immediately, also while the user drags the knob. When the data
 *       model gives back the number that the slider shows, the view does not touch the
 *       slider.</td></tr>
 *   <tr><td>{@code checks}</td><td>The messages of the failed checks show below the slider, as in
 *       the view of {@code TextField}.</td></tr>
 * </table>
 *
 * <p><b>Positions.</b> A {@code JSlider} has integer positions. The view maps them to the range:
 *
 * <ul>
 *   <li>With {@code steps}, there is one position for each step and one for {@code min}.
 *   <li>With no {@code steps}, a range whose size is a whole number from 2 to {@value
 *       #MAX_WHOLE_RANGE} has one position for each whole number. Thus a range from 0 to 100 moves
 *       in steps of 1.
 *   <li>Each other range has {@value #RESOLUTION} positions: one percent of the range for each
 *       position. Thus a range from 0 to 1 moves in steps of 0.01.
 * </ul>
 *
 * <p><b>Numbers.</b> The view writes a whole number as a {@code Long}, if it is less than 2^53 in
 * magnitude, and each other number as a {@code Double}. Thus a slider from 0 to 100 writes {@code
 * 42}, and the JSON of the data model has no decimal point. The view rounds a number to 12
 * significant digits, thus a step of 0.1 writes 0.3 and not 0.30000000000000004. A value of the
 * data model that is not on a step shows at the nearest position. The text next to the slider shows
 * the value of the data model as it is. A value that is not a number shows the slider at {@code
 * min}, and the view writes nothing.
 *
 * <p>A {@code value} with no data path is a number that only the slider has. The user can change
 * it, and the view writes nothing, until the next update.
 *
 * <p><b>Accessibility.</b> The accessible value of the slider is the number, and not the integer
 * position. An assistive tool that sets the accessible value moves the slider to the nearest
 * position, and the view writes the number of that position.
 *
 * <p>The Swing component of the view is a {@link LabeledControl}. The accessibility attributes go
 * to the {@code JSlider}.
 */
final class SliderView implements SwingComponentView {
  /** The number of positions of a range with no steps that is not a small whole number. */
  static final int RESOLUTION = 100;

  /** The largest whole range that has one position for each whole number. */
  static final int MAX_WHOLE_RANGE = 1000;

  /** The most steps that show tick marks. */
  static final int MAX_TICKS = 20;

  /** A {@code double} holds each whole number below this value exactly. */
  private static final double EXACT_LIMIT = 9.007199254740992E15;

  /** The space between the slider and the text of the value, in pixels. */
  private static final int VALUE_GAP = 8;

  /**
   * The map from the integer positions of the slider to the range of the component.
   *
   * @param min the value at position 0
   * @param max the value at the last position
   * @param positions the last position. It is 0 for a range of size 0.
   */
  record Scale(double min, double max, int positions) {
    /** Makes the scale for the properties of a component. */
    static Scale of(Object min, Object max, Object steps) {
      double low = Coercion.parseNumber(min).orElse(0);
      double high = Math.max(low, Coercion.parseNumber(max).orElse(100));
      double range = high - low;
      if (!Double.isFinite(range) || range == 0) {
        return new Scale(low, Double.isFinite(range) ? high : low, 0);
      }
      OptionalDouble divisions = Coercion.parseNumber(steps);
      if (steps instanceof Number && divisions.isPresent() && divisions.getAsDouble() >= 1) {
        return new Scale(low, high, (int) Math.min(Integer.MAX_VALUE, divisions.getAsDouble()));
      }
      if (range >= 2 && range <= MAX_WHOLE_RANGE && range == Math.rint(range)) {
        return new Scale(low, high, (int) range);
      }
      return new Scale(low, high, RESOLUTION);
    }

    /** Gets the number of a position, rounded to 12 significant digits. */
    Number valueAt(int position) {
      if (positions == 0 || position <= 0) {
        return plain(min);
      }
      if (position >= positions) {
        return plain(max);
      }
      double value = min + (max - min) * position / positions;
      return plain(new BigDecimal(value).round(new MathContext(12)).doubleValue());
    }

    /** Gets the nearest position of a number. */
    int positionOf(double value) {
      if (positions == 0 || !Double.isFinite(value)) {
        return 0;
      }
      double share = (value - min) / (max - min);
      return (int) Math.max(0, Math.min(positions, Math.round(share * positions)));
    }

    /** Gets a number as a {@code Long} if it is whole and exact, or as a {@code Double}. */
    static Number plain(double value) {
      return value == Math.rint(value) && Math.abs(value) < EXACT_LIMIT
          ? (Number) (long) value
          : (Number) value;
    }
  }

  private final LabeledControl panel = new LabeledControl();
  private final FlexPanel row =
      new FlexPanel(
          new FlexLayout(
              FlexLayout.Axis.HORIZONTAL,
              FlexLayout.Justify.START,
              FlexLayout.Align.CENTER,
              VALUE_GAP));
  private final Track slider = new Track();
  private final ValueText text = new ValueText();
  private final ValueBinding<Integer> position =
      new ValueBinding<>(slider::getValue, slider::setValue);

  private Scale scale = new Scale(0, 100, 100);

  /** True while the view changes the range of the slider. Then a move is not from the user. */
  private boolean updating;

  /** The position of the newest properties, or of the newest move of the user. */
  private int current;

  private final ChangeListener listener = event -> moved();

  /**
   * Makes the view.
   *
   * @param context the context of the view
   */
  SliderView(ViewContext context) {
    row.add(slider, 1.0);
    row.add(text);
    panel.setControl(row, slider);
    slider.addChangeListener(listener);
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public JComponent accessibleComponent() {
    return slider;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    panel.setLabel(Coercion.toText(props.value("label")));
    Scale next = Scale.of(props.value("min"), props.value("max"), props.value("steps"));
    updating = true;
    try {
      if (!next.equals(scale)) {
        scale = next;
        slider.setMinimum(0);
        slider.setMaximum(next.positions());
        boolean ticks = props.value("steps") != null && next.positions() <= MAX_TICKS;
        slider.setMajorTickSpacing(ticks ? 1 : 0);
        slider.setPaintTicks(ticks);
      }
    } finally {
      updating = false;
    }
    Object value = props.value("value");
    OptionalDouble number = value instanceof Boolean ? OptionalDouble.empty() : number(value);
    current = number.isPresent() ? scale.positionOf(number.getAsDouble()) : 0;
    position.update(current, props.writableBinding("value"));
    text.show(
        number.isPresent()
            ? Coercion.toText(Scale.plain(number.getAsDouble()))
            : valueText(current),
        List.of(valueText(0), valueText(scale.positions())));
    panel.showChecks(props.validationResults());
    panel.sync();
    panel.revalidate();
    panel.repaint();
  }

  /** Gets the label, as the name of the slider for assistive tools. */
  @Override
  public Optional<String> inferredAccessibleName() {
    return panel.labelText();
  }

  @Override
  public void dispose() {
    slider.removeChangeListener(listener);
    position.dispose();
    panel.clearToolTip();
  }

  /** Reads a value of the data model as a number. A string that is not a number is absent. */
  private static OptionalDouble number(Object value) {
    return value instanceof String text && text.isBlank()
        ? OptionalDouble.empty()
        : Coercion.parseNumber(value);
  }

  private String valueText(int at) {
    return Coercion.toText(scale.valueAt(at));
  }

  /** Writes the number of a move of the user. */
  private void moved() {
    int at = slider.getValue();
    if (updating || position.isShowing() || at == current) {
      return;
    }
    current = at;
    text.show(valueText(at), List.of(valueText(0), valueText(scale.positions())));
    position.edited(scale.valueAt(at));
  }

  /** The slider of the view. Its accessible value is the number of the component. */
  private final class Track extends JSlider {
    private static final long serialVersionUID = 1L;

    Track() {
      super(SwingConstants.HORIZONTAL, 0, 100, 0);
      setOpaque(false);
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleTrack();
      }
      return accessibleContext;
    }

    /** The accessible context of the slider. Its value is a number of the range. */
    private final class AccessibleTrack extends AccessibleJSlider {
      private static final long serialVersionUID = 1L;

      @Override
      public AccessibleValue getAccessibleValue() {
        return this;
      }

      @Override
      public Number getCurrentAccessibleValue() {
        return scale.valueAt(getValue());
      }

      @Override
      public boolean setCurrentAccessibleValue(Number value) {
        if (value == null) {
          return false;
        }
        setValue(scale.positionOf(value.doubleValue()));
        return true;
      }

      @Override
      public Number getMinimumAccessibleValue() {
        return scale.valueAt(0);
      }

      @Override
      public Number getMaximumAccessibleValue() {
        return scale.valueAt(scale.positions());
      }
    }
  }

  /**
   * The text of the value next to the slider. Its width is the width of the widest value of the
   * range. Thus the slider does not change its size while the user drags it.
   */
  private static final class ValueText extends JLabel {
    private static final long serialVersionUID = 1L;

    /** The texts that give the width. */
    private transient List<String> samples = List.of();

    ValueText() {
      setHorizontalAlignment(SwingConstants.TRAILING);
    }

    void show(String value, List<String> widest) {
      if (!value.equals(getText())) {
        setText(value);
      }
      if (!widest.equals(samples)) {
        samples = List.copyOf(widest);
        revalidate();
      }
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      if (isPreferredSizeSet() || getFont() == null) {
        return size;
      }
      FontMetrics metrics = getFontMetrics(getFont());
      int text = metrics.stringWidth(getText() == null ? "" : getText());
      int widest = text;
      for (String sample : samples) {
        widest = Math.max(widest, metrics.stringWidth(sample));
      }
      return new Dimension(size.width - text + widest, size.height);
    }

    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }
  }
}
