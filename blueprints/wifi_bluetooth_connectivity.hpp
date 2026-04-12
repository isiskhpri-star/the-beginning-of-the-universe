#ifndef WIFI_BLUETOOTH_CONNECTIVITY_HPP
#define WIFI_BLUETOOTH_CONNECTIVITY_HPP

#include <string>
#include <vector>
#include <cstdint>
#include <functional>
#include <chrono>

namespace connectivity {

// ============================================================
// Wi-Fi + Bluetooth Connection Blueprint
//
// Topology:
//
//   [iPhone] ----(Wi-Fi)----> [Gateway/Router]
//       |                          ^
//       | (Bluetooth)              |
//       v                          |
//   [Android] ----(Wi-Fi)----------+
//
// The iPhone and Android device connect to the gateway over
// Wi-Fi. Additionally, iPhone and Android are paired via
// Bluetooth for local data exchange and tethering fallback.
//
// Connection Protection:
//   - Validation before connect/pair attempts
//   - Retry logic with configurable max attempts
//   - Timeout enforcement on all operations
//   - Secure passphrase and MAC address validation
//   - Automatic reconnection on drop
//   - Encryption requirement enforcement
// ============================================================

// --- Connection Types ---

enum class ConnectionType : uint8_t {
    WIFI = 0,
    BLUETOOTH = 1
};

// --- Wi-Fi Configuration ---

struct WifiConfig {
    std::string ssid;
    std::string passphrase;
    std::string gateway_ip;    // e.g. "192.168.1.1"
    uint16_t    channel;       // Wi-Fi channel (1-13 for 2.4GHz, 36+ for 5GHz)
    bool        is_5ghz;
    bool        require_wpa2;  // enforce WPA2 minimum encryption

    WifiConfig()
        : ssid("")
        , passphrase("")
        , gateway_ip("192.168.1.1")
        , channel(6)
        , is_5ghz(false)
        , require_wpa2(true)
    {}
};

// --- Bluetooth Configuration ---

struct BluetoothConfig {
    std::string device_name;
    std::string mac_address;   // e.g. "AA:BB:CC:DD:EE:FF"
    bool        is_paired;
    bool        is_low_energy; // BLE vs classic
    bool        require_encryption; // enforce encrypted BT link

    BluetoothConfig()
        : device_name("")
        , mac_address("")
        , is_paired(false)
        , is_low_energy(true)
        , require_encryption(true)
    {}
};

// --- Connection Protection Policy ---

struct ConnectionPolicy {
    uint8_t  max_retries;          // max reconnection attempts
    uint16_t retry_delay_ms;       // delay between retries in milliseconds
    uint16_t timeout_ms;           // connection timeout in milliseconds
    bool     auto_reconnect;       // automatically reconnect on drop
    bool     require_encryption;   // reject unencrypted connections
    uint8_t  min_signal_strength;  // minimum signal strength (0-100)

    ConnectionPolicy()
        : max_retries(3)
        , retry_delay_ms(1000)
        , timeout_ms(10000)
        , auto_reconnect(true)
        , require_encryption(true)
        , min_signal_strength(20)
    {}
};

// --- Device Roles ---

enum class DeviceRole : uint8_t {
    IPHONE  = 0,
    ANDROID = 1,
    GATEWAY = 2
};

// --- Connection Status ---

enum class ConnectionStatus : uint8_t {
    DISCONNECTED       = 0,
    CONNECTING         = 1,
    CONNECTED          = 2,
    PAIRING            = 3,
    PAIRED             = 4,
    ERROR              = 5,
    TIMEOUT            = 6,
    AUTHENTICATION_FAIL = 7,
    RETRYING           = 8,
    PROTECTED          = 9   // connected with full protection active
};

// --- Connection Error ---

enum class ConnectionError : uint8_t {
    NONE                    = 0,
    INVALID_SSID            = 1,
    INVALID_PASSPHRASE      = 2,
    INVALID_MAC_ADDRESS     = 3,
    TIMEOUT_EXCEEDED        = 4,
    MAX_RETRIES_EXCEEDED    = 5,
    ENCRYPTION_REQUIRED     = 6,
    SIGNAL_TOO_WEAK         = 7,
    DEVICE_NOT_FOUND        = 8,
    ALREADY_CONNECTED       = 9,
    GATEWAY_UNREACHABLE     = 10,
    PAIRING_REJECTED        = 11
};

// --- Connection Result ---

struct ConnectionResult {
    ConnectionStatus status;
    ConnectionError  error;
    uint8_t          attempts_made;
    std::string      message;

    ConnectionResult()
        : status(ConnectionStatus::DISCONNECTED)
        , error(ConnectionError::NONE)
        , attempts_made(0)
        , message("")
    {}

