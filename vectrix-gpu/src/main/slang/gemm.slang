// Gemmf.gemm: C = alpha·op(A)·op(B) + beta·C over row-major float buffers, each element exactly Blasf.dot of a row
// of op(A) and a column of op(B) in Blasf's order. Java twin: org.dynamisengine.vectrix.blas.Gemmf.
//
// A 16×16 group computes a 16×16 tile of C, one element per thread. The k dimension is walked in Blasf's
// 256-element blocks (pairwise tree in common.slang); inside a block, element e goes to lane e mod 8, staged
// through group memory 32 elements at a time.
import common;

struct GemmParams
{
    uint transA;
    uint transB;
    uint m;
    uint n;
    uint k;
    float alpha;
    float beta;
    uint aOff;
    uint lda;
    uint bOff;
    uint ldb;
    uint cOff;
    uint ldc;
};

// Buffers in slot order, then the parameter block (Metal takes it at the next buffer index).
[[vk::binding(0, 0)]] RWStructuredBuffer<float> a;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> b;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> c;
[[vk::push_constant]] ConstantBuffer<GemmParams> p;

static const uint TILE = 16;
static const uint KC = 32;
static const uint BLOCK = 256;

groupshared float tileA[TILE][KC];
groupshared float tileB[KC][TILE];

float opA(uint i, uint kk)
{
    return p.transA != 0 ? a[p.aOff + kk * p.lda + i] : a[p.aOff + i * p.lda + kk];
}

float opB(uint kk, uint j)
{
    return p.transB != 0 ? b[p.bOff + j * p.ldb + kk] : b[p.bOff + kk * p.ldb + j];
}

struct DotBlocks : IBlockSource
{
    uint i0;
    uint j0;
    uint row;
    uint col;
    uint thread;
    uint next;

    // One 256-element block of the dot product for C[i0 + row][j0 + col].
    [mutating] float nextBlock()
    {
        uint start = next * BLOCK;
        uint len = min(BLOCK, p.k - start);
        next++;
        float acc[8] = { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f };
        for (uint kc = 0; kc < len; kc += KC)
        {
            for (uint q = thread; q < TILE * KC; q += TILE * TILE)
            {
                uint ar = q / KC;
                uint ak = q % KC;
                tileA[ar][ak] = (i0 + ar < p.m && kc + ak < len) ? opA(i0 + ar, start + kc + ak) : 0.0f;
                uint bk = q / TILE;
                uint bc = q % TILE;
                tileB[bk][bc] = (j0 + bc < p.n && kc + bk < len) ? opB(start + kc + bk, j0 + bc) : 0.0f;
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
                        acc[l] = fma(tileA[row][e], tileB[e][col], acc[l]);
                    }
                }
            }
            GroupMemoryBarrierWithGroupSync();
        }
        return combineLanes(acc[0], acc[1], acc[2], acc[3], acc[4], acc[5], acc[6], acc[7]);
    }
};

[shader("compute")]
[numthreads(16, 16, 1)]
void gemm(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    DotBlocks dot;
    dot.i0 = group.y * TILE;
    dot.j0 = group.x * TILE;
    dot.row = local.y;
    dot.col = local.x;
    dot.thread = local.y * TILE + local.x;
    dot.next = 0;
    float s = 0.0f;
    if (p.k > 0)
    {
        s = pairwiseBlocks(dot, (p.k + BLOCK - 1) / BLOCK);
    }
    uint i = dot.i0 + dot.row;
    uint j = dot.j0 + dot.col;
    if (i < p.m && j < p.n)
    {
        uint ci = p.cOff + i * p.ldc + j;
        // Gemmf.finish: C is not read when beta == 0
        c[ci] = p.beta == 0.0f ? p.alpha * s : fma(p.alpha, s, p.beta * c[ci]);
    }
}
