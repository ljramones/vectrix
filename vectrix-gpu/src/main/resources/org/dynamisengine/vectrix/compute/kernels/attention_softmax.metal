#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
uint keysFor_0(uint r_0, uint queries_0, uint t_0, uint causal_0)
{
    uint _S1;
    if(causal_0 != 0U)
    {
        _S1 = t_0 - queries_0 + 1U + r_0;
    }
    else
    {
        _S1 = t_0;
    }
    return _S1;
}

float jmax_0(float a_0, float b_0)
{
    if(a_0 != a_0)
    {
        return a_0;
    }
    bool _S2;
    if(a_0 == 0.0)
    {
        _S2 = b_0 == 0.0;
    }
    else
    {
        _S2 = false;
    }
    if(_S2)
    {
        _S2 = (as_type<uint>((a_0))) == 2147483648U;
    }
    else
    {
        _S2 = false;
    }
    if(_S2)
    {
        return b_0;
    }
    float _S3;
    if(a_0 >= b_0)
    {
        _S3 = a_0;
    }
    else
    {
        _S3 = b_0;
    }
    return _S3;
}

float jmin_0(float a_1, float b_1)
{
    if(a_1 != a_1)
    {
        return a_1;
    }
    bool _S4;
    if(a_1 == 0.0)
    {
        _S4 = b_1 == 0.0;
    }
    else
    {
        _S4 = false;
    }
    if(_S4)
    {
        _S4 = (as_type<uint>((b_1))) == 2147483648U;
    }
    else
    {
        _S4 = false;
    }
    if(_S4)
    {
        return b_1;
    }
    float _S5;
    if(a_1 <= b_1)
    {
        _S5 = a_1;
    }
    else
    {
        _S5 = b_1;
    }
    return _S5;
}

float reproducibleExp_0(float x_0)
{
    float c_0 = jmin_0(jmax_0(x_0, -104.0), 89.0);
    float kf_0 = c_0 * 1.4426950216293335 + 1.2582912e+07 - 1.2582912e+07;
    float r_1 = fma(kf_0, 0.00021219444170129, fma(kf_0, -0.693359375, c_0));
    int k_0 = int(kf_0);
    int k1_0 = k_0 >> 1U;
    return (fma(fma(fma(fma(fma(fma(0.0001984127011383, r_1, 0.00138888892251998), r_1, 0.00833333376795053), r_1, 0.0416666679084301), r_1, 0.1666666716337204), r_1, 0.5), r_1 * r_1, r_1) + 1.0) * (as_type<float>((uint((k1_0 + int(127)) << 23U)))) * (as_type<float>((uint((k_0 - k1_0 + int(127)) << 23U))));
}

float attentionExpShifted_0(float xv_0, float m_0)
{
    float a_2 = jmax_0(xv_0, m_0 - 9.99999968028569247e+37);
    float s_0 = a_2 - m_0;
    float bv_0 = s_0 - a_2;
    float e_0 = reproducibleExp_0(s_0);
    return fma(e_0, a_2 - (s_0 - bv_0) + (- bv_0 - m_0), e_0);
}

float combineLanes_0(float a0_0, float a1_0, float a2_0, float a3_0, float a4_0, float a5_0, float a6_0, float a7_0)
{
    return a0_0 + a4_0 + (a2_0 + a6_0) + (a1_0 + a5_0 + (a3_0 + a7_0));
}

struct SharedBlocks_0
{
    uint next_0;
};

struct SoftmaxRowsParams_0
{
    uint rows_0;
    uint heads_0;
    uint queries_1;
    uint t_1;
    uint causal_1;
    uint pOff_0;
    uint ldp_0;
    uint gridX_0;
};

struct KernelContext_0
{
    SoftmaxRowsParams_0 constant* p_0;
    float device* probs_0;
    array<float, int(256)> threadgroup* shared_0;
    float threadgroup* rowMax_0;
    array<float, int(1024)> threadgroup* blockSums_0;
    float threadgroup* total_0;
};

