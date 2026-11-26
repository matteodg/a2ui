# Changelog

## Unreleased

- Add `a2ui-java-core`, the core of the Java client SDK for protocol v1.0. This first change adds
  the error hierarchy (`A2uiError`), `Signal`, `EventNotifier`, `SemVer` and `Uax31`.
- Add `DataModel`, the observable store of the data of one surface, and `ExpressionParser`, the
  parser for `${...}` templates.
- Add `Catalog`, `ComponentApi`, `FunctionApi`, `FunctionReturnType`, `AllowedCallers` and
  `A2uiMetadata` in `com.google.a2ui.core.catalog`, `BasicCatalog.api()` in
  `com.google.a2ui.core.basic_catalog.v1_0`, `SchemaDocuments` in `com.google.a2ui.core.schema.v1_0`,
  and `ProtocolVersion` in `com.google.a2ui.core.common`.
- Add `PayloadValidator` and `CommonTypeValidator` in `com.google.a2ui.core.validation`, and
  `Catalog.validationSchema()`.
- Add `MessageProcessor` in `com.google.a2ui.core.processing`, `SurfaceGroupModel`, `SurfaceModel`,
  `SurfaceComponentsModel` and `ComponentModel` in `com.google.a2ui.core.state`, and
  `ValidationConfig` in `com.google.a2ui.core.validation`. A v1.0 catalog document can now name a
  common type as a local reference, for example `#/$defs/DynamicString`. The messages of
  `PayloadValidator.validateComponent` for an absent type and for an unknown type have a new text.
- Add `surfaceId()`, `functionCallId()`, `path()`, `errorCode()` and `details()` to `A2uiError`, and
  add `A2uiErrorDetail`. `MessageProcessor` sends the `error` message of the protocol for a rejected
  message to an `OutboundMessageListener`. Add `MessageProcessor.getRendererCapabilities` and
  `MessageProcessor.getRendererDataModel`. `MessageProcessor` and `PayloadValidator` reject a
  component call of an `agentOnly` function. `MessageProcessor` also rejects a component ID or a
  component type that is not a UAX #31 identifier. The message of a component problem has a new
  text.
- Add `DataContext` in the new package `com.google.a2ui.core.resolution`. It resolves data bindings
  and function calls. Add `FunctionImplementation`, `FunctionBody` and `Catalog.withFunctions` in
  `com.google.a2ui.core.catalog`, and `Coercion` in `com.google.a2ui.core.common`. Add
  `BasicCatalog.withFunctions` and `UrlOpener` in `com.google.a2ui.core.basic_catalog.v1_0`, with
  the code of the 14 functions of the basic catalog.
- Add `A2uiRegex` in `com.google.a2ui.core.common`. It compiles a pattern of the A2UI regex dialect
  and applies the static safety rules of the protocol before `java.util.regex` gets the pattern.
- Add `NodeResolver`, `ComponentNode`, `NodeProps`, `NodeState`, `ResolvedBinding`,
  `WritableBinding`, `NodeAction`, `ActionDispatchOptions`, `ValidationResult` and
  `ValidationSeverity` in `com.google.a2ui.core.resolution`. A resolver makes the live node tree of
  a surface for a UI adapter. Add `DataContext.get`. Add `onAction`, `onError`, `onDisposing`,
  `dispatchAction` and `dispatchError` to `SurfaceModel`, `onAction` to `SurfaceGroupModel`, and
  `RendererAction` in `com.google.a2ui.core.state`.
- Add a `MessageProcessor` constructor with the flag `unifiedOutbound`. With the flag, the outbound
  listener also receives an `action` message for each event action and an `error` message for each
  failure that a surface reports. An action that fails now reports an error with the code
  `INVALID_ACTION`, `EXECUTION_ERROR`, `EXPRESSION_ERROR` or `CATALOG_ERROR`, and a binding that
  fails reports `EXPRESSION_ERROR` or `CATALOG_ERROR`. `NodeAction.invoke` now throws an
  `A2uiValidationError` for metadata with a key that is not a UAX #31 identifier. An event action
  now runs with user activation, and a `userMessage` that is not text is an error.
  `DataContext.resolveDynamicValue` and `DataContext.evaluate` now throw an `A2uiExpressionError`
  when the code of a function throws an exception that is not an `A2uiError`.
- Add `RpcHandler`, `RpcScheduler` and `CallOptions` in the new package `com.google.a2ui.core.rpc`,
  and add `MessageProcessor.rpc()` and `MessageProcessor.callAgentFunction`. `MessageProcessor` now
  handles `callRendererFunction` and `agentFunctionResponse`. Before, these messages caused an
  `A2uiStateError`. Add `DataContext.evaluateTemplate`. A function whose schema describes no
  arguments now accepts all arguments. The `SurfaceModel` constructor now throws an
  `A2uiCatalogError` for a catalog that is not for the protocol version of the surface.
- Add `AgentFunctionCaller` in `com.google.a2ui.core.state`, and add `agentFunctionCaller()`,
  `setAgentFunctionCaller` and `isDisposed()` to `SurfaceModel`. A `MessageProcessor` that has an
  outbound listener sets a caller on each surface that it makes. A binding, a check or an action
  that calls a function that its catalog does not implement now sends the call to the agent. Before,
  such a call failed. A binding is null and a check passes until the agent answers.
  `DataContext.resolveDynamicValue` now refuses a local call that omits a required argument, and it
  throws an `A2uiExpressionError` for a literal object with a key that starts with one `@`.
  `SurfaceModel.dispatchAction` now omits a `userMessage` that is not a text with content.
  `MessageProcessor` now examines only the changed part of the component graph of a surface for an
  update, if the validation configuration accepts dangling references and orphans.