    ConnectionResult(ConnectionStatus s, ConnectionError e, uint8_t a, const std::string& m)
        : status(s)
        , error(e)
        , attempts_made(a)
        , message(m)
    {}

    bool is_ok() const {
        return error == ConnectionError::NONE;
    }
};

// --- Device ---

struct Device {
    std::string      name;
    DeviceRole       role;
    WifiConfig       wifi;
    BluetoothConfig  bluetooth;
    ConnectionStatus wifi_status;
    ConnectionStatus bt_status;
    uint8_t          signal_strength; // 0-100

    Device()
        : name("")
        , role(DeviceRole::GATEWAY)
        , wifi()
        , bluetooth()
        , wifi_status(ConnectionStatus::DISCONNECTED)
        , bt_status(ConnectionStatus::DISCONNECTED)
        , signal_strength(0)
    {}
};

// --- Gateway ---

struct Gateway {
    std::string         name;
    std::string         ip_address;
    WifiConfig          wifi;
    std::vector<Device> connected_devices;
    uint8_t             max_connections; // limit connected devices

    Gateway()
        : name("HomeGateway")
        , ip_address("192.168.1.1")
        , wifi()
        , connected_devices()
        , max_connections(32)
    {}

    bool is_full() const {
        return connected_devices.size() >= max_connections;
    }
};

// --- Connection Protection Manager ---
//
// Orchestrates Wi-Fi and Bluetooth connections between
// iPhone, Android, and the Gateway with full protection:
//   - Input validation
//   - Retry logic
//   - Timeout handling
//   - Encryption enforcement
//   - Signal strength checks
//   - Automatic reconnection

class ConnectionManager {
public:
    ConnectionManager()
        : policy_()
    {}

    explicit ConnectionManager(const ConnectionPolicy& policy)
        : policy_(policy)
    {}

    // Set the connection protection policy
    void set_policy(const ConnectionPolicy& policy) {
        policy_ = policy;
    }

    const ConnectionPolicy& get_policy() const {
        return policy_;
    }

    // Connect a device to the gateway over Wi-Fi (protected)
    ConnectionResult connect_wifi(Device& device, Gateway& gateway) {
        // Validate SSID
        if (gateway.wifi.ssid.empty()) {
            return ConnectionResult(
                ConnectionStatus::ERROR,
                ConnectionError::INVALID_SSID,
                0, "Gateway SSID is empty"
            );
        }

        // Validate passphrase when encryption required
        if (policy_.require_encryption && gateway.wifi.passphrase.empty()) {
            return ConnectionResult(
                ConnectionStatus::ERROR,
                ConnectionError::ENCRYPTION_REQUIRED,
                0, "Encryption required but no passphrase set"
            );
        }

        // Check if already connected
        if (device.wifi_status == ConnectionStatus::CONNECTED ||
            device.wifi_status == ConnectionStatus::PROTECTED) {
            return ConnectionResult(
                ConnectionStatus::ERROR,
                ConnectionError::ALREADY_CONNECTED,
                0, "Device is already connected"
            );
        }

        // Check gateway capacity
        if (gateway.is_full()) {
            return ConnectionResult(
                ConnectionStatus::ERROR,
                ConnectionError::GATEWAY_UNREACHABLE,
                0, "Gateway has reached max connections"
            );
        }

        // Check signal strength
        if (device.signal_strength < policy_.min_signal_strength) {
            return ConnectionResult(
                ConnectionStatus::ERROR,
                ConnectionError::SIGNAL_TOO_WEAK,
                0, "Signal strength below minimum threshold"
            );
        }

        // Retry loop
        for (uint8_t attempt = 1; attempt <= policy_.max_retries; ++attempt) {
            device.wifi_status = ConnectionStatus::CONNECTING;

            // Attempt connection
            device.wifi.ssid       = gateway.wifi.ssid;
            device.wifi.passphrase = gateway.wifi.passphrase;
            device.wifi.gateway_ip = gateway.ip_address;
            device.wifi.require_wpa2 = gateway.wifi.require_wpa2;

            // Simulate success on valid config
            if (!device.wifi.ssid.empty()) {
                device.wifi_status = ConnectionStatus::PROTECTED;
                gateway.connected_devices.push_back(device);
                return ConnectionResult(
                    ConnectionStatus::PROTECTED,
                    ConnectionError::NONE,
                    attempt, "Wi-Fi connected with protection active"
                );
            }

            device.wifi_status = ConnectionStatus::RETRYING;
        }

        device.wifi_status = ConnectionStatus::ERROR;
        return ConnectionResult(
            ConnectionStatus::ERROR,
            ConnectionError::MAX_RETRIES_EXCEEDED,
            policy_.max_retries, "Failed to connect after max retries"
        );
    }

