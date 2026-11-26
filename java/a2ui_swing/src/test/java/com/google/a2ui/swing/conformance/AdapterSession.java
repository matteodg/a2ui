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

package com.google.a2ui.swing.conformance;

import static com.google.a2ui.swing.conformance.JsonValues.asList;
import static com.google.a2ui.swing.conformance.JsonValues.asObject;
import static com.google.a2ui.swing.conformance.JsonValues.asString;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.core.state.RendererAction;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.core.validation.ValidationConfig;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.basic.SwingBasicCatalog;
import com.google.a2ui.swing.surface.A2uiAccessibility;
import com.google.a2ui.swing.surface.A2uiFallbacks;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.A2uiSurface;
import com.google.a2ui.swing.surface.A2uiSwing;
import com.google.a2ui.swing.surface.MountedNode;
import com.google.a2ui.swing.testing.Headless;
import com.google.a2ui.swing.testing.TestImageLoader;
import com.google.a2ui.swing.testing.TestModalPresenter;
import java.awt.Component;
import java.awt.Container;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 * One message processor and one mounted surface for a case of the adapter suites. The session
 * records what the surface reports, and it reads the mounted Swing components for the handlers.
 *
 * <p>The basic catalog of the session is {@code SwingBasicCatalog} with a stub for {@code openUrl}
 * and with the test function {@code incrementCounter}. The modal presenter of the session keeps the
 * content of a modal in a panel, because a case has no window for a dialog. A custom component of a
 * case gets a small view: a label that shows the values of its properties.
 */
final class AdapterSession {
  /** The catalog file of the cases. The session uses the catalog of the SDK for it. */
  static final String BASIC_CATALOG_PATH = "catalogs/basic/v1/catalog.json";

  /** The key of the client property of the view of a custom component that has its properties. */
  private static final String PROPS_PROPERTY = "a2ui.conformance.props";

  private static final int WIDTH = 480;
  private static final int HEIGHT = 360;

  private final String location;

  /** The messages for the agent of the current step. */
  final List<Map<String, Object>> outbound = new ArrayList<>();

  /** The actions that the surfaces reported in the current step. */
  final List<RendererAction> actions = new ArrayList<>();

  /** The errors that the surfaces reported in the current step. */
  final List<A2uiError> errors = new ArrayList<>();

  /** The URLs that the function {@code openUrl} opened. */
  final List<URI> opened = new ArrayList<>();

  /** The modals that the views of the surface opened. A case has no window for a dialog. */
  final TestModalPresenter modals = new TestModalPresenter();

  /** The image loader of the session. It gives a pattern image for each URL, with no network. */
  final TestImageLoader images = TestImageLoader.patterns();

  /** The services of the catalog and of the surface. */
  private final A2uiServices services =
      A2uiServices.DEFAULT
          .withUrlOpener(opened::add)
          .withModalPresenter(modals)
          .withImageLoader(images);

  final MessageProcessor<SwingComponentImplementation> processor;

  /** True while a step acts as a gesture of the user. */
  boolean gesture;

  /** The first surface that the processor made, or null. */
  SurfaceModel<SwingComponentImplementation> surface;

  /** The mounted surface component, or null. */
  A2uiSurface view;

  /** The ID of the last call that the renderer sent to the agent, or null. */
  private String lastCallId;

  /**
   * Makes a session.
   *
   * @param location the location of the case, for failure messages
   * @param inlineCatalogs the value of the key {@code catalogs} of the case, or null
   * @param config the validation configuration of the processor
   */
  AdapterSession(String location, Object inlineCatalogs, ValidationConfig config) {
    this.location = location;
    List<Catalog<SwingComponentImplementation, ?>> catalogs = new ArrayList<>();
    catalogs.add(basicCatalog());
    if (inlineCatalogs != null) {
      for (Object catalog : asList(inlineCatalogs, "catalogs in " + location)) {
        catalogs.add(inlineCatalog(asObject(catalog, "catalog in " + location)));
      }
    }
    processor = new MessageProcessor<>(catalogs, config, this::sent, true);
    A2uiSwing.install(processor);
    processor.rpc().setUserActivation(() -> gesture);
    processor
        .model()
        .onSurfaceCreated()
        .subscribe(created -> {
          if (surface == null) {
            surface = created;
          }
          created.onAction().subscribe(actions::add);
          created.onError().subscribe(errors::add);
        });
  }

  private Catalog<SwingComponentImplementation, FunctionImplementation> basicCatalog() {
    Catalog<SwingComponentImplementation, FunctionImplementation> basic =
        SwingBasicCatalog.create(Locale.US, services);
    List<FunctionImplementation> functions = new ArrayList<>(basic.functions().values());
    functions.add(
        new FunctionImplementation(
            "incrementCounter",
            Map.of("returnType", "void"),
            (args, context) -> {
              String path = (String) args.get("path");
              double step = args.get("step") == null ? 1 : Coercion.toNumber(args.get("step"));
              Object current = context.get(path);
              context.set(path, (current == null ? 0 : Coercion.toNumber(current)) + step);
              return null;
            }));
    return basic.withFunctions(functions);
  }

