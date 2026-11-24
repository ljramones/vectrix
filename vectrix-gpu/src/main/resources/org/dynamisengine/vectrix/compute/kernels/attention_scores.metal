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

struct RowTerms_0
{
    uint xBase_0;
    uint mBase_0;
};

struct SerialDotBlocks_0
{
    RowTerms_0 terms_0;
    uint n_0;
    uint next_0;
};

struct ScoresParams_0
{
    uint rows_0;
    uint heads_0;
    uint group_0;
    uint queries_1;
    uint t_1;
    uint causal_1;
    uint width_0;
    uint scaled_0;
    float scale_0;
    uint xOff_0;
    uint ldx_0;
    uint mOff_0;
    uint ldm_0;
    uint masked_0;
    uint maskOff_0;
    uint ldMask_0;
    uint outOff_0;
    uint ldOut_0;
    uint chunks_0;
    uint gridX_0;
};

struct KernelContext_0
{
    ScoresParams_0 constant* p_0;
    float device* out_0;
    float device* mask_0;
    float device* x_0;
    float device* m_0;
};

float RowTerms_left_0(const RowTerms_0 thread* this_0, uint e_0, KernelContext_0 thread* kernelContext_0)
{
    return *(kernelContext_0->x_0+(this_0->xBase_0 + e_0));
}

float RowTerms_right_0(const RowTerms_0 thread* this_1, uint e_1, KernelContext_0 thread* kernelContext_1)
{
    return *(kernelContext_1->m_0+(this_1->mBase_0 + e_1));
}

float SerialDotBlocks_nextBlock_0(SerialDotBlocks_0 thread* this_2, KernelContext_0 thread* kernelContext_2)
{
    uint start_0 = this_2->next_0 * 256U;
    uint _S2 = min(256U, this_2->n_0 - start_0);
    this_2->next_0 = this_2->next_0 + 1U;
    thread array<float, int(8)> acc_0;
    acc_0[int(0)] = 0.0;
    acc_0[int(1)] = 0.0;
    acc_0[int(2)] = 0.0;
    acc_0[int(3)] = 0.0;
    acc_0[int(4)] = 0.0;
    acc_0[int(5)] = 0.0;
    acc_0[int(6)] = 0.0;
    acc_0[int(7)] = 0.0;
    uint e8_0 = 0U;
    for(;;)
    {
        bool _S3 = e8_0 < _S2;
        if(_S3)
        {
        }
        else
        {
            break;
        }
        if(_S3)
        {
            uint _S4 = start_0 + e8_0;
            thread RowTerms_0 _S5 = this_2->terms_0;
            float _S6 = RowTerms_left_0(&_S5, _S4, kernelContext_2);
            thread RowTerms_0 _S7 = this_2->terms_0;
            float _S8 = RowTerms_right_0(&_S7, _S4, kernelContext_2);
            acc_0[0U] = fma(_S6, _S8, acc_0[0U]);
        }
        uint e_2 = e8_0 + 1U;
        if(e_2 < _S2)
        {
            uint _S9 = start_0 + e_2;
            thread RowTerms_0 _S10 = this_2->terms_0;
            float _S11 = RowTerms_left_0(&_S10, _S9, kernelContext_2);
            thread RowTerms_0 _S12 = this_2->terms_0;
            float _S13 = RowTerms_right_0(&_S12, _S9, kernelContext_2);
            acc_0[1U] = fma(_S11, _S13, acc_0[1U]);
        }
        uint e_3 = e8_0 + 2U;
        if(e_3 < _S2)
        {
            uint _S14 = start_0 + e_3;
            thread RowTerms_0 _S15 = this_2->terms_0;
            float _S16 = RowTerms_left_0(&_S15, _S14, kernelContext_2);
            thread RowTerms_0 _S17 = this_2->terms_0;
            float _S18 = RowTerms_right_0(&_S17, _S14, kernelContext_2);
            acc_0[2U] = fma(_S16, _S18, acc_0[2U]);
        }
        uint e_4 = e8_0 + 3U;
        if(e_4 < _S2)
        {
            uint _S19 = start_0 + e_4;
            thread RowTerms_0 _S20 = this_2->terms_0;
            float _S21 = RowTerms_left_0(&_S20, _S19, kernelContext_2);
            thread RowTerms_0 _S22 = this_2->terms_0;
            float _S23 = RowTerms_right_0(&_S22, _S19, kernelContext_2);
            acc_0[3U] = fma(_S21, _S23, acc_0[3U]);
        }
        uint e_5 = e8_0 + 4U;
        if(e_5 < _S2)
        {
            uint _S24 = start_0 + e_5;
            thread RowTerms_0 _S25 = this_2->terms_0;
            float _S26 = RowTerms_left_0(&_S25, _S24, kernelContext_2);
            thread RowTerms_0 _S27 = this_2->terms_0;
            float _S28 = RowTerms_right_0(&_S27, _S24, kernelContext_2);
            acc_0[4U] = fma(_S26, _S28, acc_0[4U]);
        }
        uint e_6 = e8_0 + 5U;
        if(e_6 < _S2)
        {
            uint _S29 = start_0 + e_6;
            thread RowTerms_0 _S30 = this_2->terms_0;
            float _S31 = RowTerms_left_0(&_S30, _S29, kernelContext_2);
            thread RowTerms_0 _S32 = this_2->terms_0;
            float _S33 = RowTerms_right_0(&_S32, _S29, kernelContext_2);
            acc_0[5U] = fma(_S31, _S33, acc_0[5U]);
        }
        uint e_7 = e8_0 + 6U;
        if(e_7 < _S2)
        {
            uint _S34 = start_0 + e_7;
            thread RowTerms_0 _S35 = this_2->terms_0;
            float _S36 = RowTerms_left_0(&_S35, _S34, kernelContext_2);
            thread RowTerms_0 _S37 = this_2->terms_0;
            float _S38 = RowTerms_right_0(&_S37, _S34, kernelContext_2);
            acc_0[6U] = fma(_S36, _S38, acc_0[6U]);
        }
        uint e_8 = e8_0 + 7U;
        if(e_8 < _S2)
        {
            uint _S39 = start_0 + e_8;
            thread RowTerms_0 _S40 = this_2->terms_0;
            float _S41 = RowTerms_left_0(&_S40, _S39, kernelContext_2);
            thread RowTerms_0 _S42 = this_2->terms_0;
            float _S43 = RowTerms_right_0(&_S42, _S39, kernelContext_2);
            acc_0[7U] = fma(_S41, _S43, acc_0[7U]);
        }
        e8_0 = e8_0 + 8U;
    }
    return combineLanes_0(acc_0[int(0)], acc_0[int(1)], acc_0[int(2)], acc_0[int(3)], acc_0[int(4)], acc_0[int(5)], acc_0[int(6)], acc_0[int(7)]);
}

