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

package com.google.a2ui.swing.catalog.basic;

import com.google.a2ui.swing.catalog.HeightForWidth;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Function;
import javax.swing.JComponent;

/**
 * A layout that puts the components of a container in one line, for the {@code Row}, {@code Column}
 * and {@code List} components of the basic catalog.
 *
 * <p>The main axis is the direction of the line. The cross axis is the other direction.
 *
 * <ul>
 *   <li>Each component gets its preferred size on the main axis, with the gap between two
 *       components.
 *   <li>A component can have a weight. The constraint of the component gives it: {@code
 *       container.add(component, 2.0)}. The components that have a weight share the space that the
 *       other components and the gaps do not use, in proportion to their weights. Their preferred
 *       size on the main axis has no effect then.
 *   <li>If no component has a weight, {@link Justify} tells where the free space of the main axis
 *       goes.
 *   <li>{@link Align} gives the position and the size of each component on the cross axis. A
 *       component that has the client property {@link #STRETCH_PROPERTY} always fills the cross
 *       axis.
 *   <li>If the container is too small, the layout makes the components without a weight smaller,
 *       down to their minimum size. Then the components that have a weight get no space.
 * </ul>
 *
 * <p>The layout gives its width to a component that implements {@link HeightForWidth}, and gets the
 * height for that width. Thus text that wraps gets the correct height in one layout pass. A
 * container that uses this layout passes the question on with {@link #preferredHeight(Container,
 * int)}, as {@link FlexPanel} does.
 *
 * <p>The layout ignores a component that is not visible: the component gets no space and no gap. A
 * horizontal layout starts at the right side in a container whose component orientation is right to
 * left.
 *
 * <p>After a change of a property of the layout, call {@code revalidate()} on the container.
 */
public final class FlexLayout implements LayoutManager2 {
  /** The direction of the main axis. */
  public enum Axis {
    /** The components are side by side, as in a {@code Row}. */
    HORIZONTAL("horizontal"),
    /** Each component is below the component before it, as in a {@code Column}. */
    VERTICAL("vertical");

    private final String wireValue;

    Axis(String wireValue) {
      this.wireValue = wireValue;
    }

    /**
     * Gets the text that the basic catalog uses for this value.
     *
     * @return the text, for example {@code horizontal}
     */
    public String wireValue() {
      return wireValue;
    }

    /**
     * Finds the value for a text of the basic catalog.
     *
     * @param value the value of a property, for example the {@code direction} of a {@code List}
     * @return the axis, or an empty result if the value is not the text of an axis
     */
    public static Optional<Axis> fromWire(Object value) {
      return find(values(), Axis::wireValue, value);
    }
  }

  /** The use of the free space on the main axis. */
  public enum Justify {
    /** The components are together at the start. */
    START("start"),
    /** The components are together in the center. */
    CENTER("center"),
    /** The components are together at the end. */
    END("end"),
    /** The free space is between the components. The first and the last touch the edges. */
    SPACE_BETWEEN("spaceBetween"),
    /** Each component has the same space on its two sides. */
    SPACE_AROUND("spaceAround"),
    /** The spaces between the components and at the two edges are equal. */
    SPACE_EVENLY("spaceEvenly"),
    /** The components share the free space in equal parts and fill the main axis. */
    STRETCH("stretch");

    private final String wireValue;

    Justify(String wireValue) {
      this.wireValue = wireValue;
    }

    /**
     * Gets the text that the basic catalog uses for this value.
     *
     * @return the text, for example {@code spaceBetween}
     */
    public String wireValue() {
      return wireValue;
    }

    /**
     * Finds the value for a text of the basic catalog.
     *
     * @param value the value of the property {@code justify}
     * @return the value, or an empty result if the value is not the text of a value
     */
    public static Optional<Justify> fromWire(Object value) {
      return find(values(), Justify::wireValue, value);
    }
  }

  /** The position of each component on the cross axis. */
  public enum Align {
    /** Each component has its preferred size and is at the start. */
    START("start"),
    /** Each component has its preferred size and is in the center. */
    CENTER("center"),
    /** Each component has its preferred size and is at the end. */
    END("end"),
    /** Each component fills the cross axis. */
    STRETCH("stretch");

    private final String wireValue;

    Align(String wireValue) {
      this.wireValue = wireValue;
    }

    /**
     * Gets the text that the basic catalog uses for this value.
     *
     * @return the text, for example {@code stretch}
     */
    public String wireValue() {
      return wireValue;
    }