  private Catalog<SwingComponentImplementation, FunctionApi> inlineCatalog(
      Map<String, Object> fields) {
    KnownKeys.assertKnownKeys(
        fields,
        Set.of("catalogId", "protocolVersion", "components", "functions"),
        "catalog in " + location);
    List<SwingComponentImplementation> components = new ArrayList<>();
    if (fields.get("components") != null) {
      asObject(fields.get("components"), "components in " + location)
          .forEach((name, schema) ->
              components.add(
                  SwingComponentImplementation.of(
                      name, asObject(schema, name), context -> propertiesView())));
    }
    List<FunctionApi> functions = new ArrayList<>();
    if (fields.get("functions") != null) {
      asObject(fields.get("functions"), "functions in " + location)
          .forEach((name, schema) -> functions.add(new FunctionApi(name, asObject(schema, name))));
    }
    JsonValues.assertJsonEquals("1.0", fields.get("protocolVersion"), "protocolVersion of catalog");
    return new Catalog<>(
        asString(fields.get("catalogId"), "catalogId in " + location),
        components,
        functions,
        null,
        ProtocolVersion.V1_0);
  }

  /** Makes the view of a custom component: a label that shows the values of the properties. */
  private static SwingComponentView propertiesView() {
    JLabel label = new JLabel();
    return SwingComponentView.of(
        label,
        (node, props) -> {
          label.putClientProperty(PROPS_PROPERTY, props);
          List<String> values = new ArrayList<>();
          for (String name : props.asMap().keySet()) {
            if (props.binding(name).isPresent()) {
              values.add(Coercion.toText(props.value(name)));
            }
          }
          label.setText(String.join(" ", values));
        });
  }

  private void sent(Map<String, Object> message) {
    outbound.add(message);
    if (message.get("callAgentFunction") instanceof Map<?, ?> call
        && call.get("functionCallId") instanceof String id) {
      lastCallId = id;
    }
  }

  /** Forgets the records of the step before. */
  void startStep() {
    outbound.clear();
    actions.clear();
    errors.clear();
  }

  /** Processes messages, and mounts the surface if it is not mounted. */
  void process(Object messages) {
    processor.processMessages(resolveReferences(messages));
    if (view == null && surface != null && !surface.isDisposed()) {
      view = Headless.mount(new A2uiSurface(surface, A2uiFallbacks.NONE, services), WIDTH, HEIGHT);
    }
    settle();
  }

  /** Does the layout of the surface again, as a window does after a change. */
  void settle() {
    if (view != null) {
      view.validate();
    }
  }

  /** Replaces each {@code {$ref: lastCallId}} with the ID of the last call to the agent. */
  private Object resolveReferences(Object value) {
    if (value instanceof Map<?, ?> map) {
      if (map.size() == 1 && "lastCallId".equals(map.get("$ref"))) {
        if (lastCallId == null) {
          throw new AssertionError(location + ": the renderer sent no call to the agent.");
        }
        return lastCallId;
      }
      Map<String, Object> copy = new LinkedHashMap<>();
      map.forEach((key, item) -> copy.put(String.valueOf(key), resolveReferences(item)));
      return copy;
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>();
      list.forEach(item -> copy.add(resolveReferences(item)));
      return copy;
    }
    return value;
  }

  /** Gets the root of the tree that the surface shows. */
  MountedNode tree() {
    if (view == null) {
      throw new AssertionError(location + ": the surface is not mounted.");
    }
    return view.viewTree()
        .orElseThrow(() -> new AssertionError(location + ": the surface shows no root."));
  }

  /** Finds the position of a component in the tree. */
  MountedNode find(String componentId) {
    return tree()
        .find(componentId)
        .orElseThrow(() ->
            new AssertionError(
                location + ": the surface does not show the component '" + componentId + "'."));
  }

  /** Gets the Swing component that has the accessibility attributes of a position. */
  JComponent control(MountedNode node) {
    return node.view()
        .orElseThrow(() ->
            new AssertionError(location + ": '" + node.node().componentId() + "' has no view."))
        .accessibleComponent();
  }

  /**
   * Gets the value of a property that a view of the basic catalog shows: the name of the shape of
   * an {@code Icon}, or the URL that a media view accepted.
   *
   * @param control the Swing component that has the accessibility attributes of the view
   * @param property the name of the property
   * @return the value, or an empty result if the component does not tell this property
   */
  static Optional<Object> basicProperty(JComponent control, String property) {
    return switch (property) {
      case "name" ->
          Optional.ofNullable(control.getClientProperty(SwingBasicCatalog.ICON_NAME_PROPERTY));
      case "url" ->
          Optional.ofNullable(control.getClientProperty(SwingBasicCatalog.MEDIA_URL_PROPERTY))
              .map(String::valueOf);
      default -> Optional.empty();
    };
  }

  /** Gets the properties that the view of a custom component got, or an empty result. */
  static Optional<NodeProps<?>> customProps(MountedNode node) {
    return node.component().orElse(null) instanceof JLabel label
            && label.getClientProperty(PROPS_PROPERTY) instanceof NodeProps<?> props
        ? Optional.of(props)
        : Optional.empty();
  }

