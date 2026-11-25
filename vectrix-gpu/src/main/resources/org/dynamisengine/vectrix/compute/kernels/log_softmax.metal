#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
float jmax_0(float a_0, float b_0)
{
    if(a_0 != a_0)
    {
        return a_0;
    }
    bool _S1;
    if(a_0 == 0.0)
    {
        _S1 = b_0 == 0.0;
    }
    else
    {
        _S1 = false;
    }
    if(_S1)
    {
        _S1 = (as_type<uint>((a_0))) == 2147483648U;
    }
    else
    {
        _S1 = false;
    }
    if(_S1)
    {
        return b_0;
    }
    float _S2;
    if(a_0 >= b_0)
    {
        _S2 = a_0;
    }
    else
    {
        _S2 = b_0;
    }
    return _S2;
}

struct LogSoftmaxParams_0
{
    uint rows_0;
    uint n_0;
    uint xOff_0;
    uint ldx_0;
    uint yOff_0;
    uint ldy_0;
    uint gridX_0;
};

struct KernelContext_0
{
    LogSoftmaxParams_0 constant* p_0;
    float device* x_0;
    float device* y_0;
    array<float, int(256)> threadgroup* shared_0;
    float threadgroup* rowMax_0;
    array<float, int(1024)> threadgroup* blockSums_0;
    array<float, int(4)> threadgroup* rowResults_0;
};

float groupRowMax_0(float device* x_1, uint xBase_0, uint n_1, uint t_0, KernelContext_0 thread* kernelContext_0)
{
    float mx_0 = (as_type<float>((4286578688U)));
    uint i_0 = t_0;
    for(;;)
    {
        if(i_0 < n_1)
        {
        }
        else
        {
            break;
        }
        float _S3 = jmax_0(mx_0, *(x_1+(xBase_0 + i_0)));
        uint i_1 = i_0 + 256U;
        mx_0 = _S3;
        i_0 = i_1;
    }
    (*kernelContext_0->shared_0)[t_0] = mx_0;
    threadgroup_barrier(mem_flags::mem_threadgroup);
    uint stride_0 = 128U;
    for(;;)
    {
        if(stride_0 > 0U)
        {
        }
        else
        {
            break;
        }
        if(t_0 < stride_0)
        {
            (*kernelContext_0->shared_0)[t_0] = jmax_0((*kernelContext_0->shared_0)[t_0], (*kernelContext_0->shared_0)[t_0 + stride_0]);
        }
        threadgroup_barrier(mem_flags::mem_threadgroup);
        stride_0 = stride_0 >> 1U;
    }
    if(t_0 == 0U)
    {
        *kernelContext_0->rowMax_0 = (*kernelContext_0->shared_0)[int(0)];
    }
    threadgroup_barrier(mem_flags::mem_threadgroup);
    return *kernelContext_0->rowMax_0;
}

float combineLanes_0(float a0_0, float a1_0, float a2_0, float a3_0, float a4_0, float a5_0, float a6_0, float a7_0)
{
    return a0_0 + a4_0 + (a2_0 + a6_0) + (a1_0 + a5_0 + (a3_0 + a7_0));
}

struct SharedBlocks_0
{
    uint next_0;
};

float SharedBlocks_nextBlock_0(SharedBlocks_0 thread* this_0, KernelContext_0 thread* kernelContext_1)
{
    float v_0 = (*kernelContext_1->blockSums_0)[this_0->next_0];
    this_0->next_0 = this_0->next_0 + 1U;
    return v_0;
}

float pairwiseBlocks_0(SharedBlocks_0 thread* source_0, uint blocks_0, KernelContext_0 thread* kernelContext_2)
{
    thread array<uint, int(26)> count_0;
    count_0[int(0)] = blocks_0;
    thread array<uint, int(26)> state_0;
    state_0[int(0)] = 0U;
    thread array<float, int(26)> left_0;
    left_0[int(0)] = 0.0;
    float ret_0 = 0.0;
    int sp_0 = int(1);
    for(;;)
    {
        if(sp_0 > int(0))
        {
        }
        else
        {
            break;
        }
        int top_0 = sp_0 - int(1);
        float ret_1;
        int sp_1;
        if((count_0[top_0]) == 1U)
        {
            float _S4 = SharedBlocks_nextBlock_0(source_0, kernelContext_2);
            ret_1 = _S4;
            sp_1 = top_0;
        }
        else
        {
            if((state_0[top_0]) == 0U)
            {
                state_0[top_0] = 1U;
                count_0[sp_0] = (count_0[top_0] + 1U) / 2U;
                state_0[sp_0] = 0U;
                int _S5 = sp_0 + int(1);
                ret_1 = ret_0;
                sp_1 = _S5;
            }
            else
            {
                if((state_0[top_0]) == 1U)
                {
                    left_0[top_0] = ret_0;
                    state_0[top_0] = 2U;
                    count_0[sp_0] = count_0[top_0] / 2U;
                    state_0[sp_0] = 0U;
                    int _S6 = sp_0 + int(1);
                    ret_1 = ret_0;
                    sp_1 = _S6;
                }
                else
                {
                    ret_1 = left_0[top_0] + ret_0;
                    sp_1 = top_0;
                }
            }
        }
        ret_0 = ret_1;
        sp_0 = sp_1;
    }
    return ret_0;
}

