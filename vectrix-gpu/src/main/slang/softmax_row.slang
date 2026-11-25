// Softmax.softmax for one row, run by one group of 256 threads: y[i] = e^(x[i] - m) / Σ e^(x[j] - m), m = max x,
// in the Java's exact order. Java twin: org.dynamisengine.vectrix.nn.Softmax.
//
//   max:  Java Math.max over the row. Its result does not depend on the order, so a group reduction matches.
//   exp:  Softmax.expShifted with ReproducibleMath.exp.
//   sum:  Blasf.sum of the exponentials. Thread (block, lane) owns the elements of a 256-element block whose index
//         is lane mod 8, adding them in index order; the lanes combine as in Blasf and the blocks pairwise.
//   y /= sum, by the thread that wrote each element.
// Each thread reads and writes only its own elements after the max, so y may be x at the same offset.
//
// The module also holds the group reductions and row functions of the other row kernels (log-softmax, log-sum-exp,
// the softmax backward passes, cross-entropy, and the norms in norms_row.slang): see "Row reductions" below.
import common;

static const uint GROUP = 256;
static const uint BLOCK = 256;
static const uint MAX_BLOCKS = 1024;

// Group values a thread copies to a local are kept in variables that are not written again for the row: slangc
// (2026.1, SPIR-V target) may drop such a copy and reread the group variable after a barrier, which returned
// another thread's later write when the maximum was read from `shared`.
groupshared float shared[GROUP];
groupshared float blockSums[MAX_BLOCKS];
groupshared float rowMax;
groupshared float total;

// Softmax.expShifted for one element.
float expShifted(float xv, float m)
{
    float a = jmax(xv, m - 1e38f);
    float s = a - m;
    float bv = s - a;
    float av = s - bv;
    float err = (a - av) + (-bv - m);
    float e = reproducibleExp(s);
    return fma(e, err, e);
}

struct SharedBlocks : IBlockSource
{
    uint next;

    [mutating] float nextBlock()
    {
        float v = blockSums[next];
        next++;
        return v;
    }
};

void softmaxRow(RWStructuredBuffer<float> x, uint xBase, RWStructuredBuffer<float> y, uint yBase, uint n, uint t)
{
    float mx = asfloat(0xFF800000u);
    for (uint i = t; i < n; i += GROUP)
    {
        mx = jmax(mx, x[xBase + i]);
    }
    shared[t] = mx;
    GroupMemoryBarrierWithGroupSync();
    for (uint stride = GROUP / 2; stride > 0; stride >>= 1)
    {
        if (t < stride)
        {
            shared[t] = jmax(shared[t], shared[t + stride]);
        }
        GroupMemoryBarrierWithGroupSync();
    }
    if (t == 0)
    {
        rowMax = shared[0];
    }
    GroupMemoryBarrierWithGroupSync();
    float m = rowMax;
    if (m == asfloat(0xFF800000u))
    {
        // every element is -inf (fully masked): zeros, as Softmax.softmax (4.1). m is the same for the whole group.
        for (uint i = t; i < n; i += GROUP)
        {
            y[yBase + i] = 0.0f;
        }
        return;
    }

    uint blocks = (n + BLOCK - 1) / BLOCK;
    uint lane = t % 8;
    for (uint b0 = 0; b0 < blocks; b0 += GROUP / 8)
    {
        uint b = b0 + t / 8;
        float acc = 0.0f;
        if (b < blocks)
        {
            uint base = b * BLOCK;
            uint len = min(BLOCK, n - base);
            for (uint j = lane; j < len; j += 8)
            {
                float e = expShifted(x[xBase + base + j], m);
                y[yBase + base + j] = e;
                acc = acc + e;
            }
        }
        shared[t] = acc;
        GroupMemoryBarrierWithGroupSync();
        if (lane == 0 && b < blocks)
        {
            blockSums[b] = combineLanes(shared[t], shared[t + 1], shared[t + 2], shared[t + 3], shared[t + 4],
                    shared[t + 5], shared[t + 6], shared[t + 7]);
        }
        GroupMemoryBarrierWithGroupSync();
    }
    if (t == 0)
    {
        SharedBlocks source;
        source.next = 0;
        total = pairwiseBlocks(source, blocks);
    }
    GroupMemoryBarrierWithGroupSync();
    float s = total;

    for (uint b0 = 0; b0 < blocks; b0 += GROUP / 8)
    {
        uint b = b0 + t / 8;
        if (b < blocks)
        {
            uint base = b * BLOCK;
            uint len = min(BLOCK, n - base);
            for (uint j = lane; j < len; j += 8)
            {
                y[yBase + base + j] = y[yBase + base + j] / s;
            }
        }
    }
}

