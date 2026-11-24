// Formats.toBf16 over a buffer: h[hOff + i] = toBf16(x[xOff + i]) for i in [0, n), two bfloat16 per 32-bit word of
// h (element 0 in the low half). Integer arithmetic only. Java twin: org.dynamisengine.vectrix.nn.Formats.
//
// One thread per word of h. A word only partly inside the range keeps its other half, so any offset and count work.
import formats;

struct ToBf16Params
{
    uint xOff;
    uint hOff;
    uint n;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<uint> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<uint> h;
[[vk::push_constant]] ConstantBuffer<ToBf16Params> p;

[shader("compute")]
[numthreads(64, 1, 1)]
void toBf16(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint t = (group.y * p.gridX + group.x) * 64 + local.x;
    uint first = p.hOff >> 1;
    uint last = (p.hOff + p.n - 1) >> 1;
    if (t > last - first)
    {
        return;
    }
    uint w = first + t;
    uint lo = w * 2;
    bool partial = lo < p.hOff || lo + 1 - p.hOff >= p.n;
    uint word = partial ? h[w] : 0u;
    for (uint k = 0; k < 2; k++)
    {
        uint i = lo + k;
        if (i >= p.hOff && i - p.hOff < p.n)
        {
            uint shift = k * 16u;
            word = (word & ~(0xFFFFu << shift)) | (toBf16Bits(x[p.xOff + (i - p.hOff)]) << shift);
        }
    }
    h[w] = word;
}