struct ExpShiftedSum_0
{
    uint xBase_1;
    float m_0;
};

float jmin_0(float a_1, float b_1)
{
    if(a_1 != a_1)
    {
        return a_1;
    }
    bool _S7;
    if(a_1 == 0.0)
    {
        _S7 = b_1 == 0.0;
    }
    else
    {
        _S7 = false;
    }
    if(_S7)
    {
        _S7 = (as_type<uint>((b_1))) == 2147483648U;
    }
    else
    {
        _S7 = false;
    }
    if(_S7)
    {
        return b_1;
    }
    float _S8;
    if(a_1 <= b_1)
    {
        _S8 = a_1;
    }
    else
    {
        _S8 = b_1;
    }
    return _S8;
}

float reproducibleExp_0(float x_2)
{
    float c_0 = jmin_0(jmax_0(x_2, -104.0), 89.0);
    float kf_0 = c_0 * 1.4426950216293335 + 1.2582912e+07 - 1.2582912e+07;
    float r_0 = fma(kf_0, 0.00021219444170129, fma(kf_0, -0.693359375, c_0));
    int k_0 = int(kf_0);
    int k1_0 = k_0 >> 1U;
    return (fma(fma(fma(fma(fma(fma(0.0001984127011383, r_0, 0.00138888892251998), r_0, 0.00833333376795053), r_0, 0.0416666679084301), r_0, 0.1666666716337204), r_0, 0.5), r_0 * r_0, r_0) + 1.0) * (as_type<float>((uint((k1_0 + int(127)) << 23U)))) * (as_type<float>((uint((k_0 - k1_0 + int(127)) << 23U))));
}

float expShifted_0(float xv_0, float m_1)
{
    float a_2 = jmax_0(xv_0, m_1 - 9.99999968028569247e+37);
    float s_0 = a_2 - m_1;
    float bv_0 = s_0 - a_2;
    float e_0 = reproducibleExp_0(s_0);
    return fma(e_0, a_2 - (s_0 - bv_0) + (- bv_0 - m_1), e_0);
}

float ExpShiftedSum_accumulate_0(const ExpShiftedSum_0 thread* this_1, float device* this_x_0, float acc_0, uint j_0)
{
    return acc_0 + expShifted_0(*(this_x_0+(this_1->xBase_1 + j_0)), this_1->m_0);
}

float groupSum_0(const ExpShiftedSum_0 thread* f_0, float device* f_x_0, uint n_2, uint t_1, uint slot_0, KernelContext_0 thread* kernelContext_3)
{
    float acc_1;
    uint blocks_1 = (n_2 + 256U - 1U) / 256U;
    uint _S9 = t_1 % 8U;
    uint b0_0 = 0U;
    for(;;)
    {
        if(b0_0 < blocks_1)
        {
        }
        else
        {
            break;
        }
        uint b_2 = b0_0 + t_1 / 8U;
        bool _S10 = b_2 < blocks_1;
        if(_S10)
        {
            uint base_0 = b_2 * 256U;
            uint _S11 = min(256U, n_2 - base_0);
            acc_1 = 0.0;
            uint j_1 = _S9;
            for(;;)
            {
                if(j_1 < _S11)
                {
                }
                else
                {
                    break;
                }
                float _S12 = ExpShiftedSum_accumulate_0(f_0, f_x_0, acc_1, base_0 + j_1);
                uint j_2 = j_1 + 8U;
                acc_1 = _S12;
                j_1 = j_2;
            }
        }
        else
        {
            acc_1 = 0.0;
        }
        (*kernelContext_3->shared_0)[t_1] = acc_1;
        threadgroup_barrier(mem_flags::mem_threadgroup);
        bool _S13;
        if(_S9 == 0U)
        {
            _S13 = _S10;
        }
        else
        {
            _S13 = false;
        }
        if(_S13)
        {
            (*kernelContext_3->blockSums_0)[b_2] = combineLanes_0((*kernelContext_3->shared_0)[t_1], (*kernelContext_3->shared_0)[t_1 + 1U], (*kernelContext_3->shared_0)[t_1 + 2U], (*kernelContext_3->shared_0)[t_1 + 3U], (*kernelContext_3->shared_0)[t_1 + 4U], (*kernelContext_3->shared_0)[t_1 + 5U], (*kernelContext_3->shared_0)[t_1 + 6U], (*kernelContext_3->shared_0)[t_1 + 7U]);
        }
        threadgroup_barrier(mem_flags::mem_threadgroup);
        b0_0 = b0_0 + 32U;
    }
    if(t_1 == 0U)
    {
        if(blocks_1 > 0U)
        {
            thread SharedBlocks_0 source_1;
            (&source_1)->next_0 = 0U;
            float _S14 = pairwiseBlocks_0(&source_1, blocks_1, kernelContext_3);
            acc_1 = _S14;
        }
        else
        {
            acc_1 = 0.0;
        }
        (*kernelContext_3->rowResults_0)[slot_0] = acc_1;
    }
    threadgroup_barrier(mem_flags::mem_threadgroup);
    return (*kernelContext_3->rowResults_0)[slot_0];
}

