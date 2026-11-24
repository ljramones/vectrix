// Blasf.scal: x[i] = a·x[i], in place. Java twin: org.dynamisengine.vectrix.blas.Blasf. One thread per element.
import common;

struct ScalParams
{
    float a;
    uint n;
    uint xOff;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::push_constant]] ConstantBuffer<ScalParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void scal(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 256 + local.x;
    if (i >= p.n)
    {
        return;
    }
    x[p.xOff + i] = p.a * x[p.xOff + i];
}
