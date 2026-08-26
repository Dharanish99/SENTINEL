package com.dharanish.spoofguarddialer.ui.recents

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dharanish.spoofguarddialer.telecomcore.CallPlacer
import com.dharanish.spoofguarddialer.ui.calllog.CallLogEntry
import com.dharanish.spoofguarddialer.ui.calllog.CallLogViewModel
import com.dharanish.spoofguarddialer.ui.calllog.CallType
import com.dharanish.spoofguarddialer.ui.components.BottomNavBar
import com.dharanish.spoofguarddialer.ui.components.PermissionNeededPrompt
import com.dharanish.spoofguarddialer.ui.components.SentinalTab
import com.dharanish.spoofguarddialer.ui.components.SmallAvatar
import com.dharanish.spoofguarddialer.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun RecentsScreen(
    currentTab: SentinalTab,
    onTabSelect: (SentinalTab) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallLogViewModel = viewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val context = LocalContext.current

    fun hasPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALL_LOG
    ) == PackageManager.PERMISSION_GRANTED

    var permissionGranted by remember { mutableStateOf(hasPermission()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = hasPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) viewModel.loadCallLog()
    }

    Column(modifier = modifier.fillMaxSize().background(SentinalBg)) {
        Text(
            "Recents", style = MaterialTheme.typography.headlineMedium, color = SentinalTextPrimary,
            modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 4.dp)
        )
        if (!permissionGranted) {
            PermissionNeededPrompt(
                message = "SENTINAL needs access to your call log to show recent calls.",
                onGrantClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
                items(entries) { entry ->
                    RecentRow(entry, onClick = { CallPlacer.place(context, entry.number) })
                    HorizontalDivider(color = SentinalDivider, thickness = 0.5.dp)
                }
            }
        }
        BottomNavBar(current = currentTab, onSelect = onTabSelect)
    }
}

@Composable
private fun RecentRow(entry: CallLogEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp)
    ) {
        val initial = (entry.name ?: entry.number).take(1).uppercase()
        SmallAvatar(initial = initial)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name ?: entry.number, style = MaterialTheme.typography.titleMedium, color = SentinalTextPrimary)
            Row {
                val (icon, tint) = callTypeVisuals(entry.type)
                Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text(formatRelativeDate(entry.timestampMillis), color = SentinalTextMuted, fontSize = 12.sp)
            }
        }
        if (entry.durationSeconds > 0) Text(formatDuration(entry.durationSeconds), color = SentinalTextMuted, fontSize = 12.sp)
    }
}

private fun callTypeVisuals(type: CallType): Pair<ImageVector, Color> = when (type) {
    CallType.OUTGOING -> Icons.Filled.CallMade to SentinalEmerald
    CallType.MISSED -> Icons.Filled.CallMissed to SentinalRed
    else -> Icons.Filled.CallReceived to SentinalTextMuted
}

private fun formatDuration(seconds: Long): String {
    val m = TimeUnit.SECONDS.toMinutes(seconds)
    val s = seconds - TimeUnit.MINUTES.toSeconds(m)
    return String.format(Locale.US, "%d:%02d", m, s)
}

private fun formatRelativeDate(millis: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(millis))