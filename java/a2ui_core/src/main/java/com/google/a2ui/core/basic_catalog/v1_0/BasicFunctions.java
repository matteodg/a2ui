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

import com.google.a2ui.core.catalog.FunctionBody;
import com.google.a2ui.core.common.A2uiRegex;
import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.exceptions.A2uiExpressionError;
import com.google.a2ui.core.expressions.Expression;
import com.google.a2ui.core.expressions.ExpressionParser;
import com.google.a2ui.core.resolution.DataContext;
import com.ibm.icu.number.NumberFormatter;
import com.ibm.icu.number.Precision;
import com.ibm.icu.number.UnlocalizedNumberFormatter;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.util.Currency;
import com.ibm.icu.util.ULocale;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** The code of the functions of the basic catalog. */
final class BasicFunctions {
  private static final Pattern EMAIL =
      Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

  /**
   * The maximum length of an email address: 64 characters before the {@code @} and 255 after it. A
   * longer value is not an address, and the function does not examine it with the pattern.
   */
  private static final int MAX_EMAIL_LENGTH = 320;

  /** The number of fraction digits that {@code formatNumber} shows at most by default. */
  private static final int DEFAULT_MAX_FRACTION = 3;

  /** The largest number of fraction digits that a call can request. */
  private static final int MAX_DECIMALS = 100;

  private final ULocale locale;
  private final DateFormatter dates;
  private final PluralRules plurals;
  private final UrlOpener urlOpener;

  /**
   * Makes the functions.
   *
   * @param locale the locale of the text that the format functions write
   * @param urlOpener the object that opens a URL for {@code openUrl}, or null if there is none
   */
  BasicFunctions(Locale locale, UrlOpener urlOpener) {
    this.locale = ULocale.forLocale(locale);
    this.dates = new DateFormatter(this.locale);
    this.plurals = PluralRules.forLocale(this.locale);
    this.urlOpener = urlOpener;
  }

  /**
   * Gets the code of each function that this SDK implements.
   *
   * @return the code by function name
   */
  Map<String, FunctionBody> bodies() {
    Map<String, FunctionBody> bodies = new LinkedHashMap<>();
    bodies.put("required", (args, context) -> required(args.get("value")));
    bodies.put("regex", (args, context) -> regex(args.get("value"), args.get("pattern")));
    bodies.put(
        "length", (args, context) -> length(args.get("value"), args.get("min"), args.get("max")));
    bodies.put(
        "numeric", (args, context) -> numeric(args.get("value"), args.get("min"), args.get("max")));
    bodies.put("email", (args, context) -> email(args.get("value")));
    bodies.put(
        "and",
        (args, context) -> operands("and", args.get("values")).allMatch(Coercion::toBoolean));
    bodies.put(
        "or", (args, context) -> operands("or", args.get("values")).anyMatch(Coercion::toBoolean));
    bodies.put("not", (args, context) -> !Coercion.toBoolean(args.get("value")));
    bodies.put("formatString", (args, context) -> formatString(args.get("value"), context));
    bodies.put("formatNumber", (args, context) -> formatNumber(args, null));
    bodies.put(
        "formatCurrency", (args, context) -> formatNumber(args, currency(args.get("currency"))));
    bodies.put(
        "formatDate", (args, context) -> dates.format(args.get("value"), args.get("format")));
    bodies.put("pluralize", (args, context) -> pluralize(args));
    bodies.put("openUrl", (args, context) -> openUrl(args.get("url")));
    return bodies;
  }

