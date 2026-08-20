#ifndef MEDIA_PROTECTION_HPP
#define MEDIA_PROTECTION_HPP

#include <string>
#include <vector>
#include <cstdint>
#include <algorithm>
#include <functional>

namespace media_protection {

// ============================================================
// Media Protection Blueprint — Profile Picture & Video Shield
//
// Prevents profile pictures and videos from being intercepted,
// copied, or leaked across any internet connection.
//
// Protection Vectors:
//   1. Network Interception Shield
//      - Encrypts media in transit (AES-256-GCM)
//      - Detects packet sniffing on local network
//      - Forces TLS 1.3+ for all media transfers
//      - Blocks unencrypted media transmission
//
//   2. Online Copy Prevention
//      - Invisible watermarking with owner fingerprint
//      - Access control lists per media asset
//      - Download request interception and blocking
//      - Right-click / save-as protection layer
//      - Expiring view tokens for shared media
//
//   3. Screen Leak Prevention
//      - Detects active screen capture software
//      - Blocks Miracast / AirPlay / Chromecast mirrors
//      - Monitors for unauthorized HDMI/DisplayPort output
//      - Overlay protection against screenshot tools
//      - Room-aware display dimming
//
// ============================================================

// --- Media Asset Types ---

enum class MediaType : uint8_t {
    PROFILE_PICTURE = 0,
    VIDEO           = 1,
    AVATAR          = 2,
    COVER_PHOTO     = 3,
    STORY           = 4,
    REEL            = 5
};

// --- Protection Level ---

enum class ProtectionLevel : uint8_t {
    STANDARD  = 0,   // basic encryption + watermark
    ENHANCED  = 1,   // + screen capture detection
    MAXIMUM   = 2,   // + room-aware protection + anti-mirror
    LOCKDOWN  = 3    // all protections active, no sharing allowed
};

// --- Encryption Standard ---

enum class EncryptionStandard : uint8_t {
    AES_128_GCM  = 0,
    AES_256_GCM  = 1,   // recommended minimum
    CHACHA20     = 2,
    AES_256_CBC  = 3
};

// --- TLS Version Requirement ---

enum class TLSMinimum : uint8_t {
    TLS_1_2 = 0,
    TLS_1_3 = 1    // recommended
};

// --- Network Threat Types ---

enum class NetworkThreat : uint8_t {
    NONE              = 0,
    PACKET_SNIFFING   = 1,   // someone capturing packets on the network
    MAN_IN_THE_MIDDLE = 2,   // intercepting and relaying traffic
    ARP_SPOOFING      = 3,   // poisoning ARP tables to redirect traffic
    DNS_HIJACKING     = 4,   // redirecting DNS to capture media requests
    SSL_STRIPPING     = 5,   // downgrading HTTPS to HTTP
    ROGUE_AP          = 6    // fake access point capturing traffic
};

// --- Screen Leak Threat Types ---

enum class ScreenLeakThreat : uint8_t {
    NONE                = 0,
    SCREEN_CAPTURE_APP  = 1,   // screenshot / recording software active
    MIRACAST_MIRROR     = 2,   // Miracast wireless display active
    AIRPLAY_MIRROR      = 3,   // AirPlay screen mirroring active
    CHROMECAST_MIRROR   = 4,   // Chromecast screen casting active
    HDMI_OUTPUT         = 5,   // unauthorized external display connected
    VIRTUAL_DISPLAY     = 6,   // virtual display driver (capture card)
    REMOTE_DESKTOP      = 7    // RDP / VNC / TeamViewer session active
};

// --- Copy Attempt Types ---

enum class CopyAttempt : uint8_t {
    NONE             = 0,
    RIGHT_CLICK_SAVE = 1,   // right-click > save image as
    DRAG_DROP        = 2,   // drag image to desktop/folder
    DEVELOPER_TOOLS  = 3,   // inspecting element to grab URL
    NETWORK_SNIFF    = 4,   // capturing the raw media stream
    CACHE_EXTRACT    = 5,   // pulling from browser/app cache
    API_BYPASS       = 6    // calling media endpoint directly
};

// --- Protection Verdict ---

enum class ProtectionVerdict : uint8_t {
    ALLOWED         = 0,   // media can be displayed safely
    BLOCKED_NETWORK = 1,   // blocked due to network threat
    BLOCKED_SCREEN  = 2,   // blocked due to screen leak risk
    BLOCKED_COPY    = 3,   // blocked copy attempt
    DEGRADED        = 4,   // showing low-res / watermarked version
    HIDDEN          = 5    // media completely hidden from display
};

// ============================================================
// SECTION 1: Network Interception Shield
// ============================================================

// --- Network Security Policy ---

struct NetworkMediaPolicy {
    EncryptionStandard encryption;
    TLSMinimum         tls_minimum;
    bool               force_encrypted_transport;   // reject HTTP, allow only HTTPS
    bool               detect_packet_sniffing;      // monitor for promiscuous mode NICs
    bool               detect_arp_spoofing;         // check ARP table integrity
    bool               block_on_rogue_ap;           // refuse to transmit on suspicious APs
    bool               certificate_pinning;         // pin known good certificates
    uint16_t           max_hops_allowed;            // max network hops before blocking

