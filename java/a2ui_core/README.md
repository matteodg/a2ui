# A2UI Java Core (`a2ui-java-core`)

This module contains the state model and the protocol logic of the A2UI Java client SDK. It has no
UI code. A framework adapter, such as the Swing adapter, shows the state on the screen.

The module implements the A2UI protocol v1.0. The design obeys
[blueprints/modules/a2ui_core.blueprint.md](../../blueprints/modules/a2ui_core.blueprint.md).

## Packages

The Java module name is `com.google.a2ui.core`. It exports these packages:

| Package                                   | Contents                                                                                                                                                                                                                                                               |
| :---------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `com.google.a2ui.core.exceptions`         | `A2uiError` and its subclasses. Each failure of the core is one of these. `A2uiErrorDetail` is one problem of a failure.                                                                                                                                               |
| `com.google.a2ui.core.common`             | `Signal`, `Reactive`, `EventNotifier`, `Subscription`, `SemVer`, `Uax31`, `ProtocolVersion`. `Coercion` reads a value as a boolean, as a number or as text. `A2uiRegex` is the A2UI regex dialect.                                                                     |
| `com.google.a2ui.core.state`              | `SurfaceGroupModel`, `SurfaceModel`, `SurfaceComponentsModel`, `ComponentModel` and `DataModel`. They hold the surfaces. A `RendererAction` is an action that a surface reports. An `AgentFunctionCaller` sends a function call of a surface to the agent.             |
| `com.google.a2ui.core.expressions`        | `ExpressionParser`, the parser for `${...}` templates.                                                                                                                                                                                                                 |
| `com.google.a2ui.core.catalog`            | `Catalog`, `ComponentApi` and `FunctionApi`. They read, write and derive catalog documents. A `FunctionImplementation` is a function with its code.                                                                                                                    |
| `com.google.a2ui.core.basic_catalog.v1_0` | `BasicCatalog.api()`, the signatures of the components and of the functions of the basic catalog. `BasicCatalog.withFunctions()`, the catalog with the code of its functions.                                                                                          |
| `com.google.a2ui.core.resolution`         | `NodeResolver` makes the live tree of `ComponentNode` objects of a surface. `NodeProps`, `ResolvedBinding`, `WritableBinding`, `NodeAction` and `ValidationResult` are the resolved values of a node. `DataContext` resolves the data bindings and the function calls. |
| `com.google.a2ui.core.schema.v1_0`        | `SchemaDocuments`, the JSON Schema documents of the v1.0 specification.                                                                                                                                                                                                |
| `com.google.a2ui.core.validation`         | `PayloadValidator` and `CommonTypeValidator`. They examine a component, a function call or a value of a common type. `ValidationConfig` selects the problems that message processing accepts.                                                                          |
| `com.google.a2ui.core.processing`         | `MessageProcessor`. It examines the messages of the agent and applies them to the surfaces. `OutboundMessageListener` receives the messages for the agent: actions, errors and function calls.                                                                         |
| `com.google.a2ui.core.rpc`                | `RpcHandler`, the function calls between the renderer and the agent. `RpcScheduler` runs the timeout of a call to the agent. `CallOptions` are the options of one call.                                                                                                |

The package `com.google.a2ui.core.internal` is not part of the API, and the module does not export
it.

## Threads

The core is not thread-safe. Use one thread for all the calls to one model. A Swing application uses
the event dispatch thread.

