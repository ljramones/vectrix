# Vectrix Performance Plan

These are actually **promising early numbers**, but they also already show you **where to focus next**. The big takeaway is:

> **Vectrix is already fast at the micro-op level; the next wins are going to come from batching, memory layout, and benchmark rigor more than from shaving another 0.1 ns off single vector ops.**

A few blunt observations first.

## 1. Your scalar/core math numbers are already in the "good enough to build on" zone

These stand out:

* `vector3f_cross` ~ **0.98 ns/op**
* `vector4f_dot` ~ **0.99 ns/op**
* `vector3f_normalize` ~ **1.03 ns/op**
* `vector4f_transform` ~ **2.24 ns/op**
* `matrix4f_mul` ~ **7.27 ns/op**
* `matrix4f_invertAffine` ~ **5.04 ns/op**

That says a few things:

* your hot scalar/JOML-style ops are already **very fast**
* FMA vs non-FMA is basically a wash in these benches
* you are likely near the point where **benchmark noise and CPU behavior matter as much as code changes**

So I would not spend much time trying to turn `0.99 ns/op` into `0.91 ns/op` yet. That is prestige work, not leverage.

The leverage is elsewhere.

---

## 2. Your biggest leverage is clearly batch throughput

These are more interesting than the tiny scalar ops:

* `mulBatch`

  * 64: **369 ns**
  * 256: **1466 ns**
  * 4096: **26923 ns**
* `transformBatch`

  * 64: **108 ns**
  * 256: **433 ns**
  * 4096: **11593 ns**

And also:

* skinning
* AoS/SoA staging
* memory transfer backend paths

Because this is where real engine workloads live.

A renderer, animation system, culling pipeline, and transform pipeline do not care that a single dot product is 1 ns. They care about:

* **10k transforms**
* **8k skinned vertices**
* **thousands of uploads**
* **cache behavior**
* **layout conversion cost**
* **branchless bulk execution**

That is where your effort should go.

---

## 3. The SoA result is exactly the kind of thing you want to see

This is a very good sign:

* `stageAoSMatrixUpload`

  * 64: **111.8 ns**
  * 1024: **2261.1 ns**
  * 8192: **17506.0 ns**

vs

* `stageSoADiagonalUpload`

  * 64: **41.9 ns**
  * 1024: **639.1 ns**
  * 8192: **9223.6 ns**

So SoA-ish staging is materially better here, especially at smaller and medium sizes.

That tells me:

* your intuition about data-oriented layout is right
* layout and upload format may matter more than raw arithmetic
* you should benchmark **more layout variants**, not fewer

I would expand this area aggressively.

Test:

* AoS vs SoA vs AoSoA
* packed 16-float mat4 vs affine 12-float vs quat+translation+scale
* aligned vs unaligned buffers
* interleaved vs planar upload staging
* direct write-to-target layout vs convert-then-copy

A lot of engine performance lives in exactly this zone.

---

## 4. Your memory backend numbers are very actionable

These are especially useful:

### FFM/NIO path

* 1: ~**3.2 ns**
* 16: ~**45-47 ns**
* 256: ~**723-755 ns**

### Unsafe path

* 1: ~**1.8 ns**
* 16: ~**27-28 ns**
* 256: ~**418-455 ns**

That is a pretty clear spread. Unsafe is substantially faster in these benches.

That does **not** automatically mean "use Unsafe everywhere." It means:

* Unsafe is your **latency king**
* FFM/NIO may still be viable where safety, portability, or API cleanliness matter
* you now need **size-threshold policy**

This is where a mature engine usually lands:

* **tiny hot copies** -> specialized path
* **medium bulk copies** -> optimized memory backend
* **large transfers** -> bulk native copy / staging policy
* API selects backend based on size and memory kind

I would codify that into a strategy layer rather than a one-size-fits-all backend.

For example:

* `< 64 bytes` -> inline/specialized fast path
* `64 B - 4 KB` -> Unsafe optimized bulk path
* larger or foreign memory controlled cases -> FFM/NIO/native memcpy path

Not exact thresholds yet, but that is the kind of policy your benchmarks should drive.

---

## 5. Some of your benchmark variance is too noisy to trust fully yet

A few entries have very large error bars:

* `mulBatch` 4096: **26923 +/- 14120 ns**
* `lbsLikeMatrixSkinning` 8192: **16538 +/- 8578 ns**
* `stageAoSMatrixUpload` 8192: fairly wide
* several larger batch cases are noisier than they should be

That means the immediate next task is not code optimization. It is **benchmark hardening**.

Right now you only have:

* `Threads = 1`
* `Samples = 3`

That is nowhere near enough for serious performance decisions.

For anything you want to use as a gating benchmark, I would move toward something more like:

* `@Fork(3)` minimum
* `@Warmup(iterations = 5 to 10)`
* `@Measurement(iterations = 8 to 15)`
* enough runtime per iteration to reduce noise
* pin benchmarks to performance mode if possible
* run on thermally stable machine state

The current data is good for **directional guidance**, but not yet for fine-grained optimization claims.

---

## 6. What I would optimize next, in order

### First priority: benchmark quality

Before tuning code, improve the signal.

Do this first:

* increase warmup and measurement
* use multiple forks
* capture gc profiler output
* capture perfasm for selected hot benches
* separate "latency microbench" from "throughput batch bench"

If you do not do this, you risk optimizing noise.

---

### Second priority: representation choices

Your biggest wins are likely here.

Especially for transforms and skinning, benchmark these representations:

* full `mat4`
* affine matrix
* dual quaternion
* quaternion + translation
* quaternion + translation + uniform scale
* quaternion + translation + non-uniform scale

