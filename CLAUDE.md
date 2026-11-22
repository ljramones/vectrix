# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Vectrix (package `org.dynamisengine.vectrix`) is a fork of JOML (Java OpenGL Math Library) — a Java math library for OpenGL/Vulkan rendering. It extends JOML with long-typed vectors (`Vector2L`, `Vector3L`, `Vector4L`) and rendering, animation, GPU-layout and physics math packages. The library is allocation-free, thread-safe, and requires JDK 25 or newer (enforced by `maven-enforcer-plugin`).

## Build Commands

```bash
mvn verify                       # Compile, test, checkstyle, SpotBugs, JaCoCo coverage gate, assemble JAR
mvn test                         # Run JUnit 5 tests only
mvn package -DskipTests          # Build JAR without tests
mvn clean verify -Pexperimental  # Experimental profile (Vector API, FFM, JVMCI; also runs the experimental tests)
mvn clean verify -Pexperimental -Dvectrix.test.jvmArgs=-Djoml.forceFfm=true  # run the suite on the FFM backend
```

**Important:** Always use `clean` when switching between the default and experimental profiles. The default profile generates a `module-info.class` during post-processing that causes compilation errors in the experimental profile if left in `target/`.

The build uses a C-style preprocessor (`#ifdef`/`#endif`) via JCP (Java Comment Preprocessor) for conditional compilation. The default profile compiles JDK 25 bytecode (`maven.compiler.release` from `dynamis-parent`) with NIO and Unsafe support. The `experimental` profile (activated by `-Pexperimental`) enables Java Vector API, Foreign Memory Access, and JVMCI.

## Architecture

Every math type follows a three-class pattern:
- **Mutable class** (e.g., `Matrix4f`) — read-write operations, fluent API returning `this`
- **Read-only interface** (suffix `c`, e.g., `Matrix4fc`) — const view for safe parameter passing
- **Stack class** (e.g., `Matrix4fStack`) — extends mutable class with push/pop for scoped transforms

Types come in float (`f`), double (`d`), int (`i`), and long (`L`) variants. All operations write into existing destination objects (zero heap allocation in hot paths).

### Source Layout

- `src/main/java/org/dynamisengine/vectrix/` — about 236 Java files in 19 top-level packages: `core` (JOML-derived vectors, matrices, quaternions), `affine`, `soa`, `simd`, `curve`, `geometry`, `gpu`, `sampling`, `sh`, `fft`, `optics`, `ltc`, `renderingmath`, `color`, `easing`, `sdf`, `hash`, `physics`, `experimental`
- `src/test/java/org/dynamisengine/vectrix/test/` — JUnit 5 tests
- `src/bench/java/org/dynamisengine/vectrix/bench/` — JMH benchmarks (`-Pbench`); `src/jmh/java/org/dynamisengine/vectrix/jmh/` — legacy JOML JMH benchmarks (experimental profile)
- `buildhelper/` — ASM-based post-compile tool `ModuleInfoGenerator` (generates `module-info.class`)

### Key Internal Classes

- `MemUtil` — buffer transfers via bounds-checked FFM `MemorySegment`s (default; never touches `sun.misc.Unsafe`), `sun.misc.Unsafe` (`-Djoml.noffm=true` or `-Djoml.forceUnsafe=true`; prints the JDK deprecation warning), or plain NIO (`-Djoml.noffm=true -Djoml.nounsafe=true`). The raw-address API (`getToAddress`/`setFromAddress`) still uses Unsafe and initialises it lazily. All backends honour NIO semantics (limit checks, slices, read-only and heap buffers).
- `Math` — fast math with sin/cos lookup tables, configurable via `-Djoml.fastmath`
- `Options` — JVM system property configuration (`-Djoml.debug`, `-Djoml.nounsafe`, `-Djoml.forceUnsafe`, `-Djoml.forceFfm`, `-Djoml.noffm`, `-Dvectrix.strictMath`)
- `AccurateMath` — the only place transcendentals choose `java.lang.Math` vs `StrictMath`; never call those directly (a test enforces it). See `docs/determinism.md`.

### Vectrix Additions

The `Vector2L`/`Vector3L`/`Vector4L` classes (with corresponding `*Lc` read-only interfaces) are the primary additions over upstream JOML, providing long-typed vectors for cases requiring integer precision beyond `int` range.

## Testing

- JaCoCo runs in the default profile; the report is in `target/site/jacoco/`. `vectrix.coverage.line.min` and
  `vectrix.coverage.branch.min` in `pom.xml` are a ratchet: raise them when coverage grows, never lower them.
- `FloatDoubleParityTest` drives every public method of each float type next to its double twin with the same random
  inputs. A new mismatch is usually a real bug in one of the two; exempt a method only for a documented input domain.
- Hot-path APIs must not allocate; `HotPathAllocationTest` measures bytes per call.
- `AliasingTest` runs every `R op(..., R dest)` method with `dest` aliased to `this` and each argument, and checks the
  result does not depend on `dest`'s previous contents. New destination-style methods are covered automatically;
  read every input into locals before writing `dest`.

## Code Style

Checkstyle enforces: LF line endings, no tabs, MIT license header on all Java files, Javadoc on public types. Config is in `config/checkstyle.xml`.

## Preprocessor Directives

Source files use `#ifdef __HAS_UNSAFE__`, `#ifdef __HAS_VECTOR_API__`, `#ifdef __HAS_FFM__`, `#ifdef __HAS_JVMCI__`, etc. These are processed at build time — do not remove or reformat the `// #ifdef` / `// #endif` comment blocks. NIO is always available on JDK 25+ and is no longer guarded.
