#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
uint packedByte_0(uint device* buf_0, uint i_0)
{
    return ((*(buf_0+(i_0 >> 2U))) >> ((i_0 & 3U) * 8U)) & 255U;
}

int packedInt8_0(uint device* buf_1, uint i_1)
{
    uint _S1 = packedByte_0(buf_1, i_1);
    return int(_S1 << 24U) >> 24U;
}

float combineLanes_0(float a0_0, float a1_0, float a2_0, float a3_0, float a4_0, float a5_0, float a6_0, float a7_0)
{
    return a0_0 + a4_0 + (a2_0 + a6_0) + (a1_0 + a5_0 + (a3_0 + a7_0));
}

struct GemvInt8Params_0
{
    uint m_0;
    uint n_0;
    uint qOff_0;
    uint sOff_0;
    uint xOff_0;
    uint yOff_0;
    uint gridX_0;
};

struct KernelContext_0
{
    GemvInt8Params_0 constant* p_0;
    uint device* q_0;
    float device* x_0;
    float device* scales_0;
    float device* y_0;
    array<float, int(64)> threadgroup* lanes_0;
};

[[kernel]] void gemvInt8(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], GemvInt8Params_0 constant* p_1 [[buffer(4)]], uint device* q_1 [[buffer(0)]], float device* x_1 [[buffer(2)]], float device* scales_1 [[buffer(1)]], float device* y_1 [[buffer(3)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->q_0 = q_1;
    (&kernelContext_0)->x_0 = x_1;
    (&kernelContext_0)->scales_0 = scales_1;
    (&kernelContext_0)->y_0 = y_1;
    threadgroup array<float, int(64)> lanes_1;
    (&kernelContext_0)->lanes_0 = &lanes_1;
    uint _S2 = local_0.x;
    uint row_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 8U + _S2 / 8U;
    uint lane_0 = _S2 % 8U;
    float t_0;
    if(row_0 < (p_1->m_0))
    {
        uint blocks_0 = (&kernelContext_0)->p_0->n_0 / 32U;
        uint _S3 = (&kernelContext_0)->p_0->qOff_0 + row_0 * (&kernelContext_0)->p_0->n_0;
        uint _S4 = (&kernelContext_0)->p_0->sOff_0 + row_0 * blocks_0;
        t_0 = 0.0;
        uint b_0 = 0U;
        for(;;)
        {
            if(b_0 < blocks_0)
            {
            }
            else
            {
                break;
            }
            uint _S5 = b_0 * 32U;
            float a_0 = 0.0;
            uint j_0 = lane_0;
            for(;;)
            {
                if(j_0 < 32U)
                {
                }
                else
                {
                    break;
                }
                int _S6 = packedInt8_0((&kernelContext_0)->q_0, _S3 + _S5 + j_0);
                float _S7 = fma(float(_S6), *((&kernelContext_0)->x_0+((&kernelContext_0)->p_0->xOff_0 + _S5 + j_0)), a_0);
                uint j_1 = j_0 + 8U;
                a_0 = _S7;
                j_0 = j_1;
            }
            float _S8 = fma(*((&kernelContext_0)->scales_0+(_S4 + b_0)), a_0, t_0);
            uint _S9 = b_0 + 1U;
            t_0 = _S8;
            b_0 = _S9;
        }
    }
    else
    {
        t_0 = 0.0;
    }
    (*(&kernelContext_0)->lanes_0)[_S2] = t_0;
    threadgroup_barrier(mem_flags::mem_threadgroup);
    bool _S10;
    if(lane_0 == 0U)
    {
        _S10 = row_0 < (p_1->m_0);
    }
    else
    {
        _S10 = false;
    }
    if(_S10)
    {
        *((&kernelContext_0)->y_0+((&kernelContext_0)->p_0->yOff_0 + row_0)) = combineLanes_0((*(&kernelContext_0)->lanes_0)[_S2], (*(&kernelContext_0)->lanes_0)[_S2 + 1U], (*(&kernelContext_0)->lanes_0)[_S2 + 2U], (*(&kernelContext_0)->lanes_0)[_S2 + 3U], (*(&kernelContext_0)->lanes_0)[_S2 + 4U], (*(&kernelContext_0)->lanes_0)[_S2 + 5U], (*(&kernelContext_0)->lanes_0)[_S2 + 6U], (*(&kernelContext_0)->lanes_0)[_S2 + 7U]);
    }
    return;
}