  /**
   * Gives an absolute {@code http} or {@code https} URL to the {@link UrlOpener}. Each other scheme
   * is an error, for example {@code javascript}. A URL that is absent or empty opens nothing.
   *
   * <p>The data context makes sure that the call is the result of a user gesture.
   *
   * @return null, because the function returns nothing
   */
  private Object openUrl(Object url) {
    String text = url instanceof String written ? written.strip() : "";
    if (text.isEmpty()) {
      return null;
    }
    URI uri;
    try {
      uri = new URI(text);
    } catch (URISyntaxException e) {
      throw new A2uiExpressionError("openUrl: '" + text + "' is not a valid URL.", e);
    }
    if (uri.getScheme() == null) {
      throw new A2uiExpressionError("openUrl: the URL must be absolute, but got '" + text + "'.");
    }
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new A2uiExpressionError(
          "openUrl: the scheme '"
              + scheme
              + ":' is not allowed. Only http and https URLs are opened.");
    }
    if (uri.getRawAuthority() == null) {
      throw new A2uiExpressionError("openUrl: the URL '" + text + "' has no host.");
    }
    if (urlOpener == null) {
      throw new A2uiExpressionError(
          "openUrl has nothing to open URLs with. Pass a UrlOpener when creating the basic"
              + " catalog.");
    }
    urlOpener.open(uri);
    return null;
  }

  /**
   * Selects the text for the argument {@code value}.
   *
   * <p>If the value is exactly 0, 1 or 2 and the call gives the text {@code zero}, {@code one} or
   * {@code two}, that text is the result. If not, the result is the text of the plural category of
   * the value in the locale. If the call does not give that text, the result is the text {@code
   * other}.
   */
  private String pluralize(Map<String, Object> args) {
    OptionalDouble value = Coercion.parseNumber(args.get("value"));
    String category = "other";
    if (value.isPresent()) {
      double number = value.getAsDouble();
      if (number == 0 && args.containsKey("zero")) {
        category = "zero";
      } else if (number == 1 && args.containsKey("one")) {
        category = "one";
      } else if (number == 2 && args.containsKey("two")) {
        category = "two";
      } else {
        category = plurals.select(number);
      }
    }
    Object text = args.get(category);
    return Coercion.toText(text != null ? text : args.get("other"));
  }

  // Number formats. ICU gives the separators, the digits and the currency symbols of the locale.

  /**
   * Writes the argument {@code value} as a number, or as an amount of a currency.
   *
   * <p>The result is the empty string if the value is not a number. The function rounds a half away
   * from zero.
   *
   * <ul>
   *   <li>With {@code decimals}, the text has that number of fraction digits.
   *   <li>Without {@code decimals}, a number has at most three fraction digits, and an amount has
   *       the number of fraction digits of its currency.
   *   <li>The text has the grouping separators of the locale, unless {@code grouping} is false
   *       after the rules of {@link Coercion#toBoolean(Object)}.
   * </ul>
   *
   * @param currency the currency of the amount, or null for a number
   */
  private String formatNumber(Map<String, Object> args, Currency currency) {
    OptionalDouble number = Coercion.parseNumber(args.get("value"));
    if (number.isEmpty()) {
      return "";
    }
    OptionalDouble decimals = Coercion.parseNumber(args.get("decimals"));
    boolean grouped = !args.containsKey("grouping") || Coercion.toBoolean(args.get("grouping"));
    Precision precision;
    if (decimals.isPresent()) {
      int digits = (int) Math.clamp(decimals.getAsDouble(), 0, MAX_DECIMALS);
      precision = Precision.fixedFraction(digits);
    } else {
      precision =
          currency == null
              ? Precision.maxFraction(DEFAULT_MAX_FRACTION)
              : Precision.currency(Currency.CurrencyUsage.STANDARD);
    }
    UnlocalizedNumberFormatter formatter =
        NumberFormatter.with()
            .roundingMode(RoundingMode.HALF_UP)
            .grouping(
                grouped
                    ? NumberFormatter.GroupingStrategy.AUTO
                    : NumberFormatter.GroupingStrategy.OFF)
            .precision(precision);
    if (currency != null) {
      formatter = formatter.unit(currency);
    }
    return formatter.locale(locale).format(number.getAsDouble()).toString();
  }

  private static Currency currency(Object code) {
    String text = Coercion.toText(code).strip().toUpperCase(Locale.ROOT);
    try {
      return Currency.getInstance(text);
    } catch (IllegalArgumentException e) {
      throw new A2uiExpressionError(
          "formatCurrency: '" + text + "' is not an ISO 4217 currency code.", e);
    }
  }

  /**
   * Replaces each <code>${...}</code> expression of a template with its value as text.
   *
   * <p>The context of the call evaluates each expression. Thus a relative path starts at the
   * position of the call, and a function call in the template runs in the catalog of this function.
   */
  private static String formatString(Object template, DataContext context) {
    StringBuilder text = new StringBuilder();
    for (Expression part : ExpressionParser.parseTemplate(Coercion.toText(template))) {
      text.append(Coercion.toText(context.evaluate(part)));
    }
    return text.toString();
  }

  // Logic. Each operand is a boolean after the rules of `Coercion.toBoolean`. Thus the validation
  // result of a validation function is an operand.

  private static Stream<?> operands(String name, Object values) {
    if (values instanceof Collection<?> list && list.size() >= 2) {
      return list.stream();
    }
    throw new A2uiExpressionError("The '" + name + "' function requires at least 2 values.");
  }

  // Validation. Each function returns a validation result: an object with a boolean `valid`, and
  // with a `message` if the value is not valid.

  private static Map<String, Object> valid() {
    return Map.of("valid", true);
  }

  private static Map<String, Object> invalid(String message) {
    return Map.of("valid", false, "message", message);
  }

  /** An empty object is a value. Thus it is valid. */
  private static Map<String, Object> required(Object value) {
    boolean absent =
        value == null
            || "".equals(value)
            || (value instanceof Collection<?> list && list.isEmpty());
    return absent ? invalid("This field is required.") : valid();
  }

  /**
   * Tells if a part of the value matches a pattern in the A2UI regex dialect.
   *
   * <p>The function examines the pattern before the value. Thus a pattern that is not safe always
   * fails, also for a value that cannot match.
   */
  private static Map<String, Object> regex(Object value, Object pattern) {
    A2uiRegex compiled = A2uiRegex.compile(Coercion.toText(pattern));
    return compiled.test(Coercion.toText(value))
        ? valid()
        : invalid("Value does not match required pattern.");
  }

  /**
   * Examines the number of UTF-16 code units of a string, or the number of items of a list. Each
   * other value has the length 0.
   */
  private static Map<String, Object> length(Object value, Object min, Object max) {
    int length =
        switch (value) {
          case String text -> text.length();
          case Collection<?> list -> list.size();
          case null, default -> 0;
        };
    OptionalDouble lower = Coercion.parseNumber(min);
    OptionalDouble upper = Coercion.parseNumber(max);
    if (lower.isPresent() && length < lower.getAsDouble()) {
      return invalid("Minimum length is " + Coercion.toText(lower.getAsDouble()) + ".");
    }
    if (upper.isPresent() && length > upper.getAsDouble()) {
      return invalid("Maximum length is " + Coercion.toText(upper.getAsDouble()) + ".");
    }
    return valid();
  }

  private static Map<String, Object> numeric(Object value, Object min, Object max) {
    OptionalDouble number = Coercion.parseNumber(value);
    if (number.isEmpty()) {
      return invalid("Value must be a valid number.");
    }
    OptionalDouble lower = Coercion.parseNumber(min);
    OptionalDouble upper = Coercion.parseNumber(max);
    if (lower.isPresent() && number.getAsDouble() < lower.getAsDouble()) {
      return invalid("Minimum value is " + Coercion.toText(lower.getAsDouble()) + ".");
    }
    if (upper.isPresent() && number.getAsDouble() > upper.getAsDouble()) {
      return invalid("Maximum value is " + Coercion.toText(upper.getAsDouble()) + ".");
    }
    return valid();
  }

  private static Map<String, Object> email(Object value) {
    if (value instanceof String text
        && text.length() <= MAX_EMAIL_LENGTH
        && EMAIL.matcher(text).matches()) {
      return valid();
    }
    return Map.of(
        "valid",
        false,
        "code",
        "INVALID_EMAIL",
        "message",
        "Please enter a valid email address (e.g. user@example.com).",
        "severity",
        "error");
  }
}