    NetworkMediaPolicy()
        : encryption(EncryptionStandard::AES_256_GCM)
        , tls_minimum(TLSMinimum::TLS_1_3)
        , force_encrypted_transport(true)
        , detect_packet_sniffing(true)
        , detect_arp_spoofing(true)
        , block_on_rogue_ap(true)
        , certificate_pinning(true)
        , max_hops_allowed(8)
    {}
};

// --- Network Scan Result ---

struct NetworkScanResult {
    NetworkThreat threat_detected;
    std::string   threat_source;        // IP or MAC of threat source
    uint8_t       confidence;           // 0-100 confidence in detection
    std::string   description;

    NetworkScanResult()
        : threat_detected(NetworkThreat::NONE)
        , threat_source("")
        , confidence(0)
        , description("")
    {}

    NetworkScanResult(NetworkThreat t, const std::string& src, uint8_t conf, const std::string& desc)
        : threat_detected(t)
        , threat_source(src)
        , confidence(conf)
        , description(desc)
    {}

    bool is_safe() const {
        return threat_detected == NetworkThreat::NONE;
    }
};

// --- ARP Table Monitor ---
//
// Watches the ARP table for inconsistencies that indicate
// someone is trying to redirect network traffic through their device.

struct ARPMonitor {
    uint16_t check_interval_ms;       // how often to verify ARP table
    uint8_t  max_duplicate_macs;      // max MACs pointing to same IP before alert
    bool     enabled;

    ARPMonitor()
        : check_interval_ms(5000)
        , max_duplicate_macs(1)
        , enabled(true)
    {}

    bool detect_spoofing(
        const std::vector<std::pair<std::string, std::string>>& arp_entries
    ) const {
        if (!enabled || arp_entries.size() < 2) {
            return false;
        }

        // Check for duplicate MACs mapped to different IPs
        for (size_t i = 0; i < arp_entries.size(); ++i) {
            uint8_t mac_count = 0;
            for (size_t j = i + 1; j < arp_entries.size(); ++j) {
                if (arp_entries[i].second == arp_entries[j].second &&
                    arp_entries[i].first != arp_entries[j].first) {
                    ++mac_count;
                }
            }
            if (mac_count > max_duplicate_macs) {
                return true;  // likely ARP spoofing
            }
        }
        return false;
    }
};

// --- Encrypted Media Packet ---
//
// Wraps media data for secure transit over any connection.

struct EncryptedMediaPacket {
    std::string          media_id;         // unique media asset identifier
    EncryptionStandard   cipher;
    std::vector<uint8_t> iv;               // initialization vector (12 bytes for GCM)
    std::vector<uint8_t> encrypted_data;   // ciphertext
    std::vector<uint8_t> auth_tag;         // authentication tag (16 bytes for GCM)
    std::string          sender_fingerprint;
    uint64_t             timestamp_ms;     // send timestamp for replay protection
    uint32_t             sequence_number;  // ordering for replay detection

    EncryptedMediaPacket()
        : media_id("")
        , cipher(EncryptionStandard::AES_256_GCM)
        , iv()
        , encrypted_data()
        , auth_tag()
        , sender_fingerprint("")
        , timestamp_ms(0)
        , sequence_number(0)
    {}

    bool is_valid() const {
        if (media_id.empty() || encrypted_data.empty() || timestamp_ms == 0) {
            return false;
        }

        // Validate IV and auth tag sizes based on cipher
        switch (cipher) {
            case EncryptionStandard::AES_128_GCM:
            case EncryptionStandard::AES_256_GCM:
                return iv.size() == 12 && auth_tag.size() == 16;
            case EncryptionStandard::CHACHA20:
                return iv.size() == 12 && auth_tag.size() == 16; // ChaCha20-Poly1305
            case EncryptionStandard::AES_256_CBC:
                return iv.size() == 16; // CBC uses 16-byte IV, no separate auth tag
            default:
                return false;
        }
    }
};

// ============================================================
// SECTION 2: Online Copy Prevention
// ============================================================

// --- Watermark Configuration ---

struct WatermarkConfig {
    std::string owner_id;              // unique owner identifier embedded in media
    std::string owner_fingerprint;     // cryptographic fingerprint of owner
    bool        invisible;             // true = steganographic (hidden in pixel data)
    bool        survives_screenshot;   // watermark persists even after screenshot
    bool        survives_compression;  // watermark persists after JPEG/video compression
    uint8_t     strength;              // 0-100, higher = more robust but more visible

