// Formats.dequantizeInt8: x[xOff + i] = q[qOff + i]·scales[sOff + i / 32] for i in [0, n), q packed four int8 codes
// per 32-bit word (element 0 in the low byte). Exact on every device, subnormal scales included (formats.slang).
// Java twin: org.dynamisengine.vectrix.nn.Formats.
import formats;

struct DequantizeInt8Params
{
    uint qOff;
    uint sOff;
    uint xOff;
    uint n;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<uint> q;
[[vk::binding(1, 0)]] RWStructuredBuffer<uint> scales;
[[vk::binding(2, 0)]] RWStructuredBuffer<uint> x;
[[vk::push_constant]] ConstantBuffer<DequantizeInt8Params> p;

[shader("compute")]
[numthreads(64, 1, 1)]
void dequantizeInt8(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 64 + local.x;
    if (i >= p.n)
    {
        return;
    }
    x[p.xOff + i] = int8Dequantize(packedInt8(q, p.qOff + i), scales[p.sOff + i / 32]);
}
