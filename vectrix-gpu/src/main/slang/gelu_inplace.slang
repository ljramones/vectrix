// The exact GELU backward with the gradient overwriting one of its inputs at the same offset: dest is the forward
// input (destIsDy = 0) or dy (destIsDy = 1), and `other` holds the other input. Each thread reads its element of both
// before writing it. The same layout as activation_inplace.slang; see gelu_ops.slang.
import gelu_ops;

struct GeluInPlaceParams
{
    uint op;
    uint n;
    uint destOff;
    uint otherOff;
    uint destIsDy;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> dest;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> other;
[[vk::push_constant]] ConstantBuffer<GeluInPlaceParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void geluInPlace(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 256 + local.x;
    if (i >= p.n)
    {
        return;
    }
    float d = dest[p.destOff + i];
    float o = other[p.otherOff + i];
    float fwd = p.destIsDy != 0 ? o : d;
    float dy = p.destIsDy != 0 ? d : o;
    dest[p.destOff + i] = geluOp(p.op, fwd, dy);
}