    WatermarkConfig()
        : owner_id("")
        , owner_fingerprint("")
        , invisible(true)
        , survives_screenshot(true)
        , survives_compression(true)
        , strength(70)
    {}
};

// --- Access Control Entry ---

struct MediaAccessEntry {
    std::string viewer_id;             // who can view
    bool        can_download;          // allow download?
    bool        can_share;             // allow re-sharing?
    bool        can_screenshot;        // allow screenshots?
    uint32_t    max_views;             // 0 = unlimited
    uint64_t    expires_at_ms;         // 0 = never expires
    uint32_t    current_views;         // tracking

    MediaAccessEntry()
        : viewer_id("")
        , can_download(false)
        , can_share(false)
        , can_screenshot(false)
        , max_views(0)
        , expires_at_ms(0)
        , current_views(0)
    {}

    bool is_expired(uint64_t current_time_ms) const {
        if (expires_at_ms == 0) return false;
        return current_time_ms > expires_at_ms;
    }

    bool has_views_remaining() const {
        if (max_views == 0) return true;  // unlimited
        return current_views < max_views;
    }

    bool is_permitted(uint64_t current_time_ms) const {
        return !is_expired(current_time_ms) && has_views_remaining();
    }
};

// --- View Token ---
//
// Short-lived token granting temporary access to a media asset.
// Prevents permanent URL sharing.

struct ViewToken {
    std::string token_id;
    std::string media_id;
    std::string viewer_id;
    uint64_t    issued_at_ms;
    uint64_t    expires_at_ms;
    std::string bound_ip;              // token only valid from this IP
    std::string bound_device_id;       // token only valid on this device
    bool        single_use;            // invalidate after first use

    ViewToken()
        : token_id("")
        , media_id("")
        , viewer_id("")
        , issued_at_ms(0)
        , expires_at_ms(0)
        , bound_ip("")
        , bound_device_id("")
        , single_use(true)
    {}

    bool is_valid(uint64_t current_time_ms, const std::string& request_ip,
                  const std::string& request_device) const {
        if (current_time_ms > expires_at_ms) return false;
        if (!bound_ip.empty() && bound_ip != request_ip) return false;
        if (!bound_device_id.empty() && bound_device_id != request_device) return false;
        return true;
    }
};

// --- Copy Prevention Policy ---

struct CopyPreventionPolicy {
    bool block_right_click;            // prevent right-click save
    bool block_drag_drop;              // prevent drag to save
    bool block_developer_tools;        // detect and block dev tools inspection
    bool block_network_sniff;          // block detected network stream captures
    bool block_cache_extraction;       // clear media from cache immediately after display
    bool serve_degraded_on_suspect;    // serve low-res if suspicious activity detected
    bool require_view_token;           // require valid token for every view
    bool disable_browser_cache;        // prevent caching of media responses

    CopyPreventionPolicy()
        : block_right_click(true)
        , block_drag_drop(true)
        , block_developer_tools(true)
        , block_network_sniff(true)
        , block_cache_extraction(true)
        , serve_degraded_on_suspect(true)
        , require_view_token(true)
        , disable_browser_cache(true)
    {}
};

// ============================================================
// SECTION 3: Screen Leak Prevention
// ============================================================

// --- Screen Environment ---

struct ScreenEnvironment {
    bool     screen_capture_detected;   // screenshot/recording software running
    bool     miracast_active;           // wireless display protocol active
    bool     airplay_active;            // Apple AirPlay mirroring active
    bool     chromecast_active;         // Chromecast casting active
    bool     hdmi_external_connected;   // unauthorized external monitor
    bool     virtual_display_detected;  // virtual display driver (capture card)
    bool     remote_session_active;     // RDP/VNC/TeamViewer detected
    uint8_t  display_count;             // number of active displays
    uint8_t  authorized_display_count;  // number of authorized displays

    ScreenEnvironment()
        : screen_capture_detected(false)
        , miracast_active(false)
        , airplay_active(false)
        , chromecast_active(false)
        , hdmi_external_connected(false)
        , virtual_display_detected(false)
        , remote_session_active(false)
        , display_count(1)
        , authorized_display_count(1)
    {}

    bool is_safe() const {
        return !screen_capture_detected &&
               !miracast_active &&
               !airplay_active &&
               !chromecast_active &&
               !virtual_display_detected &&
               !remote_session_active &&
               display_count <= authorized_display_count;
    }

