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

package com.google.a2ui.core.resolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionApi;
import com.google.a2ui.core.catalog.FunctionBody;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiCatalogError;
import com.google.a2ui.core.exceptions.A2uiDataError;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.expressions.Expression;
import com.google.a2ui.core.expressions.ExpressionParser;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * The behavior of the context with functions that are not in the basic catalog. Thus the tests show
 * that the context has no rule for one catalog or for one function name.
 */
class DataContextTest {
  private static final String UPPER_ID = "urn:test:upper";
  private static final String LOWER_ID = "urn:test:lower";

  private static final Map<String, Object> DYNAMIC_BOOLEAN =
      Map.of("$ref", "common_types.json#/$defs/DynamicBoolean");

  /** The names of the functions that ran, in the sequence of the calls. */
  private final List<String> calls = new ArrayList<>();

  /** A catalog whose function {@code letters} writes upper case letters. */
  private final Catalog<ComponentApi, FunctionApi> upper =
      catalog(UPPER_ID, value -> value.toUpperCase(Locale.ROOT));

  /** A catalog whose function {@code letters} writes lower case letters. */
  private final Catalog<ComponentApi, FunctionApi> lower =
      catalog(LOWER_ID, value -> value.toLowerCase(Locale.ROOT));

  private Catalog<ComponentApi, FunctionApi> catalog(String id, UnaryOperator<String> letters) {
    List<FunctionApi> functions =
        List.of(
            function(
                "letters",
                Map.of("returnType", "string"),
                (args, context) -> letters.apply(Coercion.toText(args.get("value")))),
            // Evaluates a template in the context of the call, as `formatString` does.
            function(
                "render",
                Map.of("returnType", "string"),
                (args, context) -> {
                  StringBuilder text = new StringBuilder();
                  for (Expression part :
                      ExpressionParser.parseTemplate(Coercion.toText(args.get("value")))) {
                    text.append(Coercion.toText(context.evaluate(part)));
                  }
                  return text.toString();
                }),
            function("echo", echoSchema(), (args, context) -> args),
            function("check", Map.of(), (args, context) -> Map.of("valid", false)),
            function("secret", Map.of("allowedCallers", "agentOnly"), (args, context) -> "secret"),
            function("beep", Map.of("requiresUserActivation", true), (args, context) -> null),
            new FunctionApi("described", Map.of("returnType", "string")));
    return new Catalog<>(id, List.of(), functions, null, ProtocolVersion.V1_0);
  }

  private FunctionImplementation function(
      String name, Map<String, Object> schema, FunctionBody body) {
    return new FunctionImplementation(
        name,
        schema,
        (args, context) -> {
          calls.add(name);
          return body.execute(args, context);
        });
  }

  /** The definition of a function that has arguments of the type {@code DynamicBoolean}. */
  private static Map<String, Object> echoSchema() {
    Map<String, Object> properties =
        Map.of(
            "on", DYNAMIC_BOOLEAN,
            "all", Map.of("type", "array", "items", DYNAMIC_BOOLEAN),
            "options", Map.of("type", "object", "properties", Map.of("strict", DYNAMIC_BOOLEAN)),
            "label", Map.of("$ref", "common_types.json#/$defs/DynamicString"));
    return Map.of("properties", Map.of("args", Map.of("type", "object", "properties", properties)));
  }

  /** Makes a surface that can use the two catalogs. */
  private SurfaceModel<ComponentApi> surface(
      Catalog<ComponentApi, ?> defaultCatalog, Map<String, Object> data) {
    SurfaceModel<ComponentApi> surface =
        new SurfaceModel<>(
            "s",
            ProtocolVersion.V1_0,
            defaultCatalog,
            Map.of(UPPER_ID, upper, LOWER_ID, lower),
            false,
            null);
    surface.dataModel().set("/", data);
    return surface;
  }

  private SurfaceModel<ComponentApi> surface(Map<String, Object> data) {
    return surface(upper, data);
  }

  private DataContext context() {
    return new DataContext(surface(Map.of()));
  }

