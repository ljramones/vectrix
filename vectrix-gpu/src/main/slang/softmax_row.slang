// Softmax.softmax for one row, run by one group of 256 threads: y[i] = e^(x[i] - m) / Σ e^(x[j] - m), m = max x,
// in the Java's exact order. Java twin: org.dynamisengine.vectrix.nn.Softmax.
//
//   max:  Java Math.max over the row. Its result does not depend on the order, so a group reduction matches.
//   exp:  Softmax.expShifted with ReproducibleMath.exp.
//   sum:  Blasf.sum of the exponentials. Thread (block, lane) owns the elements of a 256-element block whose index
//         is lane mod 8, adding them in index order; the lanes combine as in Blasf and the blocks pairwise.
//   y /= sum, by the thread that wrote each element.
// Each thread reads and writes only its own elements after the max, so y may be x at the same offset.
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
