# Java & Swing Progressive Discovery Router

Use this router whenever a pull request or local change modifies Java files or Maven build files across `java/` or `samples/client/swing/`.

---

## Evaluate Java & Swing Subskill Triggers

Inspect the Java files in `git diff main --stat` and apply the rules below. The module READMEs hold the details: [`java/README.md`](../../../../../java/README.md), [`java/a2ui_core/README.md`](../../../../../java/a2ui_core/README.md), [`java/a2ui_swing/README.md`](../../../../../java/a2ui_swing/README.md), and [`samples/client/swing/README.md`](../../../../../samples/client/swing/README.md).

### 1. Build, Format & Verification

- **Configuration**: [`java/pom.xml`](../../../../../java/pom.xml)
- **Activate if**: Any `*.java`, `pom.xml`, or `module-info.java` file is added or modified under `java/` or `samples/client/swing/`.
- **Rules**:
  - Run every command from the `java/` directory with the Maven wrapper. The build needs JDK 25 or later.
  - `./mvnw verify` compiles the three modules of the reactor (`a2ui_core`, `a2ui_swing`, and the sample `samples/client/swing`), runs the unit tests and the conformance suites, and checks the format. Do not run `./mvnw install`: the modules are not released, and the reactor resolves them without the local repository.
  - `./mvnw spotless:apply` applies google-java-format. `./scripts/fix_format.sh` runs the same goal from the repository root.
  - The compiler runs with `-Xlint:all -Werror`, so a warning fails the build.
  - Every source file starts with the Apache license header (year 2024). `pom.xml` files carry it as an XML comment.
  - Tests that measure a time carry the JUnit tag `performance`. `./mvnw verify` leaves them out, and `./mvnw -Pperformance verify` runs only them.

### 2. Module Layout & Public API

- **Activate if**: A package, a `module-info.java`, or a public type is added, moved, or removed.
- **Rules**:
  - `java/a2ui_core` is the Java module `com.google.a2ui.core` (artifact `a2ui-java-core`). `java/a2ui_swing` is `com.google.a2ui.swing` (artifact `a2ui-java-swing`) and requires the core and `java.desktop` transitively. Both implement protocol v1.0 only.
  - The `exports` lines of `module-info.java` are the public facade. Each exported package has a `package-info.java`, and every public or protected element has Javadoc. Code, tests of another module, and README examples import exported packages only.
  - Libraries used inside the core (ICU4J, Jackson, the JSON Schema validator) never appear in a public signature. Public values that hold JSON use plain JVM types: `Map<String, Object>`, `List<Object>`, `String`, `Long`, `Double`, `Boolean`, and `null`.
  - Declare dependency and plugin versions as properties in `java/pom.xml`. `a2ui_swing` has no runtime dependency other than the core and the JDK.
  - The build copies the specification schemas and the basic catalog into the module resources. Do not commit copies of files from `specification/` or `catalogs/`.
  - Do not use wildcard imports. Spotless sorts the imports.

### 3. Threads & the Event Dispatch Thread

- **Activate if**: Code under `java/a2ui_swing/` or `samples/client/swing/` changes, or core code adds a callback, timer, or scheduler.
- **Rules**:
  - The core is confined to one thread and has no synchronization. Do not add locks to it.
  - The Swing adapter runs on the event dispatch thread (EDT). Views subscribe to core signals directly and do not post updates with `invokeLater`. Code on another thread hands a payload to the EDT with `A2uiSwing.process`, and `A2uiSwing.install` makes the RPC scheduler of a processor deliver on the EDT.
  - Tests run headless (`-Djava.awt.headless=true`) and make no network request. Put code that needs a window, a browser, or the network behind a service of `A2uiServices` and use a test double.

### 4. Conformance Harnesses

- **Activate if**: A file under `java/*/src/test/java/**/conformance/` changes, or a change under `conformance/`, `specification/`, or `catalogs/` affects the Java build.
- **Rules**:
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

### 5. Java Changelog (`java/CHANGELOG.md`)

- **Skill**: [`update-changelog`](../../../update-changelog/SKILL.md)
- **Activate if**:
  - `java/CHANGELOG.md` is modified.
  - Any exported API or consumer-observable behavior of `a2ui-java-core` or `a2ui-java-swing` changes (exclude changes confined to `samples/client/swing/` or tests).
