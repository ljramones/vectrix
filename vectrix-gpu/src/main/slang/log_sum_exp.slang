// Log-sum-exp of rows: out[outOff + r] = Softmax.logSumExp(row r of x); -inf for empty rows. See logSumExpRow in
// softmax_row.slang.
import softmax_row;

struct LogSumExpParams
{
    uint rows;
    uint n;
    uint xOff;
    uint ldx;
    uint outOff;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> out;
[[vk::push_constant]] ConstantBuffer<LogSumExpParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void logSumExp(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return;
    }
    float v = logSumExpRow(x, p.xOff + row * p.ldx, p.n, local.x);
    if (local.x == 0)
    {
        out[p.outOff + row] = v;
    }
}
