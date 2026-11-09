package com.example.model

enum class DevicePlatform(val displayName: String, val iconName: String) {
    ANDROID("Android Mobile", "android"),
    WINDOWS("Windows Desktop (.exe)", "computer"),
    WEB("Web Dashboard (PWA)", "language")
}

enum class DeviceSyncStatus(val label: String, val colorHex: Long) {
    ONLINE("Active & Synced", 0xFF608053), // Olive Green
    SYNCING("Syncing...", 0xFFD4822B),     // Warm Caramel
    OFFLINE("Cached Offline", 0xFF8A9EA7)
}

data class ConnectedDevice(
    val id: String,
    val deviceName: String,
    val platform: DevicePlatform,
    val isCurrentDevice: Boolean = false,
    val isPrimaryAlarmTarget: Boolean = false,
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "2.4.0-Production",
    val status: DeviceSyncStatus = DeviceSyncStatus.ONLINE,
    val pushTokenRegistered: Boolean = true
)
