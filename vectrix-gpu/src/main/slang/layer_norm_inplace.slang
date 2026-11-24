// Layer normalization over rows in place: each row of x is replaced by Norms.layerNorm of it. flags: 1 gamma,
// 2 beta, 4 stats. See layerNormRow in norms_row.slang.
import norms_row;

struct LayerNormInPlaceParams
{
    uint rows;
    uint n;
    uint xOff;
    uint ldx;
    uint gOff;
    uint bOff;
    uint statsOff;
    uint flags;
    float eps;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> gamma;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> beta;
[[vk::binding(3, 0)]] RWStructuredBuffer<float> stats;
[[vk::push_constant]] ConstantBuffer<LayerNormInPlaceParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void layerNormInPlace(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return;
    }
    uint base = p.xOff + row * p.ldx;
    layerNormRow(x, base, gamma, p.gOff, (p.flags & 1) != 0, beta, p.bOff, (p.flags & 2) != 0, p.eps, x, base, p.n,
            stats, p.statsOff + 2 * row, (p.flags & 4) != 0, local.x);
}
