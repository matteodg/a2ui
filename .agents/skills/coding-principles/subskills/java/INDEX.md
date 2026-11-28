# Java & Swing Progressive Discovery Router

Use this router whenever a pull request or local change modifies Java files or Maven build files across `java/` or `samples/client/swing/`.

---

## Evaluate Java & Swing Subskill Triggers

Inspect the Java files in `git diff main --stat` and read the subskill documents below whose activation criteria match your changes:

### 1. Module Layout, Public API & Coding Standards

- **Document**: [`coding-standards-and-public-api.md`](coding-standards-and-public-api.md)
- **Activate if**:
  - Any `*.java` file is added, modified, or refactored under `java/` or `samples/client/swing/`.
  - A package, a `module-info.java`, or a public type is added, moved, or removed.
  - A dependency or a plugin is added or changed in a `pom.xml`.

### 2. Build, Format, Tests & Conformance Harnesses

- **Document**: [`build-tests-and-conformance.md`](build-tests-and-conformance.md)
- **Activate if**:
  - Any `*.java`, `pom.xml`, or `module-info.java` file is added or modified under `java/` or `samples/client/swing/`.
  - Running the Maven build (`./mvnw verify`), the formatter (`./mvnw spotless:apply`), or the `performance` profile.
  - A file under `java/*/src/test/java/**/conformance/` changes, or a change under `conformance/`, `specification/`, or `catalogs/` affects the Java build.

### 3. Swing Adapter, Threads & the Event Dispatch Thread

- **Document**: [`swing-adapter-and-threads.md`](swing-adapter-and-threads.md)
- **Activate if**:
  - Code under `java/a2ui_swing/` or `samples/client/swing/` changes.
  - Core code adds a callback, a timer, or a scheduler.

### 4. Java Changelog (`java/CHANGELOG.md`)

- **Skill**: [`update-changelog`](../../../update-changelog/SKILL.md)
- **Activate if**:
  - `java/CHANGELOG.md` is modified.
  - Any exported API or consumer-observable behavior of `a2ui-java-core` or `a2ui-java-swing` changes (exclude changes confined to `samples/client/swing/` or tests).
