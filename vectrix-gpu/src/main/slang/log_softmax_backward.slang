// Log-softmax backward over rows, three buffers: row r of dx = Softmax.logSoftmaxBackward(row r of y, row r of dy).
// See logSoftmaxBackwardRow in softmax_row.slang.
import softmax_row;

struct LogSoftmaxBackwardParams
{
    uint rows;
    uint n;
    uint yOff;
    uint ldy;
    uint dyOff;
    uint lddy;
    uint dxOff;
    uint lddx;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> y;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> dy;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> dx;
[[vk::push_constant]] ConstantBuffer<LogSoftmaxBackwardParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void logSoftmaxBackward(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return;
    }
    logSoftmaxBackwardRow(y, p.yOff + row * p.ldy, dy, p.dyOff + row * p.lddy, dx, p.dxOff + row * p.lddx, p.n,
            local.x);
}
