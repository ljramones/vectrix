// Layer normalization over rows, x and y in different buffers: row r of y = Norms.layerNorm(row r of x), with
// (mean, 1/σ) of row r at stats[statsOff + 2r]. flags: 1 gamma, 2 beta, 4 stats. See layerNormRow in norms_row.slang.
import norms_row;

struct LayerNormParams
{
    uint rows;
    uint n;
    uint xOff;
    uint ldx;
    uint gOff;
    uint bOff;
    uint yOff;
    uint ldy;
    uint statsOff;
    uint flags;
    float eps;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> gamma;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> beta;
[[vk::binding(3, 0)]] RWStructuredBuffer<float> y;
[[vk::binding(4, 0)]] RWStructuredBuffer<float> stats;
[[vk::push_constant]] ConstantBuffer<LayerNormParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void layerNorm(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return;
    }
    layerNormRow(x, p.xOff + row * p.ldx, gamma, p.gOff, (p.flags & 1) != 0, beta, p.bOff, (p.flags & 2) != 0, p.eps,
            y, p.yOff + row * p.ldy, p.n, stats, p.statsOff + 2 * row, (p.flags & 4) != 0, local.x);
}
