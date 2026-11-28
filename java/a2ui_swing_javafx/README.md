# A2UI Java Swing JavaFX Media (`a2ui-java-swing-javafx`)

This module is an optional part of the A2UI Java client SDK. It plays the `Video` and `AudioPlayer`
components of a surface in the [Swing adapter](../a2ui_swing), with the media player of JavaFX.

Swing cannot play media. Thus the Swing adapter has a `mediaPlayer` service, and its default shows
a substitute that opens the URL in an external application. This module has a `MediaPlayer` for
that service. An application that does not use this module needs no JavaFX library.

The module implements the A2UI protocol v1.0, as the Swing adapter does.

## Use the player

Add the module to the dependencies of the application:

```xml
<dependency>
  <groupId>com.google.a2ui</groupId>
  <artifactId>a2ui-java-swing-javafx</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Then give a `JavaFxMediaPlayer` to the services. Give the same services to the catalog and to each
surface.

```java
A2uiServices base = A2uiServices.DEFAULT;
A2uiServices services =
    base.withMediaPlayer(
        JavaFxMediaPlayer.builder()
            .imageLoader(base.imageLoader())
            .urlOpener(base.urlOpener())
            .build());

MessageProcessor<SwingComponentImplementation> processor =
    new MessageProcessor<>(List.of(SwingBasicCatalog.create(Locale.getDefault(), services)));
A2uiSwing.install(processor);
processor
    .model()
    .onSurfaceCreated()
    .subscribe(surface -> show(new A2uiSurface(surface, A2uiFallbacks.NONE, services)));
