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
import static com.google.a2ui.swing.conformance.JsonValues.asString;

import com.google.a2ui.swing.surface.A2uiServices;
import com.google.a2ui.swing.surface.MarkdownView;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;

/**
 * Runs the action {@code render_markdown} of {@code adapter/markdown_subset.yaml}.
 *
 * <p>The handler gives the Markdown text to a view of the default Markdown service. Then it
 * examines the document of the Swing text component, which is what the user sees: the visible text,
 * and the HTML tags that the component shows.
 */
final class RenderMarkdownHandler implements SuiteRegistry.CaseHandler {
  private static final Set<String> CASE_KEYS =
      KnownKeys.caseKeys(
          "action", "markdown", "expectVisibleText", "expectPresent", "expectAbsent");

  /** The tags of the document that show each element of the suite. */
  private static final Map<String, Set<String>> TAGS_OF =
      Map.of(
          "emphasis", Set.of("em", "i"),
          "strong", Set.of("strong", "b"),
          "heading", Set.of("h1", "h2", "h3", "h4", "h5", "h6"),
          "list_item", Set.of("li"),
          "code", Set.of("code", "pre"),
          "image", Set.of("img"),
          "link", Set.of("a"));

  /** The tags that the Markdown subset makes, and the structure tags of each HTML document. */
  private static final Set<String> SUBSET_TAGS =
      Set.of(
          "html",
          "head",
          "body",
          "content",
          "p",
          "p-implied",
          "h1",
          "h2",
          "h3",
          "h4",
          "h5",
          "h6",
          "ul",
          "ol",
          "li",
          "em",
          "strong",
          "strike",
          "code",
          "pre",
          "blockquote",
          "hr",
          "br");

  @Override
  public void run(ConformanceCase testCase) throws BadLocationException {
    String location = testCase.location();
    Map<String, Object> fields = testCase.fields();
    KnownKeys.assertKnownKeys(fields, CASE_KEYS, location);

    MarkdownView view = A2uiServices.DEFAULT.markdown().createView();
    view.setMarkdown(asString(fields.get("markdown"), "markdown in " + location));
    if (!(view.component() instanceof JTextComponent component)
        || !(component.getDocument() instanceof HTMLDocument document)) {
      throw new AssertionError(location + ": the default Markdown view has no HTML document.");
    }
    Set<String> tags = new TreeSet<>();
    collect(document.getDefaultRootElement(), tags);

    if (fields.containsKey("expectVisibleText")) {
      String expected = asString(fields.get("expectVisibleText"), "expectVisibleText");
      JsonValues.assertJsonEquals(
          normalize(expected), normalize(textOf(document)), "visible text in " + location);
    }
    if (fields.containsKey("expectPresent")) {
      for (Object element : asList(fields.get("expectPresent"), "expectPresent in " + location)) {
        if (!hasTagOf(element, tags, location)) {
          throw new AssertionError(
              location + ": expected the element '" + element + "', but the tags are " + tags);
        }
      }
    }
    if (fields.containsKey("expectAbsent")) {
      for (Object element : asList(fields.get("expectAbsent"), "expectAbsent in " + location)) {
        if ("html".equals(element)) {
          // HTML of the Markdown text must be text. Thus each tag must be a tag of the subset.
          Set<String> other = new TreeSet<>(tags);
          other.removeAll(SUBSET_TAGS);
          if (!other.isEmpty()) {
            throw new AssertionError(location + ": the document has the HTML tags " + other);
          }
        } else if (hasTagOf(element, tags, location)) {
          throw new AssertionError(
              location + ": expected no element '" + element + "', but the tags are " + tags);
        }
      }
    }
    view.dispose();
  }

  private static boolean hasTagOf(Object element, Set<String> tags, String location) {
    Set<String> names = TAGS_OF.get(element);
    if (names == null) {
      throw new AssertionError("Unknown element '" + element + "' in " + location);
    }
    return names.stream().anyMatch(tags::contains);
  }

  /** Adds the name of each block tag and of each inline tag below an element. */
  private static void collect(Element element, Set<String> tags) {
    AttributeSet attributes = element.getAttributes();
    tags.add(String.valueOf(attributes.getAttribute(StyleConstants.NameAttribute)));
    attributes
        .getAttributeNames()
        .asIterator()
        .forEachRemaining(name -> {
          if (name instanceof HTML.Tag tag) {
            tags.add(tag.toString());
          }
        });
    for (int i = 0; i < element.getElementCount(); i++) {
      collect(element.getElement(i), tags);
    }
  }

  private static String textOf(Document document) throws BadLocationException {
    return document.getText(0, document.getLength());
  }

  private static String normalize(String text) {
    return text.replaceAll("\\s+", " ").strip();
  }
}
