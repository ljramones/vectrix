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

struct NormBackwardInPlaceParams_0
{
    uint rows_0;
    uint n_0;
    uint aOff_0;
    uint lda_0;
    uint gOff_0;
    uint statsOff_0;
    uint bOff_0;
    uint ldb_0;
    uint flags_0;
    uint gridX_0;
};

struct KernelContext_0
{
    NormBackwardInPlaceParams_0 constant* p_0;
    float device* b_0;
    float device* gamma_0;
    float device* stats_0;
    float device* a_0;
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

struct NormTerms_0
{
    uint xBase_0;
    uint gBase_0;
    bool hasGamma_0;
    uint dyBase_0;
    bool rms_0;
    float mean_0;
    float scale_0;
};

struct DxhSum_0
{
    NormTerms_0 terms_0;
};

float optional_0(float device* v_1, uint base_0, bool present_0, uint j_0, float absent_0)
{
    uint _S4;
    if(present_0)
    {
        _S4 = base_0 + j_0;
    }
    else
    {
        _S4 = 0U;
    }
    float value_0 = *(v_1+_S4);
    float _S5;
    if(present_0)
    {
        _S5 = value_0;
    }
    else
    {
        _S5 = absent_0;
    }
    return _S5;
}

float NormTerms_dxh_0(const NormTerms_0 thread* this_1, float device* this_x_0, float device* this_gamma_0, float device* this_dy_0, uint j_1)
{
    float _S6 = *(this_dy_0+(this_1->dyBase_0 + j_1));
    float _S7 = optional_0(this_gamma_0, this_1->gBase_0, this_1->hasGamma_0, j_1, 1.0);
    return _S6 * _S7;
}

float DxhSum_accumulate_0(const DxhSum_0 thread* this_2, float device* this_terms_x_0, float device* this_terms_gamma_0, float device* this_terms_dy_0, float acc_0, uint j_2)
{
    float _S8 = NormTerms_dxh_0(&this_2->terms_0, this_terms_x_0, this_terms_gamma_0, this_terms_dy_0, j_2);
    return acc_0 + _S8;
}

float groupSum_0(const DxhSum_0 thread* f_0, float device* f_terms_x_0, float device* f_terms_gamma_0, float device* f_terms_dy_0, uint n_1, uint t_0, uint slot_0, KernelContext_0 thread* kernelContext_2)
{
    float acc_1;
    uint blocks_1 = (n_1 + 256U - 1U) / 256U;
    uint _S9 = t_0 % 8U;
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
        uint b_1 = b0_0 + t_0 / 8U;
        bool _S10 = b_1 < blocks_1;
        if(_S10)
        {
            uint base_1 = b_1 * 256U;
            uint _S11 = min(256U, n_1 - base_1);
            acc_1 = 0.0;
            uint j_3 = _S9;
            for(;;)
            {
                if(j_3 < _S11)
                {
                }
                else
                {
                    break;
                }
                float _S12 = DxhSum_accumulate_0(f_0, f_terms_x_0, f_terms_gamma_0, f_terms_dy_0, acc_1, base_1 + j_3);
                uint j_4 = j_3 + 8U;
                acc_1 = _S12;
                j_3 = j_4;
            }
        }
        else
        {
            acc_1 = 0.0;
        }
        (*kernelContext_2->shared_0)[t_0] = acc_1;
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
            (*kernelContext_2->blockSums_0)[b_1] = combineLanes_0((*kernelContext_2->shared_0)[t_0], (*kernelContext_2->shared_0)[t_0 + 1U], (*kernelContext_2->shared_0)[t_0 + 2U], (*kernelContext_2->shared_0)[t_0 + 3U], (*kernelContext_2->shared_0)[t_0 + 4U], (*kernelContext_2->shared_0)[t_0 + 5U], (*kernelContext_2->shared_0)[t_0 + 6U], (*kernelContext_2->shared_0)[t_0 + 7U]);
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
            float _S14 = pairwiseBlocks_0(&source_1, blocks_1, kernelContext_2);
            acc_1 = _S14;
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

struct DxhDotH_0
{
    NormTerms_0 terms_1;
};

float NormTerms_h_0(const NormTerms_0 thread* this_3, float device* this_x_1, float device* this_gamma_1, float device* this_dy_1, uint j_5)
{
    float xv_0 = *(this_x_1+(this_3->xBase_0 + j_5));
    if(this_3->rms_0)
    {
        return xv_0 * this_3->scale_0;
    }
    return (xv_0 - this_3->mean_0) * this_3->scale_0;
}

float DxhDotH_accumulate_0(const DxhDotH_0 thread* this_4, float device* this_terms_x_1, float device* this_terms_gamma_1, float device* this_terms_dy_1, float acc_2, uint j_6)
{
    float _S15 = NormTerms_dxh_0(&this_4->terms_1, this_terms_x_1, this_terms_gamma_1, this_terms_dy_1, j_6);
    float _S16 = NormTerms_h_0(&this_4->terms_1, this_terms_x_1, this_terms_gamma_1, this_terms_dy_1, j_6);
    return fma(_S15, _S16, acc_2);
}

float groupSum_1(const DxhDotH_0 thread* f_1, float device* f_terms_x_1, float device* f_terms_gamma_1, float device* f_terms_dy_1, uint n_2, uint t_1, uint slot_1, KernelContext_0 thread* kernelContext_3)
{
    float acc_3;
    uint blocks_2 = (n_2 + 256U - 1U) / 256U;
    uint _S17 = t_1 % 8U;
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
        uint b_2 = b0_1 + t_1 / 8U;
        bool _S18 = b_2 < blocks_2;
        if(_S18)
        {
            uint base_2 = b_2 * 256U;
            uint _S19 = min(256U, n_2 - base_2);
            acc_3 = 0.0;
            uint j_7 = _S17;
            for(;;)
            {
                if(j_7 < _S19)
                {
                }
                else
                {
                    break;
                }
                float _S20 = DxhDotH_accumulate_0(f_1, f_terms_x_1, f_terms_gamma_1, f_terms_dy_1, acc_3, base_2 + j_7);
                uint j_8 = j_7 + 8U;
                acc_3 = _S20;
                j_7 = j_8;
            }
        }
        else
        {
            acc_3 = 0.0;
        }
        (*kernelContext_3->shared_0)[t_1] = acc_3;
        threadgroup_barrier(mem_flags::mem_threadgroup);
        bool _S21;
        if(_S17 == 0U)
        {
            _S21 = _S18;
        }
        else
        {
            _S21 = false;
        }
        if(_S21)
        {
            (*kernelContext_3->blockSums_0)[b_2] = combineLanes_0((*kernelContext_3->shared_0)[t_1], (*kernelContext_3->shared_0)[t_1 + 1U], (*kernelContext_3->shared_0)[t_1 + 2U], (*kernelContext_3->shared_0)[t_1 + 3U], (*kernelContext_3->shared_0)[t_1 + 4U], (*kernelContext_3->shared_0)[t_1 + 5U], (*kernelContext_3->shared_0)[t_1 + 6U], (*kernelContext_3->shared_0)[t_1 + 7U]);
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
            float _S22 = pairwiseBlocks_0(&source_2, blocks_2, kernelContext_3);
            acc_3 = _S22;
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

void normBackwardRow_0(float device* x_0, uint xBase_1, float device* gamma_1, uint gBase_1, bool hasGamma_1, float device* stats_1, uint sBase_0, bool rms_1, float device* dy_0, uint dyBase_1, float device* dx_0, uint dxBase_0, uint n_3, uint t_2, KernelContext_0 thread* kernelContext_4)
{
    thread NormTerms_0 terms_2;
    (&terms_2)->xBase_0 = xBase_1;
    (&terms_2)->gBase_0 = gBase_1;
    (&terms_2)->hasGamma_0 = hasGamma_1;
    (&terms_2)->dyBase_0 = dyBase_1;
    (&terms_2)->rms_0 = rms_1;
    float s0_0 = *(stats_1+sBase_0);
    uint i_0;
    if(rms_1)
    {
        i_0 = sBase_0;
    }
    else
    {
        i_0 = sBase_0 + 1U;
    }
    float s1_0 = *(stats_1+i_0);
    float m1_0;
    if(rms_1)
    {
        m1_0 = 0.0;
    }
    else
    {
        m1_0 = s0_0;
    }
    (&terms_2)->mean_0 = m1_0;
    if(rms_1)
    {
        m1_0 = s0_0;
    }
    else
    {
        m1_0 = s1_0;
    }
    (&terms_2)->scale_0 = m1_0;
    if(!rms_1)
    {
        thread DxhSum_0 f_2;
        (&f_2)->terms_0 = terms_2;
        thread DxhSum_0 _S23 = f_2;
        float _S24 = groupSum_0(&_S23, x_0, gamma_1, dy_0, n_3, t_2, 0U, kernelContext_4);
        m1_0 = _S24 / float(n_3);
    }
    else
    {
        m1_0 = 0.0;
    }
    thread DxhDotH_0 g_0;
    (&g_0)->terms_1 = terms_2;
    thread DxhDotH_0 _S25 = g_0;
    float _S26 = groupSum_1(&_S25, x_0, gamma_1, dy_0, n_3, t_2, 1U, kernelContext_4);
    float _S27 = _S26 / float(n_3);
    i_0 = t_2;
    for(;;)
    {
        if(i_0 < n_3)
        {
        }
        else
        {
            break;
        }
        thread NormTerms_0 _S28 = terms_2;
        float _S29 = NormTerms_h_0(&_S28, x_0, gamma_1, dy_0, i_0);
        thread NormTerms_0 _S30 = terms_2;
        float _S31 = NormTerms_dxh_0(&_S30, x_0, gamma_1, dy_0, i_0);
        if(rms_1)
        {
            *(dx_0+(dxBase_0 + i_0)) = (&terms_2)->scale_0 * fma(- _S29, _S27, _S31);
        }
        else
        {
            *(dx_0+(dxBase_0 + i_0)) = (&terms_2)->scale_0 * fma(- _S29, _S27, _S31 - m1_0);
        }
        i_0 = i_0 + 256U;
    }
    return;
}

[[kernel]] void normBackwardInPlace(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], NormBackwardInPlaceParams_0 constant* p_1 [[buffer(4)]], float device* b_3 [[buffer(3)]], float device* gamma_2 [[buffer(1)]], float device* stats_2 [[buffer(2)]], float device* a_1 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_5;
    (&kernelContext_5)->p_0 = p_1;
    (&kernelContext_5)->b_0 = b_3;
    (&kernelContext_5)->gamma_0 = gamma_2;
    (&kernelContext_5)->stats_0 = stats_2;
    (&kernelContext_5)->a_0 = a_1;
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
    bool hasGamma_2 = (((&kernelContext_5)->p_0->flags_0) & 1U) != 0U;
    bool rms_2 = (((&kernelContext_5)->p_0->flags_0) & 4U) != 0U;
    uint aBase_0 = (&kernelContext_5)->p_0->aOff_0 + row_0 * (&kernelContext_5)->p_0->lda_0;
    uint bBase_0 = (&kernelContext_5)->p_0->bOff_0 + row_0 * (&kernelContext_5)->p_0->ldb_0;
    uint _S32 = (&kernelContext_5)->p_0->statsOff_0;
    uint _S33;
    if(rms_2)
    {
        _S33 = row_0;
    }
    else
    {
        _S33 = 2U * row_0;
    }
    uint sBase_1 = _S32 + _S33;
    if((((&kernelContext_5)->p_0->flags_0) & 2U) != 0U)
    {
        normBackwardRow_0((&kernelContext_5)->b_0, bBase_0, (&kernelContext_5)->gamma_0, (&kernelContext_5)->p_0->gOff_0, hasGamma_2, (&kernelContext_5)->stats_0, sBase_1, rms_2, (&kernelContext_5)->a_0, aBase_0, (&kernelContext_5)->a_0, aBase_0, (&kernelContext_5)->p_0->n_0, local_0.x, &kernelContext_5);
    }
    else
    {
        normBackwardRow_0((&kernelContext_5)->a_0, aBase_0, (&kernelContext_5)->gamma_0, (&kernelContext_5)->p_0->gOff_0, hasGamma_2, (&kernelContext_5)->stats_0, sBase_1, rms_2, (&kernelContext_5)->b_0, bBase_0, (&kernelContext_5)->a_0, aBase_0, (&kernelContext_5)->p_0->n_0, local_0.x, &kernelContext_5);
    }
    return;
}