// ---- Row reductions shared by the other row kernels (log-softmax, backward passes, loss, norms) ----
//
// A row is reduced by one group of 256 threads. groupSum walks the row in Blasf's order: thread (block, lane) adds
// the terms of a 256-element block whose index is lane mod 8, in index order; the lanes combine as in Blasf and the
// blocks pairwise. A term source decides what each element contributes and how (acc + v for Blasf.sum, fma(a, b,
// acc) for Blasf.dot). Each reduction's result goes to its own rowResults slot, written once per row, so a local
// copy of it is never invalidated (see the note on the group variables above).

// One element's contribution to a lane accumulator, as the ported Blasf kernel adds it.
interface IRowSum
{
    float accumulate(float acc, uint j);
}

static const uint ROW_RESULTS = 4;

groupshared float rowResults[ROW_RESULTS];

// The Blasf-ordered reduction of the n terms of f (0 for n = 0), returned to every thread of the group. Every thread
// must call it with the same n and slot; each slot may be used once per row.
float groupSum<T : IRowSum>(T f, uint n, uint t, uint slot)
{
    uint blocks = (n + BLOCK - 1) / BLOCK;
    uint lane = t % 8;
    for (uint b0 = 0; b0 < blocks; b0 += GROUP / 8)
    {
        uint b = b0 + t / 8;
        float acc = 0.0f;
        if (b < blocks)
        {
            uint base = b * BLOCK;
            uint len = min(BLOCK, n - base);
            for (uint j = lane; j < len; j += 8)
            {
                acc = f.accumulate(acc, base + j);
            }
        }
        shared[t] = acc;
        GroupMemoryBarrierWithGroupSync();
        if (lane == 0 && b < blocks)
        {
            blockSums[b] = combineLanes(shared[t], shared[t + 1], shared[t + 2], shared[t + 3], shared[t + 4],
                    shared[t + 5], shared[t + 6], shared[t + 7]);
        }
        GroupMemoryBarrierWithGroupSync();
    }
    if (t == 0)
    {
        float v = 0.0f;
        if (blocks > 0)
        {
            SharedBlocks source;
            source.next = 0;
            v = pairwiseBlocks(source, blocks);
        }
        rowResults[slot] = v;
    }
    GroupMemoryBarrierWithGroupSync();
    return rowResults[slot];
}

// Softmax.max: Java Math.max over the row from -inf (NaN if any element is NaN; exact, so any order matches).
// Returned to every thread; use once per row (it shares rowMax with softmaxRow).
float groupRowMax(RWStructuredBuffer<float> x, uint xBase, uint n, uint t)
{
    float mx = asfloat(0xFF800000u);
    for (uint i = t; i < n; i += GROUP)
    {
        mx = jmax(mx, x[xBase + i]);
    }
    shared[t] = mx;
    GroupMemoryBarrierWithGroupSync();
    for (uint stride = GROUP / 2; stride > 0; stride >>= 1)
    {
        if (t < stride)
        {
            shared[t] = jmax(shared[t], shared[t + stride]);
        }
        GroupMemoryBarrierWithGroupSync();
    }
    if (t == 0)
    {
        rowMax = shared[0];
    }
    GroupMemoryBarrierWithGroupSync();
    return rowMax;
}

// Blasf.sum terms: acc + x[j].
struct RowSum : IRowSum
{
    RWStructuredBuffer<float> x;
    uint xBase;

    float accumulate(float acc, uint j)
    {
        return acc + x[xBase + j];
    }
};

// Blasf.dot terms: fma(a[j], b[j], acc).
struct RowDot : IRowSum
{
    RWStructuredBuffer<float> a;
    uint aBase;
    RWStructuredBuffer<float> b;
    uint bBase;

    float accumulate(float acc, uint j)
    {
        return fma(a[aBase + j], b[bBase + j], acc);
    }
};

// Blasf.sum of Softmax.expShifted(x[j], m): the sum Softmax forms in scratch.
struct ExpShiftedSum : IRowSum
{
    RWStructuredBuffer<float> x;
    uint xBase;
    float m;

    float accumulate(float acc, uint j)
    {
        return acc + expShifted(x[xBase + j], m);
    }
};

