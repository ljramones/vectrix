// RMS normalization over rows, x and y in different buffers: row r of y = Norms.rmsNorm(row r of x), with 1/rms of
// row r at stats[statsOff + r]. flags: 1 gamma, 4 stats. See rmsNormRow in norms_row.slang.
import norms_row;

struct RmsNormParams
{
    uint rows;
    uint n;
    uint xOff;
    uint ldx;
    uint gOff;
    uint yOff;
    uint ldy;
    uint statsOff;
    uint flags;
    float eps;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> gamma;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> y;
[[vk::binding(3, 0)]] RWStructuredBuffer<float> stats;
[[vk::push_constant]] ConstantBuffer<RmsNormParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void rmsNorm(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return;
    }
    rmsNormRow(x, p.xOff + row * p.ldx, gamma, p.gOff, (p.flags & 1) != 0, p.eps, y, p.yOff + row * p.ldy, p.n,
            stats, p.statsOff + row, (p.flags & 4) != 0, local.x);
}
