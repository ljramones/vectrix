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

float jmin_0(float a_1, float b_1)
{
    if(a_1 != a_1)
    {
        return a_1;
    }
    bool _S3;
    if(a_1 == 0.0)
    {
        _S3 = b_1 == 0.0;
    }
    else
    {
        _S3 = false;
    }
    if(_S3)
    {
        _S3 = (as_type<uint>((b_1))) == 2147483648U;
    }
    else
    {
        _S3 = false;
    }
    if(_S3)
    {
        return b_1;
    }
    float _S4;
    if(a_1 <= b_1)
    {
        _S4 = a_1;
    }
    else
    {
        _S4 = b_1;
    }
    return _S4;
}

float reproducibleExp_0(float x_0)
{
    float c_0 = jmin_0(jmax_0(x_0, -104.0), 89.0);
    float kf_0 = c_0 * 1.4426950216293335 + 1.2582912e+07 - 1.2582912e+07;
    float r_0 = fma(kf_0, 0.00021219444170129, fma(kf_0, -0.693359375, c_0));
    int k_0 = int(kf_0);
    int k1_0 = k_0 >> 1U;
    return (fma(fma(fma(fma(fma(fma(0.0001984127011383, r_0, 0.00138888892251998), r_0, 0.00833333376795053), r_0, 0.0416666679084301), r_0, 0.1666666716337204), r_0, 0.5), r_0 * r_0, r_0) + 1.0) * (as_type<float>((uint((k1_0 + int(127)) << 23U)))) * (as_type<float>((uint((k_0 - k1_0 + int(127)) << 23U))));
}

float expShifted_0(float xv_0, float m_0)
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

struct SoftmaxInPlaceParams_0
{
    uint rows_0;
    uint n_0;
    uint xOff_0;
    uint ldx_0;
    uint gridX_0;
};

