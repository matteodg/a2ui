# A2UI Java

This directory contains the Java client SDK for the A2UI protocol v1.0.

| Module                   | Artifact                         | Contents                                                   |
| :----------------------- | :------------------------------- | :--------------------------------------------------------- |
| [`a2ui_core`](a2ui_core) | `com.google.a2ui:a2ui-java-core` | The state model and the protocol logic. It has no UI code. |

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

## Conformance

The tests of each module run the shared suites in [`conformance/`](../conformance). The harness of
`a2ui_core` has the name `java-core`.

To write the manifest of the executed cases, set `A2UI_CONFORMANCE_MANIFEST_DIR`:

```sh
A2UI_CONFORMANCE_MANIFEST_DIR=/tmp/a2ui-manifest ./mvnw verify
uv run --project ../conformance python ../conformance/scripts/check_executed_cases.py \
  --manifest-dir /tmp/a2ui-manifest --harness java-core --corpus core
```

## References

- Protocol: [specification/v1_0/docs/a2ui_protocol.md](../specification/v1_0/docs/a2ui_protocol.md)
- Core design: [blueprints/modules/a2ui_core.blueprint.md](../blueprints/modules/a2ui_core.blueprint.md)
