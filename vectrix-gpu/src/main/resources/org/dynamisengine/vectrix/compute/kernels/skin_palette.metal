#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
float plusZero_0(float v_0)
{
    float _S1;
    if(v_0 == 0.0)
    {
        _S1 = (as_type<float>(((as_type<uint>((v_0))) & 2147483647U)));
    }
    else
    {
        _S1 = v_0;
    }
    return _S1;
}

float add_0(uint k_0, float sum_0, float v_1)
{
    float _S2;
    if(k_0 == 0U)
    {
        _S2 = plusZero_0(v_1);
    }
    else
    {
        _S2 = sum_0 + v_1;
    }
    return _S2;
}

struct PaletteSkinParams_0
{
    uint count_0;
    uint jointCount_0;
    uint palOff_0;
    uint idxOff_0;
    uint idxSlot_0;
    uint idxStep_0;
    uint wOff_0;
    uint wSlot_0;
    uint wStep_0;
    uint inOff_0;
    uint inStride_0;
    uint outOff_0;
    uint outStride_0;
    uint gridX_0;
};

struct KernelContext_0
{
    PaletteSkinParams_0 constant* p_0;
    int device* indices_0;
    float device* weights_0;
    float device* inPos_0;
    float device* palette_0;
    float device* outPos_0;
};

