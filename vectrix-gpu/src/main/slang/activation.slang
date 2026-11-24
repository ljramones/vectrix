// Activations with the output in its own buffer: dest[destOff + i] = op(a[aOff + i], b[bOff + i]) for i in [0, n).
// A forward op reads only `a` (the wrapper binds the input at both slots); a backward op reads the forward value
// from `a` and dy from `b`, which may be the same buffer. See activation_ops.slang.
import activation_ops;

struct ActivationParams
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
[[vk::push_constant]] ConstantBuffer<ActivationParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void activation(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 256 + local.x;
    if (i >= p.n)
    {
        return;
    }
    float bv = p.op >= RELU_BACKWARD ? b[p.bOff + i] : 0.0f;
    dest[p.destOff + i] = activationOp(p.op, a[p.aOff + i], bv);
}
