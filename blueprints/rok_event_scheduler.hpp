#ifndef ROK_EVENT_SCHEDULER_HPP
#define ROK_EVENT_SCHEDULER_HPP

#include <string>
#include <vector>
#include <cstdint>
#include <functional>
#include <chrono>
#include <map>
#include <algorithm>

namespace rok {

// ============================================================
// Rise of Kingdoms Event Scheduler Blueprint
//
// Manages game event timing, countdown tracking, and
// preparation scheduling for competitive play.
//
// Architecture:
//
//   [Event Registry] ---> [Scheduler] ---> [Notification Queue]
//        |                     |                    |
//        v                     v                    v
//   [Templates]           [Countdown]          [Callbacks]
//
// Features:
//   - Event lifecycle management (schedule, activate, complete)
//   - Countdown timers with configurable precision
//   - Preparation window tracking for pre-event planning
//   - Recurring event template support
//   - Callback-based notification system
//   - Priority-based event ordering for resource allocation
// ============================================================

// --- Event Types ---

enum class EventCategory : uint8_t {
    PVP            = 0,   // KvK, Ark of Osiris, etc.
    PVE            = 1,   // Ceroli Crisis, Shadow Legion
    COMPETITIVE    = 2,   // MGE, Sunset Canyon, Lost Canyon
    RESOURCE       = 3,   // Gathering, Training, Research events
    SPECIAL        = 4,   // Wheel of Fortune, Card King, Chronicles
    ALLIANCE       = 5    // Alliance-specific events
};

enum class EventPriority : uint8_t {
    CRITICAL  = 0,  // Must participate (KvK)
    HIGH      = 1,  // Strong rewards (MGE, Ark)
    MEDIUM    = 2,  // Good rewards (training events)
    LOW       = 3,  // Optional participation
    INFO_ONLY = 4   // Awareness only, no action needed
};

enum class EventState : uint8_t {
    SCHEDULED    = 0,
    PREPARATION  = 1,
    ACTIVE       = 2,
    INTERMISSION = 3,
    FINAL_PHASE  = 4,
    COMPLETED    = 5,
    CANCELLED    = 6
};

// --- Time Management ---

using Timestamp = std::chrono::system_clock::time_point;
using Duration  = std::chrono::milliseconds;

struct TimeWindow {
    Timestamp start;
    Timestamp end;

    TimeWindow()
        : start(std::chrono::system_clock::now())
        , end(std::chrono::system_clock::now())
    {}

    TimeWindow(Timestamp s, Timestamp e)
        : start(s)
        , end(e)
    {}

    Duration duration() const {
        return std::chrono::duration_cast<Duration>(end - start);
    }

    bool is_active() const {
        auto now = std::chrono::system_clock::now();
        return now >= start && now < end;
    }

    bool has_started() const {
        return std::chrono::system_clock::now() >= start;
    }

    bool has_ended() const {
        return std::chrono::system_clock::now() >= end;
    }

    Duration time_until_start() const {
        auto now = std::chrono::system_clock::now();
        if (now >= start) return Duration(0);
        return std::chrono::duration_cast<Duration>(start - now);
    }

    Duration time_remaining() const {
        auto now = std::chrono::system_clock::now();
        if (now >= end) return Duration(0);
        return std::chrono::duration_cast<Duration>(end - now);
    }
};

// --- Preparation Task ---

struct PreparationTask {
    std::string task_id;
    std::string description;
    bool        is_completed;
    uint8_t     priority;    // 0 = highest

    PreparationTask()
        : task_id("")
        , description("")
        , is_completed(false)
        , priority(5)
    {}

    PreparationTask(const std::string& id, const std::string& desc, uint8_t pri)
        : task_id(id)
        , description(desc)
        , is_completed(false)
        , priority(pri)
    {}
};

// --- Reward Tier ---

struct RewardTier {
    uint8_t     tier;
    int64_t     score_threshold;
    std::string description;
    int64_t     gem_value;

    RewardTier()
        : tier(0)
        , score_threshold(0)
        , description("")
        , gem_value(0)
    {}

    RewardTier(uint8_t t, int64_t thresh, const std::string& desc, int64_t gems)
        : tier(t)
        , score_threshold(thresh)
        , description(desc)
        , gem_value(gems)
    {}
};

// --- Scheduled Event ---

struct ScheduledEvent {
    std::string                   event_id;
    std::string                   name;
    EventCategory                 category;
    EventPriority                 priority;
    EventState                    state;
    TimeWindow                    event_window;
    TimeWindow                    preparation_window;
    std::vector<PreparationTask>  prep_tasks;
    std::vector<RewardTier>       reward_tiers;
    int64_t                       current_score;
    int64_t                       target_score;
    bool                          is_recurring;
    Duration                      recurrence_interval;

