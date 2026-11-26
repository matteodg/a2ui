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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.surface.A2uiErrorInfo;
import com.google.a2ui.swing.surface.A2uiFallbackError;
import com.google.a2ui.swing.surface.A2uiFallbacks;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.A2uiSurface;
import com.google.a2ui.swing.surface.MountedNode;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.Headless;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class SwingBasicCatalogTest {
  private static final Set<String> VIEWS =
      Set.of("Text", "Row", "Column", "List", "Card", "Tabs", "Divider", "Button", "TextField");

  /**
   * Makes the catalog with one component that only declares its type. Thus the tests of a component
   * with no view do not depend on the list of the views of this version.
   */
  private static Catalog<SwingComponentImplementation, FunctionImplementation> catalogWithoutViewOf(
      String name) {
    List<SwingComponentImplementation> components = new ArrayList<>(SwingBasicCatalog.components());
    components.replaceAll(implementation ->
        implementation.name().equals(name)
            ? SwingComponentImplementation.withoutView(implementation)
            : implementation);
    return SwingBasicCatalog.create().withComponents(components);
  }

  /** Makes a surface with the catalog and mounts it. */
  private static A2uiSurface mount(
      Catalog<SwingComponentImplementation, FunctionImplementation> catalog,
      A2uiFallbacks fallbacks,
      Object... components) {
    MessageProcessor<SwingComponentImplementation> processor =
        new MessageProcessor<>(List.of(catalog));
    processor.processMessages(
        Map.of(
            "version",
            "1.0",
            "createSurface",
            object(
                "surfaceId",
                "s",
                "catalogId",
                BasicCatalog.ID,
                "components",
                List.of(components))));
    return Headless.mount(
        new A2uiSurface(processor.model().getSurface("s").orElseThrow(), fallbacks), 400, 300);
  }

  @Test
  void theCatalogDeclaresEachComponentAndHasTheCodeOfEachFunction() {
    Catalog<SwingComponentImplementation, FunctionImplementation> catalog =
        SwingBasicCatalog.create();

    assertEquals(BasicCatalog.ID, catalog.id());
    assertEquals(Optional.of(ProtocolVersion.V1_0), catalog.protocolVersion());
    assertEquals(
        new ArrayList<>(BasicCatalog.api().components().keySet()),
        new ArrayList<>(catalog.components().keySet()));
    assertEquals(BasicCatalog.api().functions().keySet(), catalog.functions().keySet());
    assertEquals(BasicCatalog.api().toJson(), catalog.toJson());
    catalog
        .components()
        .forEach((name, implementation) -> {
          assertEquals(VIEWS.contains(name), implementation.hasView(), name);
          assertEquals(BasicCatalog.api().components().get(name).schema(), implementation.schema());
        });
  }

  @Test
  void theNamesOfTheViewsAreTheComponentsThatHaveAView() {
    assertEquals(VIEWS, SwingBasicCatalog.viewNames());
    assertEquals(18, SwingBasicCatalog.components().size());
    assertThrows(UnsupportedOperationException.class, () -> SwingBasicCatalog.viewNames().clear());
    assertThrows(UnsupportedOperationException.class, () -> SwingBasicCatalog.components().clear());
  }

  @Test
  void theFunctionsWriteTextForTheLocaleOfTheCatalog() {
    Edt.run(() -> {
      Object text =
          component("root", "Text", "text", call("formatNumber", "value", 1234.5, "decimals", 1));

      A2uiSurface us = mount(SwingBasicCatalog.create(), A2uiFallbacks.NONE, text);
      A2uiSurface german =
          mount(SwingBasicCatalog.create(Locale.GERMANY), A2uiFallbacks.NONE, text);

      assertEquals("1,234.5", VisibleText.of(us));
      assertEquals("1.234,5", VisibleText.of(german));
    });
  }

  @Test
  void theFunctionOpenUrlUsesTheUrlOpenerOfTheServices() {
    Edt.run(() -> {
      List<URI> opened = new ArrayList<>();
      A2uiSurface view =
          mount(
              SwingBasicCatalog.create(Locale.US, A2uiServices.DEFAULT.withUrlOpener(opened::add)),
              A2uiFallbacks.NONE,
              component(
                  "root",
                  "Button",
                  "child",
                  "label",
                  "action",
                  Map.of("functionCall", call("openUrl", "url", "https://example.com/docs"))),
              component("label", "Text", "text", "Docs"));
      MountedNode root = view.viewTree().orElseThrow();

      ((JButton) root.view().orElseThrow().accessibleComponent()).doClick(0);

      assertEquals(List.of(URI.create("https://example.com/docs")), opened);
    });
  }

  @Test
  void aComponentThatHasNoViewIsCorrectAndShowsNothing() {
    Edt.run(() -> {
      A2uiSurface view =
          mount(
              catalogWithoutViewOf("Card"),
              A2uiFallbacks.NONE,
              component("root", "Column", "children", List.of("first", "card", "last")),
              component("first", "Text", "text", "first"),
              component("card", "Card", "child", "inside"),
              component("inside", "Text", "text", "inside"),
              component("last", "Text", "text", "last"));
      MountedNode root = view.viewTree().orElseThrow();
      MountedNode card = root.find("card").orElseThrow();

      assertFalse(card.occupiesLayout());
      assertEquals(Optional.empty(), card.view());
      assertEquals("first last", VisibleText.of(view));
      // The texts are side by side on the vertical axis. The card has no space between them.
      assertEquals(
          root.find("first").orElseThrow().component().orElseThrow().getHeight(),
          root.find("last").orElseThrow().component().orElseThrow().getY());
    });
  }

  @Test
  void aComponentThatHasNoViewShowsTheErrorFallbackOfTheSurface() {
    Edt.run(() -> {
      List<A2uiErrorInfo> infos = new ArrayList<>();
      A2uiSurface view =
          mount(
              catalogWithoutViewOf("Divider"),
              A2uiFallbacks.NONE.withError(info -> {
                infos.add(info);
                return new JLabel("no view for " + info.error().componentType().orElse("?"));
              }),
              component("root", "Column", "children", List.of("line")),
              component("line", "Divider"));

      assertEquals(
          List.of(
              new A2uiErrorInfo(
                  "line",
                  Optional.of("Column"),
                  new A2uiFallbackError(A2uiFallbackError.NO_VIEW, Optional.of("Divider")))),
          infos);
      assertEquals("no view for Divider", VisibleText.of(view));
      assertTrue(view.viewTree().orElseThrow().find("line").orElseThrow().occupiesLayout());
    });
  }
}
