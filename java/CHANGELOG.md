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
  the code of 13 of the 14 functions of the basic catalog. The `regex` function has no code yet.
