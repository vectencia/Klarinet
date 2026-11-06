/**
 * @file AudioEffect.cpp
 * @brief Implementation of AudioEffect base class.
 *
 * Currently empty — all AudioEffect functionality is defined inline in the
 * header. This translation unit exists so that the vtable and RTTI data for
 * AudioEffect are emitted in exactly one object file.
 */
#include "AudioEffect.h"

#ifdef KLARINET_DSP_RUST_EFFECTS
#include "klarinet_fx.h"

namespace klarinet {

AudioEffect::~AudioEffect() {
    if (rust_ != nullptr) {
        klarinet_fx_destroy(rust_);
    }
}

} // namespace klarinet
#endif
