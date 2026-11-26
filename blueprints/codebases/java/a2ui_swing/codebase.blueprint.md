---
codebase_path: java/a2ui_swing
associated_module: a2ui_framework_adapter
module_blueprint_commit: 7fae1b763868a90078280f779b4f59e012a0ffb7
implemented_features:
  node_resolution: 24d1bf9c830071f74c9c908433e73cc6cbe0aa33
local_development:
  test_command: './mvnw -pl a2ui_swing -am verify'
  lint_command: './mvnw -pl a2ui_swing spotless:check'
  format_command: './mvnw -pl a2ui_swing spotless:apply'
---

# **Java Swing Framework Adapter Codebase Blueprint**

## **Architecture & Ecosystem Map**

The Swing renderer for A2UI, built on the node layer of `java/a2ui_core`.

- **Maven module**: `java/a2ui_swing` is a module of the `java/` reactor. Its artifact is `com.google.a2ui:a2ui-java-swing`. Its only dependencies are the core and the JDK module `java.desktop`. It is not released.
- **Java module**: `com.google.a2ui.swing`. Packages follow the module blueprint's layout: `surface` (`A2uiSurface`, `A2uiFallbacks`, `A2uiServices`, `A2uiSwing`), `nodes` (the node dispatcher and the child reconciliation, not exported), `binding` (`TextBinding`, `ValueBinding`), `catalog` (`SwingComponentImplementation`, `SwingComponentView`, `ViewContext`), and `catalog.basic` (`SwingBasicCatalog`, the views, `FlexLayout`).
- **Reactivity bridge**: `A2uiSurface` makes its `NodeResolver` when it is added to a component tree and disposes it when it is removed. Views subscribe to core signals directly on the event dispatch thread (EDT).
- **Sample**: `samples/client/swing` is the gallery application of the module blueprint. It shows the examples of the basic catalog.
- **Tests**: JUnit Jupiter, headless (`-Djava.awt.headless=true`). The conformance harness `swing` in `src/test/java/com/google/a2ui/swing/conformance` runs the `adapter` and `e2e` corpora.

## **Local Technical Decisions & Overrides**

- **Protocol v1.0 only**: The core has no v0.9 model, so the adapter has no v0.9 basic catalog and no legacy entry point. The harness records the cases of `adapter/adapter_conformance_v0_9.yaml` and the scenarios under `e2e/v0_9_1/` as skipped.
- **Stateful views (Form 4)**: A `SwingComponentImplementation` makes one `SwingComponentView` for a mounted node. The adapter calls `update(node, props)` after each change and `dispose()` at the end, and the Swing component of the view is the same object for the life of the view.
- **Component implementations live in the catalog**: The Swing implementations are the components of a core `Catalog` (`SwingBasicCatalog.create()`), and the dispatcher reads `node.impl()`. The adapter has no registry of its own.
- **Threads**: The processor, the surfaces and the views are used on the EDT only, and `A2uiSurface` throws on any other thread. `A2uiSwing.install` makes the RPC timeout deliver on the EDT and reports user activation while the EDT dispatches a mouse button or key event. `A2uiSwing.process` hands a payload to the EDT from another thread.
- **Styling**: Colors, fonts and borders come from the look and feel (`UIManager`). Protocol v1.0 has no theme, and the adapter passes no colors down the tree.
- **Services**: `A2uiServices` holds the extension points for what plain Swing lacks, each with a default: Markdown view, image loader, media player, modal presenter, and URL opener.
- **Media**: Swing cannot play media. With the default `MediaPlayer.NONE`, `Video` and `AudioPlayer` show a control that opens the URL with the `urlOpener` service. An application can supply a player.
- **Accessibility**: `label` and `description` map to the name and description of the `AccessibleContext`. The Java Accessibility API has no equivalent of `hidden` or `live`, so the adapter keeps the attributes as a client property (`A2uiAccessibility`), clears the accessible name and description below a hidden component, and keeps the keyboard focus out of a hidden subtree.
- **axe-core**: The harness does not run the `axeCore` rules of `core/accessibility.yaml`, which need a DOM. It checks its own form of `aria-hidden-focus` with the Swing focus values of each hidden component.
- **Icons**: The module has no icon font. `icons.properties` holds one original shape, as SVG path data, for each icon name of the basic catalog.

## **Validation & Execution Recipes**

Run the commands from the `java/` directory.

- **Test execution**: Run `./mvnw -pl a2ui_swing -am verify`. It also builds and tests the core.
- **Formatting**: Run `./mvnw -pl a2ui_swing spotless:apply` (google-java-format).
- **Gallery**: Run `./mvnw -q -pl ../samples/client/swing -am compile exec:java`.
- **Conformance manifest**: Set `A2UI_CONFORMANCE_MANIFEST_DIR` for the build, then run `conformance/scripts/check_executed_cases.py --harness swing` on that directory with `--corpus adapter` and with `--corpus e2e`.
