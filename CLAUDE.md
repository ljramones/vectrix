# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Vectrix (package `org.dynamisengine.vectrix`) is a fork of JOML (Java OpenGL Math Library) — a Java math library for OpenGL/Vulkan rendering. It extends JOML with long-typed vectors (`Vector2L`, `Vector3L`, `Vector4L`) and rendering, animation, GPU-layout and physics math packages. The library is allocation-free, thread-safe, and requires JDK 25 or newer (enforced by `maven-enforcer-plugin`).

## Build Commands

```bash
mvn verify                       # Compile, test, checkstyle, SpotBugs, JaCoCo coverage gate, assemble JARs (plain and -simd)
mvn test                         # Run JUnit 5 tests only
mvn package -DskipTests          # Build JAR without tests
mvn clean verify -Pexperimental  # Experimental profile (Vector API, FFM, JVMCI; also runs the experimental tests)
mvn clean verify -Pexperimental -Dvectrix.test.jvmArgs=-Djoml.forceFfm=true  # run the suite on the FFM backend
```

**Important:** Always use `clean` when switching between the default and experimental profiles. The default profile generates a `module-info.class` during post-processing that causes compilation errors in the experimental profile if left in `target/`.

The default profile also builds the `simd` classifier: a second JCP pass with `__HAS_VECTOR_API__` into `target/generated-sources/simd`, compiled to `target/classes-simd` (`compile-simd`), tested by a second surefire run (`test-simd`, with `--add-modules jdk.incubator.vector`) and packaged as `vectrix-<version>-simd.jar`. Vector API types must stay in nested holder classes so the scalar paths never load `jdk.incubator.vector`.

The build uses a C-style preprocessor (`#ifdef`/`#endif`) via JCP (Java Comment Preprocessor) for conditional compilation. The default profile compiles JDK 25 bytecode (`maven.compiler.release` from `dynamis-parent`) with NIO and Unsafe support. The `experimental` profile (activated by `-Pexperimental`) enables Java Vector API, Foreign Memory Access, and JVMCI.

## Architecture

Every math type follows a three-class pattern:
- **Mutable class** (e.g., `Matrix4f`) — read-write operations, fluent API returning `this`
- **Read-only interface** (suffix `c`, e.g., `Matrix4fc`) — const view for safe parameter passing
- **Stack class** (e.g., `Matrix4fStack`) — extends mutable class with push/pop for scoped transforms

Types come in float (`f`), double (`d`), int (`i`), and long (`L`) variants. All operations write into existing destination objects (zero heap allocation in hot paths).

### Source Layout

- `src/main/java/org/dynamisengine/vectrix/` — 29 top-level packages (33 exports with the curve subpackages), layered as a DAG (`docs/package-map.md`, enforced by `PackageDependencyTest`; `core` depends on nothing)
- `src/test/java/org/dynamisengine/vectrix/test/` — JUnit 5 tests
- `src/bench/java/org/dynamisengine/vectrix/bench/` — JMH benchmarks (`-Pbench`); `src/jmh/java/org/dynamisengine/vectrix/jmh/` — legacy JOML JMH benchmarks (experimental profile)
- `buildhelper/` — ASM-based post-compile tool `ModuleInfoGenerator` (generates `module-info.class`)
- `examples/` — a separate Maven project (`vectrix-examples`, not deployed) of tested example programs; build with `mvn install -DskipTests && mvn -f examples/pom.xml verify`. Its version must equal the library's (`ExamplesVersionTest`)
- `vectrix-gpu/` — a separate Maven project (`vectrix-gpu`, module `org.dynamisengine.vectrix.compute`, not deployed to Central) of GPU twins of Vectrix kernels, written in Slang (`vectrix-gpu/src/main/slang`) and dispatched through DynamisGPU's compute API. Each kernel ports the Vectrix algorithm exactly and is tested bit for bit against it (reference backend in CI; `-Pmetal`/`-Pvulkan` on hardware). Regenerate compiled kernels with `sh vectrix-gpu/scripts/build-kernels.sh` (DynamisGPU's ShaderBuild; kernels listed in `src/main/slang/kernels.list`); see `vectrix-gpu/README.md` for the rounding rules. A change to a kernel's CPU semantics must change its twin in the same commit. Its version must equal the library's (`ExamplesVersionTest`)
- `docs/` — user docs (`getting-started.md`, `conventions.md`, `guides/`); `docs/maintainers/` for releases and benchmarks

