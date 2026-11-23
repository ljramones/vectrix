# Machine-learning kernels

This guide shows how to build the forward and backward passes of a neural network from Vectrix kernels: vector and
matrix products (`blas`), activations, softmax, normalization, loss, optimizers, attention, rotary embeddings,
convolution, pooling, token sampling and low-precision formats (`nn`), and reproducible transcendentals and random
numbers (`core.ReproducibleMath`, `hash.Philox`).

Vectrix is a kernel library, not a framework. There are no tensors, layers, models or training loops, and no
automatic differentiation. You keep parameters, activations and gradients in your own `float[]` arrays, call the
forward kernels, and write the backward pass yourself by calling the matching backward kernels in reverse order. In
return, every kernel gives the same bits on every platform, and the kernels do not allocate.

## Which class for which job

| Task | Class | Notes |
|---|---|---|
| Dot product, sums, norm, `y += a·x`, scaling | `Blasf`, `Blasd` | Arrays or `MemorySegment`s; fixed reduction order |
| Matrix × matrix, matrix × vector | `Gemmf`, `Gemmd` | Row-major with row strides; optional transposes; caller scratch |
| ReLU, sigmoid, tanh, SiLU, GELU, softplus, dropout | `Activations` | Element-wise, forward and backward |
| Softmax, log-softmax, log-sum-exp | `Softmax` | One row per call, forward and backward |
| Layer norm, RMS norm | `Norms` | One row per call; the forward returns the statistics the backward needs |
| Cross-entropy loss | `Loss` | One row of logits per call |
| AdamW, SGD with momentum, gradient clipping | `Optim` | Element-wise over a parameter range |
| Scaled dot-product attention, grouped-query attention | `Attention` | One query against cached keys and values |
| Rotary position embeddings | `Rope` | Immutable plan; angles in double |
| 2D convolution (also 1D, depthwise, grouped) | `Conv2d` | Immutable shape; im2col plus `Gemmf` |
| Max and average pooling | `Pool2d` | Immutable shape |
| Pick a token from logits | `Sampler` | Greedy, temperature, top-k, top-p |
| bfloat16, FP8, int8 block quantization | `Formats` | Conversions and a quantized dot product and GEMV |
| Half precision (fp16) | `gpu.Half` | `pack` and `unpack` |
| Reproducible `exp`, `log`, `tanh`, `erf`, `sin`, `cos` | `core.ReproducibleMath` | Same bits on every CPU, JVM and JIT tier |
| Reproducible random numbers (initialization, dropout, sampling) | `hash.Philox` | Counter-based: number `n` is a pure function of `(seed, n)` |