// Softmax.logSumExp of a row (-inf for n = 0): m + ReproducibleMath.log(Σ expShifted(x, m)), or m itself when the
// maximum is infinite. Every thread gets the result; uses rowMax and rowResults[0].
float logSumExpRow(RWStructuredBuffer<float> x, uint xBase, uint n, uint t)
{
    float m = groupRowMax(x, xBase, n, t);
    ExpShiftedSum f;
    f.x = x;
    f.xBase = xBase;
    f.m = m;
    float s = groupSum(f, n, t, 0);
    if (m == asfloat(0xFF800000u) || m == asfloat(0x7F800000u))
    {
        return m;
    }
    return m + reproducibleLog(s);
}

// Softmax.logSoftmax for one row: y[i] = (x[i] - m) - log Σ expShifted(x, m). The thread that reads x[i] in the last
// pass writes y[i], after every read of the reductions, so y may be x at the same offset.
void logSoftmaxRow(RWStructuredBuffer<float> x, uint xBase, RWStructuredBuffer<float> y, uint yBase, uint n, uint t)
{
    float m = groupRowMax(x, xBase, n, t);
    if (m == asfloat(0xFF800000u))
    {
        // every element is -inf (fully masked): -inf, as Softmax.logSoftmax (4.1)
        for (uint i = t; i < n; i += GROUP)
        {
            y[yBase + i] = m;
        }
        return;
    }
    ExpShiftedSum f;
    f.x = x;
    f.xBase = xBase;
    f.m = m;
    float lse = reproducibleLog(groupSum(f, n, t, 0));
    for (uint i = t; i < n; i += GROUP)
    {
        y[yBase + i] = (x[xBase + i] - m) - lse;
    }
}

// Softmax.softmaxBackward for one row: dx[i] = y[i]·(dy[i] - Blasf.dot(dy, y)). dx may be y or dy at the same
// offset: each element is read and written by one thread, after the reduction.
void softmaxBackwardRow(RWStructuredBuffer<float> y, uint yBase, RWStructuredBuffer<float> dy, uint dyBase,
        RWStructuredBuffer<float> dx, uint dxBase, uint n, uint t)
{
    RowDot f;
    f.a = dy;
    f.aBase = dyBase;
    f.b = y;
    f.bBase = yBase;
    float d = groupSum(f, n, t, 0);
    for (uint i = t; i < n; i += GROUP)
    {
        dx[dxBase + i] = y[yBase + i] * (dy[dyBase + i] - d);
    }
}

// Softmax.logSoftmaxBackward for one row: dx[i] = fma(-ReproducibleMath.exp(y[i]), Blasf.sum(dy), dy[i]). dx may
// be y or dy at the same offset.
void logSoftmaxBackwardRow(RWStructuredBuffer<float> y, uint yBase, RWStructuredBuffer<float> dy, uint dyBase,
        RWStructuredBuffer<float> dx, uint dxBase, uint n, uint t)
{
    RowSum f;
    f.x = dy;
    f.xBase = dyBase;
    float s = groupSum(f, n, t, 0);
    for (uint i = t; i < n; i += GROUP)
    {
        dx[dxBase + i] = fma(-reproducibleExp(y[yBase + i]), s, dy[dyBase + i]);
    }
}

// Loss.crossEntropyBackward for one row: dx = (softmax(x) - onehot(target))·dLoss, each element rounded as the Java
// rounds it (the probability, then - 1 at the target, then · dLoss). The exponentials are recomputed in the last
// pass (the same bits), so dx may be x at the same offset. A target outside [0, n) makes the row NaN.
void crossEntropyBackwardRow(RWStructuredBuffer<float> x, uint xBase, int target, float dLoss,
        RWStructuredBuffer<float> dx, uint dxBase, uint n, uint t)
{
    float m = groupRowMax(x, xBase, n, t);
    // logits all -inf (fully masked): Softmax.softmax gives zeros (4.1), so v is 0 before the one-hot
    bool masked = m == asfloat(0xFF800000u);
    float s = 1.0f;
    if (!masked)
    {
        ExpShiftedSum f;
        f.x = x;
        f.xBase = xBase;
        f.m = m;
        s = groupSum(f, n, t, 0);
    }
    bool valid = target >= 0 && uint(target) < n;
    for (uint i = t; i < n; i += GROUP)
    {
        float v = masked ? 0.0f : expShifted(x[xBase + i], m) / s;
        if (i == uint(target))
        {
            v = v - 1.0f;
        }
        dx[dxBase + i] = valid ? v * dLoss : asfloat(0x7FC00000u);
    }
}
