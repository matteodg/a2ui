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

import com.google.a2ui.core.common.Coercion;
import com.ibm.icu.text.DateFormatSymbols;
import com.ibm.icu.util.ULocale;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The code of the function {@code formatDate}.
 *
 * <p>The format is a Unicode TR35 date pattern. The function knows the fields that the basic
 * catalog lists:
 *
 * <ul>
 *   <li>Year: {@code yyyy}, {@code yy}
 *   <li>Month: {@code MMMM}, {@code MMM}, {@code MM}, {@code M}
 *   <li>Day of the week: {@code EEEE}, {@code E}
 *   <li>Day of the month: {@code dd}, {@code d}
 *   <li>Hour: {@code HH}, {@code H} (0 to 23), {@code hh}, {@code h} (1 to 12)
 *   <li>Minute: {@code mm}. Second: {@code ss}. Period: {@code a} ({@code AM} or {@code PM}).
 * </ul>
 *
 * <p>Text between single quotes is literal, and two single quotes are one single quote. The
 * function copies all other characters. ICU gives the names of the months and of the days for the
 * locale.
 */
final class DateFormatter {
  private static final String DEFAULT_PATTERN = "yyyy-MM-dd";

  /** The format that writes the instant in UTC, as {@code Date.toISOString} of JavaScript does. */
  private static final String ISO_FORMAT = "ISO";

  private static final DateTimeFormatter ISO_INSTANT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);

  /** A quoted literal or a field. A longer field is before each shorter field that starts it. */
  private static final Pattern TOKENS =
      Pattern.compile("''|'(?:[^']|'')+'|yyyy|yy|MMMM|MMM|MM|M|EEEE|E|dd|d|HH|H|hh|h|mm|ss|a");

  private static final int NOON = 12;

  private final DateFormatSymbols symbols;

  /**
   * Makes a formatter.
   *
   * @param locale the locale of the names of the months and of the days
   */
  DateFormatter(ULocale locale) {
    this.symbols = new DateFormatSymbols(locale);
  }

  /**
   * Formats a timestamp.
   *
   * <p>The fields are those that the timestamp writes. The function does not convert a timestamp
   * with an offset or with {@code Z} to a different time zone. Only the format {@code ISO} writes
   * the instant in UTC.
   *
   * @param value an ISO 8601 date or date and time, or a number of milliseconds from 1970-01-01 UTC
   * @param format the pattern. An absent or empty pattern is {@code yyyy-MM-dd}.
   * @return the text, or the empty string if the value is absent or is not a timestamp
   */
  String format(Object value, Object format) {
    Timestamp timestamp = parse(value);
    if (timestamp == null) {
      return "";
    }
    String pattern = Coercion.toText(format);
    if (pattern.isEmpty()) {
      pattern = DEFAULT_PATTERN;
    }
    if (pattern.equals(ISO_FORMAT)) {
      return ISO_INSTANT.format(timestamp.instant().atOffset(ZoneOffset.UTC));
    }
    LocalDateTime time = timestamp.written();
    return TOKENS
        .matcher(pattern)
        .replaceAll(match -> Matcher.quoteReplacement(expand(match.group(), time)));
  }

  private String expand(String token, LocalDateTime time) {
    if (token.equals("''")) {
      return "'";
    }
    if (token.startsWith("'")) {
      return token.substring(1, token.length() - 1).replace("''", "'");
    }
    int hour12 = time.getHour() % NOON == 0 ? NOON : time.getHour() % NOON;
    // ICU gives the days in the sequence of its calendar: index 1 is Sunday.
    int icuDay = time.getDayOfWeek().getValue() % 7 + 1;
    return switch (token) {
      case "yyyy" -> pad(time.getYear(), 4);
      case "yy" -> pad(Math.floorMod(time.getYear(), 100), 2);
      case "MMMM" -> months(DateFormatSymbols.WIDE)[time.getMonthValue() - 1];
      case "MMM" -> months(DateFormatSymbols.ABBREVIATED)[time.getMonthValue() - 1];
      case "MM" -> pad(time.getMonthValue(), 2);
      case "M" -> Integer.toString(time.getMonthValue());
      case "EEEE" -> weekdays(DateFormatSymbols.WIDE)[icuDay];
      case "E" -> weekdays(DateFormatSymbols.ABBREVIATED)[icuDay];
      case "dd" -> pad(time.getDayOfMonth(), 2);
      case "d" -> Integer.toString(time.getDayOfMonth());
      case "HH" -> pad(time.getHour(), 2);
      case "H" -> Integer.toString(time.getHour());
      case "hh" -> pad(hour12, 2);
      case "h" -> Integer.toString(hour12);
      case "mm" -> pad(time.getMinute(), 2);
      case "ss" -> pad(time.getSecond(), 2);
      default -> time.getHour() < NOON ? "AM" : "PM";
    };
  }

  /** Gets the names that a month has as a part of a date. */
  private String[] months(int width) {
    return symbols.getMonths(DateFormatSymbols.FORMAT, width);
  }

  private String[] weekdays(int width) {
    return symbols.getWeekdays(DateFormatSymbols.FORMAT, width);
  }

  private static String pad(int value, int width) {
    String digits = Integer.toString(Math.abs(value));
    return (value < 0 ? "-" : "") + "0".repeat(Math.max(0, width - digits.length())) + digits;
  }

  /**
   * A point in time.
   *
   * @param written the date and the time as the value writes them
   * @param instant the instant. A value with no offset is in UTC.
   */
  private record Timestamp(LocalDateTime written, Instant instant) {}

  /** Reads a timestamp. Returns null if the value is absent or is not a timestamp. */
  private static Timestamp parse(Object value) {
    if (value instanceof Number number) {
      double milliseconds = number.doubleValue();
      if (!Double.isFinite(milliseconds)) {
        return null;
      }
      try {
        Instant instant = Instant.ofEpochMilli((long) milliseconds);
        return new Timestamp(LocalDateTime.ofInstant(instant, ZoneOffset.UTC), instant);
      } catch (DateTimeException e) {
        return null;
      }
    }
    if (!(value instanceof String written)) {
      return null;
    }
    String text = written.strip();
    try {
      OffsetDateTime withOffset = OffsetDateTime.parse(text);
      return new Timestamp(withOffset.toLocalDateTime(), withOffset.toInstant());
    } catch (DateTimeParseException e) {
      // The value has no offset, or it is not a timestamp.
    }
    try {
      LocalDateTime local =
          text.indexOf('T') >= 0 ? LocalDateTime.parse(text) : LocalDate.parse(text).atStartOfDay();
      return new Timestamp(local, local.toInstant(ZoneOffset.UTC));
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
