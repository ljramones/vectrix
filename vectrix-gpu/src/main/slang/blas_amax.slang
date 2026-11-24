// The element of largest magnitude: Blasf.iamax (mode 0) and nrm2's maximum (mode 1). Java twin:
// org.dynamisengine.vectrix.blas.Blasf.
//
// Blasf.iamax returns the first NaN if there is one, and otherwise the first element of largest magnitude. That is
// the best element under one total order (NaN first, then larger |x|, then the lower index), so any reduction tree
// gives it. Magnitudes are compared as the bits of |x| (the sign cleared), which order as the values do, with NaNs
// above infinity, and never flush a subnormal.
//
// Stage 0: group g of `parts` reduces the elements g·256 + t + j·parts·256 and writes (|x| bits, index) to
// scratch[partOff + 2g ...]. Stage 1, one group: reduces the parts; mode 0 writes the index to result[outOff] (-1
// when there is none, n == 0); mode 1 finishes nrm2: m == 0 or infinite is the result, otherwise
// scratch[stateOff] = 2 and scratch[stateOff + 1] = Math.getExponent(m). With `need` not 0 the pass runs only if
// scratch[stateOff] == need.
import common;

struct AmaxParams
{
    uint mode;
    uint n;
    uint xOff;
    uint stage;
    uint parts;
    uint partOff;
    uint stateOff;
    uint outOff;
    uint need;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<uint> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<uint> scratch;
[[vk::binding(2, 0)]] RWStructuredBuffer<uint> result;
[[vk::push_constant]] ConstantBuffer<AmaxParams> p;

static const uint GROUP = 256;
static const uint NONE = 0xFFFFFFFFu;
static const uint INF_BITS = 0x7F800000u;

groupshared uint keys[GROUP];
groupshared uint indices[GROUP];

// Whether candidate a (magnitude bits, index) comes before b; NONE marks no candidate.
bool before(uint ka, uint ia, uint kb, uint ib)
{
    if (ib == NONE)
    {
        return ia != NONE;
    }
    if (ia == NONE)
    {
        return false;
    }
    bool na = ka > INF_BITS;
    bool nb = kb > INF_BITS;
    if (na || nb)
    {
        return na && nb ? ia < ib : na;
    }
    if (ka != kb)
    {
        return ka > kb;
    }
    return ia < ib;
}

[shader("compute")]
[numthreads(256, 1, 1)]
void blasAmax(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint g = group.y * p.gridX + group.x;
    if (p.stage == 0 ? g >= p.parts : g != 0)
    {
        return;
    }
    if (p.need != 0 && scratch[p.stateOff] != p.need)
    {
        return;
    }
    uint t = local.x;
    uint bk = 0;
    uint bi = NONE;
    if (p.stage == 0)
    {
        for (uint i = g * GROUP + t; i < p.n; i += p.parts * GROUP)
        {
            uint k = x[p.xOff + i] & 0x7FFFFFFFu;
            if (before(k, i, bk, bi))
            {
                bk = k;
                bi = i;
            }
        }
    }
    else
    {
        for (uint j = t; j < p.parts; j += GROUP)
        {
            uint k = scratch[p.partOff + 2 * j];
            uint i = scratch[p.partOff + 2 * j + 1];
            if (before(k, i, bk, bi))
            {
                bk = k;
                bi = i;
            }
        }
    }
    keys[t] = bk;
    indices[t] = bi;
    GroupMemoryBarrierWithGroupSync();
    for (uint stride = GROUP / 2; stride > 0; stride >>= 1)
    {
        if (t < stride)
        {
            // keys[t + stride] is not written again in this or a later round
            if (before(keys[t + stride], indices[t + stride], keys[t], indices[t]))
            {
                keys[t] = keys[t + stride];
                indices[t] = indices[t + stride];
            }
        }
        GroupMemoryBarrierWithGroupSync();
    }
    if (t == 0)
    {
        if (p.stage == 0)
        {
            scratch[p.partOff + 2 * g] = keys[0];
            scratch[p.partOff + 2 * g + 1] = indices[0];
        }
        else if (p.mode == 0)
        {
            result[p.outOff] = indices[0];
        }
        else
        {
            uint m = keys[0];
            if (m == 0u || m == INF_BITS)
            {
                result[p.outOff] = m;
                scratch[p.stateOff] = 0;
            }
            else
            {
                scratch[p.stateOff] = 2;
                // Math.getExponent: -127 for a subnormal
                scratch[p.stateOff + 1] = uint(int(m >> 23) - 127);
            }
        }
    }
}