    ScheduledEvent()
        : event_id("")
        , name("")
        , category(EventCategory::RESOURCE)
        , priority(EventPriority::MEDIUM)
        , state(EventState::SCHEDULED)
        , event_window()
        , preparation_window()
        , prep_tasks()
        , reward_tiers()
        , current_score(0)
        , target_score(0)
        , is_recurring(false)
        , recurrence_interval(Duration(0))
    {}

    double score_progress() const {
        if (target_score <= 0) return 0.0;
        return static_cast<double>(current_score) / target_score;
    }

    bool is_in_prep_window() const {
        return preparation_window.is_active();
    }

    bool needs_attention() const {
        return (state == EventState::PREPARATION || state == EventState::ACTIVE) &&
               !all_prep_complete();
    }

    bool all_prep_complete() const {
        return std::all_of(prep_tasks.begin(), prep_tasks.end(),
            [](const PreparationTask& t) { return t.is_completed; });
    }

    int incomplete_prep_count() const {
        return static_cast<int>(std::count_if(prep_tasks.begin(), prep_tasks.end(),
            [](const PreparationTask& t) { return !t.is_completed; }));
    }
};

// --- Notification ---

enum class NotificationType : uint8_t {
    EVENT_STARTING   = 0,
    EVENT_ACTIVE     = 1,
    PREP_REMINDER    = 2,
    SCORE_MILESTONE  = 3,
    EVENT_ENDING     = 4,
    EVENT_COMPLETED  = 5
};

struct Notification {
    std::string      notification_id;
    std::string      event_id;
    NotificationType type;
    std::string      message;
    Timestamp        created_at;
    bool             is_read;

    Notification()
        : notification_id("")
        , event_id("")
        , type(NotificationType::EVENT_STARTING)
        , message("")
        , created_at(std::chrono::system_clock::now())
        , is_read(false)
    {}

    Notification(const std::string& nid, const std::string& eid,
                 NotificationType t, const std::string& msg)
        : notification_id(nid)
        , event_id(eid)
        , type(t)
        , message(msg)
        , created_at(std::chrono::system_clock::now())
        , is_read(false)
    {}
};

// --- Scheduler ---

using EventCallback = std::function<void(const ScheduledEvent&, NotificationType)>;

class EventSchedulerEngine {
private:
    std::map<std::string, ScheduledEvent> events_;
    std::vector<Notification>             notifications_;
    std::vector<EventCallback>            callbacks_;

public:
    EventSchedulerEngine() = default;

    // --- Event Management ---

    void schedule_event(const ScheduledEvent& event) {
        events_[event.event_id] = event;
    }

    bool cancel_event(const std::string& event_id) {
        auto it = events_.find(event_id);
        if (it == events_.end()) return false;
        it->second.state = EventState::CANCELLED;
        return true;
    }

    bool remove_event(const std::string& event_id) {
        return events_.erase(event_id) > 0;
    }

    ScheduledEvent* get_event(const std::string& event_id) {
        auto it = events_.find(event_id);
        if (it == events_.end()) return nullptr;
        return &it->second;
    }

    std::vector<ScheduledEvent> get_all_events() const {
        std::vector<ScheduledEvent> result;
        result.reserve(events_.size());
        for (const auto& pair : events_) {
            result.push_back(pair.second);
        }
        return result;
    }

    // --- State Transitions ---

    bool activate_event(const std::string& event_id) {
        auto* event = get_event(event_id);
        if (!event) return false;
        event->state = EventState::ACTIVE;
        emit_notification(*event, NotificationType::EVENT_ACTIVE);
        return true;
    }

    bool complete_event(const std::string& event_id) {
        auto* event = get_event(event_id);
        if (!event) return false;
        event->state = EventState::COMPLETED;
        emit_notification(*event, NotificationType::EVENT_COMPLETED);
        return true;
    }

    bool update_score(const std::string& event_id, int64_t new_score) {
        auto* event = get_event(event_id);
        if (!event) return false;

        int64_t old_score = event->current_score;
        event->current_score = new_score;

        // Check if a new reward tier was reached
        for (const auto& tier : event->reward_tiers) {
            if (old_score < tier.score_threshold && new_score >= tier.score_threshold) {
                emit_notification(*event, NotificationType::SCORE_MILESTONE);
                break;
            }
        }
        return true;
    }

    // --- Queries ---

    std::vector<ScheduledEvent> get_active_events() const {
        std::vector<ScheduledEvent> result;
        for (const auto& pair : events_) {
            if (pair.second.state == EventState::ACTIVE) {
                result.push_back(pair.second);
            }
        }
        return result;
    }

    std::vector<ScheduledEvent> get_upcoming_events(Duration within) const {
        auto cutoff = std::chrono::system_clock::now() + within;
        std::vector<ScheduledEvent> result;
        for (const auto& pair : events_) {
            if (pair.second.state == EventState::SCHEDULED &&
                pair.second.event_window.start < cutoff) {
                result.push_back(pair.second);
            }
        }
        std::sort(result.begin(), result.end(),
            [](const ScheduledEvent& a, const ScheduledEvent& b) {
                return a.event_window.start < b.event_window.start;
            });
        return result;
    }