  /**
   * Reads the text that a Swing component and the components below it show. The value of a control
   * that the user can edit is not a part of the text.
   */
  static String visibleText(Component component) {
    StringBuilder text = new StringBuilder();
    collectText(component, text);
    return text.toString().replaceAll("\\s+", " ").strip();
  }

  private static void collectText(Component component, StringBuilder text) {
    if (!component.isVisible()) {
      return;
    }
    if (component instanceof JTextComponent field) {
      if (!field.isEditable()) {
        text.append(documentText(field)).append(' ');
      }
      return;
    }
    if (component instanceof JLabel label && label.getText() != null) {
      text.append(label.getText()).append(' ');
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        collectText(child, text);
      }
    }
  }

  /** Reads the text of the document of a text component. */
  static String documentText(JTextComponent component) {
    Document document = component.getDocument();
    try {
      return document.getText(0, document.getLength());
    } catch (BadLocationException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Reads the names of the labels below a component, for example the messages of the checks. */
  static List<String> labelsBelow(Component component) {
    List<String> names = new ArrayList<>();
    collectLabels(component, names);
    return names;
  }

  private static void collectLabels(Component component, List<String> names) {
    if (!component.isVisible()) {
      return;
    }
    AccessibleContext context = component.getAccessibleContext();
    if (context != null
        && context.getAccessibleRole() == AccessibleRole.LABEL
        && context.getAccessibleName() != null) {
      names.add(context.getAccessibleName());
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        collectLabels(child, names);
      }
    }
  }

  /**
   * Makes sure of the accessibility attributes of components.
   *
   * @param expected the expected attributes for each component ID
   * @param where the location of the expectation
   */
  void assertAccessibility(Map<String, Object> expected, String where) {
    expected.forEach((id, value) -> {
      Map<String, Object> attributes = asObject(value, id + " in " + where);
      KnownKeys.assertKnownKeys(
          attributes,
          Set.of("label", "description", "live", "hidden", "role", "checked"),
          id + " in " + where);
      JComponent control = control(find(id));
      A2uiAccessibility given = A2uiAccessibility.of(control);
      AccessibleContext context = control.getAccessibleContext();
      String at = id + " in " + where;
      if (attributes.containsKey("label")) {
        // A hidden component has an empty accessible name. Thus the attribute is first.
        String label = given.label().orElseGet(context::getAccessibleName);
        JsonValues.assertJsonEquals(attributes.get("label"), label, "label of " + at);
        if (!given.hidden()) {
          JsonValues.assertJsonEquals(
              attributes.get("label"), context.getAccessibleName(), "accessible name of " + at);
        }
      }
      if (attributes.containsKey("description")) {
        JsonValues.assertJsonEquals(
            attributes.get("description"),
            context.getAccessibleDescription(),
            "accessible description of " + at);
      }
      if (attributes.containsKey("live")) {
        JsonValues.assertJsonEquals(
            attributes.get("live"), given.live().orElse(null), "live of " + at);
      }
      if (attributes.containsKey("hidden")) {
        JsonValues.assertJsonEquals(attributes.get("hidden"), given.hidden(), "hidden of " + at);
      }
      if (attributes.containsKey("role")) {
        JsonValues.assertJsonEquals(attributes.get("role"), roleOf(control), "role of " + at);
      }
      if (attributes.containsKey("checked")) {
        // A check box, a radio button and a toggle button have the state CHECKED while they are
        // selected.
        JsonValues.assertJsonEquals(
            attributes.get("checked"),
            context.getAccessibleStateSet().contains(AccessibleState.CHECKED),
            "checked of " + at);
      }
    });
  }

  /** Gets the buttons below a component, for example the options of a choice picker. */
  static List<AbstractButton> optionsOf(Component component) {
    List<AbstractButton> buttons = new ArrayList<>();
    collectButtons(component, buttons);
    return buttons;
  }

  private static void collectButtons(Component component, List<AbstractButton> buttons) {
    if (component instanceof AbstractButton button) {
      buttons.add(button);
      return;
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        collectButtons(child, buttons);
      }
    }
  }

  /** Gets the name that the suites use for the accessible role of a component. */
  private static String roleOf(JComponent control) {
    AccessibleRole role = control.getAccessibleContext().getAccessibleRole();
    if (role == AccessibleRole.PUSH_BUTTON) {
      return "button";
    }
    if (role == AccessibleRole.CHECK_BOX) {
      return "checkbox";
    }
    if (role == AccessibleRole.RADIO_BUTTON) {
      return "radio";
    }
    if (role == AccessibleRole.GROUP_BOX) {
      // Swing has no role for a group of radio buttons. A group box of radio buttons is one.
      for (AbstractButton option : optionsOf(control)) {
        if (option.getAccessibleContext().getAccessibleRole() == AccessibleRole.RADIO_BUTTON) {
          return "radiogroup";
        }
      }
      return "group";
    }
    if (control instanceof JTextComponent field && field.isEditable()) {
      return "textbox";
    }
    return role.toDisplayString(Locale.ROOT);
  }
}
