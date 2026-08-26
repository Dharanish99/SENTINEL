package com.dharanish.spoofguarddialer.ui.calllog

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dharanish.spoofguarddialer.telecomcore.CallPlacer

@Composable
fun CallLogScreen(
    modifier: Modifier = Modifier,
    viewModel: CallLogViewModel = viewModel()
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadCallLog()
    }

    if (viewModel.isLoading) {
        Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
            Text("Loading call log...")
        }
        return
    }

    if (entries.isEmpty()) {
        Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
            Text("No call history yet.")
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(entries) { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { CallPlacer.place(context, entry.number) }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row {
                    Text(
                        text = entry.name ?: entry.number,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Text(
                    text = "${entry.type.label()} • ${DateUtils.getRelativeTimeSpanString(entry.timestampMillis)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HorizontalDivider()
        }
    }
}

private fun CallType.label(): String = when (this) {
    CallType.INCOMING -> "Incoming"
    CallType.OUTGOING -> "Outgoing"
    CallType.MISSED -> "Missed"
    CallType.REJECTED -> "Rejected"
    CallType.OTHER -> "Call"
}