    ScreenLeakThreat get_primary_threat() const {
        if (screen_capture_detected) return ScreenLeakThreat::SCREEN_CAPTURE_APP;
        if (miracast_active)         return ScreenLeakThreat::MIRACAST_MIRROR;
        if (airplay_active)          return ScreenLeakThreat::AIRPLAY_MIRROR;
        if (chromecast_active)       return ScreenLeakThreat::CHROMECAST_MIRROR;
        if (virtual_display_detected) return ScreenLeakThreat::VIRTUAL_DISPLAY;
        if (remote_session_active)   return ScreenLeakThreat::REMOTE_DESKTOP;
        if (display_count > authorized_display_count) return ScreenLeakThreat::HDMI_OUTPUT;
        return ScreenLeakThreat::NONE;
    }
};

// --- Anti-Mirror Policy ---

struct AntiMirrorPolicy {
    bool block_miracast;               // block Miracast connections
    bool block_airplay;                // block AirPlay mirroring
    bool block_chromecast;             // block Chromecast casting
    bool block_hdmi_unauthorized;      // block unregistered external displays
    bool block_virtual_displays;       // block virtual display drivers
    bool block_remote_desktop;         // block when RDP/VNC active
    bool allow_registered_displays;    // allow pre-authorized external displays

    AntiMirrorPolicy()
        : block_miracast(true)
        , block_airplay(true)
        , block_chromecast(true)
        , block_hdmi_unauthorized(true)
        , block_virtual_displays(true)
        , block_remote_desktop(true)
        , allow_registered_displays(true)
    {}
};

// --- Screen Capture Detector ---
//
// Monitors running processes and system APIs for active
// screen capture/recording software.

struct ScreenCaptureDetector {
    bool     enabled;
    uint16_t scan_interval_ms;         // how often to check for capture software
    bool     check_process_list;       // scan running processes
    bool     check_gpu_hooks;          // detect GPU capture hooks (OBS, Shadowplay)
    bool     check_compositor;         // detect compositor-level recording
    bool     check_accessibility_api;  // detect accessibility-based screen readers

    // Known capture process names to detect
    std::vector<std::string> known_capture_processes;

    ScreenCaptureDetector()
        : enabled(true)
        , scan_interval_ms(2000)
        , check_process_list(true)
        , check_gpu_hooks(true)
        , check_compositor(true)
        , check_accessibility_api(true)
        , known_capture_processes()
    {
        // Default list of processes to watch for
        known_capture_processes.push_back("obs");
        known_capture_processes.push_back("obs64");
        known_capture_processes.push_back("streamlabs");
        known_capture_processes.push_back("nvidia_share");       // Shadowplay
        known_capture_processes.push_back("gamebar");            // Windows Game Bar
        known_capture_processes.push_back("screencapture");      // macOS builtin
        known_capture_processes.push_back("snagit");
        known_capture_processes.push_back("greenshot");
        known_capture_processes.push_back("sharex");
        known_capture_processes.push_back("lightshot");
        known_capture_processes.push_back("bandicam");
        known_capture_processes.push_back("camtasia");
        known_capture_processes.push_back("fraps");
    }

    bool is_capturing(const std::vector<std::string>& running_processes) const {
        if (!enabled) return false;

        for (const auto& proc : running_processes) {
            // Convert to lowercase for comparison (cast to unsigned char to avoid UB)
            std::string lower_proc = proc;
            std::transform(lower_proc.begin(), lower_proc.end(),
                          lower_proc.begin(),
                          [](unsigned char c) { return static_cast<char>(std::tolower(c)); });

            for (const auto& known : known_capture_processes) {
                if (lower_proc.find(known) != std::string::npos) {
                    return true;
                }
            }
        }
        return false;
    }
};

// --- Room-Aware Display Protection ---
//
// Uses ambient sensors to determine if others are nearby
// and adjusts media display accordingly.

struct RoomAwareProtection {
    bool     enabled;
    bool     use_proximity_sensor;     // detect nearby persons
    bool     use_ambient_light;        // detect if screen is visible in bright room
    bool     use_camera_detection;     // face count > 1 = someone looking over shoulder
    uint8_t  max_faces_allowed;        // max detected faces before triggering protection
    uint8_t  dim_level_on_trigger;     // 0-100, how much to dim when triggered
    bool     blur_media_on_trigger;    // blur sensitive media when others detected
    bool     hide_media_on_trigger;    // completely hide media when others detected

    RoomAwareProtection()
        : enabled(true)
        , use_proximity_sensor(true)
        , use_ambient_light(true)
        , use_camera_detection(true)
        , max_faces_allowed(1)
        , dim_level_on_trigger(20)
        , blur_media_on_trigger(true)
        , hide_media_on_trigger(false)
    {}
};

// ============================================================
// SECTION 4: Media Asset & Protection Result
// ============================================================

// --- Protected Media Asset ---

struct ProtectedMediaAsset {
    std::string   asset_id;
    std::string   owner_id;
    MediaType     type;
    ProtectionLevel protection_level;
    WatermarkConfig watermark;
    std::vector<MediaAccessEntry> access_list;
    bool          is_encrypted_at_rest;
    bool          is_watermarked;
    uint64_t      created_at_ms;
    uint64_t      last_accessed_ms;

