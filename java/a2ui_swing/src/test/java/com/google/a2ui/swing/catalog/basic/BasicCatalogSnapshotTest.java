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
import static com.google.a2ui.swing.testing.TestSurface.event;
import static com.google.a2ui.swing.testing.TestSurface.object;
import static com.google.a2ui.swing.testing.TestSurface.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.basic_catalog.v1_0.BasicCatalog;
import com.google.a2ui.core.exceptions.A2uiError;
import com.google.a2ui.core.processing.MessageProcessor;
import com.google.a2ui.core.state.SurfaceModel;
import com.google.a2ui.core.validation.ValidationConfig;
import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.catalog.SwingComponentImplementation;
import com.google.a2ui.swing.surface.A2uiFallbacks;
import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.A2uiSurface;
import com.google.a2ui.swing.surface.MountedNode;
import com.google.a2ui.swing.testing.Edt;
import com.google.a2ui.swing.testing.Headless;
import com.google.a2ui.swing.testing.Snapshots;
import com.google.a2ui.swing.testing.TestImageLoader;
import com.google.a2ui.swing.testing.TestModalPresenter;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * Shows surfaces of the basic catalog with no window and writes them as images, in the look and
 * feel of the test JVM, in Nimbus and in the look and feel of the system. The images are in {@code
 * target/snapshots/}. A person looks at them after a change of a view. The build does not compare
 * them.
 *
 * <p>The test also makes sure of the layout of each surface: no component is out of its parent, and
 * no two components of one container are on the same pixels.
 */
class BasicCatalogSnapshotTest {
  private static final int WIDTH = 420;

  /** The examples of the basic catalog that use only the components that have a view. */
  private static final List<String> EXAMPLES =
      List.of(
          "scaffold-simple-text",
          "scaffold-formatted-text",
          "scaffold-row-layout",
          "scaffold-interactive-button",
          "scaffold-simple-login-form",
          "scaffold-complex-layout",
          "scaffold-incremental",
          "login-form",
          "markdown-text",
          "child-list-template",
          "incremental-dashboard",
          "calendar-day",
          "email-compose",
          "countdown-timer",
          "weather-current");

  /** The width of the narrow surfaces. */
  private static final int NARROW = 200;

  /** The examples that show the input components. Some of them use components with no view. */
  private static final List<String> INPUT_EXAMPLES =
      List.of(
          "advanced-form-validator",
          "software-purchase",
          "live-invitation-builder",
          "task-card",
          "music-player");

  /** The width of the surfaces of {@link #WIDE_EXAMPLES}. */
  private static final int WIDE = 480;

  /**
   * The examples whose content cannot become as narrow as the usual width in each look and feel.
   * The row of {@code task-card} has a check box, a field for a date and a time, and an icon.
   */
  private static final List<String> WIDE_EXAMPLES = List.of("task-card");

  private static final String LONG_TEXT =
      "This text is long. It does not fit in one line of the surface, and thus it breaks at the"
          + " width that its container gives it.";

  private static Map<String, Object> check(Object condition, String message) {
    return Map.of("condition", condition, "message", message);
  }

  private static Object text(String id, Object text, Object... properties) {
    List<Object> all = new ArrayList<>(List.of("text", text));
    all.addAll(List.of(properties));
    return component(id, "Text", all.toArray());
  }

  private static Object button(String id, String label, Object... properties) {
    List<Object> all = new ArrayList<>(List.of("child", label, "action", event(id)));
    all.addAll(List.of(properties));
    return component(id, "Button", all.toArray());
  }

  private static List<Object> loginForm() {
    return List.of(
        component("root", "Column", "children", List.of("title", "hint", "user", "secret", "row")),
        text("title", "## Sign in"),
        text("hint", "Use the account of your organization.", "variant", "caption"),
        component(
            "user",
            "TextField",
            "label",
            "User name",
            "value",
            path("/user"),
            "placeholder",
            "name@example.com"),
        component(
            "secret",
            "TextField",
            "label",
            "Password",
            "value",
            path("/secret"),
            "variant",
            "obscured"),
        component("row", "Row", "children", List.of("login", "forgot"), "align", "center"),
        button("login", "login_text", "variant", "primary"),
        text("login_text", "Sign in"),
        button("forgot", "forgot_text", "variant", "borderless"),
        text("forgot_text", "Forgot the password?"));
  }

