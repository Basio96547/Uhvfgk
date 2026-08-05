package com.basel.ai.core

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager

/**
 * Reads the battery.
 *
 * The app has been thermal-aware since early on and blind to power the whole
 * time, which produced exactly the behaviour a phone should not have: a
 * thinking turn burning four cores for two minutes at 8% battery, and a
 * scanned PDF uploading forty page images while the user is trying to make the
 * charge last until evening. Heat and charge are different constraints and
 * only one of them was being watched.
 *
 * Read on demand rather than by broadcast: it is consulted once per turn, and
 * a registered receiver for something asked this rarely is a leak waiting to
 * be forgotten.
 */
object PowerState {

    fun read(context: Context): PowerSnapshot {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val percent = runCatching {
            manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        }.getOrDefault(-1)

        val charging = runCatching { manager?.isCharging == true }.getOrDefault(false)
        val saving = runCatching { power?.isPowerSaveMode == true }.getOrDefault(false)

        return PowerSnapshot(
            // A percentage outside 0..100 means the platform declined to
            // answer; treating that as "flat" would throttle a healthy phone.
            percent = if (percent in 0..100) percent else -1,
            isCharging = charging,
            isPowerSaveMode = saving,
        )
    }
}
