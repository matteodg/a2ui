# A2UI Java

This directory contains the Java client SDK for the A2UI protocol v1.0.

| Module                     | Artifact                          | Contents                                                                    |
| :------------------------- | :-------------------------------- | :-------------------------------------------------------------------------- |
| [`a2ui_core`](a2ui_core)   | `com.google.a2ui:a2ui-java-core`  | The state model and the protocol logic. It has no UI code.                  |
| [`a2ui_swing`](a2ui_swing) | `com.google.a2ui:a2ui-java-swing` | The Swing adapter. It shows the surfaces of the core with Swing components. |

The Maven build also has the module
[`samples/client/swing`](../samples/client/swing), `com.google.a2ui:a2ui-java-swing-gallery`. It is
a sample application that shows the examples of the basic catalog with the Swing adapter. To start
it, run `./mvnw -q -pl ../samples/client/swing -am compile exec:java` from the `java/` directory.

The SDK is in development. It is not released.

## Requirements

- JDK 25 or later.
- No Maven installation is necessary. The Maven wrapper (`mvnw`) downloads Maven.

## Build and test

Run these commands from the `java/` directory.

```sh
./mvnw verify
```

The `verify` phase compiles the code, runs the unit tests, runs the conformance suites and does the
format check.

To correct the format of the sources, run:

```sh
./mvnw spotless:apply
```

Some tests measure a time. They have the JUnit tag `performance`, and the `verify` phase does not
run them. To run only these tests, use the Maven profile `performance` on a computer that does no
other work:

```sh
./mvnw -Pperformance verify
```

## Conformance

The tests of each module run the shared suites in [`conformance/`](../conformance). The harness of
`a2ui_core` has the name `java-core`. The harness of `a2ui_swing` has the name `swing`.

To write the manifest of the executed cases, set `A2UI_CONFORMANCE_MANIFEST_DIR`:

```sh
A2UI_CONFORMANCE_MANIFEST_DIR=/tmp/a2ui-manifest ./mvnw verify
uv run --project ../conformance python ../conformance/scripts/check_executed_cases.py \
  --manifest-dir /tmp/a2ui-manifest --harness java-core --corpus core
uv run --project ../conformance python ../conformance/scripts/check_executed_cases.py \
  --manifest-dir /tmp/a2ui-manifest --harness swing --corpus adapter
uv run --project ../conformance python ../conformance/scripts/check_executed_cases.py \
  --manifest-dir /tmp/a2ui-manifest --harness swing --corpus e2e
```

The harness `java-core` records the cases of protocol versions before 1.0 and the cases of the two
framework adapter suites as skipped, each with its reason.

The harness `swing` runs the four suites of the `adapter` corpus. It runs a case only if the adapter
has a view for each basic catalog component that the case uses. The adapter has each view, thus the
harness runs each case of protocol v1.0. It records the cases of protocol v0.9 as skipped.

The harness `swing` also runs the renderer part of the scenarios of the `e2e` corpus. It records the
scenarios of protocol v0.9.1 as skipped. The `core` corpus belongs to the harness `java-core`.

The timing cases of `core/message_processor_scaling.yaml` run only in the `performance` profile. The
usual build records them as skipped. Give the two builds different manifest directories:

```sh
A2UI_CONFORMANCE_MANIFEST_DIR=/tmp/a2ui-timing ./mvnw -Pperformance verify
uv run --project ../conformance python ../conformance/scripts/check_executed_cases.py \
  --manifest-dir /tmp/a2ui-timing --harness java-core --corpus core \
  --only core/message_processor_scaling.yaml
```

## References

- Protocol: [specification/v1_0/docs/a2ui_protocol.md](../specification/v1_0/docs/a2ui_protocol.md)
- Core design: [blueprints/modules/a2ui_core.blueprint.md](../blueprints/modules/a2ui_core.blueprint.md)
- Adapter design:
  [blueprints/modules/a2ui_framework_adapter.blueprint.md](../blueprints/modules/a2ui_framework_adapter.blueprint.md)