    ProtectedMediaAsset()
        : asset_id("")
        , owner_id("")
        , type(MediaType::PROFILE_PICTURE)
        , protection_level(ProtectionLevel::ENHANCED)
        , watermark()
        , access_list()
        , is_encrypted_at_rest(true)
        , is_watermarked(true)
        , created_at_ms(0)
        , last_accessed_ms(0)
    {}
};

// --- Protection Result ---

struct ProtectionResult {
    ProtectionVerdict   verdict;
    std::string         reason;
    NetworkThreat       network_threat;
    ScreenLeakThreat    screen_threat;
    CopyAttempt         copy_attempt;

    ProtectionResult()
        : verdict(ProtectionVerdict::ALLOWED)
        , reason("")
        , network_threat(NetworkThreat::NONE)
        , screen_threat(ScreenLeakThreat::NONE)
        , copy_attempt(CopyAttempt::NONE)
    {}

    ProtectionResult(ProtectionVerdict v, const std::string& r)
        : verdict(v)
        , reason(r)
        , network_threat(NetworkThreat::NONE)
        , screen_threat(ScreenLeakThreat::NONE)
        , copy_attempt(CopyAttempt::NONE)
    {}

    bool is_safe() const {
        return verdict == ProtectionVerdict::ALLOWED;
    }
};

// --- Protection Log Entry ---

struct ProtectionLogEntry {
    std::string         media_id;
    std::string         event_description;
    ProtectionVerdict   verdict;
    uint64_t            timestamp_ms;

    ProtectionLogEntry()
        : media_id("")
        , event_description("")
        , verdict(ProtectionVerdict::ALLOWED)
        , timestamp_ms(0)
    {}

    ProtectionLogEntry(const std::string& id, const std::string& desc,
                       ProtectionVerdict v, uint64_t ts)
        : media_id(id)
        , event_description(desc)
        , verdict(v)
        , timestamp_ms(ts)
    {}
};

// ============================================================
// SECTION 5: Media Protection Engine
// ============================================================

class MediaProtectionEngine {
public:
    MediaProtectionEngine()
        : network_policy_()
        , copy_policy_()
        , anti_mirror_()
        , screen_detector_()
        , room_protection_()
        , arp_monitor_()
        , protection_log_()
        , active_(false)
    {}

    // --- Activation ---

    void activate() { active_ = true; }
    void deactivate() { active_ = false; }
    bool is_active() const { return active_; }

    // --- Configuration ---

    void set_network_policy(const NetworkMediaPolicy& p) { network_policy_ = p; }
    void set_copy_policy(const CopyPreventionPolicy& p) { copy_policy_ = p; }
    void set_anti_mirror(const AntiMirrorPolicy& p) { anti_mirror_ = p; }
    void set_screen_detector(const ScreenCaptureDetector& d) { screen_detector_ = d; }
    void set_room_protection(const RoomAwareProtection& r) { room_protection_ = r; }

    const NetworkMediaPolicy& get_network_policy() const { return network_policy_; }
    const CopyPreventionPolicy& get_copy_policy() const { return copy_policy_; }

    // --- Network Safety Check ---
    //
    // Scans the current network environment before allowing
    // media to be transmitted or displayed.

    ProtectionResult check_network_safety(
        const std::vector<std::pair<std::string, std::string>>& arp_table,
        bool tls_active,
        uint8_t tls_version,   // 12 = TLS 1.2, 13 = TLS 1.3
        uint16_t hop_count
    ) {
        if (!active_) {
            return ProtectionResult(ProtectionVerdict::ALLOWED, "Protection inactive");
        }

        // Check TLS requirement
        if (network_policy_.force_encrypted_transport && !tls_active) {
            log_event("", "Blocked: no TLS encryption active", ProtectionVerdict::BLOCKED_NETWORK);
            ProtectionResult result(ProtectionVerdict::BLOCKED_NETWORK,
                                    "Media blocked: connection is not encrypted");
            result.network_threat = NetworkThreat::SSL_STRIPPING;
            return result;
        }

        // Check TLS version
        uint8_t required_version = (network_policy_.tls_minimum == TLSMinimum::TLS_1_3) ? 13 : 12;
        if (tls_active && tls_version < required_version) {
            log_event("", "Blocked: TLS version too low", ProtectionVerdict::BLOCKED_NETWORK);
            ProtectionResult result(ProtectionVerdict::BLOCKED_NETWORK,
                                    "Media blocked: TLS version below minimum requirement");
            result.network_threat = NetworkThreat::SSL_STRIPPING;
            return result;
        }

        // Check ARP spoofing
        if (network_policy_.detect_arp_spoofing && arp_monitor_.detect_spoofing(arp_table)) {
            log_event("", "Blocked: ARP spoofing detected", ProtectionVerdict::BLOCKED_NETWORK);
            ProtectionResult result(ProtectionVerdict::BLOCKED_NETWORK,
                                    "Media blocked: ARP spoofing detected on network");
            result.network_threat = NetworkThreat::ARP_SPOOFING;
            return result;
        }

        // Check hop count (excessive hops may indicate MITM routing)
        if (hop_count > network_policy_.max_hops_allowed) {
            log_event("", "Blocked: excessive network hops", ProtectionVerdict::BLOCKED_NETWORK);
            ProtectionResult result(ProtectionVerdict::BLOCKED_NETWORK,
                                    "Media blocked: unusual routing detected (too many hops)");
            result.network_threat = NetworkThreat::MAN_IN_THE_MIDDLE;
            return result;
        }

        return ProtectionResult(ProtectionVerdict::ALLOWED, "Network environment safe");
    }

