// Blasf.axpy: y[i] = fma(a, x[i], y[i]), x and y in different buffers. Java twin:
// org.dynamisengine.vectrix.blas.Blasf. One thread per element.
import common;

struct AxpyParams
{
    float a;
    uint n;
    uint xOff;
    uint yOff;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> y;
[[vk::push_constant]] ConstantBuffer<AxpyParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void axpy(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 256 + local.x;
    if (i >= p.n)
    {
        return;
    }
    y[p.yOff + i] = fma(p.a, x[p.xOff + i], y[p.yOff + i]);
}