  private static Map<String, Object> call(String name, Map<String, Object> args) {
    return Map.of("@call", name, "args", args);
  }

  private static Map<String, Object> call(String name, String catalogId, Map<String, Object> args) {
    return Map.of("@call", name, "catalogId", catalogId, "args", args);
  }

  @Test
  void aNewContextIsAtTheRootAndOutsideAListTemplate() {
    SurfaceModel<ComponentApi> surface = surface(Map.of());

    DataContext context = new DataContext(surface);

    assertSame(surface, context.surface());
    assertEquals("/", context.path());
    assertEquals(OptionalInt.empty(), context.index());
    assertEquals("DataContext(s, /)", context.toString());
  }

  @Test
  void aContextPathThatIsNotAbsoluteStartsAtTheRoot() {
    SurfaceModel<ComponentApi> surface = surface(Map.of());

    assertEquals("/user", new DataContext(surface, "user").path());
    assertEquals("/user", new DataContext(surface, "/user").path());
    assertEquals("/", new DataContext(surface, "").path());
  }

  @Test
  void resolvePathReadsARelativePathFromTheContextPath() {
    DataContext context = new DataContext(surface(Map.of()), "/user");

    assertEquals("/user/name", context.resolvePath("name"));
    assertEquals("/title", context.resolvePath("/title"));
    assertEquals("/user", context.resolvePath(""));
  }

  @Test
  void nestedKeepsTheListPositionUnlessTheCallGivesOne() {
    DataContext root = context();

    DataContext item = root.nested("items/1", 1);
    DataContext field = item.nested("address");

    assertEquals("/items/1", item.path());
    assertEquals(OptionalInt.of(1), item.index());
    assertEquals("/items/1/address", field.path());
    assertEquals(OptionalInt.of(1), field.index());
    assertEquals(OptionalInt.of(0), field.nested("lines/0", 0).index());
    assertEquals(OptionalInt.empty(), root.nested("items").index());
    assertThrows(IllegalArgumentException.class, () -> root.nested("items/0", -1));
  }

  @Test
  void setWritesRelativeToTheContextPath() {
    SurfaceModel<ComponentApi> surface = surface(Map.of("user", Map.of("name", "Ada")));
    DataContext context = new DataContext(surface, "/user");

    context.set("name", "Grace");
    context.set("/title", "Dr");

    assertEquals("Grace", surface.dataModel().get("/user/name"));
    assertEquals("Dr", surface.dataModel().get("/title"));
    assertThrows(A2uiDataError.class, () -> context.set("name/first", "G"));
  }

  @Test
  void aBindingBecomesTheDataAtItsPath() {
    Map<String, Object> data = Map.of("user", Map.of("name", "Ada"), "title", "Dr");
    DataContext context = new DataContext(surface(data)).nested("user");

    assertEquals("Ada", context.resolveDynamicValue(Map.of("@path", "name")));
    assertEquals("Dr", context.resolveDynamicValue(Map.of("@path", "/title")));
    assertEquals(Map.of("name", "Ada"), context.resolveDynamicValue(Map.of("@path", "")));
    assertNull(context.resolveDynamicValue(Map.of("@path", "missing")));
  }

  @Test
  void bindingsAndCallsInObjectsAndListsAreResolved() {
    DataContext context = new DataContext(surface(Map.of("name", "Ada")));
    Map<String, Object> value =
        Map.of(
            "label",
            List.of(Map.of("@path", "/name"), "!"),
            "style",
            Map.of("title", call("letters", Map.of("value", "big"))));

    Object resolved = context.resolveDynamicValue(value);

    assertEquals(Map.of("label", List.of("Ada", "!"), "style", Map.of("title", "BIG")), resolved);
    assertThrows(UnsupportedOperationException.class, () -> ((Map<?, ?>) resolved).remove("label"));
  }