    // --- Screen Environment Check ---
    //
    // Verifies the display environment is safe before showing media.

    ProtectionResult check_screen_safety(
        const ScreenEnvironment& env,
        const std::vector<std::string>& running_processes,
        uint8_t detected_faces = 0
    ) {
        if (!active_) {
            return ProtectionResult(ProtectionVerdict::ALLOWED, "Protection inactive");
        }

        // Check for screen capture software
        if (screen_detector_.is_capturing(running_processes)) {
            log_event("", "Blocked: screen capture software detected",
                     ProtectionVerdict::BLOCKED_SCREEN);
            ProtectionResult result(ProtectionVerdict::BLOCKED_SCREEN,
                                    "Media hidden: screen capture software is running");
            result.screen_threat = ScreenLeakThreat::SCREEN_CAPTURE_APP;
            return result;
        }

        // Check mirroring protocols
        if (anti_mirror_.block_miracast && env.miracast_active) {
            log_event("", "Blocked: Miracast active", ProtectionVerdict::BLOCKED_SCREEN);
            ProtectionResult result(ProtectionVerdict::BLOCKED_SCREEN,
                                    "Media hidden: Miracast wireless display is active");
            result.screen_threat = ScreenLeakThreat::MIRACAST_MIRROR;
            return result;
        }

        if (anti_mirror_.block_airplay && env.airplay_active) {
            log_event("", "Blocked: AirPlay active", ProtectionVerdict::BLOCKED_SCREEN);
            ProtectionResult result(ProtectionVerdict::BLOCKED_SCREEN,
                                    "Media hidden: AirPlay mirroring is active");
            result.screen_threat = ScreenLeakThreat::AIRPLAY_MIRROR;
            return result;
        }

        if (anti_mirror_.block_chromecast && env.chromecast_active) {
            log_event("", "Blocked: Chromecast active", ProtectionVerdict::BLOCKED_SCREEN);
            ProtectionResult result(ProtectionVerdict::BLOCKED_SCREEN,
                                    "Media hidden: Chromecast casting is active");
            result.screen_threat = ScreenLeakThreat::CHROMECAST_MIRROR;
            return result;
        }

        if (anti_mirror_.block_virtual_displays && env.virtual_display_detected) {
            log_event("", "Blocked: virtual display detected", ProtectionVerdict::BLOCKED_SCREEN);
            ProtectionResult result(ProtectionVerdict::BLOCKED_SCREEN,
                                    "Media hidden: virtual display driver detected");
            result.screen_threat = ScreenLeakThreat::VIRTUAL_DISPLAY;
            return result;
        }

        if (anti_mirror_.block_remote_desktop && env.remote_session_active) {
            log_event("", "Blocked: remote desktop session", ProtectionVerdict::BLOCKED_SCREEN);
            ProtectionResult result(ProtectionVerdict::BLOCKED_SCREEN,
                                    "Media hidden: remote desktop session is active");
            result.screen_threat = ScreenLeakThreat::REMOTE_DESKTOP;
            return result;
        }

        if (anti_mirror_.block_hdmi_unauthorized && env.hdmi_external_connected) {
            if (env.display_count > env.authorized_display_count) {
                log_event("", "Blocked: unauthorized external display",
                         ProtectionVerdict::BLOCKED_SCREEN);
                ProtectionResult result(ProtectionVerdict::BLOCKED_SCREEN,
                                        "Media hidden: unauthorized external display connected");
                result.screen_threat = ScreenLeakThreat::HDMI_OUTPUT;
                return result;
            }
        }

        // Room-aware protection (face detection)
        if (room_protection_.enabled && room_protection_.use_camera_detection) {
            if (detected_faces > room_protection_.max_faces_allowed) {
                if (room_protection_.hide_media_on_trigger) {
                    log_event("", "Blocked: room-aware protection triggered",
                             ProtectionVerdict::HIDDEN);
                    return ProtectionResult(ProtectionVerdict::HIDDEN,
                                            "Media hidden: others detected in room");
                }
                if (room_protection_.blur_media_on_trigger) {
                    log_event("", "Degraded: room-aware protection triggered",
                             ProtectionVerdict::DEGRADED);
                    return ProtectionResult(ProtectionVerdict::DEGRADED,
                                            "Media blurred: others detected in room");
                }
            }
        }

        return ProtectionResult(ProtectionVerdict::ALLOWED, "Screen environment safe");
    }

