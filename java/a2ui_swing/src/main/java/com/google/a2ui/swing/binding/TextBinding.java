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

import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.exceptions.A2uiDataError;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.core.resolution.WritableBinding;
import java.awt.EventQueue;
import java.util.Objects;
import java.util.Optional;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/**
 * Connects a Swing text component to a text property in the two directions.
 *
 * <p>The binding writes each edit of the user to the data model, through the {@link
 * WritableBinding} of the property. When new properties arrive, the binding sets the text of the
 * component only if the text is different. Thus the properties that come back after an edit do not
 * move the caret, and an edit does not cause a second write.
 *
 * <p>A view makes one binding for its text component, calls {@link #update(NodeProps, String)} in
 * each update and calls {@link #dispose()} in its disposal. Do not replace the document of the
 * component. Use the binding only on the event dispatch thread.
 *
 * <pre>{@code
 * JTextField field = new JTextField();
 * TextBinding value = new TextBinding(field);
 * return SwingComponentView.of(field, (node, props) -> value.update(props, "value"), value::dispose);
 * }</pre>
 */
public final class TextBinding {
  private final JTextComponent component;
  private final DocumentListener listener =
      new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent event) {
          edited();
        }

        @Override
        public void removeUpdate(DocumentEvent event) {
          edited();
        }

        @Override
        public void changedUpdate(DocumentEvent event) {
          // Only the attributes of the text changed.
        }
      };

  /** The binding of the property, or null if code cannot write the property. */
  private WritableBinding target;

  /** The text of the property in the newest properties. */
  private String text = "";

  /** True while this object sets the text of the component. Then an edit is not from the user. */
  private boolean showing;

  /** True while this object writes an edit. Swing does not permit a text change at that time. */
  private boolean writing;

  private boolean showLater;
  private boolean disposed;

  /**
   * Makes a binding and starts to listen to the edits of the component.
   *
   * @param component the text component, for example a {@code JTextField}
   */
  public TextBinding(JTextComponent component) {
    this.component = Objects.requireNonNull(component, "component");
    component.getDocument().addDocumentListener(listener);
  }

  /**
   * Shows the value of a property and keeps its binding for the subsequent edits. The text of a
   * value that is not a string is the result of {@link Coercion#toText(Object)}.
   *
   * @param props the current properties of the node
   * @param name the name of the property
   */
  public void update(NodeProps<?> props, String name) {
    Objects.requireNonNull(props, "props");
    update(Coercion.toText(props.value(name)), props.writableBinding(name));
  }

  /**
   * Shows a text and keeps a binding for the subsequent edits.
   *
   * @param text the text of the property
   * @param binding the binding that gets the edits, or an empty value if code cannot write the
   *     property. Then an edit changes only the component.
   */
  public void update(String text, Optional<WritableBinding> binding) {
    this.text = Objects.requireNonNull(text, "text");
    this.target = binding.orElse(null);
    if (!writing) {
      show();
    } else if (!text.equals(component.getText()) && !showLater) {
      // The data gave back a different text during the write of an edit. The document is locked
      // until its listeners return. Thus the component gets the text after that.
      showLater = true;
      EventQueue.invokeLater(() -> {
        showLater = false;
        if (!disposed) {
          show();
        }
      });
    }
  }

  /** Stops to listen to the component. Subsequent edits write nothing. */
  public void dispose() {
    disposed = true;
    target = null;
    component.getDocument().removeDocumentListener(listener);
  }

  private void show() {
    if (text.equals(component.getText())) {
      return;
    }
    showing = true;
    try {
      component.setText(text);
    } finally {
      showing = false;
    }
  }

  /**
   * Writes the text of the component after an edit of the user.
   *
   * @throws A2uiDataError if the data model cannot write the value
   */
  private void edited() {
    if (showing || disposed || target == null) {
      return;
    }
    writing = true;
    try {
      target.set(component.getText());
    } finally {
      writing = false;
    }
  }
}