Not because one is philosophically cleaner, but because different tasks want different forms.

For example:

* scene transforms may prefer affine
* animation may prefer quat+translation
* upload path may prefer packed affine
* culling may prefer decomposed forms

You want hard data for each workload.

---

### Third priority: skinning path

These are useful early numbers:

* `lbsLikeMatrixSkinning`
* `quaternionTransformBlend`

At 8192 vertices they are pretty close, with overlapping noise. That says you need deeper investigation before deciding architecture from this.

What I would benchmark next for skinning:

* 1, 2, 4 bone influences
* normalized vs pre-normalized weights
* matrix palette fetch patterns
* SoA bone data vs AoS bone data
* scalar vs Vector API implementation
* CPU skinning vs "CPU prep only" path

Skinning can become a black hole, so benchmark the forms that matter to your engine roadmap.

---

### Fourth priority: staging/upload pipeline

This matters for rendering.

You already have signs that layout affects upload cost. Extend this to real engine-relevant flows:

* transform palette upload
* instance matrix upload
* skinned vertex staging
* dynamic uniform buffer updates
* ring-buffered staging writes
* persistent mapped memory style workflows

This becomes directly valuable for DynamisLightEngine later.

---

## 7. Performance guidelines I would recommend for Vectrix

Here is the kind of rule set I would use.

## A. Separate benchmark classes by purpose

You need at least four categories:

**1. Primitive latency**

* dot
* cross
* normalize
* single matrix multiply

**2. Batch throughput**

* transform N vectors
* multiply N matrices
* skin N vertices

**3. Layout/staging**

* AoS/SoA conversion
* upload staging
* interop writes

**4. End-to-end kernels**

* culling prep
* animation pose application
* instance transform update
* frustum transform batch

The last category matters most for engine decisions.

---

## B. Treat single-op benches as regression guards, not main goals

Once a vector op is already around 1 ns, treat it as:

* "do not regress"
* not "must improve every week"

That keeps effort focused.

---

## C. Set explicit performance budgets

For example:

* `vector dot/cross/normalize`: must not regress by more than X%
* `matrix4 mul`: target under Y ns/op
* `transformBatch(4096)`: under Z ns total
* `upload staging(8192)`: under N ns total
* zero allocations in hot kernels

That turns benchmarks into a contract.

---

## D. Track scaling efficiency, not just raw ns/op

For example, calculate per-item cost:

### `mulBatch`

* 64 -> 369.7 ns = **5.78 ns/item**
* 256 -> 1466.0 ns = **5.73 ns/item**
* 4096 -> 26923.3 ns = **6.57 ns/item**

That is actually pretty decent scaling, though the high variance makes the 4096 case uncertain.

### `transformBatch`

* 64 -> 108.2 ns = **1.69 ns/item**
* 256 -> 432.5 ns = **1.69 ns/item**
* 4096 -> 11592.7 ns = **2.83 ns/item**

That suggests scaling degradation at large size, maybe from cache or memory effects.

This kind of per-item view is far more useful than raw totals.

---

## E. Benchmark allocation and GC explicitly

Even if ns/op is good, a hidden allocation can kill real workloads.

Use JMH profilers and make these visible:

* `gc.alloc.rate`
* `gc.alloc.rate.norm`
* branch/profile data where possible

For engine hot paths, many benches should aim for:

* **0 B/op**

---

## F. Benchmark with realistic data distributions

Do not only test clean happy-path values.

Include:

* identity-heavy matrices
* affine-only matrices
* random transforms
* denormal/unpleasant float ranges where relevant
* realistic skeleton sizes
* clustered vs scattered memory access

Engines rarely run on uniformly random ideal data.

---

## 8. The concrete numbers that caught my eye

A few especially interesting interpretations:

### `transformBatch` is strong

Very good small and medium numbers. This is promising for transform-heavy scene work.

### `mulBatch` has good apparent linearity

Good sign, but high-end error bars need cleanup.

### Unsafe path is clearly faster

Worth preserving as a first-class backend for hot internal paths.

### FMA doesn't matter much here

So I would not overinvest in FMA-specific branches unless a larger kernel proves a benefit.

### Skinning needs more rigorous investigation

The two approaches are not cleanly differentiated yet because the noise is too high.

---

## 9. What to add next to the suite

If I were guiding Vectrix, I would add these next:

* `frustumCullBatch`
* `transformAabbBatch`
* `matrixPaletteGather`
* `quatToMatrixBatch`
* `composeTransformBatch`
* `decomposeTransformBatch`
* `instanceUploadPackedAffine`
* `instanceUploadQuatTranslation`
* `skin4InfluenceBatch`
* `soaToInterleavedVertexWrite`
* `cacheColdTransformBatch`
* `parallelTransformBatch`

Especially `transformAabbBatch` and `instanceUploadPackedAffine` because those are real engine kernels.

---

## 10. My honest verdict

For where you are right now, these results are **good**.

Not "finished," not "marketing-ready," but good in the way that matters:

* the scalar math floor is strong
* batching looks viable
* layout choices are already proving meaningful
* the memory backend differences are actionable
* the next work is clear

So my advice is:

> **stop treating Vectrix as a math library and start treating it as a workload-kernel library.**

That means optimize for:

* transforms
* culling prep
* skinning
* staging
* upload
* bulk math kernels

That is where the engine will win or lose.

If you want, I can turn this into a **formal Vectrix performance strategy memo** with:

* benchmark categories
* target budgets
* acceptance gates
* backend policy rules
* and a prioritized optimization roadmap for the next 2 weeks.
