// Formats.fromE4M3 / fromE5M2 over a buffer: x[xOff + i] = decode(codes[cOff + i]) for i in [0, n), codes packed four
// per 32-bit word (element 0 in the low byte). The values are built from their bits, exactly. Java twin:
// org.dynamisengine.vectrix.nn.Formats (see formats.slang).
import formats;

struct FromFp8Params
{
    uint cOff;
    uint xOff;
    uint n;
    uint e5m2;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<uint> codes;
[[vk::binding(1, 0)]] RWStructuredBuffer<uint> x;
[[vk::push_constant]] ConstantBuffer<FromFp8Params> p;

[shader("compute")]
[numthreads(64, 1, 1)]
void fromFp8(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 64 + local.x;
    if (i >= p.n)
    {
        return;
    }
    x[p.xOff + i] = fp8ToFloatBits(packedByte(codes, p.cOff + i), p.e5m2);
}