float pairwiseBlocks_0(SerialDotBlocks_0 thread* source_0, uint blocks_0, KernelContext_0 thread* kernelContext_3)
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
            float _S44 = SerialDotBlocks_nextBlock_0(source_0, kernelContext_3);
            ret_1 = _S44;
            sp_1 = top_0;
        }
        else
        {
            if((state_0[top_0]) == 0U)
            {
                state_0[top_0] = 1U;
                count_0[sp_0] = (count_0[top_0] + 1U) / 2U;
                state_0[sp_0] = 0U;
                int _S45 = sp_0 + int(1);
                ret_1 = ret_0;
                sp_1 = _S45;
            }
            else
            {
                if((state_0[top_0]) == 1U)
                {
                    left_0[top_0] = ret_0;
                    state_0[top_0] = 2U;
                    count_0[sp_0] = count_0[top_0] / 2U;
                    state_0[sp_0] = 0U;
                    int _S46 = sp_0 + int(1);
                    ret_1 = ret_0;
                    sp_1 = _S46;
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

float serialDot_0(const RowTerms_0 thread* terms_1, uint n_1, KernelContext_0 thread* kernelContext_4)
{
    if(n_1 == 0U)
    {
        return 0.0;
    }
    thread SerialDotBlocks_0 blocks_1;
    (&blocks_1)->terms_0 = *terms_1;
    (&blocks_1)->n_0 = n_1;
    (&blocks_1)->next_0 = 0U;
    float _S47 = pairwiseBlocks_0(&blocks_1, (n_1 + 256U - 1U) / 256U, kernelContext_4);
    return _S47;
}

[[kernel]] void attentionScores(uint3 group_1 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], ScoresParams_0 constant* p_1 [[buffer(4)]], float device* out_1 [[buffer(3)]], float device* mask_1 [[buffer(2)]], float device* x_1 [[buffer(0)]], float device* m_1 [[buffer(1)]])
{
    thread KernelContext_0 kernelContext_5;
    (&kernelContext_5)->p_0 = p_1;
    (&kernelContext_5)->out_0 = out_1;
    (&kernelContext_5)->mask_0 = mask_1;
    (&kernelContext_5)->x_0 = x_1;
    (&kernelContext_5)->m_0 = m_1;
    uint gi_0 = group_1.y * p_1->gridX_0 + group_1.x;
    uint row_0 = gi_0 / p_1->chunks_0;
    if(row_0 >= ((&kernelContext_5)->p_0->rows_0))
    {
        return;
    }
    uint _S48 = gi_0 % p_1->chunks_0;
    uint j_0 = _S48 * 64U + local_0.x;
    uint r_1 = row_0 / (&kernelContext_5)->p_0->heads_0;
    uint _S49 = row_0 % (&kernelContext_5)->p_0->heads_0;
    uint g_0 = _S49 / (&kernelContext_5)->p_0->group_0;
    if(j_0 >= (keysFor_0(r_1, (&kernelContext_5)->p_0->queries_1, (&kernelContext_5)->p_0->t_1, (&kernelContext_5)->p_0->causal_1)))
    {
        return;
    }
    thread RowTerms_0 terms_2;
    (&terms_2)->xBase_0 = (&kernelContext_5)->p_0->xOff_0 + row_0 * (&kernelContext_5)->p_0->ldx_0;
    (&terms_2)->mBase_0 = (&kernelContext_5)->p_0->mOff_0 + j_0 * (&kernelContext_5)->p_0->ldm_0 + g_0 * (&kernelContext_5)->p_0->width_0;
    uint _S50 = (&kernelContext_5)->p_0->width_0;
    thread RowTerms_0 _S51 = terms_2;
    float _S52 = serialDot_0(&_S51, _S50, &kernelContext_5);
    float s_0;
    if(((&kernelContext_5)->p_0->scaled_0) != 0U)
    {
        s_0 = (&kernelContext_5)->p_0->scale_0 * _S52;
    }
    else
    {
        s_0 = _S52;
    }
    float device* _S53 = (&kernelContext_5)->out_0+((&kernelContext_5)->p_0->outOff_0 + row_0 * (&kernelContext_5)->p_0->ldOut_0 + j_0);
    if(((&kernelContext_5)->p_0->masked_0) != 0U)
    {
        s_0 = s_0 + *((&kernelContext_5)->mask_0+((&kernelContext_5)->p_0->maskOff_0 + r_1 * (&kernelContext_5)->p_0->ldMask_0 + j_0));
    }
    *_S53 = s_0;
    return;
}

