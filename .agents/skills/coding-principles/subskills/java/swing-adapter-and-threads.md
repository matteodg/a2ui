# Subskill: Swing Adapter, Threads & the Event Dispatch Thread

## Activation Criteria

Activate this subskill when:

- Changing code under `java/a2ui_swing/` or `samples/client/swing/`.
- Adding a callback, a timer, or a scheduler to the core (`java/a2ui_core/`).
- Writing or modifying a test of a Swing view or of the sample.

---

## 1. Threads

- The core is confined to one thread and has no synchronization. Do not add locks to it.
- The Swing adapter runs on the event dispatch thread (EDT). Views subscribe to core signals directly and do not post updates with `invokeLater`.
- Code on another thread hands a payload to the EDT with `A2uiSwing.process`, and `A2uiSwing.install` makes the RPC scheduler of a processor deliver on the EDT.

---

## 2. Services and Tests

- Tests run headless (`-Djava.awt.headless=true`) and make no network request.
- Put code that needs a window, a browser, or the network behind a service of `A2uiServices` and use a test double.

---

## 3. Details

[`java/a2ui_swing/README.md`](../../../../../java/a2ui_swing/README.md) describes the views of the basic catalog, the layout, the services, the accessibility rules, and the conformance harness of the adapter. [`samples/client/swing/README.md`](../../../../../samples/client/swing/README.md) describes the gallery sample.
