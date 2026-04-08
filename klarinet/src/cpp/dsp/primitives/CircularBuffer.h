#pragma once
#include <vector>
#include <cstdint>

namespace klarinet {

class CircularBuffer {
public:
    explicit CircularBuffer(int32_t maxSizeInSamples = 0);
    void setSize(int32_t sizeInSamples);
    void clear();
    void write(float sample);
    float read(float delaySamples) const;
    float readLinear(float delaySamples) const;
    int32_t getSize() const { return size_; }

private:
    std::vector<float> buffer_;
    int32_t writePos_ = 0;
    int32_t size_ = 0;
};

} // namespace klarinet
