#ifndef HAND_EYE_COORDINATION_HPP
#define HAND_EYE_COORDINATION_HPP

#include <string>
#include <vector>
#include <cstdint>
#include <cmath>

namespace coordination {

// ============================================================
// Hand-Eye Coordination Module
//
// Models input from visual (eye) tracking and hand/touch
// input devices to synchronize actions between what the
// user sees and what their hands do.
//
// Use cases:
//   - Touch screen calibration (iPhone / Android)
//   - Gesture recognition and response timing
//   - Input latency measurement and compensation
//   - Accessibility: adaptive sensitivity for motor control
//   - Gaming / interactive UI responsiveness
//
// Components:
//   [Eye/Visual Input] --> [Coordination Engine] <-- [Hand/Touch Input]
//                                  |
//                           [Calibrated Output]
// ============================================================

// --- 2D Point ---

struct Point2D {
    float x;
    float y;

    Point2D() : x(0.0f), y(0.0f) {}
    Point2D(float px, float py) : x(px), y(py) {}

    float distance_to(const Point2D& other) const {
        float dx = x - other.x;
        float dy = y - other.y;
        return std::sqrt(dx * dx + dy * dy);
    }
};

// --- Input Source ---

enum class InputSource : uint8_t {
    TOUCH_SCREEN   = 0,
    MOUSE          = 1,
    TRACKPAD       = 2,
    STYLUS         = 3,
    GESTURE_CAMERA = 4
};

// --- Eye Tracking Data ---

struct EyeTrackingData {
    Point2D gaze_point;         // where the user is looking on screen
    float   confidence;         // 0.0 - 1.0, tracking confidence
    bool    is_fixated;         // true if gaze is stable (not saccading)
    uint32_t timestamp_ms;

    EyeTrackingData()
        : gaze_point()
        , confidence(0.0f)
        , is_fixated(false)
        , timestamp_ms(0)
    {}
};

// --- Hand Input Data ---

struct HandInputData {
    Point2D  touch_point;       // where the hand/finger is on screen
    float    pressure;          // 0.0 - 1.0, touch pressure
    float    velocity;          // speed of hand movement (px/ms)
    InputSource source;
    uint32_t timestamp_ms;

    HandInputData()
        : touch_point()
        , pressure(0.0f)
        , velocity(0.0f)
        , source(InputSource::TOUCH_SCREEN)
        , timestamp_ms(0)
    {}
};

// --- Coordination Accuracy ---

enum class AccuracyLevel : uint8_t {
    EXCELLENT  = 0,  // < 5px offset
    GOOD       = 1,  // 5-15px offset
    FAIR       = 2,  // 15-30px offset
    POOR       = 3,  // 30-50px offset
    MISALIGNED = 4   // > 50px offset
};

// --- Coordination Result ---

struct CoordinationResult {
    float          offset_px;       // distance between gaze and touch
    AccuracyLevel  accuracy;
    uint32_t       latency_ms;      // time between eye fixation and hand action
    bool           synchronized;    // true if eye and hand are working together
    std::string    feedback;

    CoordinationResult()
        : offset_px(0.0f)
        , accuracy(AccuracyLevel::MISALIGNED)
        , latency_ms(0)
        , synchronized(false)
        , feedback("")
    {}
};

// --- Calibration Profile ---

struct CalibrationProfile {
    std::string name;
    float       sensitivity;         // multiplier for input sensitivity (0.1 - 3.0)
    float       dead_zone_px;        // ignore movements smaller than this
    float       smoothing_factor;    // 0.0 = raw, 1.0 = max smoothing
    int32_t     latency_offset_ms;   // compensate for known input delay
    bool        adaptive_enabled;    // auto-adjust based on user performance

    CalibrationProfile()
        : name("default")
        , sensitivity(1.0f)
        , dead_zone_px(2.0f)
        , smoothing_factor(0.3f)
        , latency_offset_ms(0)
        , adaptive_enabled(true)
    {}
};

// --- Performance Metrics ---

struct PerformanceMetrics {
    float    avg_offset_px;
    float    avg_latency_ms;
    uint32_t total_samples;
    uint32_t accurate_samples;   // samples with AccuracyLevel <= GOOD
    float    accuracy_rate;      // accurate_samples / total_samples

    PerformanceMetrics()
        : avg_offset_px(0.0f)
        , avg_latency_ms(0.0f)
        , total_samples(0)
        , accurate_samples(0)
        , accuracy_rate(0.0f)
    {}
};

// --- Coordination Engine ---
//
// Synchronizes visual (eye) input with hand/touch input.
// Measures accuracy, latency, and provides calibration
// to improve hand-eye coordination over time.

class CoordinationEngine {
public:
    CoordinationEngine()
        : profile_()
        , metrics_()
        , history_()
        , active_(false)
    {}

    explicit CoordinationEngine(const CalibrationProfile& profile)
        : profile_(profile)
        , metrics_()
        , history_()
        , active_(false)
    {}

    // --- Activation ---

