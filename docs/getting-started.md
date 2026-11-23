# Getting started

## Requirements

JDK 25 or newer. Vectrix has no runtime dependencies.

## Adding the dependency

Maven:

```xml
<dependency>
    <groupId>org.dynamisengine</groupId>
    <artifactId>vectrix</artifactId>
    <version>3.0.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("org.dynamisengine:vectrix:3.0.0")
```

**The SIMD jar.** The `simd` classifier is the same library with Vector API paths compiled into the batch
kernels: FFT, `blas`, `nn`, culling and transform composition. Use it instead of the plain jar, not alongside
it:

```xml
<dependency>
    <groupId>org.dynamisengine</groupId>
    <artifactId>vectrix</artifactId>
    <version>3.0.0</version>
    <classifier>simd</classifier>
</dependency>
```

Its SIMD paths run only with `--add-modules jdk.incubator.vector`. Without the flag the jar runs its scalar paths,
so it is safe to ship either way. The Vector API is an incubator module, so the JDK prints a one-line notice when
it is enabled. Scalar and SIMD paths give the same bits.

## Modules and JVM flags

The module is `org.dynamisengine.vectrix`:

```text
module my.game {
    requires org.dynamisengine.vectrix;
}
```

The jar is also an OSGi bundle with the same symbolic name, and works on the class path.

| Flag | When you need it |
|---|---|
| `--add-modules jdk.incubator.vector` | With the `simd` jar, to enable its SIMD paths |
| `--enable-native-access=org.dynamisengine.vectrix` (or `=ALL-UNNAMED` on the class path) | Only if you use the raw-address methods (`getToAddress`, `setFromAddress`); otherwise the JDK prints a warning on first use |
| `-Dvectrix.strictMath=true` | For results that are bitwise identical across platforms ([determinism](determinism.md)) |

Writing vectors and matrices into NIO buffers and `MemorySegment`s needs no flag.

## A first program

The [`FirstSteps`](../examples/src/main/java/org/dynamisengine/vectrix/examples/FirstSteps.java) example shows
the patterns you will use everywhere. Mutable types have fluent methods, and methods with a `dest` parameter write
their result there:

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

Read-only views let a method promise not to change what you pass it:

<!-- snippet: FirstSteps#readonly -->
```java
// A read-only view (Matrix4fc, Vector3fc, ...) is the same object behind an interface without setters:
// pass it where the callee must not change your data.
Vector3f direction = forward(model, new Vector3f());
```

Quaternions represent rotations:

<!-- snippet: FirstSteps#quaternion -->
```java
Quaternionf a = new Quaternionf().rotateY((float) Math.toRadians(30));
Quaternionf b = new Quaternionf().rotateY((float) Math.toRadians(90));
float angle = (float) Math.toDegrees(a.difference(b, new Quaternionf()).angle());
```

[Conventions](conventions.md) explains these rules in full: precision suffixes, destination parameters,
column-major matrices, FAST and STRICT modes, threading and errors.

## Running the examples

The [examples](../examples/README.md) are complete programs, one per topic, each tested in CI. To run them all
from a checkout:

```bash
mvn install -DskipTests
mvn -f examples/pom.xml verify
java -p examples/target/lib:examples/target/vectrix-examples.jar -m org.dynamisengine.vectrix.examples all
```

## Where next

- [Package map](package-map.md): the 29 top-level packages and how they depend on each other.
- The guides, by task:
  - [Scenes: transforms, bounds and culling](guides/scene-and-culling.md)
  - [Cameras and projections](guides/camera-and-projection.md)
  - [Large worlds](guides/large-worlds.md)
  - [Animation: rotations, poses, IK and skinning](guides/animation.md)
  - [Geometry and physics](guides/geometry-and-physics.md)
  - [Curves, easing, sampling and noise](guides/curves-sampling-noise.md)
  - [Rendering math](guides/rendering-math.md)
  - [GPU data](guides/gpu-data.md)
  - [Signal processing](guides/signal-processing.md)
  - [State estimation](guides/state-estimation.md)
  - [Machine-learning kernels](guides/machine-learning.md)
- [Performance](performance.md): what makes it fast, and which paths to use.
- [Determinism](determinism.md): reproducible results across machines.
