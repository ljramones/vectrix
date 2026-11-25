// The exact GELU entirely in one buffer at one offset: x[off + i] = op(x[off + i], x[off + i]). The forward op in
// place, or the backward op whose input, dy and output are all the same elements. The same layout as
// activation_self.slang; see gelu_ops.slang.
import gelu_ops;

struct GeluSelfParams
{
    uint op;
    uint n;
    uint off;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::push_constant]] ConstantBuffer<GeluSelfParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void geluSelf(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 256 + local.x;
    if (i >= p.n)
    {
        return;
    }
    float v = x[p.off + i];
    x[p.off + i] = geluOp(p.op, v, v);
}