  private static List<Object> rows() {
    List<Object> components = new ArrayList<>();
    List<String> ids = new ArrayList<>(List.of("weights_title", "weights"));
    components.add(text("weights_title", "**Weights 1, 2 and none**", "variant", "caption"));
    components.add(component("weights", "Row", "children", List.of("w1", "w2", "w0")));
    components.add(button("w1", "w1_text", "weight", 1));
    components.add(text("w1_text", "weight 1"));
    components.add(button("w2", "w2_text", "weight", 2));
    components.add(text("w2_text", "weight 2"));
    components.add(button("w0", "w0_text"));
    components.add(text("w0_text", "none"));
    for (String justify :
        List.of(
            "start", "center", "end", "spaceBetween", "spaceAround", "spaceEvenly", "stretch")) {
      ids.add(justify + "_title");
      ids.add(justify);
      components.add(text(justify + "_title", "justify: `" + justify + "`", "variant", "caption"));
      components.add(
          component(
              justify,
              "Row",
              "justify",
              justify,
              "children",
              List.of(justify + "_a", justify + "_b", justify + "_c")));
      for (String item : List.of("a", "b", "c")) {
        components.add(button(justify + "_" + item, justify + "_" + item + "_text"));
        components.add(text(justify + "_" + item + "_text", item.equals("b") ? "second" : item));
      }
    }
    components.add(0, component("root", "Column", "children", ids));
    return components;
  }

  private static List<Object> markdown() {
    return List.of(
        component("root", "Column", "children", List.of("body", "caption")),
        text(
            "body",
            "# Heading 1\n## Heading 2\n### Heading 3\n\nA paragraph with **bold**, *italic*,"
                + " ~~old~~ and `inline code`. It is long, and thus it breaks at the width of the"
                + " surface. HTML such as <b>this</b> is text, and [a link](https://example.com)"
                + " is its text only.\n\n- First item\n- Second item with a longer text that needs"
                + " a second line in this narrow surface\n  - A nested item\n\n1. Step one\n2."
                + " Step two\n\n> A quote of one line.\n\n```\ncode block\n  two lines\n```"),
        text(
            "caption",
            "A caption is smaller and has a weaker color. *It can also have emphasis.*",
            "variant",
            "caption"));
  }

  private static List<Object> validation() {
    return List.of(
        component("root", "Column", "children", List.of("email", "nick", "notes", "amount", "row")),
        component(
            "email",
            "TextField",
            "label",
            "Email",
            "value",
            path("/email"),
            "checks",
            List.of(
                check(
                    call("email", "value", path("/email")),
                    "Enter a valid email address, for example user@example.com. The address"
                        + " must have a domain."))),
        component(
            "nick",
            "TextField",
            "label",
            "Nickname",
            "value",
            path("/nick"),
            "checks",
            List.of(check(path("/warning"), "unused"), check(path("/info"), "unused"))),
        component(
            "notes",
            "TextField",
            "label",
            "Notes",
            "value",
            path("/notes"),
            "variant",
            "longText",
            "placeholder",
            "Tell us more"),
        component(
            "amount",
            "TextField",
            "label",
            "Amount",
            "value",
            path("/amount"),
            "variant",
            "number"),
        component("row", "Row", "children", List.of("send", "cancel", "skip"), "justify", "end"),
        button(
            "send",
            "send_text",
            "variant",
            "primary",
            "checks",
            List.of(check(call("email", "value", path("/email")), "The email is not correct."))),
        text("send_text", "Send"),
        button("cancel", "cancel_text", "checks", List.of(check(path("/never"), "Not possible."))),
        text("cancel_text", "Cancel"),
        button(
            "skip",
            "skip_text",
            "variant",
            "borderless",
            "checks",
            List.of(check(path("/never"), "Not possible."))),
        text("skip_text", "Skip"));
  }

