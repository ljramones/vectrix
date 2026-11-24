// Cross-entropy over rows: loss[lossOff + r] = Loss.crossEntropy(row r of logits, targets[tOff + r]), that is
// Softmax.logSumExp(row) - row[target]. A target outside [0, n) gives NaN (the Java method throws).
// Java twin: org.dynamisengine.vectrix.nn.Loss.
import softmax_row;

struct CrossEntropyParams
{
    uint rows;
    uint n;
    uint xOff;
    uint ldx;
    uint tOff;
    uint lossOff;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> logits;
[[vk::binding(1, 0)]] RWStructuredBuffer<int> targets;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> loss;
[[vk::push_constant]] ConstantBuffer<CrossEntropyParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void crossEntropy(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return;
    }
    uint base = p.xOff + row * p.ldx;
    float lse = logSumExpRow(logits, base, p.n, local.x);
    if (local.x == 0)
    {
        int target = targets[p.tOff + row];
        bool valid = target >= 0 && uint(target) < p.n;
        float picked = logits[valid ? base + uint(target) : base]; // in bounds either way
        loss[p.lossOff + row] = valid ? lse - picked : asfloat(0x7FC00000u);
    }
}
