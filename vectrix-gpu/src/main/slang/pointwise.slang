// nn.Pointwise with the output in its own buffer: dest[destOff + i] = op(a[aOff + i], b[bOff + i]). A unary op reads
// only `a` (the wrapper binds the input at both slots). The same layout as activation.slang; see pointwise_ops.slang.
import pointwise_ops;

struct PointwiseParams
{
    uint op;
    uint n;
    uint aOff;
    uint bOff;
    uint destOff;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> a;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> b;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> dest;
[[vk::push_constant]] ConstantBuffer<PointwiseParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void pointwise(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 256 + local.x;
    if (i >= p.n)
    {
        return;
    }
    float bv = p.op >= PW_ADD ? b[p.bOff + i] : 0.0f;
    dest[p.destOff + i] = pointwiseOp(p.op, a[p.aOff + i], bv);
}