  private static List<Object> cards() {
    return List.of(
        component("root", "Column", "children", List.of("intro", "main", "pair", "outer")),
        text("intro", "Text before the cards", "variant", "caption"),
        component("main", "Card", "child", "main_column"),
        component(
            "main_column", "Column", "children", List.of("main_title", "main_text", "main_row")),
        text("main_title", "### A card"),
        text("main_text", LONG_TEXT),
        component("main_row", "Row", "children", List.of("accept", "refuse"), "justify", "end"),
        button("accept", "accept_text", "variant", "primary"),
        text("accept_text", "Accept"),
        button("refuse", "refuse_text"),
        text("refuse_text", "Refuse"),
        component("pair", "Row", "children", List.of("left", "right")),
        component("left", "Card", "child", "left_text", "weight", 1),
        text("left_text", "A short card"),
        component("right", "Card", "child", "right_text", "weight", 1),
        text("right_text", "A card with more text has more lines, and the two cards are equal."),
        component("outer", "Card", "child", "inner"),
        component("inner", "Card", "child", "inner_text"),
        text("inner_text", "A card in a card"));
  }

  private static List<Object> dividers() {
    return List.of(
        component(
            "root", "Column", "children", List.of("above", "flat", "row", "flat2", "centered")),
        text("above", "A horizontal divider is below this text."),
        component("flat", "Divider"),
        component("row", "Row", "children", List.of("one", "up1", "two", "up2", "three")),
        text("one", "## One"),
        component("up1", "Divider", "axis", "vertical"),
        text("two", "Two has a vertical divider on its two sides."),
        component("up2", "Divider", "axis", "vertical"),
        text("three", "Three"),
        component("flat2", "Divider", "axis", "horizontal"),
        component(
            "centered",
            "Column",
            "align",
            "center",
            "children",
            List.of("centered_text", "flat3", "centered_row")),
        text("centered_text", "A column that puts its children in the center"),
        component("flat3", "Divider"),
        component(
            "centered_row", "Row", "align", "center", "children", List.of("high", "up3", "low")),
        text("high", "# High"),
        component("up3", "Divider", "axis", "vertical"),
        text("low", "low", "variant", "caption"));
  }

  private static List<Object> lists() {
    return List.of(
        component(
            "root",
            "Column",
            "children",
            List.of("vertical_title", "vertical", "horizontal_title", "horizontal", "end")),
        text("vertical_title", "**A vertical list from a template**", "variant", "caption"),
        component("vertical", "List", "children", Map.of("componentId", "row", "path", "/people")),
        component("row", "Row", "children", List.of("name", "role"), "justify", "spaceBetween"),
        text("name", path("name")),
        text("role", path("role"), "variant", "caption"),
        text(
            "horizontal_title",
            "**A horizontal list that is wider than the surface**",
            "variant",
            "caption"),
        component(
            "horizontal",
            "List",
            "direction",
            "horizontal",
            "children",
            Map.of("componentId", "chip", "path", "/tags")),
        component("chip", "Card", "child", "chip_text"),
        text("chip_text", path("label")),
        text("end", "Text after the lists"));
  }

  private static Map<String, Object> listsData() {
    List<Object> tags = new ArrayList<>();
    for (String tag :
        List.of("Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta")) {
      tags.add(Map.of("label", tag));
    }
    return object(
        "people",
        List.of(
            Map.of("name", "Ada Lovelace", "role", "Mathematics"),
            Map.of("name", "Grace Hopper", "role", "Compilers"),
            Map.of("name", "Alan Turing", "role", "Computation"),
            Map.of("name", "Edsger Dijkstra", "role", "Algorithms")),
        "tags",
        tags);
  }

  private static List<Object> tabs() {
    return List.of(
        component("root", "Column", "children", List.of("before", "tabs", "after")),
        text("before", "Text before the tabs"),
        component(
            "tabs",
            "Tabs",
            "tabs",
            List.of(
                object("title", "Summary", "child", "summary"),
                object("title", path("/second"), "child", "details"),
                object("title", "History", "child", "history"))),
        component("summary", "Column", "children", List.of("summary_text", "summary_button")),
        text("summary_text", LONG_TEXT),
        button("summary_button", "summary_button_text"),
        text("summary_button_text", "Continue"),
        text("details", "The details"),
        text("history", "The history"),
        text("after", "Text after the tabs"));
  }