```

`new JavaFxMediaPlayer()` makes a player with the default options. The builder has these options:

| Option        | Default                              | Function                                                          |
| :------------ | :----------------------------------- | :---------------------------------------------------------------- |
| `imageLoader` | The loader of `A2uiServices.DEFAULT` | Loads the poster image of a video.                                |
| `urlOpener`   | The opener of `A2uiServices.DEFAULT` | Opens a media in an external application, after a failure.        |
| `volume`      | `1`                                  | The volume of each new view, from 0 to 1. The user can change it. |

The Java module is `com.google.a2ui.swing.javafx`. Its one public class is
`com.google.a2ui.swing.javafx.JavaFxMediaPlayer`. The module also declares the class as a provider
of the service `com.google.a2ui.swing.surface.MediaPlayer`. Thus an application can find the player
with `ServiceLoader.load(MediaPlayer.class)` and needs no compile dependency on this module. The
[gallery sample](../../samples/client/swing) does this with its Maven profile `javafx`.

## What the user sees

- **Video.** A screen with the proportions 16 to 9, at most 480 pixels wide, and a row of controls
  below it. The screen shows the poster image and a play mark until the video plays. A click on the
  screen has the effect of the play control.
- **AudioPlayer.** The row of controls, and the description of the component below it.
- **Controls.** A play and pause button, a slider for the position, the time, a mute button and a
  slider for the volume. They are Swing components. Thus they have the look and feel of the
  application, and the user operates them with the keyboard: the Tab key moves between them, the
  space bar operates a button, and the arrow keys move a slider. A bar that is less than 340 pixels
  wide has no slider for the volume.
- **Failure.** If JavaFX cannot play the media, the view shows the message of JavaFX. For an `http`
  or `https` URL it also shows the button **Open in an external application**, which uses the
  `urlOpener` service. The play control tries again. A failure never fails the surface.

The accessible names of the controls are `Play` or `Pause`, `Seek`, `Mute` or `Unmute`, and
`Volume`. If the component has a description, each name has it after a colon, for example
`Play: Episode 1`. The Swing adapter gives the `@accessibility` attributes to the panel of the
view. The attribute `hidden` makes the full view inert, also the screen: the adapter keeps the
mouse and the keyboard focus out of it.

## Rules of the player

- **No start without the user.** The player starts a media only when the user operates the play
  control or the screen. Before that, it sends no request to the URL of the media. Thus the view
  does not know the length of a media before the user starts it.
- **URLs.** The Swing adapter examines each URL of the agent with `MediaUrls` before it calls the
  player. The player examines the URL again, and plays only an absolute `http` or `https` URL with
  a host. JavaFX cannot read a `data` URL, and a relative path has no base location in this module.
  For these two, the view shows a message and no button. JavaFX can also read `file` and `jar`
  URLs, but `MediaUrls` refuses them, and this module refuses them too.
- **Release.** A view stops and disposes its JavaFX player when the component goes away, when the
  URL changes, when the view leaves its window, and when a window or a parent hides the view. The
  user can then start the media again, from its start.
- **No JavaFX.** If JavaFX cannot operate, `createView` gives an empty result, and the components
  show the substitute of the Swing adapter. This occurs when the computer has no display
  (`GraphicsEnvironment.isHeadless()`), when the JavaFX classes or their native libraries are
  absent, and when the toolkit does not start in 15 seconds. The player writes one log record
  then, to the logger `com.google.a2ui.swing.javafx`.

## Media formats

JavaFX decides which media it can play. The documentation of the package `javafx.scene.media` of
OpenJFX 27 gives these combinations:

| Container | Video                   | Audio | File extension         |
| :-------- | :---------------------- | :---- | :--------------------- |
| MP4       | H.264/AVC or H.265/HEVC | AAC   | `.mp4`, `.m4a`, `.m4v` |
| MP3       | None                    | MP3   | `.mp3`                 |
| WAV       | None                    | PCM   | `.wav`                 |
| AIFF      | None                    | PCM   | `.aif`, `.aiff`        |
| HLS       | H.264/AVC or H.265/HEVC | AAC   | `.m3u8`                |
| HLS       | None                    | MP3   | `.m3u8`                |

The documentation lists no other container, for example no WebM and no Ogg. The codecs that are
available also depend on the operating system.

## OpenJFX and its license

The module uses OpenJFX 27.0.1.1 (`org.openjfx:javafx-media` and `org.openjfx:javafx-swing`, which
bring `javafx-graphics` and `javafx-base`). The property `openjfx.version` in
[`java/pom.xml`](../pom.xml) has the version. OpenJFX 27 needs JDK 25 or later.

OpenJFX has the license GPL-2.0 with the Classpath exception. The other modules of this SDK have
the Apache License 2.0 and do not depend on OpenJFX.

Each OpenJFX artifact has an empty main JAR file and one JAR file for each platform, with the
classifier `win`, `linux`, `linux-aarch64`, `mac` or `mac-aarch64`. That file has the classes and
the native libraries. The parent POM of OpenJFX has a Maven profile for each operating system and
processor, and the profile sets the classifier. Thus Maven downloads the files of the computer that
runs the build, and no manual step is necessary. The four files for Windows have 8.2 MB together.

An application that makes one package for more than one platform declares the dependencies with
each classifier that it needs.

## Threads

- All Swing work is on the event dispatch thread (EDT). Use `JavaFxMediaPlayer` only on the EDT, as
  each service of the adapter.
- All JavaFX work is on the JavaFX application thread, with `Platform.runLater`. Each event of a
  JavaFX player goes to the EDT with `SwingUtilities.invokeLater`. No thread waits for the other
  thread.
- The first call of `createView` starts the JavaFX toolkit on a helper thread and waits for the
  start one time. On the test computer that took less than one second. The player sets
  `Platform.setImplicitExit(false)`.
- A video view makes its `JFXPanel` when the user starts the video for the first time, and not
  when the surface makes the view.
- The JavaFX application thread keeps the JVM in operation. An application that uses the player
  calls `System.exit` or `javafx.application.Platform.exit()` when it stops.

## Limits

- The player has no captions, no full-screen mode and no control for the speed.
- The description of an `AudioPlayer` is one line. A description that is longer than the view
  shows its start, and the tool tip has the full text.
- JavaFX makes the HTTP connections for the media itself. The limits of the image loader of the
  adapter do not apply to them. JavaFX tells that a media is absent only after 5 attempts, which
  takes 5 seconds.
- A view that the user moves to a different window, or that a tab hides, releases its player. The
  position of the media does not stay.
- If JavaFX is on the class path and not on the module path, it writes the warning
  `Unsupported JavaFX configuration` one time. JDK 25 also writes a warning about native access.
  The option `--enable-native-access=javafx.graphics,javafx.media` (or `ALL-UNNAMED` on the class
  path) removes it.
- The real playback was examined only on Windows 11 with the JDK 25. Linux and macOS were not
  examined. On Linux, JavaFX media uses codec libraries of the system.

## Tests

The usual build (`./mvnw verify` in [`java/`](..)) runs the tests of this module with
`java.awt.headless=true`. They need no display, no JavaFX toolkit, no native media library and no
network. Thus a build server needs nothing more for this module.

The controls use the player through a small internal interface, `MediaEngine`. `JavaFxEngine` is
its JavaFX implementation, and the tests have an implementation that plays nothing.

| Test                    | What it examines                                                                        |
| :---------------------- | :-------------------------------------------------------------------------------------- |
| `PlayerModelTest`       | The states of a view, the URL rule, the failures, the release, and old events.          |
| `RelayTest`             | The move of the events from the thread of the player to the EDT.                        |
| `PlayerPanelTest`       | The Swing controls, their names, the layout, the poster and the message of a failure.   |
| `JavaFxMediaPlayerTest` | The public class, the service provider, the views in a surface, and the case no JavaFX. |
| `JavaFxEngineTest`      | The real JavaFX player. See below.                                                      |

`PlayerPanelTest` and `JavaFxMediaPlayerTest` write images of the views to `target/snapshots/`. A
person looks at them. The build does not compare them.

`JavaFxEngineTest` has the JUnit tag `javafx`, and the usual build does not run it. It starts the
JavaFX toolkit and plays a WAV file (PCM, 16 bits) from a `file` URL. The test writes the file, and
the file has no sound. The test needs a display and the native libraries of JavaFX. If the toolkit
cannot start, JUnit records the tests as skipped. To run only these tests, use the Maven profile
`javafx-it`:

```sh
./mvnw -pl a2ui_swing_javafx -am -Pjavafx-it verify -Dtest=JavaFxEngineTest -Dsurefire.failIfNoSpecifiedTests=false
```

## References

- The Swing adapter and its services: [`java/a2ui_swing/README.md`](../a2ui_swing/README.md)
- The rules for media URLs:
  [catalogs/basic/v1/basic_catalog_implementation_guide.md](../../catalogs/basic/v1/basic_catalog_implementation_guide.md)
- OpenJFX: <https://openjfx.io>
