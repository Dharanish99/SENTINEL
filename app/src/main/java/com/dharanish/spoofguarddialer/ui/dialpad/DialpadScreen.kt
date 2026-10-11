package com.dharanish.spoofguarddialer.ui.dialpad

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dharanish.spoofguarddialer.telecomcore.CallPlacer
import com.dharanish.spoofguarddialer.telecomcore.DialString
import com.dharanish.spoofguarddialer.ui.components.BottomNavBar
import com.dharanish.spoofguarddialer.ui.components.SentinalTab
import com.dharanish.spoofguarddialer.ui.components.SmallAvatar
import com.dharanish.spoofguarddialer.ui.contacts.ContactsViewModel
import com.dharanish.spoofguarddialer.util.DialerPreferences
import com.dharanish.spoofguarddialer.util.PhoneActions

private val keys = listOf("1" to "Voicemail", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialpadScreen(
    currentTab: SentinalTab, onTabSelect: (SentinalTab) -> Unit,
    dialed: String, onDialedChange: (String) -> Unit, permissionRevision: Int = 0,
    modifier: Modifier = Modifier, contactsViewModel: ContactsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val contacts by contactsViewModel.contacts.collectAsState()
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(dialed, TextRange(dialed.length))) }
    var menu by remember { mutableStateOf(false) }
    var accountPicker by remember { mutableStateOf(false) }
    var speedDialMenu by remember { mutableStateOf(false) }
    var speedDialKey by remember { mutableStateOf<String?>(null) }
    var speedDialNumber by rememberSaveable { mutableStateOf("") }
    var selectedAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    val accounts = remember(permissionRevision) { CallPlacer.accounts(context) }
    val selected = accounts.firstOrNull { it.handle.id == selectedAccountId }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 45) }.getOrNull() }
    DisposableEffect(tone) { onDispose { tone?.release() } }
    LaunchedEffect(permissionRevision) { contactsViewModel.loadContacts() }
    LaunchedEffect(dialed) { if (dialed != field.text) field = TextFieldValue(dialed, TextRange(dialed.length)) }

    fun update(value: TextFieldValue) {
        field = value.copy(text = value.text.take(256), selection = TextRange(value.selection.start.coerceAtMost(256), value.selection.end.coerceAtMost(256)))
        onDialedChange(field.text)
    }
    fun insert(text: String) {
        val start = field.selection.min; val end = field.selection.max
        val next = field.text.replaceRange(start, end, text)
        update(TextFieldValue(next, TextRange((start + text.length).coerceAtMost(256))))
    }
    fun digit(key: String) {
        if (DialerPreferences.haptics(context)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (DialerPreferences.tones(context)) {
            val value = when (key) { "*" -> ToneGenerator.TONE_DTMF_S; "#" -> ToneGenerator.TONE_DTMF_P; else -> key.toIntOrNull() }
            value?.let { tone?.startTone(it, 90) }
        }
        insert(key)
    }
    val search = DialString.searchKey(field.text)
    val matches = remember(contacts, search) {
        if (search.isEmpty()) emptyList() else contacts.asSequence().filter {
            it.numberSearchKey.contains(search) || it.nameSearchKey.contains(search)
        }.take(2).toList()
    }

    Column(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val keySpacing = ((maxHeight - 640.dp) / 10).coerceIn(2.dp, 16.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Phone", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Keypad options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Speed dial") }, onClick = { speedDialMenu = true; menu = false })
                        DropdownMenuItem(text = { Text("Add 2-second pause ,") }, onClick = { insert(","); menu = false })
                        DropdownMenuItem(text = { Text("Add wait ;") }, onClick = { insert(";"); menu = false })
                        DropdownMenuItem(text = { Text("Copy number") }, enabled = field.text.isNotBlank(), onClick = { PhoneActions.copy(context, field.text); menu = false })
                        DropdownMenuItem(text = { Text("Send message") }, enabled = DialString.isCallable(field.text), onClick = { PhoneActions.message(context, DialString.normalize(field.text)); menu = false })
                    }
                }
            }
            BasicTextField(value = field, onValueChange = ::update, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, showKeyboardOnFocus = false),
                textStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 30.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 12.dp).semantics { contentDescription = "Phone number" },
                decorationBox = { inner -> Box(contentAlignment = Alignment.Center) {
                    if (field.text.isEmpty()) Text("Enter a number", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
                    inner()
                } })
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = 144.dp).padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (matches.isNotEmpty()) matches.forEach { contact ->
                    Surface(onClick = { update(TextFieldValue(contact.number, TextRange(contact.number.length))) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SmallAvatar(contact.initial, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(contact.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(contact.number, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.NorthWest, "Use number for " + contact.name, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp).size(20.dp))
                        }
                    }
                } else if (DialString.isCallable(field.text)) {
                    TextButton(onClick = { PhoneActions.addContact(context, DialString.normalize(field.text)) }) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Add to contacts") }
                } else Text("Hold 1 for voicemail · Hold 0 for +", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp))
            }
            keys.chunked(3).forEach { row ->
                Row(Modifier.widthIn(max = 360.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (key, letters) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = keySpacing).size(width = 82.dp, height = 68.dp).clip(CircleShape)
                                .combinedClickable(onClick = { digit(key) }, onLongClickLabel = when (key) { "0" -> "Insert plus"; "1" -> "Call voicemail"; in "2".."9" -> "Speed dial " + key; else -> null },
                                    onLongClick = when (key) { "0" -> ({ insert("+") }); "1" -> ({ CallPlacer.voicemail(context, selected?.handle); Unit }); in "2".."9" -> ({
                                        val saved = DialerPreferences.speedDial(context, key)
                                        if (DialString.isCallable(saved)) CallPlacer.place(context, saved, selected?.handle)
                                        else { speedDialKey = key; speedDialNumber = "" }; Unit
                                    }); else -> null })
                                .semantics { contentDescription = key + if (letters.isBlank()) "" else ", " + letters }) {
                            Text(key, fontSize = 30.sp)
                            Text(letters, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(top = 14.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { accountPicker = true }, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.SimCard, "Choose calling SIM") }
                FilledIconButton(onClick = {
                    if (field.text.isBlank()) {
                        val last = DialerPreferences.lastNumber(context)
                        update(TextFieldValue(last, TextRange(last.length)))
                    } else CallPlacer.place(context, field.text, selected?.handle)
                }, modifier = Modifier.size(width = 92.dp, height = 64.dp), shape = CircleShape) { Icon(Icons.Default.Call, if (field.text.isBlank()) "Recall last dialed number" else "Call", Modifier.size(30.dp)) }
                Box(Modifier.size(56.dp).clip(CircleShape).combinedClickable(enabled = field.text.isNotEmpty(), onLongClickLabel = "Clear number", onLongClick = { update(TextFieldValue("")) }, onClick = {
                    val start = field.selection.min; val end = field.selection.max
                    if (start != end) update(TextFieldValue(field.text.removeRange(start, end), TextRange(start)))
                    else if (start > 0) update(TextFieldValue(field.text.removeRange(start - 1, start), TextRange(start - 1)))
                }).semantics { contentDescription = "Delete digit. Hold to clear number" }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Backspace, null) }
            }
            Text(selected?.label ?: if (accounts.size == 1) accounts.first().label else "System calling preference", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
        }
        }
        BottomNavBar(currentTab, onTabSelect)
    }
    if (accountPicker) AlertDialog(onDismissRequest = { accountPicker = false }, title = { Text("Calling SIM") }, text = {
        Column {
            TextButton(onClick = { selectedAccountId = null; accountPicker = false }) { Text("Use system preference") }
            accounts.forEach { account -> TextButton(onClick = { selectedAccountId = account.handle.id; accountPicker = false }) { Text(account.label) } }
            if (accounts.isEmpty()) Text("No calling accounts available. Check Phone permission and SIM settings.")
        }
    }, confirmButton = { TextButton(onClick = { accountPicker = false }) { Text("Done") } })
    if (speedDialMenu) AlertDialog(onDismissRequest = { speedDialMenu = false }, title = { Text("Speed dial") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Hold a key on the keypad to call its saved number.")
            (2..9).forEach { key -> TextButton(onClick = { speedDialKey = key.toString(); speedDialNumber = DialerPreferences.speedDial(context, key.toString()); speedDialMenu = false }) {
                Text(key.toString() + " · " + DialerPreferences.speedDial(context, key.toString()).ifBlank { "Not assigned" })
            } }
        }
    }, confirmButton = { TextButton(onClick = { speedDialMenu = false }) { Text("Done") } })
    speedDialKey?.let { key -> AlertDialog(onDismissRequest = { speedDialKey = null }, title = { Text("Speed dial " + key) }, text = {
        OutlinedTextField(value = speedDialNumber, onValueChange = { speedDialNumber = it.take(256) }, label = { Text("Phone number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
    }, confirmButton = { TextButton(enabled = speedDialNumber.isBlank() || DialString.isCallable(speedDialNumber), onClick = {
        DialerPreferences.setSpeedDial(context, key, speedDialNumber); speedDialKey = null
    }) { Text(if (speedDialNumber.isBlank()) "Clear" else "Save") } }, dismissButton = { TextButton(onClick = { speedDialKey = null }) { Text("Cancel") } }) }
}