- Add `Catalog.withComponents` in `com.google.a2ui.core.catalog`. It makes a catalog with other
  components, for example the components of a framework adapter that have a view.
- Add the component `userActivation` to `ActionDispatchOptions`, with the constant
  `WITHOUT_USER_ACTIVATION` and the method `withUserActivation`. A `NodeAction` that code calls with
  no user gesture does not run a function that requires one. The constructor with one argument
  stays.
- A `MessageProcessor` with `unifiedOutbound` now sends the failure of a call to the agent with the
  `functionCallId` of the call. Before, the `error` message had the `surfaceId`.
- Add `a2ui-java-swing`, the Swing adapter of the SDK, with its foundations. `A2uiSurface` shows one
  `SurfaceModel`. `A2uiFallbacks`, `A2uiServices` and `A2uiSwing` are in
  `com.google.a2ui.swing.surface`. `SwingComponentImplementation`, `SwingComponentView`,
  `ViewContext` and `ChildViews` in `com.google.a2ui.swing.catalog` are the types for component
  implementations. `TextBinding` is in `com.google.a2ui.swing.binding`, and `FlexLayout` is in
  `com.google.a2ui.swing.catalog.basic`. The module does not have the views of the components of the
  basic catalog in this change.
- Add `SwingBasicCatalog` in `com.google.a2ui.swing.catalog.basic`. It makes the basic catalog of
  protocol v1.0 with Swing views for `Text`, `Row`, `Column`, `Button` and `TextField`. Each other
  component of the catalog is declared and has no view in this change. Add
  `SwingComponentImplementation.withoutView` and `hasView`, and the error type
  `A2uiFallbackError.NO_VIEW`. Add `HeightForWidth` in `com.google.a2ui.swing.catalog` and
  `FlexPanel` and `FlexLayout.preferredHeight` in `com.google.a2ui.swing.catalog.basic`. Thus text
  that wraps gets its height in one layout pass. The default `markdown` service of `A2uiServices`
  now shows the Markdown subset of the protocol. Before, it showed the text with its marks.
- `A2uiSurface` now implements `Scrollable` and `HeightForWidth`. Thus a surface can be the view of
  a `JScrollPane`: it has the width of the viewport, its text breaks at that width, and the scroll
  pane scrolls vertically. A button of the variant `primary` or `borderless` now keeps its text
  color, and the default Markdown component keeps its position in a scroll pane, after a change of
  the look and feel at run time.
- `SwingBasicCatalog` now has Swing views for `List`, `Card`, `Tabs`, `Modal` and `Divider`. A list
  in a parent with no limit has the size of its content, and a list that gets less space scrolls.
  The selected tab of `Tabs` is state of its view. `Modal` shows its content with the
  `modalPresenter` service: the default dialog now closes also for the Escape key, has the size of
  its content and scrolls content that is too high. A button in the trigger of a modal does its
  action and then opens the modal. Add `FlexLayout.STRETCH_PROPERTY` in
  `com.google.a2ui.swing.catalog.basic`: a component with this client property fills the cross
  axis with each `align`. The default Markdown component now has the correct width on a screen with
  a scale when it gave its size before it came to a window.
- `SwingBasicCatalog` now has Swing views for `CheckBox`, `ChoicePicker`, `Slider` and
  `DateTimeInput`. `ChoicePicker` writes a list of strings, `Slider` writes a `Long` for a whole
  number and a `Double` for each other number, and `DateTimeInput` writes ISO 8601 text in the form
  of its enabled parts. Add `ValueBinding` in `com.google.a2ui.swing.binding`: the two-way binding of
  a control that is not a text component.
- `SwingBasicCatalog` now has Swing views for `Icon`, `Image`, `Video` and `AudioPlayer`, and thus
  for each component of the basic catalog. `Icon` paints a shape of the module for each icon name
  of the catalog, or the `svgPath` of the agent. A name that the catalog does not have shows the
  shape of `help`. `Image` loads with the `imageLoader` service and
  applies `variant` and `fit`. `Video` and `AudioPlayer` show the view of the `mediaPlayer` service,
  or a control that opens the URL with the `urlOpener` service. Add `MediaUrls` in
  `com.google.a2ui.swing.surface`: the safety rule for media URLs. The default
  image loader now has timeouts, a limit for the bytes and for the pixels of an image, and it
  follows a redirection only to an `http` or `https` URL. An `ImageLoader` and a `MediaView` can
  now get a relative URL.
- The `@accessibility` attribute `hidden` now hides all the components below its component. Add
  `A2uiAccessibility.isHidden`.
- The schema validation now asserts the formats `date`, `time` and `date-time` as ISO 8601 text.
  Thus a literal `min` or `max` of `DateTimeInput`, for example `2025-01-01`, is correct, and a
  literal that is not a date or a time is not correct. Before, the processor rejected each literal.
  The schema validation also asserts the format `uri`: a literal `url` argument of `openUrl` must be
  an absolute URI with a scheme, as in the Python SDK and the Dart SDK.
- A component of a `FlexLayout` that has a weight is now not smaller than its minimum size. Thus a
  weighted column of a `Row` does not cut a word.
- The user cannot operate a part of a surface that the `@accessibility` attribute `hidden` hides.
  The keyboard focus does not go into it, and the mouse events at its position reach no view. For
  the mouse rule the adapter adds a component that paints nothing to the layered pane of the root
  pane of the window.
