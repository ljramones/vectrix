#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
struct DotBlocks_0
{
    uint i0_0;
    uint j0_0;
    uint row_0;
    uint col_0;
    uint thread_0;
    uint next_0;
};

struct GemmParams_0
{
    uint transA_0;
    uint transB_0;
    uint m_0;
    uint n_0;
    uint k_0;
    float alpha_0;
    float beta_0;
    uint aOff_0;
    uint lda_0;
    uint bOff_0;
    uint ldb_0;
    uint cOff_0;
    uint ldc_0;
};

struct KernelContext_0
{
    GemmParams_0 constant* p_0;
    float device* c_0;
    float device* a_0;
    float device* b_0;
    array<array<float, int(32)>, int(16)> threadgroup* tileA_0;
    array<array<float, int(16)>, int(32)> threadgroup* tileB_0;
};

float opA_0(uint i_0, uint kk_0, KernelContext_0 thread* kernelContext_0)
{
    float _S1;
    if((kernelContext_0->p_0->transA_0) != 0U)
    {
        _S1 = *(kernelContext_0->a_0+(kernelContext_0->p_0->aOff_0 + kk_0 * kernelContext_0->p_0->lda_0 + i_0));
    }
    else
    {
        _S1 = *(kernelContext_0->a_0+(kernelContext_0->p_0->aOff_0 + i_0 * kernelContext_0->p_0->lda_0 + kk_0));
    }
    return _S1;
}

float opB_0(uint kk_1, uint j_0, KernelContext_0 thread* kernelContext_1)
{
    float _S2;
    if((kernelContext_1->p_0->transB_0) != 0U)
    {
        _S2 = *(kernelContext_1->b_0+(kernelContext_1->p_0->bOff_0 + j_0 * kernelContext_1->p_0->ldb_0 + kk_1));
    }
    else
    {
        _S2 = *(kernelContext_1->b_0+(kernelContext_1->p_0->bOff_0 + kk_1 * kernelContext_1->p_0->ldb_0 + j_0));
    }
    return _S2;
}

float combineLanes_0(float a0_0, float a1_0, float a2_0, float a3_0, float a4_0, float a5_0, float a6_0, float a7_0)
{
    return a0_0 + a4_0 + (a2_0 + a6_0) + (a1_0 + a5_0 + (a3_0 + a7_0));
}

float DotBlocks_nextBlock_0(DotBlocks_0 thread* this_0, KernelContext_0 thread* kernelContext_2)
{
    uint start_0 = this_0->next_0 * 256U;
    uint _S3 = min(256U, kernelContext_2->p_0->k_0 - start_0);
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
        if(kc_0 < _S3)
        {
        }
        else
        {
            break;
        }
        uint q_0 = this_0->thread_0;
        for(;;)
        {
            if(q_0 < 512U)
            {
            }
            else
            {
                break;
            }
            uint ar_0 = q_0 / 32U;
            uint ak_0 = q_0 % 32U;
            bool _S4;
            if((this_0->i0_0 + ar_0) < (kernelContext_2->p_0->m_0))
            {
                _S4 = (kc_0 + ak_0) < _S3;
            }
            else
            {
                _S4 = false;
            }
            float _S5;
            if(_S4)
            {
                float _S6 = opA_0(this_0->i0_0 + ar_0, start_0 + kc_0 + ak_0, kernelContext_2);
                _S5 = _S6;
            }
            else
            {
                _S5 = 0.0;
            }
            (*kernelContext_2->tileA_0)[ar_0][ak_0] = _S5;
            uint bk_0 = q_0 / 16U;
            uint bc_0 = q_0 % 16U;
            bool _S7;
            if((this_0->j0_0 + bc_0) < (kernelContext_2->p_0->n_0))
            {
                _S7 = (kc_0 + bk_0) < _S3;
            }
            else
            {
                _S7 = false;
            }
            float _S8;
            if(_S7)
            {
                float _S9 = opB_0(start_0 + kc_0 + bk_0, this_0->j0_0 + bc_0, kernelContext_2);
                _S8 = _S9;
            }
            else
            {
                _S8 = 0.0;
            }
            (*kernelContext_2->tileB_0)[bk_0][bc_0] = _S8;
            q_0 = q_0 + 256U;
        }
        threadgroup_barrier(mem_flags::mem_threadgroup);
        uint _S10 = min(32U, _S3 - kc_0);
        uint e8_0 = 0U;
        for(;;)
        {
            if(e8_0 < 32U)
            {
            }
            else
            {
                break;
            }
            if(e8_0 < _S10)
            {
                acc_0[0U] = fma((*kernelContext_2->tileA_0)[this_0->row_0][e8_0], (*kernelContext_2->tileB_0)[e8_0][this_0->col_0], acc_0[0U]);
            }
            uint e_0 = e8_0 + 1U;
            if(e_0 < _S10)
            {
                acc_0[1U] = fma((*kernelContext_2->tileA_0)[this_0->row_0][e_0], (*kernelContext_2->tileB_0)[e_0][this_0->col_0], acc_0[1U]);
            }
            uint e_1 = e8_0 + 2U;
            if(e_1 < _S10)
            {
                acc_0[2U] = fma((*kernelContext_2->tileA_0)[this_0->row_0][e_1], (*kernelContext_2->tileB_0)[e_1][this_0->col_0], acc_0[2U]);
            }
            uint e_2 = e8_0 + 3U;
            if(e_2 < _S10)
            {
                acc_0[3U] = fma((*kernelContext_2->tileA_0)[this_0->row_0][e_2], (*kernelContext_2->tileB_0)[e_2][this_0->col_0], acc_0[3U]);
            }
            uint e_3 = e8_0 + 4U;
            if(e_3 < _S10)
            {
                acc_0[4U] = fma((*kernelContext_2->tileA_0)[this_0->row_0][e_3], (*kernelContext_2->tileB_0)[e_3][this_0->col_0], acc_0[4U]);
            }
            uint e_4 = e8_0 + 5U;
            if(e_4 < _S10)
            {
                acc_0[5U] = fma((*kernelContext_2->tileA_0)[this_0->row_0][e_4], (*kernelContext_2->tileB_0)[e_4][this_0->col_0], acc_0[5U]);
            }
            uint e_5 = e8_0 + 6U;
            if(e_5 < _S10)
            {
                acc_0[6U] = fma((*kernelContext_2->tileA_0)[this_0->row_0][e_5], (*kernelContext_2->tileB_0)[e_5][this_0->col_0], acc_0[6U]);
            }
            uint e_6 = e8_0 + 7U;
            if(e_6 < _S10)
            {
                acc_0[7U] = fma((*kernelContext_2->tileA_0)[this_0->row_0][e_6], (*kernelContext_2->tileB_0)[e_6][this_0->col_0], acc_0[7U]);
            }
            e8_0 = e8_0 + 8U;
        }
        threadgroup_barrier(mem_flags::mem_threadgroup);
        kc_0 = kc_0 + 32U;
    }
    return combineLanes_0(acc_0[int(0)], acc_0[int(1)], acc_0[int(2)], acc_0[int(3)], acc_0[int(4)], acc_0[int(5)], acc_0[int(6)], acc_0[int(7)]);
}

