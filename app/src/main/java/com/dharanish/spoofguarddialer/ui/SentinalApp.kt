package com.dharanish.spoofguarddialer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dharanish.spoofguarddialer.telecomcore.SpoofGuardInCallService
import com.dharanish.spoofguarddialer.telecomcore.TelecomCallRegistry
import com.dharanish.spoofguarddialer.ui.components.SentinalTab
import com.dharanish.spoofguarddialer.ui.contacts.ContactsScreen
import com.dharanish.spoofguarddialer.ui.debug.CallBridgeDebugScreen
import com.dharanish.spoofguarddialer.ui.debug.DialerRoleDebugScreen
import com.dharanish.spoofguarddialer.ui.dialpad.DialpadScreen
import com.dharanish.spoofguarddialer.ui.recents.RecentsScreen
import com.dharanish.spoofguarddialer.ui.settings.SettingsScreen

@Composable
fun SentinalApp(
    modifier: Modifier = Modifier, initialNumber: String? = null, dialRequest: Long = 0,
    openRecents: Boolean = false, permissionRevision: Int = 0, onIntentConsumed: () -> Unit = {},
) {
    var tabName by rememberSaveable { mutableStateOf(SentinalTab.KEYPAD.name) }
    var debugScreen by rememberSaveable { mutableStateOf<String?>(null) }
    var dialed by rememberSaveable { mutableStateOf("") }
    var appliedRequest by rememberSaveable { mutableLongStateOf(-1L) }
    val tab = SentinalTab.valueOf(tabName)
    val context = LocalContext.current
    val calls by TelecomCallRegistry.snapshots.collectAsState()
    val tabSelect: (SentinalTab) -> Unit = { tabName = it.name; debugScreen = null }
    val editNumber: (String) -> Unit = { dialed = it; tabSelect(SentinalTab.KEYPAD) }

    LaunchedEffect(dialRequest, initialNumber, openRecents) {
        if (initialNumber != null && appliedRequest != dialRequest) {
            dialed = initialNumber; appliedRequest = dialRequest; tabSelect(SentinalTab.KEYPAD)
            onIntentConsumed()
        } else if (openRecents) { tabSelect(SentinalTab.RECENTS); onIntentConsumed() }
    }
    BackHandler(enabled = debugScreen != null || tab != SentinalTab.KEYPAD) {
        if (debugScreen != null) { debugScreen = null; tabName = SentinalTab.SETTINGS.name }
        else tabName = SentinalTab.KEYPAD.name
    }
    Column(modifier) {
        if (calls.isNotEmpty()) {
            Surface(onClick = { SpoofGuardInCallService.openCallUi(context) }, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Icon(Icons.Default.Call, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Return to call", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (debugScreen) {
                "role" -> DialerRoleDebugScreen()
                "bridge" -> CallBridgeDebugScreen()
                else -> when (tab) {
                    SentinalTab.KEYPAD -> DialpadScreen(tab, tabSelect, dialed = dialed, onDialedChange = { dialed = it }, permissionRevision = permissionRevision)
                    SentinalTab.CONTACTS -> ContactsScreen(tab, tabSelect, onEditNumber = editNumber, permissionRevision = permissionRevision)
                    SentinalTab.RECENTS -> RecentsScreen(tab, tabSelect, onEditNumber = editNumber, permissionRevision = permissionRevision)
                    SentinalTab.SETTINGS -> SettingsScreen(tab, tabSelect, onOpenDialerRoleDebug = { debugScreen = "role" }, onOpenBridgeTestDebug = { debugScreen = "bridge" })
                }
            }
        }
    }
}
