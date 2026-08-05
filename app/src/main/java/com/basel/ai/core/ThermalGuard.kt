package com.basel.ai.core

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watches the platform thermal signal so long generations don't cook the phone.
 *
 * Sustained LLM decode is one of the few things a phone can do that pegs every
 * core for minutes at a time. Left alone it heats the device until the kernel
 * throttles clocks — which is both slower *and* worse for the battery than
 * simply using fewer threads from the start. This class turns the platform's
 * thermal status into a thread budget, and tells the UI when to say something.
 *
 * On API < 29 there is no thermal status API, so the guard reports [ThermalLevel.NORMAL]
 * and the conservative default thread count still applies.
 */
class ThermalGuard(context: Context) {

    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val _level = MutableStateFlow(ThermalLevel.NORMAL)
    val level: StateFlow<ThermalLevel> = _level.asStateFlow()

    /** True when the platform can actually report thermal status. */
    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null

    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val pm = powerManager ?: return

        _level.value = fromPlatform(pm.currentThermalStatus)

        val callback = PowerManager.OnThermalStatusChangedListener { status ->
            _level.value = fromPlatform(status)
        }
        listener = callback
        runCatching { pm.addThermalStatusListener(callback) }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val pm = powerManager ?: return
        listener?.let { runCatching { pm.removeThermalStatusListener(it) } }
        listener = null
    }

    /**
     * Fraction of the thermal budget still available (API 30+), or null.
     * Values approaching 1.0 mean throttling is imminent.
     */
    fun headroom(): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val pm = powerManager ?: return null
        return runCatching { pm.getThermalHeadroom(HEADROOM_FORECAST_SECONDS) }
            .getOrNull()
            ?.takeIf { !it.isNaN() }
    }

    private fun fromPlatform(status: Int): ThermalLevel = when {
        status >= PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.CRITICAL
        status == PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.HOT
        status == PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.WARM
        else -> ThermalLevel.NORMAL
    }

    companion object {
        private const val HEADROOM_FORECAST_SECONDS = 10

        /** Delegated: the arithmetic is pure and lives in [ThermalPolicy]. */
        fun threadBudget(coreCount: Int, level: ThermalLevel): Int =
            ThermalPolicy.threadBudget(coreCount, level)

        fun shouldPause(level: ThermalLevel): Boolean = ThermalPolicy.shouldPause(level)
    }
}