  @Test
  void valuesThatAreNotBindingsOrCallsStayAsTheyAre() {
    DataContext context = context();
    Map<String, Object> written = new LinkedHashMap<>();
    written.put("@@path", "kept");
    written.put("path", "/name");
    written.put("call", "letters");
    written.put("nothing", null);

    Map<String, Object> expected = new LinkedHashMap<>(written);
    expected.remove("@@path");
    expected.put("@path", "kept");
    assertEquals(expected, context.resolveDynamicValue(written));
    assertEquals(5L, context.resolveDynamicValue(5L));
    assertEquals("text", context.resolveDynamicValue("text"));
    assertNull(context.resolveDynamicValue(null));
  }

  @Test
  void aLiteralObjectCannotHaveAKeyThatStartsWithOneAtSign() {
    DataContext context = context();

    A2uiExpressionError unknown =
        assertThrows(
            A2uiExpressionError.class,
            () -> context.resolveDynamicValue(Map.of("label", Map.of("@unknown", 1L))));
    A2uiExpressionError notText =
        assertThrows(
            A2uiExpressionError.class, () -> context.resolveDynamicValue(Map.of("@path", 5L)));

    assertTrue(unknown.getMessage().contains("'@unknown'"));
    assertTrue(notText.getMessage().contains("'@path'"));
  }

  @Test
  void aCallRunsInTheCatalogThatItNames() {
    DataContext context = context();
    Map<String, Object> args = Map.of("value", "Ada");

    assertEquals("ADA", context.resolveDynamicValue(call("letters", args)));
    assertEquals("ada", context.resolveDynamicValue(call("letters", LOWER_ID, args)));
    assertEquals("ADA", context.resolveDynamicValue(call("letters", UPPER_ID, args)));
  }

  @Test
  void aCallResolvesItsArgumentsBeforeItRuns() {
    DataContext context = new DataContext(surface(Map.of("name", "Ada")));
    Map<String, Object> inner =
        call("letters", LOWER_ID, Map.of("value", Map.of("@path", "/name")));

    assertEquals("ADA", context.resolveDynamicValue(call("letters", Map.of("value", inner))));
    assertEquals(List.of("letters", "letters"), calls);
  }

  @Test
  void aCallWithNoArgumentsGetsAnEmptyMap() {
    assertEquals(Map.of(), context().resolveDynamicValue(Map.of("@call", "echo")));
  }

  @Test
  void aCallThatNamesNoCatalogFailsOnASurfaceWithNoDefaultCatalog() {
    DataContext context = new DataContext(surface(null, Map.of()));

    A2uiCatalogError error =
        assertThrows(
            A2uiCatalogError.class,
            () -> context.resolveDynamicValue(call("letters", Map.of("value", "a"))));

    assertTrue(error.getMessage().contains("has no default catalogId"), error.getMessage());
    assertEquals("a", context.resolveDynamicValue(call("letters", LOWER_ID, Map.of("value", "A"))));
  }

  @Test
  void aCallThatNamesACatalogThatTheSurfaceCannotUseFails() {
    A2uiCatalogError error =
        assertThrows(
            A2uiCatalogError.class,
            () -> context().resolveDynamicValue(call("letters", "urn:missing", Map.of())));

    assertTrue(error.getMessage().contains("Catalog not found: urn:missing"), error.getMessage());
  }

  @Test
  void aCallInATemplateRunsInTheCatalogOfTheFunctionThatEvaluatesTheTemplate() {
    // The default catalog writes upper case letters. The template function is in the other one.
    DataContext context = context();
    String template = "${letters(value: 'Ab')} ${letters(value: letters(value: 'Cd'))}";

    assertEquals(
        "ab cd", context.resolveDynamicValue(call("render", LOWER_ID, Map.of("value", template))));
    assertEquals("AB CD", context.resolveDynamicValue(call("render", Map.of("value", template))));
  }

  @Test
  void aCallInATemplateNeedsNoDefaultCatalog() {
    DataContext context = new DataContext(surface(null, Map.of()));

    assertEquals(
        "ab",
        context.resolveDynamicValue(
            call("render", LOWER_ID, Map.of("value", "${letters(value: 'Ab')}"))));
  }

