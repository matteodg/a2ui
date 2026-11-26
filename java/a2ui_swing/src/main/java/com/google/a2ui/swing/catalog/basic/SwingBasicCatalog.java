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

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import com.google.a2ui.swing.surface.A2uiServices;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The basic catalog of protocol v1.0 for Swing: the components with their Swing views, and the
 * functions with their code.
 *
 * <pre>{@code
 * MessageProcessor<SwingComponentImplementation> processor =
 *     new MessageProcessor<>(List.of(SwingBasicCatalog.create()));
 * A2uiSwing.install(processor);
 * }</pre>
 *
 * <p>The catalog has the ID and the document of {@code BasicCatalog}. It declares each component of
 * the catalog document. This version has a view for these components:
 *
 * <table>
 *   <caption>Components that have a view</caption>
 *   <tr><th>Component</th><th>Swing component</th></tr>
 *   <tr><td>{@code Text}</td><td>The view of the {@code markdown} service. The default is a {@code
 *       JEditorPane} that shows the Markdown subset.</td></tr>
 *   <tr><td>{@code Image}</td><td>A component that paints the image of the {@code imageLoader}
 *       service</td></tr>
 *   <tr><td>{@code Icon}</td><td>A component that paints a shape of the module, or the SVG path
 *       data of the agent</td></tr>
 *   <tr><td>{@code Video}</td><td>The view of the {@code mediaPlayer} service. The default is a
 *       button with the poster image that opens the URL in an external application.</td></tr>
 *   <tr><td>{@code Row}, {@code Column}</td><td>{@link FlexPanel}</td></tr>
 *   <tr><td>{@code List}</td><td>{@link FlexPanel} in a {@code JScrollPane}</td></tr>
 *   <tr><td>{@code Card}</td><td>A panel that paints a rounded outline</td></tr>
 *   <tr><td>{@code Tabs}</td><td>{@code JTabbedPane}</td></tr>
 *   <tr><td>{@code Modal}</td><td>A panel for the trigger. The {@code modalPresenter} service
 *       shows the content. The default is a modal {@code JDialog}.</td></tr>
 *   <tr><td>{@code Divider}</td><td>{@code JSeparator}</td></tr>
 *   <tr><td>{@code Button}</td><td>{@code JButton}</td></tr>
 *   <tr><td>{@code TextField}</td><td>{@code JTextField}, {@code JTextArea} or {@code
 *       JPasswordField}</td></tr>
 *   <tr><td>{@code CheckBox}</td><td>{@code JCheckBox}</td></tr>
 *   <tr><td>{@code ChoicePicker}</td><td>{@code JRadioButton}, {@code JCheckBox} or {@code
 *       JToggleButton} for each option</td></tr>
 *   <tr><td>{@code Slider}</td><td>{@code JSlider}</td></tr>
 *   <tr><td>{@code DateTimeInput}</td><td>{@code JSpinner} with a text field for ISO 8601
 *       text</td></tr>
 * </table>
 *
 * <p>Each other component of the catalog document has an implementation with no view. See {@link
 * SwingComponentImplementation#withoutView(ComponentApi)}. A message that uses such a component is
 * correct. The surface shows its {@code error} fallback with the error type {@code no-view} for the
 * component, or nothing, and the component uses no layout space. This is what the adapter does for
 * each node that cannot show. A later version adds the views. {@link #viewNames()} gives the
 * components that have a view.
 *
 * <p>To replace the view of one component, make a catalog with a different list:
 *
 * <pre>{@code
 * List<SwingComponentImplementation> components = new ArrayList<>(SwingBasicCatalog.components());
 * components.replaceAll(c -> c.name().equals("Button") ? myButton : c);
 * Catalog<SwingComponentImplementation, FunctionImplementation> catalog =
 *     SwingBasicCatalog.create().withComponents(components);
 * }</pre>
 */
public final class SwingBasicCatalog {
  /**
   * The key of the client property that has the name of the icon that the view of an {@code Icon}
   * shows. The Swing component is the {@code accessibleComponent()} of the view. The value is the
   * name that the agent gave, for example {@code arrowBack}. For a name that the catalog does not
   * have, the value is that name, and the icon shows the shape of the name {@code help}. The
   * property is absent if the icon shows SVG path data or shows nothing.
   */
  public static final String ICON_NAME_PROPERTY = "a2ui.basic.iconName";

  /**
   * The key of the client property that has the URL that the view of an {@code Image}, a {@code
   * Video} or an {@code AudioPlayer} shows. The Swing component is the {@code
   * accessibleComponent()} of the view. The value is a {@code java.net.URI}. The property is absent
   * if the component has no URL or if its URL is not safe. See {@code MediaUrls}.
   */
  public static final String MEDIA_URL_PROPERTY = "a2ui.basic.mediaUrl";

  /** The function that makes the view of each component that has one, in the catalog sequence. */
  private static final Map<String, Function<ViewContext, SwingComponentView>> VIEWS = views();

  private SwingBasicCatalog() {}

  private static Map<String, Function<ViewContext, SwingComponentView>> views() {
    Map<String, Function<ViewContext, SwingComponentView>> views = new LinkedHashMap<>();
    views.put("Text", TextView::new);
    views.put("Image", ImageView::new);
    views.put("Icon", IconView::new);
    views.put("Video", VideoView::new);
    views.put("Row", context -> new FlexContainerView(context, FlexLayout.Axis.HORIZONTAL));
    views.put("Column", context -> new FlexContainerView(context, FlexLayout.Axis.VERTICAL));
    views.put("List", ListView::new);
    views.put("Card", CardView::new);
    views.put("Tabs", TabsView::new);
    views.put("Modal", ModalView::new);
    views.put("Divider", DividerView::new);
    views.put("Button", ButtonView::new);
    views.put("TextField", TextFieldView::new);
    views.put("CheckBox", CheckBoxView::new);
    views.put("ChoicePicker", ChoicePickerView::new);
    views.put("Slider", SliderView::new);
    views.put("DateTimeInput", DateTimeInputView::new);
    return Collections.unmodifiableMap(views);
  }

  /**
   * Makes the catalog with the default services. The functions write text for the locale {@link
   * Locale#US}, as {@code BasicCatalog.withFunctions()} does. The function {@code openUrl} opens
   * the browser of the system.
   *
   * @return the catalog
   */
  public static Catalog<SwingComponentImplementation, FunctionImplementation> create() {
    return create(Locale.US);
  }

  /**
   * Makes the catalog with the default services.
   *
   * @param locale the locale of the text that {@code formatNumber}, {@code formatCurrency}, {@code
   *     formatDate} and {@code pluralize} write
   * @return the catalog
   */
  public static Catalog<SwingComponentImplementation, FunctionImplementation> create(
      Locale locale) {
    return create(locale, A2uiServices.DEFAULT);
  }

  /**
   * Makes the catalog.
   *
   * <p>The catalog uses only the URL opener of the services, for the function {@code openUrl}. The
   * views get the other services from the surface component. Thus give the same services to the
   * {@code A2uiSurface}.
   *
   * @param locale the locale of the text that {@code formatNumber}, {@code formatCurrency}, {@code
   *     formatDate} and {@code pluralize} write
   * @param services the services of the application
   * @return the catalog
   */
  public static Catalog<SwingComponentImplementation, FunctionImplementation> create(
      Locale locale, A2uiServices services) {
    Objects.requireNonNull(locale, "locale");
    Objects.requireNonNull(services, "services");
    return BasicCatalog.withFunctions(locale, services.urlOpener()).withComponents(components());
  }

  /**
   * Gets the implementation of each component of the catalog document, in the sequence of the
   * document.
   *
   * @return the implementations. The list is read-only. Each call makes new objects.
   */
  public static List<SwingComponentImplementation> components() {
    List<SwingComponentImplementation> components = new ArrayList<>();
    for (ComponentApi api : BasicCatalog.api().components().values()) {
      Function<ViewContext, SwingComponentView> view = VIEWS.get(api.name());
      components.add(
          view == null
              ? SwingComponentImplementation.withoutView(api)
              : SwingComponentImplementation.of(api, view));
    }
    return Collections.unmodifiableList(components);
  }

  /**
   * Gets the names of the components that have a view in this version.
   *
   * @return the names. The set is read-only.
   */
  public static Set<String> viewNames() {
    return VIEWS.keySet();
  }
}
