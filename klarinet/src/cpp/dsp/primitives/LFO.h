#pragma once
namespace klarinet {

class LFO {
public:
    void prepare(float sampleRate);
    void setFrequency(float hz);
    float nextSample(); // Returns [-1.0, 1.0]
    void reset();

private:
    float phase_ = 0.0f;
    float phaseIncrement_ = 0.0f;
    float sampleRate_ = 48000.0f;
};

} // namespace klarinet
