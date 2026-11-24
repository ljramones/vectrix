// A backward activation whose gradient overwrites one of its inputs at the same offset: dest is the forward value
// (destIsDy = 0) or dy (destIsDy = 1), and `other` holds the other input. Each thread reads its element of both
// before writing it. See activation_ops.slang.
import activation_ops;

struct ActivationInPlaceParams
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
[[vk::push_constant]] ConstantBuffer<ActivationInPlaceParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void activationInPlace(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
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
    dest[p.destOff + i] = activationOp(p.op, fwd, dy);
}
