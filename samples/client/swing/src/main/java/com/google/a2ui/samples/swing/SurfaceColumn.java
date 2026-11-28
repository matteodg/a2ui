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

import com.google.a2ui.swing.surface.A2uiSurface;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

/**
 * The center column of the gallery: the controls of the run, the facts about the example and the
 * surface in a scroll pane.
 */
final class SurfaceColumn {
  /** The color of the text of a failure. It has sufficient contrast on a light background. */
  private static final Color FAILURE = new Color(0xB0, 0x00, 0x20);

  private final JPanel panel = new JPanel(new BorderLayout());
  private final JButton reload = new JButton("Reload");
  private final JButton restart = new JButton("Restart");
  private final JButton step = new JButton("Step");
  private final JLabel progress = new JLabel();
  private final JLabel title = new JLabel(" ");
  private final JLabel description = new JLabel(" ");
  private final JLabel absent = new JLabel(" ");
  private final JTextArea failures = new JTextArea(3, 20);
  private final JScrollPane failuresPane = new JScrollPane(failures);
  private final JLabel empty = new JLabel("", SwingConstants.CENTER);
  private final JScrollPane surfacePane = new JScrollPane();
  private A2uiSurface surface;

  /**
   * Makes the column.
   *
   * @param onReload runs when the user wants to process the example again
   * @param onRestart runs when the user wants to start the example again with no message processed
   * @param onStep runs when the user wants to process the subsequent message
   */
  SurfaceColumn(Runnable onReload, Runnable onRestart, Runnable onStep) {
    reload.setToolTipText("Process all the messages of the example again.");
    restart.setToolTipText("Start the example again and process no message.");
    step.setToolTipText("Process the subsequent message of the example.");
    reload.addActionListener(event -> onReload.run());
    restart.addActionListener(event -> onRestart.run());
    step.addActionListener(event -> onStep.run());

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 6));
    buttons.add(reload);
    buttons.add(restart);
    buttons.add(step);
    // The label gets the width that the buttons do not use. A narrow column shortens its text.
    JPanel toolBar = new JPanel(new BorderLayout());
    toolBar.add(buttons, BorderLayout.WEST);
    toolBar.add(progress, BorderLayout.CENTER);

    title.setFont(title.getFont().deriveFont(Font.BOLD));
    failures.setEditable(false);
    failures.setLineWrap(true);
    failures.setWrapStyleWord(true);
    failures.setForeground(FAILURE);
    failuresPane.setVisible(false);

    // A grid gives each label the width of the column. Thus a long text ends with an ellipsis.
    JPanel lines = new JPanel(new GridLayout(0, 1, 0, 2));
    lines.add(title);
    lines.add(description);
    lines.add(absent);
    JPanel facts = new JPanel(new BorderLayout(0, 4));
    facts.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
    facts.add(lines, BorderLayout.CENTER);
    facts.add(failuresPane, BorderLayout.SOUTH);

    JPanel header = new JPanel(new BorderLayout());
    header.add(toolBar, BorderLayout.NORTH);
    header.add(facts, BorderLayout.CENTER);

    surfacePane.setBorder(BorderFactory.createEmptyBorder());
    surfacePane.getVerticalScrollBar().setUnitIncrement(16);
    panel.add(header, BorderLayout.NORTH);
    panel.add(surfacePane, BorderLayout.CENTER);
    // The labels have long texts. The column can be narrower than their preferred widths.
    panel.setMinimumSize(new Dimension(240, 120));
    showNoExample();
  }

  /**
   * Gets the Swing component of the column.
   *
   * @return the component
   */
  JComponent component() {
    return panel;
  }

  /** Shows the column while no example is selected. */
  void showNoExample() {
    title.setText("No example");
    setLine(description, "Select an example in the list.");
    setLine(absent, " ");
    showFailures(List.of());
    progress.setText("");
    reload.setEnabled(false);
    restart.setEnabled(false);
    step.setEnabled(false);
    showSurface(null, "");
  }

  /**
   * Shows the facts about an example: its name, its description and the component types that have
   * no Swing view.
   *
   * @param example the example
   */
  void showExample(Example example) {
    title.setText(example.name().equals(example.id()) ? example.id() : example.name());
    title.setToolTipText(example.id() + ".json");
    setLine(description, example.description().isBlank() ? " " : example.description());
    if (example.problem().isPresent()) {
      setLine(absent, " ");
    } else if (example.absentViews().isEmpty()) {
      setLine(absent, "Each component of this example has a Swing view.");
    } else {
      setLine(
          absent,
          "No Swing view: "
              + String.join(", ", example.absentViews())
              + ". A placeholder shows for each of these components.");
    }
    boolean runs = example.problem().isEmpty();
    reload.setEnabled(runs);
    restart.setEnabled(runs);
  }

  /** Sets the text of a label that has one line. The tool tip has the full text. */
  private static void setLine(JLabel label, String text) {
    label.setText(text);
    label.setToolTipText(text.isBlank() ? null : text);
  }

  /**
   * Shows how many messages the processor got, and the failures.
   *
   * @param processed the number of the messages that the processor got
   * @param total the number of the messages of the example
   * @param problems the text of each failure
   */
  void showProgress(int processed, int total, List<String> problems) {
    progress.setText(processed + " of " + total + " messages");
    step.setEnabled(processed < total);
    showFailures(problems);
  }

  private void showFailures(List<String> problems) {
    failures.setText(String.join("\n", problems));
    failures.setCaretPosition(0);
    if (failuresPane.isVisible() == problems.isEmpty()) {
      failuresPane.setVisible(!problems.isEmpty());
      panel.revalidate();
    }
  }

  /**
   * Shows a surface, or a text when there is no surface.
   *
   * @param next the surface component, or null
   * @param emptyText the text to show when there is no surface
   */
  void showSurface(A2uiSurface next, String emptyText) {
    surface = next;
    empty.setText(emptyText);
    // The viewport removes the surface component that it had. Thus that component disposes its
    // views.
    surfacePane.setViewportView(next == null ? empty : next);
    surfacePane.revalidate();
    surfacePane.repaint();
  }

  /**
   * Gets the surface component that the column shows.
   *
   * @return the component, or an empty result
   */
  Optional<A2uiSurface> surface() {
    return Optional.ofNullable(surface);
  }

  /**
   * Gets the scroll pane of the surface.
   *
   * @return the scroll pane
   */
  JScrollPane surfacePane() {
    return surfacePane;
  }

  /**
   * Gets the text of the failures that the column shows.
   *
   * @return the text. It is empty when there is no failure.
   */
  String failureText() {
    return failures.getText();
  }

  /**
   * Gets the text about the component types with no Swing view.
   *
   * @return the text
   */
  String absentText() {
    return absent.getText();
  }

  /**
   * Gets the text about the progress of the run.
   *
   * @return the text
   */
  String progressText() {
    return progress.getText();
  }
}
