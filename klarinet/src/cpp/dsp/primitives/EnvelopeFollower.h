#pragma once
namespace klarinet {

class EnvelopeFollower {
public:
    void prepare(float sampleRate);
    void setAttackMs(float ms);
    void setReleaseMs(float ms);
    float process(float input); // Returns envelope level
    void reset();

private:
    float attackCoeff_ = 0.0f;
    float releaseCoeff_ = 0.0f;
    float envelope_ = 0.0f;
    float sampleRate_ = 48000.0f;
    static float msToCoeff(float ms, float sampleRate);
};

} // namespace klarinet
