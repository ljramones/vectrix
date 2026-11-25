# Migrating to 4.0

Vectrix 4.0 splits the library into four jars, and removes the GPU layout classes that moved to DynamisGPU in 3.3.
No package is renamed, and no other public method changes.

## Dependencies

The library is now four jars, each a JPMS module of its own:

| Jar | Module | Packages |
|---|---|---|
| `vectrix-core` | `org.dynamisengine.vectrix` | `core`, `simd`, `affine`, `animation`, `curve`, `easing`, `hash`, `ik`, `linalg`, `physics`, `sdf`, `soa`, `world`, `geometry`, `blas` |
| `vectrix-render` | `org.dynamisengine.vectrix.render` | `color`, `brdf`, `optics`, `atmosphere`, `renderingmath`, `ltc`, `sh`, `spherical`, `sampling`, `camera`, `gpu` |
| `vectrix-signal` | `org.dynamisengine.vectrix.signal` | `fft`, `dsp` |
| `vectrix-ml` | `org.dynamisengine.vectrix.ml` | `nn` |

`vectrix-render`, `vectrix-signal` and `vectrix-ml` each depend on `vectrix-core`, so depending on one of them brings
core too.

**The whole library, one dependency.** The `vectrix` artifact holds no classes and depends on all four jars. A 3.x build
keeps working with a version bump:

```xml
<dependency>
    <groupId>org.dynamisengine</groupId>
    <artifactId>vectrix</artifactId>
    <version>4.0.0</version>
</dependency>
```

**Only what you use.** A program that needs only vectors and transforms can depend on `vectrix-core` alone; one that
also uses FFTs adds `vectrix-signal`:

```xml
<dependency>
    <groupId>org.dynamisengine</groupId>
    <artifactId>vectrix-core</artifactId>
    <version>4.0.0</version>
</dependency>
<dependency>
    <groupId>org.dynamisengine</groupId>
    <artifactId>vectrix-signal</artifactId>
    <version>4.0.0</version>
</dependency>
```

### The SIMD jars

Each of the four jars has a `simd` classifier, with the Vector API paths of its own kernels. The `vectrix` artifact has
none, because a classifier cannot change dependencies. So a 3.x dependency on `vectrix` with `<classifier>simd</classifier>`
becomes the four jars (or the ones you use), each with that classifier:

```xml
<dependency>
    <groupId>org.dynamisengine</groupId>
    <artifactId>vectrix-core</artifactId>
    <version>4.0.0</version>
    <classifier>simd</classifier>
</dependency>
<dependency>
    <groupId>org.dynamisengine</groupId>
    <artifactId>vectrix-render</artifactId>
    <version>4.0.0</version>
    <classifier>simd</classifier>
</dependency>
<!-- and vectrix-signal and vectrix-ml the same way -->
```

Use the `simd` classifier for every Vectrix jar you use. A plain and a SIMD jar work together (the API is the same), but
only the SIMD jars' kernels take the Vector API paths. As before, those paths run only with
`--add-modules jdk.incubator.vector`, and scalar and SIMD paths give the same bits. The `experimental` classifier is
split the same way.

### Gradle

```kotlin
implementation("org.dynamisengine:vectrix:4.0.0")                 // all four jars
implementation("org.dynamisengine:vectrix-core:4.0.0:simd")        // or one jar, with its classifier
```

## Modules

On the class path nothing changes. On the module path, a module requires the Vectrix modules whose packages it uses:

```text
module my.game {
    requires org.dynamisengine.vectrix;          // core, affine, geometry, ...
    requires org.dynamisengine.vectrix.render;   // camera, color, sh, gpu, ...
}
```

`org.dynamisengine.vectrix.render`, `.signal` and `.ml` require `org.dynamisengine.vectrix` transitively, so a module
that requires one of them also reads core. The `vectrix` artifact's jar is an automatic module
(`org.dynamisengine.vectrix.all`) with no packages, so requiring it gives you nothing; require the four modules
instead.

**OSGi:** the four jars are four bundles, whose symbolic names are the module names.

## Removed

The GPU layout classes deprecated in 3.3 are gone from `gpu`. They have lived in DynamisGPU since its 1.2.0:

| Removed from Vectrix | Use |
|---|---|
| `gpu.ShaderLayout` | `org.dynamisengine.gpu.api.layout.ShaderLayout` (`dynamis-gpu-api`) |
| `gpu.VertexLayout`, `gpu.VertexAttribute`, `gpu.VertexAttributeFormat` | `org.dynamisengine.gpu.api.layout.VertexLayout`, `org.dynamisengine.gpu.api.layout.VertexAttribute`, `org.dynamisengine.gpu.api.layout.VertexFormat` (`dynamis-gpu-api`), bound by shader location |
| `gpu.GpuTransformLayout`, `gpu.GpuTransformWriteKernels`, `gpu.InstanceSubmissionPipeline` | `org.dynamisengine.gpu.vectrix.GpuTransformLayout`, `org.dynamisengine.gpu.vectrix.GpuTransformWriteKernels`, `org.dynamisengine.gpu.vectrix.InstanceSubmissionPipeline` (`dynamis-gpu-vectrix`) |

The numeric encodings stay in `gpu`: `Half`, `PackedNorm`, `PackedFormats`, `OctaNormal`, `QuatCompression` and
`MeshletCones`. [Getting data to the GPU](guides/gpu-data.md) lists where everything lives.

## New

- **`BlockQuant`** (`nn`): llama.cpp's block quantization formats (Q8_0, Q4_0, Q4_1, Q4_K, Q5_K, Q6_K). It decodes
  them bit for bit as gguf-py does, and encodes without allocating.
