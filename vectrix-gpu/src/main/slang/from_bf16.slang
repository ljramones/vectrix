// Formats.fromBf16 over a buffer: x[xOff + i] = fromBf16(h[hOff + i]) for i in [0, n), h packed two bfloat16 per
// 32-bit word (element 0 in the low half). Integer arithmetic only: the float is the bfloat16 bits shifted up, stored
// as bits, so subnormals survive on every device. Java twin: org.dynamisengine.vectrix.nn.Formats.
import formats;

struct FromBf16Params
{
    uint hOff;
    uint xOff;
    uint n;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<uint> h;
[[vk::binding(1, 0)]] RWStructuredBuffer<uint> x;
[[vk::push_constant]] ConstantBuffer<FromBf16Params> p;

[shader("compute")]
[numthreads(64, 1, 1)]
void fromBf16(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 64 + local.x;
    if (i >= p.n)
    {
        return;
    }
    x[p.xOff + i] = packedHalf(h, p.hOff + i) << 16;
}