    // --- Copy Attempt Check ---
    //
    // Evaluates whether a copy attempt should be blocked.

    ProtectionResult check_copy_attempt(
        CopyAttempt attempt,
        const ProtectedMediaAsset& asset,
        const std::string& viewer_id,
        uint64_t current_time_ms
    ) {
        if (!active_) {
            return ProtectionResult(ProtectionVerdict::ALLOWED, "Protection inactive");
        }

        if (attempt == CopyAttempt::NONE) {
            return ProtectionResult(ProtectionVerdict::ALLOWED, "No copy attempt");
        }

        // Check if viewer has download permission
        for (const auto& entry : asset.access_list) {
            if (entry.viewer_id == viewer_id && entry.is_permitted(current_time_ms)) {
                if (entry.can_download &&
                    (attempt == CopyAttempt::RIGHT_CLICK_SAVE || attempt == CopyAttempt::DRAG_DROP)) {
                    return ProtectionResult(ProtectionVerdict::ALLOWED,
                                            "Download permitted for this viewer");
                }
            }
        }

        // Block based on policy
        if (copy_policy_.block_right_click && attempt == CopyAttempt::RIGHT_CLICK_SAVE) {
            log_event(asset.asset_id, "Blocked: right-click save attempt",
                     ProtectionVerdict::BLOCKED_COPY);
            ProtectionResult result(ProtectionVerdict::BLOCKED_COPY,
                                    "Download blocked: right-click save is disabled");
            result.copy_attempt = attempt;
            return result;
        }

        if (copy_policy_.block_drag_drop && attempt == CopyAttempt::DRAG_DROP) {
            log_event(asset.asset_id, "Blocked: drag-drop save attempt",
                     ProtectionVerdict::BLOCKED_COPY);
            ProtectionResult result(ProtectionVerdict::BLOCKED_COPY,
                                    "Download blocked: drag and drop is disabled");
            result.copy_attempt = attempt;
            return result;
        }

        if (copy_policy_.block_developer_tools && attempt == CopyAttempt::DEVELOPER_TOOLS) {
            log_event(asset.asset_id, "Blocked: developer tools inspection",
                     ProtectionVerdict::BLOCKED_COPY);
            ProtectionResult result(ProtectionVerdict::BLOCKED_COPY,
                                    "Access blocked: developer tools detected");
            result.copy_attempt = attempt;
            return result;
        }

        if (copy_policy_.block_network_sniff && attempt == CopyAttempt::NETWORK_SNIFF) {
            log_event(asset.asset_id, "Blocked: network stream sniffing attempt",
                     ProtectionVerdict::BLOCKED_COPY);
            ProtectionResult result(ProtectionVerdict::BLOCKED_COPY,
                                    "Access blocked: network stream capture detected");
            result.copy_attempt = attempt;
            return result;
        }

        if (copy_policy_.block_cache_extraction && attempt == CopyAttempt::CACHE_EXTRACT) {
            log_event(asset.asset_id, "Blocked: cache extraction attempt",
                     ProtectionVerdict::BLOCKED_COPY);
            ProtectionResult result(ProtectionVerdict::BLOCKED_COPY,
                                    "Access blocked: cache extraction detected");
            result.copy_attempt = attempt;
            return result;
        }

        // Degrade quality for suspected API bypass
        if (copy_policy_.serve_degraded_on_suspect && attempt == CopyAttempt::API_BYPASS) {
            log_event(asset.asset_id, "Degraded: API bypass attempt",
                     ProtectionVerdict::DEGRADED);
            ProtectionResult result(ProtectionVerdict::DEGRADED,
                                    "Serving watermarked low-resolution version");
            result.copy_attempt = attempt;
            return result;
        }

        return ProtectionResult(ProtectionVerdict::ALLOWED, "Copy attempt not blocked by policy");
    }

    // --- Full Protection Check ---
    //
    // Comprehensive check combining all protection vectors.
    // Call this before displaying any protected media asset.

