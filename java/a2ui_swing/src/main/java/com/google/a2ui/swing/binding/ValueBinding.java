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

package com.google.a2ui.swing.binding;

import com.google.a2ui.core.exceptions.A2uiDataError;
import com.google.a2ui.core.resolution.WritableBinding;
import java.awt.EventQueue;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Connects a Swing control to a property in the two directions, for a control that is not a text
 * component. Examples are a check box, a group of options and a slider. For a text component, use
 * {@link TextBinding}.
 *
 * <p>The binding compares values of the type {@code T}: the form of the value that the control
 * shows. For example, a check box shows a {@code Boolean}, and a slider shows the {@code Integer}
 * position of its knob. The view converts the value of the property to this form before it calls
 * {@link #update(Object, Optional)}.
 *
 * <ul>
 *   <li>When new properties arrive, the binding sets the control only if the control shows a
 *       different value. Thus the properties that come back after a change of the user do not touch
 *       the control.
 *   <li>The listener of the control calls {@link #edited(Object)} with the value for the data
 *       model. The binding writes it through the {@link WritableBinding} of the property. It writes
 *       nothing while it sets the control itself. Thus a change from the data model does not write
 *       the data model again.
 *   <li>A property with no data path has no {@code WritableBinding}. Then a change of the user
 *       changes only the control, until the next update.
 * </ul>
 *
 * <p>A view makes one binding for its control, calls {@link #update(Object, Optional)} in each
 * update and calls {@link #dispose()} in its disposal. Use the binding only on the event dispatch
 * thread.
 *
 * <pre>{@code
 * JCheckBox box = new JCheckBox();
 * ValueBinding<Boolean> value = new ValueBinding<>(box::isSelected, box::setSelected);
 * box.addItemListener(event -> value.edited(box.isSelected()));
 * return SwingComponentView.of(
 *     box,
 *     (node, props) ->
 *         value.update(Coercion.toBoolean(props.value("value")), props.writableBinding("value")),
 *     value::dispose);
 * }</pre>
 *
 * @param <T> the type of the value that the control shows
 */
public final class ValueBinding<T> {
  private final Supplier<? extends T> shown;
  private final Consumer<? super T> show;

  /** The binding of the property, or null if code cannot write the property. */
  private WritableBinding target;

  /** The value of the property in the newest properties, in the form of the control. */
  private T value;

  /** True while this object sets the control. Then a change is not from the user. */
  private boolean showing;

  /** True while this object writes a change of the user. */
  private boolean writing;

  private boolean showLater;
  private boolean disposed;

  /**
   * Makes a binding.
   *
   * @param shown reads the value that the control shows
   * @param show sets the value of the control
   */
  public ValueBinding(Supplier<? extends T> shown, Consumer<? super T> show) {
    this.shown = Objects.requireNonNull(shown, "shown");
    this.show = Objects.requireNonNull(show, "show");
  }

  /**
   * Shows a value and keeps a binding for the subsequent changes of the user.
   *
   * <p>A value that arrives while the binding writes a change of the user does not go to the
   * control immediately, because the control is in its own listener. If it is different from the
   * value of the control, the control gets it after the listener returns.
   *
   * @param value the value of the property, in the form of the control. It can be null if the
   *     control accepts null.
   * @param binding the binding that gets the changes, or an empty value if code cannot write the
   *     property
   */
  public void update(T value, Optional<WritableBinding> binding) {
    Objects.requireNonNull(binding, "binding");
    this.value = value;
    this.target = binding.orElse(null);
    if (!writing) {
      show();
    } else if (!Objects.equals(value, shown.get()) && !showLater) {
      showLater = true;
      EventQueue.invokeLater(() -> {
        showLater = false;
        if (!disposed) {
          show();
        }
      });
    }
  }

  /**
   * Writes a change of the user to the data model. The listener of the control calls this method.
   * The method does nothing while the binding sets the control, after the disposal, and for a
   * property with no data path.
   *
   * @param written the value for the data model, for example a {@code Boolean} or a {@code List} of
   *     strings
   * @throws A2uiDataError if the data model cannot write the value
   */
  public void edited(Object written) {
    if (showing || disposed || target == null) {
      return;
    }
    writing = true;
    try {
      target.set(written);
    } finally {
      writing = false;
    }
  }

  /**
   * Tells if the binding sets the control at this time. A listener can use it to ignore a change
   * that is not from the user.
   *
   * @return true while the binding sets the control
   */
  public boolean isShowing() {
    return showing;
  }

  /**
   * Tells if a change of the user can go to the data model.
   *
   * @return true if the property has a data path and the binding is not disposed
   */
  public boolean isWritable() {
    return target != null && !disposed;
  }

  /** Stops the binding. Subsequent changes write nothing, and updates do not touch the control. */
  public void dispose() {
    disposed = true;
    target = null;
  }

  private void show() {
    if (disposed || Objects.equals(value, shown.get())) {
      return;
    }
    showing = true;
    try {
      show.accept(value);
    } finally {
      showing = false;
    }
  }
}