float pairwiseBlocks_0(DotBlocks_0 thread* source_0, uint blocks_0, KernelContext_0 thread* kernelContext_3)
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
            float _S11 = DotBlocks_nextBlock_0(source_0, kernelContext_3);
            ret_1 = _S11;
            sp_1 = top_0;
        }
        else
        {
            if((state_0[top_0]) == 0U)
            {
                state_0[top_0] = 1U;
                count_0[sp_0] = (count_0[top_0] + 1U) / 2U;
                state_0[sp_0] = 0U;
                int _S12 = sp_0 + int(1);
                ret_1 = ret_0;
                sp_1 = _S12;
            }
            else
            {
                if((state_0[top_0]) == 1U)
                {
                    left_0[top_0] = ret_0;
                    state_0[top_0] = 2U;
                    count_0[sp_0] = count_0[top_0] / 2U;
                    state_0[sp_0] = 0U;
                    int _S13 = sp_0 + int(1);
                    ret_1 = ret_0;
                    sp_1 = _S13;
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

[[kernel]] void gemm(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], GemmParams_0 constant* p_1 [[buffer(3)]], float device* c_1 [[buffer(2)]], float device* a_1 [[buffer(0)]], float device* b_1 [[buffer(1)]])
{
    thread KernelContext_0 kernelContext_4;
    (&kernelContext_4)->p_0 = p_1;
    (&kernelContext_4)->c_0 = c_1;
    (&kernelContext_4)->a_0 = a_1;
    (&kernelContext_4)->b_0 = b_1;
    threadgroup array<array<float, int(32)>, int(16)> tileA_1;
    (&kernelContext_4)->tileA_0 = &tileA_1;
    threadgroup array<array<float, int(16)>, int(32)> tileB_1;
    (&kernelContext_4)->tileB_0 = &tileB_1;
    thread DotBlocks_0 dot_0;
    (&dot_0)->i0_0 = group_0.y * 16U;
    (&dot_0)->j0_0 = group_0.x * 16U;
    uint _S14 = local_0.y;
    (&dot_0)->row_0 = _S14;
    uint _S15 = local_0.x;
    (&dot_0)->col_0 = _S15;
    (&dot_0)->thread_0 = _S14 * 16U + _S15;
    (&dot_0)->next_0 = 0U;
    float s_0;
    if((p_1->k_0) > 0U)
    {
        float _S16 = pairwiseBlocks_0(&dot_0, (p_1->k_0 + 256U - 1U) / 256U, &kernelContext_4);
        s_0 = _S16;
    }
    else
    {
        s_0 = 0.0;
    }
    uint i_1 = (&dot_0)->i0_0 + (&dot_0)->row_0;
    uint j_1 = (&dot_0)->j0_0 + (&dot_0)->col_0;
    bool _S17;
    if(i_1 < ((&kernelContext_4)->p_0->m_0))
    {
        _S17 = j_1 < ((&kernelContext_4)->p_0->n_0);
    }
    else
    {
        _S17 = false;
    }
    if(_S17)
    {
        uint ci_0 = (&kernelContext_4)->p_0->cOff_0 + i_1 * (&kernelContext_4)->p_0->ldc_0 + j_1;
        float device* _S18 = (&kernelContext_4)->c_0+ci_0;
        if(((&kernelContext_4)->p_0->beta_0) == 0.0)
        {
            s_0 = (&kernelContext_4)->p_0->alpha_0 * s_0;
        }
        else
        {
            s_0 = fma((&kernelContext_4)->p_0->alpha_0, s_0, (&kernelContext_4)->p_0->beta_0 * *((&kernelContext_4)->c_0+ci_0));
        }
        *_S18 = s_0;
    }
    return;
}

