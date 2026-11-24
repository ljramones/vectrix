// Log-softmax over rows in place: each row of x is replaced by Softmax.logSoftmax of it. See logSoftmaxRow in
// softmax_row.slang.
import softmax_row;

struct LogSoftmaxInPlaceParams
{
    uint rows;
    uint n;
    uint xOff;
    uint ldx;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::push_constant]] ConstantBuffer<LogSoftmaxInPlaceParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void logSoftmaxInPlace(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return;
    }
    uint base = p.xOff + row * p.ldx;
    logSoftmaxRow(x, base, x, base, p.n, local.x);
}
