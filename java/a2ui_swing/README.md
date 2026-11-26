# A2UI Java Swing (`a2ui-java-swing`)

This module is the Swing adapter of the A2UI Java client SDK. It shows the surfaces of
[`a2ui-java-core`](../a2ui_core) with Swing components. It depends only on the core and on the JDK
module `java.desktop`.

The module implements the A2UI protocol v1.0. The design obeys
[blueprints/modules/a2ui_framework_adapter.blueprint.md](../../blueprints/modules/a2ui_framework_adapter.blueprint.md).

This version has the foundations of the adapter: the surface component, the node dispatcher, the
types for component implementations, the layout of the containers and the services. It does not
have the views of the components of the basic catalog. A later version adds them. Until then, an
application supplies its own component implementations.

## Packages

The Java module name is `com.google.a2ui.swing`. It exports these packages:

| Package                               | Contents                                                                                                                                                                                                                                                           |
| :------------------------------------ | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `com.google.a2ui.swing.surface`       | `A2uiSurface`, the Swing component that shows one surface. `A2uiFallbacks` and `A2uiServices` are its options. `A2uiSwing` has the helpers for the event dispatch thread. `MountedNode` and `A2uiAccessibility` let a test or a tool examine what a surface shows. |
| `com.google.a2ui.swing.catalog`       | `SwingComponentImplementation`, a component definition that makes a view. `SwingComponentView` is the view of one component. `ViewContext` and `ChildViews` are the objects that the adapter gives to a view.                                                      |
| `com.google.a2ui.swing.binding`       | `TextBinding` connects a Swing text component to a property in the two directions.                                                                                                                                                                                 |
| `com.google.a2ui.swing.catalog.basic` | `FlexLayout`, the layout of the `Row`, `Column` and `List` components of the basic catalog. A custom container can also use it.                                                                                                                                    |

The package `com.google.a2ui.swing.nodes` has the node dispatcher and the child reconciliation. It
is not part of the API, and the module does not export it.

## Show a surface

A component implementation is a component definition of the core that can also make a Swing view.
It is an entry of a catalog. Thus the adapter has no registry of its own.

```java
SwingComponentImplementation greeting =
    SwingComponentImplementation.of(
        "Greeting",
        Map.of(
            "type", "object",
            "properties",
                Map.of(
                    "component", Map.of("const", "Greeting"),
                    "text", Map.of("$ref", "common_types.json#/$defs/DynamicString")),
            "required", List.of("component", "text")),
        context -> {
          JLabel label = new JLabel();
          return SwingComponentView.of(
              label, (node, props) -> label.setText(Coercion.toText(props.value("text"))));
        });
Catalog<SwingComponentImplementation, FunctionApi> catalog =
    new Catalog<>(
        "https://example.com/catalogs/demo/v1/catalog.json",
        List.of(greeting),
        List.of(),
        null,
        ProtocolVersion.V1_0);
```

The application owns the `MessageProcessor`. An `A2uiSurface` only shows one `SurfaceModel`.

```java
SwingUtilities.invokeLater(() -> {
  MessageProcessor<SwingComponentImplementation> processor =
      new MessageProcessor<>(List.of(catalog));
  A2uiSwing.install(processor);

  JFrame frame = new JFrame("A2UI");
  processor
      .model()
      .onSurfaceCreated()
      .subscribe(surface -> {
        frame.setContentPane(new A2uiSurface(surface));
        frame.revalidate();
      });
  frame.setSize(480, 320);
  frame.setVisible(true);

  processor.processMessages(
      Map.of(
          "version", "1.0",
          "createSurface",
              Map.of(
                  "surfaceId", "main",
                  "catalogId", catalog.id(),
                  "components",
                      List.of(Map.of("id", "root", "component", "Greeting", "text", "Hello")))));
});
```

`A2uiSurface` makes its node resolver and its views when Swing connects it to a window
(`addNotify`). It disposes them when Swing removes it (`removeNotify`). Thus the application
disposes nothing. A surface component that the application adds again makes new views from the
model. Then the state that only a view has is lost, for example a scroll position. When the
processor deletes the surface, the surface component becomes empty.

To add the views to a catalog that only describes its components, use `Catalog.withComponents`.

