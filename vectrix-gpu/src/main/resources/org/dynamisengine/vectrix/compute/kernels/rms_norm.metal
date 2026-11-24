#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
float combineLanes_0(float a0_0, float a1_0, float a2_0, float a3_0, float a4_0, float a5_0, float a6_0, float a7_0)
{
    return a0_0 + a4_0 + (a2_0 + a6_0) + (a1_0 + a5_0 + (a3_0 + a7_0));
}

struct SharedBlocks_0
{
    uint next_0;
};

struct RmsNormParams_0
{
    uint rows_0;
    uint n_0;
    uint xOff_0;
    uint ldx_0;
    uint gOff_0;
    uint yOff_0;
    uint ldy_0;
    uint statsOff_0;
    uint flags_0;
    float eps_0;
    uint gridX_0;
};

struct KernelContext_0
{
    RmsNormParams_0 constant* p_0;
    float device* x_0;
    float device* gamma_0;
    float device* y_0;
    float device* stats_0;
    array<float, int(256)> threadgroup* shared_0;
    array<float, int(1024)> threadgroup* blockSums_0;
    array<float, int(4)> threadgroup* rowResults_0;
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
            float _S1 = SharedBlocks_nextBlock_0(source_0, kernelContext_1);
            ret_1 = _S1;
            sp_1 = top_0;
        }
        else
        {
            if((state_0[top_0]) == 0U)
            {
                state_0[top_0] = 1U;
                count_0[sp_0] = (count_0[top_0] + 1U) / 2U;
                state_0[sp_0] = 0U;
                int _S2 = sp_0 + int(1);
                ret_1 = ret_0;
                sp_1 = _S2;
            }
            else
            {
                if((state_0[top_0]) == 1U)
                {
                    left_0[top_0] = ret_0;
                    state_0[top_0] = 2U;
                    count_0[sp_0] = count_0[top_0] / 2U;
                    state_0[sp_0] = 0U;
                    int _S3 = sp_0 + int(1);
                    ret_1 = ret_0;
                    sp_1 = _S3;
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

struct RowDot_0
{
    uint aBase_0;
    uint bBase_0;
};

float RowDot_accumulate_0(const RowDot_0 thread* this_1, float device* this_a_0, float device* this_b_0, float acc_0, uint j_0)
{
    return fma(*(this_a_0+(this_1->aBase_0 + j_0)), *(this_b_0+(this_1->bBase_0 + j_0)), acc_0);
}

float groupSum_0(const RowDot_0 thread* f_0, float device* f_a_0, float device* f_b_0, uint n_1, uint t_0, uint slot_0, KernelContext_0 thread* kernelContext_2)
{
    float acc_1;
    uint blocks_1 = (n_1 + 256U - 1U) / 256U;
    uint _S4 = t_0 % 8U;
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
        uint b_0 = b0_0 + t_0 / 8U;
        bool _S5 = b_0 < blocks_1;
        if(_S5)
        {
            uint base_0 = b_0 * 256U;
            uint _S6 = min(256U, n_1 - base_0);
            acc_1 = 0.0;
            uint j_1 = _S4;
            for(;;)
            {
                if(j_1 < _S6)
                {
                }
                else
                {
                    break;
                }
                float _S7 = RowDot_accumulate_0(f_0, f_a_0, f_b_0, acc_1, base_0 + j_1);
                uint j_2 = j_1 + 8U;
                acc_1 = _S7;
                j_1 = j_2;
            }
        }
        else
        {
            acc_1 = 0.0;
        }
        (*kernelContext_2->shared_0)[t_0] = acc_1;
        threadgroup_barrier(mem_flags::mem_threadgroup);
        bool _S8;
        if(_S4 == 0U)
        {
            _S8 = _S5;
        }
        else
        {
            _S8 = false;
        }
        if(_S8)
        {
            (*kernelContext_2->blockSums_0)[b_0] = combineLanes_0((*kernelContext_2->shared_0)[t_0], (*kernelContext_2->shared_0)[t_0 + 1U], (*kernelContext_2->shared_0)[t_0 + 2U], (*kernelContext_2->shared_0)[t_0 + 3U], (*kernelContext_2->shared_0)[t_0 + 4U], (*kernelContext_2->shared_0)[t_0 + 5U], (*kernelContext_2->shared_0)[t_0 + 6U], (*kernelContext_2->shared_0)[t_0 + 7U]);
        }
        threadgroup_barrier(mem_flags::mem_threadgroup);
        b0_0 = b0_0 + 32U;
    }
    if(t_0 == 0U)
    {
        if(blocks_1 > 0U)
        {
            thread SharedBlocks_0 source_1;
            (&source_1)->next_0 = 0U;
            float _S9 = pairwiseBlocks_0(&source_1, blocks_1, kernelContext_2);
            acc_1 = _S9;
        }
        else
        {
            acc_1 = 0.0;
        }
        (*kernelContext_2->rowResults_0)[slot_0] = acc_1;
    }
    threadgroup_barrier(mem_flags::mem_threadgroup);
    return (*kernelContext_2->rowResults_0)[slot_0];
}

float optional_0(float device* v_1, uint base_1, bool present_0, uint j_3, float absent_0)
{
    uint _S10;
    if(present_0)
    {
        _S10 = base_1 + j_3;
    }
    else
    {
        _S10 = 0U;
    }
    float value_0 = *(v_1+_S10);
    float _S11;
    if(present_0)
    {
        _S11 = value_0;
    }
    else
    {
        _S11 = absent_0;
    }
    return _S11;
}

void rmsNormRow_0(float device* x_1, uint xBase_0, float device* gamma_1, uint gBase_0, bool hasGamma_0, float eps_1, float device* y_1, uint yBase_0, uint n_2, float device* stats_1, uint sBase_0, bool hasStats_0, uint t_1, KernelContext_0 thread* kernelContext_3)
{
    thread RowDot_0 q_0;
    (&q_0)->aBase_0 = xBase_0;
    (&q_0)->bBase_0 = xBase_0;
    thread RowDot_0 _S12 = q_0;
    float _S13 = groupSum_0(&_S12, x_1, x_1, n_2, t_1, 0U, kernelContext_3);
    float rinv_0 = 1.0 / sqrt(_S13 / float(n_2) + eps_1);
    uint i_0 = t_1;
    for(;;)
    {
        if(i_0 < n_2)
        {
        }
        else
        {
            break;
        }
        float g_0 = optional_0(gamma_1, gBase_0, hasGamma_0, i_0, 1.0);
        *(y_1+(yBase_0 + i_0)) = *(x_1+(xBase_0 + i_0)) * rinv_0 * g_0;
        i_0 = i_0 + 256U;
    }
    bool _S14;
    if(hasStats_0)
    {
        _S14 = t_1 == 0U;
    }
    else
    {
        _S14 = false;
    }
    if(_S14)
    {
        *(stats_1+sBase_0) = rinv_0;
    }
    return;
}

[[kernel]] void rmsNorm(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], RmsNormParams_0 constant* p_1 [[buffer(4)]], float device* x_2 [[buffer(0)]], float device* gamma_2 [[buffer(1)]], float device* y_2 [[buffer(2)]], float device* stats_2 [[buffer(3)]])
{
    thread KernelContext_0 kernelContext_4;
    (&kernelContext_4)->p_0 = p_1;
    (&kernelContext_4)->x_0 = x_2;
    (&kernelContext_4)->gamma_0 = gamma_2;
    (&kernelContext_4)->y_0 = y_2;
    (&kernelContext_4)->stats_0 = stats_2;
    threadgroup array<float, int(256)> shared_1;
    (&kernelContext_4)->shared_0 = &shared_1;
    threadgroup array<float, int(1024)> blockSums_1;
    (&kernelContext_4)->blockSums_0 = &blockSums_1;
    threadgroup array<float, int(4)> rowResults_1;
    (&kernelContext_4)->rowResults_0 = &rowResults_1;
    uint row_0 = group_0.y * p_1->gridX_0 + group_0.x;
    if(row_0 >= (p_1->rows_0))
    {
        return;
    }
    rmsNormRow_0((&kernelContext_4)->x_0, (&kernelContext_4)->p_0->xOff_0 + row_0 * (&kernelContext_4)->p_0->ldx_0, (&kernelContext_4)->gamma_0, (&kernelContext_4)->p_0->gOff_0, (((&kernelContext_4)->p_0->flags_0) & 1U) != 0U, (&kernelContext_4)->p_0->eps_0, (&kernelContext_4)->y_0, (&kernelContext_4)->p_0->yOff_0 + row_0 * (&kernelContext_4)->p_0->ldy_0, (&kernelContext_4)->p_0->n_0, (&kernelContext_4)->stats_0, (&kernelContext_4)->p_0->statsOff_0 + row_0, (((&kernelContext_4)->p_0->flags_0) & 4U) != 0U, local_0.x, &kernelContext_4);
    return;
}

