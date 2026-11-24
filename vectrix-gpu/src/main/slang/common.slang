// Shared helpers for the vectrix-gpu kernels: ports of vectrix CPU code that must round exactly as the Java does.
// Every function mirrors a named Java method; change both together. The build (scripts/build-kernels.py) keeps
// each add and subtract from being fused with a product, so `a * b + c` rounds twice, as in Java.

// Java Math.max: NaN if either argument is NaN; +0 is greater than -0.
float jmax(float a, float b)
{
    if (a != a) return a;
    if (a == 0.0f && b == 0.0f && asuint(a) == 0x80000000u) return b;
    return a >= b ? a : b;
}

// Java Math.min: NaN if either argument is NaN; -0 is less than +0.
float jmin(float a, float b)
{
    if (a != a) return a;
    if (a == 0.0f && b == 0.0f && asuint(b) == 0x80000000u) return b;
    return a <= b ? a : b;
}

// ReproducibleMath.exp(float).
float reproducibleExp(float x)
{
    const float LOG2E = 1.442695040888963f;
    const float LN2_HI = 0.693359375f;
    const float LN2_LO = -2.12194440e-4f;
    const float ROUND = 12582912.0f;
    float c = jmin(jmax(x, -104.0f), 89.0f);
    float kf = (c * LOG2E + ROUND) - ROUND;
    float r = fma(kf, -LN2_HI, c);
    r = fma(kf, -LN2_LO, r);
    float p = fma(fma(fma(fma(fma(1.984127e-4f, r, 0.0013888889f), r, 0.008333334f), r, 0.041666668f), r,
            0.16666667f), r, 0.5f);
    float t = fma(p, r * r, r) + 1.0f;
    int k = int(kf);
    int k1 = k >> 1;
    return t * asfloat(uint((k1 + 127) << 23)) * asfloat(uint((k - k1 + 127) << 23));
}

// The fixed combine of the 8 interleaved lanes of a reduction block (Blasf's class comment).
float combineLanes(float a0, float a1, float a2, float a3, float a4, float a5, float a6, float a7)
{
    return ((a0 + a4) + (a2 + a6)) + ((a1 + a5) + (a3 + a7));
}

// Supplies the values of consecutive reduction blocks, left to right.
interface IBlockSource
{
    [mutating] float nextBlock();
}

// Frames for the pairwise tree: 2^24 blocks need 25.
static const int TREE_DEPTH = 26;

// The pairwise sum of `blocks` (>= 1) block values in Blasf's order: a range of more than one block splits after
// its first ceil(blocks / 2) blocks, and the halves are added left + right. The tree is walked in post-order with
// an explicit stack, so the blocks are requested left to right, each once. Control flow depends only on `blocks`,
// so a source may use group barriers when every thread of the group passes the same count.
float pairwiseBlocks<S : IBlockSource>(inout S source, uint blocks)
{
    uint count[TREE_DEPTH];
    uint state[TREE_DEPTH];
    float left[TREE_DEPTH];
    int sp = 1;
    count[0] = blocks;
    state[0] = 0;
    left[0] = 0.0f;
    float ret = 0.0f;
    while (sp > 0)
    {
        int top = sp - 1;
        if (count[top] == 1)
        {
            ret = source.nextBlock();
            sp--;
        }
        else if (state[top] == 0)
        {
            state[top] = 1;
            count[sp] = (count[top] + 1) / 2;
            state[sp] = 0;
            sp++;
        }
        else if (state[top] == 1)
        {
            left[top] = ret;
            state[top] = 2;
            count[sp] = count[top] / 2;
            state[sp] = 0;
            sp++;
        }
        else
        {
            ret = left[top] + ret;
            sp--;
        }
    }
    return ret;
}
