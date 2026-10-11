package com.dharanish.spoofguarddialer

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.dharanish.spoofguarddialer.util.DialerPreferences
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Synthetic contact only. Run on a disposable emulator with this app selected as default dialer. */
@RunWith(AndroidJUnit4::class)
class ContactsFlowTest {
    @Test fun contactSearchFavoriteAndEditBeforeCall() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val number = "5550199"
        val raw = ContactsContract.RawContacts.CONTENT_URI
        val data = ContactsContract.Data.CONTENT_URI
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS)
        val contactUri: Uri
        try {
            val operations = arrayListOf(
                ContentProviderOperation.newInsert(raw).withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build(),
                ContentProviderOperation.newInsert(data).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, "Aster Dialer QA").build(),
                ContentProviderOperation.newInsert(data).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build(),
            )
            contactUri = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)[0].uri!!
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
        try {
            context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_DIAL)
                .setData(Uri.fromParts("tel", "199", null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue(device.wait(Until.hasObject(By.text("Aster Dialer QA")), 10_000))
            device.waitForIdle()
            device.takeScreenshot(File(context.getExternalFilesDir(null), "qa-keypad-match.png"))
            device.findObject(By.text("Contacts")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Aster Dialer QA")), 5_000))
            device.waitForIdle()
            device.findObject(By.text("Aster Dialer QA")).click()
            clickText(device, "Add to favorites")
            assertTrue(device.wait(Until.hasObject(By.text("Remove from favorites")), 5_000))
            device.pressBack()
            device.waitForIdle()
            device.findObject(By.text("Favorites")).click()
            device.waitForIdle()
            assertTrue(device.hasObject(By.text("Aster Dialer QA")))
            device.findObject(By.text("Aster Dialer QA")).click()
            clickText(device, "Edit number before calling")
            assertTrue("Editing a contact replaces the keypad's partial number",
                device.wait(Until.hasObject(By.clazz("android.widget.EditText").text(number)), 5_000))
        } finally {
            if (number in DialerPreferences.favorites(context)) DialerPreferences.toggleFavorite(context, number)
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.WRITE_CONTACTS)
            try { context.contentResolver.delete(contactUri, null, null) }
            finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
        }
    }
    private fun clickText(device: UiDevice, text: String) {
        val item = device.wait(Until.findObject(By.text(text)), 5_000)
        org.junit.Assert.assertNotNull("Visible contact action: $text", item)
        item.click()
    }
}
