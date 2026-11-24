// Dropout in place: x[xOff + e] is replaced by its dropout. See dropout_core.slang.
import dropout_core;

struct DropoutInPlaceParams
{
    uint seedLo;
    uint seedHi;
    uint startLo;
    uint startHi;
    float prob;
    float scale;
    uint xOff;
    uint n;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::push_constant]] ConstantBuffer<DropoutInPlaceParams> p;

[shader("compute")]
[numthreads(64, 1, 1)]
void dropoutInPlace(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint t = (group.y * p.gridX + group.x) * 64 + local.x;
    dropoutCounter(x, p.xOff, x, p.xOff, p.seedLo, p.seedHi, p.startLo, p.startHi, p.prob, p.scale, p.n, t);
}
