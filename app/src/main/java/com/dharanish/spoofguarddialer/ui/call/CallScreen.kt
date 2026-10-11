package com.dharanish.spoofguarddialer.ui.call

import android.telecom.Call
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dharanish.spoofguarddialer.telecomcore.CallPlacer
import com.dharanish.spoofguarddialer.telecomcore.TelecomCallRegistry
import com.dharanish.spoofguarddialer.ui.incoming.IncomingCallScreen
import com.dharanish.spoofguarddialer.util.PhoneActions

@Composable
fun CallScreen(modifier: Modifier = Modifier, viewModel: CallViewModel = viewModel(), showDialpad: Boolean = false) {
    val snapshot by viewModel.currentSnapshot.collectAsState()
    val callerName by viewModel.callerName.collectAsState()
    val snapshots by viewModel.snapshots.collectAsState()
    val postDialWait by TelecomCallRegistry.postDialWait.collectAsState()
    val context = LocalContext.current
    var messageDialog by remember { mutableStateOf(false) }
    when (snapshot?.state) {
        Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> key(snapshot?.id) {
            IncomingCallScreen(
                callerName = callerName ?: snapshot?.displayName ?: snapshot?.number ?: "Private or unknown caller",
                callerNumber = snapshot?.number.orEmpty(), isVerified = callerName != null,
                onAnswer = { armed -> viewModel.setArmSecureOnConnect(armed); viewModel.answer() },
                onReject = viewModel::reject, modifier = modifier,
                onSilence = viewModel::silence, onMessage = { messageDialog = true },
                canMessage = !snapshot?.number.isNullOrBlank(),
                callWaiting = snapshots.any { it.id != snapshot?.id && it.parentId == null && it.state == Call.STATE_ACTIVE },
                accountLabel = snapshot?.accountLabel,
            )
        }
        else -> InCallScreen(modifier, viewModel, showDialpad)
    }
    if (messageDialog && snapshot?.state == Call.STATE_RINGING) AlertDialog(onDismissRequest = { messageDialog = false }, title = { Text("Reply to caller") }, text = {
        Column {
            if (snapshot?.can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT) == true) {
                snapshot?.cannedResponses.orEmpty().ifEmpty { listOf("Can't talk now. I'll call you back.", "I'm on my way.", "Please send me a message.") }.forEach { response ->
                    TextButton(onClick = { viewModel.rejectWithMessage(response); messageDialog = false }) { Text(response) }
                }
            }
            TextButton(onClick = {
                val number = snapshot?.number ?: return@TextButton
                messageDialog = false
                PhoneActions.afterUnlock(context) { if (PhoneActions.message(context, number)) viewModel.reject() }
            }) { Text("Write a message…") }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = { messageDialog = false }) { Text("Cancel") } })

    postDialWait?.let { pending -> AlertDialog(onDismissRequest = { TelecomCallRegistry.continuePostDial(false) }, title = { Text("Send additional digits?") },
        text = { Text("Send " + pending.second + " to this call?") },
        confirmButton = { TextButton(onClick = { TelecomCallRegistry.continuePostDial(true) }) { Text("Send") } },
        dismissButton = { TextButton(onClick = { TelecomCallRegistry.continuePostDial(false) }) { Text("Cancel") } }) }

    if (snapshot?.state == Call.STATE_SELECT_PHONE_ACCOUNT) {
        val accounts = remember(snapshot?.id) { CallPlacer.accounts(context) }
        AlertDialog(onDismissRequest = viewModel::hangUp, title = { Text("Choose a calling SIM") }, text = {
            Column { accounts.forEach { account -> TextButton(onClick = { viewModel.selectAccount(account.handle) }) { Text(account.label) } }
                if (accounts.isEmpty()) Text("No calling SIM is available. Check Phone permission and SIM settings.")
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = viewModel::hangUp) { Text("Cancel call") } })
    }
}
