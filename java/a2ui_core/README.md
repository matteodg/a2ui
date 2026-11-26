# A2UI Java Core (`a2ui-java-core`)

This module contains the state model and the protocol logic of the A2UI Java client SDK. It has no
UI code. A framework adapter, such as the Swing adapter, shows the state on the screen.

The module implements the A2UI protocol v1.0. The design obeys
[blueprints/modules/a2ui_core.blueprint.md](../../blueprints/modules/a2ui_core.blueprint.md).

## Packages

The Java module name is `com.google.a2ui.core`. It exports these packages:

| Package                                   | Contents                                                                                                                                                                                      |
| :---------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `com.google.a2ui.core.exceptions`         | `A2uiError` and its subclasses. Each failure of the core is one of these.                                                                                                                     |
| `com.google.a2ui.core.common`             | `Signal`, `EventNotifier`, `Subscription`, `SemVer`, `Uax31`, `ProtocolVersion`.                                                                                                              |
| `com.google.a2ui.core.state`              | `SurfaceGroupModel`, `SurfaceModel`, `SurfaceComponentsModel`, `ComponentModel` and `DataModel`. They hold the surfaces.                                                                      |
| `com.google.a2ui.core.expressions`        | `ExpressionParser`, the parser for `${...}` templates.                                                                                                                                        |
| `com.google.a2ui.core.catalog`            | `Catalog`, `ComponentApi` and `FunctionApi`. They read, write and derive catalog documents.                                                                                                   |
| `com.google.a2ui.core.basic_catalog.v1_0` | `BasicCatalog.api()`, the signatures of the components and of the functions of the basic catalog.                                                                                             |
| `com.google.a2ui.core.schema.v1_0`        | `SchemaDocuments`, the JSON Schema documents of the v1.0 specification.                                                                                                                       |
| `com.google.a2ui.core.validation`         | `PayloadValidator` and `CommonTypeValidator`. They examine a component, a function call or a value of a common type. `ValidationConfig` selects the problems that message processing accepts. |
| `com.google.a2ui.core.processing`         | `MessageProcessor`. It examines the messages of the agent and applies them to the surfaces.                                                                                                   |

The package `com.google.a2ui.core.internal` is not part of the API, and the module does not export
it.

The core does not yet contain function implementations, node resolution, and the messages from the
renderer to the agent.

## Threads

The core is not thread-safe. Use one thread for all the calls to one model. A Swing application uses
the event dispatch thread.

## Signals

A `Signal` holds a value and notifies its listeners when the value changes.

```java
Signal<String> name = new Signal<>("Ada");
Subscription subscription = name.subscribe(value -> System.out.println(value));

name.set("Grace"); // Prints "Grace".
subscription.cancel();
```

`Reactive.batch` holds the notifications until the batch ends.

```java
Reactive.batch(() -> {
  first.set(1);
  second.set(2);
});
```

## Data model

A `DataModel` holds JSON values in plain JVM types: `Map<String, Object>`, `List<Object>`, `String`,
`Long`, `Double`, `Boolean` and `null`. The paths are JSON Pointers (RFC 6901).

```java
DataModel model = new DataModel(Map.of("user", Map.of("name", "Ada")));
DataSubscription subscription = model.subscribe("/user/name", value -> System.out.println(value));

model.set("/user/name", "Grace"); // Prints "Grace".
Object user = model.get("/user"); // A read-only copy: {name=Grace}
model.delete("/user/name");
```

The model makes a copy of each value that goes in or out. A write that fails does not change the
model.

## Expression templates

`ExpressionParser.parseTemplate` divides a `formatString` template into literals, data paths and
function calls.

```java
List<Expression> parts = ExpressionParser.parseTemplate("Hello, ${/user/name}!");
// [Literal[value=Hello, ], Path[path=/user/name], Literal[value=!]]
```

## Catalogs

