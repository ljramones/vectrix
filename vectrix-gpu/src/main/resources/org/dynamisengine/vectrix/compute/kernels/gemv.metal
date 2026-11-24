#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
struct GemvBlocks_0
{
    uint i0_0;
    uint row_0;
    uint next_0;
};

struct GemvParams_0
{
    uint transA_0;
    uint m_0;
    uint n_0;
    float alpha_0;
    float beta_0;
    uint aOff_0;
    uint lda_0;
    uint xOff_0;
    uint yOff_0;
    uint gridX_0;
};

struct KernelContext_0
{
    GemvParams_0 constant* p_0;
    float device* y_0;
    float device* a_0;
    float device* x_0;
    array<array<float, int(33)>, int(64)> threadgroup* tileA_0;
    array<float, int(32)> threadgroup* tileX_0;
};

float combineLanes_0(float a0_0, float a1_0, float a2_0, float a3_0, float a4_0, float a5_0, float a6_0, float a7_0)
{
    return a0_0 + a4_0 + (a2_0 + a6_0) + (a1_0 + a5_0 + (a3_0 + a7_0));
}

float GemvBlocks_nextBlock_0(GemvBlocks_0 thread* this_0, KernelContext_0 thread* kernelContext_0)
{
    uint start_0 = this_0->next_0 * 256U;
    uint _S1 = min(256U, kernelContext_0->p_0->n_0 - start_0);
    this_0->next_0 = this_0->next_0 + 1U;
    thread array<float, int(8)> acc_0;
    acc_0[int(0)] = 0.0;
    acc_0[int(1)] = 0.0;
    acc_0[int(2)] = 0.0;
    acc_0[int(3)] = 0.0;
    acc_0[int(4)] = 0.0;
    acc_0[int(5)] = 0.0;
    acc_0[int(6)] = 0.0;
    acc_0[int(7)] = 0.0;
    uint kc_0 = 0U;
    for(;;)
    {
        if(kc_0 < _S1)
        {
        }
        else
        {
            break;
        }
        float v_0;
        uint r_0;
        uint q_0 = this_0->row_0;
        for(;;)
        {
            if(q_0 < 2048U)
            {
            }
            else
            {
                break;
            }
            uint kk_0;
            if((kernelContext_0->p_0->transA_0) != 0U)
            {
                uint _S2 = q_0 / 64U;
                r_0 = q_0 % 64U;
                kk_0 = _S2;
            }
            else
            {
                uint _S3 = q_0 % 32U;
                r_0 = q_0 / 32U;
                kk_0 = _S3;
            }
            bool _S4;
            if((this_0->i0_0 + r_0) < (kernelContext_0->p_0->m_0))
            {
                _S4 = (kc_0 + kk_0) < _S1;
            }
            else
            {
                _S4 = false;
            }
            if(_S4)
            {
                uint col_0 = start_0 + kc_0 + kk_0;
                if((kernelContext_0->p_0->transA_0) != 0U)
                {
                    v_0 = *(kernelContext_0->a_0+(kernelContext_0->p_0->aOff_0 + col_0 * kernelContext_0->p_0->lda_0 + this_0->i0_0 + r_0));
                }
                else
                {
                    v_0 = *(kernelContext_0->a_0+(kernelContext_0->p_0->aOff_0 + (this_0->i0_0 + r_0) * kernelContext_0->p_0->lda_0 + col_0));
                }
            }
            else
            {
                v_0 = 0.0;
            }
            (*kernelContext_0->tileA_0)[r_0][kk_0] = v_0;
            q_0 = q_0 + 64U;
        }
        if((this_0->row_0) < 32U)
        {
            if((kc_0 + this_0->row_0) < _S1)
            {
                v_0 = *(kernelContext_0->x_0+(kernelContext_0->p_0->xOff_0 + start_0 + kc_0 + this_0->row_0));
            }
            else
            {
                v_0 = 0.0;
            }
            (*kernelContext_0->tileX_0)[this_0->row_0] = v_0;
        }
        threadgroup_barrier(mem_flags::mem_threadgroup);
        uint _S5 = min(32U, _S1 - kc_0);
        r_0 = 0U;
        for(;;)
        {
            if(r_0 < 32U)
            {
            }
            else
            {
                break;
            }
            if(r_0 < _S5)
            {
                acc_0[0U] = fma((*kernelContext_0->tileA_0)[this_0->row_0][r_0], (*kernelContext_0->tileX_0)[r_0], acc_0[0U]);
            }
            uint e_0 = r_0 + 1U;
            if(e_0 < _S5)
            {
                acc_0[1U] = fma((*kernelContext_0->tileA_0)[this_0->row_0][e_0], (*kernelContext_0->tileX_0)[e_0], acc_0[1U]);
            }
            uint e_1 = r_0 + 2U;
            if(e_1 < _S5)
            {
                acc_0[2U] = fma((*kernelContext_0->tileA_0)[this_0->row_0][e_1], (*kernelContext_0->tileX_0)[e_1], acc_0[2U]);
            }
            uint e_2 = r_0 + 3U;
            if(e_2 < _S5)
            {
                acc_0[3U] = fma((*kernelContext_0->tileA_0)[this_0->row_0][e_2], (*kernelContext_0->tileX_0)[e_2], acc_0[3U]);
            }
            uint e_3 = r_0 + 4U;
            if(e_3 < _S5)
            {
                acc_0[4U] = fma((*kernelContext_0->tileA_0)[this_0->row_0][e_3], (*kernelContext_0->tileX_0)[e_3], acc_0[4U]);
            }
            uint e_4 = r_0 + 5U;
            if(e_4 < _S5)
            {
                acc_0[5U] = fma((*kernelContext_0->tileA_0)[this_0->row_0][e_4], (*kernelContext_0->tileX_0)[e_4], acc_0[5U]);
            }
            uint e_5 = r_0 + 6U;
            if(e_5 < _S5)
            {
                acc_0[6U] = fma((*kernelContext_0->tileA_0)[this_0->row_0][e_5], (*kernelContext_0->tileX_0)[e_5], acc_0[6U]);
            }
            uint e_6 = r_0 + 7U;
            if(e_6 < _S5)
            {
                acc_0[7U] = fma((*kernelContext_0->tileA_0)[this_0->row_0][e_6], (*kernelContext_0->tileX_0)[e_6], acc_0[7U]);
            }
            r_0 = r_0 + 8U;
        }
        threadgroup_barrier(mem_flags::mem_threadgroup);
        kc_0 = kc_0 + 32U;
    }
    return combineLanes_0(acc_0[int(0)], acc_0[int(1)], acc_0[int(2)], acc_0[int(3)], acc_0[int(4)], acc_0[int(5)], acc_0[int(6)], acc_0[int(7)]);
}

