package com.dharanish.spoofguarddialer.ui.call

import android.telecom.Call
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dharanish.spoofguarddialer.ui.incoming.IncomingCallScreen

@Composable
fun CallScreen(
    modifier: Modifier = Modifier,
    viewModel: CallViewModel = viewModel()
) {
    val callState by viewModel.callState.collectAsState()
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val callerName by viewModel.callerName.collectAsState()

    when (callState) {
        Call.STATE_RINGING -> IncomingCallScreen(
            callerName = callerName ?: (phoneNumber ?: "Unknown"),
            callerNumber = phoneNumber ?: "",
            isVerified = callerName != null,
            onAnswer = { armed ->
                viewModel.setArmSecureOnConnect(armed)
                viewModel.answer()
            },
            onReject = { viewModel.reject() },
            modifier = modifier
        )
        else -> InCallScreen(modifier = modifier, viewModel = viewModel)
    }
}