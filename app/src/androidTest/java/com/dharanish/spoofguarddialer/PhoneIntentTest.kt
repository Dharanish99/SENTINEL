package com.dharanish.spoofguarddialer

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dharanish.spoofguarddialer.util.PhoneIntents
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneIntentTest {
    @Test fun dialWithoutDataOpensAnEmptyKeypad() { assertEquals("", PhoneIntents.number(Intent(Intent.ACTION_DIAL))) }
    @Test fun externalDialPreservesTheInternationalPrefix() { assertEquals("+15550100", parse(Intent.ACTION_DIAL, "tel:%2B1%20(555)%200100")) }
    @Test fun browserTelLinksAreHandled() { assertEquals("5550101", parse(Intent.ACTION_VIEW, "tel:5550101")) }
    @Test fun extensionsBecomePostDialDigits() { assertEquals("5550101,123", parse(Intent.ACTION_VIEW, "tel:5550101;ext=123")) }
    @Test fun encodedServiceCodesArePreserved() {
        assertEquals("*#06#", parse(Intent.ACTION_DIAL, "tel:*%2306%23")) }
    @Test fun unsupportedSchemesAreIgnored() { assertNull(parse(Intent.ACTION_VIEW, "https://example.com/5550100")); assertNull(parse(Intent.ACTION_DIAL, "smsto:5550100")) }
    @Test fun callIntentsAreNotSilentlyTreatedAsPrefill() { assertNull(parse(Intent.ACTION_CALL, "tel:5550100")) }
    private fun parse(action: String, uri: String) = PhoneIntents.number(Intent(action, Uri.parse(uri)))
}
