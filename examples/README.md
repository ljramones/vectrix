# Vectrix examples

Small, complete programs that show how to use Vectrix for one task each. Every example is tested: its test checks
the results against an independent computation, and CI builds and runs them all against the library at every
commit. The guides in [`../docs/guides`](../docs/guides) quote these files.

| Example | Shows |
|---|---|
| [`FirstSteps`](src/main/java/org/dynamisengine/vectrix/examples/FirstSteps.java) | The conventions: fluent mutable types, read-only `...c` views, `dest` parameters, FAST/STRICT |
| [`SceneCulling`](src/main/java/org/dynamisengine/vectrix/examples/SceneCulling.java) | Batch transform composition, world bounds, frustum culling with a reverse-Z camera |
| [`CameraDepth`](src/main/java/org/dynamisengine/vectrix/examples/CameraDepth.java) | Projections in each depth convention, depth precision, shadow-cascade splits |
| [`LargeWorld`](src/main/java/org/dynamisengine/vectrix/examples/LargeWorld.java) | Positions 10⁹ m from the origin, camera-relative rendering |
| [`Animation`](src/main/java/org/dynamisengine/vectrix/examples/Animation.java) | Two-bone IK and linear blend skinning |
| [`ShLighting`](src/main/java/org/dynamisengine/vectrix/examples/ShLighting.java) | Baking an environment into spherical harmonics, diffuse irradiance |
| [`SignalProcessing`](src/main/java/org/dynamisengine/vectrix/examples/SignalProcessing.java) | Welch spectrum, FIR design and streaming convolution, Butterworth IIR |
| [`KalmanTracking`](src/main/java/org/dynamisengine/vectrix/examples/KalmanTracking.java) | A constant-velocity Kalman filter with outlier gating |
| [`TinyNetwork`](src/main/java/org/dynamisengine/vectrix/examples/TinyNetwork.java) | A two-layer network trained with GEMM, activations, cross-entropy and AdamW |

## Running them

This is a separate Maven project, not part of the library build or its jar, and it is not published. Build the
library first, then the examples:

```bash
mvn install -DskipTests             # in the repository root
mvn -f examples/pom.xml verify      # compile and test the examples
java -p examples/target/lib:examples/target/vectrix-examples.jar -m org.dynamisengine.vectrix.examples all
```

Replace `all` with one name (`first-steps`, `culling`, `camera`, `large-world`, `animation`, `sh-lighting`,
`signal`, `kalman`, `network`) to run a single example. To try an unreleased library build without replacing the
released version in your local repository, install it under another version and pass
`-Dvectrix.version=<that version>`.
