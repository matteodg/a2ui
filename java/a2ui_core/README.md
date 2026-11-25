# A2UI Java Core (`a2ui-java-core`)

This module contains the state model and the protocol logic of the A2UI Java client SDK. It has no
UI code. A framework adapter, such as the Swing adapter, shows the state on the screen.

The module implements the A2UI protocol v1.0. The design obeys
[blueprints/modules/a2ui_core.blueprint.md](../../blueprints/modules/a2ui_core.blueprint.md).

## Packages

The Java module name is `com.google.a2ui.core`. It exports these packages:

| Package                            | Contents                                                                  |
| :--------------------------------- | :------------------------------------------------------------------------ |
| `com.google.a2ui.core.exceptions`  | `A2uiError` and its subclasses. Each failure of the core is one of these. |
| `com.google.a2ui.core.common`      | `Signal`, `EventNotifier`, `Subscription`, `SemVer`, `Uax31`.             |
| `com.google.a2ui.core.state`       | `DataModel`, the observable store of the data of one surface.             |
| `com.google.a2ui.core.expressions` | `ExpressionParser`, the parser for `${...}` templates.                    |

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

## Tests

See [the README of the parent directory](../README.md).