`nn` works in `float` only. `blas` has float (`f`) and double (`d`) twins with the same API; see
[conventions](../conventions.md#types-and-names).

## Data layout

- **Matrices** are row-major. Each matrix argument is an array, an offset and a row stride (leading dimension,
  `lda`, `ldb`, `ldc`): element `(i, j)` is at `off + i·ld + j`. The stride lets you address a block inside a larger
  matrix. This is the opposite of the column-major `Matrix4f`; see
  [conventions](../conventions.md#coordinates-angles-and-matrices).
- **Rows.** `Softmax`, `Norms`, `Loss` and `Sampler` work on one row (an offset and a length). Loop over the rows
  of a batch yourself. Element-wise kernels (`Activations`, `Optim`) take a range, so one call can cover a whole
  matrix or a whole parameter array.
- **Images** for `Conv2d` and `Pool2d` are one image in channel-major `C×H×W` layout. Convolution weights are
  `Cout × (Cin/groups) × kh × kw`.
- **Scratch.** Calls that need working memory take a scratch array and an offset. Allocate it once at the largest
  size you need and reuse it.
- **Parameters in one array.** Keep all parameters in one `float[]` and the gradients in a second array with the
  same layout. One `Optim` call then updates everything, and `Blasf.dot(g, 0, g, 0, n)` gives the squared gradient
  norm for clipping. The example below does this.

## Vector operations: Blasf and Blasd

| Method | Computes |
|---|---|
| `Blasf.dot(x, xOff, y, yOff, n)` | `Σ x[i]·y[i]` |
| `Blasf.sum(x, xOff, n)` | `Σ x[i]` |
| `Blasf.asum(x, xOff, n)` | `Σ \|x[i]\|` |
| `Blasf.nrm2(x, xOff, n)` | `sqrt(Σ x[i]²)` without overflow or underflow |
| `Blasf.axpy(a, x, xOff, y, yOff, n)` | `y[i] = fma(a, x[i], y[i])` |
| `Blasf.scal(a, x, xOff, n)` | `x[i] = a·x[i]` |
| `Blasf.iamax(x, xOff, n)` | Index of the first element of largest magnitude; `-1` when `n == 0`, NaN counts as largest |

Each method also has a `MemorySegment` overload with `long` offsets and counts, for data above 2³¹ elements or
memory-mapped weights. Segments hold native-order values; offsets count elements, not bytes. Arrays and segments
holding the same values give the same results.

The reductions (`dot`, `sum`, `asum`, `nrm2`) add in one fixed order: blocks of `Blasf.BLOCK` (256) elements, each
summed in 8 interleaved lanes combined in a fixed pattern, and the blocks summed pairwise. The error is at most about
`(32 + log2(n/256))·ε·Σ|terms|`, against `n·ε` for a plain loop.

## Matrix products: Gemmf and Gemmd

`Gemmf.gemm(transA, transB, m, n, k, alpha, a, aOff, lda, b, bOff, ldb, beta, c, cOff, ldc, scratch, scratchOff)`
computes `C = α·op(A)·op(B) + β·C`:

| Argument | Meaning |
|---|---|
| `transA`, `transB` | Use the transpose of `A` or `B` |
| `m`, `n`, `k` | `op(A)` is `m×k`, `op(B)` is `k×n`, `C` is `m×n` |
| `a`, `aOff`, `lda` | `A` as stored (`k×m` when transposed), with its row stride |
| `b`, `bOff`, `ldb` | `B` as stored (`n×k` when transposed), with its row stride |
| `c`, `cOff`, `ldc` | The output; must not overlap `A` or `B` |
| `beta` | When 0, `C` is not read (it may hold garbage or NaN); otherwise `C = fma(α, s, β·c)` |
| `scratch` | At least `Gemmf.scratchSize(transA, transB, m, n, k)` floats from `scratchOff`, overwritten |

The scratch size depends on the transposes and sizes, so compute the maximum over every call that shares a scratch
array, as the example does.

`Gemmf.gemv(transA, m, n, alpha, a, aOff, lda, x, xOff, beta, y, yOff, scratch, scratchOff)` computes
`y = α·op(A)·x + β·y`, where `op(A)` is `m×n`. It needs `n` floats of scratch only when `transA` is true; otherwise
pass `null`.

**Layout and speed.** The Vector API path is fastest when both operands are contiguous along `k`: `A` not transposed
and `B` transposed, as in `x·Wᵀ` with `W` stored one output per row. Other layouts are packed into the scratch first.
On an Apple M4 Max with the `simd` jar, one core reaches about 55 GFLOP/s in float and 26 in double; the plain jar
about 7.5.

**Threads.** `gemm` is single-threaded. To use several cores, give each thread a disjoint range of rows of `C` (pass
the matching offsets and `m`) and its own scratch. The result does not depend on how you split the rows.

## Forward pass

The example `TinyNetwork` trains a two-layer network that decides whether a point lies inside the unit circle.
Parameters live in one array (`W1`, `B1`, `W2`, `B2` are offsets into it). The forward pass is two matrix products,
a bias, a tanh and a cross-entropy loss:

<!-- snippet: TinyNetwork#forward -->
```java
/** {@code hidden = tanh(x·W1 + b1)}, {@code logits = hidden·W2 + b2}; returns the mean cross-entropy. */
static float forward(float[] params, float[] x, float[] hidden, float[] logits, int[] label, float[] scratch) {
    Gemmf.gemm(false, false, BATCH, HIDDEN, 2, 1f, x, 0, 2, params, W1, HIDDEN, 0f, hidden, 0, HIDDEN,
        scratch, 0);
    addBias(hidden, HIDDEN, params, B1);
    Activations.tanh(hidden, 0, hidden, 0, BATCH * HIDDEN);
    Gemmf.gemm(false, false, BATCH, 2, HIDDEN, 1f, hidden, 0, HIDDEN, params, W2, 2, 0f, logits, 0, 2,
        scratch, 0);
    addBias(logits, 2, params, B2);
    float sum = 0;
    for (int i = 0; i < BATCH; i++) {
        sum += Loss.crossEntropy(logits, 2 * i, 2, label[i], scratch, 0);
    }
    return sum / BATCH;
}
```

`addBias` is a plain loop in the example; Vectrix has no broadcast kernels.

**Activations.** Each forward kernel writes `y[i] = f(x[i])` over a range, and the output may be the input at the
same offset:

| Function | Forward | Backward | Backward reads |
|---|---|---|---|
| ReLU | `Activations.relu` | `Activations.reluBackward` | Input `x` |
| Sigmoid | `Activations.sigmoid` | `Activations.sigmoidBackward` | Output `y` |
| Tanh | `Activations.tanh` | `Activations.tanhBackward` | Output `y` |
| SiLU (swish) | `Activations.silu` | `Activations.siluBackward` | Input `x` |
| GELU, exact `x·Φ(x)` | `Activations.gelu` | `Activations.geluBackward` | Input `x` |
| GELU, tanh approximation | `Activations.geluTanh` | `Activations.geluTanhBackward` | Input `x` |
| Softplus | `Activations.softplus` | `Activations.softplusBackward` | Input `x` |

Sigmoid and tanh take their derivative from the forward output, which is cheaper and more accurate. Keep the output
(as the example does with `hidden`), or keep the input for the others.

**Softmax.** `Softmax.softmax`, `Softmax.logSoftmax` and `Softmax.logSumExp` work on one row and subtract the
maximum first, so no input overflows. `logSoftmax` and `logSumExp` need `n` floats of scratch. Backward:
`Softmax.softmaxBackward` and `Softmax.logSoftmaxBackward`, both from the forward output.

**Normalization.** `Norms.layerNorm` computes `y = (x - mean)/σ·γ + β` with `σ = sqrt(var + eps)`, and
`Norms.rmsNorm` computes `y = x/rms·γ`. Pass `null` for `gamma` or `beta` to use 1 or 0. The forward kernels write
the statistics the backward needs into `stats` (`mean` and `1/σ` for layer norm, `1/rms` for RMS norm), so store two
floats (or one) per row during training. `Norms.layerNormBackward` and `Norms.rmsNormBackward` take those statistics
and `2n` floats of scratch.

**Loss.** `Loss.crossEntropy(logits, off, n, target, scratch, scratchOff)` returns `-log softmax(x)[target]` for one
row, computed as `logSumExp(x) - x[target]` so no probability underflows. It needs `n` floats of scratch.

## Backward pass

You write the backward pass: walk the forward pass in reverse, and for each step compute the gradient of its inputs
(and parameters) from the gradient of its output. The example's backward pass, followed by an AdamW step:

<!-- snippet: TinyNetwork#backward -->
```java
// dLoss/dlogits for the mean loss over the batch.
for (int i = 0; i < BATCH; i++) {
    Loss.crossEntropyBackward(logits, 2 * i, 2, label[i], 1f / BATCH, dLogits, 2 * i);
}
// dW2 = hiddenᵀ · dLogits, db2 = column sums of dLogits
Gemmf.gemm(true, false, HIDDEN, 2, BATCH, 1f, hidden, 0, HIDDEN, dLogits, 0, 2, 0f, grads, W2, 2,
    scratch, 0);
columnSums(dLogits, 2, grads, B2);
// dHidden = dLogits · W2ᵀ, then back through tanh (its derivative from its output: 1 - y²)
Gemmf.gemm(false, true, BATCH, HIDDEN, 2, 1f, dLogits, 0, 2, params, W2, 2, 0f, dHidden, 0, HIDDEN,
    scratch, 0);
Activations.tanhBackward(hidden, 0, dHidden, 0, dHidden, 0, BATCH * HIDDEN);
// dW1 = xᵀ · dHidden, db1 = column sums of dHidden
Gemmf.gemm(true, false, 2, HIDDEN, BATCH, 1f, x, 0, 2, dHidden, 0, HIDDEN, 0f, grads, W1, HIDDEN,
    scratch, 0);
columnSums(dHidden, HIDDEN, grads, B1);

Optim.adamW(params, 0, grads, 0, adamM, 0, adamV, 0, PARAMS, 0.01f, 0.9f, 0.999f, 1e-8f, 0f, step);
```

The rules this follows:

- **Matrix products.** For `Y = X·W`, the gradients are `dW = Xᵀ·dY` and `dX = dY·Wᵀ`. Both are `gemm` calls with a
  transpose flag; nothing is copied. A bias gradient is the column sum of `dY`.
- **Loss.** `Loss.crossEntropyBackward(logits, off, n, target, dLoss, dx, dxOff)` writes
  `dLoss·(softmax(x) - onehot(target))`. Pass `dLoss = 1/batch` for the mean loss. `dx` may be `logits`.
- **Overwrite or accumulate.** Kernels that produce a gradient for something shared across rows or positions
  *accumulate* (`+=`) into it, so you must zero those arrays before the backward pass: the `dGamma` and `dBeta` of
  `Norms`, `dk` and `dV` of `Attention.backward` and `Attention.groupedQueryBackward`, and `dWeights` and `dBias` of
  `Conv2d.backward`. Other outputs are overwritten. `gemm` overwrites when `beta` is 0 and accumulates when `beta`
  is 1.
- **Reproducible accumulation.** An accumulated gradient is reproducible only if you add the rows in a fixed order.
  If threads process different rows, give each thread its own gradient buffer and add the buffers in a fixed order.

**Optimizers.**

- `Optim.adamW(p, pOff, g, gOff, m, mOff, v, vOff, n, lr, beta1, beta2, eps, weightDecay, step)` performs one AdamW
  step with decoupled weight decay. `m` and `v` are your moment arrays, zero at the start; `step` is the 1-based step
  number.
- `Optim.sgd(p, pOff, g, gOff, buf, bOff, n, lr, momentum, weightDecay, nesterov)` performs one SGD step with momentum
  (`buf` is the momentum buffer) and coupled weight decay.
- `Optim.clipFactor(norm, maxNorm)` returns `min(1, maxNorm / norm)`. Compute `norm` as the square root of the sum of
  `Blasf.dot(g, g)` over all gradient arrays (in a fixed order), then multiply each gradient by the factor with
  `Blasf.scal`.

Each parameter is updated independently, so you can split an optimizer step across threads by element ranges.

## Attention and rotary embeddings

`Attention.forward(q, qOff, d, k, kOff, ldk, v, vOff, ldv, dv, t, scale, mask, maskOff, p, pOff, out, outOff,
scratch, scratchOff)` attends one query of `d` floats to `t` keys (rows of `d` floats, stride `ldk`) and values
(rows of `dv` floats, stride `ldv`):

- `scale` is usually `1/√d`.
- `mask` holds `t` additive biases (`-∞` excludes a key), or `null`. For causal attention, pass only the keys up to
  the query's position (`t = position + 1`) instead of a mask.
- `p` receives the `t` attention probabilities. Keep them for `Attention.backward`, which writes `dq` and
  accumulates `dk` and `dV`.
- Scratch: `t` floats forward, `2t` backward.

`Attention.groupedQuery` handles all heads of one position: `heads` query heads share `kvHeads` key/value heads
(`kvHeads = 1` is multi-query attention, `kvHeads = heads` ordinary multi-head attention). `q` is `heads × d`, each
cache row holds every key/value head of one position, and `out` is `heads × dv`. Each head gives exactly the result of
`Attention.forward`. `Attention.groupedQueryBackward` is its backward pass.

`Rope.plan(dim, base, layout)` creates a rotary embedding for a head dimension `dim` (even) with the standard
frequencies `base^(-2i/dim)` (`base` is 10000 in most models). `Rope.plan(invFreq, layout)` takes your own `dim/2`
frequencies, for scaled variants such as NTK-aware or YaRN. The layout picks the feature pairs:

| `Rope.Layout` | Pairs | Used by |
|---|---|---|
| `INTERLEAVED` | `(x[2i], x[2i + 1])` | The original paper, GPT-J |
| `HALF` | `(x[i], x[i + dim/2])` | GPT-NeoX, LLaMA in Hugging Face form |

`apply(x, off, position)` rotates one head vector in place; `applyBackward(dy, off, position)` rotates the gradient
back (it is also the inverse rotation). Angles are formed in double and their sine and cosine come from
`ReproducibleMath`, so long positions keep their accuracy. A plan is immutable and can be shared across threads.

## Convolution and pooling

`Conv2d.of(cin, cout, h, w, kh, kw, stride, pad, dilation, groups)` describes a convolution of one `cin × h × w`
image; another overload takes separate vertical and horizontal stride, padding and dilation. Like every deep-learning
framework, it computes cross-correlation (the kernel is not flipped). `groups = cin = cout` is a depthwise
convolution, and `h = kh = 1` a 1D convolution. Ask the shape for `outHeight()`, `outWidth()`, `weightCount()` and
`scratchSize()`.

- `forward(in, inOff, weights, wOff, bias, bOff, out, outOff, scratch, scratchOff)` writes
  `cout × outHeight × outWidth` floats; `bias` may be `null`.
- `backward(in, inOff, weights, wOff, dOut, dOutOff, dIn, dInOff, dWeights, dwOff, dBias, dbOff, scratch,
  scratchOff)` accumulates `dWeights` and `dBias` and writes `dIn`. Pass `null` for any gradient you do not need.

`Pool2d.of(c, h, w, k, stride, pad)` describes `k×k` pooling windows (`pad` at most `k/2`; padding never wins a
maximum and is not counted in an average). `max` writes the outputs and, if you pass an `int[]`, the flat input index
of each window's maximum; `maxBackward` uses those indices to route the gradient. `average` and `averageBackward`
need no indices. Both backward kernels clear `dIn` first.

A batch is a loop over images. The shapes are immutable and can be shared across threads.

## Sampling tokens

`Sampler.argmax(logits, off, n)` is greedy decoding. `Sampler.sample(logits, off, n, temperature, topK, topP, u,
probs, probsOff, order, orderOff)` divides the logits by `temperature` (`<= 0` means greedy), keeps the `topK` most
probable tokens (`<= 0` keeps all) and then the smallest set whose probability reaches `topP` (`>= 1` keeps all),
and picks one with your uniform number `u` in [0, 1). It needs `n` floats and `n` ints of scratch. Draw `u` from
`hash.Philox.uniform(seed, tokenIndex)` and the generated text is reproducible.

## Low-precision formats

| Format | Encode | Decode | Notes |
|---|---|---|---|
| bfloat16 | `Formats.toBf16` | `Formats.fromBf16` | Round to nearest even; NaN stays NaN |
| FP8 E4M3 (OCP "FN") | `Formats.toE4M3` | `Formats.fromE4M3` | Finite only, max 448; overflow gives NaN, or 448 when `saturate` is true |
| FP8 E5M2 | `Formats.toE5M2` | `Formats.fromE5M2` | IEEE-like, max 57344, has infinities; overflow gives infinity, or 57344 when saturating |
| int8 blocks | `Formats.quantizeInt8` | `Formats.dequantizeInt8` | One scale per block of `Formats.QBLOCK` (32) values: `max\|x\| / 127` |
| fp16 | `gpu.Half.pack` | `gpu.Half.unpack` | IEEE half precision |

All `Formats` conversions round to nearest, ties to even, and decoding is exact. For int8, `n` must be a multiple of
32; `Formats.dotInt8` and `Formats.gemvInt8` multiply quantized weights with float activations without
dequantizing. Int8 saves memory (a quarter of float), not time: on an Apple M4 Max with the Vector API a 4096×4096
`gemvInt8` takes 1.8 ms against 1.05 ms for the float `Gemmf.gemv`. Use it when the float weights would not fit in
memory or cache.

## Reproducible random numbers: hash.Philox

`hash.Philox` is the Philox4x32-10 counter-based generator. Random number `index` of stream `seed` is a pure function
of the two, so there is no generator state: any thread can draw any element in any order and get the same value.

| Method | Gives |
|---|---|
| `hash.Philox.uniform(seed, index)` | A float in [0, 1) with 24 random bits |
| `hash.Philox.fillUniform(seed, start, dest, off, n)` | `dest[off + i] = uniform(seed, start + i)` |
| `hash.Philox.fillNormal(seed, start, mean, std, dest, off, n)` | Normally distributed floats (Box–Muller, computed in double with `ReproducibleMath`) |
| `hash.Philox.word(seed, index)` | A raw 32-bit word |
| `hash.Philox.block(c0, c1, c2, c3, k0, k1, out, off)` | The raw four-word block for a 128-bit counter and 64-bit key |

Use it for weight initialization (`fillNormal` with a standard deviation such as `sqrt(1/fanIn)`, as the example
does), for data sampling, and for dropout. Give every use its own range of indices, for example by deriving `start`
from the step, layer and element, so that no two uses draw the same numbers.

`Activations.dropout(seed, start, p, x, xOff, y, yOff, n)` is inverted dropout: it keeps element `i` when
`hash.Philox.uniform(seed, start + i) >= p` and scales it by `1/(1 - p)`, and writes 0 otherwise. The backward pass
is the same call, with the same `seed` and `start`, on the upstream gradient. You never store the mask.

## Reproducible transcendentals: ReproducibleMath

`java.lang.Math.exp` and its relatives may differ in the last bit between platforms and JIT tiers, and `StrictMath`
is reproducible but slow. `core.ReproducibleMath` gives the same bits on every CPU, JVM and JIT tier and is still
fast. The `nn` kernels use it (the Vector API paths expand the float versions inline with the same operations). Use
it in your own code wherever results must match across machines, for example in a loss you compute yourself.

| Precision | Functions | Error (measured, as documented per method) |
|---|---|---|
| float | `exp`, `expm1`, `tanh`, `sigmoid`, `log`, `log1p` | 1.1 ulp (`exp`) to 3 ulp (`log1p`) |
| float | `erf`, `erfc`, `normalCdf`, `normalPdf` | Within 2.5 ulp; `normalPdf` is computed in double and rounded once |
| double | `exp`, `log`, `pow`, `sin`, `cos` | `exp` 1 ulp, `log` 2 ulp; `sin` and `cos` within 1 ulp of 1 (absolute); `pow` loses about `\|y·log b\|` ulps |

The float and double versions are overloads, so the argument type selects the precision. Java widens an `int` or
`long` argument to `float`, so write `ReproducibleMath.log((double) n)` when you want the double result.
`ReproducibleMath.exp(double)` is for `|x| <= 700`.

## What is reproducible

The guarantee, as the package documentation states it:

- **Same bits on every platform and jar.** Every `blas` and `nn` kernel gives the same bits on every platform and with
  or without the Vector API (the plain and the `simd` jar). Every reduction has one documented evaluation order,
  which the scalar and the Vector API paths both follow with fused multiply-adds.
- **Batch invariance.** Each element of a matrix product is defined as the `Blasf.dot` of a row and a column in that
  order. So a matrix product, a matrix-vector product and a dot product of the same data agree bit for bit, and a
  row's result does not depend on the batch size, the tiling or the thread split. `Attention` and `Conv2d` are
  built on the same products and inherit this.
- **Thread splits.** Splitting `gemm` by row ranges of `C`, or an `Optim` step or element-wise kernel by element
  ranges, gives the same result. A single long reduction (`Blasf.sum` of a huge vector) is reproduced across threads
  only if you split it at the pairwise midpoints (whole blocks of 256, the first half rounded up) and add the two
  halves.

What stays your responsibility:

- Sums you write yourself (bias gradients, the mean loss, gradient norms over several arrays) must run in a fixed
  order.
- Gradients that kernels accumulate across rows must be accumulated in a fixed order (see the backward pass).
- Transcendentals in your own code must come from `ReproducibleMath`, not `java.lang.Math`.
- Random numbers must come from `hash.Philox` with indices that do not depend on the thread schedule.

Plain Java `float` and `double` arithmetic (`+`, `-`, `*`, `/`, `sqrt`, `fma`) is correctly rounded and gives the
same bits everywhere, so the rest of your code needs no special care. See [conventions](../conventions.md#determinism).

## Accuracy, allocation and threading

- **Accuracy.** Reductions have an error of about `(32 + log2(n/256))·ε·Σ|terms|`, and each `gemm` element has the
  error of its dot product. `Formats.dotInt8` has an error of about `(7 + n/32)·ε·Σ|terms|`. `Norms` computes the
  variance from centerd values (two passes), so it is never negative. `Activations.tanhBackward` uses
  `(1 - y)(1 + y)`, which keeps its relative accuracy as `|y|` approaches 1.
- **Allocation.** No kernel allocates. `Rope.plan`, `Conv2d.of` and `Pool2d.of` allocate once when you create them.
- **Threading.** Kernels are static and single-threaded and keep no state; `Rope`, `Conv2d` and `Pool2d` are
  immutable. Call them from any number of threads, giving each thread its own scratch and its own (or disjoint)
  output ranges. Scheduling the work across threads is up to you.
- **SIMD.** The `simd` jar runs Vector API paths for `blas` and for many `nn` kernels. They change the speed, not
  the results; see [conventions](../conventions.md#simd).

## See also

- [Conventions](../conventions.md): row-major matrices, scratch arrays, determinism, threading, the SIMD jar.
- [State estimation](state-estimation.md): Kalman and particle filters, which use `hash.Philox` and
  `ReproducibleMath` the same way.
- Example: [TinyNetwork](../../examples/src/main/java/org/dynamisengine/vectrix/examples/TinyNetwork.java).
- Source: [blas](../../src/main/java/org/dynamisengine/vectrix/blas/),
  [nn](../../src/main/java/org/dynamisengine/vectrix/nn/),
  [core.ReproducibleMath](../../src/main/java/org/dynamisengine/vectrix/core/ReproducibleMath.java),
  [hash.Philox](../../src/main/java/org/dynamisengine/vectrix/hash/Philox.java).
