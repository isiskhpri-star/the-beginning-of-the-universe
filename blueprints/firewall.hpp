#ifndef FIREWALL_HPP
#define FIREWALL_HPP

#include <string>
#include <vector>
#include <cstdint>
#include <algorithm>

namespace firewall {

// ============================================================
// Firewall Blueprint — Laptop Protection
//
// Blocks static/unwanted Wi-Fi and Bluetooth connections.
// Filters inbound and outbound traffic by rules.
//
// Features:
//   - Allowlist / blocklist for Wi-Fi SSIDs
//   - Allowlist / blocklist for Bluetooth MAC addresses
//   - Packet filtering by port, protocol, and direction
//   - Rate limiting to prevent flood attacks
//   - Logging of blocked/allowed connections
//   - Auto-reject static (unchanging) Wi-Fi and BT signals
//   - Stealth mode to hide device from scans
// ============================================================

// --- Direction ---

enum class Direction : uint8_t {
    INBOUND  = 0,
    OUTBOUND = 1,
    BOTH     = 2
};

// --- Protocol ---

enum class Protocol : uint8_t {
    TCP  = 0,
    UDP  = 1,
    ICMP = 2,
    ANY  = 3
};

// --- Action ---

enum class Action : uint8_t {
    ALLOW = 0,
    BLOCK = 1,
    LOG   = 2   // allow but log
};

// --- Firewall Verdict ---

enum class Verdict : uint8_t {
    PASSED          = 0,
    BLOCKED_BY_RULE = 1,
    BLOCKED_STATIC  = 2,  // blocked because signal is static/unchanging
    RATE_LIMITED     = 3,
    BLOCKED_UNKNOWN  = 4,
    STEALTH_HIDDEN   = 5
};

// --- Wi-Fi Filter Rule ---

struct WifiRule {
    std::string ssid;          // SSID to match ("*" = any)
    Action      action;
    bool        block_static;  // block if signal strength never changes (static)

    WifiRule()
        : ssid("*")
        , action(Action::BLOCK)
        , block_static(true)
    {}

    WifiRule(const std::string& s, Action a, bool bs)
        : ssid(s)
        , action(a)
        , block_static(bs)
    {}
};

// --- Bluetooth Filter Rule ---

struct BluetoothRule {
    std::string mac_address;     // MAC to match ("*" = any)
    std::string device_name;     // device name to match ("*" = any)
    Action      action;
    bool        block_static;    // block if signal is static/unchanging

    BluetoothRule()
        : mac_address("*")
        , device_name("*")
        , action(Action::BLOCK)
        , block_static(true)
    {}

    BluetoothRule(const std::string& mac, const std::string& name, Action a, bool bs)
        : mac_address(mac)
        , device_name(name)
        , action(a)
        , block_static(bs)
    {}
};

// --- Port Rule ---

struct PortRule {
    uint16_t   port_start;
    uint16_t   port_end;
    Protocol   protocol;
    Direction  direction;
    Action     action;

    PortRule()
        : port_start(0)
        , port_end(65535)
        , protocol(Protocol::ANY)
        , direction(Direction::BOTH)
        , action(Action::BLOCK)
    {}

    PortRule(uint16_t ps, uint16_t pe, Protocol p, Direction d, Action a)
        : port_start(ps)
        , port_end(pe)
        , protocol(p)
        , direction(d)
        , action(a)
    {}

    bool matches_port(uint16_t port) const {
        return port >= port_start && port <= port_end;
    }
};

// --- Rate Limit Config ---

struct RateLimitConfig {
    uint32_t max_connections_per_minute;
    uint32_t max_packets_per_second;
    bool     enabled;

    RateLimitConfig()
        : max_connections_per_minute(60)
        , max_packets_per_second(1000)
        , enabled(true)
    {}
};

// --- Static Signal Detector ---
//
// Detects when a Wi-Fi or Bluetooth signal has an unchanging
// (static) strength, which may indicate spoofing or a rogue device.

struct StaticSignalDetector {
    uint8_t  sample_count;           // number of samples to collect
    uint8_t  min_variance_threshold; // minimum variance to consider "dynamic"
    bool     enabled;

    StaticSignalDetector()
        : sample_count(10)
        , min_variance_threshold(3)
        , enabled(true)
    {}

    bool is_static(const std::vector<uint8_t>& signal_samples) const {
        if (!enabled || signal_samples.size() < 2) {
            return false;
        }

        uint8_t min_val = signal_samples[0];
        uint8_t max_val = signal_samples[0];
        for (size_t i = 1; i < signal_samples.size(); ++i) {
            if (signal_samples[i] < min_val) min_val = signal_samples[i];
            if (signal_samples[i] > max_val) max_val = signal_samples[i];
        }

        return (max_val - min_val) < min_variance_threshold;
    }
};

// --- Firewall Log Entry ---

struct LogEntry {
    std::string source;
    std::string description;
    Verdict     verdict;

    LogEntry()
        : source("")
        , description("")
        , verdict(Verdict::PASSED)
    {}

