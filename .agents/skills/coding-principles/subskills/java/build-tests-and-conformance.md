# Subskill: Java Build, Format, Tests & Conformance Harnesses

## Activation Criteria

Activate this subskill when:

- Adding or modifying any `*.java`, `pom.xml`, or `module-info.java` file under `java/` or `samples/client/swing/`, before you build or verify the change.
- Writing or modifying unit tests under `java/*/src/test/java/` or `samples/client/swing/src/test/java/`.
- Changing a file under `java/*/src/test/java/**/conformance/`.
- Changing a file under `conformance/`, `specification/`, or `catalogs/` that affects the Java build.

---

## 1. Build and Format

Configuration: [`java/pom.xml`](../../../../../java/pom.xml)

- Run every command from the `java/` directory with the Maven wrapper. The build needs JDK 25 or later.
- `./mvnw verify` compiles the four modules of the reactor (`a2ui_core`, `a2ui_swing`, the optional `a2ui_swing_javafx`, and the sample `samples/client/swing`), runs the unit tests and the conformance suites, and checks the format. Do not run `./mvnw install`: the modules are not released, and the reactor resolves them without the local repository.
- `./mvnw spotless:apply` applies google-java-format. The property `google-java-format.version` in `java/pom.xml` sets its version. `./scripts/fix_format.sh` runs the same goal from the repository root.
- The compiler runs with `-Xlint:all -Werror`, so a warning fails the build.

---

## 2. Tests

- Tests run headless (`-Djava.awt.headless=true`) and make no network request.
- Tests that measure a time carry the JUnit tag `performance`. `./mvnw verify` leaves them out, and `./mvnw -Pperformance verify` runs only them.

---

## 3. Conformance Harnesses

- The core harness is named `java-core` and the Swing harness `swing`. Each registers every suite of its corpus in its `SuiteRegistry`, fails a case that has an unknown key or action, and records a case that it cannot run as skipped with a reason. Fix the SDK when a case fails; do not weaken the harness.
- Check the executed cases from the repository root after a build that sets `A2UI_CONFORMANCE_MANIFEST_DIR`:

  ```bash
  uv run --project conformance python conformance/scripts/check_executed_cases.py \
    --manifest-dir "$A2UI_CONFORMANCE_MANIFEST_DIR" --harness java-core --corpus core
  uv run --project conformance python conformance/scripts/check_executed_cases.py \
    --manifest-dir "$A2UI_CONFORMANCE_MANIFEST_DIR" --harness swing --corpus adapter
  uv run --project conformance python conformance/scripts/check_executed_cases.py \
    --manifest-dir "$A2UI_CONFORMANCE_MANIFEST_DIR" --harness swing --corpus e2e
  ```