  @Test
  void aCallInTheArgumentsOfACallDoesNotInheritTheCatalog() {
    DataContext context = new DataContext(surface(null, Map.of()));
    Map<String, Object> inner = call("letters", Map.of("value", "a"));

    A2uiCatalogError error =
        assertThrows(
            A2uiCatalogError.class,
            () -> context.resolveDynamicValue(call("letters", LOWER_ID, Map.of("value", inner))));

    assertTrue(error.getMessage().contains("has no default catalogId"), error.getMessage());
    assertEquals(List.of(), calls);
  }

  @Test
  void aTemplateReadsPathsFromTheContextOfTheCall() {
    Map<String, Object> data = Map.of("items", List.of(Map.of("n", 1), Map.of("n", 3)));
    DataContext item = new DataContext(surface(data)).nested("items/1", 1);
    String template = "#${@index(offset: 1)}: ${n} of ${/items/0/n}";

    assertEquals("#2: 3 of 1", item.resolveDynamicValue(call("render", Map.of("value", template))));
  }

  @Test
  void evaluateOutsideAFunctionUsesTheDefaultCatalog() {
    DataContext context = new DataContext(surface(Map.of("name", "Ada")));
    List<Expression> parts = ExpressionParser.parseTemplate("${/name}${letters(value: 'x')}y");

    assertEquals(List.of("Ada", "X", "y"), parts.stream().map(context::evaluate).toList());
  }

  @Test
  void aFunctionThatTheCatalogDoesNotHaveFails() {
    A2uiExpressionError error =
        assertThrows(
            A2uiExpressionError.class, () -> context().resolveDynamicValue(call("nope", Map.of())));

    assertEquals("Unknown function 'nope' in catalog 'urn:test:upper'.", error.getMessage());
  }

  @Test
  void aFunctionWithNoCodeFails() {
    A2uiExpressionError error =
        assertThrows(
            A2uiExpressionError.class,
            () -> context().resolveDynamicValue(call("described", Map.of())));

    assertTrue(error.getMessage().contains("has no implementation"), error.getMessage());
  }

  @Test
  void aFunctionForTheAgentOnlyIsRefusedBeforeItsArgumentsAreResolved() {
    Map<String, Object> args = Map.of("value", call("letters", Map.of("value", "a")));

    A2uiExpressionError error =
        assertThrows(
            A2uiExpressionError.class,
            () -> context().withUserActivation().resolveDynamicValue(call("secret", args)));

    assertEquals(
        "Function 'secret' is agentOnly and cannot be called by renderer code.",
        error.getMessage());
    assertEquals(List.of(), calls);
  }

  @Test
  void aFunctionThatRequiresAUserGestureRunsOnlyWithOne() {
    DataContext context = context();
    Map<String, Object> beep = call("beep", Map.of());

    A2uiExpressionError error =
        assertThrows(A2uiExpressionError.class, () -> context.resolveDynamicValue(beep));
    assertTrue(error.getMessage().contains("requires user activation"), error.getMessage());
    assertEquals(List.of(), calls);

    assertNull(context.withUserActivation().resolveDynamicValue(beep));
    assertNull(context.withUserActivation().nested("items/0", 0).resolveDynamicValue(beep));
    assertEquals(List.of("beep", "beep"), calls);
  }

  @Test
  void aTemplateOfAnActivatedCallCanCallAFunctionThatRequiresAUserGesture() {
    Map<String, Object> render = call("render", Map.of("value", "${beep()}done"));

    assertThrows(A2uiExpressionError.class, () -> context().resolveDynamicValue(render));
    assertEquals("done", context().withUserActivation().resolveDynamicValue(render));
  }

  @Test
  void theIndexFunctionGivesThePositionOfTheListItem() {
    DataContext item = context().nested("items/4", 4);

    assertEquals(4L, item.resolveDynamicValue(Map.of("@call", "@index")));
    assertEquals(5L, item.resolveDynamicValue(call("@index", Map.of("offset", 1))));
    assertEquals(6L, item.resolveDynamicValue(call("@index", Map.of("offset", "2"))));
    assertEquals(4L, item.nested("name").resolveDynamicValue(call("@index", Map.of())));
  }

