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

package com.google.a2ui.swing.catalog;

import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The definition of a component that can also show the component with Swing.
 *
 * <p>An implementation is an entry of a catalog: {@code Catalog<SwingComponentImplementation, ?>}.
 * The node resolver of the core selects the entry for each component of a surface. Thus the adapter
 * has no registry of its own, and a surface can use components of many catalogs.
 *
 * <p>An implementation has no state of one component. It makes one {@link SwingComponentView} for
 * each component that a surface shows, and the view has the state.
 *
 * <p>Extend this class, or use {@link #of(ComponentApi, Function)}:
 *
 * <pre>{@code
 * SwingComponentImplementation badge =
 *     SwingComponentImplementation.of(
 *         badgeApi,
 *         context -> {
 *           JLabel label = new JLabel();
 *           return SwingComponentView.of(
 *               label, (node, props) -> label.setText(Coercion.toText(props.value("text"))));
 *         });
 * }</pre>
 */
public abstract class SwingComponentImplementation extends ComponentApi {
  /**
   * Makes an implementation.
   *
   * @param name the name of the component as messages write it, for example {@code Button}
   * @param schema the definition of the component as a catalog document writes it
   * @throws A2uiCatalogError if the schema is not JSON, or if its {@code metadata} is not an object
   */
  protected SwingComponentImplementation(String name, Map<String, ?> schema) {
    super(name, schema);
  }

  /**
   * Makes an implementation of a component that a catalog describes.
   *
   * @param api the definition of the component, for example an entry of {@code
   *     BasicCatalog.api().components()}
   */
  protected SwingComponentImplementation(ComponentApi api) {
    super(api.name(), api.schema());
  }

  /**
   * Makes the view of one component of a surface. The adapter calls this method one time for each
   * component that it starts to show, on the event dispatch thread.
   *
   * @param context the objects that the view can use
   * @return the new view
   */
  public abstract SwingComponentView createView(ViewContext context);

  /**
   * Tells if this implementation can make a view. The default implementation returns true.
   *
   * <p>An implementation that returns false only declares the component. Then a message that uses
   * the component is correct, but the surface cannot show the component. The adapter does not call
   * {@link #createView(ViewContext)}. It shows the {@code error} fallback of the surface with the
   * error type {@code no-view}, or nothing. See {@link #withoutView(ComponentApi)}.
   *
   * @return true if {@link #createView(ViewContext)} makes a view
   */
  public boolean hasView() {
    return true;
  }

  /**
   * Makes an implementation that declares a component and has no view for it. A catalog uses it for
   * a component that the adapter cannot show at this time. Thus the catalog stays complete, and the
   * agent can send the component.
   *
   * @param api the definition of the component
   * @return the implementation. Its {@link #hasView()} returns false, and its {@link
   *     #createView(ViewContext)} throws an {@code UnsupportedOperationException}.
   */
  public static SwingComponentImplementation withoutView(ComponentApi api) {
    Objects.requireNonNull(api, "api");
    return new SwingComponentImplementation(api) {
      @Override
      public SwingComponentView createView(ViewContext context) {
        throw new UnsupportedOperationException(
            "The component '" + name() + "' has no Swing view.");
      }

      @Override
      public boolean hasView() {
        return false;
      }
    };
  }

  /**
   * Makes an implementation from a function that makes the views.
   *
   * @param api the definition of the component
   * @param viewFactory makes the view of one component. See {@link #createView(ViewContext)}.
   * @return the implementation
   */
  public static SwingComponentImplementation of(
      ComponentApi api, Function<? super ViewContext, ? extends SwingComponentView> viewFactory) {
    Objects.requireNonNull(api, "api");
    return of(api.name(), api.schema(), viewFactory);
  }

  /**
   * Makes an implementation from a name, a schema and a function that makes the views.
   *
   * @param name the name of the component as messages write it
   * @param schema the definition of the component as a catalog document writes it
   * @param viewFactory makes the view of one component. See {@link #createView(ViewContext)}.
   * @return the implementation
   * @throws A2uiCatalogError if the schema is not JSON, or if its {@code metadata} is not an object
   */
  public static SwingComponentImplementation of(
      String name,
      Map<String, ?> schema,
      Function<? super ViewContext, ? extends SwingComponentView> viewFactory) {
    Objects.requireNonNull(viewFactory, "viewFactory");
    return new SwingComponentImplementation(name, schema) {
      @Override
      public SwingComponentView createView(ViewContext context) {
        return viewFactory.apply(context);
      }
    };
  }

  @Override
  public String toString() {
    return "SwingComponentImplementation(" + name() + ")";
  }
}
