#include "EffectFactory.h"
#include "effects/Gain.h"
#include "effects/Pan.h"
#include "effects/MuteSolo.h"
#include "effects/Compressor.h"
#include "effects/Limiter.h"
#include "effects/NoiseGate.h"
#include "effects/ParametricEQ.h"
#include "effects/LowPassFilter.h"
#include "effects/HighPassFilter.h"
#include "effects/BandPassFilter.h"
#include "effects/Delay.h"
#include "effects/Reverb.h"
#include "effects/Chorus.h"
#include "effects/Flanger.h"
#include "effects/Phaser.h"
#include "effects/Tremolo.h"

namespace klarinet {

std::shared_ptr<AudioEffect> EffectFactory::create(EffectType type) {
    switch (type) {
        case EffectType::Gain:          return std::make_shared<Gain>();
        case EffectType::Pan:           return std::make_shared<Pan>();
        case EffectType::MuteSolo:      return std::make_shared<MuteSolo>();
        case EffectType::Compressor:    return std::make_shared<Compressor>();
        case EffectType::Limiter:       return std::make_shared<Limiter>();
        case EffectType::NoiseGate:     return std::make_shared<NoiseGate>();
        case EffectType::ParametricEQ:  return std::make_shared<ParametricEQ>();
        case EffectType::LowPassFilter: return std::make_shared<LowPassFilter>();
        case EffectType::HighPassFilter:return std::make_shared<HighPassFilter>();
        case EffectType::BandPassFilter:return std::make_shared<BandPassFilter>();
        case EffectType::Delay:         return std::make_shared<Delay>();
        case EffectType::Reverb:        return std::make_shared<Reverb>();
        case EffectType::Chorus:        return std::make_shared<Chorus>();
        case EffectType::Flanger:       return std::make_shared<Flanger>();
        case EffectType::Phaser:        return std::make_shared<Phaser>();
        case EffectType::Tremolo:       return std::make_shared<Tremolo>();
        default:                        return nullptr;
    }
}

} // namespace klarinet