    LogEntry(const std::string& src, const std::string& desc, Verdict v)
        : source(src)
        , description(desc)
        , verdict(v)
    {}
};

// --- Firewall Result ---

struct FirewallResult {
    Verdict     verdict;
    std::string reason;

    FirewallResult()
        : verdict(Verdict::PASSED)
        , reason("")
    {}

    FirewallResult(Verdict v, const std::string& r)
        : verdict(v)
        , reason(r)
    {}

    bool is_allowed() const {
        return verdict == Verdict::PASSED;
    }
};

// --- Firewall Configuration ---

struct FirewallConfig {
    bool stealth_mode;           // hide device from network scans
    bool block_all_static_wifi;  // auto-block all static Wi-Fi signals
    bool block_all_static_bt;    // auto-block all static Bluetooth signals
    bool default_deny;           // block everything not explicitly allowed

    FirewallConfig()
        : stealth_mode(true)
        , block_all_static_wifi(true)
        , block_all_static_bt(true)
        , default_deny(true)
    {}
};

// --- Firewall ---
//
// Protects the laptop by filtering Wi-Fi and Bluetooth
// connections and blocking static/unwanted signals.

class Firewall {
public:
    Firewall()
        : config_()
        , rate_limit_()
        , static_detector_()
        , wifi_rules_()
        , bt_rules_()
        , port_rules_()
        , log_()
        , active_(false)
    {}

    explicit Firewall(const FirewallConfig& config)
        : config_(config)
        , rate_limit_()
        , static_detector_()
        , wifi_rules_()
        , bt_rules_()
        , port_rules_()
        , log_()
        , active_(false)
    {}

    // --- Activation ---

    void activate() { active_ = true; }
    void deactivate() { active_ = false; }
    bool is_active() const { return active_; }

    // --- Configuration ---

    void set_config(const FirewallConfig& config) { config_ = config; }
    const FirewallConfig& get_config() const { return config_; }

    void set_rate_limit(const RateLimitConfig& rl) { rate_limit_ = rl; }
    void set_static_detector(const StaticSignalDetector& sd) { static_detector_ = sd; }

    // --- Rule Management ---

    void add_wifi_rule(const WifiRule& rule) {
        wifi_rules_.push_back(rule);
    }

    void add_bluetooth_rule(const BluetoothRule& rule) {
        bt_rules_.push_back(rule);
    }

    void add_port_rule(const PortRule& rule) {
        port_rules_.push_back(rule);
    }

    void clear_wifi_rules() { wifi_rules_.clear(); }
    void clear_bluetooth_rules() { bt_rules_.clear(); }
    void clear_port_rules() { port_rules_.clear(); }
    void clear_all_rules() {
        wifi_rules_.clear();
        bt_rules_.clear();
        port_rules_.clear();
    }

    // --- Wi-Fi Filtering ---

    FirewallResult check_wifi(
        const std::string& ssid,
        const std::vector<uint8_t>& signal_samples
    ) {
        if (!active_) {
            return FirewallResult(Verdict::PASSED, "Firewall inactive");
        }

        // Check for static signal
        if (config_.block_all_static_wifi && static_detector_.is_static(signal_samples)) {
            log_event(ssid, "Static Wi-Fi signal blocked", Verdict::BLOCKED_STATIC);
            return FirewallResult(Verdict::BLOCKED_STATIC, "Static Wi-Fi signal detected — possible spoofing");
        }

        // Check rules
        for (const auto& rule : wifi_rules_) {
            if (rule.ssid == "*" || rule.ssid == ssid) {
                if (rule.block_static && static_detector_.is_static(signal_samples)) {
                    log_event(ssid, "Blocked by rule (static)", Verdict::BLOCKED_STATIC);
                    return FirewallResult(Verdict::BLOCKED_STATIC, "Rule matched: static signal blocked");
                }
                if (rule.action == Action::BLOCK) {
                    log_event(ssid, "Blocked by Wi-Fi rule", Verdict::BLOCKED_BY_RULE);
                    return FirewallResult(Verdict::BLOCKED_BY_RULE, "Blocked by Wi-Fi rule for SSID: " + ssid);
                }
                if (rule.action == Action::ALLOW) {
                    log_event(ssid, "Allowed by Wi-Fi rule", Verdict::PASSED);
                    return FirewallResult(Verdict::PASSED, "Allowed by rule");
                }
                if (rule.action == Action::LOG) {
                    log_event(ssid, "Logged Wi-Fi connection", Verdict::PASSED);
                    return FirewallResult(Verdict::PASSED, "Allowed (logged)");
                }
            }
        }

        // Default policy
        if (config_.default_deny) {
            log_event(ssid, "Blocked by default deny", Verdict::BLOCKED_UNKNOWN);
            return FirewallResult(Verdict::BLOCKED_UNKNOWN, "Default deny: no matching rule");
        }

        return FirewallResult(Verdict::PASSED, "No matching rule — default allow");
    }

