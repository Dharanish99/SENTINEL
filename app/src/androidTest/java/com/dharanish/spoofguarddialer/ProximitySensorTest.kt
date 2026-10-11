package com.dharanish.spoofguarddialer

import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.dharanish.spoofguarddialer.telecomcore.CallProximityController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Host harness injects emulator proximity 0 then 5 after each readiness message. */
@RunWith(AndroidJUnit4::class)
class ProximitySensorTest {
    @Test fun nativeDisplayTurnsOffNearAndReturnsFar() {
        assumeTrue(Build.MODEL.startsWith("sdk_") || Build.FINGERPRINT.contains("emulator"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.getSystemService(PowerManager::class.java).isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK))
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        lateinit var controller: CallProximityController
        instrumentation.runOnMainSync { controller = CallProximityController(context); controller.update(true) }
        try {
            instrumentation.sendStatus(1, Bundle().apply { putString("stream", "PROXIMITY_READY_NEAR\n") })
            runBlocking { withTimeout(20_000) { CallProximityController.covered.first { it } } }
            Thread.sleep(700)
            assertEquals("Native proximity lock turns the display off", Display.STATE_OFF,
                context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).state)
            instrumentation.sendStatus(1, Bundle().apply { putString("stream", "PROXIMITY_READY_FAR\n") })
            runBlocking { withTimeout(20_000) { CallProximityController.covered.first { !it } } }
            Thread.sleep(700)
            assertEquals("Moving away restores the display", Display.STATE_ON,
                context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).state)
        } finally { instrumentation.runOnMainSync { controller.close() } }
    }
}
