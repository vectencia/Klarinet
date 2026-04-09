#pragma once
#include "AudioEffect.h"
#include <memory>

namespace klarinet {

class EffectFactory {
public:
    static std::shared_ptr<AudioEffect> create(EffectType type);
};

} // namespace klarinet
