// The input gradient of Norms.layerNormBackward (flags & 4 = 0; stats (mean, 1/σ) at statsOff + 2r) or
// Norms.rmsNormBackward (flags & 4; 1/rms at statsOff + r) over rows, x, dy and dx in different buffers. flags: 1
// gamma, 4 RMS. The parameter gradients are norm_param_grads.slang. See normBackwardRow in norms_row.slang.
import norms_row;

struct NormBackwardParams
{
    uint rows;
    uint n;
    uint xOff;
    uint ldx;
    uint gOff;
    uint statsOff;
    uint dyOff;
    uint lddy;
    uint dxOff;
    uint lddx;
    uint flags;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> gamma;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> stats;
[[vk::binding(3, 0)]] RWStructuredBuffer<float> dy;
[[vk::binding(4, 0)]] RWStructuredBuffer<float> dx;
[[vk::push_constant]] ConstantBuffer<NormBackwardParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void normBackward(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return;
    }
    bool rms = (p.flags & 4) != 0;
    normBackwardRow(x, p.xOff + row * p.ldx, gamma, p.gOff, (p.flags & 1) != 0, stats,
            p.statsOff + (rms ? row : 2 * row), rms, dy, p.dyOff + row * p.lddy, dx, p.dxOff + row * p.lddx, p.n,
            local.x);
}
