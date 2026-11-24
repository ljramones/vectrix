// Optim.sgd: one SGD step with momentum and coupled weight decay per element, in the Java's order; negLr = -lr is
// computed by the wrapper. Java twin: org.dynamisengine.vectrix.nn.Optim.sgd.

struct SgdParams
{
    uint n;
    uint pOff;
    uint gOff;
    uint bOff;
    float negLr;
    float momentum;
    float weightDecay;
    uint nesterov;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> param;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> grad;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> buf;
[[vk::push_constant]] ConstantBuffer<SgdParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void sgd(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 256 + local.x;
    if (i >= p.n)
    {
        return;
    }
    float pi = param[p.pOff + i];
    float d = fma(p.weightDecay, pi, grad[p.gOff + i]);
    float b = fma(p.momentum, buf[p.bOff + i], d);
    buf[p.bOff + i] = b;
    float step = p.nesterov != 0 ? fma(p.momentum, b, d) : b;
    param[p.pOff + i] = fma(p.negLr, step, pi);
}