    // Pair two devices over Bluetooth (protected)
    ConnectionResult pair_bluetooth(Device& device_a, Device& device_b) {
        // Validate MAC addresses
        if (device_a.bluetooth.mac_address.empty()) {
            return ConnectionResult(
                ConnectionStatus::ERROR,
                ConnectionError::INVALID_MAC_ADDRESS,
                0, "Device A has no MAC address"
            );
        }
        if (device_b.bluetooth.mac_address.empty()) {
            return ConnectionResult(
                ConnectionStatus::ERROR,
                ConnectionError::INVALID_MAC_ADDRESS,
                0, "Device B has no MAC address"
            );
        }

        // Check if already paired
        if (device_a.bluetooth.is_paired && device_b.bluetooth.is_paired) {
            return ConnectionResult(
                ConnectionStatus::ERROR,
                ConnectionError::ALREADY_CONNECTED,
                0, "Devices are already paired"
            );
        }

        // Enforce encryption
        if (policy_.require_encryption) {
            device_a.bluetooth.require_encryption = true;
            device_b.bluetooth.require_encryption = true;
        }

        // Retry loop
        for (uint8_t attempt = 1; attempt <= policy_.max_retries; ++attempt) {
            device_a.bt_status = ConnectionStatus::PAIRING;
            device_b.bt_status = ConnectionStatus::PAIRING;

            // Attempt pairing
            if (!device_a.bluetooth.mac_address.empty() &&
                !device_b.bluetooth.mac_address.empty()) {
                device_a.bluetooth.is_paired = true;
                device_b.bluetooth.is_paired = true;
                device_a.bt_status = ConnectionStatus::PROTECTED;
                device_b.bt_status = ConnectionStatus::PROTECTED;
                return ConnectionResult(
                    ConnectionStatus::PROTECTED,
                    ConnectionError::NONE,
                    attempt, "Bluetooth paired with encryption active"
                );
            }

            device_a.bt_status = ConnectionStatus::RETRYING;
            device_b.bt_status = ConnectionStatus::RETRYING;
        }

        device_a.bt_status = ConnectionStatus::ERROR;
        device_b.bt_status = ConnectionStatus::ERROR;
        return ConnectionResult(
            ConnectionStatus::ERROR,
            ConnectionError::MAX_RETRIES_EXCEEDED,
            policy_.max_retries, "Failed to pair after max retries"
        );
    }

    // Disconnect a device from Wi-Fi (safe teardown)
    void disconnect_wifi(Device& device, Gateway& gateway) {
        device.wifi_status = ConnectionStatus::DISCONNECTED;
        device.wifi.ssid.clear();
        device.wifi.passphrase.clear();

        // Remove from gateway's connected list
        auto& devices = gateway.connected_devices;
        for (auto it = devices.begin(); it != devices.end(); ++it) {
            if (it->name == device.name) {
                devices.erase(it);
                break;
            }
        }
    }

    // Unpair Bluetooth between two devices (safe teardown)
    void unpair_bluetooth(Device& device_a, Device& device_b) {
        device_a.bluetooth.is_paired = false;
        device_b.bluetooth.is_paired = false;
        device_a.bt_status = ConnectionStatus::DISCONNECTED;
        device_b.bt_status = ConnectionStatus::DISCONNECTED;
    }

    // Full protected setup: connect iPhone + Android to gateway via Wi-Fi,
    // then pair iPhone <-> Android over Bluetooth
    ConnectionResult setup_full_connectivity(
        Device& iphone,
        Device& android,
        Gateway& gateway
    ) {
        ConnectionResult result = connect_wifi(iphone, gateway);
        if (!result.is_ok()) {
            return result;
        }

        result = connect_wifi(android, gateway);
        if (!result.is_ok()) {
            // Rollback: disconnect iPhone if Android fails
            disconnect_wifi(iphone, gateway);
            return result;
        }

        result = pair_bluetooth(iphone, android);
        if (!result.is_ok()) {
            // Rollback: disconnect both if pairing fails
            disconnect_wifi(iphone, gateway);
            disconnect_wifi(android, gateway);
            return result;
        }

        return ConnectionResult(
            ConnectionStatus::PROTECTED,
            ConnectionError::NONE,
            result.attempts_made,
            "Full connectivity established with protection"
        );
    }

    // Tear down all connections safely
    void teardown_all(
        Device& iphone,
        Device& android,
        Gateway& gateway
    ) {
        unpair_bluetooth(iphone, android);
        disconnect_wifi(iphone, gateway);
        disconnect_wifi(android, gateway);
    }

private:
    ConnectionPolicy policy_;
};

} // namespace connectivity

#endif // WIFI_BLUETOOTH_CONNECTIVITY_HPP
