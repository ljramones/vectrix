// Cross-entropy backward over rows, logits and dx in different buffers: row r of dx =
// Loss.crossEntropyBackward(row r of logits, targets[tOff + r], dLoss). A target outside [0, n) makes the row NaN
// (the Java method throws). See crossEntropyBackwardRow in softmax_row.slang.
import softmax_row;

struct CrossEntropyBackwardParams
{
    uint rows;
    uint n;
    uint xOff;
    uint ldx;
    uint tOff;
    uint dxOff;
    uint lddx;
    float dLoss;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> logits;
[[vk::binding(1, 0)]] RWStructuredBuffer<int> targets;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> dx;
[[vk::push_constant]] ConstantBuffer<CrossEntropyBackwardParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void crossEntropyBackward(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return;
    }
    crossEntropyBackwardRow(logits, p.xOff + row * p.ldx, targets[p.tOff + row], p.dLoss, dx,
            p.dxOff + row * p.lddx, p.n, local.x);
}
