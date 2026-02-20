# JOML++ – Extended Java Rendering Math Library

JOML++ is a fork of [JOML](https://github.com/JOML-CI/JOML), a Java rendering math library used across graphics APIs (OpenGL, Vulkan, and others), purpose-built as the math foundation for [DynamisLightEngine](https://github.com/JOML-CI/JOML). If you already have a rendering-focused math library, use it; if not, this fork gives full control over rendering-critical capabilities. Every major engine capability — scene graphs, physics, skeletal animation, GPU upload, and culling — depends on this layer.

## Why fork?

JOML is an excellent general-purpose linear algebra library, but building a modern rendering engine can demand capabilities it doesn't provide:

- **Long-typed vectors** (`Vector2L`, `Vector3L`, `Vector4L`) — integer precision beyond `int` range, needed for large-world coordinate systems and 64-bit indexing
- **Custom SIMD-style batched operations** — bulk transforms and vectorized math paths that can leverage the Java Vector API for throughput-critical loops
- **SOA (Structure-of-Arrays) layouts** — data layouts optimized for GPU upload and cache-friendly iteration over large entity sets
- **Dual quaternions** — compact, interpolation-friendly representation for rigid-body skinning without the artifacts of linear blend skinning

These aren't incremental patches — they touch the core data types and memory layout assumptions, making a maintained fork the right approach.

## What's different from JOML

| Feature | JOML | JOML++ |
|---|---|---|
| Long vectors (`Vector2L`/`3L`/`4L`) | - | Included with read-only interfaces (`*Lc`) |
| Build system | Gradle | Maven |
| Kotlin extensions | Included | Removed |
| Android / GWT targets | Supported | Not targeted |

Everything else — the allocation-free design, thread safety, Java 1.4+ bytecode compatibility, mutable/read-only/stack class pattern — is inherited from upstream JOML and preserved.

## Build

Requires JDK 8+ to build (any JDK can run the output).

```bash
mvn verify                       # Compile, test, checkstyle, assemble JAR
mvn test                         # Run JUnit 5 tests only
mvn package -DskipTests          # Build JAR without tests
mvn clean verify -Pexperimental  # Experimental profile (JDK 19+)
```

**Important:** Always use `clean` when switching between the default and experimental profiles. The default profile generates a `module-info.class` during post-processing that causes compilation errors in the experimental profile if left in `target/`.

The default profile:
- Preprocesses sources via JCP (`#ifdef __HAS_NIO__`, `__HAS_UNSAFE__`)
- Compiles to Java 8 bytecode, then downgrades to Java 1.2 via `Java6to2`
- Generates `module-info.class` for Java 9+ module support
- Produces a JAR with OSGi bundle headers

The experimental profile (`-Pexperimental`) additionally enables:
- Java Vector API (`jdk.incubator.vector`)
- JVMCI compiler interface
- Foreign Memory Access API
- JMH benchmarks (`src/jmh/java`)

## Benchmarks

JMH benchmarks cover all hot paths targeted for JDK 25 optimization. Build and run:

```bash
mvn clean package -Pbench -DskipTests
java -jar target/benchmarks.jar
```

Quick smoke test (reduced iterations):

```bash
java -jar target/benchmarks.jar -f 1 -wi 3 -i 3 -t 1
```

The suite includes two benchmark classes that run back-to-back:
- **JomlBenchmark** — baseline with Unsafe memory access (`-Djoml.forceUnsafe=true`)
- **JomlFmaBenchmark** — same operations with `Math.fma()` enabled (`-Djoml.useMathFma=true`)

### Baseline results

Measured on Apple M4 Pro, JDK 25.0.1 (Temurin), macOS. These are the reference numbers before JDK 25 optimizations — any change that regresses these should be investigated.

| Benchmark | Baseline (ns/op) | FMA (ns/op) | FMA vs Baseline | Notes |
|-----------|:-:|:-:|:-:|-------|
| `matrix4f_mul` | 9.66 ± 0.08 | 7.37 ± 0.10 | **-23.7%** | General 4x4 multiply |
| `matrix4f_mulAffine` | 5.46 ± 0.02 | 4.36 ± 0.07 | **-20.1%** | Property-specialized affine multiply |
| `matrix4f_invert` | 10.53 ± 0.09 | 7.70 ± 0.14 | **-26.9%** | General inversion |
| `matrix4f_invertAffine` | 5.11 ± 0.09 | 5.15 ± 0.02 | ~0% | Affine inversion fast path |
| `matrix4f_transpose` | 2.73 ± 0.02 | 2.75 ± 0.08 | ~0% | Pure data shuffle, no FMA benefit |
| `vector4f_dot` | 1.04 ± 0.02 | 1.02 ± 0.01 | ~0% | Dot product |
| `vector3f_normalize` | 1.14 ± 0.01 | 1.06 ± 0.00 | **-6.8%** | Uses `Math.invsqrt()` |
| `vector4f_normalize` | 1.25 ± 0.01 | 1.23 ± 0.01 | ~0% | Uses `1/Math.sqrt()` |
| `vector3f_cross` | 0.99 ± 0.02 | 0.97 ± 0.01 | ~0% | Cross product |
| `vector4f_transform` | 2.31 ± 0.03 | 2.29 ± 0.01 | ~0% | Matrix-vector multiply |
| `matrix4f_getFloatBuffer` | 1.63 ± 0.01 | 1.69 ± 0.02 | ~0% | Matrix to FloatBuffer |
| `matrix4f_getByteBuffer` | 1.72 ± 0.01 | 1.75 ± 0.03 | ~0% | Matrix to ByteBuffer (Unsafe path) |
| `math_sin` | 2.52 ± 0.01 | 2.51 ± 0.03 | ~0% | Lookup table sin |
| `math_cos` | 2.24 ± 0.04 | 2.27 ± 0.03 | ~0% | Lookup table cos |

> **Key takeaway:** Enabling `Math.fma()` delivers 20-27% gains on multiply-accumulate-heavy matrix operations (`mul`, `mulAffine`, `invert`). Vector ops, memory transfers, and trig are unaffected — they don't have fused multiply-add patterns.

## Quick start

All operations are allocation-free and write into existing destination objects:

```java
Vector3f v = new Vector3f(0.0f, 1.0f, 0.0f);
Vector3f a = new Vector3f(1.0f, 0.0f, 0.0f);
v.add(a);       // v = v + a
a.cross(v);     // a = a x v
a.normalize();  // a = a/|a|
```

Build transformation matrices with a fluent API:

```java
Matrix4f mvp = new Matrix4f()
    .perspective((float) Math.toRadians(45.0f), 1.0f, 0.01f, 100.0f)
    .lookAt(0.0f, 0.0f, 10.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f);
```

Use read-only views (`*c` interfaces) to prevent unintended mutation:

```java
public Vector4fc getVector() {
    return sharedVector; // caller cannot modify
}
```

## Design

Inherited from JOML:

- **Zero allocation** — no internal temporaries, no hidden heap activity in hot paths
- **Thread-safe** — no shared mutable state; safe to use from any thread as long as a single object isn't written concurrently
- **Three-class pattern** — mutable class (`Matrix4f`), read-only interface (`Matrix4fc`), stack class (`Matrix4fStack`)
- **Type variants** — float (`f`), double (`d`), int (`i`), long (`L`)
- **Post-multiplication** — follows the classic graphics matrix-stack convention; chain calls like a matrix stack

## License

[MIT](LICENSE)

## Acknowledgments

JOML++ is built on the work of [Kai Burjack](https://github.com/httpdigest) and the JOML contributors. Original project: [JOML-CI/JOML](https://github.com/JOML-CI/JOML).
