# A2UI Java Core (`a2ui-java-core`)

This module contains the state model and the protocol logic of the A2UI Java client SDK. It has no
UI code. A framework adapter, such as the Swing adapter, shows the state on the screen.

The module implements the A2UI protocol v1.0. The design obeys
[blueprints/modules/a2ui_core.blueprint.md](../../blueprints/modules/a2ui_core.blueprint.md).

## Packages

The Java module name is `com.google.a2ui.core`. It exports these packages:

| Package                           | Contents                                                                  |
| :-------------------------------- | :------------------------------------------------------------------------ |
| `com.google.a2ui.core.exceptions` | `A2uiError` and its subclasses. Each failure of the core is one of these. |
| `com.google.a2ui.core.common`     | `Signal`, `EventNotifier`, `Subscription`, `SemVer`, `Uax31`.             |

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

## Tests

See [the README of the parent directory](../README.md).
