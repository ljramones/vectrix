// Helpers of the Blasf kernels (blas_*.slang). Java twin: org.dynamisengine.vectrix.blas.Blasf.
//
// The reductions (dot, sum, asum, nrm2) run in passes over Blasf's pairwise tree of 256-element blocks:
//   blas_partial: one group per subtree at depth `depth` (at most LEAF_BLOCKS blocks), as Blasf.*Pairwise of it;
//   blas_combine: the levels above, FAN_DEPTH at a time, adding left + right as Blasf does.
// A range of c > 1 blocks splits after its first ceil(c / 2) blocks (Blasf's midpoints); a node of one block is a
// leaf, and its value is stored at its leftmost descendant on the level below it.
import common;

static const uint BLAS_BLOCK = 256;

// Blocks per blas_partial group: 8 threads per block in a group of 256.
static const uint LEAF_BLOCKS = 32;

// The number of blocks of node `idx` at depth `depth` of the tree over `blocks` blocks (0 below a one-block leaf).
uint nodeBlocks(uint blocks, uint depth, uint idx)
{
    uint c = blocks;
    for (uint d = 0; d < depth; d++)
    {
        uint left = (c + 1) / 2;
        c = ((idx >> (depth - 1 - d)) & 1u) != 0 ? c - left : left;
    }
    return c;
}

// The first block of node `idx` at depth `depth` of the tree over `blocks` blocks.
uint nodeStart(uint blocks, uint depth, uint idx)
{
    uint c = blocks;
    uint start = 0;
    for (uint d = 0; d < depth; d++)
    {
        uint left = (c + 1) / 2;
        if (((idx >> (depth - 1 - d)) & 1u) != 0)
        {
            start += left;
            c -= left;
        }
        else
        {
            c = left;
        }
    }
    return start;
}

// 2^j as a float, for j in [-126, 127].
float pow2(int j)
{
    return asfloat(uint(j + 127) << 23);
}

// x·2^k for k in [-127, 127] with the bits of Java's x * Math.scalb(1f, k) (one rounding), given x's bits. It
// stays exact where 2^k is subnormal (k = -127: two exact normal steps) and where x is subnormal and the product is
// normal (x = ±M·2^-149 scaled from the integer M), so a GPU that flushes subnormals still gets the Java product
// whenever that product is normal.
float scaleBy(uint bits, int k)
{
    float x = asfloat(bits);
    if ((bits & 0x7F800000u) == 0u && k > 0)
    {
        float mag = float(bits & 0x007FFFFFu); // M, exact
        float r;
        if (k >= 23)
        {
            r = mag * pow2(k - 149);
        }
        else
        {
            // M·2^k and M·2^(k-126) are exact and normal; the last step is exact or a subnormal result
            r = ((mag * pow2(k)) * pow2(-126)) * pow2(-23);
        }
        return jcopySign(r, x);
    }
    if (k >= -126)
    {
        return x * pow2(k);
    }
    // x·2^-64 is exact unless it is below 2^-126, and then the whole product rounds to zero either way
    return (x * pow2(-64)) * pow2(-63);
}

// Blasf.step: one element of reduction `op` added to lane value `a` (0 dot, 1 sum, 2 asum, 3 sum of squares,
// 4 sum of squares of x·2^k, nrm2's rescaled pass).
float blasStep(uint op, float a, uint xb, uint yb, int k)
{
    float xv = asfloat(xb);
    if (op == 0)
    {
        return fma(xv, asfloat(yb), a);
    }
    if (op == 1)
    {
        return a + xv;
    }
    if (op == 2)
    {
        return a + jabs(xv);
    }
    if (op == 3)
    {
        return fma(xv, xv, a);
    }
    float xs = scaleBy(xb, k);
    return fma(xs, xs, a);
}
