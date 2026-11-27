# Subskill: Java Module Layout, Public API & Coding Standards

## Activation Criteria

Activate this subskill when:

- Adding, modifying, or refactoring any `*.java` file under `java/` or `samples/client/swing/`.
- Adding, moving, or removing a package, a `module-info.java`, or a public type.
- Adding or changing a dependency or a plugin in a `pom.xml`.

---

## 1. Module Layout

- `java/a2ui_core` is the Java module `com.google.a2ui.core` (artifact `a2ui-java-core`). `java/a2ui_swing` is `com.google.a2ui.swing` (artifact `a2ui-java-swing`) and requires the core and `java.desktop` transitively. `java/a2ui_swing_javafx` is `com.google.a2ui.swing.javafx` (artifact `a2ui-java-swing-javafx`), an optional media player for the Swing adapter. All implement protocol v1.0 only.
- The sample `samples/client/swing` is the third module of the Maven reactor in [`java/pom.xml`](../../../../../java/pom.xml).
- The module READMEs hold the details: [`java/README.md`](../../../../../java/README.md), [`java/a2ui_core/README.md`](../../../../../java/a2ui_core/README.md), [`java/a2ui_swing/README.md`](../../../../../java/a2ui_swing/README.md), [`java/a2ui_swing_javafx/README.md`](../../../../../java/a2ui_swing_javafx/README.md), and [`samples/client/swing/README.md`](../../../../../samples/client/swing/README.md).

---

## 2. Public API

- The `exports` lines of `module-info.java` are the public facade. Each exported package has a `package-info.java`, and every public or protected element has Javadoc. Code, tests of another module, and README examples import exported packages only.
- A package that `module-info.java` does not export is not public API, for example `com.google.a2ui.core.internal`, `com.google.a2ui.swing.nodes`, and `com.google.a2ui.swing.internal`. The tests of a module are in the same module and can use these packages.
- Libraries used inside the core (ICU4J, Jackson, the JSON Schema validator) never appear in a public signature. Public values that hold JSON use plain JVM types: `Map<String, Object>`, `List<Object>`, `String`, `Long`, `Double`, `Boolean`, and `null`.

---

## 3. Dependencies and Resources

- Declare dependency and plugin versions as properties in `java/pom.xml`. `a2ui_swing` has no runtime dependency other than the core and the JDK. Only `a2ui_swing_javafx` depends on OpenJFX: keep each JavaFX type out of the other modules.
- The build copies the specification schemas and the basic catalog into the module resources. Do not commit copies of files from `specification/` or `catalogs/`.

---

## 4. Source Files

- Every source file starts with the Apache license header (year 2024). `pom.xml` files carry it as an XML comment.
- Do not use wildcard imports. Spotless sorts the imports.
- The compiler runs with `-Xlint:all -Werror`, so a warning fails the build.
