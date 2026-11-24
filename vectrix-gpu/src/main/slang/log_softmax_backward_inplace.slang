// Log-softmax backward over rows in place: dx overwrites y (dxIsDy = 0) or dy (dxIsDy = 1) at the same offset and
// stride. a is that buffer, b the other input. See logSoftmaxBackwardRow in softmax_row.slang.
import softmax_row;

struct LogSoftmaxBackwardInPlaceParams
{
    uint rows;
    uint n;
    uint aOff;
    uint lda;
    uint bOff;
    uint ldb;
    uint dxIsDy;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> a;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> b;
[[vk::push_constant]] ConstantBuffer<LogSoftmaxBackwardInPlaceParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void logSoftmaxBackwardInPlace(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return;
    }
    uint aBase = p.aOff + row * p.lda;
    uint bBase = p.bOff + row * p.ldb;
    if (p.dxIsDy != 0)
    {
        logSoftmaxBackwardRow(b, bBase, a, aBase, a, aBase, p.n, local.x);
    }
    else
    {
        logSoftmaxBackwardRow(a, aBase, b, bBase, a, aBase, p.n, local.x);
    }
}
