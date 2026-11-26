# Changelog

## Unreleased

- Add `a2ui-java-core` (Java module `com.google.a2ui.core`), the core of the Java client SDK. It
  implements protocol v1.0, needs Java 25 or later, and is confined to one thread.
  - `MessageProcessor` in `com.google.a2ui.core.processing` validates and applies the messages of
    the agent, and keeps the surfaces. It sends the `error` message of the protocol for a rejected
    message to an `OutboundMessageListener`. With the constructor flag `unifiedOutbound`, the
    listener also receives the `action` and `error` messages of each surface.
  - `SurfaceGroupModel`, `SurfaceModel`, `SurfaceComponentsModel`, `ComponentModel`, `DataModel`,
    `DataSubscription`, `RendererAction` and `AgentFunctionCaller` in `com.google.a2ui.core.state`
    are the state of the surfaces and their data.
  - `NodeResolver`, `ComponentNode`, `NodeProps`, `NodeState`, `ResolvedBinding`,
    `WritableBinding`, `NodeAction`, `ActionDispatchOptions`, `ValidationResult` and
    `ValidationSeverity` in `com.google.a2ui.core.resolution` make the live node tree of a surface
    for a UI adapter. `DataContext` in the same package resolves data bindings and function calls.
  - `Catalog`, `ComponentApi`, `FunctionApi`, `FunctionImplementation`, `FunctionBody`,
    `FunctionReturnType`, `AllowedCallers` and `A2uiMetadata` in `com.google.a2ui.core.catalog`
    describe a catalog. `BasicCatalog` and `UrlOpener` in
    `com.google.a2ui.core.basic_catalog.v1_0` give the basic catalog and the code of its 14
    functions. `SchemaDocuments` in `com.google.a2ui.core.schema.v1_0` gives the schemas of the
    protocol.
  - `PayloadValidator` and `CommonTypeValidator` in `com.google.a2ui.core.validation` examine a
    component, a function call or a value of a common type against the schemas. `ValidationConfig`
    selects the problems that the processor accepts. The validation asserts the formats `date`,
    `time` and `date-time` as ISO 8601 text, and the format `uri` as an absolute URI with a scheme.
  - `RpcHandler`, `RpcScheduler` and `CallOptions` in `com.google.a2ui.core.rpc` handle the
    messages `callRendererFunction` and `agentFunctionResponse`.
    `MessageProcessor.callAgentFunction` calls a function of the agent. A binding, a check or an
    action that calls a function that its catalog does not implement sends the call to the agent.
  - `ExpressionParser` and `Expression` in `com.google.a2ui.core.expressions` parse `${...}`
    templates.
  - `A2uiError` with its subclasses, `A2uiErrorDetail` and `RpcErrorCode` are in
    `com.google.a2ui.core.exceptions`. The code of a custom function or of a custom component can
    give a path and details to `A2uiExpressionError` and to `A2uiValidationError`, and the `error`
    message for the agent has them. `Signal`, `ReadonlySignal`, `Reactive`, `EventNotifier`,
    `EventSource`, `EventListener`, `Subscription`, `ProtocolVersion`, `SemVer`, `Uax31`,
    `Coercion` and `A2uiRegex` are in `com.google.a2ui.core.common`.
- Add `a2ui-java-swing` (Java module `com.google.a2ui.swing`), the Swing adapter of the SDK. It
  depends only on the core and on the JDK module `java.desktop`, and it works on the event dispatch
  thread.
  - `A2uiSurface` in `com.google.a2ui.swing.surface` is a Swing component that shows one
    `SurfaceModel`. It can be the view of a `JScrollPane`. `A2uiSwing` connects a
    `MessageProcessor` to the event dispatch thread. `A2uiFallbacks`, `A2uiPendingInfo`,
    `A2uiErrorInfo` and `A2uiFallbackError` show a component that is not resolved. `MountedNode`
    is the tree that a surface shows.
  - `SwingBasicCatalog` in `com.google.a2ui.swing.catalog.basic` makes the basic catalog with a
    Swing view for each of its 18 components. `FlexLayout` and `FlexPanel` in the same package are
    the layout of the containers.
  - `SwingComponentImplementation`, `SwingComponentView`, `ViewContext`, `ChildViews` and
    `HeightForWidth` in `com.google.a2ui.swing.catalog`, and `TextBinding` and `ValueBinding` in
    `com.google.a2ui.swing.binding`, are the types for a custom component.
  - `A2uiServices` in `com.google.a2ui.swing.surface` has five services, each with a default:
    `markdown` (`MarkdownRenderer`, `MarkdownView`), `imageLoader` (`ImageLoader`), `mediaPlayer`
    (`MediaPlayer`, `MediaView`), `modalPresenter` (`ModalPresenter`) and `urlOpener`. `MediaUrls`
    in the same package is the safety rule for the media URLs of the agent.
  - `A2uiAccessibility` in `com.google.a2ui.swing.surface` reads the `@accessibility` attributes
    that the adapter gives to the Swing component of a view. The user cannot operate a part of a
    surface that the attribute `hidden` hides: the keyboard focus does not go into it, and the
    mouse events at its position reach no view.
  - Limits: Swing cannot play media, thus the default `Video` and `AudioPlayer` show a control
    that opens the URL in an external application. The default image loader reads only the
    formats of `ImageIO` and has no cache. The default modal is a `JDialog`, which needs a display.
    The Java Accessibility API has no equivalent of `hidden` and of `live`, thus the adapter keeps
    them as attributes of the Swing component.
- Add the gallery sample `samples/client/swing`. It shows the examples of the basic catalog with
  the Swing adapter.
- Add the conformance harnesses `java-core` and `swing`. `./mvnw verify` runs the `core` corpus
  with the core, and the `adapter` corpus and the renderer part of the `e2e` corpus with the Swing
  adapter.
