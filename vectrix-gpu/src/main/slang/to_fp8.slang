// Formats.toE4M3 / toE5M2 over a buffer: codes[cOff + i] = encode(x[xOff + i]) for i in [0, n), four codes per 32-bit
// word of codes (element 0 in the low byte). Java twin: org.dynamisengine.vectrix.nn.Formats (see formats.slang).
//
// One thread per word of codes. A word only partly inside the range keeps its other bytes, so any offset and count
// work.
import formats;

struct ToFp8Params
{
    uint xOff;
    uint cOff;
    uint n;
    uint e5m2;
    uint saturate;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<uint> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<uint> codes;
[[vk::push_constant]] ConstantBuffer<ToFp8Params> p;

[shader("compute")]
[numthreads(64, 1, 1)]
void toFp8(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint t = (group.y * p.gridX + group.x) * 64 + local.x;
    uint first = p.cOff >> 2;
    uint last = (p.cOff + p.n - 1) >> 2;
    if (t > last - first)
    {
        return;
    }
    uint w = first + t;
    uint lo = w * 4;
    bool partial = lo < p.cOff || lo + 3 - p.cOff >= p.n;
    uint word = partial ? codes[w] : 0u;
    for (uint k = 0; k < 4; k++)
    {
        uint i = lo + k;
        if (i >= p.cOff && i - p.cOff < p.n)
        {
            uint shift = k * 8u;
            uint c = fp8FromFloatBits(x[p.xOff + (i - p.cOff)], p.e5m2, p.saturate);
            word = (word & ~(0xFFu << shift)) | (c << shift);
        }
    }
    codes[w] = word;
}
