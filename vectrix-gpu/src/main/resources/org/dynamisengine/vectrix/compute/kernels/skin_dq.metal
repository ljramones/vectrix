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

float J_1(uint field_1, uint j_1, KernelContext_0 thread* kernelContext_1)
{
    return *(kernelContext_1->joints_0+(kernelContext_1->p_0->jointsOff_0 + field_1 * kernelContext_1->p_0->jointStride_0 + j_1));
}

float signForReference_0(float refX_0, float refY_0, float refZ_0, float refW_0, uint j_2, KernelContext_0 thread* kernelContext_2)
{
    float _S1 = J_1(0U, j_2, kernelContext_2);
    float _S2 = J_1(1U, j_2, kernelContext_2);
    float _S3 = J_1(2U, j_2, kernelContext_2);
    float _S4 = J_1(3U, j_2, kernelContext_2);
    float _S5;
    if((refX_0 * _S1 + refY_0 * _S2 + refZ_0 * _S3 + refW_0 * _S4) < 0.0)
    {
        _S5 = -1.0;
    }
    else
    {
        _S5 = 1.0;
    }
    return _S5;
}

float blend_0(uint field_2, uint j0_0, uint j1_0, uint j2_0, uint j3_0, float sw0_0, float sw1_0, float sw2_0, float sw3_0, KernelContext_0 thread* kernelContext_3)
{
    float _S6 = J_1(field_2, j0_0, kernelContext_3);
    float _S7 = _S6 * sw0_0;
    float _S8 = J_1(field_2, j1_0, kernelContext_3);
    float _S9 = _S7 + _S8 * sw1_0;
    float _S10 = J_1(field_2, j2_0, kernelContext_3);
    float _S11 = _S9 + _S10 * sw2_0;
    float _S12 = J_1(field_2, j3_0, kernelContext_3);
    return _S11 + _S12 * sw3_0;
}

uint firstbithigh_0(uint value_0)
{
    uint _S13;
    if(int(value_0) < int(0))
    {
        _S13 = ~value_0;
    }
    else
    {
        _S13 = value_0;
    }
    if(_S13 == 0U)
    {
        return 4294967295U;
    }
    uint _S14 = clz(_S13);
    return 31U - _S14;
}

uint2 engineMulWide_0(uint x_0, uint y_0)
{
    uint x0_0 = x_0 & 65535U;
    uint x1_0 = x_0 >> 16U;
    uint y0_0 = y_0 & 65535U;
    uint y1_0 = y_0 >> 16U;
    uint p01_0 = x0_0 * y1_0;
    uint p10_0 = x1_0 * y0_0;
    return uint2(x_0 * y_0, x1_0 * y1_0 + (p01_0 >> 16U) + (p10_0 >> 16U) + ((((x0_0 * y0_0) >> 16U) + (p01_0 & 65535U) + (p10_0 & 65535U)) >> 16U));
}

bool belowTwoPow73_0(uint r_0, uint m_0)
{
    uint2 m2_0 = engineMulWide_0(m_0, m_0);
    uint2 b_0 = engineMulWide_0(r_0, m2_0.y);
    uint _S15 = engineMulWide_0(r_0, m2_0.x).y;
    uint carry_0;
    if((_S15 + b_0.x) < _S15)
    {
        carry_0 = 1U;
    }
    else
    {
        carry_0 = 0U;
    }
    return (b_0.y + carry_0) < 512U;
}

float vectrixInvsqrt_0(float x_1)
{
    if(!(x_1 > 0.0))
    {
        float _S16;
        if(x_1 == 0.0)
        {
            if((as_type<uint>((x_1))) != 0U)
            {
                _S16 = (as_type<float>((4286578688U)));
            }
            else
            {
                _S16 = (as_type<float>((2139095040U)));
            }
        }
        else
        {
            _S16 = (as_type<float>((2143289344U)));
        }
        return _S16;
    }
    if(x_1 == (as_type<float>((2139095040U))))
    {
        return 0.0;
    }
    uint bits_0 = (as_type<uint>((x_1)));
    int ef_0 = int(bits_0 >> 23U);
    uint mant_0 = bits_0 & 8388607U;
    int e_0;
    uint mant_1;
    if(ef_0 == int(0))
    {
        int shift_0 = int(23) - int(firstbithigh_0(mant_0));
        uint _S17 = (mant_0 << uint(shift_0)) & 8388607U;
        e_0 = int(-126) - shift_0;
        mant_1 = _S17;
    }
    else
    {
        e_0 = ef_0 - int(127);
        mant_1 = mant_0;
    }
    int k_0 = e_0 >> 1U;
    int par_0 = e_0 - int(2) * k_0;
    uint _S18 = (mant_1 | 8388608U) << uint(par_0);
    uint _S19 = uint(1.0 / sqrt((as_type<float>(((uint(int(127) + par_0) << 23U) | mant_1)))) * 1.6777216e+07);
    bool _S20;
    if(_S19 < 16777216U)
    {
        _S20 = belowTwoPow73_0(_S18, 2U * _S19 + 1U);
    }
    else
    {
        _S20 = false;
    }
    uint y_1;
    if(_S20)
    {
        y_1 = _S19 + 1U;
    }
    else
    {
        y_1 = _S19;
    }
    if(y_1 < 16777216U)
    {
        _S20 = belowTwoPow73_0(_S18, 2U * y_1 + 1U);
    }
    else
    {
        _S20 = false;
    }
    if(_S20)
    {
        y_1 = y_1 + 1U;
    }
    if(y_1 > 8388608U)
    {
        _S20 = !belowTwoPow73_0(_S18, 2U * y_1 - 1U);
    }
    else
    {
        _S20 = false;
    }
    if(_S20)
    {
        y_1 = y_1 - 1U;
    }
    if(y_1 > 8388608U)
    {
        _S20 = !belowTwoPow73_0(_S18, 2U * y_1 - 1U);
    }
    else
    {
        _S20 = false;
    }
    if(_S20)
    {
        y_1 = y_1 - 1U;
    }
    return float(y_1) * (as_type<float>((864026624U))) * (as_type<float>((uint(int(127) - k_0) << 23U)));
}

