// Blasf.axpy with y the same array as x at the same offset: x[i] = fma(a, x[i], x[i]). Java twin:
// org.dynamisengine.vectrix.blas.Blasf. One thread per element.
import common;

struct AxpyInPlaceParams
{
    float a;
    uint n;
    uint xOff;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::push_constant]] ConstantBuffer<AxpyInPlaceParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void axpyInPlace(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 256 + local.x;
    if (i >= p.n)
    {
        return;
    }
    float v = x[p.xOff + i];
    x[p.xOff + i] = fma(p.a, v, v);
}
