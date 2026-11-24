// An activation entirely in one buffer at one offset: x[off + i] = op(x[off + i], x[off + i]). A forward op in place,
// or a backward op whose forward value, dy and output are all the same elements. See activation_ops.slang.
import activation_ops;

struct ActivationSelfParams
{
    uint op;
    uint n;
    uint off;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::push_constant]] ConstantBuffer<ActivationSelfParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void activationSelf(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 256 + local.x;
    if (i >= p.n)
    {
        return;
    }
    float v = x[p.off + i];
    x[p.off + i] = activationOp(p.op, v, v);
}