## Threads

Use the processor, the surfaces and the views only on the event dispatch thread (EDT). The core is
not thread-safe, and Swing has one UI thread. The adapter subscribes Swing code to the signals of
the core directly. Thus the screen is up to date when `processMessages` returns. `A2uiSurface`
throws an `IllegalStateException` on each other thread.

- `A2uiSwing.install(processor)` gives the RPC handler of the processor a scheduler that uses
  `javax.swing.Timer`. Thus the timeout of a call to the agent completes on the EDT. It also sets
  the user activation test of the handler. The test gives true while the EDT dispatches a mouse
  button event or a key event.
- `A2uiSwing.process(processor, payload)` runs `processMessages` on the EDT and waits. Each thread
  can call it. A transport that has its own thread uses it.
- `A2uiSwing.processLater(processor, payload)` does not wait. It returns a `CompletableFuture`.

## Write a component

A `SwingComponentView` has state. The adapter makes it one time for a component that a surface
shows. Then the adapter calls `update(node, props)` after each change of the properties, and
`dispose()` when the surface stops to show the component. The Swing component of a view is the same
object for the life of the view. Thus a text field keeps its caret and its focus.

These rules apply to a view:

- Read the properties only from the `props` of `update`. Do not use a `DataContext`.
- Write the input of the user through the `WritableBinding` of a property while the user edits.
  When the properties come back with the value that the control shows, do not touch the control.
  `TextBinding` does this for a text component.
- Call a `NodeAction` in the Swing listener of the gesture, and not later.
- Take colors, fonts and borders from the look and feel (`UIManager`). Protocol v1.0 has no theme.
- Do not make the views of children. Give the child nodes to the `ChildViews` helper.

This view is a text field with a two-way binding. It shows the messages of its failed checks as a
tool tip.

```java
final class FieldView implements SwingComponentView {
  private final JTextField field = new JTextField(12);
  private final TextBinding value = new TextBinding(field);

  @Override
  public JComponent component() {
    return field;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    value.update(props, "value");
    List<String> errors = props.validationErrors();
    field.setToolTipText(errors.isEmpty() ? null : String.join(" ", errors));
  }

  @Override
  public void dispose() {
    value.dispose();
  }
}
```

This view is a button. It does its action in the listener, and it is disabled while a check fails.

```java
final class ButtonView implements SwingComponentView {
  private final JButton button = new JButton();
  private NodeAction action;

  ButtonView() {
    button.addActionListener(event -> {
      if (action != null) {
        action.invoke();
      }
    });
  }

  @Override
  public JComponent component() {
    return button;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    button.setText(Coercion.toText(props.value("label")));
    button.setEnabled(props.isValid());
    action = props.action("action").orElse(null);
  }
}
```

### Children

A container gets a `ChildViews` helper from its `ViewContext` and gives it the child nodes in each
update. The helper keeps one view for each child position, which the `instanceId` of the node
names. It moves and keeps the Swing component of a child that stays. It disposes the view of a
child that leaves. It puts no component in the container for a child that shows nothing, for
example a component that did not arrive. It changes the Swing container only where the list
changed.

A function gives the layout constraint of each child. The helper calls it again after each update
of the child.

```java
final class BoxView implements SwingComponentView {
  private final JPanel panel = new JPanel(new FlexLayout(FlexLayout.Axis.VERTICAL));
  private final ChildViews children;

  BoxView(ViewContext context) {
    children = context.children(panel, child -> child.props().get().value("weight"));
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public void update(ComponentNode<?> node, NodeProps<?> props) {
    children.sync(props.children("children"));
  }
}
```

A component that has one child, for example a button, calls `children.sync(props.child("child"))`.

### FlexLayout

`FlexLayout` is a `LayoutManager2` that puts components in one line.

| Property  | Values                                                                               |
| :-------- | :----------------------------------------------------------------------------------- |
| `axis`    | `HORIZONTAL`, `VERTICAL`                                                             |
| `justify` | `START`, `CENTER`, `END`, `SPACE_BETWEEN`, `SPACE_AROUND`, `SPACE_EVENLY`, `STRETCH` |
| `align`   | `START`, `CENTER`, `END`, `STRETCH`                                                  |
| `gap`     | The space between two components, in pixels.                                         |
| weight    | The layout constraint of a component: `panel.add(component, 2.0)`. The default is 0. |

