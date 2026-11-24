// Cross-entropy backward over rows in place: each row of logits is replaced by its gradient. See
// crossEntropyBackwardRow in softmax_row.slang.
import softmax_row;

struct CrossEntropyBackwardInPlaceParams
{
    uint rows;
    uint n;
    uint xOff;
    uint ldx;
    uint tOff;
    float dLoss;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> logits;
[[vk::binding(1, 0)]] RWStructuredBuffer<int> targets;
[[vk::push_constant]] ConstantBuffer<CrossEntropyBackwardInPlaceParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void crossEntropyBackwardInPlace(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return;
    }
    uint base = p.xOff + row * p.ldx;
    crossEntropyBackwardRow(logits, base, targets[p.tOff + row], p.dLoss, logits, base, p.n, local.x);
}