float logReduced_0(float f_1, int e_1)
{
    float s_1 = f_1 / (2.0 + f_1);
    float z_0 = s_1 * s_1;
    float ef_0 = float(e_1);
    return fma(ef_0, 0.693359375, fma(ef_0, -0.00021219444170129, 2.0 * fma(s_1, z_0 * fma(fma(fma(0.1111111119389534, z_0, 0.1428571492433548), z_0, 0.20000000298023224), z_0, 0.3333333432674408), s_1)));
}

float reproducibleLog_0(float x_3)
{
    bool _S15;
    if(!(x_3 > 0.0))
    {
        _S15 = true;
    }
    else
    {
        _S15 = x_3 == (as_type<float>((2139095040U)));
    }
    float m_2;
    if(_S15)
    {
        if(x_3 == 0.0)
        {
            m_2 = (as_type<float>((4286578688U)));
        }
        else
        {
            if(x_3 == (as_type<float>((2139095040U))))
            {
                m_2 = x_3;
            }
            else
            {
                m_2 = (as_type<float>((2143289344U)));
            }
        }
        return m_2;
    }
    int sub_0;
    if(x_3 < (as_type<float>((8388608U))))
    {
        float _S16 = x_3 * 8.388608e+06;
        sub_0 = int(23);
        m_2 = _S16;
    }
    else
    {
        sub_0 = int(0);
        m_2 = x_3;
    }
    int bits_0 = (as_type<int>((m_2)));
    int e_2 = (bits_0 >> 23U) - int(127) - sub_0;
    float m_3 = (as_type<float>((uint((bits_0 & int(8388607)) | int(1065353216)))));
    int e_3;
    if(m_3 > 1.41421353816986084)
    {
        int e_4 = e_2 + int(1);
        m_2 = m_3 * 0.5;
        e_3 = e_4;
    }
    else
    {
        m_2 = m_3;
        e_3 = e_2;
    }
    return logReduced_0(m_2 - 1.0, e_3);
}

void logSoftmaxRow_0(float device* x_4, uint xBase_2, float device* y_1, uint yBase_0, uint n_3, uint t_2, KernelContext_0 thread* kernelContext_4)
{
    float _S17 = groupRowMax_0(x_4, xBase_2, n_3, t_2, kernelContext_4);
    uint i_2;
    if(_S17 == (as_type<float>((4286578688U))))
    {
        i_2 = t_2;
        for(;;)
        {
            if(i_2 < n_3)
            {
            }
            else
            {
                break;
            }
            *(y_1+(yBase_0 + i_2)) = _S17;
            i_2 = i_2 + 256U;
        }
        return;
    }
    thread ExpShiftedSum_0 f_2;
    (&f_2)->xBase_1 = xBase_2;
    (&f_2)->m_0 = _S17;
    thread ExpShiftedSum_0 _S18 = f_2;
    float _S19 = groupSum_0(&_S18, x_4, n_3, t_2, 0U, kernelContext_4);
    float _S20 = reproducibleLog_0(_S19);
    i_2 = t_2;
    for(;;)
    {
        if(i_2 < n_3)
        {
        }
        else
        {
            break;
        }
        *(y_1+(yBase_0 + i_2)) = *(x_4+(xBase_2 + i_2)) - _S17 - _S20;
        i_2 = i_2 + 256U;
    }
    return;
}

[[kernel]] void logSoftmax(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], LogSoftmaxParams_0 constant* p_1 [[buffer(2)]], float device* x_5 [[buffer(0)]], float device* y_2 [[buffer(1)]])
{
    thread KernelContext_0 kernelContext_5;
    (&kernelContext_5)->p_0 = p_1;
    (&kernelContext_5)->x_0 = x_5;
    (&kernelContext_5)->y_0 = y_2;
    threadgroup array<float, int(256)> shared_1;
    (&kernelContext_5)->shared_0 = &shared_1;
    threadgroup float rowMax_1;
    (&kernelContext_5)->rowMax_0 = &rowMax_1;
    threadgroup array<float, int(1024)> blockSums_1;
    (&kernelContext_5)->blockSums_0 = &blockSums_1;
    threadgroup array<float, int(4)> rowResults_1;
    (&kernelContext_5)->rowResults_0 = &rowResults_1;
    uint row_0 = group_0.y * p_1->gridX_0 + group_0.x;
    if(row_0 >= (p_1->rows_0))
    {
        return;
    }
    logSoftmaxRow_0((&kernelContext_5)->x_0, (&kernelContext_5)->p_0->xOff_0 + row_0 * (&kernelContext_5)->p_0->ldx_0, (&kernelContext_5)->y_0, (&kernelContext_5)->p_0->yOff_0 + row_0 * (&kernelContext_5)->p_0->ldy_0, (&kernelContext_5)->p_0->n_0, local_0.x, &kernelContext_5);
    return;
}

