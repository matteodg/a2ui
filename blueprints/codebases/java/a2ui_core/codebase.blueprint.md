---
codebase_path: java/a2ui_core
associated_module: a2ui_core
module_blueprint_commit: 6f15ebe7c6f47d41ed511c48ad28ec82972d58ac
implemented_features:
  node_resolution: 24d1bf9c830071f74c9c908433e73cc6cbe0aa33
local_development:
  test_command: './mvnw -pl a2ui_core verify'
  lint_command: './mvnw -pl a2ui_core spotless:check'
  format_command: './mvnw -pl a2ui_core spotless:apply'
---

# **Java Core Codebase Blueprint**

## **Architecture & Ecosystem Map**

The Java core SDK for A2UI renderers: the state model and the protocol logic, with no UI code.

- **Maven module**: `java/a2ui_core` is a module of the `java/` reactor. Its artifact is `com.google.a2ui:a2ui-java-core`. It needs JDK 25 and is not released.
- **Java module**: `com.google.a2ui.core`. The `exports` lines of `module-info.java` are the public API. Packages follow the module blueprint: `common`, `exceptions`, `state`, `expressions`, `catalog`, `basic_catalog.v1_0`, `schema.v1_0`, `validation`, `processing`, `resolution`, and `rpc`. The package `internal` is not exported.
- **Dependencies**: ICU4J, Jackson 3, and `com.networknt:json-schema-validator` are used inside the module and do not appear in a public signature.
- **Tests**: JUnit Jupiter. The conformance harness `java-core` in `src/test/java/com/google/a2ui/core/conformance` runs the `core` corpus.

## **Local Technical Decisions & Overrides**

- **Protocol v1.0 only**: The module has no v0.8, v0.9 or v0.9.1 message models, schemas or basic catalog. The harness records the cases of those versions as skipped. Most cases of `core/node_resolution.yaml` use v0.9.1 fixtures, so `NodeScenariosTest` also runs those fixtures as v1.0, outside the manifest.
- **JSON values**: Public values that hold JSON are plain JVM types (`Map<String, Object>`, `List<Object>`, `String`, `Long`, `Double`, `Boolean`, and `null`) rather than a JSON node type.
- **Threads**: The core is confined to one thread and has no synchronization. The timeout of a call to the agent runs on an `RpcScheduler`, which the application can replace so that it delivers on its UI thread.
- **Per-call validation config and user activation**: There is no `ExecutionContext` type. `MessageProcessor.processMessages(payload, validationConfig)` takes the validation config of one call, and `RpcHandler.setUserActivation` takes a supplier that tells whether a user gesture is in progress.
- **Regular expressions**: `A2uiRegex` parses the A2UI regex dialect, applies the static safety rules, and translates the pattern to `java.util.regex`, as the Java row of the module blueprint's host-engine table describes. That row is added together with this codebase, after the pinned commit.
- **Schema formats**: Schema validation asserts the formats `date`, `time` and `date-time` as ISO 8601 text, which the `DateTimeInput` schema of the basic catalog needs. It does not assert `uri`.
- **Equal values**: The data model does not notify a subscriber when a value is replaced by an equal value. Of the two cases for re-sending a cancelled agent call in `core/agent_function_routing.yaml`, the harness runs `..._on_unrelated_change` and records `..._when_its_value_is_rewritten` as skipped.
- **No agent**: The Java SDK is a renderer only. It implements the renderer side of function routing from the module blueprint (`callRendererFunction`, `callAgentFunction` and `agentFunctionResponse`), and does not implement the `agent_functions` feature or the optional `agent_renderer_app` feature. `MessageProcessor.addCatalog` and the per-call validation config exist, and the cases of `core/add_catalog.yaml` pass.
- **Timing cases**: The cases of `core/message_processor_scaling.yaml` carry the JUnit tag `performance`. The usual build records them as skipped, and `./mvnw -Pperformance verify` runs only them.

## **Validation & Execution Recipes**

Run the commands from the `java/` directory.

- **Test execution**: Run `./mvnw -pl a2ui_core verify`. It compiles with `-Xlint:all -Werror`, runs the unit tests and the conformance suites, and checks the format.
- **Formatting**: Run `./mvnw -pl a2ui_core spotless:apply` (google-java-format).
- **Conformance manifest**: Set `A2UI_CONFORMANCE_MANIFEST_DIR` for the build, then run `conformance/scripts/check_executed_cases.py --harness java-core --corpus core` on that directory.
