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

package com.google.a2ui.core.basic_catalog.v1_0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.catalog.Catalog;
import com.google.a2ui.core.catalog.ComponentApi;
import com.google.a2ui.core.catalog.FunctionImplementation;
import com.google.a2ui.core.common.ProtocolVersion;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.resolution.DataContext;
import com.google.a2ui.core.state.SurfaceModel;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The behavior of the functions that the shared suite {@code core/functions.yaml} does not examine.
 */
class BasicFunctionsTest {
  private static final Map<String, Object> VALID = Map.of("valid", true);

  /** Makes a context at the root of a surface whose default catalog is the given catalog. */
  private static DataContext context(
      Catalog<ComponentApi, FunctionImplementation> catalog, Map<String, Object> data) {
    SurfaceModel<ComponentApi> surface =
        new SurfaceModel<>(
            "s", ProtocolVersion.V1_0, catalog, Map.of(catalog.id(), catalog), false, null);
    surface.dataModel().set("/", data);
    return new DataContext(surface);
  }

  private static DataContext context() {
    return context(BasicCatalog.withFunctions(), Map.of());
  }

  /**
   * Makes a call.
   *
   * @param name the name of the function
   * @param namesAndValues the name of each argument, and after it the value of the argument
   */
  private static Map<String, Object> call(String name, Object... namesAndValues) {
    Map<String, Object> args = new LinkedHashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      args.put((String) namesAndValues[i], namesAndValues[i + 1]);
    }
    Map<String, Object> call = new LinkedHashMap<>();
    call.put("@call", name);
    call.put("args", args);
    return call;
  }

  private static Object evaluate(String name, Object... namesAndValues) {
    return context().resolveDynamicValue(call(name, namesAndValues));
  }

  private static Object evaluateIn(Locale locale, String name, Object... namesAndValues) {
    return context(BasicCatalog.withFunctions(locale), Map.of())
        .resolveDynamicValue(call(name, namesAndValues));
  }

  private static Map<String, Object> invalid(String message) {
    return Map.of("valid", false, "message", message);
  }

  @Test
  void theCatalogWithFunctionsHasTheDocumentOfTheApi() {
    Catalog<ComponentApi, FunctionImplementation> runnable = BasicCatalog.withFunctions();

    assertEquals(BasicCatalog.api().toJson(), runnable.toJson());
    assertEquals(BasicCatalog.api().components(), runnable.components());
    assertEquals(
        List.copyOf(BasicCatalog.api().functions().keySet()),
        List.copyOf(runnable.functions().keySet()));
  }

  @Test
  void requiredAcceptsEachValueThatIsNotEmpty() {
    assertEquals(VALID, evaluate("required", "value", 0));
    assertEquals(VALID, evaluate("required", "value", false));
    assertEquals(VALID, evaluate("required", "value", " "));
    assertEquals(VALID, evaluate("required", "value", Map.of()));
    assertEquals(invalid("This field is required."), evaluate("required", "value", ""));
    assertEquals(invalid("This field is required."), evaluate("required", "value", null));
    assertEquals(invalid("This field is required."), evaluate("required", "value", List.of()));
    assertEquals(invalid("This field is required."), evaluate("required"));
  }

  @Test
  void lengthTellsWhichLimitTheValueDoesNotObey() {
    assertEquals(VALID, evaluate("length", "value", "abc", "min", 3, "max", 3));
    assertEquals(invalid("Minimum length is 3."), evaluate("length", "value", "ab", "min", 3));
    assertEquals(invalid("Maximum length is 2."), evaluate("length", "value", "abc", "max", 2.0));
    assertEquals(
        invalid("Maximum length is 1."), evaluate("length", "value", List.of(1, 2), "max", 1));
    assertEquals(invalid("Minimum length is 1."), evaluate("length", "value", 12345, "min", 1));
    assertEquals(VALID, evaluate("length", "value", "abc"));
  }

  @Test
  void numericTellsWhichLimitTheValueDoesNotObey() {
    assertEquals(VALID, evaluate("numeric", "value", 10, "min", 10, "max", 10));
    assertEquals(VALID, evaluate("numeric", "value", " 2.5 ", "min", 2));
    assertEquals(invalid("Minimum value is 0."), evaluate("numeric", "value", -1, "min", 0));
    assertEquals(invalid("Maximum value is 10."), evaluate("numeric", "value", 11, "max", 10));
    assertEquals(invalid("Maximum value is 1.5."), evaluate("numeric", "value", "2", "max", 1.5));
    assertEquals(invalid("Value must be a valid number."), evaluate("numeric", "value", "abc"));
    assertEquals(invalid("Value must be a valid number."), evaluate("numeric", "value", null));
    assertEquals(
        invalid("Value must be a valid number."), evaluate("numeric", "value", List.of(1)));
  }

  @Test
  void emailGivesACodeAndASeverityForAValueThatIsNotAnAddress() {
    Map<String, Object> invalid =
        Map.of(
            "valid",
            false,
            "code",
            "INVALID_EMAIL",
            "message",
            "Please enter a valid email address (e.g. user@example.com).",
            "severity",
            "error");

    assertEquals(VALID, evaluate("email", "value", "ada.lovelace+work@example.co.uk"));
    for (Object value : Arrays.asList("ada@example", "@example.com", "ada @example.com", 5, null)) {
      assertEquals(invalid, evaluate("email", "value", value), String.valueOf(value));
    }
    assertEquals(invalid, evaluate("email", "value", "a".repeat(320) + "@example.com"));
  }

  @Test
  void theLogicFunctionsReadTheResultOfAValidationCallByItsValidity() {
    Map<String, Object> present = call("required", "value", "x");
    Map<String, Object> absent = call("required", "value", "");

    assertEquals(true, evaluate("and", "values", List.of(present, present)));
    assertEquals(false, evaluate("and", "values", List.of(present, absent)));
    assertEquals(true, evaluate("or", "values", List.of(absent, present)));
    assertEquals(false, evaluate("or", "values", List.of(absent, absent)));
    assertEquals(true, evaluate("not", "value", absent));
    assertEquals(false, evaluate("not", "value", call("not", "value", absent)));
  }

  @Test
  void theLogicFunctionsCoerceTheOperandsOfADirectCall() {
    Map<String, FunctionImplementation> functions = BasicCatalog.withFunctions().functions();
    List<Object> mixed = List.of("TRUE", 2, Map.of("valid", true));

    assertEquals(true, functions.get("and").execute(Map.of("values", mixed), context()));
    assertEquals(
        false, functions.get("or").execute(Map.of("values", List.of("yes", 0)), context()));
    assertEquals(true, functions.get("not").execute(Map.of(), context()));
  }

  @Test
  void andAndOrRequireTwoValues() {
    for (String name : List.of("and", "or")) {
      assertThrows(A2uiExpressionError.class, () -> evaluate(name, "values", List.of(true)));
      assertThrows(A2uiExpressionError.class, () -> evaluate(name, "values", true));
      A2uiExpressionError error = assertThrows(A2uiExpressionError.class, () -> evaluate(name));
      assertEquals("The '" + name + "' function requires at least 2 values.", error.getMessage());
    }
  }

  @Test
  void formatStringReadsPathsFromThePositionOfTheCall() {
    Map<String, Object> data =
        Map.of("title", "List", "items", List.of(Map.of("n", 1), Map.of("n", 3)));
    DataContext item = context(BasicCatalog.withFunctions(), data).nested("items/1", 1);

    Object text =
        item.resolveDynamicValue(
            call("formatString", "value", "${/title} #${@index(offset: 1)}: ${n}"));

    assertEquals("List #2: 3", text);
  }

  @Test
  void formatStringWritesEachValueAsText() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("whole", 3.0);
    data.put("fraction", 2.5);
    data.put("flag", true);
    data.put("object", Map.of("a", List.of(1, 2.5)));
    DataContext context = context(BasicCatalog.withFunctions(), data);
    String template = "${/whole}|${/fraction}|${/flag}|${/object}|${/missing}|${null}|\\${x}";

    Object text = context.resolveDynamicValue(call("formatString", "value", template));

    assertEquals("3|2.5|true|{\"a\":[1,2.5]}|||${x}", text);
    assertEquals("5", evaluate("formatString", "value", 5));
    assertEquals("", evaluate("formatString", "value", null));
  }

  @Test
  void formatStringRunsTheCallsOfItsTemplate() {
    String template = "${not(value: required(value: ''))} ${not(value: not(value: 'true'))}";

    assertEquals("true true", evaluate("formatString", "value", template));
  }

  @Test
  void formatStringFailsForATemplateThatIsNotCorrect() {
    assertThrows(A2uiExpressionError.class, () -> evaluate("formatString", "value", "${"));
    assertThrows(A2uiExpressionError.class, () -> evaluate("formatString", "value", "${nope()}"));
  }

  @Test
  void formatNumberShowsAtMostThreeFractionDigitsByDefault() {
    assertEquals("2.5", evaluate("formatNumber", "value", 2.5));
    assertEquals("1,234.568", evaluate("formatNumber", "value", "1234.5678"));
    assertEquals("1,234", evaluate("formatNumber", "value", 1234L));
    assertEquals("-0.5", evaluate("formatNumber", "value", -0.5));
  }

  @Test
  void formatNumberShowsTheRequestedFractionDigitsAndRoundsAHalfAwayFromZero() {
    assertEquals("0.13", evaluate("formatNumber", "value", 0.125, "decimals", 2));
    assertEquals("-0.13", evaluate("formatNumber", "value", -0.125, "decimals", 2));
    assertEquals("3", evaluate("formatNumber", "value", 2.5, "decimals", 0));
    assertEquals("2.50", evaluate("formatNumber", "value", 2.5, "decimals", "2"));
    assertEquals("3", evaluate("formatNumber", "value", 2.5, "decimals", -1));
    assertEquals("2.5", evaluate("formatNumber", "value", 2.5, "decimals", null));
  }

  @Test
  void formatNumberGroupsTheDigitsUnlessGroupingIsFalse() {
    assertEquals("1,234,567.891", evaluate("formatNumber", "value", 1234567.891));
    assertEquals("1234567.891", evaluate("formatNumber", "value", 1234567.891, "grouping", false));
    assertEquals("1,234", evaluate("formatNumber", "value", 1234, "grouping", "true"));
    assertEquals("1234", evaluate("formatNumber", "value", 1234, "grouping", "no"));
    assertEquals("1234", evaluate("formatNumber", "value", 1234, "grouping", null));
  }

  @Test
  void formatNumberGivesAnEmptyStringForAValueThatIsNotANumber() {
    assertEquals("", evaluate("formatNumber", "value", "abc"));
    assertEquals("", evaluate("formatNumber", "value", null));
    assertEquals("", evaluate("formatCurrency", "value", "abc", "currency", "USD"));
  }

  @Test
  void theNumberFunctionsFollowTheLocale() {
    assertEquals("1.234,5", evaluateIn(Locale.GERMANY, "formatNumber", "value", 1234.5));
    assertEquals("12,34,567", evaluateIn(Locale.of("en", "IN"), "formatNumber", "value", 1234567));
    assertEquals(
        "1.234,50 €",
        evaluateIn(Locale.GERMANY, "formatCurrency", "value", 1234.5, "currency", "EUR"));
  }

  @Test
  void formatCurrencyShowsTheFractionDigitsOfTheCurrencyByDefault() {
    assertEquals("$1,234.50", evaluate("formatCurrency", "value", 1234.5, "currency", "USD"));
    assertEquals("$1,234.50", evaluate("formatCurrency", "value", 1234.5, "currency", "usd"));
    assertEquals("¥1,235", evaluate("formatCurrency", "value", 1234.5, "currency", "JPY"));
    assertEquals("-$5.00", evaluate("formatCurrency", "value", -5, "currency", "USD"));
    assertEquals(
        "$1235",
        evaluate(
            "formatCurrency",
            "value",
            1234.5,
            "currency",
            "USD",
            "decimals",
            0,
            "grouping",
            false));
  }

  @Test
  void formatCurrencyFailsForACodeThatIsNotACurrencyCode() {
    A2uiExpressionError error =
        assertThrows(
            A2uiExpressionError.class,
            () -> evaluate("formatCurrency", "value", 1, "currency", "??"));

    assertEquals("formatCurrency: '??' is not an ISO 4217 currency code.", error.getMessage());
    assertThrows(A2uiExpressionError.class, () -> evaluate("formatCurrency", "value", 1));
  }

  private static Object formatDate(Object value, Object format) {
    return evaluate("formatDate", "value", value, "format", format);
  }

  @Test
  void formatDateWritesEachFieldOfThePattern() {
    assertEquals(
        "2026 26 September Sep 09 9 Friday Fri 04 4",
        formatDate("2026-09-04T21:05:09", "yyyy yy MMMM MMM MM M EEEE E dd d"));
    assertEquals("21 21 09 9 05 09 PM", formatDate("2026-09-04T21:05:09", "HH H hh h mm ss a"));
    assertEquals("00 0 12 12 07 08 AM", formatDate("2026-01-05T00:07:08Z", "HH H hh h mm ss a"));
    assertEquals("12 PM, Sunday", formatDate("2026-09-06T12:00", "h a, EEEE"));
    assertEquals("0999 99", formatDate("0999-01-01", "yyyy yy"));
  }

  @Test
  void formatDateCopiesQuotedTextAndTheCharactersThatAreNotFields() {
    assertEquals("9 o'clock", formatDate("2026-09-04T21:05:09", "h 'o''clock'"));
    assertEquals("day 4", formatDate("2026-09-04", "'day' d"));
    assertEquals("2026/09/04 @ 21:05", formatDate("2026-09-04T21:05:09", "yyyy/MM/dd @ HH:mm"));
    assertEquals("it's 21", formatDate("2026-09-04T21:05:09", "'it''s' HH"));
  }

  @Test
  void formatDateKeepsTheFieldsThatTheValueWrites() {
    String value = "2026-09-04T21:05:09+02:00";

    assertEquals("Sep 4, 26 at 9:05 PM", formatDate(value, "MMM d, yy 'at' h:mm a"));
    assertEquals("2026-09-04T19:05:09.000Z", formatDate(value, "ISO"));
    assertEquals("2026-09-04T21:05:09.120Z", formatDate("2026-09-04T21:05:09.1206", "ISO"));
    assertEquals("2026-09-04T00:00:00.000Z", formatDate("2026-09-04", "ISO"));
  }

  @Test
  void formatDateUsesTheDefaultPatternAndAcceptsMilliseconds() {
    assertEquals("1970-01-02", formatDate(86_400_000, null));
    assertEquals("2026-09-04", formatDate(" 2026-09-04T21:05:09Z ", ""));
    assertEquals("2026-09-04", evaluate("formatDate", "value", "2026-09-04"));
  }

  @Test
  void formatDateGivesAnEmptyStringForAValueThatIsNotATimestamp() {
    for (Object value : Arrays.asList(null, "", "yesterday", "2026-02-30", "09/04/2026", true)) {
      assertEquals("", formatDate(value, "yyyy"), String.valueOf(value));
    }
  }

  @Test
  void formatDateWritesTheNamesOfTheLocale() {
    assertEquals(
        "Freitag, 4. September",
        evaluateIn(Locale.GERMANY, "formatDate", "value", "2026-09-04", "format", "EEEE, d. MMMM"));
    // A month in a date has the form that the language uses after a day.
    assertEquals(
        "4 сентября",
        evaluateIn(Locale.of("ru"), "formatDate", "value", "2026-09-04", "format", "d MMMM"));
  }

  @Test
  void pluralizeSelectsTheCategoryOfTheLocale() {
    assertEquals("apple", evaluate("pluralize", "value", 1, "one", "apple", "other", "apples"));
    assertEquals("apples", evaluate("pluralize", "value", 0, "one", "apple", "other", "apples"));
    assertEquals("apples", evaluate("pluralize", "value", 1.5, "one", "apple", "other", "apples"));
    assertEquals("apple", evaluate("pluralize", "value", "1", "one", "apple", "other", "apples"));

    Locale polish = Locale.of("pl");
    Object[] forms = {"one", "plik", "few", "pliki", "many", "plików", "other", "pliku"};
    assertEquals("pliki", evaluateIn(polish, "pluralize", with(forms, "value", 3)));
    assertEquals("plików", evaluateIn(polish, "pluralize", with(forms, "value", 5)));
    assertEquals("pliku", evaluateIn(polish, "pluralize", with(forms, "value", 1.5)));
  }

  @Test
  void pluralizeUsesAnExactTextForZeroOneAndTwo() {
    Object[] forms = {"zero", "none", "two", "a pair", "other", "some"};

    assertEquals("none", evaluate("pluralize", with(forms, "value", 0)));
    assertEquals("a pair", evaluate("pluralize", with(forms, "value", 2)));
    assertEquals("some", evaluate("pluralize", with(forms, "value", 1)));
    assertEquals("some", evaluate("pluralize", with(forms, "value", 3)));
  }

  @Test
  void pluralizeUsesTheTextOtherIfTheTextOfTheCategoryIsAbsent() {
    assertEquals("apples", evaluate("pluralize", "value", 1, "other", "apples"));
    assertEquals("apples", evaluate("pluralize", "value", 1, "one", null, "other", "apples"));
    assertEquals(
        "apples", evaluate("pluralize", "value", "many", "one", "apple", "other", "apples"));
    assertEquals("apples", evaluate("pluralize", "one", "apple", "other", "apples"));
    assertEquals("", evaluate("pluralize", "value", 5, "one", "apple"));
    assertEquals("7", evaluate("pluralize", "value", 5, "other", 7));
  }

  /** Makes a list of argument names and values that starts with the given name and value. */
  private static Object[] with(Object[] namesAndValues, String name, Object value) {
    Object[] all = new Object[namesAndValues.length + 2];
    all[0] = name;
    all[1] = value;
    System.arraycopy(namesAndValues, 0, all, 2, namesAndValues.length);
    return all;
  }

  @Test
  void aFunctionThatTheSdkDoesNotImplementFailsWhenItRuns() {
    A2uiExpressionError error =
        assertThrows(
            A2uiExpressionError.class, () -> evaluate("regex", "value", "a", "pattern", "a"));

    assertTrue(error.getMessage().contains("'regex'"), error.getMessage());
    assertTrue(error.getMessage().contains("not implemented"), error.getMessage());
  }
}
