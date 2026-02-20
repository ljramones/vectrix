# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JOML++ is a fork of JOML (Java OpenGL Math Library) — a Java math library for OpenGL/Vulkan rendering. It extends JOML with long-typed vector classes (`Vector2L`, `Vector3L`, `Vector4L`). The library is allocation-free, thread-safe, and targets Java 1.4+ compatibility via bytecode downgrading.

## Build Commands

```bash
mvn verify                       # Compile, test, checkstyle, assemble JAR
mvn test                         # Run JUnit 5 tests only
mvn package -DskipTests          # Build JAR without tests
mvn clean verify -Pexperimental  # Experimental profile (JDK 19+)
```

**Important:** Always use `clean` when switching between the default and experimental profiles. The default profile generates a `module-info.class` during post-processing that causes compilation errors in the experimental profile if left in `target/`.

The build uses a C-style preprocessor (`#ifdef`/`#endif`) via JCP (Java Comment Preprocessor) for conditional compilation. The default profile targets Java 8 bytecode with NIO and Unsafe support. The `experimental` profile (activated by `-Pexperimental`) enables Java Vector API, Foreign Memory Access, and JVMCI.

## Architecture

Every math type follows a three-class pattern:
- **Mutable class** (e.g., `Matrix4f`) — read-write operations, fluent API returning `this`
- **Read-only interface** (suffix `c`, e.g., `Matrix4fc`) — const view for safe parameter passing
- **Stack class** (e.g., `Matrix4fStack`) — extends mutable class with push/pop for scoped transforms

Types come in float (`f`), double (`d`), int (`i`), and long (`L`) variants. All operations write into existing destination objects (zero heap allocation in hot paths).

### Source Layout

- `src/main/java/org/joml/` — Core library (~100 Java files): matrices, vectors, quaternions, intersections, sampling, noise
- `src/test/java/org/joml/test/` — JUnit 5 tests
- `src/jmh/java/org/joml/jmh/` — JMH benchmarks (experimental profile only)
- `buildhelper/` — ASM-based post-compile tools: `Java6to2` (bytecode downgrader) and `ModuleInfoGenerator` (Java 9 module-info)

### Key Internal Classes

- `MemUtil` — memory operations via `sun.misc.Unsafe` (fast path) or NIO buffers (fallback)
- `Math` — fast math with sin/cos lookup tables, configurable via `-Djoml.fastmath`
- `Options` — JVM system property configuration (`-Djoml.debug`, `-Djoml.nounsafe`, `-Djoml.forceUnsafe`)

### JOML++ Additions

The `Vector2L`/`Vector3L`/`Vector4L` classes (with corresponding `*Lc` read-only interfaces) are the primary additions over upstream JOML, providing long-typed vectors for cases requiring integer precision beyond `int` range.

## Code Style

Checkstyle enforces: LF line endings, no tabs, MIT license header on all Java files, Javadoc on public types. Config is in `config/checkstyle.xml`.

## Preprocessor Directives

Source files use `#ifdef __HAS_NIO__`, `#ifdef __HAS_UNSAFE__`, `#ifdef __HAS_VECTOR_API__`, etc. These are processed at build time — do not remove or reformat the `// #ifdef` / `// #endif` comment blocks.
