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