float pairwiseBlocks_0(GemvBlocks_0 thread* source_0, uint blocks_0, KernelContext_0 thread* kernelContext_1)
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
            float _S6 = GemvBlocks_nextBlock_0(source_0, kernelContext_1);
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

[[kernel]] void gemv(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], GemvParams_0 constant* p_1 [[buffer(3)]], float device* y_1 [[buffer(2)]], float device* a_1 [[buffer(0)]], float device* x_1 [[buffer(1)]])
{
    thread KernelContext_0 kernelContext_2;
    (&kernelContext_2)->p_0 = p_1;
    (&kernelContext_2)->y_0 = y_1;
    (&kernelContext_2)->a_0 = a_1;
    (&kernelContext_2)->x_0 = x_1;
    threadgroup array<array<float, int(33)>, int(64)> tileA_1;
    (&kernelContext_2)->tileA_0 = &tileA_1;
    threadgroup array<float, int(32)> tileX_1;
    (&kernelContext_2)->tileX_0 = &tileX_1;
    uint _S9 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U;
    if(_S9 >= (p_1->m_0))
    {
        return;
    }
    thread GemvBlocks_0 dot_0;
    (&dot_0)->i0_0 = _S9;
    (&dot_0)->row_0 = local_0.x;
    (&dot_0)->next_0 = 0U;
    float s_0;
    if(((&kernelContext_2)->p_0->n_0) > 0U)
    {
        float _S10 = pairwiseBlocks_0(&dot_0, ((&kernelContext_2)->p_0->n_0 + 256U - 1U) / 256U, &kernelContext_2);
        s_0 = _S10;
    }
    else
    {
        s_0 = 0.0;
    }
    uint i_0 = (&dot_0)->i0_0 + (&dot_0)->row_0;
    if(i_0 < (p_1->m_0))
    {
        uint yi_0 = (&kernelContext_2)->p_0->yOff_0 + i_0;
        if(((&kernelContext_2)->p_0->beta_0) == 0.0)
        {
            *((&kernelContext_2)->y_0+yi_0) = (&kernelContext_2)->p_0->alpha_0 * s_0;
        }
        else
        {
            *((&kernelContext_2)->y_0+yi_0) = fma((&kernelContext_2)->p_0->alpha_0, s_0, (&kernelContext_2)->p_0->beta_0 * *((&kernelContext_2)->y_0+yi_0));
        }
    }
    return;
}