    ProtectionResult full_protection_check(
        const ProtectedMediaAsset& asset,
        const std::string& viewer_id,
        uint64_t current_time_ms,
        const std::vector<std::pair<std::string, std::string>>& arp_table,
        bool tls_active,
        uint8_t tls_version,
        uint16_t hop_count,
        const ScreenEnvironment& screen_env,
        const std::vector<std::string>& running_processes,
        CopyAttempt copy_attempt,
        uint8_t detected_faces = 0
    ) {
        if (!active_) {
            return ProtectionResult(ProtectionVerdict::ALLOWED, "Protection inactive");
        }

        // LOCKDOWN: no access at all
        if (asset.protection_level == ProtectionLevel::LOCKDOWN) {
            if (viewer_id != asset.owner_id) {
                log_event(asset.asset_id, "Blocked: asset in lockdown mode",
                         ProtectionVerdict::HIDDEN, current_time_ms);
                return ProtectionResult(ProtectionVerdict::HIDDEN,
                                        "Media in lockdown: owner-only access");
            }
        }

        // Check access list
        bool viewer_authorized = (viewer_id == asset.owner_id);
        if (!viewer_authorized) {
            for (const auto& entry : asset.access_list) {
                if (entry.viewer_id == viewer_id && entry.is_permitted(current_time_ms)) {
                    viewer_authorized = true;
                    break;
                }
            }
        }

        if (!viewer_authorized) {
            log_event(asset.asset_id, "Blocked: viewer not authorized",
                     ProtectionVerdict::HIDDEN, current_time_ms);
            return ProtectionResult(ProtectionVerdict::HIDDEN,
                                    "Access denied: viewer not in access list");
        }

        // Network safety (all levels)
        ProtectionResult network_result = check_network_safety(
            arp_table, tls_active, tls_version, hop_count);
        if (!network_result.is_safe()) {
            return network_result;
        }

        // Screen safety (ENHANCED and above)
        if (asset.protection_level >= ProtectionLevel::ENHANCED) {
            ProtectionResult screen_result = check_screen_safety(
                screen_env, running_processes, detected_faces);
            if (!screen_result.is_safe()) {
                return screen_result;
            }
        }

        // Copy prevention (all levels)
        ProtectionResult copy_result = check_copy_attempt(
            copy_attempt, asset, viewer_id, current_time_ms);
        if (!copy_result.is_safe()) {
            return copy_result;
        }

        log_event(asset.asset_id, "Access granted: all checks passed",
                 ProtectionVerdict::ALLOWED, current_time_ms);
        return ProtectionResult(ProtectionVerdict::ALLOWED,
                                "All protection checks passed — safe to display");
    }

    // --- View Token Validation ---

    ProtectionResult validate_view_token(
        ViewToken& token,
        uint64_t current_time_ms,
        const std::string& request_ip,
        const std::string& request_device
    ) {
        if (!active_) {
            return ProtectionResult(ProtectionVerdict::ALLOWED, "Protection inactive");
        }

        if (!copy_policy_.require_view_token) {
            return ProtectionResult(ProtectionVerdict::ALLOWED, "View tokens not required");
        }

        if (!token.is_valid(current_time_ms, request_ip, request_device)) {
            log_event(token.media_id, "Blocked: invalid or expired view token",
                     ProtectionVerdict::HIDDEN, current_time_ms);
            return ProtectionResult(ProtectionVerdict::HIDDEN,
                                    "Access denied: view token invalid, expired, or bound to different device");
        }

        // Enforce single-use: mark token as consumed by expiring it immediately
        if (token.single_use) {
            token.expires_at_ms = 0;  // invalidate for future use
        }

        return ProtectionResult(ProtectionVerdict::ALLOWED, "View token valid");
    }

    // --- Log Access ---

    const std::vector<ProtectionLogEntry>& get_log() const { return protection_log_; }
    void clear_log() { protection_log_.clear(); }

    size_t blocked_count() const {
        size_t count = 0;
        for (const auto& entry : protection_log_) {
            if (entry.verdict != ProtectionVerdict::ALLOWED) {
                ++count;
            }
        }
        return count;
    }

    size_t total_events() const { return protection_log_.size(); }

private:
    void log_event(const std::string& media_id, const std::string& desc,
                   ProtectionVerdict v, uint64_t timestamp_ms = 0) {
        protection_log_.push_back(ProtectionLogEntry(media_id, desc, v, timestamp_ms));
    }

    NetworkMediaPolicy      network_policy_;
    CopyPreventionPolicy    copy_policy_;
    AntiMirrorPolicy        anti_mirror_;
    ScreenCaptureDetector   screen_detector_;
    RoomAwareProtection     room_protection_;
    ARPMonitor              arp_monitor_;
    std::vector<ProtectionLogEntry> protection_log_;
    bool                    active_;
};

} // namespace media_protection

#endif // MEDIA_PROTECTION_HPP
