package com.dharanish.spoofguarddialer.telecomcore

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Arms Android's proximity-controlled display only while using the handset. */
class CallProximityController(context: Context) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val sensor = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val power = context.getSystemService(PowerManager::class.java)
    private val lock = if (power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
        power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, context.packageName + ":call-proximity")
            .apply { setReferenceCounted(false) }
    } else null
    private var enabled = false

    fun update(shouldEnable: Boolean) {
        if (enabled == shouldEnable) return
        enabled = shouldEnable
        if (enabled) {
            // Keep the native lock armed while off-hook. Android wakes the display on FAR,
            // including while the app's own sensor listener is suspended with the screen off.
            lock?.let { if (!it.isHeld) it.acquire() }
            sensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        } else {
            sensors.unregisterListener(this)
            if (lock?.isHeld == true) lock.release()
            _covered.value = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (enabled && event.sensor.type == Sensor.TYPE_PROXIMITY) {
            _covered.value = ProximityPolicy.isNear(event.values.firstOrNull() ?: Float.NaN, event.sensor.maximumRange)
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    fun close() { update(false) }

    companion object {
        private val _covered = MutableStateFlow(false)
        /** Also prevents cheek touches on devices without a native proximity display lock. */
        val covered = _covered.asStateFlow()
    }
}

object ProximityPolicy {
    fun enabled(states: Iterable<Int>, route: Int?): Boolean = route == CallAudioState.ROUTE_EARPIECE &&
        states.any { it == Call.STATE_ACTIVE || it == Call.STATE_DIALING || it == Call.STATE_CONNECTING || it == Call.STATE_PULLING_CALL }

    fun isNear(distance: Float, maximumRange: Float): Boolean {
        val threshold = if (maximumRange > 0) minOf(maximumRange, 5f) else 5f
        return distance.isFinite() && distance >= 0f && distance < threshold
    }
}
