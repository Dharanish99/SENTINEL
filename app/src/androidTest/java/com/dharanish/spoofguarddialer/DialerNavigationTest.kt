package com.dharanish.spoofguarddialer

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run after selecting this app as the default dialer on a test device. No calls are placed. */
@RunWith(AndroidJUnit4::class)
class DialerNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext

    private fun dial(number: String, action: String = Intent.ACTION_DIAL) {
        context.startActivity(Intent(action, Uri.fromParts("tel", number, null))
            .setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("External number is displayed", device.wait(Until.hasObject(By.text(number)), 10_000))
        device.waitForIdle()
        assertNull("A dial link must not place a call", device.findObject(By.desc("End call")))
    }

    @Test fun externalLinksReplaceTheNumberEvenWhenAlreadyOpen() {
        dial("+15550100")
        dial("5550101", Intent.ACTION_VIEW)
        assertNull(device.findObject(By.text("+15550100")))
    }

    @Test fun numberSurvivesTabsAndRotation() {
        dial("5550102")
        clickText("Contacts")
        device.waitForIdle()
        clickText("Keypad")
        assertTrue(device.wait(Until.hasObject(By.text("5550102")), 5_000))
        try {
            device.setOrientationLeft()
            assertTrue(device.wait(Until.hasObject(By.text("5550102")), 5_000))
            device.setOrientationNatural()
            assertTrue(device.wait(Until.hasObject(By.text("5550102")), 5_000))
        } finally { device.unfreezeRotation() }
    }

    private fun clickText(text: String) {
        repeat(3) {
            val item = device.wait(Until.findObject(By.text(text)), 5_000)
            try { item.click(); return }
            catch (_: androidx.test.uiautomator.StaleObjectException) { device.waitForIdle() }
        }
        fail("Could not click $text after the screen settled")
    }

    @Test fun longPressZeroAndDeleteAreUsable() {
        dial("5550103")
        device.findObject(By.desc("Delete digit. Hold to clear number")).longClick()
        assertTrue(device.wait(Until.hasObject(By.text("Enter a number")), 5_000))
        device.findObject(By.desc("0, +")).longClick()
        assertTrue(device.wait(Until.hasObject(By.text("+")), 5_000))
        device.findObject(By.desc("2, ABC")).click()
        assertTrue(device.wait(Until.hasObject(By.text("+2")), 5_000))
        device.findObject(By.desc("Delete digit. Hold to clear number")).click()
        assertTrue(device.wait(Until.hasObject(By.text("+")), 5_000))
    }
}