### Key Internal Classes

- `MemUtil` — buffer transfers via bounds-checked FFM `MemorySegment`s (default; never touches `sun.misc.Unsafe`), `sun.misc.Unsafe` (`-Djoml.noffm=true` or `-Djoml.forceUnsafe=true`; prints the JDK deprecation warning), or plain NIO (`-Djoml.noffm=true -Djoml.nounsafe=true`). The raw-address API (`getToAddress`/`setFromAddress`) uses an FFM segment over the whole address space (needs `--enable-native-access`, otherwise the JDK warns once), falling back to Unsafe with `-Djoml.forceUnsafe`/`-Djoml.noffm` or when native access is denied; see `MemUtil.AddressAccess`. All backends honour NIO semantics (limit checks, slices, read-only and heap buffers).
- `Math` — fast math with sin/cos lookup tables, configurable via `-Djoml.fastmath`
- `CoreConfig` — math mode: process-wide default (`setMathMode`) plus per-task overrides (`runWithMathMode`/`callWithMathMode`, a `ScopedValue`)
- `Options` — JVM system property configuration (`-Djoml.debug`, `-Djoml.nounsafe`, `-Djoml.forceUnsafe`, `-Djoml.forceFfm`, `-Djoml.noffm`, `-Dvectrix.strictMath`)
- `AccurateMath` — the only place transcendentals choose `java.lang.Math` vs `StrictMath`; never call those directly (a test enforces it). See `docs/determinism.md`.

### Vectrix Additions

The `Vector2L`/`Vector3L`/`Vector4L` classes (with corresponding `*Lc` read-only interfaces) are the primary additions over upstream JOML, providing long-typed vectors for cases requiring integer precision beyond `int` range.

## Testing

- JaCoCo runs in the default profile; the report is in `target/site/jacoco/`. `vectrix.coverage.line.min` and
  `vectrix.coverage.branch.min` in `pom.xml` are a ratchet: raise them when coverage grows, never lower them.
- `TwinParityTest` drives every public method of a type next to its twin with the same random inputs: float vs double
  types, int vs long vectors (exact), and float vs double overloads inside `core.Math`, `CubicCurveMath` and the SH
  classes, with primitive, array and value-type parameters. A new mismatch is usually a real bug in one of the two;
  exempt a method only for a documented input domain. Never let it drive the raw-address API (it would crash the JVM).
- Hot-path APIs must not allocate; `HotPathAllocationTest` measures bytes per call.
- `CrossPlatformDeterminismTest` hashes every public method's results under `-Dvectrix.strictMath=true` and compares
  them with `src/test/resources/determinism/golden.txt`; CI runs it on x86_64 and arm64 in both profiles. An
  intended change to results means re-recording the golden (command in `docs/determinism.md`); an unintended one
  is a determinism bug. New methods are reported, not failed.
- Docs: Java code in `README.md`, `docs/` and `examples/README.md` must be a `<!-- snippet: Example#region -->` quote of a tagged region of an example (`DocSnippetsTest` in examples; `-Dsnippets.write=true` refreshes them), and every API named in backticks must exist and not be deprecated (`DocReferencesTest`).
- `NoConsoleOutputTest`: no `System.out`/`System.err`/`printStackTrace` in main sources; diagnostics go through `System.Logger`.
- `ByteOrderTest` and `AddressApiTest` check that every buffer and raw-address writer produces the same bytes on
  every backend and byte order.
- `AliasingTest` runs every `R op(..., R dest)` method with `dest` aliased to `this` and each argument, and checks the
  result does not depend on `dest`'s previous contents. New destination-style methods are covered automatically;
  read every input into locals before writing `dest`.

## Code Style

Checkstyle enforces: LF line endings, no tabs, MIT license header on all Java files, Javadoc on public types. Config is in `config/checkstyle.xml`.

## Preprocessor Directives

Source files use `#ifdef __HAS_UNSAFE__`, `#ifdef __HAS_VECTOR_API__`, `#ifdef __HAS_FFM__`, `#ifdef __HAS_JVMCI__`, etc. These are processed at build time — do not remove or reformat the `// #ifdef` / `// #endif` comment blocks. NIO is always available on JDK 25+ and is no longer guarded.
