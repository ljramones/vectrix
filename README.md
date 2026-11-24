# Vectrix

Vectrix is a pure-Java math library for real-time graphics, simulation, signal processing and machine learning.
It covers:
- vectors, matrices and quaternions;
- cameras, culling, animation, inverse kinematics and skinning;
- spherical harmonics, BRDFs, optics, atmosphere and color science;
- FFTs, filters and Kalman filters;
- reproducible BLAS and neural-network kernels.

- **Allocation-free.** Results go into objects and arrays you pass, so a frame loop creates no garbage.
- **Precise.** Algorithms are chosen for accuracy, the Javadoc states errors, and tests check them against
  high-precision references.
- **Reproducible.** SIMD and scalar paths give the same bits. The BLAS and neural-network kernels give the same
  bits on every platform, and one documented setting makes the rest of the library deterministic across platforms
  too.
- **Fast.** It is written for the JIT, with optional Vector API kernels.

It requires JDK 25 or newer and has no runtime dependencies. Vectrix began as a fork of
[JOML](https://github.com/JOML-CI/JOML) and keeps its core API. [joml-divergence.md](docs/joml-divergence.md)
explains what changed.

## Install

```xml
<dependency>
    <groupId>org.dynamisengine</groupId>
    <artifactId>vectrix</artifactId>
    <version>3.1.0</version>
</dependency>
```

For the Vector API kernels, depend on `<classifier>simd</classifier>` instead and run with
`--add-modules jdk.incubator.vector`. The module name is `org.dynamisengine.vectrix`.
[Getting started](docs/getting-started.md) covers Gradle, modules and JVM flags.

## A taste

<!-- snippet: FirstSteps#fluent -->
```java
// Mutable types have fluent methods that change `this` and return it.
Matrix4f model = new Matrix4f()
    .translate(10, 0, 0)
    .rotateY((float) Math.toRadians(90))
    .scale(2);

// Methods with a `dest` parameter write the result there and leave the inputs alone, so a frame loop can
// reuse its objects instead of allocating.
Vector3f moved = model.transformPosition(new Vector3f(1, 0, 0), new Vector3f());
```

Every type follows the same rules, which [conventions.md](docs/conventions.md) sets out:
- an `f`/`d` suffix for float or double;
- a read-only `...c` interface;
- results written to a `dest` you pass.

The [examples](examples/README.md) are complete, tested programs for each topic.

## What is in it

| Area | Packages | Guide |
|---|---|---|
| Linear algebra | `core`, `affine`, `linalg` | [Conventions](docs/conventions.md) |
| Scenes and culling | `soa`, `geometry` | [Scenes: transforms, bounds and culling](docs/guides/scene-and-culling.md) |
| Cameras | `camera` | [Cameras and projections](docs/guides/camera-and-projection.md) |
| Large worlds | `world` | [Large worlds](docs/guides/large-worlds.md) |
| Animation | `animation`, `ik`, `soa` | [Animation](docs/guides/animation.md) |
| Geometry and physics | `geometry`, `sdf`, `physics`, `linalg` | [Geometry and physics](docs/guides/geometry-and-physics.md) |
| Curves, sampling, noise | `curve`, `easing`, `sampling`, `hash` | [Curves, easing, sampling and noise](docs/guides/curves-sampling-noise.md) |
| Shading and lighting | `sh`, `ltc`, `brdf`, `optics`, `atmosphere`, `color`, `spherical`, `renderingmath` | [Rendering math](docs/guides/rendering-math.md) |
| GPU data | `gpu` | [GPU data](docs/guides/gpu-data.md) |
| Signal processing | `fft`, `dsp` | [Signal processing](docs/guides/signal-processing.md) |
| Estimation and tracking | `dsp` | [State estimation](docs/guides/state-estimation.md) |
| Machine learning | `blas`, `nn` | [Machine-learning kernels](docs/guides/machine-learning.md) |

[package-map.md](docs/package-map.md) describes all 29 top-level packages and their dependencies.

The GPU versions of the GEMM, Philox and softmax kernels are in a separate artifact,
[vectrix-gpu](vectrix-gpu/README.md). It runs them through DynamisGPU on Metal or Vulkan and matches the CPU
results bit for bit. The Vectrix jar itself never touches the GPU.

## Documentation

- [Getting started](docs/getting-started.md): install, modules, flags, a first program.
- [Conventions](docs/conventions.md): the rules every API follows.
- [Guides](docs/README.md#guides): one per task.
- [Performance](docs/performance.md) and [benchmarks](docs/benchmarks.md).
- [Determinism](docs/determinism.md): reproducible results across machines.
- [API policy](docs/api-policy.md): what is stable, and how deprecation works.
- [CHANGELOG](CHANGELOG.md).
- The Javadoc, published with each release on Maven Central.

## Building from source

```bash
mvn verify                              # tests, Checkstyle, SpotBugs, coverage; builds the plain and simd jars
mvn clean verify -Pexperimental         # also the experimental paths (Vector API, FFM, JVMCI)
mvn install -DskipTests && mvn -f examples/pom.xml verify   # the examples
mvn install -DskipTests && mvn -f vectrix-gpu/pom.xml verify  # the GPU kernels (vectrix-gpu/README.md)
```

[docs/maintainers/](docs/maintainers/) covers benchmarking and releases.

## License

MIT: see [LICENSE](LICENSE). The JOML lineage is attributed in [NOTICE](NOTICE). The CIE colorimetric tables in
`color.CieData` (CIE 1931 and 1964 observers, illuminant D65; © CIE) are CC BY-SA 4.0, attributed in
[NOTICE](NOTICE). That file alone is under that license.