    /**
     * Finds the value for a text of the basic catalog.
     *
     * @param value the value of the property {@code align}
     * @return the value, or an empty result if the value is not the text of a value
     */
    public static Optional<Align> fromWire(Object value) {
      return find(values(), Align::wireValue, value);
    }
  }

  /**
   * The key of the client property of a {@code JComponent} that always fills the cross axis. The
   * value is {@code Boolean.TRUE}. Such a component ignores the {@link Align} of the layout, as an
   * element with {@code align-self: stretch} does in CSS. A divider uses it: its line always has
   * the size of its container.
   */
  public static final String STRETCH_PROPERTY = "a2ui.flex.stretch";

  /** The weight of each component that has one. A component that leaves the program goes away. */
  private final Map<Component, Double> weights = new WeakHashMap<>();

  private Axis axis;
  private Justify justify;
  private Align align;
  private int gap;

  /**
   * Makes a layout with the default values of the basic catalog: the components are at the start of
   * the main axis, they fill the cross axis, and there is no gap.
   *
   * @param axis the direction of the main axis
   */
  public FlexLayout(Axis axis) {
    this(axis, Justify.START, Align.STRETCH, 0);
  }

  /**
   * Makes a layout.
   *
   * @param axis the direction of the main axis
   * @param justify the use of the free space on the main axis
   * @param align the position of each component on the cross axis
   * @param gap the space between two components, in pixels
   * @throws IllegalArgumentException if the gap is less than zero
   */
  public FlexLayout(Axis axis, Justify justify, Align align, int gap) {
    setAxis(axis);
    setJustify(justify);
    setAlign(align);
    setGap(gap);
  }

  /**
   * Gets the direction of the main axis.
   *
   * @return the axis
   */
  public Axis axis() {
    return axis;
  }

  /**
   * Sets the direction of the main axis.
   *
   * @param axis the axis
   */
  public void setAxis(Axis axis) {
    this.axis = Objects.requireNonNull(axis, "axis");
  }

  /**
   * Gets the use of the free space on the main axis.
   *
   * @return the value
   */
  public Justify justify() {
    return justify;
  }

  /**
   * Sets the use of the free space on the main axis.
   *
   * @param justify the value
   */
  public void setJustify(Justify justify) {
    this.justify = Objects.requireNonNull(justify, "justify");
  }

  /**
   * Gets the position of each component on the cross axis.
   *
   * @return the value
   */
  public Align align() {
    return align;
  }

  /**
   * Sets the position of each component on the cross axis.
   *
   * @param align the value
   */
  public void setAlign(Align align) {
    this.align = Objects.requireNonNull(align, "align");
  }

  /**
   * Gets the space between two components.
   *
   * @return the gap in pixels
   */
  public int gap() {
    return gap;
  }

  /**
   * Sets the space between two components.
   *
   * @param gap the gap in pixels
   * @throws IllegalArgumentException if the gap is less than zero
   */
  public void setGap(int gap) {
    if (gap < 0) {
      throw new IllegalArgumentException("The gap must not be less than zero: " + gap);
    }
    this.gap = gap;
  }

  /**
   * Gets the weight of a component.
   *
   * @param component a component of the container
   * @return the weight, or 0 if the component has none
   */
  public double weightOf(Component component) {
    return weights.getOrDefault(component, 0.0);
  }

  /**
   * Sets the weight of a component. {@code Container.add(Component, Object)} calls this method.
   * Code can also call it to change the weight of a component that is in the container.
   *
   * @param component the component
   * @param constraints the weight as a {@code Number}, or null for a component that has no weight.
   *     A weight of 0 is the same as no weight.
   * @throws IllegalArgumentException if the constraint is not a number, or if the weight is less
   *     than zero or is not finite
   */
  @Override
  public void addLayoutComponent(Component component, Object constraints) {
    Objects.requireNonNull(component, "component");
    switch (constraints) {
      case null -> weights.remove(component);
      case Number number -> {
        double weight = number.doubleValue();
        if (!Double.isFinite(weight) || weight < 0) {
          throw new IllegalArgumentException("The weight must be zero or more: " + number);
        }
        if (weight == 0) {
          weights.remove(component);
        } else {
          weights.put(component, weight);
        }
      }
      default ->
          throw new IllegalArgumentException(
              "The constraint of a FlexLayout must be a Number or null: " + constraints);
    }
  }

  @Override
  public void addLayoutComponent(String name, Component component) {
    // A component that has a name and no constraint has no weight.
  }

