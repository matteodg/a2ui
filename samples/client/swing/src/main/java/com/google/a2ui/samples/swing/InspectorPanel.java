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

package com.google.a2ui.samples.swing;

import com.google.a2ui.core.state.DataSubscription;
import com.google.a2ui.core.state.SurfaceModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

/**
 * The right column of the gallery: the messages of the example, the data model of the surface and
 * the log of the messages for the agent. Each part is a tab.
 */
final class InspectorPanel {
  /** The names of the tabs, in the sequence of the tabs. */
  static final List<String> TABS = List.of("Messages", "Data model", "Log");

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

  private final JTabbedPane tabs = new JTabbedPane();
  private final JTextArea messages = textArea();
  private final JTextArea dataModel = textArea();
  private final JTextArea log = textArea();
  private final JLabel logCount = new JLabel();
  private DataSubscription dataSubscription;
  private int logEntries;

  /** Makes the column with no content. */
  InspectorPanel() {
    JButton clear = new JButton("Clear");
    clear.setToolTipText("Remove each entry of the log.");
    clear.addActionListener(event -> clearLog());
    JPanel logBar = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 6));
    logBar.add(clear);
    logBar.add(logCount);
    JPanel logPanel = new JPanel(new BorderLayout());
    logPanel.add(logBar, BorderLayout.NORTH);
    logPanel.add(scroll(log), BorderLayout.CENTER);

    tabs.addTab(TABS.get(0), scroll(messages));
    tabs.addTab(TABS.get(1), scroll(dataModel));
    tabs.addTab(TABS.get(2), logPanel);
    tabs.setToolTipTextAt(0, "The messages of the example, from the agent to the renderer.");
    tabs.setToolTipTextAt(1, "The data model of the surface at this time.");
    tabs.setToolTipTextAt(2, "The messages from the renderer to the agent.");
    clearLog();
    showData(null);
  }

  private static JTextArea textArea() {
    JTextArea area = new JTextArea();
    area.setEditable(false);
    area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, area.getFont().getSize()));
    area.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
    return area;
  }

  private static JScrollPane scroll(JTextArea area) {
    JScrollPane pane = new JScrollPane(area);
    pane.setBorder(BorderFactory.createEmptyBorder());
    return pane;
  }

  /**
   * Gets the Swing component of the column.
   *
   * @return the component
   */
  JComponent component() {
    return tabs;
  }

  /**
   * Shows a tab.
   *
   * @param name one of {@link #TABS}. The case of the letters and the spaces are not important.
   * @return false if no tab has this name
   */
  boolean selectTab(String name) {
    for (int index = 0; index < TABS.size(); index++) {
      if (normal(TABS.get(index)).equals(normal(name))) {
        tabs.setSelectedIndex(index);
        return true;
      }
    }
    return false;
  }

  private static String normal(String name) {
    return name.replace(" ", "").replace("-", "").toLowerCase(java.util.Locale.ROOT);
  }

  /**
   * Shows the messages of an example as JSON text.
   *
   * @param example the example
   * @param processed the number of the messages that the processor got
   */
  void showMessages(Example example, int processed) {
    messages.setText(messagesText(example, processed));
    messages.setCaretPosition(0);
  }

  /**
   * Makes the text of the messages tab.
   *
   * @param example the example
   * @param processed the number of the messages that the processor got
   * @return each message as JSON text, below a line that tells if the processor got the message
   */
  static String messagesText(Example example, int processed) {
    if (example.problem().isPresent()) {
      return example.problem().get();
    }
    StringBuilder text = new StringBuilder();
    int total = example.messages().size();
    for (int index = 0; index < total; index++) {
      text.append("// Message ")
          .append(index + 1)
          .append(" of ")
          .append(total)
          .append(index < processed ? ": processed" : ": not processed")
          .append('\n')
          .append(Json.pretty(example.messages().get(index)))
          .append("\n\n");
    }
    return text.toString();
  }

  /**
   * Follows the data model of a surface. The tab shows each change of the data immediately.
   *
   * @param surface the surface, or null to follow nothing
   */
  void followData(SurfaceModel<?> surface) {
    if (dataSubscription != null) {
      dataSubscription.cancel();
      dataSubscription = null;
    }
    if (surface == null) {
      showData(null);
      return;
    }
    dataSubscription = surface.dataModel().subscribe("/", this::showData);
    showData(dataSubscription.value());
  }

  private void showData(Object value) {
    dataModel.setText(dataSubscription == null ? "No surface." : Json.pretty(value));
    dataModel.setCaretPosition(0);
  }

  /**
   * Adds a message for the agent to the log.
   *
   * @param message the message as plain JSON values
   */
  void appendLog(Map<String, Object> message) {
    logEntries++;
    log.append(
        "// "
            + logEntries
            + ": "
            + kind(message)
            + " at "
            + LocalTime.now().format(TIME)
            + "\n"
            + Json.pretty(message)
            + "\n\n");
    log.setCaretPosition(log.getDocument().getLength());
    showLogCount();
  }

  /** Gets the name of the message: the key of the envelope that is not the version. */
  private static String kind(Map<String, Object> message) {
    return message.keySet().stream().filter(key -> !key.equals("version")).findFirst().orElse("?");
  }

  /** Removes each entry of the log. */
  void clearLog() {
    logEntries = 0;
    log.setText("");
    showLogCount();
  }

  private void showLogCount() {
    logCount.setText(
        logEntries == 1 ? "1 message for the agent" : logEntries + " messages for the agent");
  }

  /**
   * Gets the text of the messages tab.
   *
   * @return the text
   */
  String messagesText() {
    return messages.getText();
  }

  /**
   * Gets the text of the data model tab.
   *
   * @return the text
   */
  String dataText() {
    return dataModel.getText();
  }

  /**
   * Gets the text of the log tab.
   *
   * @return the text
   */
  String logText() {
    return log.getText();
  }

  /**
   * Gets the number of the entries of the log.
   *
   * @return the number
   */
  int logEntries() {
    return logEntries;
  }
}
