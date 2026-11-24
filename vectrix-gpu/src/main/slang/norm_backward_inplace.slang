// The input gradient of Norms.layerNormBackward or Norms.rmsNormBackward over rows in place: dx overwrites x
// (flags & 2 = 0) or dy (flags & 2) at the same offset and stride. a is that buffer, b the other input. flags: 1 gamma,
// 2 dx is dy, 4 RMS. See norm_backward.slang and normBackwardRow in norms_row.slang.
import norms_row;

struct NormBackwardInPlaceParams
{
    uint rows;
    uint n;
    uint aOff;
    uint lda;
    uint gOff;
    uint statsOff;
    uint bOff;
    uint ldb;
    uint flags;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> a;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> gamma;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> stats;
[[vk::binding(3, 0)]] RWStructuredBuffer<float> b;
[[vk::push_constant]] ConstantBuffer<NormBackwardInPlaceParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void normBackwardInPlace(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return;
    }
    bool hasGamma = (p.flags & 1) != 0;
    bool rms = (p.flags & 4) != 0;
    uint aBase = p.aOff + row * p.lda;
    uint bBase = p.bOff + row * p.ldb;
    uint sBase = p.statsOff + (rms ? row : 2 * row);
    if ((p.flags & 2) != 0)
    {
        normBackwardRow(b, bBase, gamma, p.gOff, hasGamma, stats, sBase, rms, a, aBase, a, aBase, p.n, local.x);
    }
    else
    {
        normBackwardRow(a, aBase, gamma, p.gOff, hasGamma, stats, sBase, rms, b, bBase, a, aBase, p.n, local.x);
    }
}
