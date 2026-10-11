package com.dharanish.spoofguarddialer

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.dharanish.spoofguarddialer.telecomcore.CallPlacer
import com.dharanish.spoofguarddialer.telecomcore.TelecomCallRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Disposable emulator only: synthetic contacts and an emulated outgoing call. */
@RunWith(AndroidJUnit4::class)
class DialerPolishTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test fun endedCallKeepsCallerAndControlsInPlace() {
        assumeEmulator()
        val number = "5550202"
        CallPlacer.place(context, number)
        try {
            assertTrue(device.wait(Until.hasObject(By.text(number)), 10_000))
            val deadline = System.currentTimeMillis() + 10_000
            while (TelecomCallRegistry.snapshots.value.none { it.state == Call.STATE_ACTIVE } && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertTrue(TelecomCallRegistry.snapshots.value.any { it.state == Call.STATE_ACTIVE })
            val callerBounds = device.findObject(By.text(number)).visibleBounds
            instrumentation.runOnMainSync { TelecomCallRegistry.calls.value.forEach { it.disconnect() } }
            assertTrue("Ended state remains on the call layout", device.wait(Until.hasObject(By.text("Call ended")), 1500))
            device.takeScreenshot(File(context.getExternalFilesDir(null), "qa-call-ended.png"))
            assertEquals("Caller identity does not jump", callerBounds, device.findObject(By.text(number)).visibleBounds)
            assertNotNull("Controls remain in the call screen", device.findObject(By.desc("End call")))
            var control = device.findObject(By.desc("End call"))
            var disabled = false
            repeat(4) {
                if (!control.isEnabled) disabled = true
                control.parent?.let { control = it }
            }
            assertTrue("The enclosing end-call button is disabled", disabled)
            assertTrue("Call task dismisses after its brief ended state", device.wait(Until.gone(By.text("Call ended")), 5000))
        } finally {
            instrumentation.runOnMainSync { TelecomCallRegistry.calls.value.forEach { it.disconnect() } }
        }
    }

    @Test fun largeContactsListFlingsAndAlphabetCanJumpToSameLetterAgain() {
        assumeEmulator()
        val rawUris = mutableListOf<Uri>()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS)
        try {
            for (letter in 'A'..'Z') {
                val ops = arrayListOf<ContentProviderOperation>()
                repeat(20) { index ->
                    val reference = ops.size
                    ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null).withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build()
                    ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, reference)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, "$letter QA Scroll %02d".format(index)).build()
                    ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, reference)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, "155580%02d%02d".format(letter - 'A', index)).build()
                }
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops).filterIndexed { i, _ -> i % 3 == 0 }.forEach { rawUris += it.uri!! }
            }
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
        try {
            context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_DIAL).setData(Uri.parse("tel:")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            device.wait(Until.hasObject(By.text("Contacts")), 5000)
            device.findObject(By.text("Contacts")).click()
            assertTrue(device.wait(Until.hasObject(By.text("All contacts")), 5000))
            device.findObject(By.text("All contacts")).click()
            assertTrue(device.wait(Until.hasObject(By.text("A QA Scroll 00")), 10_000))
            device.waitForIdle()
            device.takeScreenshot(File(context.getExternalFilesDir(null), "qa-contacts-before-fling.png"))
            repeat(6) { flingContacts() }
            device.takeScreenshot(File(context.getExternalFilesDir(null), "qa-contacts-after-fling.png"))
            assertTrue("Large list responds to flings", device.wait(Until.gone(By.text("A QA Scroll 00")), 5000))
            device.findObjects(By.text("Z")).last().click()
            if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
            assertTrue(device.wait(Until.hasObject(By.text("Z QA Scroll 00")), 5000))
            device.findObjects(By.text("A")).last().click()
            if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
            assertTrue(device.wait(Until.hasObject(By.text("A QA Scroll 00")), 5000))
            flingContacts()
            assertTrue(device.wait(Until.gone(By.text("A QA Scroll 00")), 5000))
            device.findObjects(By.text("A")).last().click()
            if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
            assertTrue("Repeating the same alphabet jump works after scrolling", device.wait(Until.hasObject(By.text("A QA Scroll 00")), 5000))
        } finally {
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.WRITE_CONTACTS)
            try { rawUris.forEach { context.contentResolver.delete(it, null, null) } }
            finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
        }
    }

    private fun flingContacts() {
        val bounds = device.findObject(By.scrollable(true)).visibleBounds
        device.swipe(bounds.centerX(), bounds.bottom - bounds.height()/5, bounds.centerX(), bounds.top + bounds.height()/5, 40)
        device.waitForIdle()
        if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
    }
    private fun assumeEmulator() {
        assumeTrue("Synthetic QA must use an emulator", Build.MODEL.startsWith("sdk_") || Build.FINGERPRINT.contains("emulator"))
        device.setOrientationNatural()
    }
}
