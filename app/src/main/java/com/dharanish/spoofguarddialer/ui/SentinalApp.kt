package com.dharanish.spoofguarddialer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dharanish.spoofguarddialer.telecomcore.CallPlacer
import com.dharanish.spoofguarddialer.ui.components.SentinalTab
import com.dharanish.spoofguarddialer.ui.contacts.ContactsScreen
import com.dharanish.spoofguarddialer.ui.debug.CallBridgeDebugScreen
import com.dharanish.spoofguarddialer.ui.debug.DialerRoleDebugScreen
import com.dharanish.spoofguarddialer.ui.dialpad.DialpadScreen
import com.dharanish.spoofguarddialer.ui.recents.RecentsScreen
import com.dharanish.spoofguarddialer.ui.settings.SettingsScreen

private sealed interface RootScreen {
    data class Tabs(val tab: SentinalTab) : RootScreen
    data object DialerRoleDebug : RootScreen
    data object BridgeTestDebug : RootScreen
}

@Composable
fun SentinalApp(modifier: Modifier = Modifier) {
    var screen by remember { mutableStateOf<RootScreen>(RootScreen.Tabs(SentinalTab.KEYPAD)) }
    val context = LocalContext.current

    // From a developer/debug screen, back returns to the Settings tab.
    BackHandler(enabled = screen !is RootScreen.Tabs) {
        screen = RootScreen.Tabs(SentinalTab.SETTINGS)
    }

    // From any non-keypad tab, back returns to the keypad ("home") tab first —
    // standard bottom-nav convention. Only on the keypad tab does back fall
    // through to the system default (exiting the app).
    val currentTab = (screen as? RootScreen.Tabs)?.tab
    BackHandler(enabled = currentTab != null && currentTab != SentinalTab.KEYPAD) {
        screen = RootScreen.Tabs(SentinalTab.KEYPAD)
    }

    Box(modifier = modifier) {
        when (val s = screen) {
            is RootScreen.Tabs -> when (s.tab) {
                SentinalTab.KEYPAD -> DialpadScreen(
                    currentTab = s.tab,
                    onTabSelect = { screen = RootScreen.Tabs(it) },
                    onCallClick = { number ->
                        if (number.isNotBlank()) CallPlacer.place(context, number)
                    }
                )
                SentinalTab.CONTACTS -> ContactsScreen(s.tab, { screen = RootScreen.Tabs(it) })
                SentinalTab.RECENTS -> RecentsScreen(s.tab, { screen = RootScreen.Tabs(it) })
                SentinalTab.SETTINGS -> SettingsScreen(
                    currentTab = s.tab,
                    onTabSelect = { screen = RootScreen.Tabs(it) },
                    onOpenDialerRoleDebug = { screen = RootScreen.DialerRoleDebug },
                    onOpenBridgeTestDebug = { screen = RootScreen.BridgeTestDebug }
                )
            }
            RootScreen.DialerRoleDebug -> DialerRoleDebugScreen()
            RootScreen.BridgeTestDebug -> CallBridgeDebugScreen()
        }
    }
}