#pragma once
namespace klarinet {

enum class BiquadType {
    LowPass, HighPass, BandPass, Notch, Peak, LowShelf, HighShelf, AllPass,
};

class Biquad {
public:
    Biquad() = default;
    void configure(BiquadType type, float frequency, float q, float gainDb, float sampleRate);
    float process(float input);
    void reset();

private:
    float b0_ = 1.0f, b1_ = 0.0f, b2_ = 0.0f;
    float a1_ = 0.0f, a2_ = 0.0f;
    float x1_ = 0.0f, x2_ = 0.0f;
    float y1_ = 0.0f, y2_ = 0.0f;
};

} // namespace klarinet
