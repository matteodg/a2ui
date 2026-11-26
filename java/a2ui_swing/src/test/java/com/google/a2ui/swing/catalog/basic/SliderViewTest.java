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

import com.google.a2ui.swing.catalog.basic.SliderView.Scale;
import com.google.a2ui.swing.testing.Edt;
import java.awt.Component;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.accessibility.AccessibleValue;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SliderViewTest {
  private final BasicFixture fixture = new BasicFixture();

  /** The writes to the path /level. */
  private final List<Object> writes = new ArrayList<>();

  private static Object slider(Object... properties) {
    List<Object> all = new ArrayList<>();
    if (!List.of(properties).contains("label")) {
      all.addAll(List.of("label", "Level"));
    }
    if (!List.of(properties).contains("max")) {
      all.addAll(List.of("max", 100));
    }
    if (!List.of(properties).contains("value")) {
      all.addAll(List.of("value", path("/level")));
    }
    all.addAll(List.of(properties));
    return component("root", "Slider", all.toArray());
  }

  /** Makes a surface whose root is a slider from 0 to 100 with the value of /level. */
  private void show(Map<String, Object> data, Object... properties) {
    fixture.create(data, slider(properties));
    fixture.model().dataModel().subscribe("/level", writes::add);
  }

  private JSlider control() {
    return (JSlider) fixture.viewOf("root").accessibleComponent();
  }

  private JComponent panel() {
    return fixture.componentOf("root");
  }

  private JLabel valueText() {
    return (JLabel) control().getParent().getComponent(1);
  }

  private AccessibleValue accessibleValue() {
    return control().getAccessibleContext().getAccessibleValue();
  }

  private Rectangle boundsOf(Component component) {
    return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), panel());
  }

  @Test
  void theViewShowsALabelAboveASliderWithTheValueNextToIt() {
    Edt.run(() -> {
      show(Map.of("level", 10L));

      JSlider slider = control();
      assertEquals(0, slider.getMinimum());
      assertEquals(100, slider.getMaximum());
      assertEquals(10, slider.getValue());
      assertFalse(slider.getPaintTicks());
      assertEquals("10", valueText().getText());
      JLabel label = (JLabel) panel().getComponent(0);
      assertEquals("Level", label.getText());
      assertSame(slider, label.getLabelFor());
      assertEquals(10L, accessibleValue().getCurrentAccessibleValue());
      assertEquals(0L, accessibleValue().getMinimumAccessibleValue());
      assertEquals(100L, accessibleValue().getMaximumAccessibleValue());
      assertEquals("Level", slider.getAccessibleContext().getAccessibleName());
      // The slider fills the width, and the value is at its end.
      Rectangle track = boundsOf(slider);
      Rectangle text = boundsOf(valueText());
      assertEquals(8, track.x);
      assertEquals(BasicFixture.WIDTH - 8, text.x + text.width);
      assertTrue(track.x + track.width < text.x);
      assertTrue(label.getY() + label.getHeight() <= track.y);
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void aMoveOfTheUserWritesAWholeNumberWhileTheUserDrags() {
    Edt.run(() -> {
      fixture.create(
          Map.of("level", 10L),
          component("root", "Column", "children", List.of("root_slider", "preview")),
          component("root_slider", "Slider", "label", "Level", "max", 100, "value", path("/level")),
          component("preview", "Text", "text", path("/level")));
      JSlider slider = (JSlider) fixture.viewOf("root_slider").accessibleComponent();
      List<Integer> changes = new ArrayList<>();
      slider.addChangeListener(event -> changes.add(slider.getValue()));

      slider.setValueIsAdjusting(true);
      slider.setValue(30);
      assertEquals(30L, fixture.data("/level"));
      slider.setValue(42);
      slider.setValueIsAdjusting(false);

      assertEquals(42L, fixture.data("/level"));
      assertEquals("42", VisibleText.of(fixture.componentOf("preview")));
      // The start and the end of the drag are changes too. The value came back from the data
      // model, and the view did not move the slider again.
      assertEquals(List.of(10, 30, 42, 42), changes);
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void aNewValueOfTheDataModelMovesTheSliderAndWritesNothing() {
    Edt.run(() -> {
      show(Map.of("level", 10L, "other", 1L));
      JSlider slider = control();
      List<Integer> changes = new ArrayList<>();
      slider.addChangeListener(event -> changes.add(slider.getValue()));

      fixture.setData("/level", 75L);

      assertEquals(75, slider.getValue());
      assertEquals("75", valueText().getText());
      assertEquals(List.of(75L), writes);

      fixture.setData("/level", 75.0);
      fixture.setData("/other", 2L);
      fixture.update(slider("label", "Volume"));

      assertSame(slider, control());
      assertEquals(List.of(75), changes);
      assertEquals(List.of(75L, 75.0), writes);
    });
  }

  @Test
  void stepsGiveEqualDivisionsAndTickMarks() {
    Edt.run(() -> {
      show(Map.of("level", 0.5), "min", 0, "max", 1, "steps", 4);
      JSlider slider = control();

      assertEquals(4, slider.getMaximum());
      assertEquals(2, slider.getValue());
      assertTrue(slider.getPaintTicks());
      assertEquals(1, slider.getMajorTickSpacing());
      assertEquals("0.5", valueText().getText());

      slider.setValue(3);
      assertEquals(0.75, fixture.data("/level"));
      slider.setValue(4);
      assertEquals(1L, fixture.data("/level"));
      slider.setValue(0);
      assertEquals(0L, fixture.data("/level"));
    });
  }

  @Test
  void aRangeWithNoStepsThatIsNotWholeHasAPositionForEachPercent() {
    Edt.run(() -> {
      show(Map.of("level", 0.25), "min", 0, "max", 1);
      JSlider slider = control();

      assertEquals(SliderView.RESOLUTION, slider.getMaximum());
      assertEquals(25, slider.getValue());
      assertFalse(slider.getPaintTicks());

      slider.setValue(30);

      // The view writes 0.3, and not the sum of the floating point steps.
      assertEquals(0.3, fixture.data("/level"));
      assertEquals("0.3", valueText().getText());
    });
  }

  @Test
  void theScaleMapsThePositionsToTheRange() {
    assertEquals(new Scale(0, 100, 100), Scale.of(null, null, null));
    assertEquals(new Scale(5, 5, 0), Scale.of(5L, 1L, null));
    assertEquals(new Scale(-10, 10, 20), Scale.of(-10L, 10L, null));
    assertEquals(new Scale(0, 1000, 1000), Scale.of(0L, 1000L, null));
    assertEquals(new Scale(0, 1001, 100), Scale.of(0L, 1001L, null));
    assertEquals(new Scale(0, 2.5, 100), Scale.of(0L, 2.5, null));
    assertEquals(new Scale(0, 10, 3), Scale.of(0L, 10L, 3L));
    assertEquals(new Scale(0, 10, 10), Scale.of(0L, 10L, "3"));
    assertEquals(new Scale(0, 10, 10), Scale.of(0L, 10L, 0L));
    assertEquals(new Scale(0, 10, 10), Scale.of("0", "10", null));

    Scale thirds = new Scale(0, 10, 3);
    assertEquals(0L, thirds.valueAt(0));
    assertEquals(3.33333333333, thirds.valueAt(1));
    assertEquals(10L, thirds.valueAt(3));
    assertEquals(1, thirds.positionOf(3.0));
    assertEquals(3, thirds.positionOf(99));
    assertEquals(0, thirds.positionOf(-5));
    assertEquals(0, thirds.positionOf(Double.NaN));
    assertEquals(5L, new Scale(5, 5, 0).valueAt(0));
    assertEquals(5L, new Scale(5, 5, 0).valueAt(3));
    assertEquals(1.0E16, Scale.plain(1.0E16));
    assertEquals(-3L, Scale.plain(-3.0));
  }

  @Test
  void aValueThatIsNotOnAStepShowsAtTheNearestPositionAndIsNotWritten() {
    Edt.run(() -> {
      show(Map.of("level", 10.3));

      assertEquals(10, control().getValue());
      assertEquals("10.3", valueText().getText());
      assertEquals(List.of(), writes);

      fixture.setData("/level", 150L);
      assertEquals(100, control().getValue());
      assertEquals("150", valueText().getText());
      assertEquals(List.of(150L), writes);
    });
  }

  @Test
  void aValueThatIsNotANumberShowsTheMinimumAndIsNotWritten() {
    Edt.run(() -> {
      show(Map.of("level", "loud"), "min", 20);

      assertEquals(0, control().getValue());
      assertEquals("20", valueText().getText());

      fixture.setData("/level", null);
      assertEquals(0, control().getValue());
      fixture.setData("/level", "");
      fixture.setData("/level", true);
      assertEquals(0, control().getValue());
      fixture.setData("/level", "40");
      assertEquals(20, control().getValue());
      assertEquals(4, writes.size());
      assertEquals(List.of(), fixture.errors);
    });
  }

  @Test
  void aValueWithNoBindingChangesOnlyTheSlider() {
    Edt.run(() -> {
      show(Map.of("level", 1L), "value", 50);

      control().setValue(60);

      assertEquals("60", valueText().getText());
      assertEquals(1L, fixture.data("/level"));
      assertEquals(List.of(), fixture.errors);

      fixture.update(slider("value", 50, "label", "Other"));
      assertEquals(50, control().getValue());
    });
  }

  @Test
  void anAssistiveToolSetsTheNumberOfTheSlider() {
    Edt.run(() -> {
      show(Map.of("level", 0L), "min", 0, "max", 1, "steps", 10);

      assertTrue(accessibleValue().setCurrentAccessibleValue(0.68));

      assertEquals(7, control().getValue());
      assertEquals(0.7, fixture.data("/level"));
      assertEquals(0.7, accessibleValue().getCurrentAccessibleValue());
      assertFalse(accessibleValue().setCurrentAccessibleValue(null));
    });
  }

  @Test
  void aChangeOfTheRangeMovesTheSliderAndWritesNothing() {
    Edt.run(() -> {
      show(Map.of("level", 50L));

      fixture.update(slider("max", 200));

      assertEquals(200, control().getMaximum());
      assertEquals(50, control().getValue());
      assertEquals(List.of(), writes);

      fixture.update(slider("min", 40, "max", 60, "steps", 2));
      assertEquals(2, control().getMaximum());
      assertEquals(1, control().getValue());
      assertEquals(List.of(), writes);
    });
  }

  @Test
  void theMessagesOfTheChecksShowBelowTheSlider() {
    Edt.run(() -> {
      show(
          Map.of("level", 10L, "ok", false),
          "checks",
          List.of(Map.of("condition", path("/ok"), "message", "Select at least 20.")));

      JComponent lines = (JComponent) panel().getComponent(2);
      assertTrue(lines.isVisible());
      assertEquals("Select at least 20.", control().getToolTipText());
      assertTrue(control().isEnabled());
      assertTrue(boundsOf(control()).y + boundsOf(control()).height <= boundsOf(lines).y);

      fixture.setData("/ok", true);
      assertFalse(lines.isVisible());
      assertNull(control().getToolTipText());
    });
  }

  @Test
  void theSliderFitsANarrowView() {
    Edt.run(() -> {
      show(Map.of("level", 10L), "min", -1000, "max", 1000);

      fixture.resize(200, 300);

      Rectangle track = boundsOf(control());
      Rectangle text = boundsOf(valueText());
      assertTrue(track.width > 40, track.toString());
      assertEquals(200 - 8, text.x + text.width);
      // The text has the width of the widest value. Thus a move does not change the layout.
      assertTrue(
          text.width >= valueText().getFontMetrics(valueText().getFont()).stringWidth("-1000"));
      control().setValue(control().getMaximum());
      fixture.view.validate();
      assertEquals(track, boundsOf(control()));
    });
  }

  @Test
  void theAccessibleNameOfTheAgentWins() {
    Edt.run(() -> {
      show(Map.of("level", 10L), "@accessibility", object("label", "Volume"));

      assertEquals("Volume", control().getAccessibleContext().getAccessibleName());
    });
  }

  @Test
  void theDisposalRemovesTheListenerOfTheSlider() {
    Edt.run(() -> {
      show(Map.of("level", 10L));
      JSlider slider = control();
      int listeners = slider.getChangeListeners().length;

      fixture.view.removeNotify();

      assertEquals(listeners - 1, slider.getChangeListeners().length);
      slider.setValue(90);
      assertEquals(10L, fixture.data("/level"));
      assertEquals(0, fixture.view.activeSubscriptionCount());
    });
  }
}
