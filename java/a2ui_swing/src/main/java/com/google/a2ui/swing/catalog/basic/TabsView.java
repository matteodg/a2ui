/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.a2ui.swing.catalog.basic;

import com.google.a2ui.core.common.Coercion;
import com.google.a2ui.core.resolution.ComponentNode;
import com.google.a2ui.core.resolution.NodeProps;
import com.google.a2ui.swing.catalog.ChildViews;
import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.catalog.SwingComponentView;
import com.google.a2ui.swing.catalog.ViewContext;
import java.awt.Dimension;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeListener;

/**
 * The view of the {@code Tabs} component of the basic catalog: a {@code JTabbedPane} of the look
 * and feel that shows the view of the child of the selected tab.
 *
 * <table>
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Effect</th></tr>
 *   <tr><td>{@code tabs}</td><td>The tabs, in their sequence. The {@code title} of a tab is the
 *       text of its header. The title is plain text: the header does not interpret HTML. The
 *       {@code child} of a tab is the component that shows while the tab is selected.</td></tr>
 * </table>
 *
 * <p><b>Selection.</b> The selected tab is state of the view, as the implementation guide
 * specifies. It is not in the data model. The first tab is selected at the start. The user selects
 * a tab with the mouse or with the arrow keys. The selection is an index. It stays when the titles
 * change and when the agent adds or removes other tabs. If the agent removes so many tabs that the
 * index is not in the list, the last tab becomes selected.
 *
 * <p><b>Children.</b> The view mounts only the child of the selected tab. When the user selects a
 * different tab, the adapter disposes the view of the child before, and makes the view of the new
 * child. Thus the state that only a view has, for example a scroll position, is lost. The values of
 * the controls are in the data model, and thus they stay.
 *
 * <p>The headers are in one line. If the line is too long for the width of the view, the look and
 * feel shows controls that scroll the headers. The preferred height of the view depends on its
 * width, as that of the other containers. Thus text in a tab breaks at the width of the tab.
 *
 * <p>The Swing component of the view is a panel that gives the margin of a control with an outline.
 * The accessibility attributes go to the {@code JTabbedPane}, which has the accessible role of a
 * page tab list.
 */
final class TabsView implements SwingComponentView {
  /** The size that the page reports while the pane measures the space around the page. */
  private static final int PROBE = 10_000;

  private final LeafPanel panel = new LeafPanel();
  private final Page page = new Page();
  private final Pane pane = new Pane();
  private final ChildViews child;

  /** The tabs of the newest properties. */
  private List<? extends NodeProps<?>> tabs = List.of();

  /** The index of the selected tab. It is state of the view. */
  private int selected;

  /** True while the view changes the pane. Then a change of the selection is not from the user. */
  private boolean updating;

  private final ChangeListener listener =
      event -> {
        if (!updating) {
          select(pane.getSelectedIndex());
        }
      };

  /**
   * Makes the view.
   *
   * @param context the context of the view
   */
  TabsView(ViewContext context) {
    // The pane fills the panel. Thus the tabs of a row have the same height.
    panel.flexLayout().setJustify(FlexLayout.Justify.STRETCH);
    panel.add(pane);
    child = context.children(page);
    pane.addChangeListener(listener);
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public JComponent accessibleComponent() {
    return pane;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    tabs = props.objects("tabs");
    updating = true;
    try {
      while (pane.getTabCount() > tabs.size()) {
        pane.removeTabAt(pane.getTabCount() - 1);
      }
      while (pane.getTabCount() < tabs.size()) {
        pane.addTab("", null);
      }
      for (int i = 0; i < tabs.size(); i++) {
        String title = plainTitle(Coercion.toText(tabs.get(i).value("title")));
        if (!title.equals(pane.getTitleAt(i))) {
          pane.setTitleAt(i, title);
        }
      }
      selected = Math.max(0, Math.min(selected, tabs.size() - 1));
      showPage();
    } finally {
      updating = false;
    }
    syncChild();
  }

  @Override
  public void dispose() {
    pane.removeChangeListener(listener);
    tabs = List.of();
  }

  /** Selects a tab for the user and shows its child. */
  private void select(int index) {
    if (index < 0 || index >= tabs.size() || index == selected) {
      return;
    }
    selected = index;
    updating = true;
    try {
      showPage();
    } finally {
      updating = false;
    }
    syncChild();
  }

  /** Makes the page the component of the selected tab, and of no other tab. */
  private void showPage() {
    if (tabs.isEmpty()) {
      return;
    }
    if (pane.getSelectedIndex() != selected) {
      pane.setSelectedIndex(selected);
    }
    for (int i = 0; i < pane.getTabCount(); i++) {
      if (i != selected && pane.getComponentAt(i) == page) {
        pane.setComponentAt(i, null);
      }
    }
    if (pane.getComponentAt(selected) != page) {
      pane.setComponentAt(selected, page);
    }
  }

  /** Mounts the child of the selected tab, and no other child. */
  private void syncChild() {
    child.sync(tabs.isEmpty() ? Optional.empty() : tabs.get(selected).child("child"));
    panel.revalidate();
    panel.repaint();
  }

  /**
   * Makes a title that a {@code JTabbedPane} shows as plain text. Swing shows a text that starts
   * with {@code <html>} as HTML. A title comes from the agent, and HTML from the agent can load
   * data from a network. A space before the text prevents this.
   */
  static String plainTitle(String title) {
    return title.regionMatches(true, 0, "<html>", 0, 6) ? " " + title : title;
  }

  /** The panel that has the view of the child of the selected tab. */
  private static final class Page extends FlexPanel {
    private static final long serialVersionUID = 1L;

    /** True while the pane measures the space that it adds around the page. */
    private boolean probing;

    Page() {
      super(FlexLayout.Axis.VERTICAL);
      // The child fills the page.
      flexLayout().setJustify(FlexLayout.Justify.STRETCH);
    }

    @Override
    public Dimension getPreferredSize() {
      return probing ? new Dimension(PROBE, PROBE) : super.getPreferredSize();
    }
  }

  /** The tabbed pane of the view. Its preferred height depends on its width. */
  private final class Pane extends JTabbedPane implements HeightForWidth {
    private static final long serialVersionUID = 1L;

    Pane() {
      // One line of headers has the same height at each width.
      super(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    }

    /**
     * Gets the size of the headers and of the borders that the look and feel adds around the page.
     * The layout of the pane calculates its preferred size from the preferred size of the page.
     * Thus a page of a known size gives the size of the other parts.
     */
    private Dimension chrome() {
      page.probing = true;
      try {
        Dimension size = getLayout().preferredLayoutSize(this);
        return new Dimension(Math.max(0, size.width - PROBE), Math.max(0, size.height - PROBE));
      } finally {
        page.probing = false;
      }
    }

    @Override
    public int preferredHeightFor(int width) {
      if (isPreferredSizeSet() || page.getParent() != this || getLayout() == null) {
        return getPreferredSize().height;
      }
      Dimension chrome = chrome();
      return chrome.height + page.preferredHeightFor(Math.max(0, width - chrome.width));
    }
  }
}