  private static List<Object> modal() {
    return List.of(
        component("root", "Column", "children", List.of("before", "modal", "link_modal")),
        text("before", "A button and a text open a modal."),
        component("modal", "Modal", "trigger", "open", "content", "dialog"),
        button("open", "open_text"),
        text("open_text", "Open the details"),
        component("dialog", "Card", "child", "dialog_column"),
        component(
            "dialog_column",
            "Column",
            "children",
            List.of("dialog_title", "dialog_line", "dialog_text", "dialog_row")),
        text("dialog_title", "### Details"),
        component("dialog_line", "Divider"),
        text("dialog_text", LONG_TEXT),
        component("dialog_row", "Row", "children", List.of("confirm"), "justify", "end"),
        button("confirm", "confirm_text", "variant", "primary"),
        text("confirm_text", "Confirm"),
        component("link_modal", "Modal", "trigger", "link", "content", "link_content"),
        text("link", "*More information*"),
        text("link_content", "The information"));
  }

  private static Map<String, Object> warning(String message) {
    return Map.of("valid", false, "message", message, "severity", "warning");
  }

  private static List<Object> options(String... labels) {
    List<Object> options = new ArrayList<>();
    for (String label : labels) {
      options.add(object("label", label, "value", label.toLowerCase(Locale.ROOT)));
    }
    return options;
  }

  private static List<Object> checkBoxes() {
    return List.of(
        component("root", "Column", "children", List.of("agree", "news", "long", "must", "warn")),
        component("agree", "CheckBox", "label", "I agree to the terms", "value", path("/agree")),
        component("news", "CheckBox", "label", "Send me the newsletter", "value", path("/news")),
        component(
            "long",
            "CheckBox",
            "label",
            "Share my data with the partners of the service, as the policy of the data describes"
                + " it",
            "value",
            path("/news")),
        component(
            "must",
            "CheckBox",
            "label",
            "I am at least 18 years old",
            "value",
            path("/adult"),
            "checks",
            List.of(check(path("/adult"), "You must be 18 or older."))),
        component(
            "warn",
            "CheckBox",
            "label",
            "Remember me on this computer",
            "value",
            path("/remember"),
            "checks",
            List.of(check(path("/shared"), "unused"))));
  }

  private static Map<String, Object> checkBoxData() {
    return object(
        "agree", true,
        "news", false,
        "adult", false,
        "remember", true,
        "shared", warning("Do not use this on a shared computer."));
  }

  private static List<Object> choices() {
    return List.of(
        component(
            "root",
            "Column",
            "children",
            List.of("size", "toppings", "color", "tags", "fruit", "required", "hinted")),
        component(
            "size",
            "ChoicePicker",
            "label",
            "Size",
            "options",
            options("Small", "Medium", "Large"),
            "value",
            path("/size")),
        component(
            "toppings",
            "ChoicePicker",
            "label",
            "Toppings",
            "variant",
            "multipleSelection",
            "options",
            options("Cheese", "Olives", "Peppers", "Mushrooms with a long name of the option"),
            "value",
            path("/toppings")),
        component(
            "color",
            "ChoicePicker",
            "label",
            "Color",
            "displayStyle",
            "chips",
            "options",
            options("Red", "Green", "Blue"),
            "value",
            path("/color")),
        component(
            "tags",
            "ChoicePicker",
            "label",
            "Tags",
            "variant",
            "multipleSelection",
            "displayStyle",
            "chips",
            "options",
            options(
                "Work", "Home", "Urgent", "Later", "Family", "Travel", "Finance", "Health",
                "Reading"),
            "value",
            path("/tags")),
        component(
            "fruit",
            "ChoicePicker",
            "label",
            "Fruit (filter: an)",
            "filterable",
            true,
            "options",
            options("Apple", "Banana", "Cherry", "Mango", "Orange", "Pineapple"),
            "value",
            path("/fruit")),
        component(
            "required",
            "ChoicePicker",
            "label",
            "Delivery",
            "options",
            options("Standard", "Express"),
            "value",
            path("/delivery"),
            "checks",
            List.of(check(path("/never"), "Select a delivery option."))),
        component(
            "hinted",
            "ChoicePicker",
            "label",
            "Newsletter",
            "displayStyle",
            "chips",
            "options",
            options("Daily", "Weekly"),
            "value",
            path("/newsletter"),
            "checks",
            List.of(check(path("/often"), "unused"))));
  }

