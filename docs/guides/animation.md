# Animation: rotations, poses, IK and skinning

This guide covers the math of character and object animation:
- interpolating rotations and keyframes;
- blending and composing skeletal poses;
- inverse kinematics;
- deforming meshes by skinning.

The packages are `core` (quaternions), `animation`, `ik` and `soa`. Keyframe curves for positions and paths are
in [the curves guide](curves-sampling-noise.md).

The tested program is [`Animation`](../../examples/src/main/java/org/dynamisengine/vectrix/examples/Animation.java):
two-bone IK feeding linear blend skinning.

## Which class for which job

| Task | Class | Notes |
|---|---|---|
| Interpolate two rotations | `Quaternionf.slerp`, `Quaternionf.nlerp` | Along the shorter arc |
| Smooth rotation through keys | `QuaternionCurvesf.squad`, `QuaternionCurvesf.bezier`, `QuaternionCurvesf.cumulativeBSpline` | Over key arrays |
| Prepare keys for interpolation | `QuaternionCurvesf.unroll` | Flips signs so neighbors are on the same hemisphere |
| Average several rotations | `QuaternionCurvesf.weightedMean` | The geodesic (Karcher) mean |
| Rotation as a vector and back | `Quaternionf.log`, `Quaternionf.exp` | Tangent space |
| Split a rotation about an axis | `Quaternionf.swingTwist` | For twist bones and joint limits |
| Angular velocity | `Quaternionf.angularVelocity`, `Quaternionf.integrate` | |
| Smoothly follow a moving target | `QuaternionSpringf.step` | Critically damped; frame-rate independent |
| Pose arrays: forward kinematics, blending, additive layers | `PoseKernelsf` | Flat per-joint arrays with parent indices |
| Reach a target with an arm or leg | `TwoBoneIkf.solve` | Analytic, with a pole vector |
| Longer chains | `Fabrikf.solve`, `Ccdf.solve`, `DampedLeastSquaresf` | Iterative; bend limits |
| Joint limits, look-at | `JointLimitsf`, `Aimf.aim` | Cone, hinge, swing-twist limits |
| Deform a mesh | `SkinningKernels` | Linear blend or dual quaternion |

`QuaternionCurvesf`, `PoseKernelsf`, `QuaternionSpringf` and the `ik` solvers each have a double twin (`...d`).

## Rotations and keyframes

Rotations are unit quaternions `(x, y, z, w)`.
- `slerp` moves at constant angular speed. `nlerp` is cheaper and close for small angles.
- For more than two keys, run `QuaternionCurvesf.unroll` on the key array once. Then evaluate with:
  - `QuaternionCurvesf.squad` (with `QuaternionCurvesf.squadControlPoints`), which is smooth through every key;
  - `QuaternionCurvesf.bezier`, with explicit control rotations;
  - `QuaternionCurvesf.cumulativeBSpline`, which is smoother still but does not pass through the keys.
- `QuaternionCurvesf.weightedMean` averages any number of weighted rotations correctly, for example when blending
  several animations. A component-wise average of quaternions is not a rotation average.
- `QuaternionSpringf.step` moves a rotation toward a goal with a critically damped spring, given a half-life. The
  solution is exact, so the result does not depend on the frame rate.

## Poses

`PoseKernelsf` works on whole skeletons stored as flat arrays:
- translations `xyz`, rotations `xyzw` and scales `xyz` per joint;
- a parent index per joint, `-1` for roots, with every parent before its children.

The kernels:
- `PoseKernelsf.localToModel` and `PoseKernelsf.modelToLocal` convert between parent-relative and model space.
- `PoseKernelsf.blendRotations`, `PoseKernelsf.blendVectors`, `PoseKernelsf.slerpMasked` and
  `PoseKernelsf.lerp3Masked` blend poses, optionally per joint.
- `PoseKernelsf.makeAdditiveRotations` and `PoseKernelsf.applyAdditiveRotations` build and apply additive layers.

Scale composes per component, which is exact for uniform scale. A transform hierarchy of this kind cannot represent
non-uniform scale under rotation, which would shear.

## Inverse kinematics

`TwoBoneIkf.solve` places the middle and end joints of a three-joint chain so the end reaches the target:
- The chain bends in the plane of the root, the target and a pole point, on the pole's side.
- It returns the remaining distance, 0 when the target is reachable.
- It returns the joint positions, plus the world-space rotations to apply to the root bone and then to the middle
  bone.

<!-- snippet: Animation#ik -->
```java
Vector3f shoulder = new Vector3f(0, 0, 0);
Vector3f elbow = new Vector3f(1, 0, 0);
Vector3f wrist = new Vector3f(2, 0, 0);
Vector3f pole = new Vector3f(0, 0, -1); // the elbow bends toward the pole

Vector3f newElbow = new Vector3f();
Vector3f newWrist = new Vector3f();
Quaternionf shoulderDelta = new Quaternionf();
Quaternionf elbowDelta = new Quaternionf();
// maxStretch 1: the bones keep their length. Returns how far the wrist ends from the target.
float missedBy = TwoBoneIkf.solve(shoulder, elbow, wrist, TARGET, pole, 1f, newElbow, newWrist,
    shoulderDelta, elbowDelta);
```

