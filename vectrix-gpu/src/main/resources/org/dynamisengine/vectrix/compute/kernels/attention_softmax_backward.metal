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

float combineLanes_0(float a0_0, float a1_0, float a2_0, float a3_0, float a4_0, float a5_0, float a6_0, float a7_0)
{
    return a0_0 + a4_0 + (a2_0 + a6_0) + (a1_0 + a5_0 + (a3_0 + a7_0));
}

struct SharedBlocks_0
{
    uint next_0;
};

struct SoftmaxBackwardParams_0
{
    uint rows_0;
    uint heads_0;
    uint queries_1;
    uint t_1;
    uint causal_1;
    uint pOff_0;
    uint ldp_0;
    uint dsOff_0;
    uint ldds_0;
    uint gridX_0;
};

struct KernelContext_0
{
    SoftmaxBackwardParams_0 constant* p_0;
    float device* ds_0;
    float device* probs_0;
    array<float, int(256)> threadgroup* shared_0;
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
            float _S2 = SharedBlocks_nextBlock_0(source_0, kernelContext_1);
            ret_1 = _S2;
            sp_1 = top_0;
        }
        else
        {
            if((state_0[top_0]) == 0U)
            {
                state_0[top_0] = 1U;
                count_0[sp_0] = (count_0[top_0] + 1U) / 2U;
                state_0[sp_0] = 0U;
                int _S3 = sp_0 + int(1);
                ret_1 = ret_0;
                sp_1 = _S3;
            }
            else
            {
                if((state_0[top_0]) == 1U)
                {
                    left_0[top_0] = ret_0;
                    state_0[top_0] = 2U;
                    count_0[sp_0] = count_0[top_0] / 2U;
                    state_0[sp_0] = 0U;
                    int _S4 = sp_0 + int(1);
                    ret_1 = ret_0;
                    sp_1 = _S4;
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

[[kernel]] void attentionSoftmaxBackward(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], SoftmaxBackwardParams_0 constant* p_1 [[buffer(2)]], float device* ds_1 [[buffer(1)]], float device* probs_1 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_2;
    (&kernelContext_2)->p_0 = p_1;
    (&kernelContext_2)->ds_0 = ds_1;
    (&kernelContext_2)->probs_0 = probs_1;
    threadgroup array<float, int(256)> shared_1;
    (&kernelContext_2)->shared_0 = &shared_1;
    threadgroup array<float, int(1024)> blockSums_1;
    (&kernelContext_2)->blockSums_0 = &blockSums_1;
    threadgroup float total_1;
    (&kernelContext_2)->total_0 = &total_1;
    uint row_0 = group_0.y * p_1->gridX_0 + group_0.x;
    if(row_0 >= (p_1->rows_0))
    {
        return;
    }
    uint j_0;
    uint t_2 = local_0.x;
    uint _S5 = row_0 / (&kernelContext_2)->p_0->heads_0;
    uint n_0 = keysFor_0(_S5, (&kernelContext_2)->p_0->queries_1, (&kernelContext_2)->p_0->t_1, (&kernelContext_2)->p_0->causal_1);
    uint _S6 = (&kernelContext_2)->p_0->pOff_0 + row_0 * (&kernelContext_2)->p_0->ldp_0;
    uint _S7 = (&kernelContext_2)->p_0->dsOff_0 + row_0 * (&kernelContext_2)->p_0->ldds_0;
    uint blocks_1 = (n_0 + 256U - 1U) / 256U;
    uint _S8 = t_2 % 8U;
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
        uint b_0 = b0_0 + t_2 / 8U;
        bool _S9 = b_0 < blocks_1;
        float acc_0;
        if(_S9)
        {
            uint start_0 = b_0 * 256U;
            uint _S10 = min(256U, n_0 - start_0);
            acc_0 = 0.0;
            j_0 = _S8;
            for(;;)
            {
                if(j_0 < _S10)
                {
                }
                else
                {
                    break;
                }
                float _S11 = fma(*((&kernelContext_2)->ds_0+(_S7 + start_0 + j_0)), *((&kernelContext_2)->probs_0+(_S6 + start_0 + j_0)), acc_0);
                uint j_1 = j_0 + 8U;
                acc_0 = _S11;
                j_0 = j_1;
            }
        }
        else
        {
            acc_0 = 0.0;
        }
        (*(&kernelContext_2)->shared_0)[t_2] = acc_0;
        threadgroup_barrier(mem_flags::mem_threadgroup);
        bool _S12;
        if(_S8 == 0U)
        {
            _S12 = _S9;
        }
        else
        {
            _S12 = false;
        }
        if(_S12)
        {
            (*(&kernelContext_2)->blockSums_0)[b_0] = combineLanes_0((*(&kernelContext_2)->shared_0)[t_2], (*(&kernelContext_2)->shared_0)[t_2 + 1U], (*(&kernelContext_2)->shared_0)[t_2 + 2U], (*(&kernelContext_2)->shared_0)[t_2 + 3U], (*(&kernelContext_2)->shared_0)[t_2 + 4U], (*(&kernelContext_2)->shared_0)[t_2 + 5U], (*(&kernelContext_2)->shared_0)[t_2 + 6U], (*(&kernelContext_2)->shared_0)[t_2 + 7U]);
        }
        threadgroup_barrier(mem_flags::mem_threadgroup);
        b0_0 = b0_0 + 32U;
    }
    if(t_2 == 0U)
    {
        thread SharedBlocks_0 source_1;
        (&source_1)->next_0 = 0U;
        float _S13 = pairwiseBlocks_0(&source_1, blocks_1, &kernelContext_2);
        *(&kernelContext_2)->total_0 = _S13;
    }
    threadgroup_barrier(mem_flags::mem_threadgroup);
    float _S14 = *(&kernelContext_2)->total_0;
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
        uint b_1 = b0_0 + t_2 / 8U;
        if(b_1 < blocks_1)
        {
            uint start_1 = b_1 * 256U;
            uint _S15 = min(256U, n_0 - start_1);
            j_0 = _S8;
            for(;;)
            {
                if(j_0 < _S15)
                {
                }
                else
                {
                    break;
                }
                uint _S16 = _S7 + start_1 + j_0;
                *((&kernelContext_2)->ds_0+_S16) = *((&kernelContext_2)->probs_0+(_S6 + start_1 + j_0)) * (*((&kernelContext_2)->ds_0+_S16) - _S14);
                j_0 = j_0 + 8U;
            }
        }
        b0_0 = b0_0 + 32U;
    }
    return;
}