  private static Map<String, Object> choiceData() {
    return object(
        "size", List.of("medium"),
        "toppings", List.of("cheese", "mushrooms with a long name of the option"),
        "color", List.of("green"),
        "tags", List.of("urgent", "travel"),
        "fruit", List.of("apple"),
        "delivery", List.of(),
        "never", false,
        "newsletter", List.of("daily"),
        "often", warning("Daily mail can be a lot of mail."));
  }

  private static List<Object> manyChoices() {
    List<String> labels = new ArrayList<>();
    for (int i = 1; i <= 30; i++) {
      labels.add("Option " + i);
    }
    return List.of(
        component("root", "Column", "children", List.of("many", "after")),
        component(
            "many",
            "ChoicePicker",
            "label",
            "Thirty options",
            "variant",
            "multipleSelection",
            "options",
            options(labels.toArray(String[]::new)),
            "value",
            path("/many")),
        text("after", "Text after the options"));
  }

  private static List<Object> sliders() {
    return List.of(
        component(
            "root",
            "Column",
            "children",
            List.of("volume", "rating", "opacity", "temperature", "low", "loud")),
        component("volume", "Slider", "label", "Volume", "max", 100, "value", path("/volume")),
        component(
            "rating",
            "Slider",
            "label",
            "Rating (5 steps)",
            "max",
            5,
            "steps",
            5,
            "value",
            path("/rating")),
        component(
            "opacity", "Slider", "label", "Opacity", "min", 0, "max", 1, "value", path("/opacity")),
        component(
            "temperature",
            "Slider",
            "label",
            "Temperature (10 steps)",
            "min",
            -10,
            "max",
            40,
            "steps",
            10,
            "value",
            path("/temperature")),
        component(
            "low",
            "Slider",
            "label",
            "Budget",
            "max",
            1000,
            "value",
            path("/budget"),
            "checks",
            List.of(check(path("/never"), "Select a budget of 100 or more."))),
        component(
            "loud",
            "Slider",
            "label",
            "Bass",
            "max",
            10,
            "value",
            path("/bass"),
            "checks",
            List.of(check(path("/strong"), "unused"))));
  }

  private static Map<String, Object> sliderData() {
    return object(
        "volume", 25,
        "rating", 3,
        "opacity", 0.42,
        "temperature", 15,
        "budget", 40,
        "never", false,
        "bass", 9,
        "strong", warning("A high bass can hurt small speakers."));
  }

  private static List<Object> dates() {
    return List.of(
        component(
            "root",
            "Column",
            "children",
            List.of(
                "date", "time", "both", "none", "empty", "zone", "wrong", "required", "hinted")),
        component(
            "date", "DateTimeInput", "label", "Date", "enableDate", true, "value", path("/date")),
        component(
            "time", "DateTimeInput", "label", "Time", "enableTime", true, "value", path("/time")),
        component(
            "both",
            "DateTimeInput",
            "label",
            "Date and time",
            "enableDate",
            true,
            "enableTime",
            true,
            "value",
            path("/both")),
        component("none", "DateTimeInput", "label", "No flag (a date)", "value", path("/date")),
        component(
            "empty",
            "DateTimeInput",
            "label",
            "Empty date and time",
            "enableDate",
            true,
            "enableTime",
            true,
            "value",
            path("/empty")),
        component(
            "zone",
            "DateTimeInput",
            "label",
            "A time in UTC",
            "enableDate",
            true,
            "enableTime",
            true,
            "value",
            path("/zone")),
        component(
            "wrong",
            "DateTimeInput",
            "label",
            "Not ISO 8601",
            "enableDate",
            true,
            "value",
            path("/wrong")),
        component(
            "required",
            "DateTimeInput",
            "label",
            "Start",
            "enableDate",
            true,
            "value",
            path("/empty"),
            "checks",
            List.of(check(path("/never"), "Select a start date."))),
        component(
            "hinted",
            "DateTimeInput",
            "label",
            "Reminder",
            "enableTime",
            true,
            "value",
            path("/time"),
            "checks",
            List.of(check(path("/late"), "unused"))));
  }

