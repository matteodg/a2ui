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
