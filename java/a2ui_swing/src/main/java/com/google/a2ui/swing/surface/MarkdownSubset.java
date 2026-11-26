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

package com.google.a2ui.swing.surface;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The parser of the Markdown subset that the {@code Text} component of the basic catalog shows.
 *
 * <p>The parser reads these blocks: paragraphs, headings ({@code #} to {@code ######}, and a line
 * of {@code =} or {@code -} below a text), lists with {@code -}, {@code *}, {@code +} or a number,
 * also lists in lists, code blocks between lines of three backticks or tildes, quotes ({@code >})
 * and horizontal rules.
 *
 * <p>The parser reads these inline elements: emphasis ({@code *text*}, {@code _text_}), strong text
 * ({@code **text**}, {@code __text__}), strikethrough ({@code ~~text~~}), code spans, hard line
 * breaks (two spaces or a backslash at the end of a line), backslash escapes and character
 * references. The rules for {@code *} and {@code _} are the rules of CommonMark. Thus {@code 2 * 3}
 * and {@code snake_case} stay as they are.
 *
 * <p>The parser has no element for HTML, for a link or for an image, because the basic catalog
 * permits none of them. HTML is text. An inline link and an image are their text only. An autolink
 * is its address as text.
 *
 * <p>The parser accepts each text. Text that is not correct Markdown is plain text.
 */
final class MarkdownSubset {
  /** A block of a document. */
  sealed interface Block permits Heading, Paragraph, ListBlock, CodeBlock, Quote, Rule {}

  /**
   * A heading.
   *
   * @param level the level, from 1 to 6
   * @param content the text of the heading
   */
  record Heading(int level, List<Inline> content) implements Block {}

  /**
   * A paragraph.
   *
   * @param content the text of the paragraph
   */
  record Paragraph(List<Inline> content) implements Block {}

  /**
   * A list.
   *
   * @param ordered true if the items have numbers
   * @param start the number of the first item of an ordered list
   * @param items the items
   */
  record ListBlock(boolean ordered, int start, List<Item> items) implements Block {}

  /**
   * An item of a list.
   *
   * @param content the text of the item
   * @param lists the lists below the item
   */
  record Item(List<Inline> content, List<ListBlock> lists) {}

  /**
   * A block of code. The text keeps its spaces and its line breaks.
   *
   * @param code the code, with no line break at the end
   */
  record CodeBlock(String code) implements Block {}

  /**
   * A quote.
   *
   * @param blocks the content of the quote
   */
  record Quote(List<Block> blocks) implements Block {}

  /** A horizontal rule. */
  record Rule() implements Block {}

  /** A part of the text of a block. */
  sealed interface Inline permits Text, Emphasis, Strong, Strike, Code, LineBreak {}

  /**
   * Plain text.
   *
   * @param text the text
   */
  record Text(String text) implements Inline {}

  /**
   * Text with emphasis.
   *
   * @param content the text
   */
  record Emphasis(List<Inline> content) implements Inline {}

  /**
   * Strong text.
   *
   * @param content the text
   */
  record Strong(List<Inline> content) implements Inline {}

  /**
   * Text with a line through it.
   *
   * @param content the text
   */
  record Strike(List<Inline> content) implements Inline {}

  /**
   * A code span.
   *
   * @param code the code
   */
  record Code(String code) implements Inline {}

  /** A line break that the writer asked for. */
  record LineBreak() implements Inline {}

  private static final Pattern HEADING =
      Pattern.compile("^ {0,3}(#{1,6})(?:[ \\t]+(.*?))?(?:[ \\t]+#+)?[ \\t]*$");
  private static final Pattern SETEXT = Pattern.compile("^ {0,3}(=+|-+)[ \\t]*$");
  private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})[^`]*$");
  private static final Pattern RULE =
      Pattern.compile("^ {0,3}(?:(?:\\*[ \\t]*){3,}|(?:-[ \\t]*){3,}|(?:_[ \\t]*){3,})$");
  private static final Pattern QUOTE = Pattern.compile("^ {0,3}> ?(.*)$");
  private static final Pattern ITEM = Pattern.compile("^( *)(?:[-*+]|(\\d{1,9})[.)]) +(.*)$");
  private static final Pattern AUTOLINK =
      Pattern.compile("<([A-Za-z][A-Za-z0-9+.-]{1,31}:[^\\s<>]*|[^\\s<>@]+@[^\\s<>@]+)>");
  private static final Pattern REFERENCE =
      Pattern.compile("&(?:#([0-9]{1,7})|#[xX]([0-9a-fA-F]{1,6})|(amp|lt|gt|quot|apos|nbsp));");

  private static final String PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

  /** The number of levels of lists in lists and of quotes in quotes that the parser reads. */
  private static final int MAX_DEPTH = 8;

  private MarkdownSubset() {}

  /**
   * Reads a text.
   *
   * @param markdown the Markdown text
   * @return the blocks of the document. The list is read-only.
   */
  static List<Block> parse(String markdown) {
    String text = markdown.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
    return blocks(List.of(text.split("\n", -1)), 0);
  }

  // Blocks.

  private static List<Block> blocks(List<String> lines, int depth) {
    List<Block> out = new ArrayList<>();
    List<String> paragraph = new ArrayList<>();
    int i = 0;
    while (i < lines.size()) {
      String line = lines.get(i);
      if (line.isBlank()) {
        flush(paragraph, out);
        i++;
        continue;
      }
      Matcher fence = FENCE.matcher(line);
      if (fence.matches()) {
        flush(paragraph, out);
        i = codeBlock(lines, i + 1, fence.group(1), out);
        continue;
      }
      Matcher heading = HEADING.matcher(line);
      if (heading.matches()) {
        flush(paragraph, out);
        String content = heading.group(2) == null ? "" : heading.group(2);
        out.add(new Heading(heading.group(1).length(), inlines(content)));
        i++;
        continue;
      }
      Matcher underline = SETEXT.matcher(line);
      if (underline.matches() && !paragraph.isEmpty()) {
        int level = underline.group(1).charAt(0) == '=' ? 1 : 2;
        out.add(new Heading(level, inlines(String.join("\n", paragraph))));
        paragraph.clear();
        i++;
        continue;
      }
      if (RULE.matcher(line).matches()) {
        flush(paragraph, out);
        out.add(new Rule());
        i++;
        continue;
      }
      if (QUOTE.matcher(line).matches() && depth < MAX_DEPTH) {
        flush(paragraph, out);
        List<String> quoted = new ArrayList<>();
        for (; i < lines.size(); i++) {
          Matcher quote = QUOTE.matcher(lines.get(i));
          if (!quote.matches()) {
            break;
          }
          quoted.add(quote.group(1));
        }
        out.add(new Quote(blocks(quoted, depth + 1)));
        continue;
      }
      Matcher item = ITEM.matcher(line);
      // A number that is not 1 does not stop a paragraph. Thus "in\n1986. It" stays a paragraph.
      if (item.matches()
          && (paragraph.isEmpty() || item.group(2) == null || item.group(2).equals("1"))) {
        flush(paragraph, out);
        i = list(lines, i, out);
        continue;
      }
      paragraph.add(line.strip() + (line.endsWith("  ") ? "  " : ""));
      i++;
    }
    flush(paragraph, out);
    return List.copyOf(out);
  }

  private static void flush(List<String> paragraph, List<Block> out) {
    if (!paragraph.isEmpty()) {
      out.add(new Paragraph(inlines(String.join("\n", paragraph))));
      paragraph.clear();
    }
  }

  /** Reads the lines of a code block. Returns the index of the line after the block. */
  private static int codeBlock(List<String> lines, int from, String fence, List<Block> out) {
    Pattern end =
        Pattern.compile(
            "^ {0,3}" + Pattern.quote(fence.substring(0, 1)) + "{" + fence.length() + ",}[ \\t]*$");
    List<String> code = new ArrayList<>();
    int i = from;
    for (; i < lines.size(); i++) {
      if (end.matcher(lines.get(i)).matches()) {
        i++;
        break;
      }
      code.add(lines.get(i));
    }
    out.add(new CodeBlock(String.join("\n", code)));
    return i;
  }

  /** A list while the parser reads its items. */
  private static final class OpenList {
    final int indent;
    final boolean ordered;
    final int start;
    final List<OpenItem> items = new ArrayList<>();

    OpenList(int indent, boolean ordered, int start) {
      this.indent = indent;
      this.ordered = ordered;
      this.start = start;
    }

    ListBlock close() {
      List<Item> closed = new ArrayList<>();
      for (OpenItem item : items) {
        List<ListBlock> lists = new ArrayList<>();
        item.lists.forEach(list -> lists.add(list.close()));
        closed.add(new Item(inlines(String.join("\n", item.lines)), List.copyOf(lists)));
      }
      return new ListBlock(ordered, start, List.copyOf(closed));
    }
  }

  /** An item while the parser reads its lines. */
  private static final class OpenItem {
    final List<String> lines = new ArrayList<>();
    final List<OpenList> lists = new ArrayList<>();
  }

  /** Reads the lines of one or more lists. Returns the index of the line after the lists. */
  private static int list(List<String> lines, int from, List<Block> out) {
    List<OpenList> roots = new ArrayList<>();
    Deque<OpenList> open = new ArrayDeque<>();
    int i = from;
    while (i < lines.size()) {
      String line = lines.get(i);
      Matcher item = ITEM.matcher(line);
      if (item.matches() && !RULE.matcher(line).matches()) {
        int indent = item.group(1).length();
        boolean ordered = item.group(2) != null;
        int start = ordered ? Integer.parseInt(item.group(2)) : 1;
        while (open.size() > 1 && indent < open.peek().indent) {
          open.pop();
        }
        OpenList top = open.peek();
        if (top == null) {
          top = new OpenList(indent, ordered, start);
          roots.add(top);
          open.push(top);
        } else if (indent >= top.indent + 2 && open.size() < MAX_DEPTH) {
          OpenList nested = new OpenList(indent, ordered, start);
          top.items.getLast().lists.add(nested);
          open.push(nested);
          top = nested;
        } else if (top.ordered != ordered) {
          // A list of a different kind at the same level is a new list.
          open.pop();
          OpenList sibling = new OpenList(top.indent, ordered, start);
          if (open.isEmpty()) {
            roots.add(sibling);
          } else {
            open.peek().items.getLast().lists.add(sibling);
          }
          open.push(sibling);
          top = sibling;
        }
        OpenItem added = new OpenItem();
        added.lines.add(item.group(3).strip());
        top.items.add(added);
        i++;
        continue;
      }
      if (line.isBlank()) {
        // The list continues after empty lines only with a subsequent item.
        int next = i + 1;
        while (next < lines.size() && lines.get(next).isBlank()) {
          next++;
        }
        if (next < lines.size() && ITEM.matcher(lines.get(next)).matches()) {
          i = next;
          continue;
        }
        break;
      }
      if (startsBlock(line)) {
        break;
      }
      // A line with no mark continues the text of the last item.
      open.peek().items.getLast().lines.add(line.strip());
      i++;
    }
    roots.forEach(root -> out.add(root.close()));
    return i;
  }

  private static boolean startsBlock(String line) {
    return FENCE.matcher(line).matches()
        || HEADING.matcher(line).matches()
        || RULE.matcher(line).matches()
        || QUOTE.matcher(line).matches();
  }

  // Inline elements.

  /** A run of {@code *}, {@code _} or {@code ~} that can start or end an element. */
  private static final class Delimiter {
    final char mark;
    final int length;
    final boolean canOpen;
    final boolean canClose;
    int count;

    Delimiter(char mark, int length, boolean canOpen, boolean canClose) {
      this.mark = mark;
      this.length = length;
      this.count = length;
      this.canOpen = canOpen;
      this.canClose = canClose;
    }
  }

  /**
   * Reads the inline elements of a text.
   *
   * @param text the text of one block. A line feed is a line break of the source.
   * @return the elements. The list is read-only.
   */
  static List<Inline> inlines(String text) {
    return inlines(text, 0);
  }

  private static List<Inline> inlines(String text, int depth) {
    // Each element of the list is an Inline or a Delimiter.
    List<Object> pieces = new ArrayList<>();
    StringBuilder plain = new StringBuilder();
    int i = 0;
    int length = text.length();
    while (i < length) {
      char c = text.charAt(i);
      switch (c) {
        case '\\' -> {
          if (i + 1 < length && text.charAt(i + 1) == '\n') {
            addBreak(pieces, plain);
            i = skipSpaces(text, i + 2);
          } else if (i + 1 < length && PUNCTUATION.indexOf(text.charAt(i + 1)) >= 0) {
            plain.append(text.charAt(i + 1));
            i += 2;
          } else {
            plain.append(c);
            i++;
          }
        }
        case '`' -> {
          int run = runLength(text, i);
          int end = closingBackticks(text, i + run, run);
          if (end < 0) {
            plain.append(text, i, i + run);
            i += run;
          } else {
            addText(pieces, plain);
            pieces.add(new Code(codeOf(text.substring(i + run, end))));
            i = end + run;
          }
        }
        case '!', '[' -> {
          int open = c == '!' ? i + 1 : i;
          int[] link = open < length && text.charAt(open) == '[' ? linkAt(text, open) : null;
          if (link == null || depth >= MAX_DEPTH) {
            plain.append(c);
            i++;
          } else {
            // A link and an image are their text only. The parser drops the address.
            addText(pieces, plain);
            pieces.addAll(inlines(text.substring(open + 1, link[0]), depth + 1));
            i = link[1];
          }
        }
        case '<' -> {
          Matcher autolink = AUTOLINK.matcher(text).region(i, length);
          if (autolink.lookingAt()) {
            plain.append(autolink.group(1));
            i = autolink.end();
          } else {
            plain.append(c);
            i++;
          }
        }
        case '&' -> {
          Matcher reference = REFERENCE.matcher(text).region(i, length);
          if (reference.lookingAt()) {
            plain.append(characterOf(reference));
            i = reference.end();
          } else {
            plain.append(c);
            i++;
          }
        }
        case '*', '_', '~' -> {
          int run = runLength(text, i);
          if (c == '~' && run != 2) {
            plain.append(text, i, i + run);
          } else {
            addText(pieces, plain);
            pieces.add(delimiter(text, i, run));
          }
          i += run;
        }
        case '\n' -> {
          int spaces = 0;
          while (spaces < plain.length() && plain.charAt(plain.length() - 1 - spaces) == ' ') {
            spaces++;
          }
          plain.setLength(plain.length() - spaces);
          if (spaces >= 2) {
            addBreak(pieces, plain);
          } else {
            // A line break of the source is a space, as in CommonMark.
            plain.append(' ');
          }
          i = skipSpaces(text, i + 1);
        }
        default -> {
          plain.append(c);
          i++;
        }
      }
    }
    addText(pieces, plain);
    return resolve(pieces);
  }

  private static void addText(List<Object> pieces, StringBuilder plain) {
    if (plain.length() > 0) {
      pieces.add(new Text(plain.toString()));
      plain.setLength(0);
    }
  }

  private static void addBreak(List<Object> pieces, StringBuilder plain) {
    addText(pieces, plain);
    pieces.add(new LineBreak());
  }

  private static int skipSpaces(String text, int from) {
    int i = from;
    while (i < text.length() && text.charAt(i) == ' ') {
      i++;
    }
    return i;
  }

  private static int runLength(String text, int from) {
    int i = from;
    while (i < text.length() && text.charAt(i) == text.charAt(from)) {
      i++;
    }
    return i - from;
  }

  /** Finds the run of backticks that ends a code span. Returns -1 if there is none. */
  private static int closingBackticks(String text, int from, int run) {
    int i = from;
    while (i < text.length()) {
      if (text.charAt(i) != '`') {
        i++;
        continue;
      }
      int found = runLength(text, i);
      if (found == run) {
        return i;
      }
      i += found;
    }
    return -1;
  }

  private static String codeOf(String raw) {
    String code = raw.replace('\n', ' ');
    if (code.length() >= 2 && code.startsWith(" ") && code.endsWith(" ") && !code.isBlank()) {
      code = code.substring(1, code.length() - 1);
    }
    return code;
  }

  /**
   * Reads an inline link that starts at a {@code [}.
   *
   * @return the index of the {@code ]} that ends the text and the index after the link, or null if
   *     the text at the position is not an inline link
   */
  private static int[] linkAt(String text, int open) {
    int depth = 0;
    int close = -1;
    for (int i = open; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\\') {
        i++;
      } else if (c == '`') {
        int run = runLength(text, i);
        int end = closingBackticks(text, i + run, run);
        i = end < 0 ? i + run - 1 : end + run - 1;
      } else if (c == '[') {
        depth++;
      } else if (c == ']' && --depth == 0) {
        close = i;
        break;
      }
    }
    if (close < 0 || close + 1 >= text.length() || text.charAt(close + 1) != '(') {
      return null;
    }
    int i = close + 2;
    int parentheses = 1;
    char quote = 0;
    boolean angle = false;
    for (; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\\') {
        i++;
      } else if (quote != 0) {
        quote = c == quote ? 0 : quote;
      } else if (angle) {
        angle = c != '>';
      } else if (c == '<') {
        angle = true;
      } else if (c == '"' || c == '\'') {
        quote = c;
      } else if (c == '(') {
        parentheses++;
      } else if (c == ')' && --parentheses == 0) {
        return new int[] {close, i + 1};
      }
    }
    return null;
  }

  private static String characterOf(Matcher reference) {
    if (reference.group(3) != null) {
      return switch (reference.group(3)) {
        case "amp" -> "&";
        case "lt" -> "<";
        case "gt" -> ">";
        case "quot" -> "\"";
        case "apos" -> "'";
        default -> " ";
      };
    }
    int codePoint =
        reference.group(1) != null
            ? Integer.parseInt(reference.group(1))
            : Integer.parseInt(reference.group(2), 16);
    return codePoint == 0 || !Character.isValidCodePoint(codePoint)
        ? "�"
        : Character.toString(codePoint);
  }

  /** Makes the delimiter of a run, with the rules of CommonMark for the two sides of the run. */
  private static Delimiter delimiter(String text, int at, int run) {
    int before = at == 0 ? ' ' : text.codePointBefore(at);
    int after = at + run >= text.length() ? ' ' : text.codePointAt(at + run);
    boolean leftFlanking =
        !isSpace(after) && (!isPunctuation(after) || isSpace(before) || isPunctuation(before));
    boolean rightFlanking =
        !isSpace(before) && (!isPunctuation(before) || isSpace(after) || isPunctuation(after));
    char mark = text.charAt(at);
    if (mark == '_') {
      return new Delimiter(
          mark,
          run,
          leftFlanking && (!rightFlanking || isPunctuation(before)),
          rightFlanking && (!leftFlanking || isPunctuation(after)));
    }
    return new Delimiter(mark, run, leftFlanking, rightFlanking);
  }

  private static boolean isSpace(int codePoint) {
    return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
  }

  private static boolean isPunctuation(int codePoint) {
    if (codePoint < 128) {
      return PUNCTUATION.indexOf(codePoint) >= 0;
    }
    return switch (Character.getType(codePoint)) {
      case Character.CONNECTOR_PUNCTUATION,
          Character.DASH_PUNCTUATION,
          Character.START_PUNCTUATION,
          Character.END_PUNCTUATION,
          Character.INITIAL_QUOTE_PUNCTUATION,
          Character.FINAL_QUOTE_PUNCTUATION,
          Character.OTHER_PUNCTUATION,
          Character.MATH_SYMBOL,
          Character.CURRENCY_SYMBOL,
          Character.MODIFIER_SYMBOL,
          Character.OTHER_SYMBOL ->
          true;
      default -> false;
    };
  }

  /**
   * Makes the elements for the delimiters that have a partner. This is the CommonMark procedure.
   */
  private static List<Inline> resolve(List<Object> pieces) {
    int i = 0;
    while (i < pieces.size()) {
      if (!(pieces.get(i) instanceof Delimiter closer) || !closer.canClose || closer.count == 0) {
        i++;
        continue;
      }
      int at = -1;
      for (int j = i - 1; j >= 0; j--) {
        if (pieces.get(j) instanceof Delimiter candidate
            && candidate.mark == closer.mark
            && candidate.canOpen
            && candidate.count > 0
            && !multipleOfThree(candidate, closer)) {
          at = j;
          break;
        }
      }
      if (at < 0) {
        i++;
        continue;
      }
      Delimiter opener = (Delimiter) pieces.get(at);
      int use = closer.mark == '~' || (opener.count >= 2 && closer.count >= 2) ? 2 : 1;
      List<Object> between = pieces.subList(at + 1, i);
      List<Inline> content = plain(between);
      between.clear();
      Inline element;
      if (closer.mark == '~') {
        element = new Strike(content);
      } else {
        element = use == 2 ? new Strong(content) : new Emphasis(content);
      }
      pieces.add(at + 1, element);
      opener.count -= use;
      closer.count -= use;
      // The closer is now after the opener and the new element.
      i = at + 2;
      if (opener.count == 0) {
        pieces.remove(at);
        i--;
      }
      if (closer.count == 0) {
        pieces.remove(i);
      }
    }
    return plain(pieces);
  }

  /** The rule of CommonMark that keeps {@code *a**b*} as one emphasis. */
  private static boolean multipleOfThree(Delimiter opener, Delimiter closer) {
    return (opener.canClose || closer.canOpen)
        && (opener.length + closer.length) % 3 == 0
        && !(opener.length % 3 == 0 && closer.length % 3 == 0);
  }

  /** Makes text of the delimiters that have no partner, and joins text that is side by side. */
  private static List<Inline> plain(List<Object> pieces) {
    List<Inline> out = new ArrayList<>();
    StringBuilder text = new StringBuilder();
    for (Object piece : pieces) {
      switch (piece) {
        case Delimiter delimiter -> text.repeat(delimiter.mark, delimiter.count);
        case Text plainText -> text.append(plainText.text());
        case Inline element -> {
          if (text.length() > 0) {
            out.add(new Text(text.toString()));
            text.setLength(0);
          }
          out.add(element);
        }
        default -> throw new IllegalStateException("Unknown piece: " + piece);
      }
    }
    if (text.length() > 0) {
      out.add(new Text(text.toString()));
    }
    return List.copyOf(out);
  }
}
