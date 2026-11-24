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

struct LogSoftmaxBackwardInPlaceParams_0
{
    uint rows_0;
    uint n_0;
    uint aOff_0;
    uint lda_0;
    uint bOff_0;
    uint ldb_0;
    uint dxIsDy_0;
    uint gridX_0;
};

struct KernelContext_0
{
    LogSoftmaxBackwardInPlaceParams_0 constant* p_0;
    float device* b_0;
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
        uint b_1 = b0_0 + t_0 / 8U;
        bool _S5 = b_1 < blocks_1;
        if(_S5)
        {
            uint base_0 = b_1 * 256U;
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

float jmax_0(float a_1, float b_2)
{
    if(a_1 != a_1)
    {
        return a_1;
    }
    bool _S10;
    if(a_1 == 0.0)
    {
        _S10 = b_2 == 0.0;
    }
    else
    {
        _S10 = false;
    }
    if(_S10)
    {
        _S10 = (as_type<uint>((a_1))) == 2147483648U;
    }
    else
    {
        _S10 = false;
    }
    if(_S10)
    {
        return b_2;
    }
    float _S11;
    if(a_1 >= b_2)
    {
        _S11 = a_1;
    }
    else
    {
        _S11 = b_2;
    }
    return _S11;
}

float jmin_0(float a_2, float b_3)
{
    if(a_2 != a_2)
    {
        return a_2;
    }
    bool _S12;
    if(a_2 == 0.0)
    {
        _S12 = b_3 == 0.0;
    }
    else
    {
        _S12 = false;
    }
    if(_S12)
    {
        _S12 = (as_type<uint>((b_3))) == 2147483648U;
    }
    else
    {
        _S12 = false;
    }
    if(_S12)
    {
        return b_3;
    }
    float _S13;
    if(a_2 <= b_3)
    {
        _S13 = a_2;
    }
    else
    {
        _S13 = b_3;
    }
    return _S13;
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

void logSoftmaxBackwardRow_0(float device* y_0, uint yBase_0, float device* dy_0, uint dyBase_0, float device* dx_0, uint dxBase_0, uint n_2, uint t_1, KernelContext_0 thread* kernelContext_3)
{
    thread RowSum_0 f_1;
    (&f_1)->xBase_0 = dyBase_0;
    thread RowSum_0 _S14 = f_1;
    float _S15 = groupSum_0(&_S14, dy_0, n_2, t_1, 0U, kernelContext_3);
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
        *(dx_0+(dxBase_0 + i_0)) = fma(- reproducibleExp_0(*(y_0+(yBase_0 + i_0))), _S15, *(dy_0+(dyBase_0 + i_0)));
        i_0 = i_0 + 256U;
    }
    return;
}

[[kernel]] void logSoftmaxBackwardInPlace(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], LogSoftmaxBackwardInPlaceParams_0 constant* p_1 [[buffer(2)]], float device* b_4 [[buffer(1)]], float device* a_3 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_4;
    (&kernelContext_4)->p_0 = p_1;
    (&kernelContext_4)->b_0 = b_4;
    (&kernelContext_4)->a_0 = a_3;
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
    uint aBase_0 = (&kernelContext_4)->p_0->aOff_0 + row_0 * (&kernelContext_4)->p_0->lda_0;
    uint bBase_0 = (&kernelContext_4)->p_0->bOff_0 + row_0 * (&kernelContext_4)->p_0->ldb_0;
    if(((&kernelContext_4)->p_0->dxIsDy_0) != 0U)
    {
        logSoftmaxBackwardRow_0((&kernelContext_4)->b_0, bBase_0, (&kernelContext_4)->a_0, aBase_0, (&kernelContext_4)->a_0, aBase_0, (&kernelContext_4)->p_0->n_0, local_0.x, &kernelContext_4);
    }
    else
    {
        logSoftmaxBackwardRow_0((&kernelContext_4)->a_0, aBase_0, (&kernelContext_4)->b_0, bBase_0, (&kernelContext_4)->a_0, aBase_0, (&kernelContext_4)->p_0->n_0, local_0.x, &kernelContext_4);
    }
    return;
}

