#ifndef WIFI_BLUETOOTH_CONNECTIVITY_HPP
#define WIFI_BLUETOOTH_CONNECTIVITY_HPP

#include <string>
#include <vector>
#include <cstdint>

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

    WifiConfig()
        : ssid("")
        , passphrase("")
        , gateway_ip("192.168.1.1")
        , channel(6)
        , is_5ghz(false)
    {}
};

// --- Bluetooth Configuration ---

struct BluetoothConfig {
    std::string device_name;
    std::string mac_address;   // e.g. "AA:BB:CC:DD:EE:FF"
    bool        is_paired;
    bool        is_low_energy; // BLE vs classic

    BluetoothConfig()
        : device_name("")
        , mac_address("")
        , is_paired(false)
        , is_low_energy(true)
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
    DISCONNECTED   = 0,
    CONNECTING     = 1,
    CONNECTED      = 2,
    PAIRING        = 3,
    PAIRED         = 4,
    ERROR          = 5
};

// --- Device ---

struct Device {
    std::string      name;
    DeviceRole       role;
    WifiConfig       wifi;
    BluetoothConfig  bluetooth;
    ConnectionStatus wifi_status;
    ConnectionStatus bt_status;

    Device()
        : name("")
        , role(DeviceRole::GATEWAY)
        , wifi()
        , bluetooth()
        , wifi_status(ConnectionStatus::DISCONNECTED)
        , bt_status(ConnectionStatus::DISCONNECTED)
    {}
};

// --- Gateway ---

struct Gateway {
    std::string         name;
    std::string         ip_address;
    WifiConfig          wifi;
    std::vector<Device> connected_devices;

    Gateway()
        : name("HomeGateway")
        , ip_address("192.168.1.1")
        , wifi()
        , connected_devices()
    {}
};

// --- Connection Manager ---
//
// Orchestrates Wi-Fi and Bluetooth connections between
// iPhone, Android, and the Gateway.

class ConnectionManager {
public:
    ConnectionManager() {}

    // Connect a device to the gateway over Wi-Fi
    ConnectionStatus connect_wifi(Device& device, Gateway& gateway) {
        device.wifi.ssid       = gateway.wifi.ssid;
        device.wifi.gateway_ip = gateway.ip_address;
        device.wifi_status     = ConnectionStatus::CONNECTED;
        gateway.connected_devices.push_back(device);
        return ConnectionStatus::CONNECTED;
    }

    // Pair two devices over Bluetooth
    ConnectionStatus pair_bluetooth(Device& device_a, Device& device_b) {
        device_a.bluetooth.is_paired = true;
        device_b.bluetooth.is_paired = true;
        device_a.bt_status = ConnectionStatus::PAIRED;
        device_b.bt_status = ConnectionStatus::PAIRED;
        return ConnectionStatus::PAIRED;
    }

    // Disconnect a device from Wi-Fi
    void disconnect_wifi(Device& device) {
        device.wifi_status = ConnectionStatus::DISCONNECTED;
    }

    // Unpair Bluetooth between two devices
    void unpair_bluetooth(Device& device_a, Device& device_b) {
        device_a.bluetooth.is_paired = false;
        device_b.bluetooth.is_paired = false;
        device_a.bt_status = ConnectionStatus::DISCONNECTED;
        device_b.bt_status = ConnectionStatus::DISCONNECTED;
    }

    // Full setup: connect iPhone + Android to gateway via Wi-Fi,
    // then pair iPhone <-> Android over Bluetooth
    void setup_full_connectivity(
        Device& iphone,
        Device& android,
        Gateway& gateway
    ) {
        connect_wifi(iphone, gateway);
        connect_wifi(android, gateway);
        pair_bluetooth(iphone, android);
    }
};

} // namespace connectivity

#endif // WIFI_BLUETOOTH_CONNECTIVITY_HPP
