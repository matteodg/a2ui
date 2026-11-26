# A2UI Swing Gallery

This sample is a desktop application that shows the examples of the basic catalog with the Swing
adapter of the A2UI Java SDK, [`java/a2ui_swing`](../../../java/a2ui_swing). Use it to look at the
Swing views, to examine the data model of a surface and to see the messages that a surface sends to
the agent.

The gallery uses the A2UI protocol v1.0. It has no agent. Each example is a file with the messages
that an agent sends.

## Run the gallery

You need JDK 25 or later. Run this command from the [`java/`](../../../java) directory:

```sh
./mvnw -q -pl ../samples/client/swing -am compile exec:java
```

The command compiles the SDK and the sample, and then it opens the window. The sample is a module
of the Maven build of the SDK. Thus `./mvnw verify` in `java/` also builds and tests it.

The gallery reads the examples from
[`catalogs/basic/v1/examples`](../../../catalogs/basic/v1/examples) at run time. It has no copy of
them. To show the examples of a different directory, give the directory:

```sh
./mvnw -q -pl ../samples/client/swing -am compile exec:java -Dgallery.examples=/path/to/examples
```

An example file is a JSON object with a list `messages`, and optionally a `name` and a
`description`. A file can also be a list of messages, or one message.

These system properties are optional:

| Property             | Effect                                                                         |
| :------------------- | :----------------------------------------------------------------------------- |
| `gallery.examples`   | The directory of the example files.                                            |
| `gallery.example`    | The example to show first, for example `scaffold-simple-login-form`.           |
| `gallery.laf`        | The look and feel: `System`, `Metal` or `Nimbus`.                              |
| `gallery.tab`        | The tab of the inspector to show first: `Messages`, `Data model` or `Log`.     |
| `gallery.screenshot` | A PNG file. See [the screenshot mode](#the-screenshot-mode).                   |
| `gallery.size`       | Only in the screenshot mode: a new size for the window, for example `900x600`. |

## The three columns

The window has three columns, as the
[framework adapter blueprint](../../../blueprints/modules/a2ui_framework_adapter.blueprint.md)
specifies for a gallery.

1. **Examples.** The list of the example files. The examples that can show completely are first. An
   example in italic type has components with no Swing view. Its entry shows the number of those
   component types.
2. **Surface.** The surface of the selected example, in a scroll pane. The text breaks at the width
   of the column. The lines above the surface give the name and the description of the example and
   the component types that have no Swing view. If the processor rejects a message, the failure
   shows here in red, and the other messages continue.
   - **Reload** makes a new processor and processes all the messages again.
   - **Restart** makes a new processor that processes no message.
   - **Step** processes the subsequent message. Use it after **Restart** to see how a surface
     arrives in steps.
3. **Inspector.** Three tabs:
   - **Messages** shows the messages of the example as JSON text. A line above each message tells
     if the processor got it.
   - **Data model** shows the data model of the surface. It changes immediately when you edit a
     field.
   - **Log** shows each message that the renderer sends to the agent: an `action`, an `error` and
     the messages of the function calls. **Clear** removes the entries.

The menu **View** changes the look and feel while the application runs.

A component that the surface cannot show is visible as a label with a dashed border:

- `No Swing view: Gauge 'speed'` is a component whose type has no Swing view. Each component of
  the basic catalog has a view. Thus this label shows only for other component types.
- `Waiting for the component 'title'` is a component that did not arrive.

The `Modal` component shows its content in a dialog above the window. Select the example `modal`
and operate its button. Close the dialog with its close control or with the Escape key.

## Limits

- The Swing adapter has views for 18 of the 18 components of the basic catalog. Thus 43 of the 43
  examples show completely. The mark for a component with no view stays in the gallery, for a
  directory with examples that use other component types.
- Swing cannot play media. The `Video` component shows its poster image as a button, and the
  `AudioPlayer` component shows a button and its description. The button opens the URL in the
  browser of the system. See the section about the services in the
  [README of the adapter](../../../java/a2ui_swing/README.md).
- The gallery loads the images from the network with the default image loader of the adapter. An
  example with an image shows a neutral area while the image loads, and an area with a mark if the
  computer has no network. The tests do not use the network.
- The gallery shows the first surface of an example.
- The gallery has the examples of protocol v1.0 only, and it has no example with a custom catalog.
- The function `openUrl` opens the browser of the system. The tests use a different URL opener.

## How it works

Each selection of an example makes a new `MessageProcessor` with `SwingBasicCatalog` and
`ValidationConfig.RELAXED`. The relaxed configuration accepts a reference to a component that
arrives in a later message. `A2uiSwing.install(processor)` connects the processor to the event
dispatch thread. The gallery then gives the messages to the processor and puts an `A2uiSurface` for
the surface in the scroll pane.

| Class              | Function                                                                      |
| :----------------- | :---------------------------------------------------------------------------- |
| `GalleryApp`       | The main class. It finds the examples and opens the window.                   |
| `ExampleLoader`    | Reads the example files. It needs no Swing code.                              |
| `ViewAnalysis`     | Finds the component types of an example that have no Swing view.              |
| `GallerySession`   | One run of an example: the processor and the messages that it got.            |
| `GalleryPanel`     | The three columns. It needs no window, thus the tests use it with no display. |
| `ExampleListPanel` | The left column.                                                              |
| `SurfaceColumn`    | The center column.                                                            |
| `InspectorPanel`   | The right column.                                                             |
| `Placeholders`     | The `pending` and `error` fallbacks of the surface.                           |
| `GalleryFrame`     | The window and the menu.                                                      |
| `Screenshot`       | The screenshot mode.                                                          |

## Tests

The tests run with no display, in the build of the SDK. `GalleryExamplesTest` shows each example
file of the repository in a `GalleryPanel` and makes sure that the processor accepts each message
and that the surface shows. `GallerySnapshotTest` writes PNG images of some examples to
`target/snapshots/`. Look at the images after a change of the gallery or of a view. The build does
not compare them.

### The screenshot mode

The screenshot mode is a test of the adapter in a real window. It needs a display.

```sh
./mvnw -q -pl ../samples/client/swing -am compile exec:java \
  -Dgallery.screenshot=/tmp/gallery.png -Dgallery.example=scaffold-complex-layout
```

The program opens the window and shows the example. If the properties are there, it changes the
look and feel (`gallery.laf`) and then the size of the window (`gallery.size`), as a user does at
run time. After each step, it waits until the event dispatch thread is idle. Then it writes an image
of the window to the file and stops.

If the event dispatch thread is not idle after 10 seconds, the program stops with a message and
with the exit code 2. This occurs when a layout requests a new layout without end.

The program paints the window on an image. It does not copy the screen. The image has the scale of
the screen of the window. Thus, on a screen with a scale of 150 %, a window of 1280 x 800 gives an
image of 1920 x 1200 pixels, and the text has the same widths as on the screen.
