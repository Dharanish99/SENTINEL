package com.dharanish.spoofguarddialer.ui.components

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.BlockedNumberContract
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dharanish.spoofguarddialer.telecomcore.CallPlacer
import com.dharanish.spoofguarddialer.telecomcore.DialString
import com.dharanish.spoofguarddialer.util.PhoneActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberActionsSheet(
    title: String, number: String, onDismiss: () -> Unit, onEditNumber: (String) -> Unit,
    lookupUri: String? = null, details: String? = null, favorite: Boolean? = null,
    onFavorite: (() -> Unit)? = null, onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callable = DialString.isCallable(number)
    var canBlock by remember(number) { mutableStateOf(false) }
    var blocked by remember(number) { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(number) {
        val result = withContext(Dispatchers.IO) {
            val allowed = callable && runCatching {
                BlockedNumberContract.canCurrentUserBlockNumbers(context) && !context.getSystemService(TelephonyManager::class.java).isEmergencyNumber(number)
            }.getOrDefault(false)
            allowed to (allowed && runCatching { BlockedNumberContract.isBlocked(context, number) }.getOrDefault(false))
        }
        canBlock = result.first
        blocked = result.second
    }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (number.isNotBlank() && title != number) Text(number, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            details?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Button(onClick = { if (CallPlacer.place(context, number)) onDismiss() }, enabled = callable, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Call, null); Spacer(Modifier.width(8.dp)); Text("Call") }
                OutlinedButton(onClick = { PhoneActions.message(context, number); onDismiss() }, enabled = callable, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Message, null); Spacer(Modifier.width(8.dp)); Text("Message") }
            }
            if (lookupUri != null) TextButton(onClick = { PhoneActions.launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(lookupUri))); onDismiss() }) { Text("View or edit contact") }
            else if (callable) TextButton(onClick = { PhoneActions.addContact(context, number); onDismiss() }) { Text("Add to contacts") }
            if (onFavorite != null) TextButton(onClick = onFavorite) { Icon(if (favorite == true) Icons.Default.Star else Icons.Default.StarBorder, null); Spacer(Modifier.width(8.dp)); Text(if (favorite == true) "Remove from favorites" else "Add to favorites") }
            TextButton(onClick = { onEditNumber(number); onDismiss() }, enabled = callable) { Text("Edit number before calling") }
            TextButton(onClick = { PhoneActions.copy(context, number) }, enabled = number.isNotBlank()) { Text("Copy number") }
            if (canBlock) TextButton(enabled = !busy, onClick = { confirmBlock = true }) { Text(if (blocked) "Unblock number" else "Block number") }
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete from call history", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmBlock) AlertDialog(onDismissRequest = { confirmBlock = false }, title = { Text(if (blocked) "Unblock this number?" else "Block this number?") }, text = {
        Text(if (blocked) "Calls and messages from this number will be allowed again." else "Android will block calls and messages from this number. You can undo this in Blocked numbers.")
    }, confirmButton = { TextButton(onClick = {
        confirmBlock = false; busy = true
        scope.launch {
            val success = withContext(Dispatchers.IO) { runCatching {
                if (blocked) { BlockedNumberContract.unblock(context, number); true }
                else context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }) != null
            }.getOrDefault(false) }
            busy = false
            if (success) blocked = !blocked
            Toast.makeText(context, if (!success) "Couldn't update blocked numbers" else if (blocked) "Number blocked" else "Number unblocked", Toast.LENGTH_SHORT).show()
        }
    }) { Text(if (blocked) "Unblock" else "Block") } }, dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Cancel") } })
}