    void start() { active_ = true; }
    void stop() { active_ = false; }
    bool is_active() const { return active_; }

    // --- Calibration ---

    void set_profile(const CalibrationProfile& profile) {
        profile_ = profile;
    }

    const CalibrationProfile& get_profile() const {
        return profile_;
    }

    // --- Core: Evaluate coordination ---

    CoordinationResult evaluate(
        const EyeTrackingData& eye,
        const HandInputData& hand
    ) {
        CoordinationResult result;

        if (!active_) {
            result.feedback = "Engine not active";
            return result;
        }

        // Calculate offset between gaze and touch
        float raw_offset = eye.gaze_point.distance_to(hand.touch_point);

        // Apply dead zone
        if (raw_offset < profile_.dead_zone_px) {
            raw_offset = 0.0f;
        }

        // Apply sensitivity
        result.offset_px = raw_offset * profile_.sensitivity;

        // Calculate latency
        if (hand.timestamp_ms >= eye.timestamp_ms) {
            result.latency_ms = hand.timestamp_ms - eye.timestamp_ms;
        } else {
            result.latency_ms = 0;
        }

        // Apply latency compensation
        if (profile_.latency_offset_ms > 0) {
            if (result.latency_ms >= static_cast<uint32_t>(profile_.latency_offset_ms)) {
                result.latency_ms -= static_cast<uint32_t>(profile_.latency_offset_ms);
            } else {
                result.latency_ms = 0;
            }
        }

        // Determine accuracy level
        result.accuracy = classify_accuracy(result.offset_px);

        // Check synchronization (good accuracy + low latency + confident gaze)
        result.synchronized = (result.accuracy <= AccuracyLevel::GOOD)
                           && (result.latency_ms < 200)
                           && (eye.confidence > 0.7f);

        // Generate feedback
        result.feedback = generate_feedback(result);

        // Record for metrics
        record_sample(result);

        // Adaptive adjustment
        if (profile_.adaptive_enabled) {
            adapt(result);
        }

        return result;
    }

    // --- Performance ---

    const PerformanceMetrics& get_metrics() const {
        return metrics_;
    }

    void reset_metrics() {
        metrics_ = PerformanceMetrics();
        history_.clear();
    }

    // --- Smoothing ---

    Point2D smooth_input(const Point2D& current, const Point2D& previous) const {
        float sf = profile_.smoothing_factor;
        return Point2D(
            previous.x + (current.x - previous.x) * (1.0f - sf),
            previous.y + (current.y - previous.y) * (1.0f - sf)
        );
    }

private:
    AccuracyLevel classify_accuracy(float offset_px) const {
        if (offset_px < 5.0f)  return AccuracyLevel::EXCELLENT;
        if (offset_px < 15.0f) return AccuracyLevel::GOOD;
        if (offset_px < 30.0f) return AccuracyLevel::FAIR;
        if (offset_px < 50.0f) return AccuracyLevel::POOR;
        return AccuracyLevel::MISALIGNED;
    }

    std::string generate_feedback(const CoordinationResult& result) const {
        if (result.synchronized) {
            return "Excellent coordination — eye and hand synchronized";
        }
        if (result.accuracy <= AccuracyLevel::GOOD) {
            return "Good accuracy — minor latency detected";
        }
        if (result.accuracy == AccuracyLevel::FAIR) {
            return "Fair — try to focus on the target before touching";
        }
        if (result.accuracy == AccuracyLevel::POOR) {
            return "Poor — slow down and aim carefully";
        }
        return "Misaligned — recalibration recommended";
    }

    void record_sample(const CoordinationResult& result) {
        history_.push_back(result);
        metrics_.total_samples++;

        if (result.accuracy <= AccuracyLevel::GOOD) {
            metrics_.accurate_samples++;
        }

        // Running averages
        float n = static_cast<float>(metrics_.total_samples);
        metrics_.avg_offset_px  = metrics_.avg_offset_px  + (result.offset_px - metrics_.avg_offset_px) / n;
        metrics_.avg_latency_ms = metrics_.avg_latency_ms + (static_cast<float>(result.latency_ms) - metrics_.avg_latency_ms) / n;
        metrics_.accuracy_rate  = static_cast<float>(metrics_.accurate_samples) / n;
    }

    void adapt(const CoordinationResult& result) {
        // If consistently poor, reduce sensitivity to give more control
        if (metrics_.total_samples >= 10 && metrics_.accuracy_rate < 0.3f) {
            if (profile_.sensitivity > 0.3f) {
                profile_.sensitivity -= 0.05f;
            }
        }
        // If consistently excellent, can increase sensitivity for speed
        if (metrics_.total_samples >= 10 && metrics_.accuracy_rate > 0.9f) {
            if (profile_.sensitivity < 2.5f) {
                profile_.sensitivity += 0.02f;
            }
        }
    }

    CalibrationProfile                profile_;
    PerformanceMetrics                metrics_;
    std::vector<CoordinationResult>   history_;
    bool                              active_;
};

} // namespace coordination

#endif // HAND_EYE_COORDINATION_HPP
