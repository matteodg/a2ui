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

package com.google.a2ui.core.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.a2ui.core.exceptions.A2uiCatalogError;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class JsonSchemaEngineTest {
  private final JsonSchemaEngine engine =
      new JsonSchemaEngine(
          Json.asObject(
              Json.parse(
                  """
                  {
                    "$id": "https://example.com/the-name-in-the-document.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "components": {
                      "Card": {
                        "type": "object",
                        "properties": {
                          "title": {"$ref": "#/$defs/Title"},
                          "count": {"type": "integer"},
                          "tags": {"type": "array", "items": {"type": "string"}}
                        },
                        "required": ["title"],
                        "unevaluatedProperties": false
                      }
                    },
                    "$defs": {
                      "Title": {"type": "string", "minLength": 1},
                      "a/b": {"const": 1},
                      "Identifier": {
                        "type": "string",
                        "pattern": "^[\\\\p{XID_Start}_][\\\\p{XID_Continue}]*$"
                      },
                      "Hex": {"type": "string", "pattern": "[0-9a-f]{2}"},
                      "Date": {"format": "date"},
                      "Time": {"format": "time"},
                      "DateTime": {"format": "date-time"},
                      "Limit": {
                        "if": {"type": "string"},
                        "then": {
                          "oneOf": [
                            {"format": "date"}, {"format": "time"}, {"format": "date-time"}
                          ]
                        }
                      },
                      "MorningDate": {"format": "date", "pattern": "^2025"},
                      "Link": {"type": "string", "format": "uri"},
                      "Email": {"type": "string", "format": "email"},
                      "FormatAsData": {"const": {"format": "date"}},
                      "FormatAsName": {
                        "type": "object",
                        "properties": {"format": {"type": "string"}}
                      }
                    }
                  }
                  """)));

  @Test
  void aValidValueHasNoErrors() {
    Object card = Json.parse("{\"title\": \"Hello\", \"count\": 2, \"tags\": [\"a\"]}");

    assertEquals(List.of(), engine.errors("/components/Card", card));
    assertEquals(List.of(), engine.errors("/$defs/Title", "Hello"));
    assertEquals(List.of(), engine.errors("/$defs/a~1b", 1L));
  }

  @Test
  void eachProblemGivesOneMessageWithItsLocation() {
    Object card = Json.parse("{\"title\": \"\", \"tags\": [\"a\", 2], \"extra\": null}");

    List<String> errors = engine.errors("/components/Card", card);

    assertEquals(3, errors.size(), errors.toString());
    assertTrue(errors.stream().anyMatch(error -> error.startsWith("/title: ")), errors.toString());
    assertTrue(errors.stream().anyMatch(error -> error.startsWith("/tags/1: ")), errors.toString());
    assertTrue(errors.stream().anyMatch(error -> error.contains("extra")), errors.toString());
  }

  @Test
  void aProblemOfTheValueItselfHasNoLocation() {
    List<String> errors = engine.errors("/$defs/Title", 5L);

    assertEquals(1, errors.size(), errors.toString());
    assertFalse(errors.get(0).startsWith("/"), errors.toString());
    assertFalse(errors.get(0).startsWith("$"), errors.toString());
  }

  @Test
  void aProblemHasTheLocationAndTheTypeOfItsCause() {
    Object card = Json.parse("{\"count\": \"two\", \"tags\": [\"a\", 2], \"ex/tra\": null}");

    List<Problem> problems = engine.problems("/components/Card", card);

    assertEquals(4, problems.size(), problems.toString());
    assertEquals(Problem.MISSING_FIELD, codeAt(problems, "/title"));
    assertEquals(Problem.TYPE_MISMATCH, codeAt(problems, "/count"));
    assertEquals(Problem.TYPE_MISMATCH, codeAt(problems, "/tags/1"));
    // The path of a property that the schema does not permit is an escaped JSON Pointer.
    assertEquals(Problem.INVALID_VALUE, codeAt(problems, "/ex~1tra"));
    assertEquals(
        List.of("/ex~1tra"),
        problems.stream().filter(Problem::unevaluated).map(Problem::path).toList());
  }

  @Test
  void aProblemOfAConstraintIsAnInvalidValue() {
    List<Problem> problems = engine.problems("/$defs/Title", "");

    assertEquals(1, problems.size(), problems.toString());
    assertEquals("", problems.get(0).path());
    assertEquals(Problem.INVALID_VALUE, problems.get(0).code());
    assertFalse(problems.get(0).unevaluated());
  }

  private static String codeAt(List<Problem> problems, String path) {
    return problems.stream()
        .filter(problem -> problem.path().equals(path))
        .map(Problem::code)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No problem at " + path + " in " + problems));
  }

  @Test
  void aNumberWithNoFractionIsAnInteger() {
    assertEquals(List.of(), engine.errors("/components/Card/properties/count", 3.0));
    assertEquals(1, engine.errors("/components/Card/properties/count", 3.5).size());
    assertEquals(List.of(), engine.errors("/$defs/a~1b", 1.0));
  }

  @Test
  void aPatternKnowsTheUnicodeIdentifierProperties() {
    assertEquals(List.of(), engine.errors("/$defs/Identifier", "vendor_tag"));
    assertEquals(List.of(), engine.errors("/$defs/Identifier", "_x1"));
    assertEquals(List.of(), engine.errors("/$defs/Identifier", "größe"));
    // U+20BB7 is not in the Basic Multilingual Plane.
    assertEquals(List.of(), engine.errors("/$defs/Identifier", "𠮷"));
    assertEquals(1, engine.errors("/$defs/Identifier", "has-dash").size());
    assertEquals(1, engine.errors("/$defs/Identifier", "1st").size());
    assertEquals(1, engine.errors("/$defs/Identifier", "").size());
  }

  @Test
  void aPatternMatchesAPartOfTheValue() {
    assertEquals(List.of(), engine.errors("/$defs/Hex", "xx7fxx"));
    assertEquals(1, engine.errors("/$defs/Hex", "xyz").size());
  }

  @Test
  void theFormatsOfADateAndOfATimeAreAsserted() {
    assertEquals(List.of(), engine.errors("/$defs/Date", "2025-01-31"));
    assertEquals(1, engine.errors("/$defs/Date", "2025-13-01").size());
    assertEquals(1, engine.errors("/$defs/Date", "2025-01-01T08:00:00Z").size());
    assertEquals(1, engine.errors("/$defs/Date", "1 January").size());

    for (String time :
        List.of("18:30", "18:30:00", "18:30:00.250", "18:30:00Z", "08:00:00+01:00")) {
      assertEquals(List.of(), engine.errors("/$defs/Time", time), time);
    }
    assertEquals(1, engine.errors("/$defs/Time", "24:00:00").size());
    assertEquals(1, engine.errors("/$defs/Time", "2025-01-31").size());

    for (String dateTime :
        List.of(
            "2025-01-01T08:00:00Z",
            "2025-01-01T08:00:00",
            "2025-01-01T08:00",
            "2025-01-01 08:00:00.5-05:00")) {
      assertEquals(List.of(), engine.errors("/$defs/DateTime", dateTime), dateTime);
    }
    assertEquals(1, engine.errors("/$defs/DateTime", "2025-01-01").size());
    assertEquals(1, engine.errors("/$defs/DateTime", "2025-01-01T25:00:00Z").size());
  }

  @Test
  void aFormatAppliesOnlyToAString() {
    assertEquals(List.of(), engine.errors("/$defs/Date", 5L));
    assertEquals(List.of(), engine.errors("/$defs/Limit", Map.of("@path", "/min")));
  }

  @Test
  void aTextAgreesWithOneFormatOfAChoiceOfFormats() {
    for (String limit : List.of("2025-01-01", "18:30:00", "2025-01-01T08:00:00Z")) {
      assertEquals(List.of(), engine.errors("/$defs/Limit", limit), limit);
    }
    assertFalse(engine.errors("/$defs/Limit", "tomorrow").isEmpty());
    assertFalse(engine.errors("/$defs/Limit", "").isEmpty());
  }

  @Test
  void aFormatKeepsThePatternThatItsSchemaHas() {
    assertEquals(List.of(), engine.errors("/$defs/MorningDate", "2025-06-01"));
    assertEquals(1, engine.errors("/$defs/MorningDate", "2024-06-01").size());
    assertEquals(1, engine.errors("/$defs/MorningDate", "2025").size());
  }

  @Test
  void theFormatOfAUriIsAnAbsoluteUriWithAScheme() {
    for (String uri :
        List.of(
            "https://example.com/a?b=c#d",
            "mailto:someone@example.com",
            "tel:+15550100",
            "urn:isbn:0451450523",
            "custom+scheme-1.0:",
            "HTTP://EXAMPLE.COM")) {
      assertEquals(List.of(), engine.errors("/$defs/Link", uri), uri);
    }
    for (String text :
        List.of(
            "",
            "not a link",
            "example.com/page",
            "/relative/path",
            "//example.com/page",
            "1http://example.com",
            "https://example.com/a b",
            "https://example.com/\n",
            "https://example.com/a\u00a0b",
            " https://example.com")) {
      assertEquals(1, engine.errors("/$defs/Link", text).size(), text);
    }
    assertEquals(Problem.INVALID_VALUE, engine.problems("/$defs/Link", "no scheme").get(0).code());
  }

  @Test
  void theOtherFormatsAndTheWordFormatInDataAreNotAsserted() {
    assertEquals(List.of(), engine.errors("/$defs/Email", "not an address"));
    assertEquals(List.of(), engine.errors("/$defs/FormatAsData", Map.of("format", "date")));
    assertEquals(List.of(), engine.errors("/$defs/FormatAsName", Map.of("format", "long")));
  }

  @Test
  void expandKeepsAPatternThatHasNoIdentifierProperty() {
    assertEquals("^#[0-9a-fA-F]{6}$", UnicodeRegularExpressions.expand("^#[0-9a-fA-F]{6}$"));
    assertEquals("\\p{L}+", UnicodeRegularExpressions.expand("\\p{L}+"));
  }

  @Test
  void expandMakesCharacterClassesThatJavaCompiles() {
    String expanded = UnicodeRegularExpressions.expand("^[\\p{XID_Start}_][\\p{XID_Continue}]*$");

    assertFalse(expanded.contains("XID"), expanded.substring(0, 40));
    Pattern pattern = Pattern.compile(expanded);
    assertTrue(pattern.matcher("a·b").matches());
    assertFalse(pattern.matcher("·a").matches());
  }

  @Test
  void theEngineDoesNotUseTheIdOfTheDocument() {
    JsonSchemaEngine other =
        new JsonSchemaEngine(Map.of("$id", "urn:other", "$defs", Map.of("T", Map.of("const", 2L))));

    assertEquals(List.of(), other.errors("/$defs/T", 2L));
    assertEquals(List.of(), engine.errors("/$defs/a~1b", 1L));
  }

  @Test
  void aReferenceThatIsNotInTheDocumentIsACatalogError() {
    JsonSchemaEngine broken =
        new JsonSchemaEngine(
            Map.of(
                "$defs",
                Map.of(
                    "Remote", Map.of("$ref", "https://example.com/other.json#/$defs/X"),
                    "Sibling", Map.of("$ref", "common_types.json#/$defs/X"),
                    "Dangling", Map.of("$ref", "#/$defs/Missing"))));

    assertThrows(A2uiCatalogError.class, () -> broken.errors("/$defs/Remote", 1L));
    assertThrows(A2uiCatalogError.class, () -> broken.errors("/$defs/Sibling", 1L));
    assertThrows(A2uiCatalogError.class, () -> broken.errors("/$defs/Dangling", 1L));
    A2uiCatalogError error =
        assertThrows(A2uiCatalogError.class, () -> broken.errors("/$defs/NoSuchSchema", 1L));
    assertTrue(error.getMessage().startsWith("The schema at '/$defs/NoSuchSchema'"));
  }
}
