package com.dharanish.spoofguarddialer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp

enum class SentinalTab(val label: String, val icon: ImageVector) {
    CONTACTS("Contacts", Icons.Default.Person), RECENTS("Recents", Icons.Default.History),
    KEYPAD("Keypad", Icons.Default.Dialpad), SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun BottomNavBar(current: SentinalTab, onSelect: (SentinalTab) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier, containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
        SentinalTab.entries.forEach { tab ->
            NavigationBarItem(selected = current == tab, onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, null) }, label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)))
        }
    }
}