[[kernel]] void skinPalette(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], PaletteSkinParams_0 constant* p_1 [[buffer(5)]], int device* indices_1 [[buffer(1)]], float device* weights_1 [[buffer(2)]], float device* inPos_1 [[buffer(3)]], float device* palette_1 [[buffer(0)]], float device* outPos_1 [[buffer(4)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->indices_0 = indices_1;
    (&kernelContext_0)->weights_0 = weights_1;
    (&kernelContext_0)->inPos_0 = inPos_1;
    (&kernelContext_0)->palette_0 = palette_1;
    (&kernelContext_0)->outPos_0 = outPos_1;
    uint i_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    if(i_0 >= (p_1->count_0))
    {
        return;
    }
    thread array<uint, int(4)> jk_0;
    thread array<float, int(4)> wk_0;
    jk_0[0U] = uint(*((&kernelContext_0)->indices_0+((&kernelContext_0)->p_0->idxOff_0 + i_0 * (&kernelContext_0)->p_0->idxStep_0)));
    wk_0[0U] = *((&kernelContext_0)->weights_0+((&kernelContext_0)->p_0->wOff_0 + i_0 * (&kernelContext_0)->p_0->wStep_0));
    jk_0[1U] = uint(*((&kernelContext_0)->indices_0+((&kernelContext_0)->p_0->idxOff_0 + (&kernelContext_0)->p_0->idxSlot_0 + i_0 * (&kernelContext_0)->p_0->idxStep_0)));
    wk_0[1U] = *((&kernelContext_0)->weights_0+((&kernelContext_0)->p_0->wOff_0 + (&kernelContext_0)->p_0->wSlot_0 + i_0 * (&kernelContext_0)->p_0->wStep_0));
    jk_0[2U] = uint(*((&kernelContext_0)->indices_0+((&kernelContext_0)->p_0->idxOff_0 + 2U * (&kernelContext_0)->p_0->idxSlot_0 + i_0 * (&kernelContext_0)->p_0->idxStep_0)));
    wk_0[2U] = *((&kernelContext_0)->weights_0+((&kernelContext_0)->p_0->wOff_0 + 2U * (&kernelContext_0)->p_0->wSlot_0 + i_0 * (&kernelContext_0)->p_0->wStep_0));
    jk_0[3U] = uint(*((&kernelContext_0)->indices_0+((&kernelContext_0)->p_0->idxOff_0 + 3U * (&kernelContext_0)->p_0->idxSlot_0 + i_0 * (&kernelContext_0)->p_0->idxStep_0)));
    wk_0[3U] = *((&kernelContext_0)->weights_0+((&kernelContext_0)->p_0->wOff_0 + 3U * (&kernelContext_0)->p_0->wSlot_0 + i_0 * (&kernelContext_0)->p_0->wStep_0));
    bool _S3;
    if((jk_0[int(0)]) >= ((&kernelContext_0)->p_0->jointCount_0))
    {
        _S3 = true;
    }
    else
    {
        _S3 = (jk_0[int(1)]) >= ((&kernelContext_0)->p_0->jointCount_0);
    }
    if(_S3)
    {
        _S3 = true;
    }
    else
    {
        _S3 = (jk_0[int(2)]) >= ((&kernelContext_0)->p_0->jointCount_0);
    }
    if(_S3)
    {
        _S3 = true;
    }
    else
    {
        _S3 = (jk_0[int(3)]) >= ((&kernelContext_0)->p_0->jointCount_0);
    }
    if(_S3)
    {
        return;
    }
    float _S4 = *((&kernelContext_0)->inPos_0+((&kernelContext_0)->p_0->inOff_0 + i_0));
    float _S5 = *((&kernelContext_0)->inPos_0+((&kernelContext_0)->p_0->inOff_0 + (&kernelContext_0)->p_0->inStride_0 + i_0));
    float _S6 = *((&kernelContext_0)->inPos_0+((&kernelContext_0)->p_0->inOff_0 + 2U * (&kernelContext_0)->p_0->inStride_0 + i_0));
    uint m_0 = (&kernelContext_0)->p_0->palOff_0 + jk_0[0U] * 12U;
    uint m_1 = (&kernelContext_0)->p_0->palOff_0 + jk_0[1U] * 12U;
    uint m_2 = (&kernelContext_0)->p_0->palOff_0 + jk_0[2U] * 12U;
    uint m_3 = (&kernelContext_0)->p_0->palOff_0 + jk_0[3U] * 12U;
    float oy_0 = add_0(3U, add_0(2U, add_0(1U, add_0(0U, 0.0, (*((&kernelContext_0)->palette_0+(m_0 + 4U)) * _S4 + *((&kernelContext_0)->palette_0+(m_0 + 5U)) * _S5 + *((&kernelContext_0)->palette_0+(m_0 + 6U)) * _S6 + *((&kernelContext_0)->palette_0+(m_0 + 7U))) * wk_0[0U]), (*((&kernelContext_0)->palette_0+(m_1 + 4U)) * _S4 + *((&kernelContext_0)->palette_0+(m_1 + 5U)) * _S5 + *((&kernelContext_0)->palette_0+(m_1 + 6U)) * _S6 + *((&kernelContext_0)->palette_0+(m_1 + 7U))) * wk_0[1U]), (*((&kernelContext_0)->palette_0+(m_2 + 4U)) * _S4 + *((&kernelContext_0)->palette_0+(m_2 + 5U)) * _S5 + *((&kernelContext_0)->palette_0+(m_2 + 6U)) * _S6 + *((&kernelContext_0)->palette_0+(m_2 + 7U))) * wk_0[2U]), (*((&kernelContext_0)->palette_0+(m_3 + 4U)) * _S4 + *((&kernelContext_0)->palette_0+(m_3 + 5U)) * _S5 + *((&kernelContext_0)->palette_0+(m_3 + 6U)) * _S6 + *((&kernelContext_0)->palette_0+(m_3 + 7U))) * wk_0[3U]);
    float oz_0 = add_0(3U, add_0(2U, add_0(1U, add_0(0U, 0.0, (*((&kernelContext_0)->palette_0+(m_0 + 8U)) * _S4 + *((&kernelContext_0)->palette_0+(m_0 + 9U)) * _S5 + *((&kernelContext_0)->palette_0+(m_0 + 10U)) * _S6 + *((&kernelContext_0)->palette_0+(m_0 + 11U))) * wk_0[0U]), (*((&kernelContext_0)->palette_0+(m_1 + 8U)) * _S4 + *((&kernelContext_0)->palette_0+(m_1 + 9U)) * _S5 + *((&kernelContext_0)->palette_0+(m_1 + 10U)) * _S6 + *((&kernelContext_0)->palette_0+(m_1 + 11U))) * wk_0[1U]), (*((&kernelContext_0)->palette_0+(m_2 + 8U)) * _S4 + *((&kernelContext_0)->palette_0+(m_2 + 9U)) * _S5 + *((&kernelContext_0)->palette_0+(m_2 + 10U)) * _S6 + *((&kernelContext_0)->palette_0+(m_2 + 11U))) * wk_0[2U]), (*((&kernelContext_0)->palette_0+(m_3 + 8U)) * _S4 + *((&kernelContext_0)->palette_0+(m_3 + 9U)) * _S5 + *((&kernelContext_0)->palette_0+(m_3 + 10U)) * _S6 + *((&kernelContext_0)->palette_0+(m_3 + 11U))) * wk_0[3U]);
    *((&kernelContext_0)->outPos_0+((&kernelContext_0)->p_0->outOff_0 + i_0)) = add_0(3U, add_0(2U, add_0(1U, add_0(0U, 0.0, (*((&kernelContext_0)->palette_0+m_0) * _S4 + *((&kernelContext_0)->palette_0+(m_0 + 1U)) * _S5 + *((&kernelContext_0)->palette_0+(m_0 + 2U)) * _S6 + *((&kernelContext_0)->palette_0+(m_0 + 3U))) * wk_0[0U]), (*((&kernelContext_0)->palette_0+m_1) * _S4 + *((&kernelContext_0)->palette_0+(m_1 + 1U)) * _S5 + *((&kernelContext_0)->palette_0+(m_1 + 2U)) * _S6 + *((&kernelContext_0)->palette_0+(m_1 + 3U))) * wk_0[1U]), (*((&kernelContext_0)->palette_0+m_2) * _S4 + *((&kernelContext_0)->palette_0+(m_2 + 1U)) * _S5 + *((&kernelContext_0)->palette_0+(m_2 + 2U)) * _S6 + *((&kernelContext_0)->palette_0+(m_2 + 3U))) * wk_0[2U]), (*((&kernelContext_0)->palette_0+m_3) * _S4 + *((&kernelContext_0)->palette_0+(m_3 + 1U)) * _S5 + *((&kernelContext_0)->palette_0+(m_3 + 2U)) * _S6 + *((&kernelContext_0)->palette_0+(m_3 + 3U))) * wk_0[3U]);
    *((&kernelContext_0)->outPos_0+((&kernelContext_0)->p_0->outOff_0 + (&kernelContext_0)->p_0->outStride_0 + i_0)) = oy_0;
    *((&kernelContext_0)->outPos_0+((&kernelContext_0)->p_0->outOff_0 + 2U * (&kernelContext_0)->p_0->outStride_0 + i_0)) = oz_0;
    return;
}

