// nn.Pointwise entirely in one buffer at one offset: x[off + i] = op(x[off + i], x[off + i]). A unary op in place, or a
// binary op whose inputs and output are the same elements. The same layout as activation_self.slang.
import pointwise_ops;

struct PointwiseSelfParams
{
    uint op;
    uint n;
    uint off;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::push_constant]] ConstantBuffer<PointwiseSelfParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void pointwiseSelf(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 256 + local.x;
    if (i >= p.n)
    {
        return;
    }
    float v = x[p.off + i];
    x[p.off + i] = pointwiseOp(p.op, v, v);
}