[[kernel]] void skinDualQuat(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], SkinParams_0 constant* p_1 [[buffer(5)]], int device* indices_1 [[buffer(1)]], float device* weights_1 [[buffer(2)]], float device* inPos_1 [[buffer(3)]], float device* joints_1 [[buffer(0)]], float device* outPos_1 [[buffer(4)]])
{
    thread KernelContext_0 kernelContext_4;
    (&kernelContext_4)->p_0 = p_1;
    (&kernelContext_4)->indices_0 = indices_1;
    (&kernelContext_4)->weights_0 = weights_1;
    (&kernelContext_4)->inPos_0 = inPos_1;
    (&kernelContext_4)->joints_0 = joints_1;
    (&kernelContext_4)->outPos_0 = outPos_1;
    uint i_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    if(i_0 >= (p_1->count_0))
    {
        return;
    }
    uint j0_1 = uint(*((&kernelContext_4)->indices_0+((&kernelContext_4)->p_0->idxOff_0 + i_0 * (&kernelContext_4)->p_0->idxStep_0)));
    uint j1_1 = uint(*((&kernelContext_4)->indices_0+((&kernelContext_4)->p_0->idxOff_0 + (&kernelContext_4)->p_0->idxSlot_0 + i_0 * (&kernelContext_4)->p_0->idxStep_0)));
    uint j2_1 = uint(*((&kernelContext_4)->indices_0+((&kernelContext_4)->p_0->idxOff_0 + 2U * (&kernelContext_4)->p_0->idxSlot_0 + i_0 * (&kernelContext_4)->p_0->idxStep_0)));
    uint j3_1 = uint(*((&kernelContext_4)->indices_0+((&kernelContext_4)->p_0->idxOff_0 + 3U * (&kernelContext_4)->p_0->idxSlot_0 + i_0 * (&kernelContext_4)->p_0->idxStep_0)));
    bool _S21;
    if(j0_1 >= ((&kernelContext_4)->p_0->jointCount_0))
    {
        _S21 = true;
    }
    else
    {
        _S21 = j1_1 >= ((&kernelContext_4)->p_0->jointCount_0);
    }
    if(_S21)
    {
        _S21 = true;
    }
    else
    {
        _S21 = j2_1 >= ((&kernelContext_4)->p_0->jointCount_0);
    }
    if(_S21)
    {
        _S21 = true;
    }
    else
    {
        _S21 = j3_1 >= ((&kernelContext_4)->p_0->jointCount_0);
    }
    if(_S21)
    {
        return;
    }
    float device* _S22 = (&kernelContext_4)->weights_0+((&kernelContext_4)->p_0->wOff_0 + i_0 * (&kernelContext_4)->p_0->wStep_0);
    float device* _S23 = (&kernelContext_4)->weights_0+((&kernelContext_4)->p_0->wOff_0 + (&kernelContext_4)->p_0->wSlot_0 + i_0 * (&kernelContext_4)->p_0->wStep_0);
    float device* _S24 = (&kernelContext_4)->weights_0+((&kernelContext_4)->p_0->wOff_0 + 2U * (&kernelContext_4)->p_0->wSlot_0 + i_0 * (&kernelContext_4)->p_0->wStep_0);
    float device* _S25 = (&kernelContext_4)->weights_0+((&kernelContext_4)->p_0->wOff_0 + 3U * (&kernelContext_4)->p_0->wSlot_0 + i_0 * (&kernelContext_4)->p_0->wStep_0);
    float x_2 = *((&kernelContext_4)->inPos_0+((&kernelContext_4)->p_0->inOff_0 + i_0));
    float y_2 = *((&kernelContext_4)->inPos_0+((&kernelContext_4)->p_0->inOff_0 + (&kernelContext_4)->p_0->inStride_0 + i_0));
    float z_0 = *((&kernelContext_4)->inPos_0+((&kernelContext_4)->p_0->inOff_0 + 2U * (&kernelContext_4)->p_0->inStride_0 + i_0));
    float _S26 = J_0(0U, j0_1, &kernelContext_4);
    float _S27 = J_0(1U, j0_1, &kernelContext_4);
    float _S28 = J_0(2U, j0_1, &kernelContext_4);
    float _S29 = J_0(3U, j0_1, &kernelContext_4);
    float _S30 = signForReference_0(_S26, _S27, _S28, _S29, j0_1, &kernelContext_4);
    float _S31 = signForReference_0(_S26, _S27, _S28, _S29, j1_1, &kernelContext_4);
    float _S32 = signForReference_0(_S26, _S27, _S28, _S29, j2_1, &kernelContext_4);
    float _S33 = signForReference_0(_S26, _S27, _S28, _S29, j3_1, &kernelContext_4);
    float sw0_1 = _S30 * *_S22;
    float sw1_1 = _S31 * *_S23;
    float sw2_1 = _S32 * *_S24;
    float sw3_1 = _S33 * *_S25;
    float _S34 = blend_0(0U, j0_1, j1_1, j2_1, j3_1, sw0_1, sw1_1, sw2_1, sw3_1, &kernelContext_4);
    float _S35 = blend_0(1U, j0_1, j1_1, j2_1, j3_1, sw0_1, sw1_1, sw2_1, sw3_1, &kernelContext_4);
    float _S36 = blend_0(2U, j0_1, j1_1, j2_1, j3_1, sw0_1, sw1_1, sw2_1, sw3_1, &kernelContext_4);
    float _S37 = blend_0(3U, j0_1, j1_1, j2_1, j3_1, sw0_1, sw1_1, sw2_1, sw3_1, &kernelContext_4);
    float _S38 = blend_0(4U, j0_1, j1_1, j2_1, j3_1, sw0_1, sw1_1, sw2_1, sw3_1, &kernelContext_4);
    float _S39 = blend_0(5U, j0_1, j1_1, j2_1, j3_1, sw0_1, sw1_1, sw2_1, sw3_1, &kernelContext_4);
    float _S40 = blend_0(6U, j0_1, j1_1, j2_1, j3_1, sw0_1, sw1_1, sw2_1, sw3_1, &kernelContext_4);
    float _S41 = blend_0(7U, j0_1, j1_1, j2_1, j3_1, sw0_1, sw1_1, sw2_1, sw3_1, &kernelContext_4);
    float norm2_0 = _S34 * _S34 + _S35 * _S35 + _S36 * _S36 + _S37 * _S37;
    if(norm2_0 == 0.0)
    {
        *((&kernelContext_4)->outPos_0+((&kernelContext_4)->p_0->outOff_0 + i_0)) = x_2;
        *((&kernelContext_4)->outPos_0+((&kernelContext_4)->p_0->outOff_0 + (&kernelContext_4)->p_0->outStride_0 + i_0)) = y_2;
        *((&kernelContext_4)->outPos_0+((&kernelContext_4)->p_0->outOff_0 + 2U * (&kernelContext_4)->p_0->outStride_0 + i_0)) = z_0;
        return;
    }
    float invNorm_0 = vectrixInvsqrt_0(norm2_0);
    float arx_0 = _S34 * invNorm_0;
    float ary_0 = _S35 * invNorm_0;
    float arz_0 = _S36 * invNorm_0;
    float arw_0 = _S37 * invNorm_0;
    float adx_0 = _S38 * invNorm_0;
    float ady_0 = _S39 * invNorm_0;
    float adz_0 = _S40 * invNorm_0;
    float tx2_0 = 2.0 * (ary_0 * z_0 - arz_0 * y_2);
    float ty2_0 = 2.0 * (arz_0 * x_2 - arx_0 * z_0);
    float tz2_0 = 2.0 * (arx_0 * y_2 - ary_0 * x_2);
    float ry_0 = y_2 + arw_0 * ty2_0 + (arz_0 * tx2_0 - arx_0 * tz2_0);
    float rz_0 = z_0 + arw_0 * tz2_0 + (arx_0 * ty2_0 - ary_0 * tx2_0);
    float _S42 = - (_S41 * invNorm_0);
    float ty_0 = 2.0 * (_S42 * ary_0 + adx_0 * arz_0 + ady_0 * arw_0 - adz_0 * arx_0);
    float tz_0 = 2.0 * (_S42 * arz_0 - adx_0 * ary_0 + ady_0 * arx_0 + adz_0 * arw_0);
    *((&kernelContext_4)->outPos_0+((&kernelContext_4)->p_0->outOff_0 + i_0)) = x_2 + arw_0 * tx2_0 + (ary_0 * tz2_0 - arz_0 * ty2_0) + 2.0 * (_S42 * arx_0 + adx_0 * arw_0 - ady_0 * arz_0 + adz_0 * ary_0);
    *((&kernelContext_4)->outPos_0+((&kernelContext_4)->p_0->outOff_0 + (&kernelContext_4)->p_0->outStride_0 + i_0)) = ry_0 + ty_0;
    *((&kernelContext_4)->outPos_0+((&kernelContext_4)->p_0->outOff_0 + 2U * (&kernelContext_4)->p_0->outStride_0 + i_0)) = rz_0 + tz_0;
    return;
}

