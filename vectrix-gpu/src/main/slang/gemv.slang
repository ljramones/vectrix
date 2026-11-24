// Gemmf.gemv: y = alpha·op(A)·x + beta·y over row-major float buffers, each element exactly Blasf.dot of a row of
// op(A) and x in Blasf's order. Java twin: org.dynamisengine.vectrix.blas.Gemmf.
//
// A group of 64 threads computes 64 consecutive elements of y, one per thread, as gemm.slang does for a tile of C:
// the n dimension is walked in Blasf's 256-element blocks (pairwise tree in common.slang); inside a block, element
// e goes to lane e mod 8, staged through group memory 32 elements at a time. The staging reads A along its rows in
// both layouts, so neighbouring threads read neighbouring elements.
import common;

struct GemvParams
{
    uint transA;
    uint m;
    uint n;
    float alpha;
    float beta;
    uint aOff;
    uint lda;
    uint xOff;
    uint yOff;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> a;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> x;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> y;
[[vk::push_constant]] ConstantBuffer<GemvParams> p;

static const uint ROWS = 64;
static const uint KC = 32;
static const uint BLOCK = 256;

// One padding column keeps a thread's row reads on distinct banks.
groupshared float tileA[ROWS][KC + 1];
groupshared float tileX[KC];

struct GemvBlocks : IBlockSource
{
    uint i0;
    uint row;
    uint next;

    // One 256-element block of the dot product for y[i0 + row].
    [mutating] float nextBlock()
    {
        uint start = next * BLOCK;
        uint len = min(BLOCK, p.n - start);
        next++;
        float acc[8] = { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f };
        for (uint kc = 0; kc < len; kc += KC)
        {
            for (uint q = row; q < ROWS * KC; q += ROWS)
            {
                uint r;
                uint kk;
                if (p.transA != 0)
                {
                    // A is n×m: op(A)[i][kk] = A[kk][i], contiguous in i
                    r = q % ROWS;
                    kk = q / ROWS;
                }
                else
                {
                    r = q / KC;
                    kk = q % KC;
                }
                float v = 0.0f;
                if (i0 + r < p.m && kc + kk < len)
                {
                    uint col = start + kc + kk;
                    if (p.transA != 0)
                    {
                        v = a[p.aOff + col * p.lda + i0 + r];
                    }
                    else
                    {
                        v = a[p.aOff + (i0 + r) * p.lda + col];
                    }
                }
                tileA[r][kk] = v;
            }
            if (row < KC)
            {
                float v = 0.0f;
                if (kc + row < len)
                {
                    v = x[p.xOff + start + kc + row];
                }
                tileX[row] = v;
            }
            GroupMemoryBarrierWithGroupSync();
            uint lim = min(KC, len - kc);
            for (uint e8 = 0; e8 < KC; e8 += 8)
            {
                [ForceUnroll]
                for (uint l = 0; l < 8; l++)
                {
                    uint e = e8 + l;
                    if (e < lim)
                    {
                        acc[l] = fma(tileA[row][e], tileX[e], acc[l]);
                    }
                }
            }
            GroupMemoryBarrierWithGroupSync();
        }
        return combineLanes(acc[0], acc[1], acc[2], acc[3], acc[4], acc[5], acc[6], acc[7]);
    }
};

[shader("compute")]
[numthreads(64, 1, 1)]
void gemv(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint g = group.y * p.gridX + group.x;
    if (g * ROWS >= p.m)
    {
        return; // the whole group: g depends only on the group
    }
    GemvBlocks dot;
    dot.i0 = g * ROWS;
    dot.row = local.x;
    dot.next = 0;
    float s = 0.0f;
    if (p.n > 0)
    {
        s = pairwiseBlocks(dot, (p.n + BLOCK - 1) / BLOCK);
    }
    uint i = dot.i0 + dot.row;
    if (i < p.m)
    {
        uint yi = p.yOff + i;
        // Gemmf.finish: y is not read when beta == 0
        if (p.beta == 0.0f)
        {
            y[yi] = p.alpha * s;
        }
        else
        {
            y[yi] = fma(p.alpha, s, p.beta * y[yi]);
        }
    }
}
