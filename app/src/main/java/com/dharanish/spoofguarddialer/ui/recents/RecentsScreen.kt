package com.dharanish.spoofguarddialer.ui.recents

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dharanish.spoofguarddialer.telecomcore.CallPlacer
import com.dharanish.spoofguarddialer.telecomcore.DialString
import com.dharanish.spoofguarddialer.ui.calllog.*
import com.dharanish.spoofguarddialer.ui.components.*
import com.dharanish.spoofguarddialer.util.PhoneActions
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun RecentsScreen(
    currentTab: SentinalTab, onTabSelect: (SentinalTab) -> Unit,
    onEditNumber: (String) -> Unit = {}, permissionRevision: Int = 0,
    modifier: Modifier = Modifier, viewModel: CallLogViewModel = viewModel(),
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val entries by viewModel.entries.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var missedOnly by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<CallLogEntry?>(null) }
    var deleteIds by remember { mutableStateOf<List<Long>?>(null) }
    var permissionGranted by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val deletePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) deleteIds?.let(viewModel::delete)
        deleteIds = null
    }
    LaunchedEffect(permissionRevision) {
        com.dharanish.spoofguarddialer.telecomcore.MissedCallNotifications.clear(context)
        permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        viewModel.loadCallLog()
    }
    LaunchedEffect(query, missedOnly) { delay(180); viewModel.filter(query, missedOnly) }
    val zone = ZoneId.systemDefault()
    val groups = remember(entries) { entries.groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() } }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Recents", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "History options") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Refresh") }, onClick = { viewModel.loadCallLog(); menu = false })
                    DropdownMenuItem(text = { Text("Delete displayed calls") }, enabled = entries.isNotEmpty(), onClick = { deleteIds = entries.map { it.id }; menu = false })
                }
            }
        }
        if (!permissionGranted) PermissionNeededPrompt(
            message = "Allow call-log access to see your recent calls.", modifier = Modifier.weight(1f),
            onGrantClick = { PhoneActions.launch(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))) },
        ) else {
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search call history") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), shape = MaterialTheme.shapes.extraLarge)
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!missedOnly, onClick = { missedOnly = false }, label = { Text("All calls") })
                FilterChip(missedOnly, onClick = { missedOnly = true }, label = { Text("Missed") })
            }
            if (entries.isNotEmpty() && viewModel.error != null) {
                Text(viewModel.error.orEmpty(), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    groups.forEach { (date, calls) ->
                        item(key = "date_" + date) { Text(dayLabel(date, locale), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp)) }
                        items(calls, key = { it.id }) { entry ->
                            val name = entry.name ?: entry.number.ifBlank { if (entry.presentation == TelecomManager.PRESENTATION_RESTRICTED) "Private number" else "Unknown caller" }
                            Row(Modifier.fillMaxWidth().clickable { selected = entry }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                SmallAvatar(name.take(1).uppercase()); Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.titleMedium, color = if (entry.type == CallType.MISSED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(when (entry.type) { CallType.OUTGOING -> Icons.Default.CallMade; CallType.MISSED -> Icons.Default.CallMissed; else -> Icons.Default.CallReceived }, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(4.dp))
                                        Text(entry.type.name.lowercase().replaceFirstChar { it.titlecase() } + " · " + DateTimeFormatter.ofPattern("h:mm a", locale).format(Instant.ofEpochMilli(entry.timestampMillis).atZone(zone)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { CallPlacer.place(context, entry.number) }, enabled = DialString.isCallable(entry.number)) { Icon(Icons.Default.Call, "Call " + name) }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                        }
                    }
                    if (viewModel.hasMore) item { TextButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth(), enabled = !viewModel.isLoading) { Text("Load older calls") } }
                }
                if (entries.isEmpty()) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (viewModel.isLoading) CircularProgressIndicator() else {
                        Icon(Icons.Default.History, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text(viewModel.error ?: if (query.isNotBlank()) "No matching calls" else if (missedOnly) "No missed calls" else "Your calls will appear here")
                        if (viewModel.error != null) TextButton(onClick = viewModel::loadCallLog) { Text("Try again") }
                    }
                }
            }
        }
        BottomNavBar(currentTab, onTabSelect)
    }
    selected?.let { entry ->
        NumberActionsSheet(title = entry.name ?: entry.number.ifBlank { "Private or unknown caller" }, number = entry.number,
            details = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy · h:mm a", locale).format(Instant.ofEpochMilli(entry.timestampMillis).atZone(zone)) + "\n" + entry.type.name.lowercase().replaceFirstChar { it.titlecase() } + " · " + formatDuration(entry.durationSeconds),
            onDismiss = { selected = null }, onEditNumber = onEditNumber, onDelete = { deleteIds = listOf(entry.id); selected = null })
    }
    if (deleteIds != null) AlertDialog(onDismissRequest = { deleteIds = null }, title = { Text("Delete call history?") },
        text = { Text("Delete " + deleteIds!!.size + " selected call record(s) from the phone's call history? This cannot be undone.") },
        confirmButton = { TextButton(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                deleteIds?.let(viewModel::delete); deleteIds = null
            } else deletePermission.launch(Manifest.permission.WRITE_CALL_LOG)
        }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteIds = null }) { Text("Cancel") } })
}

private fun dayLabel(date: LocalDate, locale: Locale): String = when (date) {
    LocalDate.now() -> "Today"; LocalDate.now().minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
}

private fun formatDuration(seconds: Long): String = if (seconds < 60) seconds.toString() + " seconds" else
    (seconds / 60).toString() + " min " + (seconds % 60) + " sec"