  private static Map<String, Object> dateData() {
    return object(
        "date", "2025-06-15",
        "time", "09:30",
        "both", "2025-01-01T08:00:00",
        "empty", "",
        "zone", "2025-01-01T08:00Z",
        "wrong", "next Monday",
        "never", false,
        "late", warning("The office closes at 18:00."));
  }

  private static List<Object> modalInputs() {
    return List.of(
        component("root", "Column", "children", List.of("modal")),
        component("modal", "Modal", "trigger", "open", "content", "form"),
        button("open", "open_text"),
        text("open_text", "Open the form"),
        component("form", "Card", "child", "form_column"),
        component(
            "form_column",
            "Column",
            "children",
            List.of("form_title", "form_box", "form_size", "form_volume", "form_date")),
        text("form_title", "### Preferences"),
        component("form_box", "CheckBox", "label", "I agree to the terms", "value", path("/agree")),
        component(
            "form_size",
            "ChoicePicker",
            "label",
            "Size",
            "displayStyle",
            "chips",
            "options",
            options("Small", "Medium", "Large"),
            "value",
            path("/size")),
        component("form_volume", "Slider", "label", "Volume", "max", 100, "value", path("/volume")),
        component(
            "form_date",
            "DateTimeInput",
            "label",
            "Date",
            "enableDate",
            true,
            "value",
            path("/date")));
  }

  private static Map<String, Object> modalInputData() {
    return object("agree", true, "size", List.of("medium"), "volume", 60, "date", "2025-06-15");
  }

  /** Types a text in the filter field of a choice picker of a mounted surface. */
  private static void filter(A2uiSurface view, String componentId, String text) {
    JComponent group =
        view.viewTree()
            .orElseThrow()
            .find(componentId)
            .orElseThrow()
            .view()
            .orElseThrow()
            .accessibleComponent();
    ((JTextField) group.getParent().getComponent(0)).setText(text);
  }

  private static Map<String, Object> validationData() {
    return object(
        "email", "not-an-email",
        "nick", "al",
        "notes", "",
        "amount", "12.50",
        "never", false,
        "warning",
            Map.of("valid", false, "message", "The nickname is short.", "severity", "warning"),
        "info", Map.of("valid", false, "message", "Other users see it.", "severity", "info"));
  }

  /**
   * Makes a surface from messages, mounts it and gives it the height of its content. The processor
   * accepts a reference to a component that arrives in a later message, as an example can have.
   */
  private static A2uiSurface mount(
      List<Object> messages, List<A2uiError> errors, A2uiServices services, int width) {
    MessageProcessor<SwingComponentImplementation> processor =
        new MessageProcessor<>(List.of(SwingBasicCatalog.create()), ValidationConfig.RELAXED);
    processor
        .model()
        .onSurfaceCreated()
        .subscribe(surface -> surface.onError().subscribe(errors::add));
    String[] surfaceId = {null};
    processor.model().onSurfaceCreated().subscribe(surface -> surfaceId[0] = surface.id());
    processor.processMessages(messages);
    SurfaceModel<SwingComponentImplementation> surface =
        processor.model().getSurface(surfaceId[0]).orElseThrow();
    A2uiSurface view =
        Headless.mount(new A2uiSurface(surface, A2uiFallbacks.NONE, services), width, 2000);
    fit(view, width);
    return view;
  }

  /** Gives a mounted surface the height of its content at a width. */
  private static void fit(A2uiSurface view, int width) {
    JComponent root = view.viewTree().orElseThrow().component().orElseThrow();
    int height =
        root instanceof HeightForWidth wrapping
            ? wrapping.preferredHeightFor(width)
            : root.getPreferredSize().height;
    view.setSize(width, height);
    view.validate();
  }

  private static List<Object> surfaceOf(Map<String, Object> data, List<Object> components) {
    return List.of(
        Map.of(
            "version",
            "1.0",
            "createSurface",
            object(
                "surfaceId",
                "s",
                "catalogId",
                BasicCatalog.ID,
                "dataModel",
                data,
                "components",
                components)));
  }