    std::vector<ScheduledEvent> get_events_needing_attention() const {
        std::vector<ScheduledEvent> result;
        for (const auto& pair : events_) {
            if (pair.second.needs_attention()) {
                result.push_back(pair.second);
            }
        }
        std::sort(result.begin(), result.end(),
            [](const ScheduledEvent& a, const ScheduledEvent& b) {
                return static_cast<uint8_t>(a.priority) < static_cast<uint8_t>(b.priority);
            });
        return result;
    }

    std::vector<ScheduledEvent> get_events_by_category(EventCategory cat) const {
        std::vector<ScheduledEvent> result;
        for (const auto& pair : events_) {
            if (pair.second.category == cat) {
                result.push_back(pair.second);
            }
        }
        return result;
    }

    // --- Preparation ---

    bool complete_prep_task(const std::string& event_id, const std::string& task_id) {
        auto* event = get_event(event_id);
        if (!event) return false;
        for (auto& task : event->prep_tasks) {
            if (task.task_id == task_id) {
                task.is_completed = true;
                return true;
            }
        }
        return false;
    }

    std::vector<PreparationTask> get_incomplete_prep(const std::string& event_id) const {
        auto it = events_.find(event_id);
        if (it == events_.end()) return {};
        std::vector<PreparationTask> result;
        for (const auto& task : it->second.prep_tasks) {
            if (!task.is_completed) {
                result.push_back(task);
            }
        }
        std::sort(result.begin(), result.end(),
            [](const PreparationTask& a, const PreparationTask& b) {
                return a.priority < b.priority;
            });
        return result;
    }

    // --- Notifications ---

    void register_callback(EventCallback cb) {
        callbacks_.push_back(std::move(cb));
    }

    std::vector<Notification> get_unread_notifications() const {
        std::vector<Notification> result;
        for (const auto& n : notifications_) {
            if (!n.is_read) {
                result.push_back(n);
            }
        }
        return result;
    }

    void mark_notification_read(const std::string& notification_id) {
        for (auto& n : notifications_) {
            if (n.notification_id == notification_id) {
                n.is_read = true;
                return;
            }
        }
    }

    void mark_all_read() {
        for (auto& n : notifications_) {
            n.is_read = true;
        }
    }

    // --- Tick / Update ---

    void tick() {
        for (auto& pair : events_) {
            auto& event = pair.second;

            if (event.state == EventState::SCHEDULED &&
                event.event_window.has_started()) {
                event.state = EventState::ACTIVE;
                emit_notification(event, NotificationType::EVENT_ACTIVE);
            }

            if (event.state == EventState::ACTIVE &&
                event.event_window.has_ended()) {
                event.state = EventState::COMPLETED;
                emit_notification(event, NotificationType::EVENT_COMPLETED);
            }

            if (event.state == EventState::SCHEDULED &&
                event.is_in_prep_window()) {
                event.state = EventState::PREPARATION;
                emit_notification(event, NotificationType::PREP_REMINDER);
            }
        }
    }

    // --- Statistics ---

    size_t total_events() const { return events_.size(); }

    size_t active_event_count() const {
        return std::count_if(events_.begin(), events_.end(),
            [](const auto& pair) { return pair.second.state == EventState::ACTIVE; });
    }

    size_t unread_notification_count() const {
        return std::count_if(notifications_.begin(), notifications_.end(),
            [](const Notification& n) { return !n.is_read; });
    }

private:
    void emit_notification(const ScheduledEvent& event, NotificationType type) {
        std::string msg;
        switch (type) {
            case NotificationType::EVENT_STARTING:
                msg = event.name + " is starting soon!";
                break;
            case NotificationType::EVENT_ACTIVE:
                msg = event.name + " is now active!";
                break;
            case NotificationType::PREP_REMINDER:
                msg = event.name + ": " +
                      std::to_string(event.incomplete_prep_count()) +
                      " preparation tasks remaining";
                break;
            case NotificationType::SCORE_MILESTONE:
                msg = event.name + ": Score milestone reached (" +
                      std::to_string(event.current_score) + ")";
                break;
            case NotificationType::EVENT_ENDING:
                msg = event.name + " is ending soon!";
                break;
            case NotificationType::EVENT_COMPLETED:
                msg = event.name + " has completed!";
                break;
        }

        Notification n(
            "notif-" + std::to_string(notifications_.size()),
            event.event_id,
            type,
            msg
        );
        notifications_.push_back(n);

        for (const auto& cb : callbacks_) {
            cb(event, type);
        }
    }
};

} // namespace rok

#endif // ROK_EVENT_SCHEDULER_HPP