  @Override
  public void removeLayoutComponent(Component component) {
    weights.remove(component);
  }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    return layoutSize(parent, true);
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    return layoutSize(parent, false);
  }

  @Override
  public Dimension maximumLayoutSize(Container target) {
    return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  @Override
  public float getLayoutAlignmentX(Container target) {
    return Component.CENTER_ALIGNMENT;
  }

  @Override
  public float getLayoutAlignmentY(Container target) {
    return Component.CENTER_ALIGNMENT;
  }

  @Override
  public void invalidateLayout(Container target) {
    // The layout keeps no sizes.
  }

  /**
   * Gets the preferred height of the content of a container at a width. The result is the height
   * that {@link #layoutContainer(Container)} needs to give each component its preferred size on the
   * vertical axis.
   *
   * <p>A container that uses this layout implements {@link HeightForWidth} with this method. Then
   * the text below the container wraps correctly.
   *
   * @param parent the container that has this layout
   * @param width the width of the container in pixels, with its insets
   * @return the height in pixels, with the insets of the container
   */
  public int preferredHeight(Container parent, int width) {
    synchronized (parent.getTreeLock()) {
      List<Component> items = visibleComponents(parent);
      Insets insets = parent.getInsets();
      int innerWidth = Math.max(0, width - insets.left - insets.right);
      int height = 0;
      if (axis == Axis.VERTICAL) {
        height = gap * Math.max(0, items.size() - 1);
        for (Component item : items) {
          height += heightAt(item, crossSizeOfWidth(item, innerWidth));
        }
      } else if (!items.isEmpty()) {
        int[] sizes = new int[items.size()];
        mainSizes(items, innerWidth, innerWidth, sizes);
        for (int i = 0; i < sizes.length; i++) {
          height = Math.max(height, heightAt(items.get(i), sizes[i]));
        }
      }
      return height + insets.top + insets.bottom;
    }
  }

  @Override
  public void layoutContainer(Container parent) {
    synchronized (parent.getTreeLock()) {
      List<Component> items = visibleComponents(parent);
      int count = items.size();
      if (count == 0) {
        return;
      }
      Insets insets = parent.getInsets();
      boolean horizontal = axis == Axis.HORIZONTAL;
      int innerWidth = Math.max(0, parent.getWidth() - insets.left - insets.right);
      int innerHeight = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
      int mainSpace = horizontal ? innerWidth : innerHeight;
      int crossSpace = horizontal ? innerHeight : innerWidth;

      int[] sizes = new int[count];
      int free = mainSizes(items, mainSpace, innerWidth, sizes);

      double lead = 0;
      double between = gap;
      if (free > 0) {
        switch (justify) {
          case START, STRETCH -> {}
          case CENTER -> lead = free / 2.0;
          case END -> lead = free;
          case SPACE_BETWEEN -> between += count > 1 ? (double) free / (count - 1) : 0;
          case SPACE_AROUND -> {
            double unit = (double) free / count;
            lead = unit / 2;
            between += unit;
          }
          case SPACE_EVENLY -> {
            double unit = (double) free / (count + 1);
            lead = unit;
            between += unit;
          }
        }
      }

      boolean mirror = horizontal && !parent.getComponentOrientation().isLeftToRight();
      double offset = lead;
      for (int i = 0; i < count; i++) {
        Component item = items.get(i);
        int position = (int) Math.round(offset);
        boolean stretch = stretches(item);
        int crossSize;
        if (stretch) {
          crossSize = crossSpace;
        } else if (horizontal) {
          // The height of a component can depend on the width that it got on the main axis.
          crossSize = Math.min(heightAt(item, sizes[i]), crossSpace);
        } else {
          crossSize = crossSizeOfWidth(item, crossSpace);
        }
        int crossPosition =
            stretch
                ? 0
                : switch (align) {
                  case START, STRETCH -> 0;
                  case CENTER -> (crossSpace - crossSize) / 2;
                  case END -> crossSpace - crossSize;
                };
        if (horizontal) {
          int x =
              mirror
                  ? parent.getWidth() - insets.right - position - sizes[i]
                  : insets.left + position;
          item.setBounds(x, insets.top + crossPosition, sizes[i], crossSize);
        } else {
          item.setBounds(insets.left + crossPosition, insets.top + position, crossSize, sizes[i]);
        }
        offset += sizes[i] + between;
      }
    }
  }

  /**
   * Calculates the size of each component on the main axis.
   *
   * @param items the visible components
   * @param mainSpace the space of the container on the main axis, without the insets
   * @param innerWidth the width of the container without the insets. A vertical layout uses it to
   *     get the height of each component.
   * @param sizes receives the size of each component
   * @return the space that stays free. It is not less than zero.
   */
  private int mainSizes(List<Component> items, int mainSpace, int innerWidth, int[] sizes) {
    int count = items.size();
    double[] itemWeights = new double[count];
    int[] minimums = new int[count];
    double totalWeight = 0;
    int used = gap * (count - 1);
    int shrinkable = 0;
    for (int i = 0; i < count; i++) {
      Component item = items.get(i);
      itemWeights[i] = weightOf(item);
      totalWeight += itemWeights[i];
      if (itemWeights[i] == 0) {
        sizes[i] =
            axis == Axis.HORIZONTAL
                ? item.getPreferredSize().width
                : heightAt(item, crossSizeOfWidth(item, innerWidth));
        minimums[i] = Math.min(main(item.getMinimumSize()), sizes[i]);
        used += sizes[i];
        shrinkable += sizes[i] - minimums[i];
      }
    }
    int free = mainSpace - used;
    if (free < 0) {
      // The container is too small. Each component without a weight gives a part of the space
      // between its preferred size and its minimum size.
      int missing = Math.min(-free, shrinkable);
      double[] shares = new double[count];
      for (int i = 0; i < count; i++) {
        shares[i] = itemWeights[i] == 0 ? sizes[i] - minimums[i] : 0;
      }
      if (shrinkable > 0) {
        share(-missing, shares, shrinkable, sizes);
      }
      return 0;
    }
    if (totalWeight > 0) {
      share(free, itemWeights, totalWeight, sizes);
      return 0;
    }
    if (justify == Justify.STRETCH) {
      double[] equal = new double[count];
      Arrays.fill(equal, 1);
      share(free, equal, count, sizes);
      return 0;
    }
    return free;
  }

  /**
   * Gets the width of a component of a vertical layout.
   *
   * @param item the component
   * @param innerWidth the width of the container without the insets
   */
  private int crossSizeOfWidth(Component item, int innerWidth) {
    return stretches(item) ? innerWidth : Math.min(item.getPreferredSize().width, innerWidth);
  }

  /** Tells if a component fills the cross axis. See {@link #STRETCH_PROPERTY}. */
  private boolean stretches(Component item) {
    return align == Align.STRETCH
        || item instanceof JComponent swing
            && Boolean.TRUE.equals(swing.getClientProperty(STRETCH_PROPERTY));
  }

  /** Gets the preferred height of a component at a width. */
  private static int heightAt(Component item, int width) {
    return item instanceof HeightForWidth wrapping
        ? wrapping.preferredHeightFor(width)
        : item.getPreferredSize().height;
  }

  /**
   * Adds a part of a space to the size of each component, in proportion to its share. The sum of
   * the parts is the space, to the pixel. A space that is less than zero makes the sizes smaller.
   */
  private static void share(int space, double[] shares, double totalShare, int[] sizes) {
    double given = 0;
    for (int i = 0; i < sizes.length; i++) {
      double before = given;
      given += space * shares[i] / totalShare;
      sizes[i] += (int) (Math.round(given) - Math.round(before));
    }
  }

  private Dimension layoutSize(Container parent, boolean preferred) {
    synchronized (parent.getTreeLock()) {
      List<Component> items = visibleComponents(parent);
      int mainSize = gap * Math.max(0, items.size() - 1);
      int crossSize = 0;
      for (Component item : items) {
        Dimension size = preferred ? item.getPreferredSize() : item.getMinimumSize();
        mainSize += main(size);
        crossSize = Math.max(crossSize, cross(size));
      }
      Insets insets = parent.getInsets();
      int width = axis == Axis.HORIZONTAL ? mainSize : crossSize;
      int height = axis == Axis.HORIZONTAL ? crossSize : mainSize;
      return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
    }
  }

  private static List<Component> visibleComponents(Container parent) {
    List<Component> items = new ArrayList<>(parent.getComponentCount());
    for (Component component : parent.getComponents()) {
      if (component.isVisible()) {
        items.add(component);
      }
    }
    return items;
  }

  private int main(Dimension size) {
    return axis == Axis.HORIZONTAL ? size.width : size.height;
  }

  private int cross(Dimension size) {
    return axis == Axis.HORIZONTAL ? size.height : size.width;
  }

  private static <E> Optional<E> find(E[] values, Function<E, String> wireValue, Object value) {
    for (E candidate : values) {
      if (wireValue.apply(candidate).equals(value)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  @Override
  public String toString() {
    return "FlexLayout(" + axis + ", " + justify + ", " + align + ", gap " + gap + ")";
  }
}