struct KernelContext_0
{
    SoftmaxInPlaceParams_0 constant* p_0;
    float device* x_1;
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
            float _S5 = SharedBlocks_nextBlock_0(source_0, kernelContext_1);
            ret_1 = _S5;
            sp_1 = top_0;
        }
        else
        {
            if((state_0[top_0]) == 0U)
            {
                state_0[top_0] = 1U;
                count_0[sp_0] = (count_0[top_0] + 1U) / 2U;
                state_0[sp_0] = 0U;
                int _S6 = sp_0 + int(1);
                ret_1 = ret_0;
                sp_1 = _S6;
            }
            else
            {
                if((state_0[top_0]) == 1U)
                {
                    left_0[top_0] = ret_0;
                    state_0[top_0] = 2U;
                    count_0[sp_0] = count_0[top_0] / 2U;
                    state_0[sp_0] = 0U;
                    int _S7 = sp_0 + int(1);
                    ret_1 = ret_0;
                    sp_1 = _S7;
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

void softmaxRow_0(float device* x_2, uint xBase_0, float device* y_0, uint yBase_0, uint n_1, uint t_0, KernelContext_0 thread* kernelContext_2)
{
    float _S8 = (as_type<float>((4286578688U)));
    float mx_0 = _S8;
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
        float _S9 = jmax_0(mx_0, *(x_2+(xBase_0 + i_0)));
        uint i_1 = i_0 + 256U;
        mx_0 = _S9;
        i_0 = i_1;
    }
    (*kernelContext_2->shared_0)[t_0] = mx_0;
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
            (*kernelContext_2->shared_0)[t_0] = jmax_0((*kernelContext_2->shared_0)[t_0], (*kernelContext_2->shared_0)[t_0 + stride_0]);
        }
        threadgroup_barrier(mem_flags::mem_threadgroup);
        stride_0 = stride_0 >> 1U;
    }
    bool _S10 = t_0 == 0U;
    if(_S10)
    {
        *kernelContext_2->rowMax_0 = (*kernelContext_2->shared_0)[int(0)];
    }
    threadgroup_barrier(mem_flags::mem_threadgroup);
    float threadgroup* _S11 = kernelContext_2->rowMax_0;
    float m_1 = *_S11;
    if((*_S11) == _S8)
    {
        i_0 = t_0;
        for(;;)
        {
            if(i_0 < n_1)
            {
            }
            else
            {
                break;
            }
            *(y_0+(yBase_0 + i_0)) = 0.0;
            i_0 = i_0 + 256U;
        }
        return;
    }
    uint j_0;
    uint blocks_1 = (n_1 + 256U - 1U) / 256U;
    uint _S12 = t_0 % 8U;
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
        uint b_2 = b0_0 + t_0 / 8U;
        bool _S13 = b_2 < blocks_1;
        float acc_0;
        if(_S13)
        {
            uint base_0 = b_2 * 256U;
            uint _S14 = min(256U, n_1 - base_0);
            acc_0 = 0.0;
            j_0 = _S12;
            for(;;)
            {
                if(j_0 < _S14)
                {
                }
                else
                {
                    break;
                }
                float e_1 = expShifted_0(*(x_2+(xBase_0 + base_0 + j_0)), m_1);
                *(y_0+(yBase_0 + base_0 + j_0)) = e_1;
                float _S15 = acc_0 + e_1;
                uint j_1 = j_0 + 8U;
                acc_0 = _S15;
                j_0 = j_1;
            }
        }
        else
        {
            acc_0 = 0.0;
        }
        (*kernelContext_2->shared_0)[t_0] = acc_0;
        threadgroup_barrier(mem_flags::mem_threadgroup);
        bool _S16;
        if(_S12 == 0U)
        {
            _S16 = _S13;
        }
        else
        {
            _S16 = false;
        }
        if(_S16)
        {
            (*kernelContext_2->blockSums_0)[b_2] = combineLanes_0((*kernelContext_2->shared_0)[t_0], (*kernelContext_2->shared_0)[t_0 + 1U], (*kernelContext_2->shared_0)[t_0 + 2U], (*kernelContext_2->shared_0)[t_0 + 3U], (*kernelContext_2->shared_0)[t_0 + 4U], (*kernelContext_2->shared_0)[t_0 + 5U], (*kernelContext_2->shared_0)[t_0 + 6U], (*kernelContext_2->shared_0)[t_0 + 7U]);
        }
        threadgroup_barrier(mem_flags::mem_threadgroup);
        b0_0 = b0_0 + 32U;
    }
    if(_S10)
    {
        thread SharedBlocks_0 source_1;
        (&source_1)->next_0 = 0U;
        float _S17 = pairwiseBlocks_0(&source_1, blocks_1, kernelContext_2);
        *kernelContext_2->total_0 = _S17;
    }
    threadgroup_barrier(mem_flags::mem_threadgroup);
    float _S18 = *kernelContext_2->total_0;
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
        uint b_3 = b0_0 + t_0 / 8U;
        if(b_3 < blocks_1)
        {
            uint base_1 = b_3 * 256U;
            uint _S19 = min(256U, n_1 - base_1);
            j_0 = _S12;
            for(;;)
            {
                if(j_0 < _S19)
                {
                }
                else
                {
                    break;
                }
                uint _S20 = yBase_0 + base_1 + j_0;
                *(y_0+_S20) = *(y_0+_S20) / _S18;
                j_0 = j_0 + 8U;
            }
        }
        b0_0 = b0_0 + 32U;
    }
    return;
}

[[kernel]] void softmaxInPlace(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], SoftmaxInPlaceParams_0 constant* p_1 [[buffer(1)]], float device* x_3 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_3;
    (&kernelContext_3)->p_0 = p_1;
    (&kernelContext_3)->x_1 = x_3;
    threadgroup array<float, int(256)> shared_1;
    (&kernelContext_3)->shared_0 = &shared_1;
    threadgroup float rowMax_1;
    (&kernelContext_3)->rowMax_0 = &rowMax_1;
    threadgroup array<float, int(1024)> blockSums_1;
    (&kernelContext_3)->blockSums_0 = &blockSums_1;
    threadgroup float total_1;
    (&kernelContext_3)->total_0 = &total_1;
    uint row_0 = group_0.y * p_1->gridX_0 + group_0.x;
    if(row_0 >= (p_1->rows_0))
    {
        return;
    }
    uint base_2 = (&kernelContext_3)->p_0->xOff_0 + row_0 * (&kernelContext_3)->p_0->ldx_0;
    softmaxRow_0((&kernelContext_3)->x_1, base_2, (&kernelContext_3)->x_1, base_2, (&kernelContext_3)->p_0->n_0, local_0.x, &kernelContext_3);
    return;
}

