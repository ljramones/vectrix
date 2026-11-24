// Optim.adamW: one AdamW step per element, in the Java's order. The wrapper computes, in Java exactly as Optim does,
// bc1 = (float) (1 - β1^t) and bc2 = (float) (1 - β2^t) (in double, with ReproducibleMath.pow), a1 = 1 - β1,
// a2 = 1 - β2, decay = 1 - lr·wd and negLr = -lr. (float) Math.sqrt of a float is the correctly rounded float
// square root, which the GPU's sqrt is. Java twin: org.dynamisengine.vectrix.nn.Optim.adamW.

struct AdamWParams
{
    uint n;
    uint pOff;
    uint gOff;
    uint mOff;
    uint vOff;
    float negLr;
    float beta1;
    float beta2;
    float eps;
    float bc1;
    float bc2;
    float a1;
    float a2;
    float decay;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> param;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> grad;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> m;
[[vk::binding(3, 0)]] RWStructuredBuffer<float> v;
[[vk::push_constant]] ConstantBuffer<AdamWParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void adamw(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 256 + local.x;
    if (i >= p.n)
    {
        return;
    }
    float gi = grad[p.gOff + i];
    float mi = fma(p.beta1, m[p.mOff + i], p.a1 * gi);
    float vi = fma(p.beta2, v[p.vOff + i], p.a2 * gi * gi);
    m[p.mOff + i] = mi;
    v[p.vOff + i] = vi;
    float update = (mi / p.bc1) / (sqrt(vi / p.bc2) + p.eps);
    param[p.pOff + i] = fma(p.negLr, update, param[p.pOff + i] * p.decay);
}
