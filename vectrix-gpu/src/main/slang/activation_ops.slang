// The float-only element-wise functions of Activations, forward and backward, selected by an op code. Each case is
// the Java loop body for one element, in the same order. Java twin: org.dynamisengine.vectrix.nn.Activations.
//
// `a` is the element of the forward input (the forward output for sigmoidBackward and tanhBackward) and `b` the
// element of the upstream gradient dy; forward ops ignore `b`. The codes match GpuActivations.Op.
import common;

static const uint RELU = 0;
static const uint SIGMOID = 1;
static const uint SILU = 2;
static const uint TANH = 3;
static const uint GELU_TANH = 4;
static const uint SOFTPLUS = 5;
static const uint RELU_BACKWARD = 6;
static const uint SIGMOID_BACKWARD = 7;
static const uint SILU_BACKWARD = 8;
static const uint TANH_BACKWARD = 9;
static const uint GELU_TANH_BACKWARD = 10;
static const uint SOFTPLUS_BACKWARD = 11;

// Activations.GELU_TANH_K = 0.7978846f (√(2/π)), GELU_TANH_C = 0.044715f, and 3f * GELU_TANH_C, as bits.
static const uint GELU_TANH_K_BITS = 0x3F4C422Au;
static const uint GELU_TANH_C_BITS = 0x3D372713u;
static const uint GELU_TANH_3C_BITS = 0x3E095D4Eu;

float activationOp(uint op, float a, float b)
{
    const float K = asfloat(GELU_TANH_K_BITS);
    const float C = asfloat(GELU_TANH_C_BITS);
    switch (op)
    {
    case RELU:
        return jmax(a, 0.0f);
    case SIGMOID:
        return reproducibleSigmoid(a);
    case SILU:
        return a * reproducibleSigmoid(a);
    case TANH:
        return reproducibleTanh(a);
    case GELU_TANH:
    {
        float u = K * fma(C * a, a * a, a);
        return 0.5f * a * (1.0f + reproducibleTanh(u));
    }
    case SOFTPLUS:
        return jmax(a, 0.0f) + reproducibleLog1p(reproducibleExp(-jabs(a)));
    case RELU_BACKWARD:
        return a > 0.0f ? b : 0.0f;
    case SIGMOID_BACKWARD:
        return b * (a * (1.0f - a));
    case SILU_BACKWARD:
    {
        float s = reproducibleSigmoid(a);
        return b * fma(a * s, 1.0f - s, s);
    }
    case TANH_BACKWARD:
        return b * ((1.0f - a) * (1.0f + a));
    case GELU_TANH_BACKWARD:
    {
        float v2 = a * a;
        float t = reproducibleTanh(K * fma(C * a, v2, a));
        float du = K * fma(asfloat(GELU_TANH_3C_BITS), v2, 1.0f);
        float d = fma(0.5f * a * ((1.0f - t) * (1.0f + t)), du, 0.5f * (1.0f + t));
        return b * d;
    }
    default: // SOFTPLUS_BACKWARD
        return b * reproducibleSigmoid(a);
    }
}
