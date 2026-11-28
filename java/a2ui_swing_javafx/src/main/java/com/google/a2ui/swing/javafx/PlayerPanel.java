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

package com.google.a2ui.swing.javafx;

import com.google.a2ui.core.basic_catalog.v1_0.UrlOpener;
import com.google.a2ui.core.common.Subscription;
import com.google.a2ui.swing.catalog.HeightForWidth;
import com.google.a2ui.swing.surface.ImageLoader;
import com.google.a2ui.swing.surface.MediaPlayer;
import com.google.a2ui.swing.surface.MediaUrls;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * The Swing component of a media view: the screen of a video, the controls, the description and the
 * message of a failure.
 *
 * <p>The controls are Swing components. Thus they have the look and feel of the application, the
 * user operates them with the keyboard, and assistive tools read their names. The names are {@link
 * #PLAY} or {@link #PAUSE}, {@link #SEEK}, {@link #MUTE} or {@link #UNMUTE}, and {@link #VOLUME}.
 * If the component has a description, each name has the description after a colon.
 *
 * <p>The panel puts its parts in a column, at most {@value #MAX_WIDTH} pixels wide. The screen has
 * the proportions 16 to 9. The height of the panel depends on its width, thus the panel implements
 * {@link HeightForWidth}.
 *
 * <p>The screen shows the poster image and a play mark until the media plays. A click on the screen
 * has the effect of the play control. When the panel leaves its window, or when the window hides
 * it, the panel releases the player. The user can then start the media again.
 */
final class PlayerPanel extends JPanel implements HeightForWidth {
  /** The largest width of the parts, in pixels. */
  static final int MAX_WIDTH = 480;

  /** The accessible name of the play control while the media does not play. */
  static final String PLAY = "Play";

  /** The accessible name of the play control while the media plays. */
  static final String PAUSE = "Pause";

  /** The accessible name of the slider of the position. */
  static final String SEEK = "Seek";

  /** The accessible name of the sound control while the sound is on. */
  static final String MUTE = "Mute";

  /** The accessible name of the sound control while the sound is off. */
  static final String UNMUTE = "Unmute";

  /** The accessible name of the slider of the volume. */
  static final String VOLUME = "Volume";

  /** The text of the button that opens the media in an external application. */
  static final String OPEN = "Open in an external application";

  /** The start of the message of a failure. */
  static final String FAILURE = "The media does not play. ";

  /** The text that shows in place of the time while the player loads the media. */
  static final String LOADING = "Loading...";

  private static final long serialVersionUID = 1L;
  private static final System.Logger LOG = System.getLogger("com.google.a2ui.swing.javafx");

  /** The space between two parts, in pixels. */
  private static final int GAP = 4;

  /** The smallest width at which the bar shows the slider of the volume, in pixels. */
  private static final int VOLUME_FROM = 340;

  private static final int VOLUME_WIDTH = 72;
  private static final int MIN_WIDTH = 160;

  private final transient PlayerModel model;
  private final transient ImageLoader imageLoader;
  private final transient UrlOpener urlOpener;

  /** The screen of a video, or null for audio. */
  private final Screen screen;

  private final JButton play = new JButton(PlayerIcon.PLAY);
  private final Track seek = new Track(0, 1, 0);
  private final JLabel time = new JLabel(clock(0));
  private final JButton mute = new JButton(PlayerIcon.SOUND);
  private final Track volume = new Track(0, 100, 0);
  private final Bar bar = new Bar();
  private final JLabel description = new JLabel();
  private final JLabel message = new JLabel();
  private final JButton open = new JButton(OPEN);

  private String descriptionText = "";
  private URI poster;
  private transient Subscription posterRequest;
  private transient Image posterImage;

  /** True while the panel writes the state of the model to the controls. */
  private boolean refreshing;

  /**
   * Makes the panel of a view with no media.
   *
   * @param kind the type of the media. A video has a screen.
   * @param engine the player
   * @param imageLoader loads the poster image
   * @param urlOpener opens the media in an external application, after a failure
   * @param startVolume the volume at the start, from 0 to 1
   */
  PlayerPanel(
      MediaPlayer.Kind kind,
      MediaEngine engine,
      ImageLoader imageLoader,
      UrlOpener urlOpener,
      double startVolume) {
    super(null);
    this.imageLoader = Objects.requireNonNull(imageLoader, "imageLoader");
    this.urlOpener = Objects.requireNonNull(urlOpener, "urlOpener");
    this.screen = kind == MediaPlayer.Kind.VIDEO ? new Screen(engine) : null;
    this.model =
        new PlayerModel(
            engine, screen == null ? null : screen::picture, startVolume, this::refresh);
    setOpaque(false);

    play.setName("a2ui.media.play");
    seek.setName("a2ui.media.seek");
    time.setName("a2ui.media.time");
    mute.setName("a2ui.media.mute");
    volume.setName("a2ui.media.volume");
    message.setName("a2ui.media.message");
    open.setName("a2ui.media.open");
    description.setName("a2ui.media.description");

    play.addActionListener(event -> model.toggle());
    mute.addActionListener(event -> model.setMuted(!model.muted()));
    seek.addChangeListener(event -> {
      if (!refreshing && !seek.getValueIsAdjusting()) {
        model.seek(seek.getValue());
      }
    });
    volume.addChangeListener(event -> {
      if (!refreshing) {
        model.setVolume(volume.getValue() / 100.0);
      }
    });
    open.addActionListener(event -> openExternally());
    // The panel releases the player when a window, a tab or a parent hides the panel.
    addHierarchyListener(event -> {
      if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) {
        model.suspend();
      }
    });

    bar.add(play);
    bar.add(seek);
    bar.add(time);
    bar.add(mute);
    bar.add(volume);
    if (screen != null) {
      add(screen);
    }
    add(bar);
    add(description);
    add(message);
    add(open);
    refresh();
  }

  /** Gets the state of the view. */
  PlayerModel model() {
    return model;
  }

  /**
   * Sets the title or the summary of the media. The panel shows it and adds it to the names of the
   * controls.
   *
   * @param text the description, or an empty text
   */
  void setDescription(String text) {
    String next = text == null ? "" : text.strip();
    if (!next.equals(descriptionText)) {
      descriptionText = next;
      refresh();
    }
  }

  /**
   * Sets the image that the screen shows before the media plays. The same URL as before loads
   * nothing. The panel examines the URL with {@code MediaUrls} again, and loads nothing for a URL
   * that is not safe.
   *
   * @param url the URL of the image, or an empty value for no image
   */
  void setPoster(Optional<URI> url) {
    URI next = url.filter(uri -> MediaUrls.safe(uri.toString()).isPresent()).orElse(null);
    if (screen == null || Objects.equals(next, poster)) {
      return;
    }
    cancelPoster();
    poster = next;
    if (next != null) {
      try {
        posterRequest = imageLoader.load(next, this::posterLoaded);
      } catch (RuntimeException e) {
        LOG.log(System.Logger.Level.WARNING, "The image loader did not load the poster.", e);
      }
    }
    screen.repaint();
  }

  private void posterLoaded(Optional<Image> image) {
    posterRequest = null;
    posterImage = image == null ? null : image.orElse(null);
    screen.repaint();
  }

  private void cancelPoster() {
    Subscription request = posterRequest;
    posterRequest = null;
    posterImage = null;
    if (request != null) {
      try {
        request.cancel();
      } catch (RuntimeException e) {
        LOG.log(System.Logger.Level.WARNING, "The image loader did not cancel a request.", e);
      }
    }
  }

  /** Releases the player and the request of the poster image. */
  void dispose() {
    model.dispose();
    cancelPoster();
    poster = null;
  }

  private void openExternally() {
    model
        .external()
        .ifPresent(uri -> {
          try {
            urlOpener.open(uri);
          } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "The URL opener did not open " + uri + ".", e);
          }
        });
  }

  /** Writes the state of the model to the controls. The model calls it after each change. */
  private void refresh() {
    refreshing = true;
    try {
      PlayerModel.Phase phase = model.phase();
      boolean plays = phase == PlayerModel.Phase.LOADING || phase == PlayerModel.Phase.PLAYING;
      play.setIcon(plays ? PlayerIcon.PAUSE : PlayerIcon.PLAY);
      play.setEnabled(model.canToggle());
      label(play, plays ? PAUSE : PLAY);

      boolean seekable = model.canSeek();
      seek.setEnabled(seekable);
      if (!seek.getValueIsAdjusting()) {
        int length = seekable ? Math.max(1, (int) Math.ceil(model.duration())) : 1;
        seek.setMaximum(length);
        seek.setValue(seekable ? (int) Math.round(model.position()) : 0);
      }
      label(seek, SEEK);
      time.setText(timeText(phase));

      mute.setIcon(model.muted() ? PlayerIcon.MUTED : PlayerIcon.SOUND);
      label(mute, model.muted() ? UNMUTE : MUTE);
      volume.setValue((int) Math.round(model.volume() * 100));
      label(volume, VOLUME);

      boolean failed = phase == PlayerModel.Phase.FAILED;
      boolean layoutChanged = false;
      layoutChanged |= show(description, !descriptionText.isEmpty());
      layoutChanged |= show(message, failed);
      layoutChanged |= show(open, failed && model.external().isPresent());
      description.setText(descriptionText);
      description.setToolTipText(descriptionText.isEmpty() ? null : descriptionText);
      String failure = failed ? FAILURE + model.message() : "";
      message.setText(failure);
      message.setToolTipText(failed ? failure : null);
      if (screen != null) {
        screen.sync();
      }
      if (layoutChanged) {
        revalidate();
      }
      repaint();
    } finally {
      refreshing = false;
    }
  }

  private static boolean show(JComponent component, boolean visible) {
    if (component.isVisible() == visible) {
      return false;
    }
    component.setVisible(visible);
    return true;
  }

  /** Gives a control its accessible name and its tool tip: what it does, and the description. */
  private void label(JComponent control, String function) {
    String name = descriptionText.isEmpty() ? function : function + ": " + descriptionText;
    control.getAccessibleContext().setAccessibleName(name);
    control.setToolTipText(function);
  }

  private String timeText(PlayerModel.Phase phase) {
    if (phase == PlayerModel.Phase.LOADING) {
      return LOADING;
    }
    double length = model.duration();
    String now = clock(model.position());
    return Double.isNaN(length) ? now : now + " / " + clock(length);
  }

  /**
   * Writes a time as minutes and seconds, with hours if there are some.
   *
   * @param seconds the time in seconds
   * @return the text, for example {@code 1:05} or {@code 1:02:03}
   */
  static String clock(double seconds) {
    long total = Double.isNaN(seconds) || seconds < 0 ? 0 : (long) Math.floor(seconds);
    long hours = total / 3600;
    long minutes = total % 3600 / 60;
    long rest = total % 60;
    return hours > 0
        ? String.format("%d:%02d:%02d", hours, minutes, rest)
        : String.format("%d:%02d", minutes, rest);
  }

  /** Gets the parts that show, from the top to the bottom. */
  private List<Component> rows() {
    List<Component> rows = new ArrayList<>();
    for (Component part : getComponents()) {
      if (part.isVisible()) {
        rows.add(part);
      }
    }
    return rows;
  }

  private int rowHeight(Component part, int width) {
    return part == screen
        ? Math.max(1, Math.round(width * 9 / 16f))
        : part.getPreferredSize().height;
  }

  private int contentWidth(int width) {
    Insets insets = getInsets();
    return Math.max(0, Math.min(width - insets.left - insets.right, MAX_WIDTH));
  }

  @Override
  public int preferredHeightFor(int width) {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize().height;
    }
    Insets insets = getInsets();
    int content = contentWidth(width);
    int height = insets.top + insets.bottom;
    List<Component> rows = rows();
    for (Component part : rows) {
      height += rowHeight(part, content);
    }
    return height + GAP * Math.max(0, rows.size() - 1);
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize();
    }
    Insets insets = getInsets();
    int width = MAX_WIDTH + insets.left + insets.right;
    return new Dimension(width, preferredHeightFor(width));
  }

  @Override
  public Dimension getMinimumSize() {
    if (isMinimumSizeSet()) {
      return super.getMinimumSize();
    }
    Insets insets = getInsets();
    int width = MIN_WIDTH + insets.left + insets.right;
    return new Dimension(width, preferredHeightFor(width));
  }

  @Override
  public void doLayout() {
    Insets insets = getInsets();
    int width = contentWidth(getWidth());
    int y = insets.top;
    for (Component part : rows()) {
      int height = rowHeight(part, width);
      int partWidth = part == open ? Math.min(width, part.getPreferredSize().width) : width;
      part.setBounds(insets.left, y, partWidth, height);
      y += height + GAP;
    }
  }

  /** Releases the player when the panel leaves its window. */
  @Override
  public void removeNotify() {
    super.removeNotify();
    model.suspend();
  }

  /** The row of the controls: the play control, the position, the time and the sound. */
  private final class Bar extends JPanel {
    private static final long serialVersionUID = 1L;

    Bar() {
      super(null);
      setOpaque(false);
    }

    @Override
    public Dimension getPreferredSize() {
      int height = 0;
      for (Component control : getComponents()) {
        height = Math.max(height, control.getPreferredSize().height);
      }
      return new Dimension(MAX_WIDTH, height);
    }

    @Override
    public void doLayout() {
      int width = getWidth();
      int height = getHeight();
      boolean wide = width >= VOLUME_FROM;
      if (volume.isVisible() != wide) {
        volume.setVisible(wide);
      }
      int left = place(play, 0, play.getPreferredSize().width, height) + GAP;
      int right = width;
      if (wide) {
        right -= VOLUME_WIDTH;
        place(volume, right, VOLUME_WIDTH, height);
        right -= GAP;
      }
      int muteWidth = mute.getPreferredSize().width;
      right -= muteWidth;
      place(mute, right, muteWidth, height);
      right -= GAP;
      int timeWidth = Math.min(time.getPreferredSize().width, Math.max(0, right - left));
      right -= timeWidth;
      place(time, right, timeWidth, height);
      right -= GAP;
      place(seek, left, Math.max(0, right - left), height);
    }

    /** Puts a control at a horizontal position, in the vertical center. Gets its right edge. */
    private int place(Component control, int x, int width, int height) {
      int controlHeight = Math.min(height, control.getPreferredSize().height);
      control.setBounds(x, (height - controlHeight) / 2, width, controlHeight);
      return x + width;
    }
  }

  /** A slider of the bar. */
  private static final class Track extends JSlider {
    private static final long serialVersionUID = 1L;

    Track(int minimum, int maximum, int value) {
      super(SwingConstants.HORIZONTAL, minimum, maximum, value);
      setOpaque(false);
    }

    /**
     * Gets the position of the mouse, or null on a computer with no display. Nimbus asks for the
     * position in each layout of a slider, and AWT throws a {@code HeadlessException} there.
     */
    @Override
    public Point getMousePosition() {
      return GraphicsEnvironment.isHeadless() ? null : super.getMousePosition();
    }
  }

  /**
   * The area of a video. It shows the picture of the engine while the media plays. Before that, it
   * paints the poster image and a play mark on a black background.
   */
  private final class Screen extends JComponent {
    private static final long serialVersionUID = 1L;

    /** The radius of the circle of the play mark, in pixels. */
    private static final int MARK_RADIUS = 22;

    private final transient MediaEngine engine;

    private final transient MouseAdapter click =
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            if (SwingUtilities.isLeftMouseButton(event)) {
              model.toggle();
            }
          }
        };

    /** The component of the engine that shows the picture, or null before the first start. */
    private JComponent picture;

    Screen(MediaEngine engine) {
      this.engine = engine;
      setName("a2ui.media.screen");
      addMouseListener(click);
    }

    /**
     * Gets the component of the engine that shows the picture. The first call makes it. The model
     * calls this method when the user starts the video. Thus the engine makes the component in an
     * event of the user, and not while the surface makes its views.
     */
    JComponent picture() {
      if (picture == null) {
        JComponent made =
            Objects.requireNonNull(engine.createScreen(), "the engine gave no screen");
        made.setVisible(false);
        made.addMouseListener(click);
        made.setCursor(getCursor());
        add(made);
        picture = made;
        revalidate();
      }
      return picture;
    }

    /** Shows or hides the picture of the engine, as the state of the model tells. */
    void sync() {
      Cursor cursor =
          model.canToggle()
              ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
              : Cursor.getDefaultCursor();
      setCursor(cursor);
      if (picture != null) {
        boolean shows = model.showsPicture();
        if (picture.isVisible() != shows) {
          picture.setVisible(shows);
        }
        picture.setCursor(cursor);
      }
    }

    @Override
    public void doLayout() {
      if (picture != null) {
        picture.setBounds(0, 0, getWidth(), getHeight());
      }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        int width = getWidth();
        int height = getHeight();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);
        Image image = posterImage;
        int imageWidth = image == null ? 0 : image.getWidth(null);
        int imageHeight = image == null ? 0 : image.getHeight(null);
        if (imageWidth > 0 && imageHeight > 0 && width > 0 && height > 0) {
          g.setRenderingHint(
              RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
          // The image fills the area and keeps its proportions. The area cuts off the rest.
          double scale = Math.max((double) width / imageWidth, (double) height / imageHeight);
          int w = (int) Math.round(imageWidth * scale);
          int h = (int) Math.round(imageHeight * scale);
          g.clipRect(0, 0, width, height);
          g.drawImage(image, (width - w) / 2, (height - h) / 2, w, h, null);
        }
        PlayerModel.Phase phase = model.phase();
        if (phase == PlayerModel.Phase.IDLE || phase == PlayerModel.Phase.ENDED) {
          paintMark(g, width, height);
        }
      } catch (RuntimeException e) {
        // A paint never fails the surface.
      } finally {
        g.dispose();
      }
    }

    /** Paints a circle with a triangle in the center of the area. */
    private void paintMark(Graphics2D g, int width, int height) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      double radius = Math.min(MARK_RADIUS, Math.min(width, height) / 3.0);
      double x = width / 2.0;
      double y = height / 2.0;
      // The mark is on an image of each color. Thus it has its own two colors.
      g.setColor(new Color(0, 0, 0, 150));
      g.fill(new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius));
      g.setColor(new Color(255, 255, 255, 110));
      g.draw(new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius));
      Path2D.Double triangle = new Path2D.Double();
      triangle.moveTo(x - radius * 0.3, y - radius * 0.45);
      triangle.lineTo(x + radius * 0.5, y);
      triangle.lineTo(x - radius * 0.3, y + radius * 0.45);
      triangle.closePath();
      g.setColor(Color.WHITE);
      g.fill(triangle);
    }
  }
}