The components that have a weight share the space that the other components do not use, in
proportion to their weights. `Justify.STRETCH` gives the free space to all the components in equal
parts. Each enum reads the text of the basic catalog: `Justify.fromWire("spaceBetween")`.

## Fallbacks

A component that did not arrive, a component of an unknown type and a reference that makes a cycle
show nothing and use no layout space. An application can show its own Swing component for them:

```java
A2uiFallbacks fallbacks =
    A2uiFallbacks.NONE
        .withPending(info -> new JLabel("Loading " + info.componentId()))
        .withError(info -> new JLabel("Cannot show " + info.componentId()));
A2uiSurface view = new A2uiSurface(surface, fallbacks);
```

A fallback returns a new component for each call, or null to show nothing. The surface also uses
the `pending` fallback for the root before the root arrives. A fallback only changes what the user
sees. The core reports each error to the agent.

## Services

`A2uiServices` has the extension points for the functions that plain Swing does not have. Each
service has a default. `A2uiServices.DEFAULT.withImageLoader(loader)` replaces one service.

| Service          | Interface          | Default                                                                                         |
| :--------------- | :----------------- | :---------------------------------------------------------------------------------------------- |
| `markdown`       | `MarkdownRenderer` | Shows the text with its Markdown marks. The version that adds the `Text` component replaces it. |
| `imageLoader`    | `ImageLoader`      | Reads `http`, `https` and `data` URLs with `ImageIO`, not on the EDT. It has no cache.          |
| `mediaPlayer`    | `MediaPlayer`      | `MediaPlayer.NONE`. Swing cannot play media. An application can supply a JavaFX or vlcj player. |
| `modalPresenter` | `ModalPresenter`   | Shows a modal `JDialog` above the window of the surface. It needs a display.                    |
| `urlOpener`      | `UrlOpener`        | Opens the URL in the browser of the system with `java.awt.Desktop`.                             |

A view gets the services from `ViewContext.services()`.

## Accessibility

The adapter gives the `@accessibility` attributes of each component to the Swing component of its
view, after each update. A view does not do this work.

- `label` is the accessible name, and `description` is the accessible description of the
  `AccessibleContext`.
- Without a label, the name is the result of `SwingComponentView.inferredAccessibleName()`. A view
  overrides the method to give a name from its visible text, for example the text of the child of a
  button. An explicit label is before the inferred name.
- The Java Accessibility API has no equivalent of `hidden` and of `live`. The adapter keeps all the
  attributes as a client property of the Swing component. `A2uiAccessibility.of(component)` reads
  them. A hidden component also gets an empty accessible name. A live component fires an accessible
  property change after each update. A screen reader can ignore the two.
- A view whose control is in a panel overrides `accessibleComponent()` and returns the control.

## Tests

The tests run with no display (`-Djava.awt.headless=true`). A test runs its Swing code on the EDT.
It connects an `A2uiSurface` with a call of `addNotify()`, in a parent that is not in a window.
Then `validate()` does the layout, and `print(Graphics)` paints the component on an image. The test
helpers `Edt`, `Headless` and `Snapshots` in `src/test/java` do this work.

`SurfaceSnapshotTest` writes PNG images of a surface to `target/snapshots/`. Look at the images
after a change of a view or of a layout. The build does not compare them.

The conformance harness of this module has the name `swing`. It registers each suite of the
`adapter` corpus of [`conformance/`](../../conformance). A case of the two rendering suites runs
only if the adapter has a view for each basic catalog component that the case uses. In this version
the adapter has no such view. Thus the harness records each case as skipped, with its reason. See
[the conformance section](../README.md#conformance) for the commands.

## References

- Adapter design:
  [blueprints/modules/a2ui_framework_adapter.blueprint.md](../../blueprints/modules/a2ui_framework_adapter.blueprint.md)
- Basic catalog:
  [catalogs/basic/v1/basic_catalog_implementation_guide.md](../../catalogs/basic/v1/basic_catalog_implementation_guide.md)
