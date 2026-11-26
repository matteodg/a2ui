# A2UI Java Swing (`a2ui-java-swing`)

This module is the Swing adapter of the A2UI Java client SDK. It shows the surfaces of
[`a2ui-java-core`](../a2ui_core) with Swing components. It depends only on the core and on the JDK
module `java.desktop`.

The module implements the A2UI protocol v1.0. The design obeys
[blueprints/modules/a2ui_framework_adapter.blueprint.md](../../blueprints/modules/a2ui_framework_adapter.blueprint.md).

This version has the surface component, the node dispatcher, the types for component
implementations, the layout of the containers, the services, and the basic catalog with the views of
five components: `Text`, `Row`, `Column`, `Button` and `TextField`. A later version adds the views
of the other components. See [the basic catalog](#the-basic-catalog).

The sample application [`samples/client/swing`](../../samples/client/swing) is a gallery that shows
the examples of the basic catalog with this module.

## Quick start

```java
SwingUtilities.invokeLater(() -> {
  MessageProcessor<SwingComponentImplementation> processor =
      new MessageProcessor<>(List.of(SwingBasicCatalog.create()));
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

  // Give each message of the agent to the processor.
  processor.processMessages(message);
});
```

## Packages

The Java module name is `com.google.a2ui.swing`. It exports these packages:

| Package                               | Contents                                                                                                                                                                                                                                                           |
| :------------------------------------ | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `com.google.a2ui.swing.surface`       | `A2uiSurface`, the Swing component that shows one surface. `A2uiFallbacks` and `A2uiServices` are its options. `A2uiSwing` has the helpers for the event dispatch thread. `MountedNode` and `A2uiAccessibility` let a test or a tool examine what a surface shows. |
| `com.google.a2ui.swing.catalog`       | `SwingComponentImplementation`, a component definition that makes a view. `SwingComponentView` is the view of one component. `ViewContext` and `ChildViews` are the objects that the adapter gives to a view.                                                      |
| `com.google.a2ui.swing.binding`       | `TextBinding` connects a Swing text component to a property in the two directions. `ValueBinding` does the same for a control that is not text, for example a check box.                                                                                           |
| `com.google.a2ui.swing.catalog.basic` | `SwingBasicCatalog` makes the basic catalog with its Swing views. `FlexLayout` and `FlexPanel` are the layout of the `Row`, `Column` and `List` components. A custom container can also use them.                                                                  |

The package `com.google.a2ui.swing.nodes` has the node dispatcher and the child reconciliation. It
is not part of the API, and the module does not export it.

## The basic catalog

`SwingBasicCatalog.create()` makes the basic catalog of protocol v1.0: the functions of the core
with their code, and each component of the catalog document. `create(locale)` sets the locale of the
format functions. `create(locale, services)` also takes the URL opener of the function `openUrl`
from the services. Give the same services to the `A2uiSurface`.

| Component       | Swing component                                                     | State     |
| :-------------- | :------------------------------------------------------------------ | :-------- |
| `Text`          | The view of the `markdown` service. The default is a `JEditorPane`. | Available |
| `Row`           | `FlexPanel` with a horizontal `FlexLayout`                          | Available |
| `Column`        | `FlexPanel` with a vertical `FlexLayout`                            | Available |
| `List`          | `FlexPanel` in a `JScrollPane`                                      | Available |
| `Card`          | A `FlexPanel` that paints a rounded outline                         | Available |
| `Tabs`          | `JTabbedPane`                                                       | Available |
| `Modal`         | A `FlexPanel` for the trigger, and the `modalPresenter` service     | Available |
| `Divider`       | `JSeparator`                                                        | Available |
| `Button`        | `JButton` with the view of the child in it                          | Available |
| `TextField`     | `JTextField`, `JTextArea` in a `JScrollPane`, or `JPasswordField`   | Available |
| `CheckBox`      | `JCheckBox` with a label that wraps                                 | Available |
| `ChoicePicker`  | `JRadioButton`, `JCheckBox` or `JToggleButton` for each option      | Available |
| `Slider`        | `JSlider` with the value next to it                                 | Available |
| `DateTimeInput` | `JSpinner` with a text field for ISO 8601 text                      | Available |
| `Image`         | A component that paints the image of the `imageLoader` service      | Available |
| `Icon`          | A component that paints a shape of the module or SVG path data      | Available |
| `Video`         | The view of the `mediaPlayer` service, or a button with the poster  | Available |
| `AudioPlayer`   | The view of the `mediaPlayer` service, or a button and the text     | Available |

Each component of the catalog has a view. An application can declare a component that has no view
(`SwingComponentImplementation.withoutView`). A message that uses such a component is correct. The
surface shows its `error` fallback for it, with the error type `no-view`, or nothing. Then the
component uses no layout space. The adapter reports nothing to the agent.
`SwingBasicCatalog.viewNames()` gives the components that have a view.

These rules apply to the views:

- **Text.** The default Markdown view shows the Markdown subset of the protocol: headings, emphasis,
  strong text, lists, code, quotes and rules. It shows HTML as plain text. It shows a link and an
  image as their text only. The text breaks at the width of the component. The variant `caption`
  has 0.8 times the font size and a weaker color.
- **Text style.** The base font and the text color are those of the Swing component above a view.
  Thus `surface.setFont(font)` and `surface.setForeground(color)` on the `A2uiSurface` set the text
  style of the full surface. A button gives its text color to its content in the same way.
- **Spacing.** A `Row`, a `Column` and a `List` have no padding, no gap and no background. Each
  `Text`, `Divider`, `Card`, `Tabs`, `Button`, `TextField`, `CheckBox`, `ChoicePicker`, `Slider` and
  `DateTimeInput` has a margin of 8 pixels on its four sides. Text in a button has no margin. This is the leaf margin strategy of the implementation
  guide.
- **Row and Column.** `justify`, `align` and the `weight` of each child go to the `FlexLayout`.
  With the default `align`, `stretch`, a child fills the cross axis. Thus a button in a column is as
  wide as the column. Use `align: start` for a button with its preferred width.
- **Card.** The card paints a rounded outline with a border color of the look and feel. It has no
  background of its own. The padding between the outline and the child is 8 pixels. With the margin
  of a leaf, the space between the outline and a text is 16 pixels. Text in a card breaks at the
  width of the card.
- **Divider.** The line fills the cross axis of its container with each value of `align`. Thus a
  vertical divider in a `Row` has the height of the row.
- **List.** A list in a parent with no limit, for example a `Column` of a surface in a scroll pane,
  has the size of its content and no scroll bar. Then the outer scroll pane scrolls. A list that
  gets less than its content scrolls in its direction. Examples are a list that is the root of a
  surface in a window, and a list in a `Column` that is higher than its window. A turn of the mouse
  wheel that the list cannot use goes to the outer scroll pane. The list is not virtualized: each
  child has a view. Do not use it for some thousand children.
- **Tabs.** The selected tab is state of the view. It is an index, and it stays when the titles or
  the other tabs change. The view mounts only the child of the selected tab. Thus a change of the
  tab disposes the view of the child before. A title is plain text.
- **Modal.** Only the trigger shows in the surface. A `Button` in the trigger does its own action,
  and then the modal opens. A click on a different part of the trigger also opens the modal. A
  trigger with no button gets the keyboard focus and opens with the Space key and the Enter key. The
  view makes the view of the content when the modal opens, and the adapter disposes it when the
  modal closes. The default presenter shows a modal dialog in the center of the window of the
  surface. The user closes it with the close control of the dialog or with the Escape key.
- **Button.** The view calls the action in the Swing listener of the gesture. The variant `default`
  is the button of the look and feel. The adapter paints `primary` with the accent color of the look
  and feel, and `borderless` as a link. The user cannot operate a button while a check of the
  severity `error` fails. The messages of the failed checks are the tool tip of the button.
- **TextField.** The view writes each edit to the data model. The value that comes back does not
  move the caret. The variant `number` accepts only the text of a decimal number. The messages of
  the failed checks show below the control. A warning and an information are hints that do not stop
  the user.
- **Input components.** `CheckBox`, `ChoicePicker`, `Slider` and `DateTimeInput` write the change of
  the user to the data model immediately. When the value comes back, the view does not touch the
  control. A value with no data path is a value that only the control has, until the next update.
  The messages of the failed checks show below the control, as for `TextField`, and the user can
  always operate the control. The label is the accessible name of the control.
- **CheckBox.** The label is next to the check box, and it breaks into lines. A click on the label
  operates the check box. The view writes `true` or `false`.
- **ChoicePicker.** The view writes a list of strings, also for one selection, in the sequence of
  the options. The style `checkbox` has radio buttons for `mutuallyExclusive` and check boxes for
  `multipleSelection`. More than six options go in columns when the width permits. The style
  `chips` has toggle buttons that go to the next line when the width is not sufficient. A
  `filterable` picker has a text field that hides the options whose label does not contain its
  text. A selected option that the filter hides stays selected. The panel of the options has the
  accessible role of a group box.
- **Slider.** The current value shows next to the slider. With `steps`, the slider moves in equal
  steps and shows tick marks. With no `steps`, a whole range from 2 to 1000 moves in steps of 1, and
  each other range moves in steps of one percent. The view writes a whole number as a `Long` and
  each other number as a `Double`, also while the user drags. The accessible value of the slider is
  the number.
- **DateTimeInput.** Plain Swing has no date picker. The field is a `JSpinner` with the text
  `yyyy-mm-dd`, `hh:mm` or `yyyy-mm-dd hh:mm`. The user types the value, or moves the part at the
  caret with the arrows, and removes the value with the removal of the text. With no `enableDate`
  and no `enableTime`, the field has a date. The view writes ISO 8601 text in the form of the
  enabled parts, and it keeps the seconds and the zone of the value. It never changes a value to a
  different zone. A value that is not ISO 8601 text shows as it is and is not written back.

To replace the view of one component, make a catalog with a different list:

```java
List<SwingComponentImplementation> components = new ArrayList<>(SwingBasicCatalog.components());
components.replaceAll(component -> component.name().equals("Button") ? myButton : component);
Catalog<SwingComponentImplementation, FunctionImplementation> catalog =
    SwingBasicCatalog.create().withComponents(components);
```

## Show a surface with custom components

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

### A surface in a scroll pane

`A2uiSurface` implements `Scrollable` and `HeightForWidth`. Thus it can be the view of a
`JScrollPane`:

```java
JScrollPane scrollPane = new JScrollPane(new A2uiSurface(surface));
```

The surface has the width of the viewport, and its text breaks at that width. Its height is the
height that its content needs at that width. Thus the scroll pane scrolls vertically. A surface
that is shorter than the viewport keeps its height, at the top of the viewport. The scroll pane
scrolls horizontally only when the viewport is narrower than the minimum width of the content.

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
  `TextBinding` does this for a text component, and `ValueBinding` for each other control.
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

A component with the client property `FlexLayout.STRETCH_PROPERTY` set to `Boolean.TRUE` fills the
cross axis with each value of `align`, as an element with `align-self: stretch` does in CSS.

`getPreferredSize()` of a Swing component has no width as its input. Thus text that wraps has the
preferred height of one line. A component whose height depends on its width implements
`HeightForWidth`. `FlexLayout` gives such a component the height for the width that it gets, in the
same layout pass. `FlexPanel` is a `JPanel` with a `FlexLayout` that implements `HeightForWidth`
for its content. It is not opaque, and it takes its font and its foreground color from the component
above it. Use `FlexPanel` for a custom container. Then the text below it wraps correctly.

A `JScrollPane` and a `JTabbedPane` do not pass the width to their content. The views of `List` and
of `Tabs` show how a subclass of such a component implements `HeightForWidth`. A scroll pane is
also the root of a layout for Swing. A scroll pane that must have the size of its content returns
false from `isValidateRoot()`. Then a change of the content makes the parent do its layout again.

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
| `markdown`       | `MarkdownRenderer` | Shows the Markdown subset of the protocol in a `JEditorPane` that the user cannot edit.         |
| `imageLoader`    | `ImageLoader`      | Reads `http`, `https` and `data` URLs with `ImageIO`, not on the EDT. It has no cache.          |
| `mediaPlayer`    | `MediaPlayer`      | `MediaPlayer.NONE`. Swing cannot play media. An application can supply a JavaFX or vlcj player. |
| `modalPresenter` | `ModalPresenter`   | Shows a modal `JDialog` above the window of the surface. It needs a display.                    |
| `urlOpener`      | `UrlOpener`        | Opens the URL in the browser of the system with `java.awt.Desktop`.                             |

A view gets the services from `ViewContext.services()`.

The view of the `Modal` component gives its content to the `modalPresenter` service. The default
dialog has the size of the content, and it is not larger than the window of the surface less 48
pixels on each side. Text breaks at the width of the dialog, and the dialog scrolls content that is
too high. An application that shows the content in a different way, for example in a layer of its
window, implements `ModalPresenter`: `present(owner, content, onClosed)` shows the content and
returns a handle. Call `onClosed` when the user closes the content. The view calls the handle when
the surface stops to show the modal.

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
helpers `Edt`, `Headless` and `Snapshots` in `src/test/java` do this work. `TestModalPresenter` is
a `ModalPresenter` that needs no window: it keeps the content of each modal in a panel.

`SurfaceSnapshotTest` and `BasicCatalogSnapshotTest` write PNG images of surfaces to
`target/snapshots/`. The second test writes each surface in three look and feels: the look and feel
of the test JVM, Nimbus and the look and feel of the system. Look at the images after a change of a
view or of a layout. The build does not compare them. The image of the content of a modal is a
second file, with `-modal-content` in its name.

The conformance harness of this module has the name `swing`. It registers each suite of the
`adapter` corpus of [`conformance/`](../../conformance). A case of the two rendering suites runs
only if the adapter has a view for each basic catalog component that the case uses. The harness
records each other case as skipped, with the names of the absent views. The adapter has each view
of the basic catalog. Thus 36 cases run: the 7 cases of the Markdown subset, the 22 cases of the
surface suite and the 7 cases of the accessibility suite. The harness skips only the 16 cases of
the suite for protocol v0.9. The harness does not run the `axeCore` rules, which need a DOM. See
[the conformance section](../README.md#conformance) for the commands.

## References

- Adapter design:
  [blueprints/modules/a2ui_framework_adapter.blueprint.md](../../blueprints/modules/a2ui_framework_adapter.blueprint.md)
- Basic catalog:
  [catalogs/basic/v1/basic_catalog_implementation_guide.md](../../catalogs/basic/v1/basic_catalog_implementation_guide.md)
