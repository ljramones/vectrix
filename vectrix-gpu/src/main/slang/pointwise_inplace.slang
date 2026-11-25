// A binary nn.Pointwise op whose output overwrites one input at the same offset: dest is `a` (destIsDy = 0) or `b`
// (destIsDy = 1), and `other` holds the other input. The same layout as activation_inplace.slang.
import pointwise_ops;

struct PointwiseInPlaceParams
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
[[vk::push_constant]] ConstantBuffer<PointwiseInPlaceParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void pointwiseInPlace(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 256 + local.x;
    if (i >= p.n)
    {
        return;
    }
    float d = dest[p.destOff + i];
    float o = other[p.otherOff + i];
    float av = p.destIsDy != 0 ? o : d;
    float bv = p.destIsDy != 0 ? d : o;
    dest[p.destOff + i] = pointwiseOp(p.op, av, bv);
}