The timeout of a call to the agent is the only work that can run on a different thread. See
[Threads and timeouts](#threads-and-timeouts).

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
gives `callRendererFunction` and `agentFunctionResponse` to its `RpcHandler`. See
[Function calls](#function-calls).

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

### Messages for the agent

A surface reports each event action of the user to `onAction`, and each failure to `onError`. To
send them to the agent, make the processor with `unifiedOutbound`. Then the outbound listener also
receives an `action` message for each event action and an `error` message for each failure. A
transport needs only this listener.

```java
MessageProcessor<ComponentApi> processor =
    new MessageProcessor<>(
        List.of(BasicCatalog.withFunctions()),
        ValidationConfig.STRICT,
        message -> transport.send(message),
        true);
// {"version": "1.0", "action": {"name": "save", "surfaceId": "main", "sourceComponentId": "ok",
//   "timestamp": "2026-11-26T10:15:30Z", "context": {"who": "Ada"}}}
// {"version": "1.0", "error": {"code": "EXPRESSION_ERROR", "surfaceId": "main", "message": ...}}
```

Each message is a read-only map of plain JSON values with the structure of the schema
`renderer_to_agent.json`. The listeners of `onAction` and of `onError` continue to receive their
events. Without `unifiedOutbound`, the listener receives only the `error` message of a rejected
message. `unifiedOutbound` without a listener causes an `IllegalArgumentException`.

The listener runs in the call that reports the action or the failure. Do not throw an exception from
the listener.

### Data for the agent

`getRendererCapabilities` gives the catalogs that this renderer supports. `getRendererDataModel`
gives the data of the surfaces that asked for it with `sendDataModel`. The two results are plain
JSON values that a transport attaches to the messages for the agent.

```java
processor.getRendererCapabilities(List.of(ProtocolVersion.V1_0));
// {1.0={supportedCatalogIds=[https://a2ui.org/specification/v1_0/catalogs/basic/catalog.json]}}

processor.getRendererDataModel(); // Optional[{version=1.0, surfaces={main={...}}}]
```

## Function calls

The agent and the renderer can call the functions of each other. `processor.rpc()` gives the
`RpcHandler` that does this work. The messages of the two directions go to the
`OutboundMessageListener` of the processor, with or without `unifiedOutbound`.

### The agent calls the renderer

A `callRendererFunction` message runs a function of a catalog of the renderer. The processor always
sends a `rendererFunctionResponse` message: the value of the function, or an error.

- The function runs in the catalog that the call names. The function must permit agent callers:
  its `allowedCallers` is `rendererOrAgent` or `agentOnly`.
- A function that requires user activation runs only when the application tells that a user gesture
  is present. Set the test with `setUserActivation`. Without a test, the handler refuses each such
  function.
- These two rules also apply to each call in the arguments and to each call in a `${...}` template
  that the function evaluates.
- A message that names a surface reads the data of that surface. A message that names no surface
  reads no data.

```java
processor.rpc().setUserActivation(() -> userClickedInTheLastSecond());
processor.processMessages(callRendererFunctionMessage);
// The listener gets {version=1.0, rendererFunctionResponse={functionCallId=..., value=...}}.
```

The error of a response has the code `INVALID_FUNCTION_CALL` if the handler refuses the call, and
`EXECUTION_ERROR` if an argument or the function fails.

### The renderer calls the agent

`callAgentFunction` sends a `callAgentFunction` message and returns a `CompletableFuture`. The
`agentFunctionResponse` message with the same `functionCallId` completes the future.

```java
CompletableFuture<Object> stock =
    processor.callAgentFunction(
        "main",
        Map.of("@call", "queryInventory", "catalogId", "urn:store", "args", Map.of("sku", "A-1")),
        CallOptions.withTimeout(Duration.ofSeconds(5)));
stock.whenComplete((value, error) -> {
  if (error instanceof A2uiRpcError failure) {
    System.out.println(failure.code()); // For example TIMEOUT.
  }
});
```

The future fails with an `A2uiRpcError`. Its `code()` is an `RpcErrorCode`:

| Code                    | Cause                                                                              |
| :---------------------- | :--------------------------------------------------------------------------------- |
| the code of the agent   | The response of the agent has an `error`.                                          |
| `TIMEOUT`               | No response came in the permitted time. The default is 30 seconds.                 |
| `CANCELLED`             | The surface was deleted, `cancelSurfaceCalls` cancelled its calls, or `dispose()`. |
| `DISPOSED`              | The call started after `dispose()`.                                                |
| `NO_LISTENER`           | The processor has no outbound listener.                                            |
| `DUPLICATE`             | A call with the same `functionCallId` waits for a response.                        |
| `INVALID_FUNCTION_CALL` | The call names no function, or it is not JSON.                                     |

The handler ignores a response that comes after its call ended.

### A component calls the agent

A binding, a check or an action of a component can call a function that the renderer cannot run:
a function that the catalog of the call does not declare, or a function that the catalog declares
with no code. A processor that has an outbound listener sends such a call to the agent as a
`callAgentFunction` message. A UI adapter writes no code for this.

```java
// The catalog has no code for `fetchTitle`. Thus the agent runs it.
Map<String, Object> text = Map.of("@call", "fetchTitle", "args", Map.of("id", Map.of("@path", "/id")));
processor.processMessages(
    Map.of(
        "version", "1.0",
        "updateComponents",
        Map.of(
            "surfaceId", "main",
            "components", List.of(Map.of("id", "root", "component", "Text", "text", text)))));
// When a NodeResolver resolves the node, the listener gets {version=1.0,
// callAgentFunction={surfaceId=main, functionCallId=..., callFunction={@call=fetchTitle,
// catalogId=..., args={id=7}}}}.
// node.props().get().value("text") is null until the agentFunctionResponse message comes.
```

`ValidationConfig.STRICT` rejects a message that calls a function that the catalog does not declare.
To use functions of the agent with strict validation, declare them in the catalog with no code, or
set `allowUnknownElements`.

- **Bindings.** The value of the binding is null until the agent answers. Then the resolver resolves
  the node again, and the `props` signal of the node notifies. The surface shares one request between
  the identical calls that bindings make while the request waits. It keeps the last 256 answers,
  thus a binding that makes an identical call later sends nothing.
- **Checks.** A check that waits for the agent passes. If the agent fails the call, the check fails.
- **Actions.** An action sends each call as a request of its own, and continues when the agent
  answers. An event sends the call of a context entry only after the agent answered the calls of
  the entries before it.
- **Failures.** An error response and a timeout go to `onError` of the surface, with the code
  `EXPRESSION_ERROR`, the message of the agent and the `functionCallId`. The value stays null. A
  node sends a failed call again only when the call changes. A call that the deletion of its surface
  cancels reports nothing.
- **Declared functions.** For a function that the catalog declares with no code, the core applies
  `allowedCallers` and `requiresUserActivation`, adds the defaults of the argument schema and
  examines the arguments against the schema before it sends the call.
- **No routing.** These evaluations send nothing: a part of a `${...}` template, the arguments of a
  `callRendererFunction` of the agent, a call with arguments that are not JSON, and a `DataContext`
  that your code makes. Such a `DataContext` gives the answer that the surface keeps for an
  identical call, or it fails.

A surface sends its calls through its `AgentFunctionCaller`. A processor with no outbound listener
sets none, and the call fails on the surface. An application that sends its messages to the agent
in a different way sets its own caller:

```java
surface.setAgentFunctionCaller(call -> myTransport.send(surface.id(), call)); // A CompletableFuture.
```

### Threads and timeouts

Call the processor and the handler on one thread. The future of a call completes on the thread that
ends the call. For a response, a cancellation or a disposal, that is your thread. For a timeout,
that is the thread of the `RpcScheduler` of the handler.

- `RpcScheduler.timerThread()` is the default. It ends a timeout on a daemon thread of the SDK with
  the name `a2ui-rpc-timeouts`. Use it only if the code that you attach to a future does not use the
  core or the UI.
- `RpcScheduler.onExecutor(executor)` measures the time on the same thread and then gives the work
  to your executor. A Swing application sets this scheduler:

  ```java
  processor.rpc().setScheduler(RpcScheduler.onExecutor(SwingUtilities::invokeLater));
  ```

- A UI adapter can implement `RpcScheduler` with the timer of its toolkit. A test can implement it
  with a list of tasks, thus the test does not wait.

The answer to a call of a component changes the nodes of its surface. The processor gives such an
answer to the surface only on the thread that made the call. With the default scheduler, a timeout
of such a call completes on the timer thread. Then the processor keeps the timeout and gives it to
the surface at the subsequent `processMessages` call. A UI adapter sets a scheduler that runs on its
thread, as the example above shows. Then the surface gets each timeout immediately. An
`AgentFunctionCaller` of your own must complete its futures on the thread that owns the surface.

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
- A call of a function that has no code, for example a function of a catalog that has only
  signatures.

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
`Catalog.withComponents` gives it components of a different type, for example components that have
a view.

If the code of a function throws an exception that is not an `A2uiError`, the context throws an
`A2uiExpressionError` that has the exception as its cause. Thus the exception of one function does
not stop the resolution of a surface.

A function reads data with `context.get(path)`, not directly from the data model. The node resolver
records each path that a function reads through its context. Then it evaluates the binding again
when the data at one of these paths changes.

### Regular expressions

An agent writes the pattern of the `regex` function. Thus the function does not give the pattern to
`java.util.regex` as it is. `A2uiRegex.compile` reads the pattern as the regex dialect of the
protocol, a subset of the ECMAScript patterns. It refuses a pattern that can use exponential time
for a match. Use `A2uiRegex` in each function of your catalog that has a pattern argument.

```java
A2uiRegex postcode = A2uiRegex.compile("^\\d{5}(?:-\\d{4})?$");
postcode.test("94043"); // true
postcode.test("zip 94043"); // false

A2uiRegex.compile("(a+)+$");
// A2uiExpressionError: Invalid or unsafe regex pattern (NESTED_QUANTIFIER): ...
```

- A match is case-sensitive. `test` is true if a part of the value matches.
- `\d`, `\w` and `\b` know only ASCII characters. `.` matches each code point that is not a line
  terminator. `^` and `$` match only at the two ends of the value.
- The dialect has no backreferences, no lookbehind, no named groups and no `\p{...}`.
- A pattern longer than 256 code points is refused. A value longer than 4,096 code points never
  matches.
- The message of a refused pattern contains one of these tokens: `PATTERN_TOO_LONG`,
  `UNSUPPORTED_SYNTAX`, `NESTED_QUANTIFIER`, `OVERLAPPING_ALTERNATION`, `ADJACENT_OVERLAP`.

An `A2uiRegex` is immutable. The class does not keep the compiled patterns. If your code uses a
pattern many times, keep the instance.

`java.util.regex` uses one level of recursion for each repetition of a group such as `(ab|c)*`. If a
long value causes a stack overflow, `test` does the match again on a temporary thread that has a
large stack, and waits for the result.

## Nodes

A `NodeResolver` makes a live tree of `ComponentNode` objects from the components and the data of
one surface. A UI adapter makes one resolver for each surface and shows `rootNode()`. It makes one
view for each node and subscribes the view to `node.props()`. A view never resolves a binding.

```java
SurfaceModel<ComponentApi> surface = processor.model().getSurface("main").orElseThrow();
NodeResolver<ComponentApi> resolver = new NodeResolver<>(surface);

resolver.rootNode().subscribe(root -> showRoot(root)); // Optional<ComponentNode<ComponentApi>>
ComponentNode<ComponentApi> node = resolver.rootNode().get().orElseThrow();

NodeProps<ComponentApi> props = node.props().get();
Object text = props.value("text"); // the value of a binding, or a static value
props.writableBinding("value").ifPresent(binding -> binding.set("Grace"));
props.action("action").ifPresent(NodeAction::invoke);
List<ComponentNode<ComponentApi>> children = props.children("children");

Subscription subscription = node.props().subscribe(next -> updateView(next));
node.addCleanup(subscription::cancel);
```

The resolver follows the surface. It makes, updates and disposes nodes when the components and the
data change. `resolver.dispose()` disposes each node. The deletion of the surface also does this.

**Nodes.** There is one node for each position of the tree. A component that two parents refer to
has two nodes. `instanceId()` is different for each child of one parent, and it stays the same when
a new node object replaces the node at a position. Use it as the key of a child view. `id()` is
unique in the process for one node object. `impl()` is the catalog entry of the component, for
example your subclass of `ComponentApi` that makes the view.

**Properties.** `node.props()` is a signal of `NodeProps`. It notifies one time for each update,
with the complete new properties. A change in a child notifies only on the child. The schema of the
component gives the form of each property, and the name of a property does not:

| Schema type of the property                            | Value in `NodeProps`                         | Typed reader                          |
| :----------------------------------------------------- | :------------------------------------------- | :------------------------------------ |
| A `Dynamic*` type, a `DataBinding` or a `FunctionCall` | `ResolvedBinding`                            | `binding`, `writableBinding`, `value` |
| `Action`                                               | `NodeAction`                                 | `action`                              |
| `ComponentId` or `Child`                               | `ComponentNode`                              | `child`                               |
| `ChildList`: a list of IDs or a template               | a list of `ComponentNode`                    | `children`                            |
| An object, or a list of objects                        | a map, or a list of maps, of resolved values | `object`, `objects`                   |
| Each other type                                        | the value as the agent wrote it              | `get`, `value`                        |

**Bindings.** A `ResolvedBinding` is a snapshot of one value. A changed value arrives as a new
binding in new properties. A binding is a `WritableBinding` only if the message bound the property
to a data path. `set` writes at that path, in the data scope of the node. A property that is a
boolean has a boolean value, with the coercion rules of the protocol. A binding that the core cannot
evaluate has the value `null`, and the surface reports the failure to `onError` one time. The error
has the code `EXPRESSION_ERROR`. If the surface cannot use the catalog of a call, the code is
`CATALOG_ERROR`.

**Templates.** A template in a child list makes one node for each item of the data list.
`dataPath()` is the path of the item, for example `/people/1`, and `index()` is its position.

**Placeholders.** `state()` tells if a node is resolved. A component that the surface does not have
at this time gives a node with the state `PENDING`. A resolved node replaces it when the component
arrives. A component of an unknown type gives `UNKNOWN_TYPE`, and a reference that repeats a parent
at the same data scope gives `CYCLIC`. The surface reports the last two to `onError`. Show a
fallback view for each node where `isPlaceholder()` is true.

**Actions.** A view calls `NodeAction.invoke()` when the user operates the component. An event
action resolves its context at that time and the surface reports a `RendererAction` to `onAction`.
`SurfaceGroupModel.onAction()` gives the actions of all the surfaces. A function call action runs
the function and reports no action. A call counts as the result of a user gesture. Code that calls
an action with no gesture uses `invoke(ActionDispatchOptions.WITHOUT_USER_ACTIVATION)`. Then a
function that requires a user gesture does not run, and the surface reports an `EXPRESSION_ERROR`.
`invoke(ActionDispatchOptions.withMetadata(metadata))` sends an `action.metadata` with the event. If
a key of its extensions is not a UAX #31 identifier, `invoke` throws an `A2uiValidationError`.

An action that fails reports no action. The failure goes to `onError`, and `errorCode()` tells its
cause:

| Code               | Cause                                                                                      |
| :----------------- | :----------------------------------------------------------------------------------------- |
| `INVALID_ACTION`   | The event has no name, or its `userMessage` is not text.                                   |
| `EXECUTION_ERROR`  | A function ran and failed.                                                                 |
| `EXPRESSION_ERROR` | The core refused a call before the function ran, or the agent failed a call of the action. |
| `CATALOG_ERROR`    | The surface cannot use the catalog of a call.                                              |

The core resolves the context entries of an event in their sequence, and then the `userMessage`. The
first failure stops the action. A `userMessage` that has no value is absent from the action. An
action can wait for the agent. See [A component calls the agent](#a-component-calls-the-agent).

**Checks.** A component that has `@checks` has three more properties. `props.isValid()` is false if
a check of the severity `error` failed. `props.validationErrors()` has the messages of these checks.
`props.validationResults()` has each failed `ValidationResult`, with its code and its severity. The
message of a rule replaces the message of a function result. The message of a result object that a
data path gives replaces the message of the rule.

**Metadata and accessibility.** `node.metadata()` is a signal of the `@metadata` of the component.
It is not in the properties. `props.accessibility()` gives the resolved `@accessibility` attributes.

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
