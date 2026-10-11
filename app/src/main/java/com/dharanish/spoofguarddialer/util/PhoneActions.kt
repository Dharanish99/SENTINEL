package com.dharanish.spoofguarddialer.util

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast

fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

object PhoneActions {
    fun launch(context: Context, intent: Intent): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent); true
    } catch (_: RuntimeException) {
        Toast.makeText(context, "No app is available for this action", Toast.LENGTH_SHORT).show(); false
    }
    fun afterUnlock(context: Context, action: () -> Unit) {
        val activity = context.activity() ?: return
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) action()
        else keyguard.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = action()
        })
    }
    fun message(context: Context, number: String) = launch(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))
    fun addContact(context: Context, number: String) = launch(context, Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
        type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
        putExtra(ContactsContract.Intents.Insert.PHONE, number)
    })
    fun copy(context: Context, number: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Phone number", number))
        if (android.os.Build.VERSION.SDK_INT <= 32) Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
    }
}
