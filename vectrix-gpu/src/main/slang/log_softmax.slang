// Log-softmax over rows, x and y in different buffers: row r of y = Softmax.logSoftmax(row r of x). See
// logSoftmaxRow in softmax_row.slang.
import softmax_row;

struct LogSoftmaxParams
{
    uint rows;
    uint n;
    uint xOff;
    uint ldx;
    uint yOff;
    uint ldy;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> y;
[[vk::push_constant]] ConstantBuffer<LogSoftmaxParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void logSoftmax(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return; // the whole group: row depends only on the group
    }
    logSoftmaxRow(x, p.xOff + row * p.ldx, y, p.yOff + row * p.ldy, p.n, local.x);
}
