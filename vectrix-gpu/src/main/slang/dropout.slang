// Dropout with the output in its own buffer. See dropout_core.slang.
import dropout_core;

struct DropoutParams
{
    uint seedLo;
    uint seedHi;
    uint startLo;
    uint startHi;
    float prob;
    float scale;
    uint xOff;
    uint yOff;
    uint n;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> y;
[[vk::push_constant]] ConstantBuffer<DropoutParams> p;

[shader("compute")]
[numthreads(64, 1, 1)]
void dropout(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint t = (group.y * p.gridX + group.x) * 64 + local.x;
    dropoutCounter(x, p.xOff, y, p.yOff, p.seedLo, p.seedHi, p.startLo, p.startHi, p.prob, p.scale, p.n, t);
}
