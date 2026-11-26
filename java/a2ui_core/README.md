# A2UI Java Core (`a2ui-java-core`)

This module contains the state model and the protocol logic of the A2UI Java client SDK. It has no
UI code. A framework adapter, such as the Swing adapter, shows the state on the screen.

The module implements the A2UI protocol v1.0. The design obeys
[blueprints/modules/a2ui_core.blueprint.md](../../blueprints/modules/a2ui_core.blueprint.md).

## Packages

The Java module name is `com.google.a2ui.core`. It exports these packages:

| Package                                   | Contents                                                                                                                                                                                      |
| :---------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `com.google.a2ui.core.exceptions`         | `A2uiError` and its subclasses. Each failure of the core is one of these. `A2uiErrorDetail` is one problem of a failure.                                                                      |
| `com.google.a2ui.core.common`             | `Signal`, `EventNotifier`, `Subscription`, `SemVer`, `Uax31`, `ProtocolVersion`. `Coercion` reads a value as a boolean, as a number or as text.                                               |
| `com.google.a2ui.core.state`              | `SurfaceGroupModel`, `SurfaceModel`, `SurfaceComponentsModel`, `ComponentModel` and `DataModel`. They hold the surfaces.                                                                      |
| `com.google.a2ui.core.expressions`        | `ExpressionParser`, the parser for `${...}` templates.                                                                                                                                        |
| `com.google.a2ui.core.catalog`            | `Catalog`, `ComponentApi` and `FunctionApi`. They read, write and derive catalog documents. A `FunctionImplementation` is a function with its code.                                           |
| `com.google.a2ui.core.basic_catalog.v1_0` | `BasicCatalog.api()`, the signatures of the components and of the functions of the basic catalog. `BasicCatalog.withFunctions()`, the catalog with the code of its functions.                 |
| `com.google.a2ui.core.resolution`         | `DataContext`. It resolves the data bindings and the function calls of a surface.                                                                                                             |
| `com.google.a2ui.core.schema.v1_0`        | `SchemaDocuments`, the JSON Schema documents of the v1.0 specification.                                                                                                                       |
| `com.google.a2ui.core.validation`         | `PayloadValidator` and `CommonTypeValidator`. They examine a component, a function call or a value of a common type. `ValidationConfig` selects the problems that message processing accepts. |
| `com.google.a2ui.core.processing`         | `MessageProcessor`. It examines the messages of the agent and applies them to the surfaces. `OutboundMessageListener` receives the messages for the agent.                                    |

The package `com.google.a2ui.core.internal` is not part of the API, and the module does not export
it.

The core does not yet contain the `regex` function, actions, node resolution and the function-call
messages (`callRendererFunction`, `agentFunctionResponse`).

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

### A rejected message

`processMessages` throws an `A2uiError` for a message that it rejects. The error tells the surface
of the message (`surfaceId()`) and the location of the problem (`path()`, for example
`/components/0/text`). `details()` lists each problem with its path and its code: `missing_field`,
`type_mismatch` or `invalid_value`. `errorCode()` gives the protocol error code if the error has its
own code, for example `UNALLOWED_PARENT`. Each of these values can be absent.

```java
try {
  processor.processMessages(messagesFromTheAgent);
} catch (A2uiValidationError e) {
  e.surfaceId(); // Optional[main]
  e.path(); // Optional[/components/0/text]
  for (A2uiErrorDetail detail : e.details()) {
    System.out.println(detail.path() + " " + detail.code() + " " + detail.message());
  }
}
```

To also get the `error` message of the protocol, give an `OutboundMessageListener` to the processor.
The processor sends the message to the listener before it throws the error. The message is a
read-only map of plain JSON values.

```java
MessageProcessor<ComponentApi> processor =
    new MessageProcessor<>(
        List.of(BasicCatalog.api()),
        ValidationConfig.STRICT,
        message -> transport.send(message));
// {"version": "1.0", "error": {"code": "VALIDATION_FAILED", "surfaceId": "main", ...}}
```

The processor also rejects a component ID or a component type that is not a UAX #31 identifier, a
component that calls an `agentOnly` function, and an `updateDataModel` path that does not start with
`/`.

### Data for the agent

`getRendererCapabilities` gives the catalogs that this renderer supports. `getRendererDataModel`
gives the data of the surfaces that asked for it with `sendDataModel`. The two results are plain
JSON values that a transport attaches to the messages for the agent.

```java
processor.getRendererCapabilities(List.of(ProtocolVersion.V1_0));
// {1.0={supportedCatalogIds=[https://a2ui.org/specification/v1_0/catalogs/basic/catalog.json]}}

processor.getRendererDataModel(); // Optional[{version=1.0, surfaces={main={...}}}]
```

## Functions and bindings

`BasicCatalog.withFunctions` gives the basic catalog with the code of its functions. A `DataContext`
resolves a value as a message writes it. A binding `{"@path": ...}` becomes the data at the path. A
call `{"@call": ...}` becomes the result of the function.

```java
UrlOpener opener =
    uri -> {
      try {
        Desktop.getDesktop().browse(uri);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
Catalog<ComponentApi, FunctionImplementation> catalog =
    BasicCatalog.withFunctions(Locale.US, opener);
MessageProcessor<ComponentApi> processor = new MessageProcessor<>(List.of(catalog));
processor.processMessages(messagesFromTheAgent);
DataContext context = new DataContext(processor.model().getSurface("main").orElseThrow());

context.resolveDynamicValue(Map.of("@path", "/user/name")); // "Ada"
context.resolveDynamicValue(
    Map.of("@call", "formatString", "args", Map.of("value", "Hello, ${/user/name}!")));
// "Hello, Ada!"
```

`formatNumber`, `formatCurrency`, `formatDate` and `pluralize` write text for the locale of
`withFunctions`. The default locale is `Locale.US`. Thus the text is the same on each computer.
`openUrl` gives an absolute `http` or `https` URL to the `UrlOpener` of the application, and refuses
each other URL.

A call runs in the catalog that its `catalogId` names. If the call names none, it runs in the
default catalog of the surface. A call in a `formatString` template runs in the catalog of that
`formatString` call.

A context refuses these calls with an `A2uiExpressionError`:

- A call of a function that only the agent can call (`allowedCallers` is `agentOnly`).
- A call of a function that requires a user gesture, for example `openUrl`, in a context that is not
  the result of one. Use `context.withUserActivation()` in the handler of a click.
- A call of a function that has no code. The `regex` function has no code yet.

In a list template, `context.nested("items/2", 2)` gives the context of one item. Relative paths
start at the item, and the `@index` function returns its position.

A function of your application is a `FunctionImplementation`: a name, the definition of the function
as a catalog document writes it, and the code. The code gets the resolved arguments. `Coercion`
reads an argument as a boolean, as a number or as text with the rules of the protocol.

```java
FunctionImplementation shout =
    new FunctionImplementation(
        "shout",
        Map.of("returnType", "string"),
        (args, context) -> Coercion.toText(args.get("value")).toUpperCase(Locale.ROOT));
Catalog<ComponentApi, FunctionImplementation> custom =
    new Catalog<>("https://example.com/catalogs/custom", List.of(), List.of(shout));
```

`Catalog.withFunctions` gives a catalog from a document the code of its functions.

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
