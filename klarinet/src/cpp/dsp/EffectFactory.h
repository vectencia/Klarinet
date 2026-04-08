/**
 * @file EffectFactory.h
 * @brief Factory for creating concrete AudioEffect instances by EffectType.
 *
 * The EffectFactory centralises construction of all built-in effect types. It
 * maps each EffectType enum value to the corresponding concrete class (Gain,
 * Pan, Compressor, etc.) and returns a shared_ptr to the newly created effect.
 *
 * This factory is the single point through which the C API (klarinet_dsp.h) and
 * higher-level Kotlin code create effects, ensuring consistent initialisation.
 */
#pragma once
#include "AudioEffect.h"
#include <memory>

namespace klarinet {

/**
 * @class EffectFactory
 * @brief Creates AudioEffect instances by EffectType tag.
 */
class EffectFactory {
public:
    /**
     * @brief Create an AudioEffect of the given type.
     *
     * @param type The EffectType identifying which concrete effect to instantiate.
     * @return A shared_ptr to the new effect, or nullptr if the type is unknown
     *         (e.g., EffectType::Count or an out-of-range value).
     */
    static std::shared_ptr<AudioEffect> create(EffectType type);
};

} // namespace klarinet