For longer chains, use these on flat position arrays:
- `Fabrikf.solve` and `Ccdf.solve` are iterative and accept bend limits.
- `DampedLeastSquaresf` takes a Jacobian step. It is useful for many targets or end-effector orientation.

`JointLimitsf` clamps rotations to cones, hinges and swing-twist limits. `Aimf.aim` turns a bone to look at a
point.

## Skinning

A skinned vertex moves with the joints it is weighted to. Each joint's skinning transform is its posed world
transform times the inverse of its bind-pose world transform. It maps the bind-pose vertex to the posed one.

<!-- snippet: Animation#pose -->
```java
// Skinning transforms map bind-pose positions to posed ones: posedWorld · inverse(bindWorld).
// Bind pose: both joints unrotated, at the shoulder and the elbow.
Transformf shoulderBind = new Transformf();
Transformf elbowBind = new Transformf();
elbowBind.translation.set(elbow);

// Posed world transforms: the deltas are world-space rotations, the root's first.
Transformf shoulderPosed = new Transformf();
shoulderPosed.rotation.set(shoulderDelta);
Transformf elbowPosed = new Transformf();
elbowPosed.translation.set(newElbow);
elbowDelta.mul(shoulderDelta, elbowPosed.rotation);

TransformSoA skinning = new TransformSoA(2);
Transformf inverse = new Transformf();
Transformf skin = new Transformf();
skinning.set(0, Transformf.compose(shoulderPosed, shoulderBind.invertRigidFast(inverse), skin));
skinning.set(1, Transformf.compose(elbowPosed, elbowBind.invertRigidFast(inverse), skin));
```

`SkinningKernels.skinLbs4` applies linear blend skinning with up to four joints per vertex, each joint with its
translation, rotation and scale. Indices and weights are interleaved, four per vertex. Positions are separate `x`,
`y`, `z` arrays. Array sizes and joint indices are checked before anything is written.

<!-- snippet: Animation#skin -->
```java
// Up to four joints per vertex: indices and weights interleaved, four per vertex. Vertices blend from the
// upper arm (joint 0) to the forearm (joint 1) around the elbow.
float[] inX = new float[VERTICES];
float[] inY = new float[VERTICES];
float[] inZ = new float[VERTICES];
int[] joints = new int[4 * VERTICES];
float[] weights = new float[4 * VERTICES];
for (int v = 0; v < VERTICES; v++) {
    float along = 2f * v / (VERTICES - 1);
    inX[v] = along;
    float w1 = Math.clamp((along - 0.8f) / 0.4f, 0f, 1f);
    joints[4 * v] = 0;
    joints[4 * v + 1] = 1;
    weights[4 * v] = 1 - w1;
    weights[4 * v + 1] = w1; // the other two slots keep weight 0
}
float[] outX = new float[VERTICES];
float[] outY = new float[VERTICES];
float[] outZ = new float[VERTICES];
SkinningKernels.skinLbs4(skinning, joints, weights, inX, inY, inZ, outX, outY, outZ, VERTICES);
```

The other entry points:
- `SkinningKernels.skinLbs4SoA` takes the indices and weights as four separate arrays each.
- `SkinningKernels.skinDualQuat4` and `SkinningKernels.skinDualQuat4SoA` blend dual quaternions (`soa.DualQuatSoA`).
  This avoids the volume loss of linear blending at twisting joints, at a higher cost.
- `SkinningKernels.buildMatrixPalette12` (or `SkinningKernels.buildRigidMatrixPalette12`, which ignores joint
  scale) with `SkinningKernels.skinLbs4MatrixPalette` skins from a packed 3×4 matrix palette, the layout shaders
  use.

[Performance](../performance.md) explains which skinning path to choose.

## Accuracy, allocation and threading

- The `animation` and `ik` kernels store floats but evaluate in double. They do not allocate after warm-up. Several
  keep per-thread scratch, allocated on their first call in each thread (`Fabrikf` also grows it once for chains
  longer than 32 bones).
- The skinning kernels are scalar in both jars, and allocation-free.
- In STRICT mode, the quaternion operations with a unit-length precondition (`log`, `swingTwist`, `squad`, ...)
  throw for non-unit input instead of trusting it.

## See also

- [Curves, easing, sampling and noise](curves-sampling-noise.md): position and path curves, easing.
- [Scenes: transforms, bounds and culling](scene-and-culling.md): composing transform hierarchies in batch.
- [GPU data](gpu-data.md): packing palettes and transforms for upload.
- Sources: [`animation`](../../vectrix-core/src/main/java/org/dynamisengine/vectrix/animation/),
  [`ik`](../../vectrix-core/src/main/java/org/dynamisengine/vectrix/ik/),
  [`soa`](../../vectrix-core/src/main/java/org/dynamisengine/vectrix/soa/).
