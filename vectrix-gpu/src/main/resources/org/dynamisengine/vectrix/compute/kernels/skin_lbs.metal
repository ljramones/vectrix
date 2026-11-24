#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
struct SkinParams_0
{
    uint count_0;
    uint jointCount_0;
    uint jointsOff_0;
    uint jointStride_0;
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
    SkinParams_0 constant* p_0;
    int device* indices_0;
    float device* weights_0;
    float device* inPos_0;
    float device* joints_0;
    float device* outPos_0;
};

float J_0(uint field_0, uint j_0, KernelContext_0 thread* kernelContext_0)
{
    return *(kernelContext_0->joints_0+(kernelContext_0->p_0->jointsOff_0 + field_0 * kernelContext_0->p_0->jointStride_0 + j_0));
}

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

[[kernel]] void skinLbs(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], SkinParams_0 constant* p_1 [[buffer(5)]], int device* indices_1 [[buffer(1)]], float device* weights_1 [[buffer(2)]], float device* inPos_1 [[buffer(3)]], float device* joints_1 [[buffer(0)]], float device* outPos_1 [[buffer(4)]])
{
    thread KernelContext_0 kernelContext_1;
    (&kernelContext_1)->p_0 = p_1;
    (&kernelContext_1)->indices_0 = indices_1;
    (&kernelContext_1)->weights_0 = weights_1;
    (&kernelContext_1)->inPos_0 = inPos_1;
    (&kernelContext_1)->joints_0 = joints_1;
    (&kernelContext_1)->outPos_0 = outPos_1;
    uint i_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    if(i_0 >= (p_1->count_0))
    {
        return;
    }
    thread array<uint, int(4)> jk_0;
    thread array<float, int(4)> wk_0;
    jk_0[0U] = uint(*((&kernelContext_1)->indices_0+((&kernelContext_1)->p_0->idxOff_0 + i_0 * (&kernelContext_1)->p_0->idxStep_0)));
    wk_0[0U] = *((&kernelContext_1)->weights_0+((&kernelContext_1)->p_0->wOff_0 + i_0 * (&kernelContext_1)->p_0->wStep_0));
    jk_0[1U] = uint(*((&kernelContext_1)->indices_0+((&kernelContext_1)->p_0->idxOff_0 + (&kernelContext_1)->p_0->idxSlot_0 + i_0 * (&kernelContext_1)->p_0->idxStep_0)));
    wk_0[1U] = *((&kernelContext_1)->weights_0+((&kernelContext_1)->p_0->wOff_0 + (&kernelContext_1)->p_0->wSlot_0 + i_0 * (&kernelContext_1)->p_0->wStep_0));
    jk_0[2U] = uint(*((&kernelContext_1)->indices_0+((&kernelContext_1)->p_0->idxOff_0 + 2U * (&kernelContext_1)->p_0->idxSlot_0 + i_0 * (&kernelContext_1)->p_0->idxStep_0)));
    wk_0[2U] = *((&kernelContext_1)->weights_0+((&kernelContext_1)->p_0->wOff_0 + 2U * (&kernelContext_1)->p_0->wSlot_0 + i_0 * (&kernelContext_1)->p_0->wStep_0));
    jk_0[3U] = uint(*((&kernelContext_1)->indices_0+((&kernelContext_1)->p_0->idxOff_0 + 3U * (&kernelContext_1)->p_0->idxSlot_0 + i_0 * (&kernelContext_1)->p_0->idxStep_0)));
    wk_0[3U] = *((&kernelContext_1)->weights_0+((&kernelContext_1)->p_0->wOff_0 + 3U * (&kernelContext_1)->p_0->wSlot_0 + i_0 * (&kernelContext_1)->p_0->wStep_0));
    bool _S3;
    if((jk_0[int(0)]) >= ((&kernelContext_1)->p_0->jointCount_0))
    {
        _S3 = true;
    }
    else
    {
        _S3 = (jk_0[int(1)]) >= ((&kernelContext_1)->p_0->jointCount_0);
    }
    if(_S3)
    {
        _S3 = true;
    }
    else
    {
        _S3 = (jk_0[int(2)]) >= ((&kernelContext_1)->p_0->jointCount_0);
    }
    if(_S3)
    {
        _S3 = true;
    }
    else
    {
        _S3 = (jk_0[int(3)]) >= ((&kernelContext_1)->p_0->jointCount_0);
    }
    if(_S3)
    {
        return;
    }
    float _S4 = *((&kernelContext_1)->inPos_0+((&kernelContext_1)->p_0->inOff_0 + i_0));
    float _S5 = *((&kernelContext_1)->inPos_0+((&kernelContext_1)->p_0->inOff_0 + (&kernelContext_1)->p_0->inStride_0 + i_0));
    float _S6 = *((&kernelContext_1)->inPos_0+((&kernelContext_1)->p_0->inOff_0 + 2U * (&kernelContext_1)->p_0->inStride_0 + i_0));
    float _S7 = J_0(7U, jk_0[0U], &kernelContext_1);
    float px_0 = _S4 * _S7;
    float _S8 = J_0(8U, jk_0[0U], &kernelContext_1);
    float py_0 = _S5 * _S8;
    float _S9 = J_0(9U, jk_0[0U], &kernelContext_1);
    float pz_0 = _S6 * _S9;
    float _S10 = J_0(3U, jk_0[0U], &kernelContext_1);
    float _S11 = J_0(4U, jk_0[0U], &kernelContext_1);
    float _S12 = J_0(5U, jk_0[0U], &kernelContext_1);
    float _S13 = J_0(6U, jk_0[0U], &kernelContext_1);
    float tx2_0 = 2.0 * (_S11 * pz_0 - _S12 * py_0);
    float ty2_0 = 2.0 * (_S12 * px_0 - _S10 * pz_0);
    float tz2_0 = 2.0 * (_S10 * py_0 - _S11 * px_0);
    float _S14 = px_0 + _S13 * tx2_0 + (_S11 * tz2_0 - _S12 * ty2_0);
    float _S15 = J_0(0U, jk_0[0U], &kernelContext_1);
    float _S16 = add_0(0U, 0.0, (_S14 + _S15) * wk_0[0U]);
    float _S17 = py_0 + _S13 * ty2_0 + (_S12 * tx2_0 - _S10 * tz2_0);
    float _S18 = J_0(1U, jk_0[0U], &kernelContext_1);
    float _S19 = add_0(0U, 0.0, (_S17 + _S18) * wk_0[0U]);
    float _S20 = pz_0 + _S13 * tz2_0 + (_S10 * ty2_0 - _S11 * tx2_0);
    float _S21 = J_0(2U, jk_0[0U], &kernelContext_1);
    float _S22 = add_0(0U, 0.0, (_S20 + _S21) * wk_0[0U]);
    float _S23 = J_0(7U, jk_0[1U], &kernelContext_1);
    float px_1 = _S4 * _S23;
    float _S24 = J_0(8U, jk_0[1U], &kernelContext_1);
    float py_1 = _S5 * _S24;
    float _S25 = J_0(9U, jk_0[1U], &kernelContext_1);
    float pz_1 = _S6 * _S25;
    float _S26 = J_0(3U, jk_0[1U], &kernelContext_1);
    float _S27 = J_0(4U, jk_0[1U], &kernelContext_1);
    float _S28 = J_0(5U, jk_0[1U], &kernelContext_1);
    float _S29 = J_0(6U, jk_0[1U], &kernelContext_1);
    float tx2_1 = 2.0 * (_S27 * pz_1 - _S28 * py_1);
    float ty2_1 = 2.0 * (_S28 * px_1 - _S26 * pz_1);
    float tz2_1 = 2.0 * (_S26 * py_1 - _S27 * px_1);
    float _S30 = px_1 + _S29 * tx2_1 + (_S27 * tz2_1 - _S28 * ty2_1);
    float _S31 = J_0(0U, jk_0[1U], &kernelContext_1);
    float _S32 = add_0(1U, _S16, (_S30 + _S31) * wk_0[1U]);
    float _S33 = py_1 + _S29 * ty2_1 + (_S28 * tx2_1 - _S26 * tz2_1);
    float _S34 = J_0(1U, jk_0[1U], &kernelContext_1);
    float _S35 = add_0(1U, _S19, (_S33 + _S34) * wk_0[1U]);
    float _S36 = pz_1 + _S29 * tz2_1 + (_S26 * ty2_1 - _S27 * tx2_1);
    float _S37 = J_0(2U, jk_0[1U], &kernelContext_1);
    float _S38 = add_0(1U, _S22, (_S36 + _S37) * wk_0[1U]);
    float _S39 = J_0(7U, jk_0[2U], &kernelContext_1);
    float px_2 = _S4 * _S39;
    float _S40 = J_0(8U, jk_0[2U], &kernelContext_1);
    float py_2 = _S5 * _S40;
    float _S41 = J_0(9U, jk_0[2U], &kernelContext_1);
    float pz_2 = _S6 * _S41;
    float _S42 = J_0(3U, jk_0[2U], &kernelContext_1);
    float _S43 = J_0(4U, jk_0[2U], &kernelContext_1);
    float _S44 = J_0(5U, jk_0[2U], &kernelContext_1);
    float _S45 = J_0(6U, jk_0[2U], &kernelContext_1);
    float tx2_2 = 2.0 * (_S43 * pz_2 - _S44 * py_2);
    float ty2_2 = 2.0 * (_S44 * px_2 - _S42 * pz_2);
    float tz2_2 = 2.0 * (_S42 * py_2 - _S43 * px_2);
    float _S46 = px_2 + _S45 * tx2_2 + (_S43 * tz2_2 - _S44 * ty2_2);
    float _S47 = J_0(0U, jk_0[2U], &kernelContext_1);
    float _S48 = add_0(2U, _S32, (_S46 + _S47) * wk_0[2U]);
    float _S49 = py_2 + _S45 * ty2_2 + (_S44 * tx2_2 - _S42 * tz2_2);
    float _S50 = J_0(1U, jk_0[2U], &kernelContext_1);
    float _S51 = add_0(2U, _S35, (_S49 + _S50) * wk_0[2U]);
    float _S52 = pz_2 + _S45 * tz2_2 + (_S42 * ty2_2 - _S43 * tx2_2);
    float _S53 = J_0(2U, jk_0[2U], &kernelContext_1);
    float _S54 = add_0(2U, _S38, (_S52 + _S53) * wk_0[2U]);
    float _S55 = J_0(7U, jk_0[3U], &kernelContext_1);
    float px_3 = _S4 * _S55;
    float _S56 = J_0(8U, jk_0[3U], &kernelContext_1);
    float py_3 = _S5 * _S56;
    float _S57 = J_0(9U, jk_0[3U], &kernelContext_1);
    float pz_3 = _S6 * _S57;
    float _S58 = J_0(3U, jk_0[3U], &kernelContext_1);
    float _S59 = J_0(4U, jk_0[3U], &kernelContext_1);
    float _S60 = J_0(5U, jk_0[3U], &kernelContext_1);
    float _S61 = J_0(6U, jk_0[3U], &kernelContext_1);
    float tx2_3 = 2.0 * (_S59 * pz_3 - _S60 * py_3);
    float ty2_3 = 2.0 * (_S60 * px_3 - _S58 * pz_3);
    float tz2_3 = 2.0 * (_S58 * py_3 - _S59 * px_3);
    float _S62 = px_3 + _S61 * tx2_3 + (_S59 * tz2_3 - _S60 * ty2_3);
    float _S63 = J_0(0U, jk_0[3U], &kernelContext_1);
    float ox_0 = add_0(3U, _S48, (_S62 + _S63) * wk_0[3U]);
    float _S64 = py_3 + _S61 * ty2_3 + (_S60 * tx2_3 - _S58 * tz2_3);
    float _S65 = J_0(1U, jk_0[3U], &kernelContext_1);
    float oy_0 = add_0(3U, _S51, (_S64 + _S65) * wk_0[3U]);
    float _S66 = pz_3 + _S61 * tz2_3 + (_S58 * ty2_3 - _S59 * tx2_3);
    float _S67 = J_0(2U, jk_0[3U], &kernelContext_1);
    float oz_0 = add_0(3U, _S54, (_S66 + _S67) * wk_0[3U]);
    *((&kernelContext_1)->outPos_0+((&kernelContext_1)->p_0->outOff_0 + i_0)) = ox_0;
    *((&kernelContext_1)->outPos_0+((&kernelContext_1)->p_0->outOff_0 + (&kernelContext_1)->p_0->outStride_0 + i_0)) = oy_0;
    *((&kernelContext_1)->outPos_0+((&kernelContext_1)->p_0->outOff_0 + 2U * (&kernelContext_1)->p_0->outStride_0 + i_0)) = oz_0;
    return;
}