  @Test
  void theIndexFunctionFailsOutsideAListTemplate() {
    A2uiExpressionError error =
        assertThrows(
            A2uiExpressionError.class,
            () -> context().resolveDynamicValue(Map.of("@call", "@index")));

    assertEquals(
        "The @index function can only be evaluated inside a collection template iteration scope.",
        error.getMessage());
  }

  @Test
  void aSystemCallThatIsNotCorrectFails() {
    DataContext item = context().nested("items/0", 0);

    assertThrows(
        A2uiExpressionError.class,
        () -> item.resolveDynamicValue(call("@index", Map.of("offset", "x"))));
    assertThrows(
        A2uiExpressionError.class, () -> item.resolveDynamicValue(Map.of("@call", "@clock")));
    A2uiExpressionError error =
        assertThrows(
            A2uiExpressionError.class,
            () -> item.resolveDynamicValue(call("@index", UPPER_ID, Map.of())));
    assertEquals("System function '@index' must not name a catalogId.", error.getMessage());
  }

  @Test
  void anArgumentOfTheTypeDynamicBooleanIsReadAsABoolean() {
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("on", call("check", Map.of()));
    args.put("all", List.of("TRUE", 0, Map.of("valid", true)));
    args.put("options", Map.of("strict", 2, "other", "kept"));
    args.put("label", "true");
    args.put("extra", Map.of("valid", true));

    Object received = context().resolveDynamicValue(call("echo", args));

    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("on", false);
    expected.put("all", List.of(true, false, true));
    expected.put("options", Map.of("strict", true, "other", "kept"));
    expected.put("label", "true");
    expected.put("extra", Map.of("valid", true));
    assertEquals(expected, received);
  }

  @Test
  void aNullArgumentOfTheTypeDynamicBooleanIsFalseAndAnAbsentArgumentStaysAbsent() {
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("on", Map.of("@path", "/missing"));

    Object received = context().resolveDynamicValue(call("echo", args));

    assertEquals(Map.of("on", false), received);
  }

  @Test
  void getReadsAValueAtAPathThatStartsAtTheContextPath() {
    SurfaceModel<ComponentApi> surface =
        surface(Map.of("title", "Team", "user", Map.of("name", "Ada")));
    DataContext context = new DataContext(surface, "/user");

    assertEquals("Ada", context.get("name"));
    assertEquals("Team", context.get("/title"));
    assertEquals(Map.of("name", "Ada"), context.get(""));
    assertNull(context.get("missing"));
  }

  @Test
  void aTrackingContextRecordsEachPathThatItAndItsContextsRead() {
    SurfaceModel<ComponentApi> surface =
        surface(Map.of("title", "Team", "user", Map.of("name", "Ada")));
    Set<String> reads = new LinkedHashSet<>();
    DataContext context = DataContext.tracking(surface, "/user", OptionalInt.of(3), reads);

    context.resolveDynamicValue(Map.of("@path", "name"));
    context.resolveDynamicValue(call("render", Map.of("value", "${/title}")));
    context.nested("address").get("city");
    context.withUserActivation().get("/flag");

    assertEquals(OptionalInt.of(3), context.index());
    assertEquals(Set.of("/user/name", "/title", "/user/address/city", "/flag"), reads);
  }

  @Test
  void aFunctionReadsDataThroughATrackingContext() {
    SurfaceModel<ComponentApi> surface = surface(Map.of("count", 2L));
    Set<String> reads = new LinkedHashSet<>();
    DataContext context = DataContext.tracking(surface, "/", OptionalInt.empty(), reads);

    Object letters =
        context.resolveDynamicValue(call("letters", Map.of("value", Map.of("@path", "/count"))));

    assertEquals("2", letters);
    assertEquals(Set.of("/count"), reads);
  }
}