  /** Reads the messages of an example of the basic catalog. */
  private static List<Object> example(String name) throws IOException {
    Path directory = Path.of("").toAbsolutePath();
    while (!Files.isDirectory(directory.resolve("catalogs/basic/v1/examples"))) {
      directory = directory.getParent();
    }
    String json =
        Files.readString(directory.resolve("catalogs/basic/v1/examples").resolve(name + ".json"));
    Object document = new Load(LoadSettings.builder().build()).loadFromString(json);
    return plainList(((Map<?, ?>) document).get("messages"));
  }

  @SuppressWarnings("unchecked")
  private static List<Object> plainList(Object value) {
    return (List<Object>) plain(value);
  }

  private static Object plain(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      map.forEach((key, item) -> copy.put(String.valueOf(key), plain(item)));
      return copy;
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>();
      list.forEach(item -> copy.add(plain(item)));
      return copy;
    }
    return value;
  }

  /** Makes sure that each component is in its parent and that no two siblings overlap. */
  private static void assertLayout(A2uiSurface view, MountedNode node, String name) {
    List<Rectangle> siblings = new ArrayList<>();
    for (MountedNode child : node.children()) {
      if (child.component().isEmpty()) {
        continue;
      }
      JComponent component = child.component().get();
      Rectangle bounds = component.getBounds();
      String id = name + ": " + child.node().componentId();
      assertTrue(bounds.width > 0 && bounds.height > 0, id + " has no size: " + bounds);
      Rectangle parent = new Rectangle(component.getParent().getSize());
      assertTrue(parent.contains(bounds), id + " " + bounds + " is out of its parent " + parent);
      Rectangle inSurface = SwingUtilities.convertRectangle(component.getParent(), bounds, view);
      for (Rectangle sibling : siblings) {
        assertTrue(!sibling.intersects(inSurface), id + " is on a sibling: " + inSurface);
      }
      siblings.add(inSurface);
      // A component has the height that its content needs at its width.
      if (component instanceof HeightForWidth wrapping) {
        assertTrue(
            bounds.height >= wrapping.preferredHeightFor(bounds.width),
            id + " " + bounds + " needs the height " + wrapping.preferredHeightFor(bounds.width));
      }
      assertLayout(view, child, name);
    }
  }

  /**
   * Mounts a surface, makes sure of its layout and writes its image. Then the method opens each
   * modal of the surface and writes an image of the content of each one. The surface has no window.
   * Thus a test presenter keeps the content, and the image of the content is a different file.
   */
  private static void snapshot(String name, List<Object> messages) {
    snapshot(name, messages, WIDTH, view -> {});
  }

  /**
   * Mounts a surface at a width, operates it, and then does the work of {@link #snapshot(String,
   * List)}.
   */
  private static void snapshot(
      String name, List<Object> messages, int width, Consumer<A2uiSurface> operate) {
    TestModalPresenter presenter = new TestModalPresenter();
    List<A2uiError> errors = new ArrayList<>();
    A2uiSurface view =
        mount(
            messages,
            errors,
            A2uiServices.DEFAULT
                .withModalPresenter(presenter)
                .withImageLoader(TestImageLoader.patterns()),
            width);
    operate.accept(view);
    fit(view, width);
    assertEquals(List.of(), errors, name);
    assertTrue(view.getHeight() > 20, name + " has the height " + view.getHeight());
    assertLayout(view, view.viewTree().orElseThrow(), name);
    Path file = Snapshots.write(view, name);
    assertTrue(Files.exists(file), file.toString());
    openModals(view.viewTree().orElseThrow());
    List<TestModalPresenter.Presentation> modals = presenter.open();
    for (int i = 0; i < modals.size(); i++) {
      JComponent content = modals.get(i).layout(WIDTH - 80);
      String modalName = name + "-modal-content" + (modals.size() == 1 ? "" : "-" + (i + 1));
      assertTrue(content.getHeight() > 20, modalName + " has the height " + content.getHeight());
      assertTrue(Files.exists(Snapshots.write(content, modalName)), modalName);
    }
    assertEquals(List.of(), errors, name);
    view.removeNotify();
    assertEquals(List.of(), presenter.open(), name + ": a modal is open after the unmount");
  }

  /** Operates the trigger of each modal of a tree, as the user does. */
  private static void openModals(MountedNode node) {
    if (node.node().type().equals("Modal") && node.view().isPresent()) {
      JComponent trigger = node.view().get().accessibleComponent();
      AbstractButton button = buttonIn(trigger);
      if (button != null) {
        button.doClick(0);
      } else {
        trigger.getAccessibleContext().getAccessibleAction().doAccessibleAction(0);
      }
      return;
    }
    node.children().forEach(BasicCatalogSnapshotTest::openModals);
  }

  private static AbstractButton buttonIn(Component component) {
    if (component instanceof AbstractButton button) {
      return button;
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        AbstractButton found = buttonIn(child);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private static void snapshots(String lookAndFeel) throws IOException {
    snapshot(
        lookAndFeel + "-login-form",
        surfaceOf(Map.of("user", "", "secret", "secret"), loginForm()));
    snapshot(lookAndFeel + "-rows", surfaceOf(Map.of(), rows()));
    snapshot(lookAndFeel + "-markdown", surfaceOf(Map.of(), markdown()));
    snapshot(lookAndFeel + "-validation", surfaceOf(validationData(), validation()));
    snapshot(lookAndFeel + "-cards", surfaceOf(Map.of(), cards()));
    snapshot(lookAndFeel + "-dividers", surfaceOf(Map.of(), dividers()));
    snapshot(lookAndFeel + "-lists", surfaceOf(listsData(), lists()));
    snapshot(lookAndFeel + "-tabs", surfaceOf(Map.of("second", "Details"), tabs()));
    snapshot(lookAndFeel + "-modal", surfaceOf(Map.of(), modal()));
    snapshot(lookAndFeel + "-example-modal", example("modal"));
    inputSnapshots(lookAndFeel, WIDTH, "");
    inputSnapshots(lookAndFeel, NARROW, "-narrow");
    snapshot(
        lookAndFeel + "-choices-many",
        surfaceOf(object("many", List.of("option 3")), manyChoices()));
    snapshot(
        lookAndFeel + "-choices-many-narrow",
        surfaceOf(object("many", List.of("option 3")), manyChoices()),
        NARROW,
        view -> {});
    snapshot(lookAndFeel + "-inputs-modal", surfaceOf(modalInputData(), modalInputs()));
    for (String example : INPUT_EXAMPLES) {
      snapshot(
          lookAndFeel + "-example-" + example,
          example(example),
          WIDE_EXAMPLES.contains(example) ? WIDE : WIDTH,
          view -> {});
    }
    for (String example : EXAMPLES) {
      snapshot(lookAndFeel + "-example-" + example, example(example));
    }
  }

  /** Writes the images of the surfaces of the input components at a width. */
  private static void inputSnapshots(String lookAndFeel, int width, String suffix) {
    snapshot(
        lookAndFeel + "-checkboxes" + suffix,
        surfaceOf(checkBoxData(), checkBoxes()),
        width,
        view -> {});
    snapshot(
        lookAndFeel + "-choices" + suffix,
        surfaceOf(choiceData(), choices()),
        width,
        view -> filter(view, "fruit", "an"));
    snapshot(
        lookAndFeel + "-sliders" + suffix, surfaceOf(sliderData(), sliders()), width, view -> {});
    snapshot(lookAndFeel + "-dates" + suffix, surfaceOf(dateData(), dates()), width, view -> {});
  }

  @Test
  void theSurfacesPaintInTheLookAndFeelOfTheTestJvm() {
    Edt.run(() -> snapshots("default"));
  }

  @Test
  void theSurfacesPaintInEachOtherLookAndFeelOfTheComputer() {
    Edt.run(() -> {
      LookAndFeel original = UIManager.getLookAndFeel();
      Color panel = UIManager.getColor("Panel.background");
      try {
        for (String[] other :
            new String[][] {
              {"nimbus", "javax.swing.plaf.nimbus.NimbusLookAndFeel"},
              {"system", UIManager.getSystemLookAndFeelClassName()}
            }) {
          try {
            UIManager.setLookAndFeel(other[1]);
          } catch (ReflectiveOperationException
              | javax.swing.UnsupportedLookAndFeelException
              | RuntimeException e) {
            // A computer with no display cannot have each look and feel.
            continue;
          }
          snapshots(other[0]);
        }
      } finally {
        UIManager.setLookAndFeel(original);
      }
      assertEquals(panel, UIManager.getColor("Panel.background"));
    });
  }
}
