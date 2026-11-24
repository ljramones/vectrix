// The parameter gradients of Norms.layerNormBackward (flags & 4 = 0) and Norms.rmsNormBackward (flags & 4) over rows:
// dGamma[i] += dy·x̂ and dBeta[i] += dy (layer norm only), accumulated over rows 0, 1, ... in order, as calling the
// Java method on each row in turn does. flags: 1 dGamma, 2 dBeta, 4 RMS. Java twin: org.dynamisengine.vectrix.nn.Norms.
//
// One thread per column walks the rows: x̂ = (x - mean)·rstd from stats (mean, rstd) at statsOff + 2r, or x·rinv
// from stats at statsOff + r. The input gradient (norm_backward.slang) is recorded after this kernel, so this one
// reads x and dy before an in-place dx overwrites them.
import common;

struct NormParamGradsParams
{
    uint rows;
    uint n;
    uint xOff;
    uint ldx;
    uint statsOff;
    uint dyOff;
    uint lddy;
    uint dgOff;
    uint dbOff;
    uint flags;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> stats;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> dy;
[[vk::binding(3, 0)]] RWStructuredBuffer<float> dGamma;
[[vk::binding(4, 0)]] RWStructuredBuffer<float> dBeta;
[[vk::push_constant]] ConstantBuffer<NormParamGradsParams> p;

static const uint GROUP = 64;

[shader("compute")]
[numthreads(64, 1, 1)]
void normParamGrads(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * GROUP + local.x;
    if (i >= p.n)
    {
        return;
    }
    bool hasDGamma = (p.flags & 1) != 0;
    bool hasDBeta = (p.flags & 2) != 0;
    bool rms = (p.flags & 4) != 0;
    float dg = dGamma[hasDGamma ? p.dgOff + i : 0];
    float db = dBeta[hasDBeta ? p.dbOff + i : 0];
    for (uint r = 0; r < p.rows; r++)
    {
        float xv = x[p.xOff + r * p.ldx + i];
        float d = dy[p.dyOff + r * p.lddy + i];
        float h;
        if (rms)
        {
            h = xv * stats[p.statsOff + r];
        }
        else
        {
            h = (xv - stats[p.statsOff + 2 * r]) * stats[p.statsOff + 2 * r + 1];
        }
        dg = fma(d, h, dg);
        db = db + d;
    }
    if (hasDGamma)
    {
        dGamma[p.dgOff + i] = dg;
    }
    if (hasDBeta)
    {
        dBeta[p.dbOff + i] = db;
    }
}