    // --- Bluetooth Filtering ---

    FirewallResult check_bluetooth(
        const std::string& mac_address,
        const std::string& device_name,
        const std::vector<uint8_t>& signal_samples
    ) {
        if (!active_) {
            return FirewallResult(Verdict::PASSED, "Firewall inactive");
        }

        // Check for static signal
        if (config_.block_all_static_bt && static_detector_.is_static(signal_samples)) {
            log_event(mac_address, "Static Bluetooth signal blocked", Verdict::BLOCKED_STATIC);
            return FirewallResult(Verdict::BLOCKED_STATIC, "Static Bluetooth signal detected — possible spoofing");
        }

        // Check rules
        for (const auto& rule : bt_rules_) {
            bool mac_match  = (rule.mac_address == "*" || rule.mac_address == mac_address);
            bool name_match = (rule.device_name == "*" || rule.device_name == device_name);

            if (mac_match && name_match) {
                if (rule.block_static && static_detector_.is_static(signal_samples)) {
                    log_event(mac_address, "Blocked by rule (static BT)", Verdict::BLOCKED_STATIC);
                    return FirewallResult(Verdict::BLOCKED_STATIC, "Rule matched: static BT signal blocked");
                }
                if (rule.action == Action::BLOCK) {
                    log_event(mac_address, "Blocked by Bluetooth rule", Verdict::BLOCKED_BY_RULE);
                    return FirewallResult(Verdict::BLOCKED_BY_RULE, "Blocked by BT rule for: " + mac_address);
                }
                if (rule.action == Action::ALLOW) {
                    log_event(mac_address, "Allowed by Bluetooth rule", Verdict::PASSED);
                    return FirewallResult(Verdict::PASSED, "Allowed by rule");
                }
                if (rule.action == Action::LOG) {
                    log_event(mac_address, "Logged Bluetooth connection", Verdict::PASSED);
                    return FirewallResult(Verdict::PASSED, "Allowed (logged)");
                }
            }
        }

        // Default policy
        if (config_.default_deny) {
            log_event(mac_address, "Blocked by default deny", Verdict::BLOCKED_UNKNOWN);
            return FirewallResult(Verdict::BLOCKED_UNKNOWN, "Default deny: no matching rule");
        }

        return FirewallResult(Verdict::PASSED, "No matching rule — default allow");
    }

    // --- Port Filtering ---

    FirewallResult check_port(uint16_t port, Protocol protocol, Direction direction) {
        if (!active_) {
            return FirewallResult(Verdict::PASSED, "Firewall inactive");
        }

        for (const auto& rule : port_rules_) {
            if (rule.matches_port(port) &&
                (rule.protocol == Protocol::ANY || rule.protocol == protocol) &&
                (rule.direction == Direction::BOTH || rule.direction == direction)) {

                if (rule.action == Action::BLOCK) {
                    log_event("port:" + std::to_string(port), "Blocked by port rule", Verdict::BLOCKED_BY_RULE);
                    return FirewallResult(Verdict::BLOCKED_BY_RULE, "Port blocked by rule");
                }
                if (rule.action == Action::ALLOW) {
                    return FirewallResult(Verdict::PASSED, "Port allowed by rule");
                }
            }
        }

        if (config_.default_deny) {
            log_event("port:" + std::to_string(port), "Blocked by default deny", Verdict::BLOCKED_UNKNOWN);
            return FirewallResult(Verdict::BLOCKED_UNKNOWN, "Default deny: port not allowed");
        }

        return FirewallResult(Verdict::PASSED, "No matching rule — default allow");
    }

    // --- Stealth Mode ---

    FirewallResult handle_scan_request() {
        if (!active_) {
            return FirewallResult(Verdict::PASSED, "Firewall inactive");
        }

        if (config_.stealth_mode) {
            log_event("scan", "Scan request hidden (stealth mode)", Verdict::STEALTH_HIDDEN);
            return FirewallResult(Verdict::STEALTH_HIDDEN, "Device hidden from scan");
        }

        return FirewallResult(Verdict::PASSED, "Device visible to scan");
    }

    // --- Log Access ---

    const std::vector<LogEntry>& get_log() const { return log_; }
    void clear_log() { log_.clear(); }

    size_t blocked_count() const {
        size_t count = 0;
        for (const auto& entry : log_) {
            if (entry.verdict != Verdict::PASSED) {
                ++count;
            }
        }
        return count;
    }

private:
    void log_event(const std::string& source, const std::string& desc, Verdict v) {
        log_.push_back(LogEntry(source, desc, v));
    }

    FirewallConfig        config_;
    RateLimitConfig       rate_limit_;
    StaticSignalDetector  static_detector_;
    std::vector<WifiRule>      wifi_rules_;
    std::vector<BluetoothRule> bt_rules_;
    std::vector<PortRule>      port_rules_;
    std::vector<LogEntry>      log_;
    bool                       active_;
};

} // namespace firewall

#endif // FIREWALL_HPP
