// RMS normalization over rows in place: each row of x is replaced by Norms.rmsNorm of it. flags: 1 gamma, 4 stats.
// See rmsNormRow in norms_row.slang.
import norms_row;

struct RmsNormInPlaceParams
{
    uint rows;
    uint n;
    uint xOff;
    uint ldx;
    uint gOff;
    uint statsOff;
    uint flags;
    float eps;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> gamma;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> stats;
[[vk::push_constant]] ConstantBuffer<RmsNormInPlaceParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void rmsNormInPlace(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return;
    }
    uint base = p.xOff + row * p.ldx;
    rmsNormRow(x, base, gamma, p.gOff, (p.flags & 1) != 0, p.eps, x, base, p.n, stats, p.statsOff + row,
            (p.flags & 4) != 0, local.x);
}
