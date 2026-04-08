/**
 * @file EffectChain.h
 * @brief Manages an ordered list of AudioEffects with lock-free hot-swap.
 *
 * The EffectChain owns a sequence of AudioEffect instances and processes audio
 * through them in order. It is designed for a two-thread model:
 *
 * - **Audio thread** calls process() — must be real-time safe.
 * - **Control thread** calls addEffect(), removeEffect(), reorderEffects(),
 *   clear(), enqueueParameterChange(), and prepare().
 *
 * ## Lock-free hot-swap mechanism
 *
 * Mutations (add / remove / reorder / clear) do **not** lock a mutex that the
 * audio thread ever touches. Instead, the control thread:
 *   1. Copies the current EffectList under a mutex.
 *   2. Applies the mutation to the copy.
 *   3. Atomically swaps the active pointer (acq_rel exchange).
 *   4. Stores the old pointer in pendingDelete_ for deferred cleanup.
 *
 * The audio thread reads the active pointer with acquire semantics and iterates
 * the list. It also deletes pendingDelete_ at the start of each process() call,
 * which is safe because only the audio thread reads activeChain_ — once swapped,
 * the old list is unreachable to any other reader.
 *
 * ## Lock-free parameter changes
 *
 * Parameter updates from the control thread are enqueued into a SPSC RingBuffer
 * (paramQueue_) and drained by the audio thread at the top of process(), which
 * then calls setParameter() on the target effects.
 */
#pragma once
#include "AudioEffect.h"
#include "RingBuffer.h"
#include <vector>
#include <memory>
#include <atomic>
#include <mutex>

namespace klarinet {

/**
 * @struct ParameterChange
 * @brief A deferred parameter update queued from the control thread to the audio thread.
 */
struct ParameterChange {
    AudioEffect* effect;  ///< Target effect (raw pointer — the EffectChain owns the shared_ptr).
    int32_t paramId;      ///< Effect-specific parameter identifier.
    float value;          ///< New parameter value.
};

/**
 * @class EffectChain
 * @brief An ordered chain of AudioEffects with lock-free audio-thread access.
 *
 * @see AudioEffect, EffectFactory, RingBuffer
 */
class EffectChain {
public:
    /// Construct an empty EffectChain.
    EffectChain();

    /// Destructor. Frees the active chain and any pending-delete list.
    ~EffectChain();

    // -----------------------------------------------------------------------
    // Audio thread
    // -----------------------------------------------------------------------

    /**
     * @brief Process a buffer of interleaved audio through every enabled effect.
     *
     * Called on the **audio thread** — must be real-time safe. Before iterating
     * effects, this method applies pending parameter changes and cleans up any
     * previously swapped-out EffectList.
     *
     * @param buffer       Interleaved audio samples (modified in-place).
     * @param numFrames    Number of audio frames.
     * @param channelCount Number of channels per frame.
     */
    void process(float* buffer, int32_t numFrames, int32_t channelCount);

    // -----------------------------------------------------------------------
    // Control thread
    // -----------------------------------------------------------------------

    /**
     * @brief Append an effect to the end of the chain.
     *
     * The effect is prepared with the chain's current sample rate and channel
     * count before insertion. Thread-safe (takes controlMutex_).
     *
     * @param effect Shared ownership of the effect to add.
     */
    void addEffect(std::shared_ptr<AudioEffect> effect);

    /**
     * @brief Remove a specific effect from the chain.
     *
     * The shared_ptr is dropped from the chain, but the effect may remain alive
     * if other owners exist. Thread-safe (takes controlMutex_).
     *
     * @param effect Raw pointer identifying the effect to remove.
     */
    void removeEffect(AudioEffect* effect);

    /**
     * @brief Reorder the effects in the chain according to the given sequence.
     *
     * Effects not present in @p newOrder are silently dropped. Thread-safe
     * (takes controlMutex_).
     *
     * @param newOrder Desired effect order as raw pointers.
     */
    void reorderEffects(const std::vector<AudioEffect*>& newOrder);

    /**
     * @brief Remove all effects from the chain.
     *
     * Thread-safe (takes controlMutex_).
     */
    void clear();

    /**
     * @brief Enqueue a parameter change for lock-free delivery to the audio thread.
     *
     * The change is written into a SPSC ring buffer and applied at the start of
     * the next process() call. If the ring buffer is full, the change is silently
     * dropped.
     *
     * @param effect  Target effect.
     * @param paramId Parameter identifier.
     * @param value   New parameter value.
     */
    void enqueueParameterChange(AudioEffect* effect, int32_t paramId, float value);

    /**
     * @brief Prepare all effects in the chain for a new audio format.
     *
     * Stores the sample rate and channel count, then forwards the call to every
     * effect. Thread-safe (takes controlMutex_).
     *
     * @param sampleRate   Sample rate in Hz.
     * @param channelCount Number of audio channels.
     */
    void prepare(int32_t sampleRate, int32_t channelCount);

    /**
     * @brief Get the number of effects currently in the chain.
     * @return Effect count (may be stale by the time the caller uses it).
     */
    int32_t getEffectCount() const;

private:
    /// Convenience typedef for the vector of shared effect pointers.
    using EffectList = std::vector<std::shared_ptr<AudioEffect>>;

    /**
     * @brief Atomically replace the active chain and defer deletion of the old one.
     *
     * Uses acq_rel exchange on activeChain_ and stores the old pointer in
     * pendingDelete_ for cleanup on the next audio-thread process() call.
     *
     * @param newChain Heap-allocated EffectList to install (ownership transferred).
     */
    void swapChain(EffectList* newChain);

    /**
     * @brief Drain the parameter queue and apply changes; clean up pendingDelete_.
     *
     * Called at the start of process() on the **audio thread**.
     */
    void applyPendingChanges();

    std::atomic<EffectList*> activeChain_;   ///< Currently active effect list (audio thread reads this).
    EffectList* pendingDelete_ = nullptr;    ///< Previously active list awaiting deferred deletion by the audio thread.
    std::mutex controlMutex_;                ///< Serialises control-thread mutations (never held by the audio thread).
    RingBuffer<ParameterChange, 512> paramQueue_; ///< Lock-free SPSC queue for parameter changes (control -> audio).
    int32_t sampleRate_ = 48000;             ///< Current sample rate cached for preparing new effects.
    int32_t channelCount_ = 1;               ///< Current channel count cached for preparing new effects.
};

} // namespace klarinet
