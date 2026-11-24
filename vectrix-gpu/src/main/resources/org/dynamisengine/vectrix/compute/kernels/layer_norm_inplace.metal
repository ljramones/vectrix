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

struct LayerNormInPlaceParams_0
{
    uint rows_0;
    uint n_0;
    uint xOff_0;
    uint ldx_0;
    uint gOff_0;
    uint bOff_0;
    uint statsOff_0;
    uint flags_0;
    float eps_0;
    uint gridX_0;
};

struct KernelContext_0
{
    LayerNormInPlaceParams_0 constant* p_0;
    float device* x_0;
    float device* gamma_0;
    float device* beta_0;
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

struct RowSum_0
{
    uint xBase_0;
};

float RowSum_accumulate_0(const RowSum_0 thread* this_1, float device* this_x_0, float acc_0, uint j_0)
{
    return acc_0 + *(this_x_0+(this_1->xBase_0 + j_0));
}

float groupSum_0(const RowSum_0 thread* f_0, float device* f_x_0, uint n_1, uint t_0, uint slot_0, KernelContext_0 thread* kernelContext_2)
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
                float _S7 = RowSum_accumulate_0(f_0, f_x_0, acc_1, base_0 + j_1);
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

struct CentredSquares_0
{
    uint xBase_1;
    float mean_0;
};

float CentredSquares_accumulate_0(const CentredSquares_0 thread* this_2, float device* this_x_1, float acc_2, uint j_3)
{
    float c_0 = *(this_x_1+(this_2->xBase_1 + j_3)) - this_2->mean_0;
    return fma(c_0, c_0, acc_2);
}

float groupSum_1(const CentredSquares_0 thread* f_1, float device* f_x_1, uint n_2, uint t_1, uint slot_1, KernelContext_0 thread* kernelContext_3)
{
    float acc_3;
    uint blocks_2 = (n_2 + 256U - 1U) / 256U;
    uint _S10 = t_1 % 8U;
    uint b0_1 = 0U;
    for(;;)
    {
        if(b0_1 < blocks_2)
        {
        }
        else
        {
            break;
        }
        uint b_1 = b0_1 + t_1 / 8U;
        bool _S11 = b_1 < blocks_2;
        if(_S11)
        {
            uint base_1 = b_1 * 256U;
            uint _S12 = min(256U, n_2 - base_1);
            acc_3 = 0.0;
            uint j_4 = _S10;
            for(;;)
            {
                if(j_4 < _S12)
                {
                }
                else
                {
                    break;
                }
                float _S13 = CentredSquares_accumulate_0(f_1, f_x_1, acc_3, base_1 + j_4);
                uint j_5 = j_4 + 8U;
                acc_3 = _S13;
                j_4 = j_5;
            }
        }
        else
        {
            acc_3 = 0.0;
        }
        (*kernelContext_3->shared_0)[t_1] = acc_3;
        threadgroup_barrier(mem_flags::mem_threadgroup);
        bool _S14;
        if(_S10 == 0U)
        {
            _S14 = _S11;
        }
        else
        {
            _S14 = false;
        }
        if(_S14)
        {
            (*kernelContext_3->blockSums_0)[b_1] = combineLanes_0((*kernelContext_3->shared_0)[t_1], (*kernelContext_3->shared_0)[t_1 + 1U], (*kernelContext_3->shared_0)[t_1 + 2U], (*kernelContext_3->shared_0)[t_1 + 3U], (*kernelContext_3->shared_0)[t_1 + 4U], (*kernelContext_3->shared_0)[t_1 + 5U], (*kernelContext_3->shared_0)[t_1 + 6U], (*kernelContext_3->shared_0)[t_1 + 7U]);
        }
        threadgroup_barrier(mem_flags::mem_threadgroup);
        b0_1 = b0_1 + 32U;
    }
    if(t_1 == 0U)
    {
        if(blocks_2 > 0U)
        {
            thread SharedBlocks_0 source_2;
            (&source_2)->next_0 = 0U;
            float _S15 = pairwiseBlocks_0(&source_2, blocks_2, kernelContext_3);
            acc_3 = _S15;
        }
        else
        {
            acc_3 = 0.0;
        }
        (*kernelContext_3->rowResults_0)[slot_1] = acc_3;
    }
    threadgroup_barrier(mem_flags::mem_threadgroup);
    return (*kernelContext_3->rowResults_0)[slot_1];
}

float optional_0(float device* v_1, uint base_2, bool present_0, uint j_6, float absent_0)
{
    uint _S16;
    if(present_0)
    {
        _S16 = base_2 + j_6;
    }
    else
    {
        _S16 = 0U;
    }
    float value_0 = *(v_1+_S16);
    float _S17;
    if(present_0)
    {
        _S17 = value_0;
    }
    else
    {
        _S17 = absent_0;
    }
    return _S17;
}

void layerNormRow_0(float device* x_1, uint xBase_2, float device* gamma_1, uint gBase_0, bool hasGamma_0, float device* beta_1, uint bBase_0, bool hasBeta_0, float eps_1, float device* y_0, uint yBase_0, uint n_3, float device* stats_1, uint sBase_0, bool hasStats_0, uint t_2, KernelContext_0 thread* kernelContext_4)
{
    thread RowSum_0 s_0;
    (&s_0)->xBase_0 = xBase_2;
    thread RowSum_0 _S18 = s_0;
    float _S19 = groupSum_0(&_S18, x_1, n_3, t_2, 0U, kernelContext_4);
    float _S20 = float(n_3);
    float mean_1 = _S19 / _S20;
    thread CentredSquares_0 q_0;
    (&q_0)->xBase_1 = xBase_2;
    (&q_0)->mean_0 = mean_1;
    thread CentredSquares_0 _S21 = q_0;
    float _S22 = groupSum_1(&_S21, x_1, n_3, t_2, 1U, kernelContext_4);
    float rstd_0 = 1.0 / sqrt(_S22 / _S20 + eps_1);
    uint i_0 = t_2;
    for(;;)
    {
        if(i_0 < n_3)
        {
        }
        else
        {
            break;
        }
        float v_2 = (*(x_1+(xBase_2 + i_0)) - mean_1) * rstd_0;
        float g_0 = optional_0(gamma_1, gBase_0, hasGamma_0, i_0, 1.0);
        float b_2 = optional_0(beta_1, bBase_0, hasBeta_0, i_0, 0.0);
        *(y_0+(yBase_0 + i_0)) = fma(v_2, g_0, b_2);
        i_0 = i_0 + 256U;
    }
    bool _S23;
    if(hasStats_0)
    {
        _S23 = t_2 == 0U;
    }
    else
    {
        _S23 = false;
    }
    if(_S23)
    {
        *(stats_1+sBase_0) = mean_1;
        *(stats_1+(sBase_0 + 1U)) = rstd_0;
    }
    return;
}

[[kernel]] void layerNormInPlace(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], LayerNormInPlaceParams_0 constant* p_1 [[buffer(4)]], float device* x_2 [[buffer(0)]], float device* gamma_2 [[buffer(1)]], float device* beta_2 [[buffer(2)]], float device* stats_2 [[buffer(3)]])
{
    thread KernelContext_0 kernelContext_5;
    (&kernelContext_5)->p_0 = p_1;
    (&kernelContext_5)->x_0 = x_2;
    (&kernelContext_5)->gamma_0 = gamma_2;
    (&kernelContext_5)->beta_0 = beta_2;
    (&kernelContext_5)->stats_0 = stats_2;
    threadgroup array<float, int(256)> shared_1;
    (&kernelContext_5)->shared_0 = &shared_1;
    threadgroup array<float, int(1024)> blockSums_1;
    (&kernelContext_5)->blockSums_0 = &blockSums_1;
    threadgroup array<float, int(4)> rowResults_1;
    (&kernelContext_5)->rowResults_0 = &rowResults_1;
    uint row_0 = group_0.y * p_1->gridX_0 + group_0.x;
    if(row_0 >= (p_1->rows_0))
    {
        return;
    }
    uint base_3 = (&kernelContext_5)->p_0->xOff_0 + row_0 * (&kernelContext_5)->p_0->ldx_0;
    layerNormRow_0((&kernelContext_5)->x_0, base_3, (&kernelContext_5)->gamma_0, (&kernelContext_5)->p_0->gOff_0, (((&kernelContext_5)->p_0->flags_0) & 1U) != 0U, (&kernelContext_5)->beta_0, (&kernelContext_5)->p_0->bOff_0, (((&kernelContext_5)->p_0->flags_0) & 2U) != 0U, (&kernelContext_5)->p_0->eps_0, (&kernelContext_5)->x_0, base_3, (&kernelContext_5)->p_0->n_0, (&kernelContext_5)->stats_0, (&kernelContext_5)->p_0->statsOff_0 + 2U * row_0, (((&kernelContext_5)->p_0->flags_0) & 4U) != 0U, local_0.x, &kernelContext_5);
    return;
}