float SharedBlocks_nextBlock_0(SharedBlocks_0 thread* this_0, KernelContext_0 thread* kernelContext_0)
{
    float v_0 = (*kernelContext_0->blockSums_0)[this_0->next_0];
    this_0->next_0 = this_0->next_0 + 1U;
    return v_0;
}

float pairwiseBlocks_0(SharedBlocks_0 thread* source_0, uint blocks_0, KernelContext_0 thread* kernelContext_1)
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
            float _S6 = SharedBlocks_nextBlock_0(source_0, kernelContext_1);
            ret_1 = _S6;
            sp_1 = top_0;
        }
        else
        {
            if((state_0[top_0]) == 0U)
            {
                state_0[top_0] = 1U;
                count_0[sp_0] = (count_0[top_0] + 1U) / 2U;
                state_0[sp_0] = 0U;
                int _S7 = sp_0 + int(1);
                ret_1 = ret_0;
                sp_1 = _S7;
            }
            else
            {
                if((state_0[top_0]) == 1U)
                {
                    left_0[top_0] = ret_0;
                    state_0[top_0] = 2U;
                    count_0[sp_0] = count_0[top_0] / 2U;
                    state_0[sp_0] = 0U;
                    int _S8 = sp_0 + int(1);
                    ret_1 = ret_0;
                    sp_1 = _S8;
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

[[kernel]] void attentionSoftmax(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], SoftmaxRowsParams_0 constant* p_1 [[buffer(1)]], float device* probs_1 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_2;
    (&kernelContext_2)->p_0 = p_1;
    (&kernelContext_2)->probs_0 = probs_1;
    threadgroup array<float, int(256)> shared_1;
    (&kernelContext_2)->shared_0 = &shared_1;
    threadgroup float rowMax_1;
    (&kernelContext_2)->rowMax_0 = &rowMax_1;
    threadgroup array<float, int(1024)> blockSums_1;
    (&kernelContext_2)->blockSums_0 = &blockSums_1;
    threadgroup float total_1;
    (&kernelContext_2)->total_0 = &total_1;
    uint row_0 = group_0.y * p_1->gridX_0 + group_0.x;
    if(row_0 >= (p_1->rows_0))
    {
        return;
    }
    uint t_2 = local_0.x;
    uint _S9 = row_0 / (&kernelContext_2)->p_0->heads_0;
    uint n_0 = keysFor_0(_S9, (&kernelContext_2)->p_0->queries_1, (&kernelContext_2)->p_0->t_1, (&kernelContext_2)->p_0->causal_1);
    uint _S10 = (&kernelContext_2)->p_0->pOff_0 + row_0 * (&kernelContext_2)->p_0->ldp_0;
    float _S11 = (as_type<float>((4286578688U)));
    float mx_0 = _S11;
    uint i_0 = t_2;
    for(;;)
    {
        if(i_0 < n_0)
        {
        }
        else
        {
            break;
        }
        float _S12 = jmax_0(mx_0, *((&kernelContext_2)->probs_0+(_S10 + i_0)));
        uint i_1 = i_0 + 256U;
        mx_0 = _S12;
        i_0 = i_1;
    }
    (*(&kernelContext_2)->shared_0)[t_2] = mx_0;
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
        if(t_2 < stride_0)
        {
            (*(&kernelContext_2)->shared_0)[t_2] = jmax_0((*(&kernelContext_2)->shared_0)[t_2], (*(&kernelContext_2)->shared_0)[t_2 + stride_0]);
        }
        threadgroup_barrier(mem_flags::mem_threadgroup);
        stride_0 = stride_0 >> 1U;
    }
    bool _S13 = t_2 == 0U;
    if(_S13)
    {
        *(&kernelContext_2)->rowMax_0 = (*(&kernelContext_2)->shared_0)[int(0)];
    }
    threadgroup_barrier(mem_flags::mem_threadgroup);
    float threadgroup* _S14 = (&kernelContext_2)->rowMax_0;
    float m_1 = *_S14;
    if((*_S14) == _S11)
    {
        i_0 = t_2;
        for(;;)
        {
            if(i_0 < n_0)
            {
            }
            else
            {
                break;
            }
            *((&kernelContext_2)->probs_0+(_S10 + i_0)) = 0.0;
            i_0 = i_0 + 256U;
        }
        return;
    }
    uint j_0;
    uint blocks_1 = (n_0 + 256U - 1U) / 256U;
    uint _S15 = t_2 % 8U;
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
        uint b_2 = b0_0 + t_2 / 8U;
        bool _S16 = b_2 < blocks_1;
        float acc_0;
        if(_S16)
        {
            uint start_0 = b_2 * 256U;
            uint _S17 = min(256U, n_0 - start_0);
            acc_0 = 0.0;
            j_0 = _S15;
            for(;;)
            {
                if(j_0 < _S17)
                {
                }
                else
                {
                    break;
                }
                uint _S18 = _S10 + start_0 + j_0;
                float e_1 = attentionExpShifted_0(*((&kernelContext_2)->probs_0+_S18), m_1);
                *((&kernelContext_2)->probs_0+_S18) = e_1;
                float _S19 = acc_0 + e_1;
                uint j_1 = j_0 + 8U;
                acc_0 = _S19;
                j_0 = j_1;
            }
        }
        else
        {
            acc_0 = 0.0;
        }
        (*(&kernelContext_2)->shared_0)[t_2] = acc_0;
        threadgroup_barrier(mem_flags::mem_threadgroup);
        bool _S20;
        if(_S15 == 0U)
        {
            _S20 = _S16;
        }
        else
        {
            _S20 = false;
        }
        if(_S20)
        {
            (*(&kernelContext_2)->blockSums_0)[b_2] = combineLanes_0((*(&kernelContext_2)->shared_0)[t_2], (*(&kernelContext_2)->shared_0)[t_2 + 1U], (*(&kernelContext_2)->shared_0)[t_2 + 2U], (*(&kernelContext_2)->shared_0)[t_2 + 3U], (*(&kernelContext_2)->shared_0)[t_2 + 4U], (*(&kernelContext_2)->shared_0)[t_2 + 5U], (*(&kernelContext_2)->shared_0)[t_2 + 6U], (*(&kernelContext_2)->shared_0)[t_2 + 7U]);
        }
        threadgroup_barrier(mem_flags::mem_threadgroup);
        b0_0 = b0_0 + 32U;
    }
    if(_S13)
    {
        thread SharedBlocks_0 source_1;
        (&source_1)->next_0 = 0U;
        float _S21 = pairwiseBlocks_0(&source_1, blocks_1, &kernelContext_2);
        *(&kernelContext_2)->total_0 = _S21;
    }
    threadgroup_barrier(mem_flags::mem_threadgroup);
    float _S22 = *(&kernelContext_2)->total_0;
    b0_0 = 0U;
    for(;;)
    {
        if(b0_0 < blocks_1)
        {
        }
        else
        {
            break;
        }
        uint b_3 = b0_0 + t_2 / 8U;
        if(b_3 < blocks_1)
        {
            uint start_1 = b_3 * 256U;
            uint _S23 = min(256U, n_0 - start_1);
            j_0 = _S15;
            for(;;)
            {
                if(j_0 < _S23)
                {
                }
                else
                {
                    break;
                }
                uint _S24 = _S10 + start_1 + j_0;
                *((&kernelContext_2)->probs_0+_S24) = *((&kernelContext_2)->probs_0+_S24) / _S22;
                j_0 = j_0 + 8U;
            }
        }
        b0_0 = b0_0 + 32U;
    }
    return;
}