A `Catalog` holds the definitions of components and of functions. `Catalog.fromJson` reads a catalog
document, and `toJson` writes the same document. `derive` makes a catalog with a new ID from some
entries of a catalog and from new entries.

```java
Catalog<ComponentApi, FunctionApi> basic = BasicCatalog.api();
Catalog<ComponentApi, FunctionApi> textOnly =
    basic.derive("https://example.com/catalogs/text", List.of("Text", "Column"));

// The document is a Map<String, Object> of plain JSON values.
Catalog<ComponentApi, FunctionApi> custom = Catalog.fromJson(myCatalogDocument);
FunctionApi openUrl = basic.functions().get("openUrl");
openUrl.allowedCallers(); // RENDERER_ONLY
```

For a v1.0 document, `fromJson` also applies the rules that v1.0 adds, for example the UAX #31 names
and the permitted `$ref` targets. A document that does not obey a rule causes an `A2uiCatalogError`.

`SchemaDocuments` gives the schemas of the specification as plain JSON values, for example
`SchemaDocuments.commonTypes()`.

The build copies the basic catalog from `catalogs/basic/v1/` and the schemas from
`specification/v1_0/json/` into the JAR file. Thus the SDK does not contain a copy that a person can
edit.

## Message processing

A `MessageProcessor` reads the messages of the agent as plain JSON values. The payload is one
message, a list of messages, or a `{"messages": [...]}` wrapper. The processor examines a full
message before it applies it. Thus a message that it rejects changes nothing.

```java
MessageProcessor<ComponentApi> processor =
    new MessageProcessor<>(List.of(BasicCatalog.api()), ValidationConfig.RELAXED);
processor.model().onSurfaceCreated().subscribe(surface -> System.out.println(surface.id()));

processor.processMessages(messagesFromTheAgent);

SurfaceModel<ComponentApi> surface = processor.model().getSurface("main").orElseThrow();
surface.componentsModel().all(); // The components by ID, as the agent described them.
surface.dataModel().get("/"); // The data of the surface.
```

The processor handles `createSurface`, `updateComponents`, `updateDataModel` and `deleteSurface`. It
does not yet handle `callRendererFunction` and `agentFunctionResponse`.

`ValidationConfig.STRICT` is the default. It rejects a surface that is not complete: a surface with
no root component, a reference to a component that does not exist, or a component that the root
does not reach. A renderer that receives a surface in parts uses `ValidationConfig.RELAXED`. To
change one rule, use a `with` method, for example
`ValidationConfig.STRICT.withAllowOrphanComponents(true)`.

A surface can use each v1.0 catalog that the processor has at the creation of the surface.
`addCatalog` adds a catalog for the surfaces that the processor makes subsequently.

The models have public methods that change them, for example `SurfaceComponentsModel.addComponent`.
These methods do not examine a schema or the graph of the surface. Only the message processor makes
these checks.

## Validation

`Catalog.validationSchema` gives a v1.0 catalog as one self-contained JSON Schema document. Each
`$ref` in it is local. Each component is one object schema that has the content of its `allOf`
mixins. `$defs` has each common type that the schema uses.

`PayloadValidator` examines one component or one function call against one v1.0 catalog.
`CommonTypeValidator` examines a value against one common type, for example `DynamicString`. A
value that is not correct causes an `A2uiValidationError`. Its message lists each problem.

```java
PayloadValidator validator = new PayloadValidator(BasicCatalog.api());
validator.validateComponent(Map.of("id", "title", "component", "Text", "text", "Hello"));
validator.validateFunction("openUrl", Map.of("url", "https://example.com"));

CommonTypeValidator.validate("DynamicBoolean", true);
```

A surface can use more than one catalog. A validator fully examines a nested function call only if
the call runs in the catalog of the validator. For each other call, it examines the structure and
the names only.

The validators read nothing from the disk or from the network. The JSON Schema library and Jackson
are not part of the API.

## Tests

See [the README of the parent directory](../README.md).
